/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2014 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
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

import com.evolvedbinary.j8fu.lazy.LazyVal;
import net.sf.saxon.Configuration;
import net.sf.saxon.dom.DOMNodeWrapper;
import net.sf.saxon.dom.DocumentWrapper;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.expr.parser.ExplicitLocation;
import net.sf.saxon.expr.parser.Location;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.NodeTest;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.tiny.TinyBuilder;
import net.sf.saxon.tree.tiny.TinyTree;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.AnySimpleType;
import net.sf.saxon.type.Untyped;
import org.exist.dom.QName;
import org.exist.dom.memory.TinyTreeWithId;
import org.exist.dom.persistent.NodeProxy;
import org.exist.xquery.Constants;
import org.exist.xquery.XQueryContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.Attributes;

import javax.xml.XMLConstants;


/**
 * Use this class to build a new in-memory DOM document.
 *
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang</a>
 */
public class MemTreeBuilder {

    private final XQueryContext context;
    private final Configuration saxonConfiguration;
    private TinyBuilder tinyBuilder;
//    private short level = 1;
//    private int[] prevNodeInLevel;
    private String defaultNamespaceURI = XMLConstants.NULL_NS_URI;

    public MemTreeBuilder() {
        this(null);
    }


    public MemTreeBuilder(final XQueryContext context) {
        this.context = context;
//        prevNodeInLevel = new int[15];
//        Arrays.fill(prevNodeInLevel, -1);
//        prevNodeInLevel[0] = 0;
        this.saxonConfiguration = Configuration.newConfiguration();
        this.tinyBuilder = new TinyBuilder(new PipelineConfiguration(saxonConfiguration));
        this.tinyBuilder.setBaseURI("http://memtree");
        this.tinyBuilder.setUseEventLocation(false);
        this.tinyBuilder.open();
    }

    /**
     * Returns the created document object.
     *
     * @return DOCUMENT ME!
     */
    public org.exist.dom.memory.DocumentImpl getDocument() {
        final TinyTreeWithId tinyTreeWithId = new TinyTreeWithId(tinyBuilder.getTree());
        return new org.exist.dom.memory.DocumentImpl(tinyTreeWithId, 0);
    }


    public XQueryContext getContext() {
        return context;
    }


    public int getSize() {
        return tinyBuilder.getTree().getNumberOfNodes();
    }


    /**
     * Start building the document.
     */
    public void startDocument() {
        try {
            tinyBuilder.startDocument(0);
        } catch (final net.sf.saxon.trans.XPathException e) {
            throw new IllegalStateException(e);
        }
    }


    /**
     * Start building the document.
     *
     * @param explicitCreation DOCUMENT ME!
     */
    public void startDocument(final boolean explicitCreation) {
        try {
            tinyBuilder.startDocument(0);
        } catch (final net.sf.saxon.trans.XPathException e) {
            throw new IllegalStateException(e);
        }
    }


    /**
     * End building the document.
     */
    public void endDocument() {
        try {
            tinyBuilder.endDocument();
        } catch (final net.sf.saxon.trans.XPathException e) {
            throw new IllegalStateException(e);
        }
    }


    /**
     * Create a new element.
     *
     * @param namespaceURI DOCUMENT ME!
     * @param localName    DOCUMENT ME!
     * @param qname        DOCUMENT ME!
     * @param attributes   DOCUMENT ME!
     * @return the node number of the created element
     */
    public int startElement(final String namespaceURI, String localName, final String qname, final Attributes attributes) {
        final int prefixIdx = qname.indexOf(':');

        String prefix = null;
        if (context != null && !getDefaultNamespace().equals(namespaceURI == null ? XMLConstants.NULL_NS_URI : namespaceURI)) {
            prefix = context.getPrefixForURI(namespaceURI);
        }

        if (prefix == null) {
            prefix = (prefixIdx != Constants.STRING_NOT_FOUND) ? qname.substring(0, prefixIdx) : null;
        }

        if (localName.isEmpty()) {
            if (prefixIdx > -1) {
                localName = qname.substring(prefixIdx + 1);
            } else {
                localName = qname;
            }
        }

        final QName qn = new QName(localName, namespaceURI, prefix);
        return startElement(qn, attributes);
    }


