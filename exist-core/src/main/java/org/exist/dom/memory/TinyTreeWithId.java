package org.exist.dom.memory;

import net.sf.saxon.dom.NodeOverNodeInfo;
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
import java.util.Map;

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

    public NodeImpl getNodeById(final NodeId id) {
        if (nodeId == null) {
            calculateNodeIds();
        }

        for(int i = 0; i < tinyTree.getNumberOfNodes(); i++) {
            if(id.equals(nodeId[i])) {
                return getNode(i);
            }
        }
        return null;
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

    public int getNextSibling(final int nodeNr) {
        // TODO(AR) is this check needed here?
        if (tinyTree.getNodeKind(nodeNr) == Type.ATTRIBUTE) {
            return -1;
        }

        final int nextNr = tinyTree.getNextPointerArray()[nodeNr];
        return nextNr < nodeNr ? -1 : nextNr;
    }

    public int getFirstChildFor(final int nodeNr) {
        final short level = tinyTree.getNodeDepthArray()[nodeNr];
        final int nextNode = nodeNr + 1;
        if((nextNode < tinyTree.getNumberOfNodes()) && (tinyTree.getNodeDepthArray()[nextNode] > level)) {
            return nextNode;
        }
        return -1;
    }

    public int getNextFor(final int nodeNr) {
        return tinyTree.getNextPointerArray()[nodeNr];
    }

    public int getAttributesCountFor(final int nodeNumber) {
        int count = 0;
        int attr = tinyTree.getAlphaArray()[nodeNumber];
        if(-1 < attr) {
            while((attr < tinyTree.getNumberOfAttributes()) && (tinyTree.getAttributeParentArray()[attr++] == nodeNumber)) {
                ++count;
            }
        }
        return count;
    }

    public int getParentNode(final int nodeNr) {
        int next = tinyTree.getNextPointerArray()[nodeNr];
        while (next > nodeNr) {
            next = tinyTree.getNextPointerArray()[next];
        }
        if (next < 0) {
            return -1;
        }
        return next;
    }

    public int getChildCountFor(final int nodeNr) {
        int count = 0;
        int nextNode = getFirstChildFor(nodeNr);
        while (nextNode > nodeNr) {
            ++count;
            nextNode = tinyTree.getNextPointerArray()[nextNode];
        }
        return count;
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

        //TODO(AR) replace with tinyNode.copy(new NodeImpl.ReceiverAdapter(tinyNode, receiver), 0, tinyNode);

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

        return wrap(this, nodeNr);
    }

    static NodeImpl wrap(final TinyTreeWithId tinyTreeWithId, final int nodeNr) {
        final NodeImpl wrapped;
        switch (tinyTreeWithId.tinyTree.getNodeKind(nodeNr)) {
            case Type.DOCUMENT:
                wrapped = new DocumentImpl(tinyTreeWithId, nodeNr);
                break;

            case Type.ELEMENT:
                wrapped = new ElementImpl(tinyTreeWithId, nodeNr);
                break;

            case Type.ATTRIBUTE:
                wrapped = new AttrImpl(tinyTreeWithId, nodeNr, null);
                break;

            case Type.COMMENT:
                wrapped = new CommentImpl(tinyTreeWithId, nodeNr);
                break;

            case Node.CDATA_SECTION_NODE:
                wrapped = new CDATASectionImpl(tinyTreeWithId, nodeNr);
                break;

            case Type.PROCESSING_INSTRUCTION:
                wrapped = new ProcessingInstructionImpl(tinyTreeWithId, nodeNr);
                break;

            case Type.TEXT:
                wrapped = new TextImpl(tinyTreeWithId, nodeNr);
                break;

            default:
                throw new UnsupportedOperationException("TODO AR implement");

                //TODO(AR) figure out!
//                case NodeImpl.REFERENCE_NODE:
//                    node = new ReferenceNode(this, nodeNr);
//                    break;

//                default:
//                    throw new DOMException(DOMException.NOT_FOUND_ERR, "node not found");
        }
        return wrapped;
    }

    static NodeImpl wrap(final TinyTreeWithId tinyTreeWithId, final Node node) {
        if (node == null) {
            return null;
        } else if (node instanceof NodeImpl) {
            return (NodeImpl)node;
        } else if (node instanceof NodeOverNodeInfo) {
            final NodeImpl wrapped;
            switch (node.getNodeType()) {
                case Node.DOCUMENT_NODE:
                    wrapped = new DocumentImpl(tinyTreeWithId, (NodeOverNodeInfo)node);
                    break;

                case Node.ELEMENT_NODE:
                    wrapped = new ElementImpl(tinyTreeWithId, (NodeOverNodeInfo)node);
                    break;

                case Node.ATTRIBUTE_NODE:
                    wrapped = new AttrImpl(tinyTreeWithId, (NodeOverNodeInfo)node, null);
                    break;

                case Node.COMMENT_NODE:
                    wrapped = new CommentImpl(tinyTreeWithId, (NodeOverNodeInfo)node);
                    break;

                case Node.CDATA_SECTION_NODE:
                    wrapped = new CDATASectionImpl(tinyTreeWithId, (NodeOverNodeInfo)node);
                    break;

                case Node.PROCESSING_INSTRUCTION_NODE:
                    wrapped = new ProcessingInstructionImpl(tinyTreeWithId, (NodeOverNodeInfo)node);
                    break;

                case Node.TEXT_NODE:
                    wrapped = new TextImpl(tinyTreeWithId, (NodeOverNodeInfo)node);
                    break;

                default:
                    throw new UnsupportedOperationException("TODO AR implement");
            }
            return wrapped;

        } else {
            throw new UnsupportedOperationException("TODO AR implement");
        }
    }

    public int getNamespacesCountFor(final int nodeNr) {
        int count = 0;
        int ns = tinyTree.getBetaArray()[nodeNr];
        if(-1 < ns) {
            while((ns < tinyTree.getNumberOfNamespaces()) && (tinyTree.getNamespaceParentArray()[ns++] == nodeNr)) {
                ++count;
            }
        }
        return count;
    }

    public Map<String, String> getNamespaceMap(final Map<String, String> map, final int nodeNr) {
        int ns = tinyTree.getBetaArray()[nodeNr];
        if(-1 < ns) {
            while(ns < tinyTree.getNumberOfNamespaces() && tinyTree.getNamespaceParentArray()[ns] == nodeNr) {
                final NamespaceBinding binding = tinyTree.getNamespaceBindings()[ns];
                map.put(binding.getPrefix(), binding.getURI());
                ++ns;
            }
        }

        int attr = tinyTree.getAlphaArray()[nodeNr];
        if(-1 < attr) {
            while(attr < tinyTree.getNumberOfAttributes() && tinyTree.getAttributeParentArray()[attr] == nodeNr) {
                final int attrNameCode = tinyTree.getAttributeNameCodeArray()[attr];
                final StructuredQName attrName = tinyTree.getNamePool().getUnprefixedQName(attrNameCode);
                final QName qname = QName.fromJavaQName(attrName.toJaxpQName());
                if(qname.getPrefix() != null && !qname.getPrefix().isEmpty()) {
                    map.put(qname.getPrefix(), qname.getNamespaceURI());
                }
                ++attr;
            }
        }

        return map;
    }
}
