package org.exist.dom.memory;

import net.sf.saxon.dom.NodeOverNodeInfo;
import net.sf.saxon.tree.tiny.TinyNodeImpl;
import org.exist.dom.QName;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.w3c.dom.Comment;
import org.w3c.dom.Node;

public class CommentImpl extends AbstractCharacterData<CommentImpl, Comment> implements Comment {

    CommentImpl(final TinyTreeWithId tinyTreeWithId, final int nodeNr) {
        super(tinyTreeWithId, nodeNr);
        if (node.getNodeType() != NodeImpl.COMMENT_NODE) {
            throw new IllegalArgumentException("comment argument must be of Comment type");
        }
    }

    CommentImpl(final TinyTreeWithId tinyTreeWithId, final TinyNodeImpl tinyNode) {
        super(tinyTreeWithId, tinyNode);
        if (node.getNodeType() != NodeImpl.COMMENT_NODE) {
            throw new IllegalArgumentException("comment argument must be of Comment type");
        }
    }

    CommentImpl(final TinyTreeWithId tinyTreeWithId, final NodeOverNodeInfo nodeOverNodeInfo) {
        super(tinyTreeWithId, nodeOverNodeInfo);
        if (node.getNodeType() != NodeImpl.COMMENT_NODE) {
            throw new IllegalArgumentException("comment argument must be of Comment type");
        }
    }

    public AtomicValue atomize() throws XPathException {
        return new StringValue(getData());
    }

    @Override
    public String getBaseURI() {
        final Node parent = getParentNode();
        if(parent == null) {
            return null;
        }
        return parent.getBaseURI();
    }

    // <editor-fold desc="org.exist.xquery.value.Item">
    @Override
    public int getType() {
        return Type.COMMENT;
    }
    // </editor-fold>

    public String toString() {
        return "in-memory#comment {" + getData() + "} ";
    }

    // <editor-fold desc="INode implementation">
    @Override
    public QName getQName() {
        return QName.EMPTY_QNAME;
    }
    // </editor-fold>
}
