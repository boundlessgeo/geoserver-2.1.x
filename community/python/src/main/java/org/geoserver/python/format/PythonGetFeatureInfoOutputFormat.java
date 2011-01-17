package org.geoserver.python.format;

import java.io.IOException;
import java.io.OutputStream;

import net.opengis.wfs.FeatureCollectionType;

import org.geoserver.platform.ServiceException;
import org.geoserver.wms.GetFeatureInfoRequest;
import org.geoserver.wms.featureinfo.GetFeatureInfoOutputFormat;

public class PythonGetFeatureInfoOutputFormat extends GetFeatureInfoOutputFormat {

    PythonVectorFormatAdapter adapter;
    
    public PythonGetFeatureInfoOutputFormat(PythonVectorFormatAdapter adapter) {
        super(adapter.getMimeType());
        this.adapter = adapter;
        
        //supportedFormats = Arrays.asList(format, adapter.getName());
    }
    
    @Override
    public void write(FeatureCollectionType results, GetFeatureInfoRequest request, OutputStream out)
            throws ServiceException, IOException {
        
        try {
            adapter.write(results, out);
        } 
        catch (Exception e) {
            throw new ServiceException(e);
        }
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        return new PythonGetFeatureInfoOutputFormat(adapter);
    }
    
}
