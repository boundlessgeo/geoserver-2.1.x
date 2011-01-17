/* Copyright (c) 2001 - 2007 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.web.data.layergroup;

import org.apache.wicket.PageParameters;
import org.geoserver.catalog.LayerGroupInfo;
import org.geoserver.web.wicket.ParamResourceModel;

/**
 * Edits a layer group
 */
@SuppressWarnings("serial")
public class LayerGroupEditPage extends AbstractLayerGroupPage {

    public static final String GROUP = "group";
    
    public LayerGroupEditPage(PageParameters parameters) {
        String groupName = parameters.getString(GROUP);
        LayerGroupInfo lg = getCatalog().getLayerGroupByName(groupName);
        
        if(lg == null) {
            error(new ParamResourceModel("LayerGroupEditPage.notFound", this, groupName).getString());
            setResponsePage(LayerGroupPage.class);
            return;
        }
        
        initUI(lg);
    }
    
    protected void onSubmit() {
        LayerGroupInfo lg = (LayerGroupInfo) lgModel.getObject();
        
        getCatalog().save( lg );
        setResponsePage(LayerGroupPage.class);
    }
    
    
}
