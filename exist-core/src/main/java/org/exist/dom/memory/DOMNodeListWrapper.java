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
        return TinyTreeWithId.wrap(tinyTreeWithId, node);
    }

    @Override
    public int getLength() {
        return nodeList.getLength();
    }
}
