package org.exist.dom.memory;

import net.sf.saxon.om.NamePool;
import net.sf.saxon.om.NamespaceBinding;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.tree.tiny.TinyNodeImpl;
import net.sf.saxon.tree.tiny.TinyTree;
import net.sf.saxon.type.Type;
import org.exist.dom.QName;
import org.exist.dom.memtree.DocumentBuilderReceiver;
import org.exist.dom.memtree.ReferenceNode;
import org.exist.numbering.DLN;
import org.exist.numbering.NodeId;
import org.exist.storage.serializers.Serializer;
import org.exist.util.serializer.AttrList;
import org.exist.util.serializer.Receiver;
import org.w3c.dom.DOMException;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.annotation.Nullable;
import javax.xml.XMLConstants;

public class TinyTreeWithId {

    public final TinyTree tinyTree;

    private NodeId[] nodeId = null;
    private NodeId[] attrNodeId = null;

    public TinyTreeWithId(final TinyTree tinyTree) {
        this.tinyTree = tinyTree;
    }

    public NodeId getNodeId(final int nodeNr) {
        if (nodeId == null) {
            calculateNodeIds();
        }
        return nodeId[nodeNr];
    }

    public NodeId getAttrNodeId(final int nodeNr) {
        if (attrNodeId == null) {
            calculateNodeIds();
        }
        return attrNodeId[nodeNr];
    }

    private void calculateNodeIds() {
        final int size = tinyTree.getNumberOfNodes();
        if (size == 0) {
            nodeId = new NodeId[0];
            attrNodeId = new NodeId[0];
            return;
        }

        nodeId = new NodeId[tinyTree.getNumberOfNodes()];
        attrNodeId = new NodeId[tinyTree.getNumberOfAttributes()];

        nodeId[0] = NodeId.DOCUMENT_NODE;
        if(size == 1) {
            return;
        }

        NodeId nextId = new DLN();
        TinyNodeImpl next = tinyTree.getNode(1);
        while (next != null) {
            computeNodeIds(nextId, next.getNodeNumber());

            next = getNextSibling(next);
            nextId = nextId.nextSibling();
        }
    }

    private @Nullable TinyNodeImpl getNextSibling(final TinyNodeImpl node) {
        if (node.getNodeKind() == Type.ATTRIBUTE) {
            return null;
        }

        final int nextNr = tinyTree.getNextPointerArray()[node.getNodeNumber()];
        return nextNr < node.getNodeNumber() ? null : tinyTree.getNode(nextNr);
    }

    private int getNextSibling(final int nodeNr) {
        // TODO(AR) is this check needed here?
        if (tinyTree.getNodeKind(nodeNr) == Type.ATTRIBUTE) {
            return -1;
        }

        final int nextNr = tinyTree.getNextPointerArray()[nodeNr];
        return nextNr < nodeNr ? -1 : nextNr;
    }

    private int getFirstChildFor(final int nodeNr) {
        final short level = tinyTree.getNodeDepthArray()[nodeNr];
        final int nextNode = nodeNr + 1;
        if((nextNode < tinyTree.getNumberOfNodes()) && (tinyTree.getNodeDepthArray()[nextNode] > level)) {
            return nextNode;
        }
        return -1;
    }

    private int getParentNode(final int nodeNr) {
        int next = tinyTree.getNextPointerArray()[nodeNr];
        while (next > nodeNr) {
            next = tinyTree.getNextPointerArray()[next];
        }
        if (next < 0) {
            return -1;
        }
        return next;
    }

    private void computeNodeIds(final NodeId id, final int nodeNr) {
        nodeId[nodeNr] = id;
        if (tinyTree.getNodeKind(nodeNr) == Type.ELEMENT) {
            NodeId nextId = id.newChild();
            int attr = tinyTree.getAlphaArray()[nodeNr];

            if (-1 < attr) {
                while ((attr < tinyTree.getNumberOfAttributes()) && (tinyTree.getAttributeParentArray()[attr] == nodeNr)) {
                    attrNodeId[attr] = nextId;
                    nextId = nextId.nextSibling();
                    ++attr;
                }
            }

            int nextNode = getFirstChildFor(nodeNr);
            while (nextNode > nodeNr) {
                computeNodeIds(nextId, nextNode);
                nextNode = tinyTree.getNextPointerArray()[nextNode];
                if(nextNode > nodeNr) {
                    nextId = nextId.nextSibling();
                }
            }
        }
    }

