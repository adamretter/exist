package org.exist.dom.memory;

import net.sf.saxon.dom.DOMNodeList;
import net.sf.saxon.dom.ElementOverNodeInfo;
import net.sf.saxon.dom.NodeOverNodeInfo;
import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.tiny.TinyNodeImpl;
import org.exist.dom.QName;
import org.exist.storage.ElementValue;
import org.exist.xquery.NodeTest;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;
import org.w3c.dom.*;

import java.util.HashMap;
import java.util.Map;

public class ElementImpl extends NodeImpl<ElementImpl, Element> implements Element {

    ElementImpl(final TinyTreeWithId tinyTreeWithId, final int nodeNr) {
        super(tinyTreeWithId, nodeNr);
        if (node.getNodeType() != NodeImpl.ELEMENT_NODE) {
            throw new IllegalArgumentException("element argument must be of Element type");
        }
    }

    ElementImpl(final TinyTreeWithId tinyTreeWithId, final TinyNodeImpl tinyNode) {
        super(tinyTreeWithId, tinyNode);
        if (node.getNodeType() != NodeImpl.ELEMENT_NODE) {
            throw new IllegalArgumentException("element argument must be of Element type");
        }
    }

    ElementImpl(final TinyTreeWithId tinyTreeWithId, final NodeOverNodeInfo nodeOverNodeInfo) {
        super(tinyTreeWithId, nodeOverNodeInfo);
        if (node.getNodeType() != NodeImpl.ELEMENT_NODE) {
            throw new IllegalArgumentException("element argument must be of Element type");
        }
    }

    // <editor-fold desc="org.w3c.dom.Element implementation">
    @Override
    public String getTagName() {
        return node.getTagName();
    }

    @Override
    public String getAttribute(final String name) {
        return node.getAttribute(name);
    }

    @Override
    public void setAttribute(final String name, final String value) throws DOMException {
        disallowUpdate();
    }

    @Override
    public void removeAttribute(final String name) throws DOMException {
        disallowUpdate();
    }

    @Override
    public Attr getAttributeNode(final String name) {
        final Attr attr = node.getAttributeNode(name);
        if (attr == null) {
            return null;
        }
        return new AttrImpl(tinyTreeWithId, getTinyNode(attr), this);
    }

    @Override
    public Attr setAttributeNode(final Attr newAttr) throws DOMException {
        disallowUpdate();
        return null;
    }

    @Override
    public Attr setAttributeNodeNS(final Attr newAttr) throws DOMException {
        disallowUpdate();
        return null;
    }

    @Override
    public Attr removeAttributeNode(final Attr oldAttr) throws DOMException {
        disallowUpdate();
        return null;
    }

    @Override
    public NodeList getElementsByTagName(final String name) {
        final NodeList nodeList = node.getElementsByTagName(name);
        return new DOMNodeListWrapper(tinyTreeWithId, (DOMNodeList)nodeList);
    }

    @Override
    public String getAttributeNS(final String namespaceURI, final String localName) throws DOMException {
        return node.getAttributeNS(namespaceURI, localName);
    }

    @Override
    public void setAttributeNS(final String namespaceURI, final String qualifiedName, final String value) throws DOMException {
        disallowUpdate();
    }

    @Override
    public void removeAttributeNS(final String namespaceURI, final String localName) throws DOMException {
        disallowUpdate();
    }

    @Override
    public Attr getAttributeNodeNS(final String namespaceURI, final String localName) throws DOMException {
        final Attr attr = node.getAttributeNodeNS(namespaceURI, localName);
        if (attr == null) {
            return null;
        }
        return new AttrImpl(tinyTreeWithId, getTinyNode(attr), this);
    }

    @Override
    public NodeList getElementsByTagNameNS(final String namespaceURI, final String localName) throws DOMException {
        final NodeList nodeList = node.getElementsByTagNameNS(namespaceURI, localName);
        return new DOMNodeListWrapper(tinyTreeWithId, (DOMNodeList)nodeList);
    }

    @Override
    public boolean hasAttribute(final String name) {
        return node.hasAttribute(name);
    }

