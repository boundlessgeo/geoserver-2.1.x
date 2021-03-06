/* Copyright (c) 2001 - 2007 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, availible at the root
 * application directory.
 */
package org.geoserver.wfs.xml;

import static org.geoserver.ows.util.ResponseUtils.buildURL;
import static org.geoserver.ows.util.ResponseUtils.params;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.xsd.XSDComplexTypeDefinition;
import org.eclipse.xsd.XSDCompositor;
import org.eclipse.xsd.XSDDerivationMethod;
import org.eclipse.xsd.XSDElementDeclaration;
import org.eclipse.xsd.XSDFactory;
import org.eclipse.xsd.XSDForm;
import org.eclipse.xsd.XSDImport;
import org.eclipse.xsd.XSDInclude;
import org.eclipse.xsd.XSDModelGroup;
import org.eclipse.xsd.XSDNamedComponent;
import org.eclipse.xsd.XSDParticle;
import org.eclipse.xsd.XSDSchema;
import org.eclipse.xsd.XSDSchemaContent;
import org.eclipse.xsd.XSDTypeDefinition;
import org.eclipse.xsd.impl.XSDImportImpl;
import org.eclipse.xsd.impl.XSDSchemaImpl;
import org.eclipse.xsd.util.XSDConstants;
import org.eclipse.xsd.util.XSDSchemaLocator;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.catalog.NamespaceInfo;
import org.geoserver.config.GeoServer;
import org.geoserver.ows.URLMangler.URLType;
import org.geoserver.ows.util.ResponseUtils;
import org.geoserver.platform.GeoServerResourceLoader;
import org.geoserver.wfs.GMLInfo;
import org.geoserver.wfs.WFSInfo;
import org.geotools.feature.NameImpl;
import org.geotools.gml2.GMLConfiguration;
import org.geotools.gml3.v3_2.GML;
import org.geotools.wfs.v2_0.WFS;
import org.geotools.xml.Configuration;
import org.geotools.xml.Schemas;
import org.geotools.xs.XS;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.AttributeType;
import org.opengis.feature.type.ComplexType;
import org.opengis.feature.type.FeatureType;
import org.opengis.feature.type.Name;
import org.opengis.feature.type.PropertyDescriptor;
import org.opengis.feature.type.Schema;

/**
 * Builds a {@link org.eclipse.xsd.XSDSchema} from {@link FeatureTypeInfo}
 * metadata objects.
 * <p>
 *
 * </p>
 *
 * @author Justin Deoliveira, The Open Planning Project
 * @author Ben Caradoc-Davies, CSIRO Exploration and Mining
 */
public abstract class FeatureTypeSchemaBuilder {
    /** logging instance */
    static Logger logger = org.geotools.util.logging.Logging.getLogger("org.geoserver.wfs");

    /** wfs configuration */
    WFSInfo wfs;

    /** the catalog */
    Catalog catalog;

    /** resource loader */
    GeoServerResourceLoader resourceLoader;

    /**
     * profiles used for type mapping.
     */
    protected List profiles;

    /**
     * gml schema stuff
     */
    protected String gmlNamespace;
    protected String gmlSchemaLocation;
    protected String baseType;
    protected String substitutionGroup;
    protected Map<String, String> describeFeatureTypeParams;
    protected String gmlPrefix;
    protected Configuration xmlConfiguration;

    protected FeatureTypeSchemaBuilder(GeoServer gs) {
        this.wfs = gs.getService( WFSInfo.class );
        this.catalog = gs.getCatalog();
        this.resourceLoader = gs.getCatalog().getResourceLoader();

        profiles = new ArrayList();
        profiles.add(new XSProfile());
    }

    public Map<String, String> getDescribeFeatureTypeParams() {
        return describeFeatureTypeParams;
    }
    
    public Configuration getXmlConfiguration() {
        return xmlConfiguration;
    }
    
    public XSDSchema build(FeatureTypeInfo featureTypeInfo, String baseUrl)
        throws IOException {
        return build(new FeatureTypeInfo[] { featureTypeInfo }, baseUrl);
    }

