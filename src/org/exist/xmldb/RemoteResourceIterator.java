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
import java.net.URISyntaxException;
import java.util.*;

import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceIterator;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

import javax.xml.transform.OutputKeys;

public class RemoteResourceIterator implements ResourceIterator {

    private final RemoteCollection collection;
    private final List<Object> resources;
    private final int indentXML;
    private final String encoding;
    private int pos = 0;

    public RemoteResourceIterator(final RemoteCollection collection, final List<Object> resources, final int indentXML, final String encoding) {
        this.collection = collection;
        this.resources = resources;
        this.indentXML = indentXML;
        this.encoding = encoding;
	}

    public int getLength() {
        return resources.size();
    }

    @Override
    public boolean hasMoreResources() {
        return pos < resources.size();
    }

    public void setNext(int next) {
        pos = next;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Resource nextResource() throws XMLDBException {
        if (pos >= resources.size()) {
            return null;
        }
        // node or value?
        if (resources.get(pos) instanceof List) {
            // node
            final List<String> v = (List<String>) resources.get(pos++);
            final String doc = v.get(0);
            final String s_id = v.get(1);
            final Map<String, Object> parameters = new HashMap<>();
            parameters.put(OutputKeys.INDENT, indentXML > 0 ? "yes" : "no");
            parameters.put(OutputKeys.ENCODING, encoding);

            try {
                final byte[] data = collection.getClient().retrieve(doc, s_id, parameters);
                final XMLResource res = new RemoteXMLResource(collection, XmldbURI.xmldbUriFor(doc), Optional.of(doc + "_" + s_id));
                res.setContent(new String(data, encoding));
                return res;
            } catch (final EXistException | IOException e) {
                throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
            } catch (final PermissionDeniedException e) {
                throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
            } catch (final URISyntaxException e) {
                throw new XMLDBException(ErrorCodes.INVALID_URI, e.getMessage(), e);
            }
        } else {
            // value
            final XMLResource res = new RemoteXMLResource(collection, null, Optional.of(Integer.toString(pos)));
            res.setContent(resources.get(pos++));
            return res;
        }
    }
}
