/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2015 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.exist.xmldb;

import java.io.IOException;
import java.io.Writer;
import java.net.URISyntaxException;
import java.util.*;

import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.source.Source;
import org.exist.xmlrpc.RpcAPI;
import org.exist.xquery.XPathException;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.CompiledExpression;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

import javax.xml.XMLConstants;

import static java.nio.charset.StandardCharsets.UTF_8;

public class RemoteXPathQueryService extends AbstractRemote implements EXistXPathQueryService, EXistXQueryService {

    private final Map<String, String> namespaceMappings = new HashMap<>();
    private final Map<String, Object> variableDecls = new HashMap<>();
    private final Properties outputProperties;
    private String moduleLoadPath = null;
    private boolean protectedMode = false;

    /**
     * Creates a new <code>RemoteXPathQueryService</code> instance.
     *
     * @param collection a <code>RemoteCollection</code> value
     */
    public RemoteXPathQueryService(final RemoteCollection collection) {
        super(collection);
        this.outputProperties = collection.getProperties();
    }

    @Override
    public String getName() {
        return "XPathQueryService";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public ResourceSet query(final String query) throws XMLDBException {
        return query(query, null);
    }

    @Override
    public ResourceSet query(final String query, final String sortExpr) throws XMLDBException {
        try {
            final Map<String, Object> optParams = new HashMap<>();

            if (sortExpr != null) {
                optParams.put(RpcAPI.SORT_EXPR, sortExpr);
            }
            if (namespaceMappings.size() > 0) {
                optParams.put(RpcAPI.NAMESPACES, namespaceMappings);
            }
            if (variableDecls.size() > 0) {
                optParams.put(RpcAPI.VARIABLES, variableDecls);
            }
            optParams.put(RpcAPI.BASE_URI, outputProperties.getProperty(RpcAPI.BASE_URI, collection.getPath()));
            if (moduleLoadPath != null) {
                optParams.put(RpcAPI.MODULE_LOAD_PATH, moduleLoadPath);
            }
            if (protectedMode) {
                optParams.put(RpcAPI.PROTECTED_MODE, collection.getPath());
            }

            final Map<String, Object> result = collection.getClient().queryPT(query.getBytes(UTF_8), optParams);

            if (result.get(RpcAPI.ERROR) != null) {
                throwException(result);
            }

            final Object[] resources = (Object[]) result.get("results");
            int handle = -1;
            int hash = -1;
            if (resources != null && resources.length > 0) {
                handle = (Integer) result.get("id");
                hash = (Integer) result.get("hash");
            }
            return new RemoteResourceSet(collection, outputProperties, resources, handle, hash);
        } catch (final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }
    }

    @Override
    public CompiledExpression compile(final String query) throws XMLDBException {
        try {
            return compileAndCheck(query);
        } catch (final XPathException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        }
    }

    @Override
    public CompiledExpression compileAndCheck(final String query) throws XMLDBException, XPathException {
        try {
            final Map<String, Object> optParams = new HashMap<>();
            if (namespaceMappings.size() > 0) {
                optParams.put(RpcAPI.NAMESPACES, namespaceMappings);
            }
            if (variableDecls.size() > 0) {
                optParams.put(RpcAPI.VARIABLES, variableDecls);
            }
            if (moduleLoadPath != null) {
                optParams.put(RpcAPI.MODULE_LOAD_PATH, moduleLoadPath);
            }
            optParams.put(RpcAPI.BASE_URI,
                    outputProperties.getProperty(RpcAPI.BASE_URI, collection.getPath()));
            final Map<String, Object> result = collection.getClient().compile(query.getBytes(UTF_8), optParams);

            if (result.get(RpcAPI.ERROR) != null) {
                throwXPathException(result);
            }
            return new RemoteCompiledExpression(query);
        } catch (final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }
    }

    private void throwException(final Map result) throws XMLDBException {
        final String message = (String) result.get(RpcAPI.ERROR);
        final Integer lineInt = (Integer) result.get(RpcAPI.LINE);
        final Integer columnInt = (Integer) result.get(RpcAPI.COLUMN);
        final int line = lineInt == null ? 0 : lineInt.intValue();
        final int column = columnInt == null ? 0 : columnInt.intValue();
        final XPathException cause = new XPathException(line, column, message);
        throw new XMLDBException(ErrorCodes.VENDOR_ERROR, message, cause);
    }

    private void throwXPathException(final Map result) throws XPathException {
        final String message = (String) result.get(RpcAPI.ERROR);
        final Integer lineInt = (Integer) result.get(RpcAPI.LINE);
        final Integer columnInt = (Integer) result.get(RpcAPI.COLUMN);
        final int line = lineInt == null ? 0 : lineInt.intValue();
        final int column = columnInt == null ? 0 : columnInt.intValue();
        throw new XPathException(line, column, message);
    }

    @Override
    public ResourceSet execute(final Source source) throws XMLDBException {
        try {
            final String xq = source.getContent();
            return query(xq, null);
        } catch (final IOException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        }
    }

    @Override
    public ResourceSet executeStoredQuery(final String uri) throws XMLDBException {
        try {
            final Map<String, Object> result = collection.getClient().executeT(uri, Collections.emptyMap());

            if (result.get(RpcAPI.ERROR) != null) {
                throwException(result);
            }

            final Object[] resources = (Object[]) result.get("results");
            int handle = -1;
            int hash = -1;
            if (resources != null && resources.length > 0) {
                handle = (Integer) result.get("id");
                hash = (Integer) result.get("hash");
            }
            return new RemoteResourceSet(collection, outputProperties, resources, handle, hash);
        } catch (final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }
    }

    @Override
    public ResourceSet query(final XMLResource res, final String query)
            throws XMLDBException {
        return query(res, query, null);
    }

    @Override
    public ResourceSet query(final XMLResource res, final String query, final String sortExpr)
            throws XMLDBException {
        final RemoteXMLResource resource = (RemoteXMLResource) res;
        try {
            final Map<String, Object> optParams = new HashMap<>();
            if (namespaceMappings.size() > 0) {
                optParams.put(RpcAPI.NAMESPACES, namespaceMappings);
            }
            if (variableDecls.size() > 0) {
                optParams.put(RpcAPI.VARIABLES, variableDecls);
            }
            if (sortExpr != null) {
                optParams.put(RpcAPI.SORT_EXPR, sortExpr);
            }
            if (moduleLoadPath != null) {
                optParams.put(RpcAPI.MODULE_LOAD_PATH, moduleLoadPath);
            }
            optParams.put(RpcAPI.BASE_URI,
                    outputProperties.getProperty(RpcAPI.BASE_URI, collection.getPath()));
            if (protectedMode) {
                optParams.put(RpcAPI.PROTECTED_MODE, collection.getPath());
            }
            final Map<String, Object> result = collection.getClient().queryPT(query.getBytes(UTF_8), resource.path.toString(), resource.idIsPresent() ? resource.getNodeId() : "", optParams);

            if (result.get(RpcAPI.ERROR) != null) {
                throwException(result);
            }

            final Object[] resources = (Object[]) result.get("results");
            int handle = -1;
            int hash = -1;
            if (resources != null && resources.length > 0) {
                handle = (Integer) result.get("id");
                hash = (Integer) result.get("hash");
            }
            return new RemoteResourceSet(collection, outputProperties, resources, handle, hash);
        } catch (final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI, e.getMessage(), e);
        } catch (final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }
    }

