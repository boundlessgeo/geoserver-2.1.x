/* Copyright (c) 2001 - 2010 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.wms.wms_1_3;

import static org.custommonkey.xmlunit.XMLAssert.assertXpathEvaluatesTo;
import static org.custommonkey.xmlunit.XMLUnit.newXpathEngine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import junit.framework.Test;

import org.custommonkey.xmlunit.NamespaceContext;
import org.custommonkey.xmlunit.SimpleNamespaceContext;
import org.custommonkey.xmlunit.XMLUnit;
import org.custommonkey.xmlunit.XpathEngine;
import org.geoserver.catalog.AttributionInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.StyleInfo;
import org.geoserver.config.GeoServerInfo;
import org.geoserver.data.test.MockData;
import org.geoserver.wms.WMSTestSupport;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * WMS 1.3 GetCapabilities integration tests
 * 
 * <p>
 * These tests are initialy ported from the 1.1.1 capabilities integration tests
 * </p>
 * 
 * @author Gabriel Roldan
 * 
 */
public class CapabilitiesIntegrationTest extends WMSTestSupport {

    public CapabilitiesIntegrationTest() {
        super();
    }

    /**
     * This is a READ ONLY TEST so we can use one time setup
     */
    public static Test suite() {
        return new OneTimeTestSetup(new CapabilitiesIntegrationTest());
    }

    @Override
    protected void oneTimeSetUp() throws Exception {
        super.oneTimeSetUp();
        Map<String, String> namespaces = new HashMap<String, String>();
        namespaces.put("xlink", "http://www.w3.org/1999/xlink");
        namespaces.put("xsi", "http://www.w3.org/2001/XMLSchema-instance");
        namespaces.put("wms", "http://www.opengis.net/wms");
        namespaces.put("ows", "http://www.opengis.net/ows");
        getTestData().registerNamespaces(namespaces);

        NamespaceContext ctx = new SimpleNamespaceContext(namespaces);
        XMLUnit.setXpathNamespaceContext(ctx);

        GeoServerInfo global = getGeoServer().getGlobal();
        global.setProxyBaseUrl("src/test/resources/geoserver");
        getGeoServer().save(global);
    }

    @Override
    protected void populateDataDirectory(MockData dataDirectory) throws Exception {
        super.populateDataDirectory(dataDirectory);
        dataDirectory.addWcs11Coverages();
        dataDirectory.disableDataStore(MockData.SF_PREFIX);
    }

    public void testCapabilities() throws Exception {
        Document dom = dom(get("wms?request=getCapabilities&version=1.3.0"), false);
        Element e = dom.getDocumentElement();
        assertEquals("WMS_Capabilities", e.getLocalName());
    }

    public void testGetCapsContainsNoDisabledTypes() throws Exception {

        Document doc = getAsDOM("wms?service=WMS&request=getCapabilities&version=1.3.0", true);
        // print(doc);
        assertEquals("WMS_Capabilities", doc.getDocumentElement().getNodeName());
        // see that disabled elements are disabled for good
        assertXpathEvaluatesTo("0", "count(//Name[text()='sf:PrimitiveGeoFeature'])", doc);

    }

    public void testFilteredCapabilitiesCite() throws Exception {
        Document dom = dom(get("wms?request=getCapabilities&version=1.3.0&namespace=cite"), true);
        Element e = dom.getDocumentElement();
        assertEquals("WMS_Capabilities", e.getLocalName());
        XpathEngine xpath = XMLUnit.newXpathEngine();
        assertTrue(xpath.getMatchingNodes("//wms:Layer/wms:Name[starts-with(., cite)]", dom)
                .getLength() > 0);
        assertEquals(0,
                xpath.getMatchingNodes("//wms:Layer/wms:Name[not(starts-with(., cite))]", dom)
                        .getLength());
    }

    public void testLayerCount() throws Exception {
        List<LayerInfo> layers = new ArrayList<LayerInfo>(getCatalog().getLayers());
        for (ListIterator<LayerInfo> it = layers.listIterator(); it.hasNext();) {
            LayerInfo next = it.next();
            if (!next.enabled() || next.getName().equals(MockData.GEOMETRYLESS.getLocalPart())) {
                it.remove();
            }
        }

        Document dom = dom(get("wms?request=GetCapabilities&version=1.3.0"), true);

        XpathEngine xpath = XMLUnit.newXpathEngine();
        NodeList nodeLayers = xpath.getMatchingNodes(
                "/wms:WMS_Capabilities/wms:Capability/wms:Layer/wms:Layer", dom);

        assertEquals(layers.size(), nodeLayers.getLength());
    }

    public void testWorkspaceQualified() throws Exception {
        Document dom = dom(get("cite/wms?request=getCapabilities&version=1.3.0"), true);
        Element e = dom.getDocumentElement();
        assertEquals("WMS_Capabilities", e.getLocalName());
        XpathEngine xpath = XMLUnit.newXpathEngine();
        assertTrue(xpath.getMatchingNodes("//wms:Layer/wms:Name[starts-with(., cite)]", dom)
                .getLength() > 0);
        assertEquals(0,
                xpath.getMatchingNodes("//wms:Layer/wms:Name[not(starts-with(., cite))]", dom)
                        .getLength());

        NodeList nodes = xpath.getMatchingNodes("//wms:OnlineResource", dom);
        assertTrue(nodes.getLength() > 0);
        for (int i = 0; i < nodes.getLength(); i++) {
            e = (Element) nodes.item(i);
            String attribute = e.getAttribute("xlink:href");
            assertTrue(attribute.contains("geoserver/cite/ows"));
        }

    }

