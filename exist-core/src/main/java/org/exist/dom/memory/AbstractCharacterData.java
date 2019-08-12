/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2014 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *
 *  $Id$
 */
package org.exist.dom.memory;

import net.sf.saxon.dom.NodeOverNodeInfo;
import net.sf.saxon.tree.tiny.TinyNodeImpl;
import org.exist.xquery.NodeTest;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Sequence;
import org.w3c.dom.CharacterData;
import org.w3c.dom.DOMException;
import org.w3c.dom.Node;


public abstract class AbstractCharacterData<T extends org.exist.dom.memory.AbstractCharacterData, N extends org.w3c.dom.CharacterData>
        extends NodeImpl<T, N> implements CharacterData {

    AbstractCharacterData(final TinyTreeWithId tinyTreeWithId, final int nodeNr) {
        super(tinyTreeWithId, nodeNr);
    }

    AbstractCharacterData(final TinyTreeWithId tinyTreeWithId, final TinyNodeImpl tinyNode) {
        super(tinyTreeWithId, tinyNode);
    }

    AbstractCharacterData(final TinyTreeWithId tinyTreeWithId, final NodeOverNodeInfo nodeOverNodeInfo) {
        super(tinyTreeWithId, nodeOverNodeInfo);
    }

    @Override
    public int getLength() {
        return node.getLength();
    }

    @Override
    public String getData() throws DOMException {
        return node.getData();
    }

    @Override
    public String substringData(final int offset, final int count) throws DOMException {
        return node.substringData(offset, count);
    }

    @Override
    public void replaceData(final int offset, int count, final String arg) throws DOMException {
        node.replaceData(offset, count, arg);
    }

    @Override
    public void insertData(final int offset, final String arg) throws DOMException {
        node.insertData(offset, arg);
    }

    @Override
    public void appendData(final String arg) throws DOMException {
        node.appendData(arg);
    }

    @Override
    public void setData(final String data) throws DOMException {
        node.setData(data);
    }

    @Override
    public void deleteData(final int offset, int count) throws DOMException {
        node.deleteData(offset, count);
    }

    @Override
    public String getNodeValue() throws DOMException {
        return node.getNodeValue();
    }

    @Override
    public void setNodeValue(final String nodeValue) throws DOMException {
        node.setNodeValue(nodeValue);
    }

    @Override
    public String getTextContent() throws DOMException {
        return node.getTextContent();
    }

    @Override
    public void setTextContent(final String textContent) throws DOMException {
        node.setTextContent(textContent);
    }

    @Override
    public Node getFirstChild() {
        return null;
    }

    @Override
    public void selectAttributes(final NodeTest test, final Sequence result)
        throws XPathException {

    }

    @Override
    public void selectChildren(final NodeTest test, final Sequence result)
        throws XPathException {
    }

    @Override
    public void selectDescendantAttributes(final NodeTest test, final Sequence result)
        throws XPathException {
    }
}
