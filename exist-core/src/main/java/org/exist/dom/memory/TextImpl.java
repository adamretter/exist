package org.exist.dom.memory;

import net.sf.saxon.dom.NodeOverNodeInfo;
import net.sf.saxon.tree.tiny.TinyNodeImpl;
import org.exist.dom.QName;
import org.exist.xquery.value.Type;
import org.w3c.dom.DOMException;
import org.w3c.dom.Text;

public class TextImpl extends AbstractCharacterData<TextImpl, Text> implements Text {

    TextImpl(final TinyTreeWithId tinyTreeWithId, final int nodeNr) {
        super(tinyTreeWithId, nodeNr);
        if (node.getNodeType() != NodeImpl.TEXT_NODE) {
            throw new IllegalArgumentException("text argument must be of Text type");
        }
    }

    TextImpl(final TinyTreeWithId tinyTreeWithId, final TinyNodeImpl tinyNode) {
        super(tinyTreeWithId, tinyNode);
        if (node.getNodeType() != NodeImpl.TEXT_NODE) {
            throw new IllegalArgumentException("text argument must be of Text type");
        }
    }

    TextImpl(final TinyTreeWithId tinyTreeWithId, final NodeOverNodeInfo nodeOverNodeInfo) {
        super(tinyTreeWithId, nodeOverNodeInfo);
        if (node.getNodeType() != NodeImpl.TEXT_NODE) {
            throw new IllegalArgumentException("text argument must be of Text type");
        }
    }

    // <editor-fold desc="org.w3c.dom.Text implementation>
    @Override
    public Text splitText(final int offset) throws DOMException {
        final Text text = node.splitText(offset);
        if (text == null) {
            return null;
        }
        return new TextImpl(tinyTreeWithId, getTinyNode(text).getNodeNumber());
    }

    @Override
    public boolean isElementContentWhitespace() {
        return node.isElementContentWhitespace();
    }

    @Override
    public String getWholeText() {
        return node.getWholeText();
    }

    @Override
    public Text replaceWholeText(final String content) throws DOMException {
        final Text text = node.replaceWholeText(content);
        if (text == null) {
            return null;
        }
        return new TextImpl(tinyTreeWithId, getTinyNode(text).getNodeNumber());
    }
    // </editor-fold>


    // <editor-fold desc="org.exist.xquery.value.Item">
    @Override
    public int getType() {
        return Type.TEXT;
    }
    // </editor-fold>

    @Override
    public String toString() {
        return "in-memory#text {" + getData() + "} ";
    }

    // <editor-fold desc="INode implementation">
    @Override
    public QName getQName() {
        return QName.EMPTY_QNAME;
    }
    // </editor-fold>
}
