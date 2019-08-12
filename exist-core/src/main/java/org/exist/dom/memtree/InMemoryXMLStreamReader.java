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
 *
 * $Id$
 */
package org.exist.dom.memtree;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.sf.saxon.om.NamespaceBinding;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.SimpleType;
import net.sf.saxon.type.Type;
import org.exist.dom.QName;
import org.exist.dom.memory.DocumentImpl;
import org.exist.dom.memory.NodeImpl;
import org.exist.numbering.NodeId;
import org.exist.stax.ExtendedXMLStreamReader;

import javax.annotation.Nullable;
import javax.xml.namespace.NamespaceContext;
import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.util.*;


/**
 * Implementation of a StAX {@link javax.xml.stream.XMLStreamReader}, which wraps around eXist's in-memory DOM.
 * This class complements {@link org.exist.stax.EmbeddedXMLStreamReader} which reads persistent documents.
 */
public class InMemoryXMLStreamReader implements ExtendedXMLStreamReader {
    private static final String NOT_START_ELEMENT = "Cursor is not at the start of an element";

    private final DocumentImpl doc;
    private final NodeImpl rootNode;
    private final InScopeNamespaces inScopeNamespaces = new InScopeNamespaces();
    private int currentNode;

    private int state = XMLStreamReader.START_DOCUMENT;



    public InMemoryXMLStreamReader(final DocumentImpl doc, final NodeImpl node) {
        this.doc = doc;
        this.rootNode = node;
        this.currentNode = -1;
    }

    @Override
    public Object getProperty(final String name) throws IllegalArgumentException {
        if(name.equals(PROPERTY_NODE_ID)) {
            if(currentNode < 0 || currentNode >= doc.getTree().tinyTree.getNumberOfNodes()) {
                return null;
            }
            doc.expand();

            return doc.getTree().getNodeId(currentNode);
        }
        return null;
    }

    @Override
    public int next() throws XMLStreamException {
        if(currentNode > -1) {
            int next = -1;

            if(state == XMLStreamReader.START_ELEMENT || state == XMLStreamReader.START_DOCUMENT) {
                next = doc.getTree().getFirstChildFor(currentNode);

                if(next < 0) { // no child nodes
                    state = XMLStreamReader.END_ELEMENT;

                    inScopeNamespaces.pop(getQName());

                    return state;
                }
            }

            if(next < 0) {
                next = doc.getTree().getNextFor(currentNode);

                if(next < currentNode) {

                    if(next == 0) {
                        state = XMLStreamReader.END_DOCUMENT;
                    } else {
                        state = XMLStreamReader.END_ELEMENT;
                    }
                    currentNode = next;

                    if (state == XMLStreamReader.END_ELEMENT) {
                        inScopeNamespaces.pop(getQName());
                    }

                    return state;
                }
            }
            currentNode = next;
        } else {
            currentNode = rootNode.getNodeNumber();
        }

        if (state == XMLStreamReader.START_ELEMENT) {
            inScopeNamespaces.push(getQName());
        }  else if (state == XMLStreamReader.END_ELEMENT) {
            inScopeNamespaces.pop(getQName());
        }

        switch(doc.getTree().tinyTree.getNodeKind(currentNode)) {

            case Type.TEXT: {
                state = XMLStreamReader.CHARACTERS;
                break;
            }

//            case Type.CDATA_SECTION_NODE: {
//                state = XMLStreamReader.CDATA;
//                break;
//            }

            case Type.COMMENT: {
                state = XMLStreamReader.COMMENT;
                break;
            }

            case Type.PROCESSING_INSTRUCTION: {
                state = XMLStreamReader.PROCESSING_INSTRUCTION;
                break;
            }

            case Type.ELEMENT: {
                state = XMLStreamReader.START_ELEMENT;
                break;
            }
        }
        return state;
    }

    @Override
    public void require(final int type, final String namespaceURI, final String localName) throws XMLStreamException {
    }

