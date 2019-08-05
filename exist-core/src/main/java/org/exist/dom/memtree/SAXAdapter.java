/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2014 The eXist Team
 *
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

import net.sf.saxon.Configuration;
import net.sf.saxon.dom.NodeOverNodeInfo;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.om.FingerprintedQName;
import net.sf.saxon.om.NamePool;
import net.sf.saxon.om.NamespaceBinding;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.tiny.TinyBuilder;
import net.sf.saxon.tree.tiny.TinyTree;
import net.sf.saxon.type.AnySimpleType;
import net.sf.saxon.type.Untyped;
import org.exist.dom.QName;
import org.exist.dom.memory.DocumentImpl;
import org.exist.dom.memory.TinyTreeWithId;
import org.exist.xquery.Constants;
import org.exist.xquery.XQueryContext;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.ext.LexicalHandler;

import javax.xml.XMLConstants;
import java.util.HashMap;
import java.util.Map;


/**
 * Adapter class to build an internal, in-memory DOM from a SAX stream.
 *
 * @author wolf
 */
public class SAXAdapter implements ContentHandler, LexicalHandler {
    private MemTreeBuilder builder;
    private TinyBuilder tinyBuilder;
    private Map<String, String> namespaces = null;
    private boolean replaceAttributeFlag = false;
    private boolean cdataFlag = false;
    private final StringBuilder cdataBuf = new StringBuilder();

    public SAXAdapter() {
        setBuilder(new MemTreeBuilder());
    }

    public SAXAdapter(final XQueryContext context) {
        setBuilder(new MemTreeBuilder(context));
    }

    protected final void setBuilder(final MemTreeBuilder builder) {
        this.builder = builder;
        this.tinyBuilder = new TinyBuilder(new PipelineConfiguration(Configuration.newConfiguration()));
        this.tinyBuilder.setBaseURI("http://memtree");
        this.tinyBuilder.setUseEventLocation(false);
        this.tinyBuilder.open();
    }

//    public DocumentImpl getDocument() {
//        return builder.getDocument();
//    }

//    public Document getDocument() {
//        final Node node = NodeOverNodeInfo.wrap(tinyBuilder.getTree().getRootNode());
//        return node.getOwnerDocument();
//    }

    public DocumentImpl getDocument() {
        final TinyTreeWithId tinyTreeWithId = new TinyTreeWithId(tinyBuilder.getTree());
        return new DocumentImpl(tinyTreeWithId, 0);
    }

//    public TinyTree getSaxonTree() {
//        final Node node = NodeOverNodeInfo.wrap(tinyBuilder.getTree().getRootNode());
//        return node.getOwnerDocument();
//    }

    @Override
    public void endDocument() throws SAXException {
        builder.endDocument();
        try {
            tinyBuilder.endDocument();
        } catch (final net.sf.saxon.trans.XPathException e) {
            throw new SAXException(e);
        }
    }

    @Override
    public void startDocument() throws SAXException {
        builder.startDocument();
        try {
            tinyBuilder.startDocument(0);
        } catch (final net.sf.saxon.trans.XPathException e) {
            throw new SAXException(e);
        }
        if(replaceAttributeFlag) {
            builder.setReplaceAttributeFlag(replaceAttributeFlag);
        }
    }

    @Override
    public void characters(final char[] ch, final int start, final int length) throws SAXException {
        if (cdataFlag) {
            cdataBuf.append(ch, start, length);
        } else {
            builder.characters(ch, start, length);
        }

        try {
            tinyBuilder.characters(new String(ch, start, length), null, 0);
        } catch (final net.sf.saxon.trans.XPathException e) {
            throw new SAXException(e);
        }
    }

    @Override
    public void ignorableWhitespace(final char[] ch, final int start, final int length) throws SAXException {
        builder.characters(ch, start, length);

        try {
            tinyBuilder.characters(new String(ch, start, length), null, 0);
        } catch (final net.sf.saxon.trans.XPathException e) {
            throw new SAXException(e);
        }
    }

    @Override
    public void endPrefixMapping(final String prefix) throws SAXException {
    }

    @Override
    public void skippedEntity(final String name) throws SAXException {
    }

    @Override
    public void setDocumentLocator(final Locator locator) {
    }

    @Override
    public void processingInstruction(final String target, final String data) throws SAXException {
        builder.processingInstruction(target, data);
        try {
            tinyBuilder.processingInstruction(target, data, null, 0);
        } catch (final net.sf.saxon.trans.XPathException e) {
            throw new SAXException(e);
        }
    }

