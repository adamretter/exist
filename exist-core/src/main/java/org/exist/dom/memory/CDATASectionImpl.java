package org.exist.dom.memory;

import net.sf.saxon.dom.NodeOverNodeInfo;
import net.sf.saxon.tree.tiny.TinyNodeImpl;
import org.exist.dom.QName;
import org.exist.xquery.value.Type;
import org.w3c.dom.CDATASection;
import org.w3c.dom.DOMException;
import org.w3c.dom.Text;

public class CDATASectionImpl extends AbstractCharacterData<CDATASectionImpl, CDATASection> implements CDATASection {

    CDATASectionImpl(final TinyTreeWithId tinyTreeWithId, final int nodeNr) {
        super(tinyTreeWithId, nodeNr);
        if (node.getNodeType() != NodeImpl.CDATA_SECTION_NODE) {
            throw new IllegalArgumentException("cdata section argument must be of CDATA Section type");
        }
    }

    CDATASectionImpl(final TinyTreeWithId tinyTreeWithId, final TinyNodeImpl tinyNode) {
        super(tinyTreeWithId, tinyNode);
        if (node.getNodeType() != NodeImpl.CDATA_SECTION_NODE) {
            throw new IllegalArgumentException("cdata section argument must be of CDATA Section type");
        }
    }

    CDATASectionImpl(final TinyTreeWithId tinyTreeWithId, final NodeOverNodeInfo nodeOverNodeInfo) {
        super(tinyTreeWithId, nodeOverNodeInfo);
        if (node.getNodeType() != NodeImpl.CDATA_SECTION_NODE) {
            throw new IllegalArgumentException("cdata section argument must be of CDATA Section type");
        }
    }

    // <editor-fold desc="org.w3c.dom.CDATASection implementation>
    @Override
    public Text splitText(int offset) throws DOMException {
        return null;
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
    public Text replaceWholeText(String content) throws DOMException {
        return null;
    }
    // </editor-fold>

    @Override
    public int getType() {
        return Type.CDATA_SECTION;
    }

    // <editor-fold desc="INode implementation">
    @Override
    public QName getQName() {
        return QName.EMPTY_QNAME;
    }
    // </editor-fold>
}
