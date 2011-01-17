/* Copyright (c) 2001 - 2007 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.wps.gs;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import org.geoserver.wps.WPSStorageHandler;
import org.geoserver.wps.jts.DescribeParameter;
import org.geoserver.wps.jts.DescribeProcess;
import org.geoserver.wps.jts.DescribeResult;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.io.AbstractGridFormat;
import org.geotools.coverage.grid.io.imageio.GeoToolsWriteParams;
import org.geotools.gce.geotiff.GeoTiffFormat;
import org.geotools.gce.geotiff.GeoTiffWriteParams;
import org.geotools.gce.geotiff.GeoTiffWriter;
import org.opengis.parameter.GeneralParameterValue;
import org.opengis.parameter.ParameterValueGroup;

/**
 * Stores a coverage and the file system and returns a link to retrieve it back
 * 
 * @author Andrea Aime - GeoSolutions
 * @author ETj <etj at geo-solutions.it>
 */
@DescribeProcess(title = "storeCoverage", description = "Applies a raster symbolizer to the coverage")
public class StoreCoverage implements GeoServerProcess {

    private final static GeoTiffWriteParams DEFAULT_WRITE_PARAMS;

    static {
        // setting the write parameters (we my want to make these configurable in the future
        DEFAULT_WRITE_PARAMS = new GeoTiffWriteParams();
        DEFAULT_WRITE_PARAMS.setCompressionMode(GeoTiffWriteParams.MODE_EXPLICIT);
        DEFAULT_WRITE_PARAMS.setCompressionType("LZW");
        DEFAULT_WRITE_PARAMS.setCompressionQuality(0.75F);
        DEFAULT_WRITE_PARAMS.setTilingMode(GeoToolsWriteParams.MODE_EXPLICIT);
        DEFAULT_WRITE_PARAMS.setTiling(512, 512);
    }

    WPSStorageHandler storage;

    public StoreCoverage(WPSStorageHandler storage) {
        this.storage = storage;
    }

    @DescribeResult(name = "coverageLocation", description = "The URL that can be used to retrieve the coverage")
    public URL execute(
            @DescribeParameter(name = "coverage", description = "The raster to be styled") GridCoverage2D coverage)
            throws IOException {
        final File file = File.createTempFile(coverage.getName().toString(), ".tif", storage.getStorage());

        // TODO check file prior to writing
        GeoTiffWriter writer = new GeoTiffWriter(file);

        // setting the write parameters for this geotiff
        final ParameterValueGroup params = new GeoTiffFormat().getWriteParameters();
        params.parameter(AbstractGridFormat.GEOTOOLS_WRITE_PARAMS.getName().toString()).setValue(
                DEFAULT_WRITE_PARAMS);
        final GeneralParameterValue[] wps = (GeneralParameterValue[]) params.values().toArray(
                new GeneralParameterValue[1]);
        try {
            writer.write(coverage, wps);
        } finally {
            try {
                writer.dispose();
            } catch (Exception e) {
                // we tried, no need to fuss around this one
            }
        }

        return storage.getURL(file);
    }

}