    @Override
    public void startPrefixMapping(final String prefix, final String uri) throws SAXException {
        if(namespaces == null) {
            namespaces = new HashMap<>();
        }
        namespaces.put(prefix, uri);
        try {
            tinyBuilder.namespace(new NamespaceBinding(prefix, uri), 0);
        } catch (final net.sf.saxon.trans.XPathException e) {
            throw new SAXException(e);
        }
    }

    @Override
    public void endElement(final String namespaceURI, final String localName, final String qName) throws SAXException {
        builder.endElement();
        try {
            tinyBuilder.endElement();
        } catch (final net.sf.saxon.trans.XPathException e) {
            throw new SAXException(e);
        }
    }

    @Override
    public void startElement(final String namespaceURI, final String localName, final String qName, final Attributes atts) throws SAXException {
        builder.startElement(namespaceURI, localName, qName, atts);

        if (namespaces != null) {
            for (final Map.Entry<String, String> entry : namespaces.entrySet()) {
                builder.namespaceNode(entry.getKey(), entry.getValue());
            }
        }

        for (int i = 0; i < atts.getLength(); i++) {
            final String attQName = atts.getQName(i);
            if (attQName.startsWith(XMLConstants.XMLNS_ATTRIBUTE)) {
                final int idxPrefixSep = attQName.indexOf(":");
                final String prefix = idxPrefixSep > -1 ? attQName.substring(idxPrefixSep + 1) : null;
                final String uri = atts.getValue(i);
                if (namespaces == null || !namespaces.containsKey(prefix)) {
                    builder.namespaceNode(prefix, uri);
                }
            }
        }
        namespaces = null;

        NamePool pool = tinyBuilder.getConfiguration().getNamePool();
        try {
            final String prefix;
            if (qName.indexOf(':') > -1) {
                prefix = qName.substring(0, qName.indexOf(':'));
            } else {
                prefix = "";
            }
            tinyBuilder.startElement(new FingerprintedQName(new StructuredQName(prefix, namespaceURI, localName), pool), Untyped.INSTANCE, null, 0);
        } catch (final net.sf.saxon.trans.XPathException e) {
            throw new SAXException(e);
        }
        if(atts != null) {

            // parse attributes
            for (int i = 0; i < atts.getLength(); i++) {
                final String attrQName = atts.getQName(i);

                // skip xmlns-attributes and attributes in eXist's namespace
                if (!(attrQName.startsWith(XMLConstants.XMLNS_ATTRIBUTE))) {
//                  || attrNS.equals(Namespaces.EXIST_NS))) {
                    final int p = attrQName.indexOf(':');
                    final String attrNS = atts.getURI(i);
                    final String attrPrefix = (p != Constants.STRING_NOT_FOUND) ? attrQName.substring(0, p) : null;
                    final String attrLocalName = atts.getLocalName(i);

                    final QName attrQn = new QName(attrLocalName, attrNS, attrPrefix);
//                    final int type = getAttribType(attrQn, atts.getType(i));
                    //doc.addAttribute(nodeNr, attrQn, attributes.getValue(i), type);

                    final FingerprintedQName name = new FingerprintedQName(new StructuredQName(attrQn.getPrefix() != null ? attrQn.getPrefix() : "", attrQn.getNamespaceURI(), attrQn.getLocalPart()), pool);
                    try {
                        tinyBuilder.attribute(name, AnySimpleType.INSTANCE, atts.getValue(i), null, 0);
                    } catch (final net.sf.saxon.trans.XPathException e) {
                        throw new IllegalStateException(e);
                    }
                }
            }
        }
    }

    @Override
    public void endDTD() throws SAXException {
    }

    @Override
    public void startCDATA() throws SAXException {
        this.cdataFlag = true;
    }


    @Override
    public void endCDATA() throws SAXException {
        builder.cdataSection(cdataBuf);
        cdataBuf.delete(0, cdataBuf.length());
        this.cdataFlag = false;
    }

    @Override
    public void comment(final char[] ch, final int start, final int length) throws SAXException {
        builder.comment(ch, start, length);
        try {
            tinyBuilder.comment(new String(ch, start, length), null, 0);
        } catch (final net.sf.saxon.trans.XPathException e) {
            throw new SAXException(e);
        }
    }

    @Override
    public void endEntity(final String name) throws SAXException {
    }

    @Override
    public void startEntity(final String name) throws SAXException {
    }

    @Override
    public void startDTD(final String name, final String publicId, final String systemId) throws SAXException {
    }

    public void setReplaceAttributeFlag(final boolean replaceAttributeFlag) {
        this.replaceAttributeFlag = replaceAttributeFlag;
    }
}