    @Override
    public String getElementText() throws XMLStreamException {
        if(getEventType() != START_ELEMENT) {
            throw new XMLStreamException("parser must be on START_ELEMENT to read next text");
        }
        int eventType = next();
        final StringBuilder content = new StringBuilder();

        while(eventType != END_ELEMENT) {

            if(eventType == CHARACTERS || eventType == CDATA || eventType == SPACE || eventType == ENTITY_REFERENCE) {
                content.append(getText());
            } else if(eventType == PROCESSING_INSTRUCTION || eventType == COMMENT) {
                // skipping
            } else if(eventType == END_DOCUMENT) {
                throw new XMLStreamException("unexpected end of document when reading element text content");
            } else if(eventType == START_ELEMENT) {
                throw new XMLStreamException("element text content may not contain START_ELEMENT");
            } else {
                throw new XMLStreamException("Unexpected event type " + eventType);
            }
            eventType = next();
        }
        return content.toString();
    }

    @Override
    public int nextTag() throws XMLStreamException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean hasNext() throws XMLStreamException {
        return currentNode != rootNode.getNodeNumber() || state == XMLStreamReader.START_DOCUMENT || state == XMLStreamReader.START_ELEMENT;
    }

    @Override
    public void close() throws XMLStreamException {
    }

    @Override
    public String getNamespaceURI(final String prefix) {
        return getNamespaceContext().getNamespaceURI(prefix);
    }

    @Override
    public boolean isStartElement() {
        return state == XMLStreamReader.START_ELEMENT;
    }

    @Override
    public boolean isEndElement() {
        return state == XMLStreamReader.END_ELEMENT;
    }

    @Override
    public boolean isCharacters() {
        return state == XMLStreamReader.CHARACTERS;
    }

    @Override
    public boolean isWhiteSpace() {
        return false;
    }

    @Override
    public String getAttributeValue(final String namespaceURI, final String localName) {
        final int attrCount = doc.getTree().getAttributesCountFor(currentNode);

        if(attrCount == 0) {
            return null;
        }
        final int attrStart = doc.getTree().tinyTree.getAlphaArray()[currentNode];

        for(int i = 0; i < attrCount; i++) {

            final int attrNameCode = doc.getTree().tinyTree.getAttributeNameCodeArray()[attrStart + i];
            final QName qname = QName.fromJavaQName(doc.getTree().tinyTree.getNamePool().getUnprefixedQName(attrNameCode).toJaxpQName());

            if((namespaceURI == null || namespaceURI.equals(qname.getNamespaceURI())) && localName.equals(qname.getLocalPart())) {
                return doc.getTree().tinyTree.getAttributeValueArray()[attrStart + i].toString();
            }
        }
        return null;
    }

    @Override
    public int getAttributeCount() {
        if(state != START_ELEMENT) {
            throw new IllegalStateException(NOT_START_ELEMENT);
        }
        return doc.getTree().getAttributesCountFor(currentNode);
    }

    @Override
    public QName getAttributeQName(final int index) {
        if(state != START_ELEMENT) {
            throw new IllegalStateException(NOT_START_ELEMENT);
        }

        if(index > getAttributeCount()) {
            throw new ArrayIndexOutOfBoundsException();
        }
        final int attr = doc.getTree().tinyTree.getAlphaArray()[currentNode];
        final int attrNameCode = doc.getTree().tinyTree.getAttributeNameCodeArray()[attr];
        final QName qname = QName.fromJavaQName(doc.getTree().tinyTree.getNamePool().getUnprefixedQName(attrNameCode).toJaxpQName());
        return qname;
    }

    @Override
    public javax.xml.namespace.QName getAttributeName(final int index) {
        return getAttributeQName(index).toJavaQName();
    }

    @Override
    public String getAttributeNamespace(final int index) {
        if(state != START_ELEMENT) {
            throw new IllegalStateException(NOT_START_ELEMENT);
        }
        return getAttributeQName(index).getNamespaceURI();
    }

    @Override
    public String getAttributeLocalName(final int index) {
        if(state != START_ELEMENT) {
            throw new IllegalStateException(NOT_START_ELEMENT);
        }
        return getAttributeQName(index).getLocalPart();
    }

    @Override
    public String getAttributePrefix(final int index) {
        if(state != START_ELEMENT) {
            throw new IllegalStateException(NOT_START_ELEMENT);
        }
        return getAttributeQName(index).getPrefix();
    }

