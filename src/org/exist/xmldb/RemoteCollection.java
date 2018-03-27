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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.internal.aider.ACEAider;
import org.exist.util.Compressor;
import org.exist.util.EXistInputSource;
import org.exist.xmlrpc.RpcAPI;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.Service;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.BinaryResource;
import org.xmldb.api.modules.XMLResource;

/**
 * A remote implementation of the Collection interface. This implementation
 * communicates with the server through the XMLRPC protocol.
 *
 * @author wolf Updated Andy Foster - Updated code to allow child collection
 * cache to resync with the remote collection.
 */
public class RemoteCollection extends AbstractRemote implements EXistCollection {

    protected final static Logger LOG = LogManager.getLogger(RemoteCollection.class);

    // Max size of a resource to be send to the server.
    // If the resource exceeds this limit, the data is split into
    // junks and uploaded to the server via the update() call
    private static final int MAX_CHUNK_LENGTH = 512 * 1024; //512KB
    private static final int MAX_UPLOAD_CHUNK = 10 * 1024 * 1024; //10 MB

    private final XmldbURI path;
    private final RpcAPI apiClient;
    private Properties properties = null;

    public static RemoteCollection instance(final RpcAPI apiClient, final XmldbURI path) throws XMLDBException {
        return instance(apiClient, null, path);
    }

    public static RemoteCollection instance(final RpcAPI apiClient, final RemoteCollection parent, final XmldbURI path) throws XMLDBException {
        try {
            //check we can open the collection i.e. that we have permission!
            final boolean existsAndCanOpen = apiClient.existsAndCanOpenCollection(path.toString());

            if (existsAndCanOpen) {
                return new RemoteCollection(apiClient, parent, path);
            } else {
                return null;
            }
        } catch (final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }
    }

    private RemoteCollection(final RpcAPI apiClient, final RemoteCollection parent, final XmldbURI path) {
        super(parent);
        this.path = path.toCollectionPathURI();
        this.apiClient = apiClient;
    }

    protected RpcAPI getClient() {
        return apiClient;
    }

