package org.geoserver.wps;

import static org.custommonkey.xmlunit.XMLAssert.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import net.opengis.ows11.BoundingBoxType;

import org.geoserver.test.RemoteOWSTestSupport;
import org.geotools.feature.FeatureCollection;
import org.geotools.geojson.feature.FeatureJSON;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.gml3.GMLConfiguration;
import org.geotools.ows.v1_1.OWSConfiguration;
import org.geotools.referencing.CRS;
import org.geotools.xml.Parser;
import org.w3c.dom.Document;

import com.mockrunner.mock.web.MockHttpServletResponse;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.io.WKTReader;

public class ExecuteTest extends WPSTestSupport {
	
	/* TODO GET requests A.4.4.1 */

    public void testDataInline() throws Exception { // Standard Test A.4.4.2, A.4.4.4
        String xml =  
          "<wps:Execute service='WPS' version='1.0.0' xmlns:wps='http://www.opengis.net/wps/1.0.0' " + 
              "xmlns:ows='http://www.opengis.net/ows/1.1'>" + 
            "<ows:Identifier>gt:buffer</ows:Identifier>" + 
             "<wps:DataInputs>" + 
                "<wps:Input>" + 
                    "<ows:Identifier>geom1</ows:Identifier>" + 
                    "<wps:Data>" +
                      "<wps:ComplexData>" + 
                        "<gml:Polygon xmlns:gml='http://www.opengis.net/gml'>" +
                          "<gml:exterior>" + 
                            "<gml:LinearRing>" + 
                              "<gml:coordinates>1 1 2 1 2 2 1 2 1 1</gml:coordinates>" + 
                            "</gml:LinearRing>" + 
                          "</gml:exterior>" + 
                        "</gml:Polygon>" +
                      "</wps:ComplexData>" + 
                    "</wps:Data>" +     
                "</wps:Input>" + 
                "<wps:Input>" + 
                   "<ows:Identifier>buffer</ows:Identifier>" + 
                   "<wps:Data>" + 
                     "<wps:LiteralData>1</wps:LiteralData>" + 
                   "</wps:Data>" + 
                "</wps:Input>" + 
           "</wps:DataInputs>" +
           "<wps:ResponseForm>" +  
             "<wps:ResponseDocument storeExecuteResponse='false'>" + 
               "<wps:Output>" +
                 "<ows:Identifier>result</ows:Identifier>" +
               "</wps:Output>" + 
             "</wps:ResponseDocument>" +
           "</wps:ResponseForm>" + 
         "</wps:Execute>";
        // System.out.println(xml);
        
        Document d = postAsDOM( "wps", xml );
        checkValidationErrors(d);
        
        assertEquals( "wps:ExecuteResponse", d.getDocumentElement().getNodeName() );
        
        assertXpathExists( "/wps:ExecuteResponse/wps:Status/wps:ProcessSucceeded", d);
        assertXpathExists( 
            "/wps:ExecuteResponse/wps:ProcessOutputs/wps:Output/wps:Data/wps:ComplexData/gml:Polygon", d);
    }
    
    public void testDataInlineRawOutput() throws Exception { // Standard Test A.4.4.3
        String xml =  
          "<wps:Execute service='WPS' version='1.0.0' xmlns:wps='http://www.opengis.net/wps/1.0.0' " + 
              "xmlns:ows='http://www.opengis.net/ows/1.1'>" + 
            "<ows:Identifier>gt:buffer</ows:Identifier>" + 
             "<wps:DataInputs>" + 
                "<wps:Input>" + 
                    "<ows:Identifier>geom1</ows:Identifier>" + 
                    "<wps:Data>" +
                      "<wps:ComplexData>" + 
                        "<gml:Polygon xmlns:gml='http://www.opengis.net/gml'>" +
                          "<gml:exterior>" + 
                            "<gml:LinearRing>" + 
                              "<gml:coordinates>1 1 2 1 2 2 1 2 1 1</gml:coordinates>" + 
                            "</gml:LinearRing>" + 
                          "</gml:exterior>" + 
                        "</gml:Polygon>" +
                      "</wps:ComplexData>" + 
                    "</wps:Data>" +     
                "</wps:Input>" + 
                "<wps:Input>" + 
                   "<ows:Identifier>buffer</ows:Identifier>" + 
                   "<wps:Data>" + 
                     "<wps:LiteralData>1</wps:LiteralData>" + 
                   "</wps:Data>" + 
                "</wps:Input>" + 
           "</wps:DataInputs>" +
           "<wps:ResponseForm>" + 
           "    <wps:RawDataOutput>" + 
           "        <ows:Identifier>result</ows:Identifier>" + 
           "    </wps:RawDataOutput>" + 
           "  </wps:ResponseForm>" +
           "</wps:Execute>";
        
        Document d = postAsDOM( "wps", xml );
        checkValidationErrors(d, new GMLConfiguration());
        
        assertEquals( "gml:Polygon", d.getDocumentElement().getNodeName() );
    }
    
