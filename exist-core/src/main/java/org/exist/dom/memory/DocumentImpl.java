package org.exist.dom.memory;


import org.exist.dom.QName;
import org.exist.dom.memtree.NamespaceNode;
import org.exist.xquery.NodeTest;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;
import org.w3c.dom.*;

public class DocumentImpl extends NodeImpl<DocumentImpl, Document> implements Document {
    /**
     * @throws IllegalArgumentException if the provided argument is not a {@link NodeImpl#DOCUMENT_NODE}
     */
    public DocumentImpl(final TinyTreeWithId tinyTreeWithId, final int nodeNr) {
        super(tinyTreeWithId, nodeNr);
        if (node.getNodeType() != NodeImpl.DOCUMENT_NODE) {
            throw new IllegalArgumentException("document argument must be of Document type");
        }
    }


    // <editor-fold desc="org.w3c.dom.Document implementation">
    @Override
    public DocumentType getDoctype() {
        return node.getDoctype();
    }

    @Override
    public DOMImplementation getImplementation() {
        return node.getImplementation();
    }

    @Override
    public Element getDocumentElement() {
        return node.getDocumentElement();
    }

    @Override
    public Element createElement(final String tagName) throws DOMException {
        return node.createElement(tagName);
    }

    @Override
    public DocumentFragment createDocumentFragment() {
        return node.createDocumentFragment();
    }

    @Override
    public Text createTextNode(final String data) {
        return node.createTextNode(data);
    }

    @Override
    public Comment createComment(final String data) {
        return node.createComment(data);
    }

    @Override
    public CDATASection createCDATASection(final String data) throws DOMException {
        return node.createCDATASection(data);
    }

    @Override
    public ProcessingInstruction createProcessingInstruction(final String target, final String data) throws DOMException {
        return node.createProcessingInstruction(target, data);
    }

    @Override
    public Attr createAttribute(final String name) throws DOMException {
        return node.createAttribute(name);
    }

    @Override
    public EntityReference createEntityReference(final String name) throws DOMException {
        return node.createEntityReference(name);
    }

    @Override
    public NodeList getElementsByTagName(final String tagname) {
        return node.getElementsByTagName(tagname);
    }

    @Override
    public Node importNode(final Node importedNode, final boolean deep) throws DOMException {
        return node.importNode(importedNode, deep);
    }

    @Override
    public Element createElementNS(final String namespaceURI, final String qualifiedName) throws DOMException {
        return node.createElementNS(namespaceURI, qualifiedName);
    }

    @Override
    public Attr createAttributeNS(final String namespaceURI, final String qualifiedName) throws DOMException {
        return node.createAttributeNS(namespaceURI, qualifiedName);
    }

    @Override
    public NodeList getElementsByTagNameNS(final String namespaceURI, final String localName) {
        return node.getElementsByTagNameNS(namespaceURI, localName);
    }

    @Override
    public Element getElementById(final String elementId) {
        return node.getElementById(elementId);
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
        return node.adoptNode(source);
    }

    @Override
    public DOMConfiguration getDomConfig() {
        return node.getDomConfig();
    }

    @Override
    public void normalizeDocument() {
        node.normalizeDocument();
    }

    @Override
    public Node renameNode(final Node n, final String namespaceURI, final String qualifiedName) throws DOMException {
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

//    @Override
    public void selectChildren(final NodeTest test, final Sequence result) throws XPathException {
        if (tinyTreeWithId.tinyTree.getNumberOfNodes() == 1) {
            return;
        }

        NodeImpl next = (NodeImpl) getFirstChild();
        while(next != null) {
            if(test.matches(next)) {
                result.add(next);
            }
            next = (NodeImpl) next.getNextSibling();
        }
    }
}
