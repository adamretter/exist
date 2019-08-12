package org.exist.dom.memory;


import net.sf.saxon.dom.DOMNodeList;
import net.sf.saxon.dom.NodeOverNodeInfo;
import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.tiny.TinyAttributeImpl;
import net.sf.saxon.tree.tiny.TinyNodeImpl;
import net.sf.saxon.type.BuiltInAtomicType;
import org.exist.dom.QName;
import org.exist.numbering.NodeId;
import org.exist.xquery.NodeTest;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;
import org.w3c.dom.*;

import javax.xml.XMLConstants;

public class DocumentImpl extends NodeImpl<DocumentImpl, Document> implements Document {
    private XQueryContext context;

    /**
     * @throws IllegalArgumentException if the provided argument is not a {@link NodeImpl#DOCUMENT_NODE}
     */
    public DocumentImpl(final TinyTreeWithId tinyTreeWithId, final int nodeNr) {
        super(tinyTreeWithId, nodeNr);
        if (node.getNodeType() != NodeImpl.DOCUMENT_NODE) {
            throw new IllegalArgumentException("document argument must be of Document type");
        }
    }

    DocumentImpl(final TinyTreeWithId tinyTreeWithId, final NodeOverNodeInfo nodeOverNodeInfo) {
        super(tinyTreeWithId, nodeOverNodeInfo);
        if (node.getNodeType() != NodeImpl.DOCUMENT_NODE) {
            throw new IllegalArgumentException("document argument must be of Document type");
        }
    }


    // <editor-fold desc="org.w3c.dom.Document implementation">
    @Override
    public DocumentType getDoctype() {
        //TODO(AR) need to wrap?
        return node.getDoctype();
    }

    @Override
    public DOMImplementation getImplementation() {
        //TODO(AR) need to wrap?
        return node.getImplementation();
    }

    @Override
    public Element getDocumentElement() {
        return new ElementImpl(tinyTreeWithId, getTinyNode(node.getDocumentElement()));
    }

    @Override
    public Element createElement(final String tagName) throws DOMException {
        return new ElementImpl(tinyTreeWithId, getTinyNode(node.createElement(tagName)));
    }

    @Override
    public DocumentFragment createDocumentFragment() {
        return new DocumentFragmentImpl(tinyTreeWithId, getTinyNode(node.createDocumentFragment()));
    }

    @Override
    public Text createTextNode(final String data) {
        return new TextImpl(tinyTreeWithId, getTinyNode(node.createTextNode(data)));
    }

    @Override
    public Comment createComment(final String data) {
        return new CommentImpl(tinyTreeWithId, getTinyNode(node.createComment(data)));
    }

    @Override
    public CDATASection createCDATASection(final String data) throws DOMException {
        return new CDATASectionImpl(tinyTreeWithId, getTinyNode(node.createCDATASection(data)));
    }

    @Override
    public ProcessingInstruction createProcessingInstruction(final String target, final String data) throws DOMException {
        return new ProcessingInstructionImpl(tinyTreeWithId, getTinyNode(node.createProcessingInstruction(target, data)));
    }

    @Override
    public Attr createAttribute(final String name) throws DOMException {
        return new AttrImpl(tinyTreeWithId, getTinyNode(node.createAttribute(name)), null);
    }

    @Override
    public EntityReference createEntityReference(final String name) throws DOMException {
        //TODO(AR) need to wrap?
        return node.createEntityReference(name);
    }

    @Override
    public NodeList getElementsByTagName(final String tagname) {
        return new DOMNodeListWrapper(tinyTreeWithId, (DOMNodeList)node.getElementsByTagName(tagname));
    }

    @Override
    public Node importNode(final Node importedNode, final boolean deep) throws DOMException {
        //TODO(AR) need to wrap?
        return node.importNode(importedNode, deep);
    }

    @Override
    public Element createElementNS(final String namespaceURI, final String qualifiedName) throws DOMException {
        return new ElementImpl(tinyTreeWithId, getTinyNode(node.createElementNS(namespaceURI, qualifiedName)));
    }

    @Override
    public Attr createAttributeNS(final String namespaceURI, final String qualifiedName) throws DOMException {
        return new AttrImpl(tinyTreeWithId, getTinyNode(node.createAttributeNS(namespaceURI, qualifiedName)), null);
    }

