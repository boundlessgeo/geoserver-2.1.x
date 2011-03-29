package org.geoserver.gwc;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GWCConfig implements Cloneable, Serializable {

    private static final long serialVersionUID = 3287178222706781438L;

    private boolean directWMSIntegrationEnabled;

    private boolean WMSCEnabled;

    private boolean WMTSEnabled;

    private boolean TMSEnabled;

    /**
     * Whether to automatically cache GeoServer layers or they should be enabled explicitly
     */
    private boolean cacheLayersByDefault = true;

    /**
     * Whether to cache the layer's declared CRS by default
     * <p>
     * TODO:this one is problematic until we have a steady way of defining new gridsets based on a
     * CRS
     * </p>
     */
    private transient boolean cacheDeclaredCRS;

    /**
     * Whether to cache any non default Style associated to the layer
     */
    private boolean cacheNonDefaultStyles;

    private int metaTilingX;

    private int metaTilingY;

    /**
     * Which SRS's to cache by default when adding a new Layer. Defaults to
     * {@code [EPSG:4326, EPSG:900913]}
     */
    private ArrayList<String> defaultCachingGridSetIds;

    private ArrayList<String> defaultCoverageCacheFormats;

    private ArrayList<String> defaultVectorCacheFormats;

    /**
     * Default cache formats for non coverage/vector layers (LayerGroups and WMS layers)
     */
    private ArrayList<String> defaultOtherCacheFormats;

    /**
     * Creates a new GWC config with default values
     */
    public GWCConfig() {
        setOldDefaults();
        String png = "image/png";
        String jpeg = "image/jpeg";

        setDefaultCoverageCacheFormats(Arrays.asList(jpeg));
        setDefaultOtherCacheFormats(Arrays.asList(png, jpeg));
        setDefaultVectorCacheFormats(Arrays.asList(png));
    }

    public boolean isCacheLayersByDefault() {
        return cacheLayersByDefault;
    }

    public void setCacheLayersByDefault(boolean cacheLayersByDefault) {
        this.cacheLayersByDefault = cacheLayersByDefault;
    }

    public boolean isDirectWMSIntegrationEnabled() {
        return directWMSIntegrationEnabled;
    }

    public void setDirectWMSIntegrationEnabled(boolean directWMSIntegrationEnabled) {
        this.directWMSIntegrationEnabled = directWMSIntegrationEnabled;
    }

    public boolean isWMSCEnabled() {
        return WMSCEnabled;
    }

    public void setWMSCEnabled(boolean wMSCEnabled) {
        WMSCEnabled = wMSCEnabled;
    }

    public boolean isWMTSEnabled() {
        return WMTSEnabled;
    }

    public void setWMTSEnabled(boolean wMTSEnabled) {
        WMTSEnabled = wMTSEnabled;
    }

    public boolean isTMSEnabled() {
        return TMSEnabled;
    }

    public void setTMSEnabled(boolean tMSEnabled) {
        TMSEnabled = tMSEnabled;
    }

    /**
     * see reason of getting rid of this property at the fields comment
     */
    private boolean isCacheDeclaredCRS() {
        return cacheDeclaredCRS;
    }

    private void setCacheDeclaredCRS(boolean cacheDeclaredCRS) {
        this.cacheDeclaredCRS = cacheDeclaredCRS;
    }

    public boolean isCacheNonDefaultStyles() {
        return cacheNonDefaultStyles;
    }

    public void setCacheNonDefaultStyles(boolean cacheNonDefaultStyles) {
        this.cacheNonDefaultStyles = cacheNonDefaultStyles;
    }

    public List<String> getDefaultCachingGridSetIds() {
        return defaultCachingGridSetIds;
    }

    public void setDefaultCachingGridSetIds(List<String> defaultCachingGridSetIds) {
        this.defaultCachingGridSetIds = new ArrayList<String>(defaultCachingGridSetIds);
    }

    public List<String> getDefaultCoverageCacheFormats() {
        return defaultCoverageCacheFormats;
    }

    public void setDefaultCoverageCacheFormats(List<String> defaultCoverageCacheFormats) {
        this.defaultCoverageCacheFormats = new ArrayList<String>(defaultCoverageCacheFormats);
    }

    public List<String> getDefaultVectorCacheFormats() {
        return defaultVectorCacheFormats;
    }

    public void setDefaultVectorCacheFormats(List<String> defaultVectorCacheFormats) {
        this.defaultVectorCacheFormats = new ArrayList<String>(defaultVectorCacheFormats);
    }

    public List<String> getDefaultOtherCacheFormats() {
        return defaultOtherCacheFormats;
    }

    public void setDefaultOtherCacheFormats(List<String> defaultOtherCacheFormats) {
        this.defaultOtherCacheFormats = new ArrayList<String>(defaultOtherCacheFormats);
    }

    /**
     * Returns a config suitable to match the old defaults when the integrated GWC behaivour was not
     * configurable.
     */
    public static GWCConfig getOldDefaults() {
        GWCConfig config = new GWCConfig();
        config.setOldDefaults();
        return config;
    }

    private void setOldDefaults() {

        setCacheDeclaredCRS(false);
        setCacheLayersByDefault(true);
        setMetaTilingX(4);
        setMetaTilingY(4);
        // this is not an old default, but a new feature so we enabled it anyway
        setCacheNonDefaultStyles(true);
        setDefaultCachingGridSetIds(Arrays.asList("EPSG:4326", "EPSG:900913"));
        List<String> oldDefaultFormats = Arrays.asList("image/png", "image/jpeg");
        setDefaultCoverageCacheFormats(oldDefaultFormats);
        setDefaultOtherCacheFormats(oldDefaultFormats);
        setDefaultVectorCacheFormats(oldDefaultFormats);
        setDirectWMSIntegrationEnabled(false);
        setWMSCEnabled(true);
        setWMTSEnabled(true);
        setTMSEnabled(true);
    }

    public int getMetaTilingX() {
        return metaTilingX;
    }

    public void setMetaTilingX(int metaFactorX) {
        this.metaTilingX = metaFactorX;
    }

    public int getMetaTilingY() {
        return metaTilingY;
    }

    public void setMetaTilingY(int metaFactorY) {
        this.metaTilingY = metaFactorY;
    }

    @Override
    public GWCConfig clone() {
        GWCConfig clone;
        try {
            clone = (GWCConfig) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }

        clone.setDefaultCachingGridSetIds(new ArrayList<String>(getDefaultCachingGridSetIds()));
        clone.setDefaultCoverageCacheFormats(new ArrayList<String>(getDefaultCoverageCacheFormats()));
        clone.setDefaultVectorCacheFormats(new ArrayList<String>(getDefaultVectorCacheFormats()));
        clone.setDefaultOtherCacheFormats(new ArrayList<String>(getDefaultOtherCacheFormats()));

        return clone;
    }
}
