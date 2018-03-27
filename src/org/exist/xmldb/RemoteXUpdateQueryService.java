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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.util.LockException;
import org.exist.xquery.XPathException;
import org.xml.sax.SAXException;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XUpdateQueryService;

import static java.nio.charset.StandardCharsets.UTF_8;

public class RemoteXUpdateQueryService implements XUpdateQueryService {

	private final static Logger LOG = LogManager.getLogger(RemoteXUpdateQueryService.class);

    private RemoteCollection parent;

    public RemoteXUpdateQueryService(final RemoteCollection parent) {
        this.parent = parent;
    }

    @Override
    public String getName() {
        return "XUpdateQueryService";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public long update(final String commands) throws XMLDBException {
        if(LOG.isDebugEnabled()) {
            LOG.debug("processing xupdate:\n" + commands);
        }

        final byte[] xupdateData = commands.getBytes(UTF_8);
        try {
            final int mods = parent.getClient().xupdate(parent.getPath(), xupdateData);

            if(LOG.isDebugEnabled()) {
                LOG.debug("processed " + mods + " modifications");
            }

            return mods;
        } catch (final EXistException | SAXException | LockException | XPathException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }
    }

    @Override
    public long updateResource(final String id, final String commands) throws XMLDBException {
        if(LOG.isDebugEnabled()) {
            LOG.debug("processing xupdate:\n" + commands);
        }
        final byte[] xupdateData = commands.getBytes(UTF_8);
        try {
            final int mods = parent.getClient().xupdateResource(parent.getPath() + "/" + id, xupdateData);
            if(LOG.isDebugEnabled()) {
                LOG.debug("processed " + mods + " modifications");
            }
            return mods;
        } catch (final EXistException | SAXException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }
    }


    @Override
    public void setCollection(final Collection collection) {
        parent = (RemoteCollection) collection;
    }

    @Override
    public String getProperty(final String name) {
        return null;
    }

    @Override
    public void setProperty(final String name, final String value) {
    }
}
