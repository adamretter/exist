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

import org.exist.Indexer;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.xmldb.XmldbURI;
import org.xml.sax.*;
import org.xml.sax.ext.LexicalHandler;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/**
 * Represents a Group of Document Triggers
 *
 * These Triggers may be used across multiple threads
 * each Trigger is responsible for maintaining it's own
 * state.
 *
 * NOTE: Each time the group is re-used for a different {@see org.exist.Indexer}
 * then {@see setIndexer must be called)
 *
 * @author Adam Retter <adam.retter@googlemail.com>
 */
public class DocumentTriggersGroup extends AbstractTriggerGroup<LazyDocumentTrigger> implements DocumentTrigger, ContentHandler, LexicalHandler, ErrorHandler {

    public final static DocumentTriggersGroup EMPTY = new DocumentTriggersGroup(Collections.EMPTY_LIST);

    public DocumentTriggersGroup(final List<LazyDocumentTrigger> triggers) {
        super(triggers);
    }

    private SAXTrigger firstTrigger = null;
    private SAXTrigger lastTrigger = null;
    private final static ThreadLocal<Boolean> localValidating = new ThreadLocalBoolean();
    private final static ThreadLocal<Indexer> localIndexer = new ThreadLocal<>();

    private final AtomicInteger pipelineState = new AtomicInteger();
    private final static int PIPELINE_UNINITIALIZED = 0;
    private final static int PIPELINE_INITIALIZING = 1;
    private final static int PIPELINE_INITIALIZED = 2;
    private final static int PIPELINE_INITIALIZATION_FAILED = 3;

    /**
     * This function uses CAS to
     * ensure that {@see configureSaxPipeline} is only called once
     * even for multiple threads across this class instance
     */
    protected void configureSaxPipelineOnce() {
        //spin until we have attempted configuration once
        for(;;) {
            if(pipelineState.get() >= PIPELINE_INITIALIZED) {
                //we have configured already
                return;

            } else if(pipelineState.compareAndSet(PIPELINE_UNINITIALIZED, PIPELINE_INITIALIZING)) {
                try {
                    configureSaxPipeline();
                    pipelineState.set(PIPELINE_INITIALIZED);
                } catch(final TriggerException te) {
                    LOG.error("Unable to initialize SAX Trigger pipeline", te);
                    pipelineState.set(PIPELINE_INITIALIZATION_FAILED);
                }
            } else {
                //we are currently configuring, we need to wait/retry
                LockSupport.parkNanos(100);
            }
        }
    }

    /**
     * Configures the SAX Pipeline for this group instance
     * of Document Triggers (which may include SAXTriggers)
     *
     * This should only be called once for any thread
     * in this class instance {@see configureSaxPipelineOnce}
     */
    protected void configureSaxPipeline() throws TriggerException {
        for(final LazyDocumentTrigger lazyTrigger : getTriggers()) {
            final DocumentTrigger trigger = lazyTrigger.get();
            if(trigger instanceof SAXTrigger) {
                SAXTrigger filteringTrigger = (SAXTrigger) trigger;

                if(lastTrigger == null) {
                    firstTrigger = filteringTrigger;
                } else {
                    lastTrigger.setNext(filteringTrigger);
                }

                lastTrigger = filteringTrigger;
            }
        }

        //set the indexer
        if(lastTrigger != null) {
            lastTrigger.setNext(localIndexer.get());
        }
    }

    /**
     * Updates the ThreadLocal indexer
     *
     * WARN: This must be called for each new indexer operation
     * on this DocumentTriggersGroup as the filter pipeline
     * is reused and the indexer needs to be bound to the
     * current thread of the pipeline to ensure that events go
     * to the correct indexer!
     */
    public void setIndexer(final Indexer indexer) {
        localIndexer.set(indexer);
        if(lastTrigger != null) {
            lastTrigger.setNext(localIndexer.get());
        }
    }

    /**
     * Gets the current Content Handler
     *
     * if {@see lastTrigger} != null there must by definition be a {@see firstTrigger}
     * so we use that, otherwise we fall back to the {@see localIndexer}
     * which may also be null
     *
     * @return The handler which is either the {@see firstTrigger}, the {@see localIndexer}, or null
     */
    protected ContentHandler getContentHandler() {
        configureSaxPipelineOnce();

        if(lastTrigger != null) {
            return firstTrigger;
        } else {
            return localIndexer.get();
        }
    }

    /**
     * Gets the current Lexical Handler
     *
     * if {@see lastTrigger} != null there must by definition be a {@see firstTrigger}
     * so we use that, otherwise we fall back to the {@see localIndexer}
     * which may also be null
     *
     * @return The handler which is either the {@see firstTrigger}, the {@see localIndexer}, or null
     */
    protected LexicalHandler getLexicalHandler() {
        configureSaxPipelineOnce();

        if(lastTrigger != null) {
            return firstTrigger;
        } else {
            return localIndexer.get();
        }
    }