    /**
     * Create a new element.
     *
     * @param qname      DOCUMENT ME!
     * @param attributes DOCUMENT ME!
     * @return the node number of the created element
     */
    public int startElement(final QName qname, final Attributes attributes) {
        final NamePool pool = tinyBuilder.getConfiguration().getNamePool();
        try {
            final FingerprintedQName name = new FingerprintedQName(new StructuredQName(qname.getPrefix() != null ? qname.getPrefix() : "" , qname.getNamespaceURI(), qname.getLocalPart()), pool);
            tinyBuilder.startElement(name, Untyped.INSTANCE, ExplicitLocation.UNKNOWN_LOCATION, 0);
        } catch (final net.sf.saxon.trans.XPathException e) {
            throw new IllegalStateException(e);
        }

        if(attributes != null) {

            // parse attributes
            for(int i = 0; i < attributes.getLength(); i++) {
                final String attrQName = attributes.getQName(i);

                // skip xmlns-attributes and attributes in eXist's namespace
                if (!(attrQName.startsWith(XMLConstants.XMLNS_ATTRIBUTE))) {
//                  || attrNS.equals(Namespaces.EXIST_NS))) {
                    final int p = attrQName.indexOf(':');
                    final String attrNS = attributes.getURI(i);
                    final String attrPrefix = (p != Constants.STRING_NOT_FOUND) ? attrQName.substring(0, p) : null;
                    final String attrLocalName = attributes.getLocalName(i);

                    final QName attrQn = new QName(attrLocalName, attrNS, attrPrefix);
//                    final int type = getAttribType(attrQn, attributes.getType(i));
                    //doc.addAttribute(nodeNr, attrQn, attributes.getValue(i), type);

                    final FingerprintedQName name = new FingerprintedQName(new StructuredQName(attrQn.getPrefix() != null ? attrQn.getPrefix() : "", attrQn.getNamespaceURI(), attrQn.getLocalPart()), pool);
                    try {
                        tinyBuilder.attribute(name, AnySimpleType.INSTANCE, attributes.getValue(i), ExplicitLocation.UNKNOWN_LOCATION, 0);
                    } catch (final net.sf.saxon.trans.XPathException e) {
                        throw new IllegalStateException(e);
                    }
                }
            }
        }

        return tinyBuilder.getTree().getNumberOfNodes() - 1;
    }


//    private int getAttribType(final QName qname, final String type) {
//        if(qname.equals(Namespaces.XML_ID_QNAME) || type.equals(Indexer.ATTR_ID_TYPE)) {
//            // an xml:id attribute.
//            return AttrImpl.ATTR_ID_TYPE;
//        } else if(type.equals(Indexer.ATTR_IDREF_TYPE)) {
//            return AttrImpl.ATTR_IDREF_TYPE;
//        } else if(type.equals(Indexer.ATTR_IDREFS_TYPE)) {
//            return AttrImpl.ATTR_IDREFS_TYPE;
//        } else {
//            return AttrImpl.ATTR_CDATA_TYPE;
//        }
//    }


    /**
     * Close the last element created.
     */
    public void endElement() {
        try {
            tinyBuilder.endElement();
        } catch (final net.sf.saxon.trans.XPathException e) {
            throw new IllegalStateException(e);
        }
////      System.out.println("end-element: level = " + level);
//        prevNodeInLevel[level] = -1;
//        --level;
    }


