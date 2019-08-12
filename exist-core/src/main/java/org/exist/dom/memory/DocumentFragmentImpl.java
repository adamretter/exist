package org.exist.dom.memory;

import net.sf.saxon.tree.tiny.TinyNodeImpl;
import org.exist.xquery.NodeTest;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;
import org.w3c.dom.DocumentFragment;

public class DocumentFragmentImpl extends NodeImpl<DocumentFragmentImpl, DocumentFragment> implements DocumentFragment {

    DocumentFragmentImpl(final TinyTreeWithId tinyTreeWithId, int nodeNr) {
        super(tinyTreeWithId, nodeNr);
        if (node.getNodeType() != NodeImpl.DOCUMENT_FRAGMENT_NODE) {
            throw new IllegalArgumentException("document fragment argument must be of Document Fragment type");
        }
    }

    DocumentFragmentImpl(final TinyTreeWithId tinyTreeWithId, final TinyNodeImpl tinyNode) {
        super(tinyTreeWithId, tinyNode);
        if (node.getNodeType() != NodeImpl.DOCUMENT_FRAGMENT_NODE) {
            throw new IllegalArgumentException("document fragment argument must be of Document Fragment type");
        }
    }

    @Override
    public int getType() {
        //TODO(AR) this doesn't seem right?!?
        return Type.DOCUMENT;
    }

    @Override
    public short getNodeType() {
        return DOCUMENT_FRAGMENT_NODE;
    }

    @Override
    public void selectAttributes(final NodeTest test, final Sequence result) throws XPathException {
        //TODO(AR) implement?
    }

    @Override
    public void selectDescendantAttributes(final NodeTest test, final Sequence result) throws XPathException {
        //TODO(AR) implement?
    }

    @Override
    public void selectChildren(final NodeTest test, final Sequence result) throws XPathException {
        //TODO(AR) implement?
    }
}
