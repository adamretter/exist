package org.exist.dom.memory;

import net.sf.saxon.dom.NodeOverNodeInfo;
import net.sf.saxon.tree.tiny.TinyNodeImpl;
import org.exist.dom.QName;
import org.exist.storage.ElementValue;
import org.exist.xquery.NodeTest;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;
import org.w3c.dom.Attr;
import org.w3c.dom.DOMException;
import org.w3c.dom.Element;
import org.w3c.dom.TypeInfo;

import javax.annotation.Nullable;

public class AttrImpl extends NodeImpl<AttrImpl, Attr> implements Attr {

    @Nullable private final ElementImpl ownerElement;

    AttrImpl(final TinyTreeWithId tinyTreeWithId, final int nodeNr, @Nullable final ElementImpl ownerElement) {
        super(tinyTreeWithId, nodeNr);
        if (node.getNodeType() != NodeImpl.ATTRIBUTE_NODE) {
            throw new IllegalArgumentException("attribute argument must be of Attribute type");
        }
        this.ownerElement = ownerElement;
    }

    AttrImpl(final TinyTreeWithId tinyTreeWithId, final TinyNodeImpl tinyNode, @Nullable final ElementImpl ownerElement) {
        super(tinyTreeWithId, tinyNode);
        if (node.getNodeType() != NodeImpl.ATTRIBUTE_NODE) {
            throw new IllegalArgumentException("attribute argument must be of Attribute type");
        }
        this.ownerElement = ownerElement;
    }

    AttrImpl(final TinyTreeWithId tinyTreeWithId, final NodeOverNodeInfo nodeOverNodeInfo, @Nullable final ElementImpl ownerElement) {
        super(tinyTreeWithId, nodeOverNodeInfo);
        if (node.getNodeType() != NodeImpl.ATTRIBUTE_NODE) {
            throw new IllegalArgumentException("attribute argument must be of Attribute type");
        }
        this.ownerElement = ownerElement;
    }

    // <editor-fold desc="org.w3c.dom.Attr implementation">
    @Override
    public String getName() {
        return node.getName();
    }

    @Override
    public boolean getSpecified() {
        return node.getSpecified();
    }

    @Override
    public String getValue() {
        return node.getValue();
    }

    @Override
    public void setValue(final String value) throws DOMException {
        disallowUpdate();
    }

    @Override
    public Element getOwnerElement() {
        if (ownerElement != null) {
            return ownerElement;
        }

        return (Element)tinyTreeWithId.getNode(tinyTreeWithId.tinyTree.getAttributeParentArray()[nodeNr]);
    }

    @Override
    public TypeInfo getSchemaTypeInfo() {
        return node.getSchemaTypeInfo();
    }

    @Override
    public boolean isId() {
        return node.isId();
    }
    // </editor-fold>

    // <editor-fold desc="org.exist.xquery.value.Item implementation">
    @Override
    public int getType() {
        return Type.ATTRIBUTE;
    }
    // </editor-fold>

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
    public void selectDescendantAttributes(final NodeTest test, final Sequence result) throws XPathException {
        if(test.matches(this)) {
            result.add(this);
        }
    }

    // <editor-fold desc="INode implementation">
    @Override
    public QName getQName() {
        final TinyNodeImpl tinyNode = getTinyNode(node);
        return new QName(tinyNode.getLocalPart(), tinyNode.getURI(), tinyNode.getPrefix(), ElementValue.ATTRIBUTE);
    }
    // </editor-fold>
}
