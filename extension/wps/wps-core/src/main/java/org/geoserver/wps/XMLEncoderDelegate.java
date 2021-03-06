/* Copyright (c) 2001 - 2007 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.wps;

import org.geoserver.wps.ppio.XMLPPIO;
import org.geotools.xml.EncoderDelegate;
import org.xml.sax.ContentHandler;

/**
 * Encodes complex objects as inline XML
 * @author Justin Deoliveria
 */
public class XMLEncoderDelegate implements EncoderDelegate {

    XMLPPIO ppio;
    Object object;
    
    public XMLEncoderDelegate( XMLPPIO ppio, Object object ) {
        this.ppio = ppio;
        this.object = object;
    }
    
    public XMLPPIO getProcessParameterIO() { 
        return ppio;
    }
    
    public void encode(ContentHandler handler) throws Exception {
        ppio.encode(object, handler);
    }

}