    @Override
    public boolean hasAttributeNS(final String namespaceURI, final String localName) throws DOMException {
        return node.hasAttributeNS(namespaceURI, localName);
    }

    @Override
    public TypeInfo getSchemaTypeInfo() {
        return node.getSchemaTypeInfo();
    }

    @Override
    public void setIdAttribute(final String name, final boolean isId) throws DOMException {
        disallowUpdate();
    }

    @Override
    public void setIdAttributeNS(final String namespaceURI, final String localName, final boolean isId) throws DOMException {
        disallowUpdate();
    }

    @Override
    public void setIdAttributeNode(final Attr idAttr, final boolean isId) throws DOMException {
        disallowUpdate();
    }
    // </editor-fold>


    // <editor-fold desc="org.exist.xquery.value.Item implementation">
    @Override
    public int getType() {
        return Type.ELEMENT;
    }
    // </editor-fold>


    @Override
    public void selectAttributes(final NodeTest test, final Sequence result) throws XPathException {
        try (final AxisIterator iterator = ((NodeOverNodeInfo) node).getUnderlyingNodeInfo().iterateAxis(AxisInfo.ATTRIBUTE)) {
            NodeInfo item;
            while ((item = iterator.next()) != null) {
                final AttrImpl attrib = new AttrImpl(tinyTreeWithId, (TinyNodeImpl) item, this);
                if (test.matches(attrib)) {
                    result.add(attrib);
                }
            }
        }
    }

    @Override
    public void selectDescendantAttributes(final NodeTest test, final Sequence result) throws XPathException {
        try (final AxisIterator iterator = ((NodeOverNodeInfo) node).getUnderlyingNodeInfo().iterateAxis(AxisInfo.DESCENDANT, NodeKindTest.ATTRIBUTE)) {
            NodeInfo item;
            while ((item = iterator.next()) != null) {
                final AttrImpl attrib = new AttrImpl(tinyTreeWithId, (TinyNodeImpl) item, this);
                if (test.matches(attrib)) {
                    result.add(attrib);
                }
            }
        }
    }

    @Override
    public void selectChildren(final NodeTest test, final Sequence result) throws XPathException {
        try (final AxisIterator iterator = ((NodeOverNodeInfo) node).getUnderlyingNodeInfo().iterateAxis(AxisInfo.CHILD)) {
            NodeInfo item;
            while ((item = iterator.next()) != null) {
                final NodeImpl node = TinyTreeWithId.wrap(tinyTreeWithId, NodeOverNodeInfo.wrap(item));
                if (test.matches(node)) {
                    result.add(node);
                }
            }
        }
    }

    @Override
    public void selectDescendants(final boolean includeSelf, final NodeTest test, final Sequence result)
            throws XPathException {
        try (final AxisIterator iterator = ((NodeOverNodeInfo) node).getUnderlyingNodeInfo().iterateAxis(includeSelf ? AxisInfo.DESCENDANT_OR_SELF : AxisInfo.DESCENDANT)) {
            NodeInfo item;
            while ((item = iterator.next()) != null) {
                final NodeImpl node = TinyTreeWithId.wrap(tinyTreeWithId, NodeOverNodeInfo.wrap(item));
                if (test.matches(node)) {
                    result.add(node);
                }
            }
        }
    }

    // <editor-fold desc="INode implementation">
    @Override
    public QName getQName() {
        final TinyNodeImpl tinyNode = getTinyNode(node);
        return new QName(tinyNode.getLocalPart(), tinyNode.getURI(), tinyNode.getPrefix(), ElementValue.ELEMENT);
    }
    // </editor-fold>

    public boolean declaresNamespacePrefixes() {
        return tinyTreeWithId.getNamespacesCountFor(nodeNr) > 0;
    }

    public Map<String, String> getNamespaceMap() {
        return getNamespaceMap(new HashMap<>());
    }

    public Map<String, String> getNamespaceMap(final Map<String, String> map) {
        return tinyTreeWithId.getNamespaceMap(map, nodeNr);
    }
}