    public void testWKTInlineRawOutput() throws Exception { // Standard Test A.4.4.3
        String xml =  
          "<wps:Execute service='WPS' version='1.0.0' xmlns:wps='http://www.opengis.net/wps/1.0.0' " + 
              "xmlns:ows='http://www.opengis.net/ows/1.1'>" + 
            "<ows:Identifier>gt:buffer</ows:Identifier>" + 
             "<wps:DataInputs>" + 
                "<wps:Input>" + 
                    "<ows:Identifier>geom1</ows:Identifier>" + 
                    "<wps:Data>" +
                      "<wps:ComplexData mimeType=\"application/wkt\">" +
                        "<![CDATA[POLYGON((1 1, 2 1, 2 2, 1 2, 1 1))]]>" +
                      "</wps:ComplexData>" + 
                    "</wps:Data>" +     
                "</wps:Input>" + 
                "<wps:Input>" + 
                   "<ows:Identifier>buffer</ows:Identifier>" + 
                   "<wps:Data>" + 
                     "<wps:LiteralData>1</wps:LiteralData>" + 
                   "</wps:Data>" + 
                "</wps:Input>" + 
           "</wps:DataInputs>" +
           "<wps:ResponseForm>" + 
           "    <wps:RawDataOutput mimeType=\"application/wkt\">" + 
           "        <ows:Identifier>result</ows:Identifier>" + 
           "    </wps:RawDataOutput>" + 
           "  </wps:ResponseForm>" +
           "</wps:Execute>";
        
        // print(dom(new StringInputStream("<?xml version=\"1.0\" encoding=\"UTF-16\"?>\n" + xml)));
        
        MockHttpServletResponse response = postAsServletResponse( "wps", xml );
        System.out.println(response.getOutputStreamContent());
        assertEquals("application/wkt", response.getContentType());
        Geometry g = new WKTReader().read(response.getOutputStreamContent());
        assertTrue(g instanceof Polygon);
    }
    
    public void testWKTInlineKVPRawOutput() throws Exception {
        String request = "wps?service=WPS&version=1.0.0&request=Execute&Identifier=gt:buffer" +
        		"&DataInputs=" + urlEncode("geom1=POLYGON((1 1, 2 1, 2 2, 1 2, 1 1))@mimetype=application/wkt;buffer=1") +
        		"&RawDataOutput=" + urlEncode("result=@mimetype=application/wkt");
        MockHttpServletResponse response = getAsServletResponse(request);
        // System.out.println(response.getOutputStreamContent());
        assertEquals("application/wkt", response.getContentType());
        Geometry g = new WKTReader().read(response.getOutputStreamContent());
        assertTrue(g instanceof Polygon);
    }


    public void testFeatureCollectionInline() throws Exception { // Standard Test A.4.4.2, A.4.4.4
        String xml = "<wps:Execute service='WPS' version='1.0.0' xmlns:wps='http://www.opengis.net/wps/1.0.0' " + 
              "xmlns:ows='http://www.opengis.net/ows/1.1'>" + 
              "<ows:Identifier>gt:BufferFeatureCollection</ows:Identifier>" + 
               "<wps:DataInputs>" + 
                  "<wps:Input>" + 
                      "<ows:Identifier>features</ows:Identifier>" + 
                      "<wps:Data>" +
                        "<wps:ComplexData>" + 
                             readFileIntoString("states-FeatureCollection.xml") + 
                        "</wps:ComplexData>" + 
                      "</wps:Data>" +     
                  "</wps:Input>" + 
                  "<wps:Input>" + 
                     "<ows:Identifier>buffer</ows:Identifier>" + 
                     "<wps:Data>" + 
                       "<wps:LiteralData>10</wps:LiteralData>" + 
                     "</wps:Data>" + 
                  "</wps:Input>" + 
                 "</wps:DataInputs>" +
                 "<wps:ResponseForm>" +  
                   "<wps:ResponseDocument storeExecuteResponse='false'>" + 
                     "<wps:Output>" +
                       "<ows:Identifier>result</ows:Identifier>" +
                     "</wps:Output>" + 
                   "</wps:ResponseDocument>" +
                 "</wps:ResponseForm>" + 
               "</wps:Execute>";
        
        Document d = postAsDOM( "wps", xml );
        // print(d);
        // checkValidationErrors(d);
        
        assertEquals( "wps:ExecuteResponse", d.getDocumentElement().getNodeName() );
        
        assertXpathExists( "/wps:ExecuteResponse/wps:Status/wps:ProcessSucceeded", d);
        assertXpathExists( 
            "/wps:ExecuteResponse/wps:ProcessOutputs/wps:Output/wps:Data/wps:ComplexData/wfs:FeatureCollection", d);
    }
    
    public void testFeatureCollectionInlineBoundedBy() throws Exception { // Standard Test A.4.4.2, A.4.4.4
        String xml = "<wps:Execute service='WPS' version='1.0.0' xmlns:wps='http://www.opengis.net/wps/1.0.0' " + 
              "xmlns:ows='http://www.opengis.net/ows/1.1'>" + 
              "<ows:Identifier>gt:BufferFeatureCollection</ows:Identifier>" + 
               "<wps:DataInputs>" + 
                  "<wps:Input>" + 
                      "<ows:Identifier>features</ows:Identifier>" + 
                      "<wps:Data>" +
                        "<wps:ComplexData mimeType=\"text/xml; subtype=wfs-collection/1.0\">" + 
                             readFileIntoString("restricted-FeatureCollection.xml") + 
                        "</wps:ComplexData>" + 
                      "</wps:Data>" +     
                  "</wps:Input>" + 
                  "<wps:Input>" + 
                     "<ows:Identifier>buffer</ows:Identifier>" + 
                     "<wps:Data>" + 
                       "<wps:LiteralData>1000</wps:LiteralData>" + 
                     "</wps:Data>" + 
                  "</wps:Input>" + 
                 "</wps:DataInputs>" +
                 "<wps:ResponseForm>" +  
                   "<wps:ResponseDocument storeExecuteResponse='false'>" + 
                     "<wps:Output>" +
                       "<ows:Identifier>result</ows:Identifier>" +
                     "</wps:Output>" + 
                   "</wps:ResponseDocument>" +
                 "</wps:ResponseForm>" + 
               "</wps:Execute>";
        
        Document d = postAsDOM( "wps", xml );
        // print(d);
        // checkValidationErrors(d);
        
        assertEquals( "wps:ExecuteResponse", d.getDocumentElement().getNodeName() );
        
        assertXpathExists( "/wps:ExecuteResponse/wps:Status/wps:ProcessSucceeded", d);
        assertXpathExists( 
            "/wps:ExecuteResponse/wps:ProcessOutputs/wps:Output/wps:Data/wps:ComplexData/wfs:FeatureCollection", d);
        assertXpathEvaluatesTo("0", "count(//feature:boundedBy)", d);
    }
    