    public XSDSchema build(FeatureTypeInfo[] featureTypeInfos, String baseUrl)
        throws IOException {
        XSDFactory factory = XSDFactory.eINSTANCE;
        XSDSchema schema = factory.createXSDSchema();
        schema.setSchemaForSchemaQNamePrefix("xsd");
        schema.getQNamePrefixToNamespaceMap().put("xsd", XSDConstants.SCHEMA_FOR_SCHEMA_URI_2001);
        schema.setElementFormDefault(XSDForm.get(XSDForm.QUALIFIED));

        //group the feature types by namespace
        HashMap ns2featureTypeInfos = new HashMap();

        for (int i = 0; i < featureTypeInfos.length; i++) {
            String prefix = featureTypeInfos[i].getNamespace().getPrefix();
            List l = (List) ns2featureTypeInfos.get(prefix);

            if (l == null) {
                l = new ArrayList();
            }

            l.add(featureTypeInfos[i]);

            ns2featureTypeInfos.put(prefix, l);
        }
        
        if (baseUrl == null)
            baseUrl = wfs.getSchemaBaseURL(); 
                
        if (ns2featureTypeInfos.entrySet().size() == 1) {
            // only 1 namespace, write target namespace out
            String targetPrefix = (String) ns2featureTypeInfos.keySet().iterator().next();
            String targetNamespace = catalog.getNamespaceByPrefix(targetPrefix).getURI();
            schema.setTargetNamespace(targetNamespace);
            schema.getQNamePrefixToNamespaceMap().put(targetPrefix, targetNamespace);
            // add secondary namespaces from catalog
            for (NamespaceInfo nameSpaceinfo : catalog.getNamespaces()) {
                if (!schema.getQNamePrefixToNamespaceMap().containsKey(nameSpaceinfo.getPrefix())) {
                    schema.getQNamePrefixToNamespaceMap().put(nameSpaceinfo.getPrefix(),
                            nameSpaceinfo.getURI());
                }
            }
            // would result in some xsd:include or xsd:import if schema location is specified
            try {
                FeatureType featureType = featureTypeInfos[0].getFeatureType();
                Object schemaUri = featureType.getUserData().get("schemaURI");
                if (schemaUri != null && schemaUri instanceof Map) {
                    // should always be a Map.. set in AppSchemaDataAccessConfigurator
                    Map<String, String> schemaURIs = (Map<String, String>) schemaUri;
                    // schema is supplied by the user.. just include the top level schema instead of
                    // building the type
                    if (!findTypeInSchema(featureTypeInfos[0], schema, factory)) {
                        ArrayList<String> importedNamespaces = new ArrayList<String>(
                                ns2featureTypeInfos.size() + 1);
                        for (String namespace : schemaURIs.keySet()) {
                            if (targetNamespace.equals(namespace)) {
                                addInclude(schema, factory, schemaURIs.get(namespace));
                            } else {
                                addImport(schema, factory, namespace, schemaURIs.get(namespace),
                                        importedNamespaces);
                                // ensure there's only 1 import per namespace
                                importedNamespaces.add(namespace);
                            }
                        }
                    }
                    return schema;
                }
            } catch (IOException e) {
                logger.warning("Unable to get schema location for feature type '"
                        + featureTypeInfos[0].getPrefixedName() + "'. Reason: '" + e.getMessage()
                        + "'. Building the schema manually instead.");
            }

            // user didn't define schema location
            // import gml schema
            importGMLSchema(schema, factory, baseUrl);
            
            schema.getQNamePrefixToNamespaceMap().put(gmlPrefix, gmlNamespace);
            //schema.getQNamePrefixToNamespaceMap().put("gml", "http://www.opengis.net/gml");
            // then manually build schema
            for (int i = 0; i < featureTypeInfos.length; i++) {
                try {
                    buildSchemaContent(featureTypeInfos[i], schema, factory, baseUrl);
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Could not build xml schema for type: "
                            + featureTypeInfos[i].getName(), e);
                }
            }
        } else {
            //different namespaces, write out import statements
            ArrayList<String> importedNamespaces = new ArrayList<String>(
                    ns2featureTypeInfos.size() + 1);
            for (Iterator i = ns2featureTypeInfos.entrySet().iterator(); i.hasNext();) {
                Map.Entry entry = (Map.Entry) i.next();
                String prefix = (String) entry.getKey();
                List types = (List) entry.getValue();

                StringBuffer typeNames = new StringBuffer();
                for (Iterator t = types.iterator(); t.hasNext();) {
                    FeatureTypeInfo info = (FeatureTypeInfo) t.next();
                    FeatureType featureType = info.getFeatureType();
                    Object schemaUri = featureType.getUserData().get("schemaURI");
                    if (schemaUri != null && schemaUri instanceof Map) {
                        // should always be a Map.. set in AppSchemaDataAccessConfigurator
                        Map<String, String> schemaURIs = (Map<String, String>) schemaUri;
                        // schema is supplied by the user.. just import the specified location
                        for (String namespace : schemaURIs.keySet()) {
                            addImport(schema, factory, namespace, schemaURIs.get(namespace),
                                    importedNamespaces);
                            // ensure there's only 1 import per namespace
                            importedNamespaces.add(namespace);
                        }
                    } else {
                        typeNames.append(info.getPrefixedName()).append(",");
                    }
                }
                if (typeNames.length() > 0) {
                    typeNames.setLength(typeNames.length()-1);
                    
                    // schema not found, encode describe feature type URL
                    Map<String, String> params = new LinkedHashMap<String, String>(describeFeatureTypeParams);
                    params.put("typeName", typeNames.toString().trim());
    
                    String schemaLocation = buildURL(baseUrl, "wfs", params, URLType.RESOURCE);
                    String namespace = catalog.getNamespaceByPrefix(prefix).getURI();
    
                    XSDImport imprt = factory.createXSDImport();
                    imprt.setNamespace(namespace);
                    imprt.setSchemaLocation(schemaLocation);
    
                    schema.getContents().add(imprt);
                }
            }
        }

//        for (Iterator i = schema.getContents().iterator(); i.hasNext();) {
//            Object o = i.next();
//            if (o instanceof XSDImport) {
//                ((XSDSchemaImpl)((XSDImport)o).getResolvedSchema()).imported((XSDImport)o);
//                
//            }
//        }
        return schema;
    }

    protected void importGMLSchema(XSDSchema schema, XSDFactory factory, String baseUrl) {
        XSDImport imprt = factory.createXSDImport();
        imprt.setNamespace(gmlNamespace);
        imprt.setSchemaLocation(ResponseUtils.buildSchemaURL(baseUrl, gmlSchemaLocation));

        XSDSchema gmlSchema = gmlSchema();
        imprt.setResolvedSchema(gmlSchema);
        
        schema.getContents().add(imprt);

    }

    /**
     * Add include statement to schema.
     * 
     * @param schema
     *            Output schema
     * @param factory
     *            XSD factory used to produce schema
     * @param schemaLocation
     *            The schema location to be included
     */
    private void addInclude(XSDSchema schema, XSDFactory factory, String schemaLocation) {
        XSDInclude xsdInclude = factory.createXSDInclude();
        xsdInclude.setSchemaLocation(schemaLocation);
        schema.getContents().add(xsdInclude);        
        schema.updateElement();
    }

    /**
     * Add import statement to schema.
     * 
     * @param schema
     *            Output schema
     * @param factory
     *            XSD factory used to produce schema
     * @param nsURI
     *            Import name space
     * @param schemaLocations
     *            The schema to be imported
     * @param importedNamespaces
     *            List of already imported name spaces
     */
    private void addImport(XSDSchema schema, XSDFactory factory, String nsURI, String schemaURI,
            List<String> importedNamespaces) {
        if (!importedNamespaces.contains(nsURI)) {
            XSDImport xsdImport = factory.createXSDImport();
            xsdImport.setNamespace(nsURI);
            xsdImport.setSchemaLocation((String) schemaURI);
            schema.getContents().add(xsdImport);
            importedNamespaces.add(nsURI);
        }
    }

    /**
     * Adds types defined in the catalog to the provided schema.
     */
    public XSDSchema addApplicationTypes( XSDSchema wfsSchema ) throws IOException {
        //incorporate application schemas into the wfs schema
        Collection featureTypeInfos = catalog.getFeatureTypes();

        for (Iterator i = featureTypeInfos.iterator(); i.hasNext();) {
            FeatureTypeInfo meta = (FeatureTypeInfo) i.next();
            
            // don't build schemas for disabled feature types
            if(!meta.enabled())
                continue;

            //build the schema for the types in the single namespace
            XSDSchema schema = build(new FeatureTypeInfo[] { meta }, null);

            //declare the namespace
            String prefix = meta.getNamespace().getPrefix();
            String namespaceURI = meta.getNamespace().getURI();
            wfsSchema.getQNamePrefixToNamespaceMap().put(prefix, namespaceURI);

            //add the types + elements to the wfs schema
            for (Iterator t = schema.getTypeDefinitions().iterator(); t.hasNext();) {
                wfsSchema.getTypeDefinitions().add(t.next());
            }

            for (Iterator e = schema.getElementDeclarations().iterator(); e.hasNext();) {
                wfsSchema.getElementDeclarations().add(e.next());
            }
            
            // add secondary namespaces from catalog
            for (Map.Entry entry : (Set<Map.Entry>) schema.getQNamePrefixToNamespaceMap()
                    .entrySet()) {
                if (!wfsSchema.getQNamePrefixToNamespaceMap().containsKey(entry.getKey())) {
                    wfsSchema.getQNamePrefixToNamespaceMap().put(entry.getKey(), entry.getValue());
                }
            }
        }

        return wfsSchema;
    }

    boolean findTypeInSchema(FeatureTypeInfo featureTypeMeta, XSDSchema schema, XSDFactory factory)
            throws IOException {
        // look if the schema for the type is already defined
        String ws = featureTypeMeta.getStore().getWorkspace().getName();
        String ds = featureTypeMeta.getStore().getName();
        String name = featureTypeMeta.getName();

        File schemaFile = null;

        try {
            schemaFile = resourceLoader.find("workspaces/" + ws + "/" + ds + "/" + name + "/schema.xsd");
        } catch (IOException e1) {
        }

        if (schemaFile != null) {
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("Found customized schema.xsd: " + schemaFile.getAbsolutePath());    
            }
            
            //schema file found, parse it and lookup the complex type
            List resolvers = Schemas.findSchemaLocationResolvers(xmlConfiguration);
            List locators = new ArrayList(); 
            locators.add( new XSDSchemaLocator() {
                public XSDSchema locateSchema(XSDSchema schema, String namespaceURI,
                        String rawSchemaLocationURI, String resolvedSchemaLocationURI) {
                    
                    if ( gmlNamespace.equals( namespaceURI ) ) {
                        return gmlSchema();
                    }
                    return null;
                }
            });
            
            XSDSchema ftSchema = null;
            try {
                ftSchema = Schemas.parse(schemaFile.getAbsolutePath(), locators, resolvers);
            } catch (IOException e) {
                logger.log(Level.WARNING,
                    "Unable to parse schema: " + schemaFile.getAbsolutePath(), e);
            }

            if (ftSchema != null) {
                //respect the prefix (xs vs xsd) given by the underlying schema file
                if ( ftSchema.getSchemaForSchemaQNamePrefix() != null ) {
                    schema.setSchemaForSchemaQNamePrefix(ftSchema.getSchemaForSchemaQNamePrefix());
                }
                
                //add the contents of this schema to the schema being built
                //look up the complex type
                List contents = ftSchema.getContents();

                //ensure that an element for the feature is present
                boolean hasElement = false;
                
                for (Iterator i = contents.iterator(); i.hasNext();) {
                    XSDSchemaContent content = (XSDSchemaContent) i.next();
                    content.setElement(null);
                    
                    //check for import of gml, skip over since we already imported it
                    if ( content instanceof XSDImport ) {
                        XSDImport imprt = (XSDImport) content;
                        if ( gmlNamespace.equals( imprt.getNamespace() ) ) {
                            i.remove();
                        }
                    }
                    
                    //check for duplicated elements and types
                    
                    if ( content instanceof XSDElementDeclaration ) {
                        if ( contains( (XSDNamedComponent) content, schema.getElementDeclarations() ) ) {
                            i.remove();
                        }
                    }
                    else if ( content instanceof XSDTypeDefinition ) {
                        if ( contains( (XSDNamedComponent) content, schema.getTypeDefinitions() ) ) {
                            i.remove();
                        }
                    }
                    
                    // check for element
                    if ( !hasElement && content instanceof XSDElementDeclaration ) {
                        XSDElementDeclaration element = (XSDElementDeclaration) content;
                        if ( name.equals( element.getName() ) && 
                            featureTypeMeta.getNamespace().getURI().equals( element.getTargetNamespace() ) ) {
                            hasElement = true;
                        }
                    }
                }
                
                if ( !hasElement ) {
                    //need to create an element declaration in the schema
                    XSDElementDeclaration element = factory.createXSDElementDeclaration();
                    element.setName( featureTypeMeta.getName() );
                    element.setTargetNamespace( featureTypeMeta.getNamespace().getURI() );
                    element.setSubstitutionGroupAffiliation(
                        schema.resolveElementDeclaration(gmlNamespace, substitutionGroup));
                    
                    //find the type of the element
                    List<XSDComplexTypeDefinition> candidates = new ArrayList<XSDComplexTypeDefinition>();
                    for ( Iterator t = ftSchema.getTypeDefinitions().iterator(); t.hasNext(); ) {
                        XSDTypeDefinition type = (XSDTypeDefinition) t.next();
                        if ( type instanceof XSDComplexTypeDefinition ) {
                            XSDTypeDefinition base = type.getBaseType();
                            while(base != null ) {
                                if ( baseType.equals(base.getName())
                                    && gmlNamespace.equals( base.getTargetNamespace() ) ) {
                                    
                                    candidates.add( (XSDComplexTypeDefinition) type );
                                    break;
                                }   
                                if ( base.equals( base.getBaseType() ) ) {
                                    break;
                                }
                                base = base.getBaseType();
                            }
                        }
                    }
                    
                    if ( candidates.size() != 1 ) {
                        throw new IllegalStateException("Could not determine feature type for " +
                        "generated element. Must specify explicitly in schema.xsd.");
                    }
                    
                    element.setTypeDefinition( candidates.get(0));
                    schema.getContents().add(element);
                }

                schema.getContents().addAll(contents);
                schema.updateElement();

                return true;
            }
        }
        return false;
    }

    private void buildSchemaContent(FeatureTypeInfo featureTypeMeta, XSDSchema schema,
            XSDFactory factory, String baseUrl)
            throws IOException {
        if (!findTypeInSchema(featureTypeMeta, schema, factory)) {
            // build the type manually
            XSDComplexTypeDefinition xsdComplexType = buildComplexSchemaContent(featureTypeMeta
                    .getFeatureType(), schema, factory);

            XSDElementDeclaration element = factory.createXSDElementDeclaration();
            element.setName(featureTypeMeta.getName());
            element.setTargetNamespace(featureTypeMeta.getNamespace().getURI());
            element.setSubstitutionGroupAffiliation(schema.resolveElementDeclaration(gmlNamespace,
                    substitutionGroup));
            element.setTypeDefinition(xsdComplexType);

            schema.getContents().add(element);

            schema.updateElement();
        }
    }

    /**
     * Construct an XSD type definition for a ComplexType. 
     * 
     * <p>
     * 
     * A side-effect of calling this method is that the constructed type and any concrete nested
     * complex types are added to the schema.
     * 
     * @param complexType
     * @param schema
     * @param factory
     * @return
     */
    private XSDComplexTypeDefinition buildComplexSchemaContent(ComplexType complexType,
            XSDSchema schema, XSDFactory factory) {
        XSDComplexTypeDefinition xsdComplexType = factory.createXSDComplexTypeDefinition();
        xsdComplexType.setName(complexType.getName().getLocalPart() + "Type");

        xsdComplexType.setDerivationMethod(XSDDerivationMethod.EXTENSION_LITERAL);
        xsdComplexType.setBaseTypeDefinition(resolveTypeInSchema(schema, new NameImpl(gmlNamespace, baseType)));

        XSDModelGroup group = factory.createXSDModelGroup();
        group.setCompositor(XSDCompositor.SEQUENCE_LITERAL);

        for (PropertyDescriptor pd : complexType.getDescriptors()) {
            if (pd instanceof AttributeDescriptor) {
                AttributeDescriptor attribute = (AttributeDescriptor) pd;

                if ( filterAttributeType( attribute ) ) {
                    GMLInfo gml = getGMLConfig(wfs);
                    if (gml == null || !gml.getOverrideGMLAttributes()) {
                        continue;
                    }
                }
                 
                XSDElementDeclaration element = factory.createXSDElementDeclaration();
                element.setName(attribute.getLocalName());
                element.setNillable(attribute.isNillable());

                Name typeName = attribute.getType().getName();
                // skip if it's XS.AnyType. It's not added to XS.Profile, because
                // a lot of types extend XS.AnyType causing it to be the returned
                // binding.. I could make it so that it checks against all profiles
                // but would that slow things down? At the moment, it returns the
                // first matching one, so it doesn't go through all profiles.
                if (!(typeName.getLocalPart().equals(XS.ANYTYPE.getLocalPart()) && typeName
                        .getNamespaceURI().equals(XS.NAMESPACE))) {
                    if (attribute.getType() instanceof ComplexType) {
                        // If non-simple complex property not in schema, recurse.
                        // Note that abstract types will of course not be resolved; these must be
                        // configured at global level, so they can be found by the
                        // encoder.
                        if (schema.resolveTypeDefinition(typeName.getNamespaceURI(), typeName
                                .getLocalPart()) == null) {
                            buildComplexSchemaContent((ComplexType) attribute.getType(), schema,
                                    factory);
                        }
                    } else {
                        Class binding = attribute.getType().getBinding();
                        typeName = findTypeName(binding);
                        if (typeName == null) {
                            throw new NullPointerException("Could not find a type for property: "
                                    + attribute.getName() + " of type: " + binding.getName());

                        }
                    }
                }

                //XSDTypeDefinition type = schema.resolveTypeDefinition(typeName.getNamespaceURI(),
                //        typeName.getLocalPart());
                XSDTypeDefinition type = resolveTypeInSchema(schema, typeName);
                element.setTypeDefinition(type);

                XSDParticle particle = factory.createXSDParticle();
                particle.setMinOccurs(attribute.getMinOccurs());
                particle.setMaxOccurs(attribute.getMaxOccurs());
                particle.setContent(element);
                group.getContents().add(particle);
            }
        }

        XSDParticle particle = factory.createXSDParticle();
        particle.setContent(group);

        xsdComplexType.setContent(particle);

        schema.getContents().add(xsdComplexType);
        return xsdComplexType;
    }

    XSDTypeDefinition resolveTypeInSchema(XSDSchema schema, Name typeName) {
        XSDTypeDefinition type = null;
        for (XSDTypeDefinition td : ((List<XSDTypeDefinition>)schema.getTypeDefinitions())) {
            if (typeName.getNamespaceURI().equals(td.getTargetNamespace()) 
                && typeName.getLocalPart().equals(td.getName())) {
                type = td;
                break;
            }
        }
        if (type == null) {
            type =  schema.resolveTypeDefinition(typeName.getNamespaceURI(),
                        typeName.getLocalPart());
        }
        return type;
    }

    boolean contains( XSDNamedComponent c, List l ) {

        boolean contains = false;
        for ( Iterator i = l.iterator(); !contains && i.hasNext(); ) {
            XSDNamedComponent e = (XSDNamedComponent) i.next();
            if ( e.getName().equals( c.getName() ) ) {
                if ( e.getTargetNamespace() == null ) {
                    contains = c.getTargetNamespace() == null;
                }
                else {
                    contains = e.getTargetNamespace().equals( c.getTargetNamespace() );
                }
            }
        }
        
        return contains;
    }
    
    Name findTypeName(Class binding) {
        for (Iterator p = profiles.iterator(); p.hasNext();) {
            Object profile = p.next();
            Name name = null;
            if (profile instanceof TypeMappingProfile) {
                name = ((TypeMappingProfile)profile).name(binding);
            }
            else if (profile instanceof Schema){
                Schema schema = (Schema) profile;
                for (Map.Entry<Name,AttributeType> e : schema.entrySet()) {
                    AttributeType at = e.getValue();
                    if (at.getBinding() != null && at.getBinding().equals(binding)) {
                        name = at.getName();
                        break;
                    }
                }
                
                if (name == null) {
                    for (AttributeType at : schema.values()) {
                        if (binding.isAssignableFrom(at.getBinding())) {
                            name = at.getName();
                            break;
                        }
                    }
                }
            }

            if (name != null) {
                return name;
            }
        }

        return null;
    }

    protected abstract XSDSchema gmlSchema();

    protected abstract GMLInfo getGMLConfig(WFSInfo wfs);
    
    protected boolean filterAttributeType( AttributeDescriptor attribute ) {
        return "name".equals( attribute.getLocalName() ) 
            || "description".equals( attribute.getLocalName()) 
            || "boundedBy".equals( attribute.getLocalName());
    }
    
    public static final class GML2 extends FeatureTypeSchemaBuilder {
        /**
         * Cached gml2 schema
         */
        private static XSDSchema gml2Schema;

        public GML2(GeoServer gs) {
            super(gs);

            profiles.add(new GML2Profile());
            gmlNamespace = org.geotools.gml2.GML.NAMESPACE;
            gmlSchemaLocation = "gml/2.1.2/feature.xsd";
            baseType = "AbstractFeatureType";
            substitutionGroup = "_Feature";
            describeFeatureTypeParams = params("request", "DescribeFeatureType", 
                    "version", "1.0.0",
                    "service", "WFS");
            gmlPrefix = "gml";
            xmlConfiguration = new GMLConfiguration();
        }

        protected XSDSchema gmlSchema() {
            if (gml2Schema == null) {
                gml2Schema = xmlConfiguration.schema();
            }

            return gml2Schema;
        }
        
        @Override
        protected GMLInfo getGMLConfig(WFSInfo wfs) {
            return wfs.getGML().get(WFSInfo.Version.V_10);
        }
    }

    public static class GML3 extends FeatureTypeSchemaBuilder {
        /**
         * Cached gml3 schema
         */
        private static XSDSchema gml3Schema;

        public GML3(GeoServer gs) {
            super(gs);

            profiles.add(createTypeMappingProfile());

            gmlNamespace = org.geotools.gml3.GML.NAMESPACE;
            gmlSchemaLocation = "gml/3.1.1/base/gml.xsd";
            baseType = "AbstractFeatureType";
            substitutionGroup = "_Feature";
            describeFeatureTypeParams =  params("request", "DescribeFeatureType", 
                    "version", "1.1.0",
                    "service", "WFS");

            gmlPrefix = "gml";
            xmlConfiguration = new org.geotools.gml3.GMLConfiguration();
        }

        Object createTypeMappingProfile() {
            return new GML3Profile();
        }

        protected XSDSchema gmlSchema() {
            if (gml3Schema == null) {
                gml3Schema = createGmlSchema();
            }

            return gml3Schema;
        }
        
        XSDSchema createGmlSchema() {
            return xmlConfiguration.schema();
        }

        protected boolean filterAttributeType( AttributeDescriptor attribute ) {
            return super.filterAttributeType( attribute ) || 
                "metaDataProperty".equals( attribute.getLocalName() ) || 
                "location".equals( attribute.getLocalName() );
        }
        
        @Override
        protected GMLInfo getGMLConfig(WFSInfo wfs) {
            return wfs.getGML().get(WFSInfo.Version.V_11);
        }
    }
    
    public static final class GML32 extends GML3 {

        public GML32(GeoServer gs) {
            super(gs);

            gmlNamespace = GML.NAMESPACE;
            gmlPrefix = "gml";
            gmlSchemaLocation = "gml/3.2.1/gml.xsd";
            baseType = "AbstractFeatureType";
            substitutionGroup = "AbstractFeature";
            describeFeatureTypeParams =  params("request", "DescribeFeatureType", 
                    "version", "1.1.0",
                    "service", "WFS", 
                    "outputFormat", "text/xml; subtype=gml/3.2");
            xmlConfiguration = new org.geotools.gml3.v3_2.GMLConfiguration();
        }

        @Override
        Object createTypeMappingProfile() {
            return GML.getInstance().getTypeMappingProfile();
        }
        
        @Override
        XSDSchema createGmlSchema() {
            try {
                return GML.getInstance().getSchema();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        
        @Override
        protected void importGMLSchema(XSDSchema schema, XSDFactory factory, String baseUrl) {
            XSDImport imprt;
            try {
                imprt = factory.createXSDImport();
                imprt.setNamespace( WFS.getInstance().getSchema().getTargetNamespace() );
                imprt.setSchemaLocation(ResponseUtils.buildSchemaURL(baseUrl, gmlSchemaLocation));
                imprt.setResolvedSchema(WFS.getInstance().getSchema());
                schema.getContents().add( imprt );
                
                schema.getQNamePrefixToNamespaceMap().put("wfs", WFS.NAMESPACE);
                //imprt = Schemas.importSchema(schema, WFS.getInstance().getSchema());
                ((XSDSchemaImpl)WFS.getInstance().getSchema()).imported(imprt);
                //((XSDSchemaImpl)schema).resolveSchema(WFS.NAMESPACE);
                
                
                //schema.getContents().add(imprt);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            
        }
        
        protected boolean filterAttributeType( AttributeDescriptor attribute ) {
            return super.filterAttributeType( attribute ) || 
                "descriptionReference".equals( attribute.getLocalName() ) || 
                "identifier".equals( attribute.getLocalName() );
        }
    }
    
    
}
