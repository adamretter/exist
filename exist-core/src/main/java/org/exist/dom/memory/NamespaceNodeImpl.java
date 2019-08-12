package org.exist.dom.memory;

import net.sf.saxon.dom.NodeOverNodeInfo;
import org.exist.xquery.NodeTest;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Sequence;
import org.w3c.dom.Attr;
import org.w3c.dom.DOMException;
import org.w3c.dom.Element;
import org.w3c.dom.TypeInfo;

import javax.annotation.Nullable;

public class NamespaceNodeImpl extends NodeImpl<NamespaceNodeImpl, Attr> implements Attr {

    NamespaceNodeImpl(final TinyTreeWithId tinyTreeWithId, final NodeOverNodeInfo nodeOverNodeInfo, @Nullable final ElementImpl ownerElement) {
        super(tinyTreeWithId, nodeOverNodeInfo);
        if (node.getNodeType() != NodeImpl.ATTRIBUTE_NODE) {
            throw new IllegalArgumentException("attribute argument must be of Attribute type");
        }
//        this.ownerElement = ownerElement;
    }

    @Override
    public void selectAttributes(NodeTest test, Sequence result) throws XPathException {

    }

    @Override
    public void selectDescendantAttributes(NodeTest test, Sequence result) throws XPathException {

    }

    @Override
    public void selectChildren(NodeTest test, Sequence result) throws XPathException {

    }

    @Override
    public int compareTo(NamespaceNodeImpl o) {
        return 0;
    }

    @Override
    public int getType() {
        return 0;
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public boolean getSpecified() {
        return false;
    }

    @Override
    public String getValue() {
        return null;
    }

    @Override
    public void setValue(String value) throws DOMException {

    }

    @Override
    public Element getOwnerElement() {
        return null;
    }

    @Override
    public TypeInfo getSchemaTypeInfo() {
        return null;
    }

    @Override
    public boolean isId() {
        return false;
    }
}