    @Override
    public ResourceSet queryResource(final String resource, final String query) throws XMLDBException {
        final Resource res = collection.getResource(resource);
        try {
            if (res == null) {
                throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, "Resource " + resource + " not found");
            }
            if (!"XMLResource".equals(res.getResourceType())) {
                throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, "Resource " + resource + " is not an XML resource");
            }
            return query((XMLResource) res, query);
        }
        finally {
            if (res!=null && res instanceof AbstractRemoteResource) ((AbstractRemoteResource)res).freeResources();
        }
    }

    @Override
    public void setCollection(final Collection collection) {
    }

    @Override
    public String getProperty(final String name) {
        return outputProperties.getProperty(name);
    }

    @Override
    public void setProperty(final String property, final String value) {
        outputProperties.setProperty(property, value);
    }

    @Override
    public void clearNamespaces() {
        namespaceMappings.clear();
    }

    @Override
    public void removeNamespace(final String ns) {
        namespaceMappings.values().removeIf(s -> s.equals(ns));
    }

    @Override
    public void setNamespace(final String prefix, final String namespace) {
        namespaceMappings.put(prefix != null ? prefix : XMLConstants.DEFAULT_NS_PREFIX, namespace);
    }

    @Override
    public String getNamespace(final String prefix) {
        return namespaceMappings.get(prefix != null ? prefix : XMLConstants.DEFAULT_NS_PREFIX);
    }

    @Override
    public void declareVariable(final String qname, final Object initialValue) {
        variableDecls.put(qname, initialValue);
    }

    @Override
    public void clearVariables() {
        variableDecls.clear();
    }

    @Override
    public ResourceSet execute(final CompiledExpression expression) throws XMLDBException {
        return query(((RemoteCompiledExpression) expression).getQuery());
    }

    @Override
    public ResourceSet execute(final XMLResource res, final CompiledExpression expression) throws XMLDBException {
        return query(res, ((RemoteCompiledExpression) expression).getQuery());
    }

    @Override
    public void setXPathCompatibility(final boolean backwardsCompatible) {
        // TODO: not passed
    }

    /**
     * Calling this method has no effect. The server loads modules
     * relative to its own context.
     *
     * @see org.exist.xmldb.EXistXQueryService#setModuleLoadPath(java.lang.String)
     */
    @Override
    public void setModuleLoadPath(final String path) {
        this.moduleLoadPath = path;
    }

    @Override
    public void dump(final CompiledExpression expression, final Writer writer) throws XMLDBException {
        final String query = ((RemoteCompiledExpression) expression).getQuery();
        final Map<String, Object> optParams = new HashMap<>();
        if (namespaceMappings.size() > 0) {
            optParams.put(RpcAPI.NAMESPACES, namespaceMappings);
        }
        if (variableDecls.size() > 0) {
            optParams.put(RpcAPI.VARIABLES, variableDecls);
        }
        optParams.put(RpcAPI.BASE_URI,
                outputProperties.getProperty(RpcAPI.BASE_URI, collection.getPath()));
        try {
            final String dump = collection.getClient().printDiagnostics(query, optParams);
            writer.write(dump);
        } catch (final EXistException |  IOException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }
    }

    @Override
    public void beginProtected() {
        protectedMode = true;
    }

    @Override
    public void endProtected() {
        protectedMode = false;
    }
}
