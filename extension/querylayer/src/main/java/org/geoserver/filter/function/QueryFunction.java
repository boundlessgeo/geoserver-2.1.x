/* Copyright (c) 2001 - 2008 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 */
package org.geoserver.filter.function;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geotools.data.FeatureSource;
import org.geotools.data.Query;
import org.geotools.feature.FeatureIterator;
import org.geotools.filter.FunctionImpl;
import org.geotools.filter.text.ecql.ECQL;
import org.opengis.feature.Feature;
import org.opengis.filter.Filter;
import org.opengis.filter.expression.Expression;
import org.opengis.filter.expression.Literal;

/**
 * Queries a GeoServer layer and extracts the value(s) of an attribute TODO: add sorting
 * 
 * @author Andrea Aime - GeoSolutions
 */
public class QueryFunction extends FunctionImpl {

    Catalog catalog;

    int maxResults;
    
    boolean single;

    public QueryFunction(String name, Catalog catalog, List<Expression> args, Literal fallback,
            boolean single, int maxResults) {
        this.catalog = catalog;
        this.maxResults = maxResults;
        this.single = single;
        setName(name);
        setFallbackValue(fallback);
        setParameters(args);

        if (args.size() < 3 || args.size() > 4) {
            throw new IllegalArgumentException(
                    "QuerySingle function requires 3 or 4 arguments (feature type qualified name, "
                            + "cql filter, extracted attribute name and sort by clause");
        }
    }

    @Override
    public Object evaluate(Object object) {
        FeatureIterator fi = null;
        try {
            // extract layer
            String layerName = getParameters().get(0).evaluate(object, String.class);
            if (layerName == null) {
                throw new IllegalArgumentException(
                        "The first argument should be a vector layer name");
            }
            FeatureTypeInfo ft = catalog.getFeatureTypeByName(layerName);
            if (ft == null) {
                throw new IllegalArgumentException("Could not find vector layer " + layerName
                        + " in the GeoServer catalog");
            }

            // extract and check the attribute
            String attribute = getParameters().get(1).evaluate(object, String.class);
            if (attribute == null) {
                throw new IllegalArgumentException("The second argument of the query "
                        + "function should be the attribute name");
            }
            if (ft.getFeatureType().getDescriptor(attribute) == null) {
                throw new IllegalArgumentException("Attribute " + attribute
                        + " could not be found in layer " + layerName);
            }

            // extract and check the filter
            String cql = getParameters().get(2).evaluate(object, String.class);
            if (cql == null) {
                throw new IllegalArgumentException("The third argument of the query "
                        + "function should be a valid (E)CQL filter");
            }
            Filter filter;
            try {
                filter = (Filter) ECQL.toFilter(cql);
            } catch (Exception e) {
                throw new IllegalArgumentException("The third argument of the query "
                        + "function should be a valid (E)CQL filter", e);
            }

            // perform the query
            Query query = new Query(null, filter, new String[] { attribute });
            // .. just enough to judge if we went beyond the limit
            query.setMaxFeatures(maxResults + 1);
            FeatureSource fs = ft.getFeatureSource(null, null);
            fi = fs.getFeatures(query).features();
            List<Object> results = new ArrayList<Object>(maxResults);
            while (fi.hasNext()) {
                Feature f = fi.next();
                Object value = f.getProperty(attribute).getValue();
                results.add(value);
            }

            if (results.size() == 0) {
                return null;
            }
            if (maxResults > 0 && results.size() > maxResults && !single) {
                throw new IllegalStateException("The query in " + getName() + " returns too many "
                        + "features, the limit is " + maxResults);
            }
            if (maxResults == 1) {
                return results.get(0);
            } else {
                return results;
            }

        } catch (IOException e) {
            throw new RuntimeException("Failed to evaluated the query: " + e.getMessage(), e);
        } finally {
            if (fi != null) {
                fi.close();
            }
        }

    }

}