    /**
     * Gets the current Error Handler
     *
     * if {@see lastTrigger} != null there must by definition be a {@see firstTrigger}
     * so we use that, otherwise we fall back to the {@see localIndexer}
     * which may also be null
     *
     * @return The handler which is either the {@see firstTrigger}, the {@see localIndexer}, or null
     */
    protected ErrorHandler getErrorHandler() {
        configureSaxPipelineOnce();

        if(lastTrigger != null) {
            return firstTrigger;
        } else {
            return localIndexer.get();
        }
    }

    //<editor-fold desc="DocumentTrigger method implementations">
    @Override
    public boolean isValidating() {
        return localValidating.get();
    }

    @Override
    public void setValidating(final boolean validating) {
        this.localValidating.set(validating);
        for(final LazyDocumentTrigger trigger : getTriggers()) {
            try {
                trigger.get().setValidating(validating);
            } catch(final TriggerException te) {
                LOG.error("Unable to set validating=" + validating + " on trigger: " + trigger.getClazz().getName(), te);
            }
        }

        final Indexer indexer = localIndexer.get();
        if(localIndexer != null) {
            indexer.setValidating(validating);
        }
    }

    @Override
    public void beforeCreateDocument(final DBBroker broker, final Txn txn, final XmldbURI uri) throws TriggerException {
        for(final LazyDocumentTrigger trigger : getTriggers()) {
            trigger.get().beforeCreateDocument(broker, txn, uri);
        }
    }

    @Override
    public void afterCreateDocument(final DBBroker broker, final Txn txn, final DocumentImpl document) {
        for(final LazyDocumentTrigger trigger : getTriggers()) {
            try {
                trigger.get().afterCreateDocument(broker, txn, document);
            } catch (Exception e) {
                Trigger.LOG.error(e.getMessage(), e);
            }
        }
    }

    @Override
    public void beforeUpdateDocument(final DBBroker broker, final Txn txn, final DocumentImpl document) throws TriggerException {
        for(final LazyDocumentTrigger trigger : getTriggers()) {
            trigger.get().beforeUpdateDocument(broker, txn, document);
        }
    }

    @Override
    public void afterUpdateDocument(final DBBroker broker, final Txn txn, final DocumentImpl document) {
        for(final LazyDocumentTrigger trigger : getTriggers()) {
            try {
                trigger.get().afterUpdateDocument(broker, txn, document);
            } catch (Exception e) {
                Trigger.LOG.error(e.getMessage(), e);
            }
        }
    }

    @Override
    public void beforeUpdateDocumentMetadata(final DBBroker broker, final Txn txn, final DocumentImpl document) throws TriggerException {
        for(final LazyDocumentTrigger trigger : getTriggers()) {
            trigger.get().beforeUpdateDocumentMetadata(broker, txn, document);
        }
    }

    @Override
    public void afterUpdateDocumentMetadata(final DBBroker broker, final Txn txn, final DocumentImpl document) {
        for(final LazyDocumentTrigger trigger : getTriggers()) {
            try {
                trigger.get().afterUpdateDocumentMetadata(broker, txn, document);
            } catch (Exception e) {
                Trigger.LOG.error(e.getMessage(), e);
            }
        }
    }

    @Override
    public void beforeCopyDocument(final DBBroker broker, final Txn txn, final DocumentImpl document, final XmldbURI newUri) throws TriggerException {
        for(final LazyDocumentTrigger trigger : getTriggers()) {
            trigger.get().beforeCopyDocument(broker, txn, document, newUri);
        }
    }

    @Override
    public void afterCopyDocument(final DBBroker broker, final Txn txn, final DocumentImpl document, final XmldbURI oldUri) {
        for(final LazyDocumentTrigger trigger : getTriggers()) {
            try {
                trigger.get().afterCopyDocument(broker, txn, document, oldUri);
            } catch (Exception e) {
                Trigger.LOG.error(e.getMessage(), e);
            }
        }
    }

    @Override
    public void beforeMoveDocument(final DBBroker broker, final Txn txn, final DocumentImpl document, final XmldbURI newUri) throws TriggerException {
        for(final LazyDocumentTrigger trigger : getTriggers()) {
            trigger.get().beforeMoveDocument(broker, txn, document, newUri);
        }
    }

    @Override
    public void afterMoveDocument(final DBBroker broker, final Txn txn, final DocumentImpl document, final XmldbURI oldUri) {
        for(final LazyDocumentTrigger trigger : getTriggers()) {
            try {
                trigger.get().afterMoveDocument(broker, txn, document, oldUri);
            } catch (Exception e) {
                Trigger.LOG.error(e.getMessage(), e);
            }
        }
    }

    @Override
    public void beforeDeleteDocument(final DBBroker broker, final Txn txn, final DocumentImpl document) throws TriggerException {
        for(final LazyDocumentTrigger trigger : getTriggers()) {
            trigger.get().beforeDeleteDocument(broker, txn, document);
        }
    }

    @Override
    public void afterDeleteDocument(final DBBroker broker, final Txn txn, final XmldbURI uri) {
        for(final LazyDocumentTrigger trigger : getTriggers()) {
            try {
                trigger.get().afterDeleteDocument(broker, txn, uri);
            } catch (Exception e) {
                Trigger.LOG.error(e.getMessage(), e);
            }
        }
    }
    //</editor-fold>