    public void testLayerQualified() throws Exception {
        Document dom = dom(get("cite/Forests/wms?request=getCapabilities&version=1.3.0"), true);
        Element e = dom.getDocumentElement();
        assertEquals("WMS_Capabilities", e.getLocalName());
        XpathEngine xpath = XMLUnit.newXpathEngine();
        assertTrue(xpath
                .getMatchingNodes("//wms:Layer/wms:Name[starts-with(., cite:Forests)]", dom)
                .getLength() == 1);
        assertEquals(1, xpath.getMatchingNodes("//wms:Layer/wms:Layer", dom).getLength());

        NodeList nodes = xpath.getMatchingNodes("//wms:OnlineResource", dom);
        assertTrue(nodes.getLength() > 0);
        for (int i = 0; i < nodes.getLength(); i++) {
            e = (Element) nodes.item(i);
            String attribute = e.getAttribute("xlink:href");
            assertTrue(attribute.contains("geoserver/cite/Forests/ows"));
        }

    }

    public void testAttribution() throws Exception {
        // Uncomment the following lines if you want to use DTD validation for these tests
        // (by passing false as the second param to getAsDOM())
        // BUG: Currently, this doesn't seem to actually validate the document, although
        // 'validation' fails if the DTD is missing

        // GeoServerInfo global = getGeoServer().getGlobal();
        // global.setProxyBaseUrl("src/test/resources/geoserver");
        // getGeoServer().save(global);

        Document doc = getAsDOM("wms?service=WMS&request=getCapabilities&version=1.3.0", true);
        assertXpathEvaluatesTo("0", "count(//wms:Attribution)", doc);

        // Add attribution to one of the layers
        LayerInfo points = getCatalog().getLayerByName(MockData.POINTS.getLocalPart());
        AttributionInfo attr = points.getAttribution();

        attr.setTitle("Point Provider");
        getCatalog().save(points);

        doc = getAsDOM("wms?service=WMS&request=getCapabilities&version=1.3.0", true);
        assertXpathEvaluatesTo("1", "count(//wms:Attribution)", doc);
        assertXpathEvaluatesTo("1", "count(//wms:Attribution/wms:Title)", doc);

        // Add href to same layer
        attr = points.getAttribution();
        attr.setHref("http://example.com/points/provider");
        getCatalog().save(points);

        doc = getAsDOM("wms?service=WMS&request=getCapabilities&version=1.3.0", true);
        // print(doc);
        assertXpathEvaluatesTo("1", "count(//wms:Attribution)", doc);
        assertXpathEvaluatesTo("1", "count(//wms:Attribution/wms:Title)", doc);
        assertXpathEvaluatesTo("1", "count(//wms:Attribution/wms:OnlineResource)", doc);

        // Add logo to same layer
        attr = points.getAttribution();
        attr.setLogoURL("http://example.com/points/logo");
        attr.setLogoType("image/logo");
        attr.setLogoHeight(50);
        attr.setLogoWidth(50);
        getCatalog().save(points);

        doc = getAsDOM("wms?service=WMS&request=getCapabilities&version=1.3.0", true);
        // print(doc);
        assertXpathEvaluatesTo("1", "count(//wms:Attribution)", doc);
        assertXpathEvaluatesTo("1", "count(//wms:Attribution/wms:Title)", doc);
        assertXpathEvaluatesTo("1", "count(//wms:Attribution/wms:LogoURL)", doc);
    }

    public void testAlternateStyles() throws Exception {
        // add an alternate style to Fifteen
        StyleInfo pointStyle = getCatalog().getStyleByName("point");
        LayerInfo layer = getCatalog().getLayerByName("Fifteen");
        layer.getStyles().add(pointStyle);
        getCatalog().save(layer);

        Document doc = getAsDOM("wms?service=WMS&request=getCapabilities&version=1.3.0", true);
        // print(doc);

        assertXpathEvaluatesTo("1", "count(//wms:Layer[wms:Name='cdf:Fifteen'])", doc);
        assertXpathEvaluatesTo("2", "count(//wms:Layer[wms:Name='cdf:Fifteen']/wms:Style)", doc);

        XpathEngine xpath = newXpathEngine();
        String href = xpath
                .evaluate(
                        "//wms:Layer[wms:Name='cdf:Fifteen']/wms:Style[wms:Name='Default']/wms:LegendURL/wms:OnlineResource/@xlink:href",
                        doc);
        assertTrue(href.contains("GetLegendGraphic"));
        assertTrue(href.contains("layer=Fifteen"));
        assertFalse(href.contains("style"));
        href = xpath
                .evaluate(
                        "//wms:Layer[wms:Name='cdf:Fifteen']/wms:Style[wms:Name='point']/wms:LegendURL/wms:OnlineResource/@xlink:href",
                        doc);
        assertTrue(href.contains("GetLegendGraphic"));
        assertTrue(href.contains("layer=Fifteen"));
        assertTrue(href.contains("style=point"));
    }
}
