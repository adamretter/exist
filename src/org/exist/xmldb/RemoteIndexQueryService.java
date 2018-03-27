/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2015 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xmldb;

import java.net.URISyntaxException;
import java.util.List;
import java.util.stream.Stream;

import org.exist.EXistException;
import org.exist.dom.QName;
import org.exist.security.PermissionDeniedException;
import org.exist.util.Occurrences;
import org.exist.xmlrpc.RpcAPI;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;

public class RemoteIndexQueryService extends AbstractRemote implements IndexQueryService {

    private final RpcAPI apiClient;

    public RemoteIndexQueryService(final RpcAPI apiClient, final RemoteCollection parent) {
        super(parent);
        this.apiClient = apiClient;
    }

    @Override
    public String getName() {
        return "IndexQueryService";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public void reindexCollection() throws XMLDBException {
        reindexCollection(collection.getPath());
    }

    /**
     * @deprecated {@link org.exist.xmldb.IndexQueryService#reindexCollection(org.exist.xmldb.XmldbURI)}
     */
    @Deprecated
    @Override
    public void reindexCollection(final String collectionPath) throws XMLDBException {
        try {
            reindexCollection(XmldbURI.xmldbUriFor(collectionPath));
        } catch (final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI, e.getMessage(), e);
        }
    }

    @Override
    public void reindexCollection(final XmldbURI collection) throws XMLDBException {
        final XmldbURI collectionPath = resolve(collection);
        try {
            apiClient.reindexCollection(collectionPath.toString());
        } catch (final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI, e.getMessage());
        } catch (final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }
    }

    @Override
    public void reindexDocument(final String name) throws XMLDBException {
        final XmldbURI collectionPath = resolve(collection.getPathURI());
        final XmldbURI documentPath = collectionPath.append(name);
        try {
            apiClient.reindexDocument(documentPath.toString());
        } catch (final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }
    }

    @Override
    public Occurrences[] getIndexedElements(final boolean inclusive) throws XMLDBException {
        try {
            final List<List> result = apiClient.getIndexedElements(collection.getPath(), inclusive);
            final Stream<Occurrences> occurrences = result.stream()
                    .map(row -> new Occurrences(new QName(row.get(0).toString(), row.get(1).toString(), row.get(2).toString()), (Integer) row.get(3)));
            return occurrences.toArray(Occurrences[]::new);
        } catch (final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI, e.getMessage(), e);
        } catch (final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }
    }

    @Override
    public void setCollection(final Collection collection) {
        this.collection = (RemoteCollection) collection;
    }

    @Override
    public String getProperty(final String name) {
        return null;
    }

    @Override
    public void setProperty(final String name, final String value) {
    }

    @Override
    public void configureCollection(final String configData) throws XMLDBException {
        try {
            apiClient.configureCollection(collection.getPath(), configData);
        } catch (final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }
    }
}