    //<editor-fold desc="ContentHandler method implementations">
    @Override
    public void setDocumentLocator(final Locator locator) {
        final ContentHandler contentHandler = getContentHandler();
        if(contentHandler != null) {
            contentHandler.setDocumentLocator(locator);
        }
    }

    @Override
    public void startDocument() throws SAXException {
        final ContentHandler contentHandler = getContentHandler();
        if(contentHandler != null) {
            contentHandler.startDocument();
        }
    }

    @Override
    public void endDocument() throws SAXException {
        final ContentHandler contentHandler = getContentHandler();
        if(contentHandler != null) {
            contentHandler.endDocument();
        }
    }

    @Override
    public void startPrefixMapping(final String prefix, final String uri) throws SAXException {
        final ContentHandler contentHandler = getContentHandler();
        if(contentHandler != null) {
            contentHandler.startPrefixMapping(prefix, uri);
        }
    }

    @Override
    public void endPrefixMapping(final String prefix) throws SAXException {
        final ContentHandler contentHandler = getContentHandler();
        if(contentHandler != null) {
            contentHandler.endPrefixMapping(prefix);
        }
    }

    @Override
    public void startElement(final String uri, final String localName, final String qName, final Attributes atts) throws SAXException {
        final ContentHandler contentHandler = getContentHandler();
        if(contentHandler != null) {
            contentHandler.startElement(uri, localName, qName, atts);
        }
    }

    @Override
    public void endElement(final String uri, final String localName, final String qName) throws SAXException {
        final ContentHandler contentHandler = getContentHandler();
        if(contentHandler != null) {
            contentHandler.endElement(uri, localName, qName);
        }
    }

    @Override
    public void characters(final char[] ch, final int start, final int length) throws SAXException {
        final ContentHandler contentHandler = getContentHandler();
        if(contentHandler != null) {
            contentHandler.characters(ch, start, length);
        }
    }

    @Override
    public void ignorableWhitespace(final char[] ch, final int start, final int length) throws SAXException {
        final ContentHandler contentHandler = getContentHandler();
        if(contentHandler != null) {
            contentHandler.ignorableWhitespace(ch, start, length);
        }
    }

    @Override
    public void processingInstruction(final String target, final String data) throws SAXException {
        final ContentHandler contentHandler = getContentHandler();
        if(contentHandler != null) {
            contentHandler.processingInstruction(target, data);
        }
    }

    @Override
    public void skippedEntity(final String name) throws SAXException {
        final ContentHandler contentHandler = getContentHandler();
        if(contentHandler != null) {
            contentHandler.skippedEntity(name);
        }
    }
    //</editor-fold>

    //<editor-fold desc="LexicalHandler method implementations">
    @Override
    public void startDTD(final String name, final String publicId, final String systemId) throws SAXException {
        final LexicalHandler lexicalHandler = getLexicalHandler();
        if(lexicalHandler != null) {
            lexicalHandler.startDTD(name, publicId, systemId);
        }
    }

    @Override
    public void endDTD() throws SAXException {
        final LexicalHandler lexicalHandler = getLexicalHandler();
        if(lexicalHandler != null) {
            lexicalHandler.endDTD();
        }
    }

    @Override
    public void startEntity(final String name) throws SAXException {
        final LexicalHandler lexicalHandler = getLexicalHandler();
        if(lexicalHandler != null) {
            lexicalHandler.startEntity(name);
        }
    }

    @Override
    public void endEntity(final String name) throws SAXException {
        final LexicalHandler lexicalHandler = getLexicalHandler();
        if(lexicalHandler != null) {
            lexicalHandler.endEntity(name);
        }
    }

    @Override
    public void startCDATA() throws SAXException {
        final LexicalHandler lexicalHandler = getLexicalHandler();
        if(lexicalHandler != null) {
            lexicalHandler.startCDATA();
        }
    }

    @Override
    public void endCDATA() throws SAXException {
        final LexicalHandler lexicalHandler = getLexicalHandler();
        if(lexicalHandler != null) {
            lexicalHandler.endCDATA();
        }
    }

    @Override
    public void comment(final char[] ch, final int start, final int length) throws SAXException {
        final LexicalHandler lexicalHandler = getLexicalHandler();
        if(lexicalHandler != null) {
            lexicalHandler.comment(ch, start, length);
        }
    }
    //</editor-fold>

    //<editor-fold desc="ErrorHandler method implementations">
    @Override
    public void warning(final SAXParseException exception) throws SAXException {
        final ErrorHandler errorHandler = getErrorHandler();
        if(errorHandler != null) {
            errorHandler.warning(exception);
        }
    }

    @Override
    public void error(final SAXParseException exception) throws SAXException {
        final ErrorHandler errorHandler = getErrorHandler();
        if(errorHandler != null) {
            errorHandler.error(exception);
        }
    }

    @Override
    public void fatalError(final SAXParseException exception) throws SAXException {
        final ErrorHandler errorHandler = getErrorHandler();
        if(errorHandler != null) {
            errorHandler.fatalError(exception);
        }
    }
    //</editor-fold>

}
