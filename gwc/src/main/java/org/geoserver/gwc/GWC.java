/* Copyright (c) 2010 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.gwc;

import static org.geowebcache.seed.GWCTask.TYPE.TRUNCATE;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.LayerGroupInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.NamespaceInfo;
import org.geoserver.config.GeoServer;
import org.geoserver.platform.GeoServerExtensions;
import org.geoserver.wms.GetMapRequest;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.geotools.util.logging.Logging;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.diskquota.DiskQuotaConfig;
import org.geowebcache.diskquota.DiskQuotaMonitor;
import org.geowebcache.diskquota.storage.BDBQuotaStore;
import org.geowebcache.diskquota.storage.LayerQuota;
import org.geowebcache.diskquota.storage.Quota;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridMismatchException;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.SRS;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.seed.GWCTask;
import org.geowebcache.seed.TileBreeder;
import org.geowebcache.storage.StorageBroker;
import org.geowebcache.storage.StorageException;
import org.geowebcache.storage.TileRange;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.util.Assert;

import com.vividsolutions.jts.geom.Envelope;

/**
 * Spring bean acting as a facade to GWC for the GWC/GeoServer integration classes so that they
 * don't need to worry about GWC complexities nor API changes.
 * 
 * @author Gabriel Roldan
 * @version $Id$
 * 
 */
public class GWC implements DisposableBean, ApplicationContextAware {

    /**
     * @see #get()
     */
    private static GWC INSTANCE;

    private static Logger log = Logging.getLogger("org.geoserver.gwc.GWC");

    private final CatalogConfiguration embeddedConfig;

    private final TileLayerDispatcher tld;

    private final StorageBroker storageBroker;

    private final TileBreeder tileBreeder;

    private final GeoServer geoserver;

    private ApplicationContext appContext;

    private final BDBQuotaStore quotaStore;

    private final GWCConfigPersister gwcConfigPersister;

    public GWC(final GWCConfigPersister gwcConfigPersister, final StorageBroker sb,
            final TileLayerDispatcher tld, final TileBreeder tileBreeder,
            final CatalogConfiguration config, GeoServer geoserver, BDBQuotaStore quotaStore) {
        this.gwcConfigPersister = gwcConfigPersister;
        this.tld = tld;
        this.storageBroker = sb;
        this.tileBreeder = tileBreeder;
        this.embeddedConfig = config;
        this.geoserver = geoserver;
        this.quotaStore = quotaStore;
    }

    public synchronized static GWC get() {
        if (GWC.INSTANCE == null) {
            GWC.INSTANCE = GeoServerExtensions.bean(GWC.class);
            if (GWC.INSTANCE == null) {
                throw new IllegalStateException("No bean of type " + GWC.class.getName()
                        + " found by GeoServerExtensions");
            }
        }
        return GWC.INSTANCE;
    }

    public GWCConfig getConfig() {
        return gwcConfigPersister.getConfig();
    }