    public void testFeatureCollectionInlineKVP() throws Exception { 
        String request = "wps?service=WPS&version=1.0.0&request=Execute&Identifier=gt:BufferFeatureCollection" +
            "&DataInputs=" + urlEncode("features=" + readFileIntoString("states-FeatureCollection.xml") + "@mimetype=application/wfs-collection-1.1;buffer=10") +
            "&ResponseDocument=" + urlEncode("result");
        
        Document d = getAsDOM(request);
        // print(d);
        // checkValidationErrors(d);
        
        assertEquals( "wps:ExecuteResponse", d.getDocumentElement().getNodeName() );
        
        assertXpathExists( "/wps:ExecuteResponse/wps:Status/wps:ProcessSucceeded", d);
        assertXpathExists( 
            "/wps:ExecuteResponse/wps:ProcessOutputs/wps:Output/wps:Data/wps:ComplexData/wfs:FeatureCollection", d);
    }
    
    public void testFeatureCollectionFileReference() throws Exception { // Standard Test A.4.4.2, A.4.4.4
        URL collectionURL = getClass().getResource("states-FeatureCollection.xml");
        String xml = "<wps:Execute service='WPS' version='1.0.0' xmlns:wps='http://www.opengis.net/wps/1.0.0' xmlns:xlink=\"http://www.w3.org/1999/xlink\" " + 
              "xmlns:ows='http://www.opengis.net/ows/1.1'>" + 
              "<ows:Identifier>gt:BufferFeatureCollection</ows:Identifier>" + 
               "<wps:DataInputs>" + 
                  "<wps:Input>" + 
                      "<ows:Identifier>features</ows:Identifier>" + 
                      "  <wps:Reference mimeType=\"text/xml; subtype=wfs-collection/1.1\" " +
                      "xlink:href=\"" + collectionURL.toExternalForm() + "\"/>\n" + 
                  "</wps:Input>" + 
                  "<wps:Input>" + 
                     "<ows:Identifier>buffer</ows:Identifier>" + 
                     "<wps:Data>" + 
                       "<wps:LiteralData>10</wps:LiteralData>" + 
                     "</wps:Data>" + 
                  "</wps:Input>" + 
                 "</wps:DataInputs>" +
                 "<wps:ResponseForm>" +  
                   "<wps:ResponseDocument storeExecuteResponse='false'>" + 
                     "<wps:Output>" +
                       "<ows:Identifier>result</ows:Identifier>" +
                     "</wps:Output>" + 
                   "</wps:ResponseDocument>" +
                 "</wps:ResponseForm>" + 
               "</wps:Execute>";
        
        Document d = postAsDOM( "wps", xml );
        // print(d);
        // checkValidationErrors(d);
        
        assertEquals( "wps:ExecuteResponse", d.getDocumentElement().getNodeName() );
        
        assertXpathExists( "/wps:ExecuteResponse/wps:Status/wps:ProcessSucceeded", d);
        assertXpathExists( 
            "/wps:ExecuteResponse/wps:ProcessOutputs/wps:Output/wps:Data/wps:ComplexData/wfs:FeatureCollection", d);
    }
    
    public void testFeatureCollectionFileReferenceKVP() throws Exception { 
        URL collectionURL = getClass().getResource("states-FeatureCollection.xml");
        String request = "wps?service=WPS&version=1.0.0&request=Execute&Identifier=gt:BufferFeatureCollection" +
            "&DataInputs=" + urlEncode("features=@mimetype=application/wfs-collection-1.1@xlink:href=" 
                    + collectionURL.toExternalForm() + ";buffer=10") + "&ResponseDocument=" + urlEncode("result");
        
        Document d = getAsDOM(request);
        // print(d);
        // checkValidationErrors(d);
        
        assertEquals( "wps:ExecuteResponse", d.getDocumentElement().getNodeName() );
        
        assertXpathExists( "/wps:ExecuteResponse/wps:Status/wps:ProcessSucceeded", d);
        assertXpathExists( 
            "/wps:ExecuteResponse/wps:ProcessOutputs/wps:Output/wps:Data/wps:ComplexData/wfs:FeatureCollection", d);
    }
    