    @Override
    public NodeList getElementsByTagNameNS(final String namespaceURI, final String localName) {
        return new DOMNodeListWrapper(tinyTreeWithId, (DOMNodeList)node.getElementsByTagNameNS(namespaceURI, localName));
    }

    @Override
    public Element getElementById(final String elementId) {
        return new ElementImpl(tinyTreeWithId, getTinyNode(node.getElementById(elementId)));
    }

    @Override
    public String getInputEncoding() {
        return node.getInputEncoding();
    }

    @Override
    public String getXmlEncoding() {
        return node.getXmlEncoding();
    }

    @Override
    public boolean getXmlStandalone() {
        return node.getXmlStandalone();
    }

    @Override
    public void setXmlStandalone(final boolean xmlStandalone) throws DOMException {
        node.setXmlStandalone(xmlStandalone);
    }

    @Override
    public String getXmlVersion() {
        return node.getXmlVersion();
    }

    @Override
    public void setXmlVersion(final String xmlVersion) throws DOMException {
        node.setXmlVersion(xmlVersion);
    }

    @Override
    public boolean getStrictErrorChecking() {
        return node.getStrictErrorChecking();
    }

    @Override
    public void setStrictErrorChecking(final boolean strictErrorChecking) {
        node.setStrictErrorChecking(strictErrorChecking);
    }

    @Override
    public String getDocumentURI() {
        return node.getDocumentURI();
    }

    @Override
    public void setDocumentURI(final String documentURI) {
        node.setDocumentURI(documentURI);
    }

    @Override
    public Node adoptNode(final Node source) throws DOMException {
        //TODO(AR) need to wrap?
        return node.adoptNode(source);
    }

    @Override
    public DOMConfiguration getDomConfig() {
        //TODO(AR) need to wrap?
        return node.getDomConfig();
    }

    @Override
    public void normalizeDocument() {
        node.normalizeDocument();
    }

    @Override
    public Node renameNode(final Node n, final String namespaceURI, final String qualifiedName) throws DOMException {
        //TODO(AR) need to wrap?
        return node.renameNode(n, namespaceURI, qualifiedName);
    }
    // </editor-fold>


    // <editor-fold desc="INode implementation">
    @Override
    public QName getQName() {
        return QName.EMPTY_QNAME;
    }
    // </editor-fold>

    // <editor-fold desc="Item implementation">
    @Override
    public int getType() {
        return Type.DOCUMENT;
    }
    // </editor-fold>


    //TODO(AR) TEMP BELOW this line <--- until we figure out a better interface - we shouldn't always have to build a DocumentImpl for just a node

    public NodeImpl getNode(final int nodeNr) throws DOMException {
        return tinyTreeWithId.getNode(nodeNr);
    }

    public int getLastNode() {
        return tinyTreeWithId.tinyTree.getNumberOfNodes() - 1;
    }

    public NodeImpl getAttribute(final int nodeNum) throws DOMException {
        return new AttrImpl(tinyTreeWithId, nodeNum, null);
    }

    public NodeImpl getLastAttr() {
        final int nextAttr = tinyTreeWithId.tinyTree.getNumberOfAttributes();

        if(nextAttr == 0) {
            return null;
        }
        return new AttrImpl(tinyTreeWithId, nextAttr - 1, null);
    }

    public NodeImpl getNamespaceNode(final int nodeNum) throws DOMException {
        // TODO(AR) we used to have a NamespaceNode class in memtree - not sure that was correct though?
        return new AttrImpl(tinyTreeWithId, nodeNum, null);
    }

    @Override
    public void selectAttributes(final NodeTest test, final Sequence result)
            throws XPathException {
    }

    @Override
    public void selectChildren(final NodeTest test, final Sequence result) throws XPathException {
        try (final AxisIterator iterator = ((NodeOverNodeInfo) node).getUnderlyingNodeInfo().iterateAxis(AxisInfo.CHILD)) {
            NodeInfo item;
            while ((item = iterator.next()) != null) {
                final NodeImpl node = TinyTreeWithId.wrap(tinyTreeWithId, NodeOverNodeInfo.wrap(item));
                if (test.matches(node)) {
                    result.add(node);
                }
            }
        }
    }

