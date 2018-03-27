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

import org.exist.security.PermissionDeniedException;
import org.exist.xmlrpc.RpcAPI;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;

public class RemoteDatabaseInstanceManager implements DatabaseInstanceManager {

    final RpcAPI apiClient;

    /**
     * Constructor for DatabaseInstanceManagerImpl.
     */
    public RemoteDatabaseInstanceManager(final RpcAPI apiClient) {
        this.apiClient = apiClient;
    }

    @Override
    public String getName() {
        return "DatabaseInstanceManager";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public boolean isLocalInstance() {
        return false;
    }

    @Override
    public void shutdown() throws XMLDBException {
        try {
            apiClient.shutdown();
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, "shutdown failed", e);
        }
    }

    @Override
    public void shutdown(final long delay) throws XMLDBException {
        try {
            if (delay > 0) {
                apiClient.shutdown(delay);
            } else {
                apiClient.shutdown();
            }
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, "shutdown failed", e);
        }
    }

    @Override
    public boolean enterServiceMode() throws XMLDBException {
        try {
            apiClient.enterServiceMode();
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, "Failed to switch db to service mode: " + e.getMessage(), e);
        }
        return true;
    }

    @Override
    public void exitServiceMode() throws XMLDBException {
        try {
            apiClient.exitServiceMode();
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, "Failed to switch db to service mode: " + e.getMessage(), e);
        }
    }

    @Override
    public void setCollection(final Collection collection) {
    }

    @Override
    public String getProperty(final String name) {
        return null;
    }

    @Override
    public void setProperty(final String name, final String value) {
    }

    @Override
    public DatabaseStatus getStatus() throws XMLDBException {
        throw new XMLDBException(ErrorCodes.NOT_IMPLEMENTED, "this method is not available for remote connections");
    }
}
