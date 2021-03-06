package org.geoserver.python.format;

import java.io.File;
import java.io.OutputStream;

import org.geoserver.python.Python;
import org.geoserver.wms.WMSMapContext;
import org.python.core.Py;
import org.python.core.PyObject;

public class PythonMapFormatAdapter extends PythonFormatAdapter {

    public PythonMapFormatAdapter(File module, Python py) {
        super(module, py);
    }

    @Override
    protected String getMarker() {
        return "__map_format__";
    }

    public void write(WMSMapContext mapContext, OutputStream output) throws Exception {
        PyObject obj = pyObject();
        obj.__call__(Py.javas2pys(mapContext, output));
        
        output.flush();
    }

}