    public int addReferenceNode(final NodeProxy proxy) {
        //TODO(AR) implement this
//        final int lastNode = doc.getLastNode();
//
//        if((lastNode > 0) && (level == doc.getTreeLevel(lastNode))) {
//
//            if((doc.getNodeType(lastNode) == Node.TEXT_NODE) && (proxy.getNodeType() == Node.TEXT_NODE)) {
//
//                // if the last node is a text node, we have to append the
//                // characters to this node. XML does not allow adjacent text nodes.
//                doc.appendChars(lastNode, proxy.getNodeValue());
//                return lastNode;
//            }
//
//            if(doc.getNodeType(lastNode) == NodeImpl.REFERENCE_NODE) {
//
//                // check if the previous node is a reference node. if yes, check if it is a text node
//                final int p = doc.alpha[lastNode];
//
//                if((doc.references[p].getNodeType() == Node.TEXT_NODE) && (proxy.getNodeType() == Node.TEXT_NODE)) {
//
//                    // found a text node reference. create a new char sequence containing
//                    // the concatenated text of both nodes
//                    final String s = doc.references[p].getStringValue() + proxy.getStringValue();
//                    doc.replaceReferenceNode(lastNode, s);
//                    return lastNode;
//                }
//            }
//        }

        // TODO(AR) call getNode here is very expensive! it would be better to graft a NodeProxy into the tree
        final Node node = proxy.getNode();
        final Document doc = node.getOwnerDocument();

        // TODO(AR) wrapping the entire document is expensive!
        final DocumentWrapper wrapper = new DocumentWrapper(doc, doc.getBaseURI(), saxonConfiguration);
        final DOMNodeWrapper nodeWrapper = wrapper.wrap(node);

        tinyBuilder.getTree().graft(nodeWrapper, tinyBuilder.getTree().getNumberOfNodes(), tinyBuilder.getCurrentDepth(), false);

        return tinyBuilder.getTree().getNumberOfNodes() - 1;

//        if (proxy.getNodeType() == Node.ELEMENT_NODE) {
//            tinyBuilder.getTree().graft(externalNode, tinyBuilder.getTree().getNumberOfNodes(), tinyBuilder.getCurrentDepth(), false);
//        }
//
//        tinyBuilder.getTree().
//
//        final int nodeNr = doc.addNode(NodeImpl.REFERENCE_NODE, level, null);
//        doc.addReferenceNode(nodeNr, proxy);
//        linkNode(nodeNr);
//        return nodeNr;
    }

//    private static class GraftedNodeProxy implements NodeInfo {
//        private final NodeProxy nodeProxy;
//        private final LazyVal<Node> node;
//        private final TinyTree tree;
//        private String systemId = null;
//
//        public GraftedNodeProxy(final TinyTree tree, final NodeProxy nodeProxy) {
//            this.nodeProxy = nodeProxy;
//            this.node = new LazyVal<>(() -> nodeProxy.getNode());
//            this.tree = tree;
//        }
//
//        @Override
//        public TreeInfo getTreeInfo() {
//            return tree;
//        }
//
//        @Override
//        public int getNodeKind() {
//            return nodeProxy.getNodeType();
//        }
//
//        @Override
//        public void setSystemId(final String systemId) {
//            this.systemId = systemId;
//        }
//
//        @Override
//        public String getSystemId() {
//            return systemId;
//        }
//
//        @Override
//        public Location saveLocation() {
//            return null;
//        }
//
//        @Override
//        public String getBaseURI() {
//            return node.get().getBaseURI();
//        }
//
//        @Override
//        public int compareOrder(final NodeInfo other) {
//            //TODO(AR) implement
//            return 0;
//        }
//
//        @Override
//        public String getStringValue() {
//            //TODO(AR) implement
//            return node.get().getNodeValue();
//        }
//
//        @Override
//        public CharSequence getStringValueCS() {
//            return getStringValue();
//        }
//
//        @Override
//        public boolean hasFingerprint() {
//            return false;
//        }
//
//        @Override
//        public int getFingerprint() {
//            throw new UnsupportedOperationException();
//        }
//
//        @Override
//        public String getLocalPart() {
//            return node.get().getLocalName();
//        }
//
//        @Override
//        public String getURI() {
//            return node.get().getNamespaceURI();
//        }
//
//        @Override
//        public String getDisplayName() {
//            return node.get().getNodeName();
//        }
//
//        @Override
//        public String getPrefix() {
//            return node.get().getPrefix();
//        }
//
//        @Override
//        public AtomicSequence atomize() throws XPathException {
//
//        }
//
//        @Override
//        public NodeInfo getParent() {
//            return null;
//        }
//
//        @Override
//        public AxisIterator iterateAxis(final byte axisNumber, final NodeTest nodeTest) {
//            return null;
//        }
//
//        @Override
//        public String getAttributeValue(String uri, String local) {
//            getNode
//        }
//
//        @Override
//        public NodeInfo getRoot() {
//            return null;
//        }
//
//        @Override
//        public boolean hasChildNodes() {
//            return false;
//        }
//
//        @Override
//        public void generateId(FastStringBuffer buffer) {
//
//        }
//
//        @Override
//        public NamespaceBinding[] getDeclaredNamespaces(NamespaceBinding[] buffer) {
//            return new NamespaceBinding[0];
//        }
//    }