    /**
     * Copy the document fragment starting at the specified node to the given document builder.
     *
     * @param nodeNr node to provide document fragment
     * @param receiver document builder
     * @throws SAXException DOCUMENT ME!
     */
    public void copyTo(final int nodeNr, final DocumentBuilderReceiver receiver) throws SAXException {
        copyTo(nodeNr, receiver, false);
    }

    protected void copyTo(int nodeNr, final DocumentBuilderReceiver receiver, final boolean expandRefs)
            throws SAXException {
        final int top = nodeNr;
        while (nodeNr > -1) {
            copyStartNode(nodeNr, receiver, expandRefs);
            int nextNode;
        //TODO(AR) figure out!
//            if(node instanceof ReferenceNode) {
//                //Nothing more to stream ?
//                nextNode = null;
//            } else {
                nextNode = getFirstChildFor(nodeNr);
//            }
            while (nextNode == -1) {
                copyEndNode(nodeNr, receiver);
                if(top != -1 && top == nodeNr) {
                    break;
                }
                //No nextNode if the top node is a Document node
                nextNode = getNextSibling(nodeNr);
                if (nextNode == -1) {
                    nodeNr = getParentNode(nodeNr);
                    if((nodeNr == -1) || ((top != -1) && (top == nodeNr))) {
                        copyEndNode(nodeNr, receiver);
                        break;
                    }
                }
            }
            nodeNr = nextNode;
        }
    }

    private void copyStartNode(final int nodeNr, final DocumentBuilderReceiver receiver, final boolean expandRefs)
            throws SAXException {

        switch(tinyTree.getNodeKind(nodeNr)) {
            case Type.ELEMENT: {
                final QName nodeName = getQName(nodeNr);
                receiver.startElement(nodeName, null);
                int attr = tinyTree.getAlphaArray()[nodeNr];
                if(-1 < attr) {
                    while((attr < tinyTree.getNumberOfAttributes()) && (tinyTree.getAttributeParentArray()[attr] == nodeNr)) {

                        final QName attrQName = getAttrQName(attr);
                        receiver.attribute(attrQName, tinyTree.getAttributeValueArray()[attr].toString());
                        ++attr;
                    }
                }
                int ns = tinyTree.getBetaArray()[nodeNr];
                if(-1 < ns) {
                    while((ns < tinyTree.getNumberOfNamespaces()) && (tinyTree.getNamespaceParentArray()[ns] == nodeNr)) {
                        final NamespaceBinding namespaceBinding = tinyTree.getNamespaceBindings()[ns];
                        final QName nsQName = new QName(namespaceBinding.getPrefix(), namespaceBinding.getURI());
                        receiver.addNamespaceNode(nsQName);
                        ++ns;
                    }
                }
                break;
            }

            case Type.TEXT:
                receiver.characters(tinyTree.getCharacterBuffer().subSequence(tinyTree.getAlphaArray()[nodeNr], tinyTree.getAlphaArray()[nodeNr] + tinyTree.getBetaArray()[nodeNr]));
                break;

        //TODO(AR) figure out
//            case Type.CDATA_SECTION_NODE:
//                receiver.cdataSection(document.characters, document.alpha[nodeNr], document.alphaLen[nodeNr]);
//                break;

            case Type.ATTRIBUTE:
                final QName attrQName = getAttrQName(nodeNr);
                receiver.attribute(attrQName, tinyTree.getAttributeValueArray()[nodeNr].toString());
                break;

            case Type.COMMENT:
                final CharSequence cs = tinyTree.getCharacterBuffer().subSequence(tinyTree.getAlphaArray()[nodeNr], tinyTree.getAlphaArray()[nodeNr] + tinyTree.getBetaArray()[nodeNr]);
                receiver.comment(cs.toString().toCharArray(), 0, cs.length());
                break;

            case Type.PROCESSING_INSTRUCTION:
                final QName piQName = getQName(nodeNr);
                final String data = tinyTree.getCharacterBuffer().subSequence(tinyTree.getAlphaArray()[nodeNr], tinyTree.getAlphaArray()[nodeNr] + tinyTree.getBetaArray()[nodeNr]).toString();
                receiver.processingInstruction(piQName.getLocalPart(), data);
                break;

            case Type.NAMESPACE:
                final NamespaceBinding namespaceBinding = tinyTree.getNamespaceBindings()[nodeNr];
                final QName nsQName = new QName(namespaceBinding.getPrefix(), namespaceBinding.getURI());
                receiver.addNamespaceNode(nsQName);
                break;

            //TODO(AR) figure out
//            case org.exist.dom.memtree.NodeImpl.REFERENCE_NODE:
//                if(expandRefs) {
//                    try(final DBBroker broker = getDatabase().getBroker()) {
//                        final Serializer serializer = broker.getSerializer();
//                        serializer.reset();
//                        serializer.setProperty(Serializer.GENERATE_DOC_EVENTS, "false");
//                        serializer.setReceiver(receiver);
//                        serializer.toReceiver(document.references[document.alpha[nodeNr]], false, false);
//                    } catch(final EXistException e) {
//                        throw new SAXException(e);
//                    }
//                } else {
//                    receiver.addReferenceNode(document.references[document.alpha[nodeNr]]);
//                }
//                break;
        }
    }

