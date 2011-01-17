/* Copyright (c) 2010 TOPP - www.openplans.org.  All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.wms.map;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.geoserver.wms.WMSMapContext;
import org.geoserver.wms.WebMap;

/**
 * An already encoded {@link WebMap} that holds the raw response content in a byte array.
 * 
 * @author Gabriel Roldan
 * @see RawMapResponse
 */
public class RawMap extends WebMap {

    private byte[] mapContents;

    private ByteArrayOutputStream buffer;

    public RawMap(final WMSMapContext mapContext, final byte[] mapContents, final String mimeType) {
        super(mapContext);
        this.mapContents = mapContents;
        setMimeType(mimeType);
    }

    public RawMap(final WMSMapContext mapContext, final ByteArrayOutputStream buff,
            final String mimeType) {
        super(mapContext);
        this.buffer = buff;
        setMimeType(mimeType);
    }

    public void writeTo(OutputStream out) throws IOException {
        if (mapContents != null) {
            out.write(mapContents);
        } else if (buffer != null) {
            buffer.writeTo(out);
        } else {
            throw new IllegalStateException();
        }
    }

}
