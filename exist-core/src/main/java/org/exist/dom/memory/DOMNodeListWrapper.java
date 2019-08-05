package org.exist.dom.memory;

import net.sf.saxon.dom.DOMNodeList;
import net.sf.saxon.dom.NodeOverNodeInfo;
import net.sf.saxon.tree.tiny.TinyNodeImpl;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Objects;

public class DOMNodeListWrapper implements NodeList {

    private final DOMNodeList nodeList;
    private final TinyTreeWithId tinyTreeWithId;

    public DOMNodeListWrapper(final TinyTreeWithId tinyTreeWithId, final DOMNodeList nodeList) {
        this.tinyTreeWithId = tinyTreeWithId;
        this.nodeList = Objects.requireNonNull(nodeList);
    }

    @Override
    public Node item(final int index) {
        final Node node = nodeList.item(index);
        switch (node.getNodeType()) {
            case Node.ELEMENT_NODE:
                return new ElementImpl(tinyTreeWithId, (TinyNodeImpl)((NodeOverNodeInfo)node).getUnderlyingNodeInfo());

            default:
                throw new UnsupportedOperationException("TODO AR implement this");
        }
    }

    @Override
    public int getLength() {
        return nodeList.getLength();
    }
}