    private void copyEndNode(final int nodeNr, final DocumentBuilderReceiver receiver)
            throws SAXException {
        if (tinyTree.getNodeKind(nodeNr) == Type.ELEMENT) {
            receiver.endElement(getQName(nodeNr));
        }
    }

    private QName getQName(final int nodeNr) {
        final NamePool namePool = tinyTree.getNamePool();
        final StructuredQName structuredQName = namePool.getStructuredQName(tinyTree.getFingerprint(nodeNr));
        return QName.fromJavaQName(structuredQName.toJaxpQName());
    }

    private QName getAttrQName(final int attrNr) {
        final NamePool namePool = tinyTree.getNamePool();
        final int attrNameCode = tinyTree.getAttributeNameCodeArray()[attrNr];
        final StructuredQName structuredQName = namePool.getUnprefixedQName(attrNameCode);
        return new QName(structuredQName.getLocalPart(), structuredQName.getURI());
    }

    //TODO(AR) make package-private!
    public NodeImpl getNode(final int nodeNr) throws DOMException {


        //TODO(AR) what to do with document-node?
//            if(nodeNr == 0) {
//                return this;
//            }


        if (nodeNr >= tinyTree.getNumberOfNodes()) {
            throw new DOMException(DOMException.HIERARCHY_REQUEST_ERR, "node not found");
        }

        final NodeImpl node;
        switch (tinyTree.getNodeKind(nodeNr)) {
            case Type.ELEMENT:
                node = new ElementImpl(this, nodeNr);
                break;

            default:
                throw new UnsupportedOperationException("TODO AR implement");

//                case Type.TEXT:
//                    node = new TextImpl(this, nodeNr);
//                    break;
//
//                case Type.COMMENT:
//                    node = new CommentImpl(this, nodeNr);
//                    break;
//
//                case Type.PROCESSING_INSTRUCTION:
//                    node = new ProcessingInstructionImpl(this, nodeNr);
//                    break;

        //TODO(AR) figure out!
//                case Node.CDATA_SECTION_NODE:
//                    node = new CDATASectionImpl(this, nodeNr);
//                    break;
//                case NodeImpl.REFERENCE_NODE:
//                    node = new ReferenceNode(this, nodeNr);
//                    break;

//                default:
//                    throw new DOMException(DOMException.NOT_FOUND_ERR, "node not found");
        }
        return node;
    }

    /**
     * Stream the specified document fragment to a receiver. This method
     * is called by the serializer to output in-memory nodes.
     *
     * @param serializer the serializer
     * @param nodeNr node to be serialized
     * @param receiver the receiveer
     * @throws SAXException DOCUMENT ME
     */
    public void streamTo(final Serializer serializer, int nodeNr, final Receiver receiver)
            throws SAXException {
        final int top = nodeNr;
        while (nodeNr != -1) {
            startNode(serializer, nodeNr, receiver);
            int nextNode;
//            if(node instanceof ReferenceNode) {
//                //Nothing more to stream ?
//                nextNode = null;
//            } else {
                nextNode = getFirstChildFor(nodeNr);
//            }
            while (nextNode == -1) {
                endNode(nodeNr, receiver);
                if(top != -1 && top == nodeNr) {
                    break;
                }
                nextNode = getNextSibling(nodeNr);
                if(nextNode == -1) {
                    nodeNr = getParentNode(nodeNr);
                    if((nodeNr == -1) || ((top != -1) && (top == nodeNr))) {
                        endNode(nodeNr, receiver);
                        break;
                    }
                }
            }
            nodeNr = nextNode;
        }
    }