    public void testInlineGeoJSON() throws Exception {
        String xml = "<wps:Execute service='WPS' version='1.0.0' xmlns:wps='http://www.opengis.net/wps/1.0.0' " + 
        "xmlns:ows='http://www.opengis.net/ows/1.1'>" + 
        "<ows:Identifier>gt:BufferFeatureCollection</ows:Identifier>" + 
         "<wps:DataInputs>" + 
            "<wps:Input>" + 
                "<ows:Identifier>features</ows:Identifier>" + 
                "<wps:Data>" +
                  "<wps:ComplexData mimeType=\"application/json\"><![CDATA[" + 
                       readFileIntoString("states-FeatureCollection.json") + 
                  "]]></wps:ComplexData>" + 
                "</wps:Data>" +     
            "</wps:Input>" + 
            "<wps:Input>" + 
               "<ows:Identifier>buffer</ows:Identifier>" + 
               "<wps:Data>" + 
                 "<wps:LiteralData>10</wps:LiteralData>" + 
               "</wps:Data>" + 
            "</wps:Input>" + 
           "</wps:DataInputs>" +
           "<wps:ResponseForm>" +  
             "<wps:RawDataOutput mimeType=\"application/json\">" + 
                 "<ows:Identifier>result</ows:Identifier>" +
             "</wps:RawDataOutput>" +
           "</wps:ResponseForm>" + 
         "</wps:Execute>";
  
		MockHttpServletResponse r = postAsServletResponse("wps", xml);
		assertEquals("application/json", r.getContentType());
		// System.out.println(r.getOutputStreamContent());
		FeatureCollection fc = new FeatureJSON().readFeatureCollection(r.getOutputStreamContent());
		assertEquals(2, fc.size());
		
    }
    
    public void testShapeZip() throws Exception {
        String xml = "<wps:Execute service='WPS' version='1.0.0' xmlns:xlink=\"http://www.w3.org/1999/xlink\" " +
        		"xmlns:wps='http://www.opengis.net/wps/1.0.0' xmlns:wfs='http://www.opengis.net/wfs' " + 
        "xmlns:ows='http://www.opengis.net/ows/1.1'>" + 
        "<ows:Identifier>gt:BufferFeatureCollection</ows:Identifier>" + 
         "<wps:DataInputs>" + 
         "    <wps:Input>\n" + 
                "<ows:Identifier>features</ows:Identifier>" + 
                "<wps:Data>" +
                  "<wps:ComplexData>" + 
                      readFileIntoString("states-FeatureCollection.xml") + 
                  "</wps:ComplexData>" + 
                "</wps:Data>" +
            "</wps:Input>" + 
            "<wps:Input>" + 
               "<ows:Identifier>buffer</ows:Identifier>" + 
               "<wps:Data>" + 
                 "<wps:LiteralData>10</wps:LiteralData>" + 
               "</wps:Data>" + 
            "</wps:Input>" + 
           "</wps:DataInputs>" +
           "<wps:ResponseForm>" +  
             "<wps:RawDataOutput mimeType=\"application/zip\">" + 
                 "<ows:Identifier>result</ows:Identifier>" +
             "</wps:RawDataOutput>" +
           "</wps:ResponseForm>" + 
         "</wps:Execute>";
  
        MockHttpServletResponse r = postAsServletResponse("wps", xml);
        assertEquals("application/zip", r.getContentType());
        checkShapefileIntegrity(new String[] {"states"}, getBinaryInputStream(r));
    }
    
    public void testPlainAddition() throws Exception { // Standard Test A.4.4.3
        String xml = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\" ?>\r\n" + 
            "<wps:Execute service=\"WPS\" version=\"1.0.0\"\r\n" + 
            "        xmlns:wps=\"http://www.opengis.net/wps/1.0.0\" xmlns:ows=\"http://www.opengis.net/ows/1.1\"\r\n" + 
            "        xmlns:xlink=\"http://www.w3.org/1999/xlink\">\r\n" + 
            "        <ows:Identifier>gt:DoubleAddition</ows:Identifier>\r\n" + 
            "        <wps:DataInputs>\r\n" + 
            "                <wps:Input>\r\n" + 
            "                        <ows:Identifier>input_a</ows:Identifier>\r\n" + 
            "                        <wps:Data>\r\n" + 
            "                                <wps:LiteralData>7</wps:LiteralData>\r\n" + 
            "                        </wps:Data>\r\n" + 
            "                </wps:Input>\r\n" + 
            "                <wps:Input>\r\n" + 
            "                        <ows:Identifier>input_b</ows:Identifier>\r\n" + 
            "                        <wps:Data>\r\n" + 
            "                                <wps:LiteralData>7</wps:LiteralData>\r\n" + 
            "                        </wps:Data>\r\n" + 
            "                </wps:Input>\r\n" + 
            "        </wps:DataInputs>\r\n" + 
            "        <wps:ResponseForm>\r\n" + 
            "                <wps:RawDataOutput>\r\n" + 
            "                        <ows:Identifier>result</ows:Identifier>\r\n" + 
            "                </wps:RawDataOutput>\r\n" + 
            "        </wps:ResponseForm>\r\n" + 
            "</wps:Execute>";
        
         MockHttpServletResponse response = postAsServletResponse(root(), xml);
         assertEquals("text/plain", response.getContentType());
         assertEquals("14.0", response.getOutputStreamContent());
    }
    
    public void testPlainAdditionKVP() throws Exception { // Standard Test A.4.4.3
        String request = "wps?service=WPS&version=1.0.0&request=Execute&Identifier=gt:DoubleAddition" +
        "&DataInputs=" + urlEncode("input_a=7;input_b=7") + "&RawDataOutput=result";
        
         MockHttpServletResponse response = getAsServletResponse(request);
         assertEquals("text/plain", response.getContentType());
         assertEquals("14.0", response.getOutputStreamContent());
    }
    
