package org.exist.dom.memory;

import net.sf.saxon.tree.tiny.TinyNodeImpl;
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
}
