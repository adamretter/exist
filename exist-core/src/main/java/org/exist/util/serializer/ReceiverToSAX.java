/*
*  eXist Open Source Native XML Database
*  Copyright (C) 2001-04 Wolfgang M. Meier (wolfgang@exist-db.org) 
*  and others (see http://exist-db.org)
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
package org.exist.util.serializer;

import org.exist.dom.INodeHandle;
import org.exist.dom.QName;
import org.w3c.dom.Document;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.ext.LexicalHandler;
import org.xml.sax.helpers.AttributesImpl;

/**
 * A wrapper class that forwards the method calls defined in the
 * {@link org.exist.util.serializer.Receiver} interface to a
 * SAX content handler and lexical handler.
 *
 * @author wolf
 */
public class ReceiverToSAX implements Receiver {

    private ContentHandler contentHandler;
    private LexicalHandler lexicalHandler = null;

    private final char[] charBuf = new char[2048];

    private DeferredStartElement deferredStartElement = null;

    /**
     * @param handler the content handler
     */
    public ReceiverToSAX(final ContentHandler handler) {
        super();
        this.contentHandler = handler;

        if (handler instanceof LexicalHandler) {
            lexicalHandler = (LexicalHandler) handler;
        }
    }

    public void setLexicalHandler(final LexicalHandler handler) {
        this.lexicalHandler = handler;
    }

    public void setContentHandler(final ContentHandler handler) {
        this.contentHandler = handler;
    }

    public ContentHandler getContentHandler() {
        return contentHandler;
    }

    public LexicalHandler getLexicalHandler() {
        return lexicalHandler;
    }

    @Override
    public void startDocument() throws SAXException {
        contentHandler.startDocument();
    }

    @Override
    public void endDocument() throws SAXException {
        contentHandler.endDocument();
    }

    @Override
    public void startPrefixMapping(final String prefix, final String namespaceURI)
            throws SAXException {
        contentHandler.startPrefixMapping(prefix, namespaceURI);
    }

    @Override
    public void endPrefixMapping(final String prefix) throws SAXException {
        contentHandler.endPrefixMapping(prefix);
    }

    @Override
    public void startElement(final QName qname, final AttrList attribs) throws SAXException {
        if (deferredStartElement != null) {
            deferredStartElement.apply();
            deferredStartElement = null;
        }

        deferredStartElement = new DeferredStartElement(qname);
        if (attribs != null) {
            for (int i = 0; i < attribs.getLength(); i++) {
                final QName attrQName = attribs.getQName(i);
                deferredStartElement.attributes.addAttribute(attrQName.getNamespaceURI(), attrQName.getLocalPart(), attrQName.getStringValue(),
                        "CDATA", attribs.getValue(i));
            }
        }
    }

    private class DeferredStartElement {
        final QName qname;
        final AttributesImpl attributes = new AttributesImpl();

        private DeferredStartElement(final QName qname) {
            this.qname = qname;
        }

        public void apply() throws SAXException {
            contentHandler.startElement(qname.getNamespaceURI(), qname.getLocalPart(), qname.getStringValue(), attributes);
        }
    }

    @Override
    public void endElement(final QName qname) throws SAXException {
        if (deferredStartElement != null) {
            deferredStartElement.apply();
            deferredStartElement = null;
        }

        contentHandler.endElement(qname.getNamespaceURI(), qname.getLocalPart(), qname.getStringValue());
    }

    @Override
    public void characters(final CharSequence seq) throws SAXException {
        if (deferredStartElement != null) {
            deferredStartElement.apply();
            deferredStartElement = null;
        }

        final int len = seq.length();
        if (len < charBuf.length) {
            for (int i = 0; i < len; i++) {
                charBuf[i] = seq.charAt(i);
            }
            contentHandler.characters(charBuf, 0, len);
        } else {
            contentHandler.characters(seq.toString().toCharArray(), 0, seq.length());
        }
    }

    @Override
    public void attribute(final QName qname, final String value) throws SAXException {
        if (deferredStartElement == null) {
            throw new SAXException("Cannot serialize an attribute by itself");
        }

        deferredStartElement.attributes.addAttribute(
                qname.getNamespaceURI(), qname.getLocalPart(), qname.getStringValue(),
                "CDATA", value);
    }

    @Override
    public void comment(final char[] ch, final int start, final int length) throws SAXException {
        if (deferredStartElement != null) {
            deferredStartElement.apply();
            deferredStartElement = null;
        }

        if (lexicalHandler != null) {
            lexicalHandler.comment(ch, start, length);
        }
    }

    @Override
    public void processingInstruction(final String target, final String data)
            throws SAXException {
        if (deferredStartElement != null) {
            deferredStartElement.apply();
            deferredStartElement = null;
        }

        contentHandler.processingInstruction(target, data);
    }

    @Override
    public void cdataSection(final char[] ch, final int start, final int len) throws SAXException {
        if (deferredStartElement != null) {
            deferredStartElement.apply();
            deferredStartElement = null;
        }

        if (lexicalHandler != null) {
            lexicalHandler.startCDATA();
        }
        contentHandler.characters(ch, start, len);
        if (lexicalHandler != null) {
            lexicalHandler.endCDATA();
        }
    }

    @Override
    public void documentType(final String name, final String publicId, final String systemId)
            throws SAXException {
        if (lexicalHandler != null) {
            lexicalHandler.startDTD(name, publicId, systemId);
            lexicalHandler.endDTD();
        }
    }

    @Override
    public void highlightText(final CharSequence seq) {
        // not supported with this receiver
    }

    @Override
    public void setCurrentNode(final INodeHandle node) {
        // just ignore
    }

    @Override
    public Document getDocument() {
        //just ignore
        return null;
    }
}
