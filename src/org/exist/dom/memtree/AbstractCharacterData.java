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
package org.exist.dom.memtree;

import org.exist.xquery.NodeTest;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Sequence;
import org.w3c.dom.CharacterData;
import org.w3c.dom.DOMException;
import org.w3c.dom.Node;


public abstract class AbstractCharacterData extends NodeImpl implements CharacterData {

    public AbstractCharacterData(final DocumentImpl doc, final int nodeNumber) {
        super(doc, nodeNumber);
    }

    @Override
    public int getLength() {
        return document.alphaLen[nodeNumber];
    }

    @Override
    public String getData() throws DOMException {
        return new String(document.characters, document.alpha[nodeNumber],
            document.alphaLen[nodeNumber]);
    }

    @Override
    public String substringData(final int offset, final int count) throws DOMException {
        if(offset < 0 || count < 0) {
            throw new DOMException(DOMException.INDEX_SIZE_ERR, "offset is out of bounds");
        }

        final int length = document.alphaLen[nodeNumber];
        final int inDocOffset = document.alpha[nodeNumber];
        if(offset > length) {
            throw new DOMException(DOMException.INDEX_SIZE_ERR, "offset is out of bounds");
        }

        if(offset + count > length) {
            return new String(document.characters, inDocOffset + offset, length - offset);
        } else {
            return new String(document.characters, inDocOffset + offset, length);
        }
    }

    @Override
    public void replaceData(final int offset, final int count, final String arg) throws DOMException {
        if(offset < 0 || count < 0) {
            throw new DOMException(DOMException.INDEX_SIZE_ERR, "offset is out of bounds");
        }
        throw new UnsupportedOperationException("Operation is unsupported on node type: " + this.getNodeType());
    }

    @Override
    public void insertData(final int offset, final String arg) throws DOMException {
        if(offset < 0) {
            throw new DOMException(DOMException.INDEX_SIZE_ERR, "offset is out of bounds");
        }

        throw new UnsupportedOperationException("Operation is unsupported on node type: " + this.getNodeType());
    }

    @Override
    public void appendData(final String arg) throws DOMException {
        if(arg == null || arg.length() == 0) {
            return;
        }

        final int len = arg.length();
        final int existingDataOffset = document.alpha[nodeNumber];
        final int existingDataLen = document.alphaLen[nodeNumber];

        // expand space for existing data and set

        // 1) create a new array of the correct size for the data
        final int existingCharactersLen = document.characters.length;
        final int extraRequired = len;
        final int newCharactersLen = existingCharactersLen + extraRequired;
        final char newCharacters[] = new char[newCharactersLen];

        // 2) copy everything from data to newData that is upto the end of our offset + len
        System.arraycopy(document.characters, 0, newCharacters, 0, existingDataOffset + existingDataLen);

        // 3) insert our new data after the existing data
        System.arraycopy(arg.toCharArray(), 0, newCharacters, existingDataOffset + existingDataLen, len);

        // 4) copy everything from data to newData that is after our our offset + len
        final int remainingExistingCharacters = existingCharactersLen - (existingDataOffset + existingDataLen);
        System.arraycopy(document.characters, existingDataOffset + existingDataLen, newCharacters, existingDataOffset + existingDataLen + len, remainingExistingCharacters);

        // 5) replace document.characters with our new characters
        document.characters = newCharacters;
        document.alphaLen[nodeNumber] = existingDataLen + len;

        // 6) renumber all offsets following our offset
        for(int i = nodeNumber + 1; i < document.alpha.length; i++) {
            document.alpha[i] += extraRequired;
        }
    }

    @Override
    public void setData(String data) throws DOMException {
        if(data == null) {
            data = "";
        }

        final int len = data.length();
        final int existingDataOffset = document.alpha[nodeNumber];
        final int existingDataLen = document.alphaLen[nodeNumber];

        if(len <= existingDataLen) {
            // replace existing data

            System.arraycopy(data.toCharArray(), 0, document.characters, existingDataOffset, len);
            document.alphaLen[nodeNumber] = len;

        } else {
            // expand space for existing data and set

            // 1) create a new array of the correct size for the data
            final int existingCharactersLen = document.characters.length;
            final int extraRequired = len - existingDataLen;
            final int newCharactersLen = existingCharactersLen + extraRequired;
            final char newCharacters[] = new char[newCharactersLen];

            // 2) copy everything from data to newData that is before our offset
            System.arraycopy(document.characters, 0, newCharacters, 0, existingDataOffset);

            // 3) insert our new data
            System.arraycopy(data.toCharArray(), 0, newCharacters, existingDataOffset, len);

            // 4) copy everything from data to newData that is after our offset
            final int remainingExistingCharacters = existingCharactersLen - (existingDataOffset + existingDataLen);
            System.arraycopy(document.characters, existingDataOffset + existingDataLen, newCharacters, existingDataOffset + len, remainingExistingCharacters);

            // 5) replace document.characters with our new characters
            document.characters = newCharacters;
            document.alphaLen[nodeNumber] = len;


            // 6) renumber all offsets following our offset
            for(int i = nodeNumber + 1; i < document.alpha.length; i++) {
                document.alpha[i] += extraRequired;
            }
        }
    }

    @Override
    public void deleteData(final int offset, final int count) throws DOMException {
        if(offset < 0 || count < 0) {
            throw new DOMException(DOMException.INDEX_SIZE_ERR, "offset is out of bounds");
        }

        final int length = document.alphaLen[nodeNumber];
        if(offset > length) {
            throw new DOMException(DOMException.INDEX_SIZE_ERR, "offset is out of bounds");
        }

        final int inDocOffset = document.alpha[nodeNumber];
        if(offset > length) {
            throw new DOMException(DOMException.INDEX_SIZE_ERR, "offset is out of bounds");
        }

        if(offset + count > length) {
            document.alpha[nodeNumber] = inDocOffset + offset;
            document.alphaLen[nodeNumber] = length - offset;
        } else {
            document.alpha[nodeNumber] = inDocOffset + offset;
            document.alphaLen[nodeNumber] = count;
        }
    }

    @Override
    public String getNodeValue() throws DOMException {
        return getData();
    }

    @Override
    public void setNodeValue(final String nodeValue) throws DOMException {
        setData(nodeValue);
    }

    @Override
    public String getTextContent() throws DOMException {
        return getNodeValue();
    }

    @Override
    public void setTextContent(final String textContent) throws DOMException {
        setNodeValue(textContent);
    }

    @Override
    public String getStringValue() {
        return getData();
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
