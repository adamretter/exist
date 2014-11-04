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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.collections.triggers;

import java.util.List;
import java.util.Map;

import org.exist.Indexer;
import org.exist.collections.Collection;
import org.exist.storage.DBBroker;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.ErrorHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.ext.LexicalHandler;

/**
 * Abstract default implementation of a Trigger. This implementation just
 * forwards all SAX events to the output content handler.
 * 
 * @author wolf
 */
public abstract class SAXTrigger implements DocumentTrigger, ContentHandler, LexicalHandler, ErrorHandler {

    private Collection collection = null;
    private ThreadLocal<Boolean> localValidating = new ThreadLocalBoolean();

    /**
     * Events are either forwarded to the nextTrigger or the localIndexer
     */
    private SAXTrigger nextTrigger = null;
    private final static ThreadLocal<Indexer> localIndexer = new ThreadLocal<>();

    protected void setNext(final SAXTrigger trigger) {
        this.nextTrigger = trigger;
    }

    protected void setNext(final Indexer indexer) {
        localIndexer.set(indexer);
    }

    protected ContentHandler getNextContentHandler() {
        if(nextTrigger != null) {
            return nextTrigger;
        } else {
            return localIndexer.get();
        }
    }

    protected LexicalHandler getNextLexicalHandler() {
        if(nextTrigger != null) {
            return nextTrigger;
        } else {
            return localIndexer.get();
        }
    }

    protected ErrorHandler getNextErrorHandler() {
        if(nextTrigger != null) {
            return nextTrigger;
        } else {
            return localIndexer.get();
        }
    }

    protected Collection getCollection() {
        return collection;
    }

    /**
     * Configure the trigger. The default implementation just stores the parent
     * collection reference into the field {@link #collection collection}. Use
     * method {@link #getCollection() getCollection} to later retrieve the
     * collection.
     */
    @Override
    public void configure(final DBBroker broker, final Collection collection, final Map<String, List<?>> parameters) throws TriggerException {
        this.collection = collection;
    }

    @Override
    public void setValidating(final boolean validating) {
        this.localValidating.set(validating);
    }

    @Override
    public boolean isValidating() {
        return localValidating.get();
    }

    @Override
    public void setDocumentLocator(final Locator locator) {
        final ContentHandler nextContentHandler = getNextContentHandler();
        if(nextContentHandler != null) {
            nextContentHandler.setDocumentLocator(locator);
        }
    }

    @Override
    public void startDocument() throws SAXException {
        final ContentHandler nextContentHandler = getNextContentHandler();
        if(nextContentHandler != null) {
            nextContentHandler.startDocument();
        }
    }

    @Override
    public void endDocument() throws SAXException {
        final ContentHandler nextContentHandler = getNextContentHandler();
        if(nextContentHandler != null) {
            nextContentHandler.endDocument();
        }
    }

    @Override
    public void startPrefixMapping(final String prefix, final String namespaceURI) throws SAXException {
        final ContentHandler nextContentHandler = getNextContentHandler();
        if(nextContentHandler != null) {
            nextContentHandler.startPrefixMapping(prefix, namespaceURI);
        }
    }

    @Override
    public void endPrefixMapping(final String prefix) throws SAXException {
        final ContentHandler nextContentHandler = getNextContentHandler();
        if(nextContentHandler != null) {
            nextContentHandler.endPrefixMapping(prefix);
        }
    }

    @Override
    public void startElement(final String namespaceURI, final String localName, final String qname, final Attributes attributes) throws SAXException {
        final ContentHandler nextContentHandler = getNextContentHandler();
        if(nextContentHandler != null) {
            nextContentHandler.startElement(namespaceURI, localName, qname, attributes);
        }
    }

    @Override
    public void endElement(final String namespaceURI, final String localName, final String qname) throws SAXException {
        final ContentHandler nextContentHandler = getNextContentHandler();
        if(nextContentHandler != null) {
            nextContentHandler.endElement(namespaceURI, localName, qname);
        }
    }

    @Override
    public void characters(final char[] ch, final int start, final int length) throws SAXException {
        final ContentHandler nextContentHandler = getNextContentHandler();
        if(nextContentHandler != null) {
            nextContentHandler.characters(ch, start, length);
        }
    }

    @Override
    public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
        final ContentHandler nextContentHandler = getNextContentHandler();
        if(nextContentHandler != null) {
            nextContentHandler.ignorableWhitespace(ch, start, length);
        }
    }

    @Override
    public void processingInstruction(final String target, final String data) throws SAXException {
        final ContentHandler nextContentHandler = getNextContentHandler();
        if(nextContentHandler != null) {
            nextContentHandler.processingInstruction(target, data);
        }
    }

    @Override
    public void skippedEntity(final String name) throws SAXException {
        final ContentHandler nextContentHandler = getNextContentHandler();
        if(nextContentHandler != null) {
            nextContentHandler.skippedEntity(name);
        }
    }

    @Override
    public void startDTD(final String name, final String publicId, final String systemId) throws SAXException {
        final LexicalHandler nextLexicalHandler = getNextLexicalHandler();
        if(nextLexicalHandler != null) {
            nextLexicalHandler.startDTD(name, publicId, systemId);
        }
    }

    @Override
    public void endDTD() throws SAXException {
        final LexicalHandler nextLexicalHandler = getNextLexicalHandler();
        if(nextLexicalHandler != null) {
            nextLexicalHandler.endDTD();
        }
    }

    @Override
    public void startEntity(final String name) throws SAXException {
        final LexicalHandler nextLexicalHandler = getNextLexicalHandler();
        if(nextLexicalHandler != null) {
            nextLexicalHandler.startEntity(name);
        }
    }

    @Override
    public void endEntity(final String name) throws SAXException {
        final LexicalHandler nextLexicalHandler = getNextLexicalHandler();
        if(nextLexicalHandler != null) {
            nextLexicalHandler.endEntity(name);
        }
    }

    @Override
    public void startCDATA() throws SAXException {
        final LexicalHandler nextLexicalHandler = getNextLexicalHandler();
        if(nextLexicalHandler != null) {
            nextLexicalHandler.startCDATA();
        }
    }

    @Override
    public void endCDATA() throws SAXException {
        final LexicalHandler nextLexicalHandler = getNextLexicalHandler();
        if(nextLexicalHandler != null) {
            nextLexicalHandler.endCDATA();
        }
    }

    @Override
    public void comment(final char[] ch, final int start, final int length) throws SAXException {
        final LexicalHandler nextLexicalHandler = getNextLexicalHandler();
        if(nextLexicalHandler != null) {
            nextLexicalHandler.comment(ch, start, length);
        }
    }
    
    public void warning(final SAXParseException exception) throws SAXException {
        final ErrorHandler nextErrorHandler = getNextErrorHandler();
        if(nextErrorHandler != null) {
            nextErrorHandler.warning(exception);
        }
    }

    public void error(final SAXParseException exception) throws SAXException {
        final ErrorHandler nextErrorHandler = getNextErrorHandler();
        if(nextErrorHandler != null) {
            nextErrorHandler.error(exception);
        }
    }
    
    public void fatalError(final SAXParseException exception) throws SAXException {
        final ErrorHandler nextErrorHandler = getNextErrorHandler();
        if(nextErrorHandler != null) {
            nextErrorHandler.fatalError(exception);
        }
    }
}