    /**
     * Tests a process execution with a BoudingBox as the output and check internal layer
     * request handling as well
     */
    public void testBoundsPost() throws Exception {
        String request = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + 
        		"<wps:Execute version=\"1.0.0\" service=\"WPS\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns=\"http://www.opengis.net/wps/1.0.0\" xmlns:wfs=\"http://www.opengis.net/wfs\" xmlns:wps=\"http://www.opengis.net/wps/1.0.0\" xmlns:ows=\"http://www.opengis.net/ows/1.1\" xmlns:gml=\"http://www.opengis.net/gml\" xmlns:ogc=\"http://www.opengis.net/ogc\" xmlns:wcs=\"http://www.opengis.net/wcs/1.1.1\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" xsi:schemaLocation=\"http://www.opengis.net/wps/1.0.0 http://schemas.opengis.net/wps/1.0.0/wpsAll.xsd\">\n" + 
        		"  <ows:Identifier>gs:Bounds</ows:Identifier>\n" + 
        		"  <wps:DataInputs>\n" + 
        		"    <wps:Input>\n" + 
        		"      <ows:Identifier>features</ows:Identifier>\n" + 
        		"      <wps:Reference mimeType=\"text/xml; subtype=wfs-collection/1.0\" xlink:href=\"http://geoserver/wfs\" method=\"POST\">\n" + 
        		"        <wps:Body>\n" + 
        		"          <wfs:GetFeature service=\"WFS\" version=\"1.0.0\">\n" + 
        		"            <wfs:Query typeName=\"cite:Streams\"/>\n" + 
        		"          </wfs:GetFeature>\n" + 
        		"        </wps:Body>\n" + 
        		"      </wps:Reference>\n" + 
        		"    </wps:Input>\n" + 
        		"  </wps:DataInputs>\n" + 
        		"  <wps:ResponseForm>\n" + 
        		"    <wps:RawDataOutput>\n" + 
        		"      <ows:Identifier>bounds</ows:Identifier>\n" + 
        		"    </wps:RawDataOutput>\n" + 
        		"  </wps:ResponseForm>\n" + 
        		"</wps:Execute>";
        
        Document dom = postAsDOM(root(), request);
        // print(dom);
        
        assertXpathEvaluatesTo("-4.0E-4 -0.0024", "/ows:BoundingBox/ows:LowerCorner", dom);
        assertXpathEvaluatesTo("0.0036 0.0024", "/ows:BoundingBox/ows:UpperCorner", dom);
    }
    
    /**
     * Tests a process execution with a BoudingBox as the output and check internal layer
     * request handling as well
     */
    public void testBoundsGet() throws Exception {
        String request = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + 
                "<wps:Execute version=\"1.0.0\" service=\"WPS\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns=\"http://www.opengis.net/wps/1.0.0\" xmlns:wfs=\"http://www.opengis.net/wfs\" xmlns:wps=\"http://www.opengis.net/wps/1.0.0\" xmlns:ows=\"http://www.opengis.net/ows/1.1\" xmlns:gml=\"http://www.opengis.net/gml\" xmlns:ogc=\"http://www.opengis.net/ogc\" xmlns:wcs=\"http://www.opengis.net/wcs/1.1.1\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" xsi:schemaLocation=\"http://www.opengis.net/wps/1.0.0 http://schemas.opengis.net/wps/1.0.0/wpsAll.xsd\">\n" + 
                "  <ows:Identifier>gs:Bounds</ows:Identifier>\n" + 
                "  <wps:DataInputs>\n" + 
                "    <wps:Input>\n" + 
                "      <ows:Identifier>features</ows:Identifier>\n" + 
                "      <wps:Reference mimeType=\"text/xml; subtype=wfs-collection/1.0\" xlink:href=\"http://geoserver/wfs?service=WFS&amp;request=GetFeature&amp;typename=cite:Streams\" method=\"GET\"/>\n" + 
                "    </wps:Input>\n" + 
                "  </wps:DataInputs>\n" + 
                "  <wps:ResponseForm>\n" + 
                "    <wps:RawDataOutput>\n" + 
                "      <ows:Identifier>bounds</ows:Identifier>\n" + 
                "    </wps:RawDataOutput>\n" + 
                "  </wps:ResponseForm>\n" + 
                "</wps:Execute>";
        
        Document dom = postAsDOM(root(), request);
        print(dom);
        
        assertXpathEvaluatesTo("-4.0E-4 -0.0024", "/ows:BoundingBox/ows:LowerCorner", dom);
        assertXpathEvaluatesTo("0.0036 0.0024", "/ows:BoundingBox/ows:UpperCorner", dom);
    }
    
    /**
     * Tests a process grabbing a remote layer 
     */
    public void testRemoteGetWFS10Layer() throws Exception {
        String request = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + 
        "<wps:Execute version=\"1.0.0\" service=\"WPS\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns=\"http://www.opengis.net/wps/1.0.0\" xmlns:wfs=\"http://www.opengis.net/wfs\" xmlns:wps=\"http://www.opengis.net/wps/1.0.0\" xmlns:ows=\"http://www.opengis.net/ows/1.1\" xmlns:gml=\"http://www.opengis.net/gml\" xmlns:ogc=\"http://www.opengis.net/ogc\" xmlns:wcs=\"http://www.opengis.net/wcs/1.1.1\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" xsi:schemaLocation=\"http://www.opengis.net/wps/1.0.0 http://schemas.opengis.net/wps/1.0.0/wpsAll.xsd\">\n" + 
        "  <ows:Identifier>gs:Bounds</ows:Identifier>\n" + 
        "  <wps:DataInputs>\n" + 
        "    <wps:Input>\n" + 
        "      <ows:Identifier>features</ows:Identifier>\n" + 
        "      <wps:Reference mimeType=\"text/xml; subtype=wfs-collection/1.0\" " +
        " xlink:href=\"http://demo.opengeo.org/geoserver/wfs?request=GetFeature&amp;service=wfs&amp;version=1.0.0&amp;typeName=topp:states&amp;featureid=states.1\" />\n" + 
        "    </wps:Input>\n" + 
        "  </wps:DataInputs>\n" + 
        "  <wps:ResponseForm>\n" + 
        "    <wps:RawDataOutput>\n" + 
        "      <ows:Identifier>bounds</ows:Identifier>\n" + 
        "    </wps:RawDataOutput>\n" + 
        "  </wps:ResponseForm>\n" + 
        "</wps:Execute>";
        
        executeState1BoundsTest(request, "GET WFS 1.0");
    }
    
