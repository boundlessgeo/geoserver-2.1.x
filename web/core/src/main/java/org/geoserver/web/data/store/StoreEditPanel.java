/* Copyright (c) 2001 - 2009 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.web.data.store;

import java.io.IOException;
import java.io.Serializable;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.panel.Panel;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.DataStoreInfo;
import org.geoserver.catalog.StoreInfo;
import org.geoserver.web.GeoServerApplication;
import org.geotools.data.DataAccessFactory;
import org.geotools.data.DataAccessFactory.Param;

/**
 * Base class for panels containing the form edit components for {@link StoreInfo} objects
 * 
 * @author Gabriel Roldan
 * @see DefaultCoverageStoreEditPanel
 */
public abstract class StoreEditPanel extends Panel {

    private static final long serialVersionUID = 1L;

    protected final Form storeEditForm;

    protected StoreEditPanel(final String componentId, final Form storeEditForm) {
        super(componentId);
        this.storeEditForm = storeEditForm;

        StoreInfo info = (StoreInfo) storeEditForm.getModel().getObject();
        final boolean isNew = null == info.getId();
        if (isNew && info instanceof DataStoreInfo) {
            applyDataStoreParamsDefaults(info);
        }
    }

    /**
     * Initializes all store parameters to their default value
     * @param info
     */
    void applyDataStoreParamsDefaults(StoreInfo info) {
        // grab the factory
        final DataStoreInfo dsInfo = (DataStoreInfo) info;
        DataAccessFactory dsFactory;
        try {
            dsFactory = getCatalog().getResourcePool().getDataStoreFactory(dsInfo);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        final Param[] dsParams = dsFactory.getParametersInfo();
        for (Param p : dsParams) {
            ParamInfo paramInfo = new ParamInfo(p);

            // set default value
            Serializable defValue;
            if ("namespace".equals(paramInfo.getName())) {
                defValue = getCatalog().getDefaultNamespace().getURI();
            } else if (URL.class == paramInfo.getBinding()) {
                defValue = "file:data/example.extension";
            } else {
                defValue = paramInfo.getValue();
            }
            info.getConnectionParameters().put(paramInfo.getName(), defValue);
        }
    }

    protected Catalog getCatalog() {
        GeoServerApplication application = (GeoServerApplication) getApplication();
        return application.getCatalog();
    }

}