    @Override
    public void selectDescendants(final boolean includeSelf, final NodeTest test, final Sequence result)
            throws XPathException {
        try (final AxisIterator iterator = ((NodeOverNodeInfo) node).getUnderlyingNodeInfo().iterateAxis(includeSelf ? AxisInfo.DESCENDANT_OR_SELF : AxisInfo.DESCENDANT)) {
            NodeInfo item;
            while ((item = iterator.next()) != null) {
                final NodeImpl node = TinyTreeWithId.wrap(tinyTreeWithId, NodeOverNodeInfo.wrap(item));
                if (test.matches(node)) {
                    result.add(node);
                }
            }
        }
    }

    @Override
    public void selectDescendantAttributes(final NodeTest test, final Sequence result) throws XPathException {
        try (final AxisIterator iterator = ((NodeOverNodeInfo) node).getUnderlyingNodeInfo().iterateAxis(AxisInfo.DESCENDANT, NodeKindTest.ATTRIBUTE)) {
            NodeInfo item;
            while ((item = iterator.next()) != null) {
                final AttrImpl attrib = new AttrImpl(tinyTreeWithId, (TinyNodeImpl) item, null);
                if (test.matches(attrib)) {
                    result.add(attrib);
                }
            }
        }
    }

    public NodeImpl selectById(final String id) {
        try (final AxisIterator iterator = ((NodeOverNodeInfo) node).getUnderlyingNodeInfo().iterateAxis(AxisInfo.DESCENDANT, NodeKindTest.ELEMENT)) {
            NodeInfo item;
            while ((item = iterator.next()) != null) {

                NodeInfo attr;
                try (final AxisIterator attrIterator = item.iterateAxis(AxisInfo.ATTRIBUTE)) {
                    while ((attr = attrIterator.next()) != null) {
                        final int attrNr = ((TinyNodeImpl)attr).getNodeNumber();
                        if (tinyTreeWithId.tinyTree.getAttributeTypeArray()[attrNr].equals(BuiltInAtomicType.ID)) {
                            return TinyTreeWithId.wrap(tinyTreeWithId, NodeOverNodeInfo.wrap(item));
                        }
                    }
                }
            }
        }
        return null;
    }

    public NodeImpl selectByIdRef(final String idRef) {
        try (final AxisIterator iterator = ((NodeOverNodeInfo) node).getUnderlyingNodeInfo().iterateAxis(AxisInfo.DESCENDANT, NodeKindTest.ELEMENT)) {
            NodeInfo item;
            while ((item = iterator.next()) != null) {
                NodeInfo attr;
                try (final AxisIterator attrIterator = item.iterateAxis(AxisInfo.ATTRIBUTE)) {
                    while ((attr = attrIterator.next()) != null) {
                        final int attrNr = ((TinyNodeImpl)attr).getNodeNumber();
                        if (tinyTreeWithId.tinyTree.getAttributeTypeArray()[attrNr].equals(BuiltInAtomicType.IDREF)) {
                            return TinyTreeWithId.wrap(tinyTreeWithId, NodeOverNodeInfo.wrap(item));
                        }
                    }
                }
            }
        }
        return null;
    }

    public boolean hasReferenceNodes() {
        //TODO(AR) implement
//        return references != null && references[0] != null;
        return false;
    }

    public DocumentImpl expandRefs(final NodeImpl rootNode) throws DOMException {
        throw new UnsupportedOperationException("AR - Need to implement");
    }

    public void expand() throws DOMException {
        //TODO(AR) implement
    }

    public NodeImpl getNodeById(final NodeId id) {
        expand();
        return tinyTreeWithId.getNodeById(id);
    }

    public org.exist.dom.persistent.DocumentImpl makePersistent() throws XPathException {
        if(tinyTreeWithId.tinyTree.getNumberOfNodes() <= 1) {
            return null;
        }
        //TODO(AR) implement
        throw new UnsupportedOperationException("AR - Need to implement");
//        return context.storeTemporaryDoc(this);
    }

//    @Override
//    public Node getFirstChild() {
//        node.getFirstChild()
//
//        final int childNr = tinyTreeWithId.getFirstChildFor(nodeNr);
//        if (childNr > -1) {
//            return tinyTreeWithId.getNode(childNr);
//        }
//        return null;
//    }

    public void setContext(final XQueryContext context) {
        this.context = context;
    }

    public XQueryContext getContext() {
        return context;
    }

    public TinyTreeWithId getTree() {
        return tinyTreeWithId;
    }
}