    /**
     * Tests a process grabbing a remote layer 
     */
    public void testRemotePostWFS10Layer() throws Exception {
        String request = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + 
        "<wps:Execute version=\"1.0.0\" service=\"WPS\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns=\"http://www.opengis.net/wps/1.0.0\" xmlns:wfs=\"http://www.opengis.net/wfs\" xmlns:wps=\"http://www.opengis.net/wps/1.0.0\" xmlns:ows=\"http://www.opengis.net/ows/1.1\" xmlns:gml=\"http://www.opengis.net/gml\" xmlns:ogc=\"http://www.opengis.net/ogc\" xmlns:wcs=\"http://www.opengis.net/wcs/1.1.1\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" xsi:schemaLocation=\"http://www.opengis.net/wps/1.0.0 http://schemas.opengis.net/wps/1.0.0/wpsAll.xsd\">\n" + 
        "  <ows:Identifier>gs:Bounds</ows:Identifier>\n" + 
        "  <wps:DataInputs>\n" + 
        "    <wps:Input>\n" + 
        "      <ows:Identifier>features</ows:Identifier>\n" + 
        "      <wps:Reference mimeType=\"text/xml; subtype=wfs-collection/1.0\" " +
        " xlink:href=\"http://demo.opengeo.org/geoserver/wfs\" method=\"POST\">\n" +
        "         <wps:Body>\n" +
        "<![CDATA[<wfs:GetFeature service=\"WFS\" version=\"1.0.0\"\n" + 
        "  outputFormat=\"GML2\"\n" + 
        "  xmlns:topp=\"http://www.openplans.org/topp\"\n" + 
        "  xmlns:wfs=\"http://www.opengis.net/wfs\"\n" + 
        "  xmlns:ogc=\"http://www.opengis.net/ogc\"\n" + 
        "  xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" + 
        "  xsi:schemaLocation=\"http://www.opengis.net/wfs\n" + 
        "                      http://schemas.opengis.net/wfs/1.0.0/WFS-basic.xsd\">\n" + 
        "  <wfs:Query typeName=\"topp:states\">\n" + 
        "    <ogc:Filter>\n" + 
        "       <ogc:FeatureId fid=\"states.1\"/>\n" + 
        "    </ogc:Filter>\n" + 
        "    </wfs:Query>\n" + 
        "</wfs:GetFeature>]]>" +
        "         </wps:Body>\n" +
        "      </wps:Reference>\n" +
        "    </wps:Input>\n" + 
        "  </wps:DataInputs>\n" + 
        "  <wps:ResponseForm>\n" + 
        "    <wps:RawDataOutput>\n" + 
        "      <ows:Identifier>bounds</ows:Identifier>\n" + 
        "    </wps:RawDataOutput>\n" + 
        "  </wps:ResponseForm>\n" + 
        "</wps:Execute>";
        
        executeState1BoundsTest(request, "POST WFS 1.0");
    }
    
    /**
     * Tests a process grabbing a remote layer 
     */
    public void testRemoteBodyReferencePostWFS10Layer() throws Exception {
        URL getFeatureURL = getClass().getResource("getFeature.xml");
        String request = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + 
        "<wps:Execute version=\"1.0.0\" service=\"WPS\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns=\"http://www.opengis.net/wps/1.0.0\" xmlns:wfs=\"http://www.opengis.net/wfs\" xmlns:wps=\"http://www.opengis.net/wps/1.0.0\" xmlns:ows=\"http://www.opengis.net/ows/1.1\" xmlns:gml=\"http://www.opengis.net/gml\" xmlns:ogc=\"http://www.opengis.net/ogc\" xmlns:wcs=\"http://www.opengis.net/wcs/1.1.1\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" xsi:schemaLocation=\"http://www.opengis.net/wps/1.0.0 http://schemas.opengis.net/wps/1.0.0/wpsAll.xsd\">\n" + 
        "  <ows:Identifier>gs:Bounds</ows:Identifier>\n" + 
        "  <wps:DataInputs>\n" + 
        "    <wps:Input>\n" + 
        "      <ows:Identifier>features</ows:Identifier>\n" + 
        "      <wps:Reference mimeType=\"text/xml; subtype=wfs-collection/1.0\" " +
        " xlink:href=\"http://demo.opengeo.org/geoserver/wfs\" method=\"POST\">\n" +
        "         <wps:BodyReference xlink:href=\"" + getFeatureURL.toExternalForm() + "\"/>\n" +
        "      </wps:Reference>\n" +
        "    </wps:Input>\n" + 
        "  </wps:DataInputs>\n" + 
        "  <wps:ResponseForm>\n" + 
        "    <wps:RawDataOutput>\n" + 
        "      <ows:Identifier>bounds</ows:Identifier>\n" + 
        "    </wps:RawDataOutput>\n" + 
        "  </wps:ResponseForm>\n" + 
        "</wps:Execute>";
        
        executeState1BoundsTest(request, "POST WFS 1.0");
    }
    