    @Override
    public NodeId getAttributeId(final int index) {
        if(state != START_ELEMENT) {
            throw new IllegalStateException(NOT_START_ELEMENT);
        }

        if(index > getAttributeCount()) {
            throw new ArrayIndexOutOfBoundsException();
        }
        doc.expand();
        final int attr = doc.getTree().tinyTree.getAlphaArray()[currentNode];
        return doc.getTree().getAttrNodeId(attr);
    }

    @Override
    public String getAttributeType(final int index) {
        if(state != START_ELEMENT) {
            throw new IllegalStateException(NOT_START_ELEMENT);
        }

        if(index > getAttributeCount()) {
            throw new ArrayIndexOutOfBoundsException();
        }
        final int attr = doc.getTree().tinyTree.getAlphaArray()[currentNode];
        final SimpleType type = doc.getTree().tinyTree.getAttributeTypeArray()[attr + index];

        if (type.equals(BuiltInAtomicType.ID)) {
            return "ID";
        } else if (type.equals(BuiltInAtomicType.IDREF)) {
            return "IDREF";
//        } else if (type.equals(BuiltInAtomicType.IDREFS)) {
//            return "IDREFS";
        } else {
            return "CDATA";
        }
    }

    @Override
    public String getAttributeValue(final int index) {
        if(state != START_ELEMENT) {
            throw new IllegalStateException(NOT_START_ELEMENT);
        }

        if(index > getAttributeCount()) {
            throw new ArrayIndexOutOfBoundsException();
        }
        final int attr = doc.getTree().tinyTree.getAlphaArray()[currentNode];

        return doc.getTree().tinyTree.getAttributeValueArray()[attr + index].toString();
    }

    @Override
    public boolean isAttributeSpecified(final int index) {
        if(state != START_ELEMENT) {
            throw new IllegalStateException(NOT_START_ELEMENT);
        }
        return true;
    }

    @Override
    public int getNamespaceCount() {
        if(state != START_ELEMENT && state != END_ELEMENT && state != NAMESPACE) {
            throw new IllegalStateException("Cursor is not at an element or namespace");
        }
        return doc.getTree().getNamespacesCountFor(currentNode);
    }

    @Override
    public String getNamespacePrefix(final int index) {
        if(index > getNamespaceCount()) {
            throw new ArrayIndexOutOfBoundsException();
        }
        final int ns = doc.getTree().tinyTree.getBetaArray()[currentNode];
        final NamespaceBinding namespaceBinding = doc.getTree().tinyTree.getNamespaceBindings()[ns + index];
        return namespaceBinding.getPrefix();
    }

    @Override
    public String getNamespaceURI(final int index) {
        if(index > getNamespaceCount()) {
            throw new ArrayIndexOutOfBoundsException();
        }
        final int ns = doc.getTree().tinyTree.getBetaArray()[currentNode];
        final NamespaceBinding namespaceBinding = doc.getTree().tinyTree.getNamespaceBindings()[ns + index];
        return namespaceBinding.getURI();
    }

    @Override
    public NamespaceContext getNamespaceContext() {
        return inScopeNamespaces;
    }

    @Override
    public int getEventType() {
        return state;
    }

    @Override
    public String getText() {
        if(state == CHARACTERS || state == COMMENT || state == CDATA) {
            final int start = doc.getTree().tinyTree.getAlphaArray()[currentNode];
            final int len = doc.getTree().tinyTree.getBetaArray()[currentNode];
            return doc.getTree().tinyTree.getCharacterBuffer().subSequence(start, start + len).toString();
        }
        return "";
    }

    @Override
    public char[] getTextCharacters() {
        final int start = doc.getTree().tinyTree.getAlphaArray()[currentNode];
        final int len = doc.getTree().tinyTree.getBetaArray()[currentNode];
        return doc.getTree().tinyTree.getCharacterBuffer().subSequence(start, start + len).toString().toCharArray();
    }

    @Override
    public int getTextCharacters(final int sourceStart, final char[] target, final int targetStart, final int length) throws XMLStreamException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getTextStart() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getTextLength() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getEncoding() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean hasText() {
        return state == CHARACTERS || state == COMMENT || state == CDATA;
    }