    public int addAttribute(final QName qname, final String value) {
//        final int lastNode = doc.getLastNode();
//
//        //if(0 < lastNode && doc.nodeKind[lastNode] != Node.ELEMENT_NODE) {
//        //Definitely wrong !
//        //lastNode = characters(value);
//        //} else {
//        //lastNode = doc.addAttribute(lastNode, qname, value);
//        //}
//        final int nodeNr = doc.addAttribute(lastNode, qname, value, getAttribType(qname, Indexer.ATTR_CDATA_TYPE));
//
//        //TODO :
//        //1) call linkNode(nodeNr); ?
//        //2) is there a relationship between lastNode and nodeNr ?
//        return nodeNr;

        final NamePool pool = tinyBuilder.getTree().getNamePool();
        final FingerprintedQName name = new FingerprintedQName(new StructuredQName(qname.getPrefix() != null ? qname.getPrefix() : "", qname.getNamespaceURI(), qname.getLocalPart()), pool);
        try {
            tinyBuilder.attribute(name, AnySimpleType.INSTANCE, value, ExplicitLocation.UNKNOWN_LOCATION, 0);
        } catch (final net.sf.saxon.trans.XPathException e) {
            throw new IllegalStateException(e);
        }

        return tinyBuilder.getTree().getNumberOfNodes() - 1;
    }


    /**
     * Create a new text node.
     *
     * @param ch    DOCUMENT ME!
     * @param start DOCUMENT ME!
     * @param len   DOCUMENT ME!
     * @return the node number of the created node
     */
    public int characters(final char[] ch, final int start, final int len) {
        try {
            tinyBuilder.characters(new String(ch, start, len), ExplicitLocation.UNKNOWN_LOCATION, 0);
        } catch (final net.sf.saxon.trans.XPathException e) {
            throw new IllegalStateException(e);
        }

        return tinyBuilder.getTree().getNumberOfNodes() - 1;
    }


    /**
     * Create a new text node.
     *
     * @param s DOCUMENT ME!
     * @return the node number of the created node, -1 if no node was created
     */
    public int characters(final CharSequence s) {
        if(s == null) {
            return -1;
        }

        try {
            tinyBuilder.characters(s, ExplicitLocation.UNKNOWN_LOCATION, 0);
        } catch (final net.sf.saxon.trans.XPathException e) {
            throw new IllegalStateException(e);
        }

        return tinyBuilder.getTree().getNumberOfNodes() - 1;
    }


    public int comment(final CharSequence data) {
        try {
            tinyBuilder.comment(data, ExplicitLocation.UNKNOWN_LOCATION, 0);
        } catch (final net.sf.saxon.trans.XPathException e) {
            throw new IllegalStateException(e);
        }

        return tinyBuilder.getTree().getNumberOfNodes() - 1;
    }