    @Override
    public void close() throws XMLDBException {
        try {
            apiClient.sync();
        } catch (final Exception e) {
            throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR, "failed to close collection", e);
        }
    }

    @Override
    public String createId() throws XMLDBException {
        try {
            return apiClient.createResourceId(getPath());
        } catch (final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI, e.getMessage(), e);
        } catch (final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }
    }

    @Override
    public Resource createResource(final String id, final String type) throws XMLDBException {
        try {
            final XmldbURI newId = (id == null) ? XmldbURI.xmldbUriFor(createId()) : XmldbURI.xmldbUriFor(id);
            if (XMLResource.RESOURCE_TYPE.equals(type)) {
                return new RemoteXMLResource(this, -1, -1, newId, Optional.empty());
            } else if (BinaryResource.RESOURCE_TYPE.equals(type)) {
                return new RemoteBinaryResource(this, newId);
            } else {
                throw new XMLDBException(ErrorCodes.UNKNOWN_RESOURCE_TYPE, "Unknown resource type: " + type);
            }
        } catch (final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI, e.getMessage(), e);
        }
    }

    @Override
    public Collection getChildCollection(final String name) throws XMLDBException {
        try {
            return getChildCollection(XmldbURI.xmldbUriFor(name));
        } catch (final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI, e.getMessage(), e);
        }
    }

    public Collection getChildCollection(final XmldbURI name) throws XMLDBException {
        // AF: get the child collection refreshing cache from server if not found
        return getChildCollection(name, true);
    }

    // AF: NEW METHOD
    protected Collection getChildCollection(final XmldbURI name, final boolean refreshCacheIfNotFound) throws XMLDBException {
        return instance(apiClient, this, name.numSegments() > 1 ? name : getPathURI().append(name));
    }

    @Override
    public int getChildCollectionCount() throws XMLDBException {
        return listChildCollections().length;
    }

    @Override
    public String getName() {
        return path.toString();
    }

    @Override
    public Collection getParentCollection() {
        if (collection == null && !path.equals(XmldbURI.ROOT_COLLECTION_URI)) {
            final XmldbURI parentUri = path.removeLastSegment();
            return new RemoteCollection(apiClient, null, parentUri);
        }
        return collection;
    }

    public String getPath() {
        return getPathURI().toString();
    }

    @Override
    public XmldbURI getPathURI() {
        if (collection == null) {
            return XmldbURI.ROOT_COLLECTION_URI;
        }
        return path;
    }

    @Override
    public String getProperty(final String property) {
        if (properties == null) {
            return null;
        }
        return (String) properties.get(property);
    }

    public Properties getProperties() {
        if (properties == null) {
            properties = new Properties();
        }
        return properties;
    }

    public void setProperties(final Properties properties) {
        this.properties = properties;
    }

    @Override
    public int getResourceCount() throws XMLDBException {
        try {
            return apiClient.getResourceCount(getPath());
        } catch (final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI, e.getMessage(), e);
        } catch (final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }
    }

    @Override
    public Service getService(final String name, final String version) throws XMLDBException {
        final Service service;
        switch (name) {
            case "XPathQueryService":
            case "XQueryService":
                service = new RemoteXPathQueryService(this);
                break;

            case "CollectionManagementService":
            case "CollectionManager":
                service = new RemoteCollectionManagementService(this, apiClient);
                break;

            case "UserManagementService":
                service = new RemoteUserManagementService(this);
                break;

            case "DatabaseInstanceManager":
                service = new RemoteDatabaseInstanceManager(apiClient);
                break;

            case "IndexQueryService":
                service = new RemoteIndexQueryService(apiClient, this);
                break;

            case "XUpdateQueryService":
                service = new RemoteXUpdateQueryService(this);
                break;

            default:
                throw new XMLDBException(ErrorCodes.NO_SUCH_SERVICE);
        }
        return service;
    }

    @Override
    public Service[] getServices() {
        return new Service[]{
            new RemoteXPathQueryService(this),
            new RemoteCollectionManagementService(this, apiClient),
            new RemoteUserManagementService(this),
            new RemoteDatabaseInstanceManager(apiClient),
            new RemoteIndexQueryService(apiClient, this),
            new RemoteXUpdateQueryService(this)
        };
    }

    protected boolean hasChildCollection(final String name) throws XMLDBException {
        for (final String child : listChildCollections()) {
            if (child.equals(name)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isOpen() {
        return true;
    }

    @Override
    public String[] listChildCollections() throws XMLDBException {
        try {
            final List<String> collections = apiClient.getCollectionListing(getPath());
            return collections.toArray(new String[collections.size()]);
        } catch (final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI, e.getMessage(), e);
        } catch (final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }
    }

    @Override
    public String[] getChildCollections() throws XMLDBException {
        return listChildCollections();
    }

    @Override
    public String[] listResources() throws XMLDBException {
        try {
            final List<String> resources = apiClient.getDocumentListing(getPath());
            return resources.toArray(new String[resources.size()]);
        } catch (final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI, e.getMessage(), e);
        } catch (final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }
    }

    @Override
    public String[] getResources() throws XMLDBException {
        return listResources();
    }

    public Permission getSubCollectionPermissions(final String name) throws PermissionDeniedException, XMLDBException {
        try {
            final Map<String, Object> result = apiClient.getSubCollectionPermissions(getPath(), name);

            final String owner = (String) result.get("owner");
            final String group = (String) result.get("group");
            final int mode = (Integer) result.get("permissions");
            final Stream<ACEAider> aces = extractAces(result.get("acl"));

            return getPermission(owner, group, mode, aces);
        } catch (final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI, e.getMessage(), e);
        } catch (final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        }
    }

    public Permission getSubResourcePermissions(final String name) throws PermissionDeniedException, XMLDBException {
        try {
            final Map<String, Object> result = apiClient.getSubResourcePermissions(getPath(), name);

            final String owner = (String) result.get("owner");
            final String group = (String) result.get("group");
            final int mode = (Integer) result.get("permissions");
            final Stream<ACEAider> aces = extractAces(result.get("acl"));

            return getPermission(owner, group, mode, aces);
        } catch (final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI, e.getMessage(), e);
        } catch (final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        }
    }

    public Long getSubCollectionCreationTime(final String name) throws XMLDBException {
        try {
            return apiClient.getSubCollectionCreationTime(getPath(), name);
        } catch (final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI, e.getMessage(), e);
        } catch (final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }
    }

    @Override
    public Resource getResource(final String name) throws XMLDBException {
        XmldbURI docUri;
        try {
            docUri = XmldbURI.xmldbUriFor(name);
        } catch (final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI, e.getMessage(), e);
        }

        final Map<String, Object> hash;
        try {
            hash = apiClient.describeResource(getPathURI().append(docUri).toString());
        } catch (final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }

        final String docName = (String) hash.get("name");
        if (docName == null) {
            return null; // resource does not exist!
        }
        try {
            docUri = XmldbURI.xmldbUriFor(docName).lastSegment();
        } catch (final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI, e.getMessage(), e);
        }
        final String owner = (String) hash.get("owner");
        final String group = (String) hash.get("group");
        final int mode = (Integer) hash.get("permissions");
        final Stream<ACEAider> aces = extractAces(hash.get("acl"));

        final Permission perm;
        try {
            perm = getPermission(owner, group, mode, aces);
        } catch (final PermissionDeniedException pde) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, "Unable to retrieve permissions for resource '" + name + "': " + pde.getMessage(), pde);
        }
        final String type = (String) hash.get("type");
        long contentLen = 0;
        if (hash.containsKey("content-length-64bit")) {
            final Object o = hash.get("content-length-64bit");
            if (o instanceof Long) {
                contentLen = (Long) o;
            } else {
                contentLen = Long.parseLong((String) o);
            }
        } else if (hash.containsKey("content-length")) {
            contentLen = (Integer) hash.get("content-length");
        }

        final AbstractRemoteResource r;
        if (type == null || "XMLResource".equals(type)) {
            r = new RemoteXMLResource(this, -1, -1, docUri, Optional.empty());
        } else {
            r = new RemoteBinaryResource(this, docUri);
        }
        r.setPermissions(perm);
        r.setContentLength(contentLen);
        r.dateCreated = (Date) hash.get("created");
        r.dateModified = (Date) hash.get("modified");
        if (hash.containsKey("mime-type")) {
            r.setMimeType((String) hash.get("mime-type"));
        }
        return r;
    }

    public void registerService(final Service serv) throws XMLDBException {
        throw new XMLDBException(ErrorCodes.NOT_IMPLEMENTED);
    }

    @Override
    public void removeResource(final Resource res) throws XMLDBException {
        try {
            apiClient.remove(getPathURI().append(XmldbURI.xmldbUriFor(res.getId())).toString());
        } catch (final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI, e.getMessage(), e);
        } catch (final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }
    }

    @Override
    public Date getCreationTime() throws XMLDBException {
        try {
            return apiClient.getCreationDate(getPath());
        } catch (final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI, e.getMessage(), e);
        } catch (final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }
    }

    @Override
    public void setProperty(final String property, final String value) {
        if (properties == null) {
            properties = new Properties();
        }
        properties.setProperty(property, value);
    }

    @Override
    public void storeResource(final Resource res) throws XMLDBException {
        storeResource(res, null, null);
    }

    @Override
    public void storeResource(final Resource res, final Date a, final Date b) throws XMLDBException {
        final Object content = (res instanceof ExtendedResource) ? ((ExtendedResource) res).getExtendedContent() : res.getContent();
        if (content instanceof File || content instanceof InputSource) {
            long fileLength = -1;
            if (content instanceof File) {
                final File file = (File) content;
                if (!file.canRead()) {
                    throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, "Failed to read resource from file " + file.getAbsolutePath());
                }
                fileLength = file.length();
            } else if (content instanceof EXistInputSource) {
                fileLength = ((EXistInputSource) content).getByteStreamLength();
            }
            if (res instanceof AbstractRemoteResource) {
                ((AbstractRemoteResource) res).dateCreated = a;
                ((AbstractRemoteResource) res).dateModified = b;
            }
            if (!BinaryResource.RESOURCE_TYPE.equals(res.getResourceType()) && fileLength != -1
                    && fileLength < MAX_CHUNK_LENGTH) {
                store((RemoteXMLResource) res);
            } else {
                uploadAndStore(res);
            }
        } else if(res instanceof AbstractRemoteResource) {
            ((AbstractRemoteResource) res).dateCreated = a;
            ((AbstractRemoteResource) res).dateModified = b;
            if (XMLResource.RESOURCE_TYPE.equals(res.getResourceType())) {
                store((RemoteXMLResource) res);
            } else {
                store((RemoteBinaryResource) res);
            }
        }
    }

    private void store(final RemoteXMLResource res) throws XMLDBException {
        try {
            final byte[] data = res.getData();
            if (res.getCreationTime() != null) {
                apiClient.parse(data, getPathURI().append(XmldbURI.xmldbUriFor(res.getId())).toString(), 1, res.getCreationTime(), res.getLastModificationTime());
            } else {
                apiClient.parse(data, getPathURI().append(XmldbURI.xmldbUriFor(res.getId())).toString(), 1);
            }
        } catch (final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI, e.getMessage(), e);
        } catch (final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }
    }

    private void store(final RemoteBinaryResource res) throws XMLDBException {
        try {
            final byte[] data = (byte[]) res.getContent();
            if (res.getCreationTime() != null) {
                apiClient.storeBinary(data, getPathURI().append(XmldbURI.xmldbUriFor(res.getId())).toString(), res.getMimeType(), true, res.getCreationTime(), res.getLastModificationTime());
            } else {
                apiClient.storeBinary(data, getPathURI().append(XmldbURI.xmldbUriFor(res.getId())).toString(), res.getMimeType(), true);
            }
        } catch (final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI, e.getMessage(), e);
        } catch (final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }
    }

    private void uploadAndStore(final Resource res) throws XMLDBException {
        InputStream is = null;
        String descString = "<unknown>";
        try {
            if (res instanceof RemoteBinaryResource) {
                is = ((RemoteBinaryResource) res).getStreamContent();
                descString = ((RemoteBinaryResource) res).getStreamSymbolicPath();
            } else {
                final Object content = res.getContent();
                if (content instanceof File) {
                    final File file = (File) content;
                    try {
                        is = new BufferedInputStream(new FileInputStream(file));
                    } catch (final FileNotFoundException e) {
                        throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, "could not read resource from file " + file.getAbsolutePath(), e);
                    }
                } else if (content instanceof InputSource) {
                    is = ((InputSource) content).getByteStream();
                    if (content instanceof EXistInputSource) {
                        descString = ((EXistInputSource) content).getSymbolicPath();
                    }
                }
            }

            final byte[] chunk = new byte[MAX_UPLOAD_CHUNK];
            try {
                int len;
                String fileName = null;
                byte[] compressed;
                while ((len = is.read(chunk)) > -1) {
                    compressed = Compressor.compress(chunk, len);

                    //params = new ArrayList<>();
                    if (fileName != null) {
                        fileName = apiClient.uploadCompressed(fileName, compressed, len);
                    } else {
                        fileName = apiClient.uploadCompressed(compressed, len);
                    }
                }

                // Zero length stream? Let's get a fileName!
                if (fileName == null) {
                    compressed = Compressor.compress(new byte[0], 0);
                    fileName = apiClient.uploadCompressed(compressed, 0);
                }

                final String resURI = getPathURI().append(XmldbURI.xmldbUriFor(res.getId())).toString();
                final EXistResource rxres = (EXistResource) res;
                if (rxres.getCreationTime() != null) {
                    apiClient.parseLocalExt(fileName, resURI, true, rxres.getMimeType(), XMLResource.RESOURCE_TYPE.equals(res.getResourceType()), rxres.getCreationTime(), rxres.getLastModificationTime());
                } else {
                    apiClient.parseLocalExt(fileName, resURI, true, rxres.getMimeType(), XMLResource.RESOURCE_TYPE.equals(res.getResourceType()));
                }

            } catch (final IOException e) {
                throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, "failed to read resource from " + descString, e);
            } catch (final URISyntaxException e) {
                throw new XMLDBException(ErrorCodes.INVALID_URI, e.getMessage(), e);
            } catch (final EXistException e) {
                throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
            } catch (final PermissionDeniedException e) {
                throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
            } catch (final SAXException e) {
                throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, e.getMessage(), e);
            }
        } finally {
            if(is != null) {
                try {
                    is.close();
                } catch (final IOException ioe) {
                    LOG.warn(ioe.getMessage(), ioe);
                }
            }
        }
    }

    @Override
    public boolean isRemoteCollection() {
        return true;
    }

    @Override
    public void setTriggersEnabled(final boolean triggersEnabled) throws XMLDBException {
        try {
            apiClient.setTriggersEnabled(getPath(), Boolean.toString(triggersEnabled));
        } catch (final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }
    }
}