    private void startNode(final Serializer serializer, final int nodeNr, final Receiver receiver)
            throws SAXException {
        switch(tinyTree.getNodeKind(nodeNr)) {
            case Type.ELEMENT:
                final QName nodeName = getQName(nodeNr);
                //Output required namespace declarations
                int ns = tinyTree.getBetaArray()[nodeNr];
                if(ns > -1) {
                    while((ns < tinyTree.getNumberOfNamespaces()) && (tinyTree.getNamespaceParentArray()[ns] == nodeNr)) {
                        final NamespaceBinding namespaceBinding = tinyTree.getNamespaceBindings()[ns];
                        final QName nsQName = new QName(namespaceBinding.getPrefix(), namespaceBinding.getURI());
                        if(XMLConstants.XMLNS_ATTRIBUTE.equals(nsQName.getLocalPart())) {
                            receiver.startPrefixMapping(XMLConstants.DEFAULT_NS_PREFIX, nsQName.getNamespaceURI());
                        } else {
                            receiver.startPrefixMapping(nsQName.getLocalPart(), nsQName.getNamespaceURI());
                        }
                        ++ns;
                    }
                }
                //Create the attribute list
                AttrList attribs = null;
                int attr = tinyTree.getAlphaArray()[nodeNr];
                if(attr > -1) {
                    attribs = new AttrList();
                    while((attr < tinyTree.getNumberOfAttributes()) && (tinyTree.getAttributeParentArray()[attr] == nodeNr)) {
                        final QName attrQName = getAttrQName(attr);
                        attribs.addAttribute(attrQName, tinyTree.getAttributeValueArray()[attr].toString());
                        ++attr;
                    }
                }
                receiver.startElement(nodeName, attribs);
                break;

            case Type.TEXT:
                receiver.characters(tinyTree.getCharacterBuffer().subSequence(tinyTree.getAlphaArray()[nodeNr], tinyTree.getAlphaArray()[nodeNr] + tinyTree.getBetaArray()[nodeNr]));
                break;

            case Type.ATTRIBUTE:
                final QName attrQName = getAttrQName(nodeNr);
                receiver.attribute(attrQName, tinyTree.getAttributeValueArray()[nodeNr].toString());
                break;

            case Type.COMMENT:
                final CharSequence cs = tinyTree.getCharacterBuffer().subSequence(tinyTree.getAlphaArray()[nodeNr], tinyTree.getAlphaArray()[nodeNr] + tinyTree.getBetaArray()[nodeNr]);
                receiver.comment(cs.toString().toCharArray(), 0, cs.length());
                break;

            case Type.PROCESSING_INSTRUCTION:
                final QName piQName = getQName(nodeNr);
                final String data = tinyTree.getCharacterBuffer().subSequence(tinyTree.getAlphaArray()[nodeNr], tinyTree.getAlphaArray()[nodeNr] + tinyTree.getBetaArray()[nodeNr]).toString();
                receiver.processingInstruction(piQName.getLocalPart(), data);
                break;

                //TODO(AR) figure this out!
//            case Node.CDATA_SECTION_NODE:
//                receiver.cdataSection(document.characters, document.alpha[nodeNr], document.alphaLen[nodeNr]);
//                break;
//
//            case org.exist.dom.memtree.NodeImpl.REFERENCE_NODE:
//                serializer.toReceiver(document.references[document.alpha[nodeNr]], true, false);
//                break;
        }
    }

    private void endNode(final int nodeNr, final Receiver receiver) throws SAXException {
        if(tinyTree.getNodeKind(nodeNr) == Type.ELEMENT) {
            receiver.endElement(getQName(nodeNr));

            //End all prefix mappings used for the element
            int ns = tinyTree.getBetaArray()[nodeNr];
            if(ns > -1) {
                while ((ns < tinyTree.getNumberOfNamespaces()) && (tinyTree.getNamespaceParentArray()[ns] == nodeNr)) {
                    final NamespaceBinding namespaceBinding = tinyTree.getNamespaceBindings()[ns];
                    final QName nsQName = new QName(namespaceBinding.getPrefix(), namespaceBinding.getURI());
                    if(XMLConstants.XMLNS_ATTRIBUTE.equals(nsQName.getLocalPart())) {
                        receiver.endPrefixMapping(XMLConstants.DEFAULT_NS_PREFIX);
                    } else {
                        receiver.endPrefixMapping(nsQName.getLocalPart());
                    }
                    ++ns;
                }
            }
        }
    }
}