    /**
     * Fully truncates the given layer, including any ParameterFilter
     * 
     * @param layerName
     */
    public void truncate(final String layerName) {
        try {
            // TODO: use fast truncate path instead of delete. Delete will result in loosing usage
            // statistics

            storageBroker.delete(layerName);
        } catch (StorageException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Truncates the cache for the given layer/style combination
     * 
     * @param layerName
     * @param styleName
     */
    public void truncate(final String layerName, final String styleName) {
        try {
            // TODO: use truncate instead of delete. Delete will result in loosing usage statistics
            storageBroker.delete(layerName);
        } catch (StorageException e) {
            throw new RuntimeException(e);
        }
    }

    public void truncate(final String layerName, final ReferencedEnvelope bounds)
            throws GeoWebCacheException {

        final TileLayer tileLayer = tld.getTileLayer(layerName);

        List<GWCTask> truncateTasks = new ArrayList<GWCTask>(tileLayer.getGridSubsets().size()
                * tileLayer.getMimeTypes().size());
        /*
         * Create a truncate task for each gridSubset (CRS) and format
         */
        for (GridSubset layerGrid : tileLayer.getGridSubsets().values()) {
            final GridSet gridSet = layerGrid.getGridSet();
            final String gridSetId = gridSet.getName();
            final SRS srs = gridSet.getSRS();
            final CoordinateReferenceSystem gridSetCrs;
            try {
                gridSetCrs = CRS.decode("EPSG:" + srs.getNumber());
            } catch (Exception e) {
                throw new RuntimeException("Can't decode SRS for layer '" + layerName + "': ESPG:"
                        + srs.getNumber());
            }

            ReferencedEnvelope truncateBoundsInGridsetCrs;

            try {
                truncateBoundsInGridsetCrs = bounds.transform(gridSetCrs, true);
            } catch (Exception e) {
                log.info("Can't truncate layer " + layerName
                        + ": error transforming requested bounds to layer gridset " + gridSetId
                        + ": " + e.getMessage());
                continue;
            }

            final double minx = truncateBoundsInGridsetCrs.getMinX();
            final double miny = truncateBoundsInGridsetCrs.getMinY();
            final double maxx = truncateBoundsInGridsetCrs.getMaxX();
            final double maxy = truncateBoundsInGridsetCrs.getMaxY();
            final BoundingBox reqBounds = new BoundingBox(minx, miny, maxx, maxy);
            /*
             * layerGrid.getCoverageIntersections is not too robust, so we better check the
             * requested bounds intersect the layer bounds
             */
            final BoundingBox layerBounds = layerGrid.getCoverageBestFitBounds();
            if (!layerBounds.intersects(reqBounds)) {
                log.fine("Requested truncation bounds do not intersect cached layer bounds, ignoring truncate request");
                continue;
            }
            final BoundingBox intersection = BoundingBox.intersection(layerBounds, reqBounds);
            final long[][] coverageIntersections = layerGrid.getCoverageIntersections(intersection);
            final int zoomStart = layerGrid.getZoomStart();
            final int zoomStop = layerGrid.getZoomStop();
            final String parameters = null;// how do I get these?

            for (MimeType mime : tileLayer.getMimeTypes()) {
                TileRange tileRange;
                tileRange = new TileRange(layerName, gridSetId, zoomStart, zoomStop,
                        coverageIntersections, mime, parameters);
                GWCTask[] singleTask;
                singleTask = tileBreeder.createTasks(tileRange, tileLayer, TRUNCATE, 1, false);
                truncateTasks.add(singleTask[0]);
            }

        }

        GWCTask[] tasks = truncateTasks.toArray(new GWCTask[truncateTasks.size()]);
        tileBreeder.dispatchTasks(tasks);
    }

    /**
     * 
     * @see org.springframework.beans.factory.DisposableBean#destroy()
     */
    public void destroy() throws Exception {
    }

    public void addOrReplaceLayer(TileLayer layer) {
        tld.getLayerList();
        tld.add(layer);
        log.finer(layer.getName() + " added to TileLayerDispatcher");
    }

    public synchronized void removeLayer(String prefixedName) {
        embeddedConfig.removeLayer(prefixedName);
        tld.remove(prefixedName);
        try {
            storageBroker.delete(prefixedName);
        } catch (StorageException e) {
            throw new RuntimeException(e);
        }
    }

    public void reload() {
        try {
            tld.reInit();
        } catch (GeoWebCacheException gwce) {
            log.fine("Unable to reinit TileLayerDispatcher gwce.getMessage()");
        }
    }

    public void createLayer(LayerInfo layerInfo) {
        TileLayer tileLayer = embeddedConfig.createLayer(layerInfo);
        addOrReplaceLayer(tileLayer);
    }

    public void createLayer(LayerGroupInfo lgi) {
        TileLayer tileLayer = embeddedConfig.createLayer(lgi);
        addOrReplaceLayer(tileLayer);
    }

    /**
     * Tries to dispatch a tile request represented by a GeoServer WMS {@link GetMapRequest} through
     * GeoWebCache, and returns the {@link ConveyorTile} if succeeded or {@code null} if it wasn't
     * possible.
     * <p>
     * Preconditions:
     * <ul>
     * <li><code>{@link GetMapRequest#isTiled() request.isTiled()} == true</code>
     * </ul>
     * </p>
     * 
     * @param request
     * @return
     */
    public final ConveyorTile dispatch(final GetMapRequest request) {
        // Assert.isTrue(request.isTiled(), "isTiled");
        // Assert.notNull(request.getTilesOrigin(), "getTilesOrigin");

        if (!isCachingPossible(request)) {
            return null;
        }

        // request.isTransparent()??
        // request.getEnv()??
        // request.getFormatOptions()??
        final String layerName = request.getRawKvp().get("LAYERS");
        /*
         * This is a quick way of checking if the request was for a single layer. We can't really
         * use request.getLayers() because in the event that a layerGroup was requested, the request
         * parser turned it into a list of actual Layers
         */
        if (layerName.indexOf(',') != -1) {
            return null;
        }

        final TileLayer tileLayer;
        try {
            tileLayer = this.tld.getTileLayer(layerName);
        } catch (GeoWebCacheException e) {
            return null;
        }
        if (!tileLayer.isEnabled()) {
            return null;
        }

        GridSubset gridSubset;
        try {
            String srs = request.getSRS();
            int epsgId = Integer.parseInt(srs.substring(srs.indexOf(':') + 1));
            SRS srs2 = SRS.getSRS(epsgId);
            gridSubset = tileLayer.getGridSubsetForSRS(srs2);
            if (gridSubset == null) {
                return null;
            }
        } catch (Exception e) {
            return null;
        }

        if (request.getWidth() != gridSubset.getTileWidth()
                || request.getHeight() != gridSubset.getTileHeight()) {
            return null;
        }
        final MimeType mimeType;
        try {
            mimeType = MimeType.createFromFormat(request.getFormat());
            List<MimeType> tileLayerFormats = tileLayer.getMimeTypes();
            if (!tileLayerFormats.contains(mimeType)) {
                return null;
            }
        } catch (MimeException me) {
            // not a GWC supported format
            return null;
        }
        ConveyorTile tileResp = null;

        try {
            HttpServletRequest servletReq = null;
            HttpServletResponse servletResp = null;
            final String gridSetId;
            long[] tileIndex;
            gridSetId = gridSubset.getName();
            Envelope bbox = request.getBbox();
            BoundingBox tileBounds = new BoundingBox(bbox.getMinX(), bbox.getMinY(),
                    bbox.getMaxX(), bbox.getMaxY());
            try {
                tileIndex = gridSubset.closestIndex(tileBounds);
            } catch (GridMismatchException e) {
                return null;
            }

            Map<String, String> fullParameters = null;
            Map<String, String> modifiedParameters = null;
            {
                Map<String, String> requestParameterMap = request.getRawKvp();
                Map<String, String>[] modStrs;
                modStrs = tileLayer.getModifiableParameters(requestParameterMap, "UTF-8");
                if (modStrs != null) {
                    fullParameters = modStrs[0];
                    modifiedParameters = modStrs[1];
                }
            }
            ConveyorTile tileReq;
            tileReq = new ConveyorTile(storageBroker, layerName, gridSetId, tileIndex, mimeType,
                    fullParameters, modifiedParameters, servletReq, servletResp);

            tileResp = tileLayer.getTile(tileReq);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return tileResp;
    }

    /**
     * Determines whether the given {@link GetMapRequest} is a candidate to match a GWC tile or not.
     * 
     * @param request
     * @return {@code true} if {@code request} <b>might</b>
     */
    private boolean isCachingPossible(GetMapRequest request) {

        if (request.getFormatOptions() != null && !request.getFormatOptions().isEmpty()) {
            return false;
        }
        if (0.0 != request.getAngle()) {
            return false;
        }
        // if (null != request.getBgColor()) {
        // return false;
        // }
        if (0 != request.getBuffer()) {
            return false;
        }
        if (null != request.getCQLFilter() && !request.getCQLFilter().isEmpty()) {
            return false;
        }
        if (!Double.isNaN(request.getElevation())) {
            return false;
        }
        if (null != request.getFeatureId() && !request.getFeatureId().isEmpty()) {
            return false;
        }
        if (null != request.getFilter() && !request.getFilter().isEmpty()) {
            return false;
        }
        if (null != request.getPalette()) {
            return false;
        }
        if (null != request.getRemoteOwsType() || null != request.getRemoteOwsURL()) {
            return false;
        }
        if (null != request.getSld() || null != request.getSldBody()) {
            return false;
        }
        if (null != request.getStartIndex()) {
            return false;
        }
        if (null != request.getTime() && !request.getTime().isEmpty()) {
            return false;
        }
        if (null != request.getViewParams() && !request.getViewParams().isEmpty()) {
            return false;
        }
        return true;
    }

    /**
     * @param layerName
     * @return the tile layer named {@code layerName}
     * @throws IllegalArgumentException
     *             if no {@link TileLayer} named {@code layeName} is found
     */
    public TileLayer getLayerByName(String layerName) throws IllegalArgumentException {
        TileLayer tileLayer;
        try {
            tileLayer = tld.getTileLayer(layerName);
        } catch (GeoWebCacheException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
        return tileLayer;
    }

    public Set<String> getLayerNames() {
        return tld.getLayerNames();
    }

    public List<TileLayer> getLayers() {
        return new ArrayList<TileLayer>(tld.getLayerList());
    }

    public List<TileLayer> getLayers(final String namespacePrefixFilter) {
        if (namespacePrefixFilter == null) {
            return getLayers();
        }

        final Catalog catalog = embeddedConfig.getCatalog();

        final NamespaceInfo namespaceFilter = catalog.getNamespaceByPrefix(namespacePrefixFilter);
        if (namespaceFilter == null) {
            return getLayers();
        }

        List<TileLayer> filteredLayers = new ArrayList<TileLayer>();

        NamespaceInfo layerNamespace;
        String layerName;

        for (TileLayer tileLayer : getLayers()) {
            layerName = tileLayer.getName();
            LayerInfo layerInfo = catalog.getLayerByName(layerName);
            if (layerInfo != null) {
                layerNamespace = layerInfo.getResource().getNamespace();
                if (namespaceFilter.equals(layerNamespace)) {
                    filteredLayers.add(tileLayer);
                }
            }
        }

        return filteredLayers;
    }

    public DiskQuotaConfig getDiskQuotaConfig() {
        DiskQuotaMonitor monitor = getDiskQuotaMonitor();
        return monitor.getConfig();
    }

    private DiskQuotaMonitor getDiskQuotaMonitor() {
        DiskQuotaMonitor monitor = (DiskQuotaMonitor) appContext.getBean("DiskQuotaMonitor");
        Assert.notNull(monitor);
        return monitor;
    }

    public void saveConfig(GWCConfig gwcConfig) throws IOException {
        gwcConfigPersister.save(gwcConfig);
    }

    public void saveDiskQuotaConfig(DiskQuotaConfig config) {
        DiskQuotaMonitor monitor = getDiskQuotaMonitor();
        monitor.saveConfig(config);
    }

    /**
     * @see org.springframework.context.ApplicationContextAware#setApplicationContext(org.springframework.context.ApplicationContext)
     */
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.appContext = applicationContext;
    }

    public Quota getGlobalQuota() {
        return getDiskQuotaConfig().getGlobalQuota();
    }

    public Quota getGlobalUsedQuota() {
        try {
            return quotaStore.getGloballyUsedQuota();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public Quota getQuotaLimit(final String layerName) {
        DiskQuotaConfig disQuotaConfig = getDiskQuotaConfig();
        List<LayerQuota> layerQuotas = disQuotaConfig.getLayerQuotas();
        if (layerQuotas == null) {
            return null;
        }
        for (LayerQuota lq : layerQuotas) {
            if (layerName.equals(lq.getLayer())) {
                return new Quota(lq.getQuota());
            }
        }
        return null;
    }

    public Quota getUsedQuota(final String layerName) {
        try {
            Quota usedQuotaByLayerName = quotaStore.getUsedQuotaByLayerName(layerName);
            return usedQuotaByLayerName;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return null;
    }
}