    /**
     * Tests a process grabbing a remote layer 
     */
    public void testRemoteGetWFS11Layer() throws Exception {
        String request = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + 
        "<wps:Execute version=\"1.0.0\" service=\"WPS\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns=\"http://www.opengis.net/wps/1.0.0\" xmlns:wfs=\"http://www.opengis.net/wfs\" xmlns:wps=\"http://www.opengis.net/wps/1.0.0\" xmlns:ows=\"http://www.opengis.net/ows/1.1\" xmlns:gml=\"http://www.opengis.net/gml\" xmlns:ogc=\"http://www.opengis.net/ogc\" xmlns:wcs=\"http://www.opengis.net/wcs/1.1.1\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" xsi:schemaLocation=\"http://www.opengis.net/wps/1.0.0 http://schemas.opengis.net/wps/1.0.0/wpsAll.xsd\">\n" + 
        "  <ows:Identifier>gs:Bounds</ows:Identifier>\n" + 
        "  <wps:DataInputs>\n" + 
        "    <wps:Input>\n" + 
        "      <ows:Identifier>features</ows:Identifier>\n" + 
        "      <wps:Reference mimeType=\"text/xml; subtype=wfs-collection/1.1\" " +
        " xlink:href=\"http://demo.opengeo.org/geoserver/wfs?request=GetFeature&amp;service=wfs&amp;version=1.1&amp;typeName=topp:states&amp;featureid=states.1\" />\n" + 
        "    </wps:Input>\n" + 
        "  </wps:DataInputs>\n" + 
        "  <wps:ResponseForm>\n" + 
        "    <wps:RawDataOutput>\n" + 
        "      <ows:Identifier>bounds</ows:Identifier>\n" + 
        "    </wps:RawDataOutput>\n" + 
        "  </wps:ResponseForm>\n" + 
        "</wps:Execute>";
        // System.out.println(request);
        
        executeState1BoundsTest(request, "GET WFS 1.1");
    }
    
    /**
     * Tests a process grabbing a remote layer 
     */
    public void testRemotePostWFS11Layer() throws Exception {
        String request = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + 
        "<wps:Execute version=\"1.0.0\" service=\"WPS\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns=\"http://www.opengis.net/wps/1.0.0\" xmlns:wfs=\"http://www.opengis.net/wfs\" xmlns:wps=\"http://www.opengis.net/wps/1.0.0\" xmlns:ows=\"http://www.opengis.net/ows/1.1\" xmlns:gml=\"http://www.opengis.net/gml\" xmlns:ogc=\"http://www.opengis.net/ogc\" xmlns:wcs=\"http://www.opengis.net/wcs/1.1.1\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" xsi:schemaLocation=\"http://www.opengis.net/wps/1.0.0 http://schemas.opengis.net/wps/1.0.0/wpsAll.xsd\">\n" + 
        "  <ows:Identifier>gs:Bounds</ows:Identifier>\n" + 
        "  <wps:DataInputs>\n" + 
        "    <wps:Input>\n" + 
        "      <ows:Identifier>features</ows:Identifier>\n" + 
        "      <wps:Reference mimeType=\"text/xml; subtype=wfs-collection/1.1\" " +
        " xlink:href=\"http://demo.opengeo.org/geoserver/wfs\" method=\"POST\">\n" +
        "         <wps:Body>\n" +
        "<![CDATA[<wfs:GetFeature service=\"WFS\" version=\"1.1.0\"\n" + 
        "  xmlns:topp=\"http://www.openplans.org/topp\"\n" + 
        "  xmlns:wfs=\"http://www.opengis.net/wfs\"\n" + 
        "  xmlns:ogc=\"http://www.opengis.net/ogc\"\n" + 
        "  xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" + 
        "  xsi:schemaLocation=\"http://www.opengis.net/wfs\n" + 
        "                      http://schemas.opengis.net/wfs/1.1.0/wfs.xsd\">\n" + 
        "  <wfs:Query typeName=\"topp:states\">\n" + 
        "    <ogc:Filter>\n" + 
        "       <ogc:FeatureId fid=\"states.1\"/>\n" + 
        "    </ogc:Filter>\n" + 
        "    </wfs:Query>\n" + 
        "</wfs:GetFeature>]]>" +
        "         </wps:Body>\n" +
        "      </wps:Reference>\n" +
        "    </wps:Input>\n" + 
        "  </wps:DataInputs>\n" + 
        "  <wps:ResponseForm>\n" + 
        "    <wps:RawDataOutput>\n" + 
        "      <ows:Identifier>bounds</ows:Identifier>\n" + 
        "    </wps:RawDataOutput>\n" + 
        "  </wps:ResponseForm>\n" + 
        "</wps:Execute>";
        
        executeState1BoundsTest(request, "POST WFS 1.1");
    }
    
