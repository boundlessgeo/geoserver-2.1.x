/* Copyright (c) 2001 - 2007 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, availible at the root
 * application directory.
 */
package org.geoserver.wms.map;

import java.awt.image.RenderedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.geoserver.wms.WMS;
import org.geoserver.wms.WMSMapContext;
import org.geotools.image.ImageWorker;

import com.sun.media.imageioimpl.common.PackageUtil;

/**
 * Map response handler for JPEG image format.
 * 
 * @author Simone Giannecchini
 * @since 1.4.x
 * 
 */
public final class JPEGMapResponse extends RenderedImageMapResponse {

    /** Logger. */
    private final static Logger LOGGER = org.geotools.util.logging.Logging
            .getLogger(JPEGMapResponse.class.toString());

    private static final boolean CODEC_LIB_AVAILABLE = PackageUtil.isCodecLibAvailable();

    /** the only MIME type this map producer supports */
    private static final String MIME_TYPE = "image/jpeg";

    public JPEGMapResponse(WMS wms) {
        super(MIME_TYPE, wms);
    }

    @Override
    public void formatImageOutputStream(RenderedImage image, OutputStream outStream,
            WMSMapContext mapContext) throws IOException {
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("About to write a JPEG image.");
        }

        boolean JPEGNativeAcc = wms.getJPEGNativeAcceleration() && CODEC_LIB_AVAILABLE;
        float quality = (100 - wms.getJpegCompression()) / 100.0f;
        ImageWorker iw = new ImageWorker(image);
        iw.writeJPEG(outStream, "JPEG", quality, JPEGNativeAcc);

        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("Writing a JPEG done!!!");
        }
    }
}