    @Override
    public Location getLocation() {
        throw new UnsupportedOperationException();
    }

    @Override
    public QName getQName() {
        if(state == START_ELEMENT || state == END_ELEMENT) {
            final int nameCode = doc.getTree().tinyTree.getNameCode(currentNode);
            final QName qname = QName.fromJavaQName(doc.getTree().tinyTree.getNamePool().getUnprefixedQName(nameCode).toJaxpQName());
            return qname;
        }
        throw new IllegalStateException("Cursor is not at the start of end of an element");
    }

    @Override
    public javax.xml.namespace.QName getName() {
        return getQName().toJavaQName();
    }

    @Override
    public String getLocalName() {
        return getQName().getLocalPart();
    }

    @Override
    public boolean hasName() {
        return state == START_ELEMENT || state == END_ELEMENT;
    }

    @Override
    public String getNamespaceURI() {
        return getQName().getNamespaceURI();
    }

    @Override
    public String getPrefix() {
        return getQName().getPrefix();
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public boolean isStandalone() {
        return false;
    }

    @Override
    public boolean standaloneSet() {
        return false;
    }

    @Override
    public String getCharacterEncodingScheme() {
        return null;
    }

    @Override
    public String getPITarget() {
        final int nameCode = doc.getTree().tinyTree.getNameCode(currentNode);
        return doc.getTree().tinyTree.getNamePool().getLocalName(nameCode);
    }

    @Override
    public String getPIData() {
        final int start = doc.getTree().tinyTree.getAlphaArray()[currentNode];
        final int len = doc.getTree().tinyTree.getBetaArray()[currentNode];
        return doc.getTree().tinyTree.getCharacterBuffer().subSequence(start, start + len).toString();
    }

    /**
     * Allows access to namespaces that are in-scope
     * by using a reference counting mechanism as
     * opposed to a stack.
     */
    private static class InScopeNamespaces implements NamespaceContext {
        private final Object2IntMap<QName> namespaces;

        // uri -> prefix(s)
        private final Map<String, Set<String>> byUri = new HashMap<>();

        // prefix -> url
        private final Map<String, String> byPrefix = new HashMap<>();

        public InScopeNamespaces() {
            this.namespaces = new Object2IntOpenHashMap<>(4);
            this.namespaces.defaultReturnValue(-1);
        }

        /**
         * Push an in-scope namespace.
         *
         * The namespace.
         */
        public void push(@Nullable final QName qname) {
            if (qname == null) {
                return;
            }

            final int count;
            if (namespaces.containsKey(qname)) {
                count = namespaces.getInt(qname) + 1;
            } else {
                count = 1;
            }
            namespaces.put(qname, count);

            byUri.compute(qname.getNamespaceURI(), (k,v) -> {
                if (v == null) {
                    v = new HashSet<>();
                }
                v.add(qname.getPrefix());
                return v;
            });

            byPrefix.putIfAbsent(qname.getPrefix(), qname.getNamespaceURI());
        }

        /**
         * Pop an in-scope namespace.
         */
        public void pop(@Nullable final QName qname) {
            if (qname == null) {
                return;
            }

            if (!namespaces.containsKey(qname)) {
                return;
            }

            final int newCount = namespaces.getInt(qname) - 1;
            if (newCount >= 1) {
                namespaces.put(qname, newCount);
            } else {
                namespaces.removeInt(qname);
                byUri.remove(qname);
                byPrefix.remove(qname);
            }
        }

        @Override
        public String getNamespaceURI(final String prefix) {
            return byPrefix.get(prefix);
        }

        @Override
        public String getPrefix(final String namespaceURI) {
            final Set<String> prefixes = byUri.get(namespaceURI);
            if (prefixes == null) {
                return null;
            }

            return prefixes.iterator().next();
        }

        @Override
        public Iterator getPrefixes(final String namespaceURI) {
            final Set<String> prefixes = byUri.get(namespaceURI);
            if (prefixes == null) {
                return Collections.EMPTY_SET.iterator();
            } else {
                return prefixes.iterator();
            }
        }
    }
}