    public int comment(final char[] ch, final int start, final int len) {
        try {
            tinyBuilder.comment(new String(ch, start, len), ExplicitLocation.UNKNOWN_LOCATION, 0);
        } catch (final net.sf.saxon.trans.XPathException e) {
            throw new IllegalStateException(e);
        }

        return tinyBuilder.getTree().getNumberOfNodes() - 1;
    }


    public int cdataSection(final CharSequence data) {

        //TODO(AR) figure this out!!!
        return tinyBuilder.getTree().getNumberOfNodes() - 1;

//        final int lastNode = doc.getLastNode();
//
//        if((lastNode > 0) && (level == doc.getTreeLevel(lastNode))) {
//
//            if((doc.getNodeType(lastNode) == Node.TEXT_NODE) || (doc.getNodeType(lastNode) == Node.CDATA_SECTION_NODE)) {
//
//                // if the last node is a text node, we have to append the
//                // characters to this node. XML does not allow adjacent text nodes.
//                doc.appendChars(lastNode, data);
//                return lastNode;
//            }
//
//            if(doc.getNodeType(lastNode) == NodeImpl.REFERENCE_NODE) {
//
//                // check if the previous node is a reference node. if yes, check if it is a text node
//                final int p = doc.alpha[lastNode];
//
//                if((doc.references[p].getNodeType() == Node.TEXT_NODE) || (doc.references[p].getNodeType() == Node.CDATA_SECTION_NODE)) {
//
//                    // found a text node reference. create a new char sequence containing
//                    // the concatenated text of both nodes
//                    doc.replaceReferenceNode(lastNode, doc.references[p].getStringValue() + data);
//                    return lastNode;
//                }
//                // fall through and add the node below
//            }
//        }
//        final int nodeNr = doc.addNode(Node.CDATA_SECTION_NODE, level, null);
//        doc.addChars(nodeNr, data);
//        linkNode(nodeNr);
//        return nodeNr;
    }


    public int processingInstruction(final String target, final String data) {
        try {
            tinyBuilder.processingInstruction(target, data, ExplicitLocation.UNKNOWN_LOCATION, 0);
        } catch (final net.sf.saxon.trans.XPathException e) {
            throw new IllegalStateException(e);
        }

        return tinyBuilder.getTree().getNumberOfNodes() - 1;
    }

    public int namespaceNode(final String prefix, final String uri) {
        try {
            tinyBuilder.namespace(new NamespaceBinding(prefix == null ? "" : prefix, uri), 0);
        } catch (final net.sf.saxon.trans.XPathException e) {
            throw new IllegalStateException(e);
        }

        return tinyBuilder.getTree().getNumberOfNodes() - 1;
    }

    public int namespaceNode(final QName qname) {
        return namespaceNode(qname, false);
    }

    public int namespaceNode(final QName qname, final boolean checkNS) {
        try {
            tinyBuilder.namespace(new NamespaceBinding(qname.getLocalPart(), qname.getNamespaceURI()), 0);
        } catch (final net.sf.saxon.trans.XPathException e) {
            throw new IllegalStateException(e);
        }

        return tinyBuilder.getTree().getNumberOfNodes() - 1;
    }


    public int documentType(final String publicId, final String systemId) {
//      int nodeNr = doc.addNode(Node.DOCUMENT_TYPE_NODE, level, null);
//      doc.addChars(nodeNr, data);
//      linkNode(nodeNr);
//      return nodeNr;
        return -1;
    }


    public void documentType(final String name, final String publicId, final String systemId) {
    }


    public void setReplaceAttributeFlag(final boolean replaceAttribute) {
        //doc.replaceAttribute = replaceAttribute;
    }

    public void setDefaultNamespace(final String defaultNamespaceURI) {
        this.defaultNamespaceURI = defaultNamespaceURI;
    }

    private String getDefaultNamespace() {
        // guard against someone setting null as the defaultNamespaceURI
        return defaultNamespaceURI == null ? XMLConstants.NULL_NS_URI : defaultNamespaceURI;
    }
}