    public void testProcessChaining() throws Exception {
    	// chain two JTS processes
    	String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + 
    			"<wps:Execute version=\"1.0.0\" service=\"WPS\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns=\"http://www.opengis.net/wps/1.0.0\" xmlns:wfs=\"http://www.opengis.net/wfs\" xmlns:wps=\"http://www.opengis.net/wps/1.0.0\" xmlns:ows=\"http://www.opengis.net/ows/1.1\" xmlns:gml=\"http://www.opengis.net/gml\" xmlns:ogc=\"http://www.opengis.net/ogc\" xmlns:wcs=\"http://www.opengis.net/wcs/1.1.1\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" xsi:schemaLocation=\"http://www.opengis.net/wps/1.0.0 http://schemas.opengis.net/wps/1.0.0/wpsAll.xsd\">\n" + 
    			"  <ows:Identifier>JTS:area</ows:Identifier>\n" + 
    			"  <wps:DataInputs>\n" + 
    			"    <wps:Input>\n" + 
    			"      <ows:Identifier>geom</ows:Identifier>\n" + 
    			"      <wps:Reference mimeType=\"text/xml; subtype=gml/3.1.1\" xlink:href=\"http://geoserver/wps\" method=\"POST\">\n" + 
    			"        <wps:Execute>\n" + 
    			"          <ows:Identifier>JTS:buffer</ows:Identifier>\n" + 
    			"          <wps:DataInputs>\n" + 
    			"            <wps:Input>\n" + 
    			"              <ows:Identifier>geom</ows:Identifier>\n" + 
    			"              <wps:Data>\n" + 
    			"                <wps:ComplexData mimeType=\"application/wkt\"><![CDATA[POINT(0 0)]]></wps:ComplexData>\n" + 
    			"              </wps:Data>\n" + 
    			"            </wps:Input>\n" + 
    			"            <wps:Input>\n" + 
    			"              <ows:Identifier>distance</ows:Identifier>\n" + 
    			"              <wps:Data>\n" + 
    			"                <wps:LiteralData>10</wps:LiteralData>\n" + 
    			"              </wps:Data>\n" + 
    			"            </wps:Input>\n" + 
    			"          </wps:DataInputs>\n" + 
    			"          <wps:ResponseForm>\n" + 
    			"            <wps:RawDataOutput mimeType=\"text/xml; subtype=gml/3.1.1\">\n" + 
    			"              <ows:Identifier>result</ows:Identifier>\n" + 
    			"            </wps:RawDataOutput>\n" + 
    			"          </wps:ResponseForm>\n" + 
    			"        </wps:Execute>\n" + 
    			"      </wps:Reference>\n" + 
    			"    </wps:Input>\n" + 
    			"  </wps:DataInputs>\n" + 
    			"  <wps:ResponseForm>\n" + 
    			"    <wps:RawDataOutput>\n" + 
    			"      <ows:Identifier>result</ows:Identifier>\n" + 
    			"    </wps:RawDataOutput>\n" + 
    			"  </wps:ResponseForm>\n" + 
    			"</wps:Execute>";
    	
    	MockHttpServletResponse resp = postAsServletResponse(root(), xml);
    	assertEquals("text/plain", resp.getContentType());
    	// the result is inaccurate since the buffer is just a poor approximation of a circle
    	assertTrue(resp.getOutputStreamContent().matches("312\\..*"));
    }
    
    public void testProcessChainingKVP() throws Exception {
        String nested = "http://geoserver/wps?service=WPS&version=1.0.0&request=Execute&Identifier=JTS:buffer" +
        "&DataInputs=" + urlEncode("geom=POINT(0 0)@mimetype=application/wkt;distance=10") + "&RawDataOutput=result";
        String request = "wps?service=WPS&version=1.0.0&request=Execute&Identifier=JTS:area" +
        "&DataInputs=" + urlEncode("geom=@href=" + nested) + "&RawDataOutput=result";
        
        MockHttpServletResponse resp = getAsServletResponse(request);
        assertEquals("text/plain", resp.getContentType());
        // the result is inaccurate since the buffer is just a poor approximation of a circle
        assertTrue(resp.getOutputStreamContent().matches("312\\..*"));
    }
    
    /**
     * Checks the bounds process returned the expected envelope
     * @param request
     * @param id
     * @throws Exception
     */
    void executeState1BoundsTest(String request, String id) throws Exception {
        if (!RemoteOWSTestSupport.isRemoteWMSStatesAvailable(LOGGER)) {
            LOGGER.warning("Remote OWS tests disabled, skipping test with " + id + " reference source");
            return;
        }
        
        MockHttpServletResponse resp = postAsServletResponse(root(), request);
        ReferencedEnvelope re = toEnvelope(resp.getOutputStreamContent());
        assertEquals(-91.516129, re.getMinX(), 0.001);
        assertEquals(36.986771, re.getMinY(), 0.001);
        assertEquals(-87.507889, re.getMaxX(), 0.001);
        assertEquals(42.509361, re.getMaxY(), 0.001);
    }
    
    ReferencedEnvelope toEnvelope(String xml) throws Exception {
        Parser p = new Parser(new OWSConfiguration());
        Object parsed = p.parse(new ByteArrayInputStream(xml.getBytes()));
        assertTrue(parsed instanceof BoundingBoxType);
        BoundingBoxType box = (BoundingBoxType) parsed;
        
        ReferencedEnvelope re;
        if(box.getCrs() != null) {
            re = new ReferencedEnvelope(CRS.decode(box.getCrs()));
        } else {
            re = new ReferencedEnvelope();
        }
        
        re.expandToInclude((Double) box.getLowerCorner().get(0), (Double) box.getLowerCorner().get(1));
        re.expandToInclude((Double) box.getUpperCorner().get(0), (Double) box.getUpperCorner().get(1));
        return re;
    }
    
    String urlEncode(String string) throws Exception {
        return URLEncoder.encode(string, "ASCII");
    }
    
    private void checkShapefileIntegrity(String[] typeNames, final InputStream in) throws IOException {
        ZipInputStream zis = new ZipInputStream(in);
        ZipEntry entry = null;
        
        final String[] extensions = new String[] {".shp", ".shx", ".dbf", ".prj", ".cst"};
        Set names = new HashSet();
        for (String name : typeNames) {
            for (String extension : extensions) {
                names.add(name + extension);
            }
        }
        while((entry = zis.getNextEntry()) != null) {
            final String name = entry.getName();
            assertTrue("Missing " + name, names.contains(name));
            names.remove(name);
            zis.closeEntry();
        }
        zis.close();
    }
	
	/* TODO Updating of Response requests A.4.4.5 */
	
	/* TODO Language selection requests A.4.4.6 */
}
