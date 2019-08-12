package org.exist.dom.memory;

import net.sf.saxon.dom.NodeOverNodeInfo;
import net.sf.saxon.tree.tiny.TinyNodeImpl;
import org.exist.dom.QName;
import org.exist.storage.ElementValue;
import org.exist.xquery.NodeTest;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.w3c.dom.DOMException;
import org.w3c.dom.Node;
import org.w3c.dom.ProcessingInstruction;

public class ProcessingInstructionImpl
        extends NodeImpl<ProcessingInstructionImpl, ProcessingInstruction> implements ProcessingInstruction {

    ProcessingInstructionImpl(final TinyTreeWithId tinyTreeWithId, final int nodeNr) {
        super(tinyTreeWithId, nodeNr);
        if (node.getNodeType() != NodeImpl.PROCESSING_INSTRUCTION_NODE) {
            throw new IllegalArgumentException("processing instruction argument must be of Processing Instruction type");
        }
    }

    ProcessingInstructionImpl(final TinyTreeWithId tinyTreeWithId, final TinyNodeImpl tinyNode) {
        super(tinyTreeWithId, tinyNode);
        if (node.getNodeType() != NodeImpl.PROCESSING_INSTRUCTION_NODE) {
            throw new IllegalArgumentException("processing instruction argument must be of Processing Instruction type");
        }
    }

    ProcessingInstructionImpl(final TinyTreeWithId tinyTreeWithId, final NodeOverNodeInfo nodeOverNodeInfo) {
        super(tinyTreeWithId, nodeOverNodeInfo);
        if (node.getNodeType() != NodeImpl.PROCESSING_INSTRUCTION_NODE) {
            throw new IllegalArgumentException("processing instruction argument must be of Processing Instruction type");
        }
    }

    @Override
    public String getTarget() {
        return node.getTarget();
    }

    @Override
    public String getStringValue() {
        return getData().replaceFirst("^\\s+", "");
    }

    @Override
    public String getData() {
        return node.getData();
    }

    @Override
    public String getNodeValue() throws DOMException {
        return node.getNodeValue();
    }

    @Override
    public AtomicValue atomize() throws XPathException {
        return new StringValue(getData());
    }

    @Override
    public void setData(final String data) throws DOMException {
        node.setData(data);
    }

//    @Override
//    public String getBaseURI() {
//        String baseURI = "";
//        int parent = -1;
//        int test = document.getParentNodeFor(nodeNumber);
//
//        if(document.nodeKind[test] != Node.DOCUMENT_NODE) {
//            parent = test;
//        }
//
//        // fixme! Testa med 0/ljo
//        while((parent != -1) && (document.getNode(parent).getBaseURI() != null)) {
//
//            if(baseURI.isEmpty()) {
//                baseURI = document.getNode(parent).getBaseURI();
//            } else {
//                baseURI = document.getNode(parent).getBaseURI() + "/" + baseURI;
//            }
//
//            test = document.getParentNodeFor(parent);
//
//            if(document.nodeKind[test] == Node.DOCUMENT_NODE) {
//                return (baseURI);
//            } else {
//                parent = test;
//            }
//        }
//
//        if(baseURI.isEmpty()) {
//            baseURI = getOwnerDocument().getBaseURI();
//        }
//        return (baseURI);
//    }

    @Override
    public Node getFirstChild() {
        //No child
        return null;
    }

    @Override
    public int getItemType() {
        return Type.PROCESSING_INSTRUCTION;
    }

    @Override
    public int getType() {
        return Type.PROCESSING_INSTRUCTION;
    }

    @Override
    public String toString() {
        return "in-memory#processing-instruction {" + getTarget() + "} {" + getData() + "} ";
    }

    @Override
    public void selectAttributes(final NodeTest test, final Sequence result)
            throws XPathException {
        //do nothing, which will return an empty sequence
    }

    @Override
    public void selectChildren(final NodeTest test, final Sequence result)
            throws XPathException {
        //do nothing, which will return an empty sequence
    }

    @Override
    public void selectDescendantAttributes(final NodeTest test, final Sequence result)
            throws XPathException {
        //do nothing, which will return an empty sequence
    }

    // <editor-fold desc="INode implementation">
    @Override
    public QName getQName() {
        final TinyNodeImpl tinyNode = getTinyNode(node);
        return new QName(tinyNode.getLocalPart(), tinyNode.getURI(), tinyNode.getPrefix(), ElementValue.ELEMENT);
    }
    // </editor-fold>
}
