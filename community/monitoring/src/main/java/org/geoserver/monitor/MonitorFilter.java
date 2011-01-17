package org.geoserver.monitor;

import java.io.IOException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.security.Authentication;
import org.springframework.security.context.SecurityContextHolder;
import org.springframework.security.userdetails.User;
import org.geoserver.monitor.RequestData.Status;
import org.geoserver.ows.util.ResponseUtils;
import org.geoserver.platform.GeoServerExtensions;
import org.geotools.util.logging.Logging;

public class MonitorFilter implements Filter {

    static Logger LOGGER = Logging.getLogger("org.geoserver.monitor");
    
    Monitor monitor;
    MonitorRequestFilter requestFilter;
    
    ExecutorService postProcessExecutor;
    
    public MonitorFilter(Monitor monitor, MonitorRequestFilter requestFilter) {
        this.monitor = monitor;
        this.requestFilter = requestFilter;
        
        postProcessExecutor = Executors.newFixedThreadPool(2);
        
        LOGGER.info("Monitor extension enabled");
    }
    
    public void init(FilterConfig filterConfig) throws ServletException {
    }
    
    
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        //ignore non http requests
        if (!(request instanceof HttpServletRequest)) {
            chain.doFilter(request, response);
            return;
        }
        
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        
        if (requestFilter.filter(req)) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine(req.getRequestURI() + " was filtered from monitoring");
            }
            //don't monitor this request
            chain.doFilter(request, response);
            return;
        }
        
        //start a new request
        RequestData data = monitor.start();
        data.setStartTime(new Date());
        
        //fill in the initial data
        data.setPath(req.getServletPath() + req.getPathInfo());
        
        if (req.getQueryString() != null) {
            data.setQueryString(URLDecoder.decode(req.getQueryString(), "UTF-8"));
        }
        
        data.setHttpMethod(req.getMethod());
        
        String serverName = System.getProperty("http.serverName");
        if (serverName == null) {
            serverName = req.getServerName();
        }
        data.setHost(serverName);
        data.setRemoteAddr(getRemoteAddr(req));
        data.setStatus(Status.RUNNING);
        
        if (SecurityContextHolder.getContext() != null 
                && SecurityContextHolder.getContext().getAuthentication() != null) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth.getPrincipal() != null && auth.getPrincipal() instanceof User) {
                data.setRemoteUser(((User)auth.getPrincipal()).getUsername());
            }
        }
        
        //wrap the request and response
        request = new MonitorServletRequest(req);
        response = new MonitorServletResponse(resp);
        
        monitor.update();
        
        //execute the request
        Throwable error = null;
        try {
            chain.doFilter(request, response);
        }
        catch(Throwable t) {
            error = t;
        }
        
        data = monitor.current();
        data.setBody(((MonitorServletRequest)request).getBodyContent());
        data.setResponseContentType(response.getContentType());
        data.setResponseLength(((MonitorServletResponse)response).getContentLength());
        
        if (error != null) {
            data.setStatus(Status.FAILED);
            data.setErrorMessage(error.getLocalizedMessage());
            data.setError(error);
        }
        
        if (data.getStatus() != Status.FAILED) {
            data.setStatus(Status.FINISHED);
        }
        
        data.setEndTime(new Date());
        data.setTotalTime(data.getEndTime().getTime() - data.getStartTime().getTime());
        monitor.update();
        data = monitor.current();
        
        monitor.complete();
        
        //post processing
        postProcessExecutor.execute(new PostProcessTask(monitor, data, req, resp));
        
        if (error != null) {
            if (error instanceof RuntimeException) {
                throw (RuntimeException)error;
            }
            else {
                throw new RuntimeException(error);
            }
        }
    }

    public void destroy() {
        postProcessExecutor.shutdown();
        monitor.dispose();
    }

    String getRemoteAddr(HttpServletRequest req) {
        String forwardedFor = req.getHeader("X-Forwarded-For");
        if (forwardedFor != null) {
            String[] ips = forwardedFor.split(", ");
            return ips[0];
        } else {
            return req.getRemoteAddr();
        }
    }
    
    static class PostProcessTask implements Runnable {

        Monitor monitor;
        RequestData data;
        HttpServletRequest request;
        HttpServletResponse response;
        
        PostProcessTask(Monitor monitor, RequestData data, HttpServletRequest request, HttpServletResponse response) {
            this.monitor = monitor;
            this.data = data;
            this.request = request;
            this.response = response;
        }
        
        public void run() {
            try {
                List<RequestPostProcessor> pp = new ArrayList();
                pp.add(new ReverseDNSPostProcessor());
                pp.addAll(GeoServerExtensions.extensions(RequestPostProcessor.class));
                
                for (RequestPostProcessor p : pp) {
                    try {
                        p.run(data, request, response);
                    }
                    catch(Exception e) {
                        LOGGER.log(Level.WARNING, "Post process task failed", e);
                    }
                }

                monitor.getDAO().update(data);
            }
            finally {
                monitor = null;
                data = null;
                request = null;
                response = null;
            }
        }
    }

}
