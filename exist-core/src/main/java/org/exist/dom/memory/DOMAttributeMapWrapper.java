package org.exist.dom.memory;

import org.w3c.dom.DOMException;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

public class DOMAttributeMapWrapper implements NamedNodeMap {

    private final TinyTreeWithId tinyTreeWithId;
    private final NamedNodeMap namedNodeMap;

    public DOMAttributeMapWrapper(final TinyTreeWithId tinyTreeWithId, final NamedNodeMap namedNodeMap) {
        this.tinyTreeWithId = tinyTreeWithId;
        this.namedNodeMap = namedNodeMap;
    }

    @Override
    public Node getNamedItem(final String name) {
        return TinyTreeWithId.wrap(tinyTreeWithId, namedNodeMap.getNamedItem(name));
    }

    @Override
    public Node setNamedItem(final Node arg) throws DOMException {
        return TinyTreeWithId.wrap(tinyTreeWithId, namedNodeMap.setNamedItem(arg));
    }

    @Override
    public Node removeNamedItem(final String name) throws DOMException {
        return TinyTreeWithId.wrap(tinyTreeWithId, namedNodeMap.removeNamedItem(name));
    }

    @Override
    public Node item(final int index) {
        return TinyTreeWithId.wrap(tinyTreeWithId, namedNodeMap.item(index));
    }

    @Override
    public int getLength() {
        return namedNodeMap.getLength();
    }

    @Override
    public Node getNamedItemNS(final String namespaceURI, final String localName) throws DOMException {
        return TinyTreeWithId.wrap(tinyTreeWithId, namedNodeMap.getNamedItemNS(namespaceURI, localName));
    }

    @Override
    public Node setNamedItemNS(final Node arg) throws DOMException {
        return TinyTreeWithId.wrap(tinyTreeWithId, namedNodeMap.setNamedItemNS(arg));
    }

    @Override
    public Node removeNamedItemNS(final String namespaceURI, final String localName) throws DOMException {
        return TinyTreeWithId.wrap(tinyTreeWithId, namedNodeMap.removeNamedItemNS(namespaceURI, localName));
    }
}
