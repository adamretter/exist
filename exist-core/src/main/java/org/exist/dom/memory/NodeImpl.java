package org.exist.dom.memory;

import net.sf.saxon.dom.NodeOverNodeInfo;
import net.sf.saxon.tree.tiny.TinyNodeImpl;
import net.sf.saxon.tree.tiny.TinyTree;
import net.sf.saxon.type.Type;
import org.exist.collections.Collection;
import org.exist.dom.INode;
import org.exist.dom.QName;
import org.exist.dom.memtree.DocumentBuilderReceiver;
import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.persistent.EmptyNodeSet;
import org.exist.dom.persistent.NodeHandle;
import org.exist.dom.persistent.NodeSet;
import org.exist.numbering.NodeId;
import org.exist.storage.DBBroker;
import org.exist.storage.serializers.Serializer;
import org.exist.util.serializer.Receiver;
import org.exist.xquery.*;
import org.exist.xquery.value.*;
import org.w3c.dom.*;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.ext.LexicalHandler;

import java.util.Iterator;
import java.util.Objects;
import java.util.Properties;

public abstract class NodeImpl<T extends org.exist.dom.memory.NodeImpl, N extends org.w3c.dom.Node> implements INode<DocumentImpl, T>, NodeValue {
    protected final TinyTreeWithId tinyTreeWithId;
    protected final int nodeNr;
    protected final N node;

    // <editor-fold desc="org.w3c.dom.Node implementation">
    //public NodeImpl(final TinyNodeImpl nodeInfo) {
    public NodeImpl(final TinyTreeWithId tinyTreeWithId, final int nodeNr) {
        this.tinyTreeWithId = tinyTreeWithId;
        this.nodeNr = nodeNr;
        this.node = (N) NodeOverNodeInfo.wrap(Objects.requireNonNull(tinyTreeWithId.tinyTree.getNode(nodeNr)));
    }

    public NodeImpl(final TinyTreeWithId tinyTreeWithId, final TinyNodeImpl tinyNode) {
        this.tinyTreeWithId = tinyTreeWithId;
        this.nodeNr = Objects.requireNonNull(tinyNode).getNodeNumber();
        this.node = (N) NodeOverNodeInfo.wrap(tinyNode);
    }

    @Override
    public String getNodeName() {
        return node.getNodeName();
    }

    @Override
    public String getNodeValue() throws DOMException {
        return node.getNodeValue();
    }

    @Override
    public void setNodeValue(final String nodeValue) throws DOMException {
        node.setNodeValue(nodeValue);
    }

    @Override
    public short getNodeType() {
        return node.getNodeType();
    }

    @Override
    public Node getParentNode() {
        return node.getParentNode();
    }

    @Override
    public NodeList getChildNodes() {
        return node.getChildNodes();
    }

    @Override
    public Node getFirstChild() {
        return null;
    }

    @Override
    public Node getLastChild() {
        return node.getLastChild();
    }

    @Override
    public Node getPreviousSibling() {
        return node.getPreviousSibling();
    }

    @Override
    public Node getNextSibling() {
        return node.getNextSibling();
    }

    @Override
    public NamedNodeMap getAttributes() {
        return node.getAttributes();
    }

    @Override
    public Node insertBefore(final Node newChild, final Node refChild) throws DOMException {
        return node.insertBefore(newChild, refChild);
    }

    @Override
    public Node replaceChild(final Node newChild, final Node oldChild) throws DOMException {
        return node.replaceChild(newChild, oldChild);
    }

    @Override
    public Node removeChild(final Node oldChild) throws DOMException {
        return node.removeChild(oldChild);
    }

    @Override
    public Node appendChild(final Node newChild) throws DOMException {
        return node.appendChild(newChild);
    }

    @Override
    public boolean hasChildNodes() {
        return node.hasChildNodes();
    }

    @Override
    public Node cloneNode(final boolean deep) {
        return node.cloneNode(deep);
    }

    @Override
    public void normalize() {
        node.normalize();
    }

    @Override
    public boolean isSupported(final String feature, final String version) {
        return node.isSupported(feature, version);
    }

    @Override
    public String getNamespaceURI() {
        return node.getNamespaceURI();
    }

    @Override
    public String getPrefix() {
        return node.getPrefix();
    }

    @Override
    public void setPrefix(final String prefix) throws DOMException {
        node.setPrefix(prefix);
    }

    @Override
    public String getLocalName() {
        return node.getLocalName();
    }

    @Override
    public boolean hasAttributes() {
        return node.hasAttributes();
    }

    @Override
    public String getBaseURI() {
        return node.getBaseURI();
    }

    @Override
    public short compareDocumentPosition(final Node other) throws DOMException {
        return node.compareDocumentPosition(other);
    }

    @Override
    public String getTextContent() throws DOMException {
        return node.getTextContent();
    }

    @Override
    public void setTextContent(final String textContent) throws DOMException {
        node.setTextContent(textContent);
    }

    @Override
    public boolean isSameNode(final Node other) {
        return node.isSameNode(other);
    }

    @Override
    public String lookupPrefix(final String namespaceURI) {
        return node.lookupPrefix(namespaceURI);
    }

    @Override
    public boolean isDefaultNamespace(final String namespaceURI) {
        return node.isDefaultNamespace(namespaceURI);
    }

    @Override
    public String lookupNamespaceURI(final String prefix) {
        return node.lookupNamespaceURI(prefix);
    }

    @Override
    public boolean isEqualNode(final Node arg) {
        return node.isEqualNode(arg);
    }

    @Override
    public Object getFeature(final String feature, final String version) {
        return node.getFeature(feature, version);
    }

    @Override
    public Object setUserData(final String key, final Object data, final UserDataHandler handler) {
        return setUserData(key, data, handler);
    }

    @Override
    public Object getUserData(final String key) {
        return node.getUserData(key);
    }
    // </editor-fold>


    // <editor-fold desc="INodeHandle implementation">
    @Override
    public NodeId getNodeId() {
        return tinyTreeWithId.getNodeId(nodeNr);
    }

    @Override
    public DocumentImpl getOwnerDocument() {
        if (node instanceof Document) {
            return (DocumentImpl)this;
        } else {
            final Document ownerDocument = node.getOwnerDocument();
            if (ownerDocument == null) {
                return null;
            }
            final int docNodeNumber = ((TinyNodeImpl)((NodeOverNodeInfo)ownerDocument).getUnderlyingNodeInfo()).getNodeNumber();
            return new DocumentImpl(tinyTreeWithId, docNodeNumber);
        }
    }
    // </editor-fold>


    // <editor-fold desc="INode implementation">
    @Override
    public QName getQName() {
        return null;
    }

    @Override
    public void setQName(final QName qname) {
        disallowUpdate();
    }
    // </editor-fold>


    // <editor-fold desc="NodeValue implementation">
    @Override
    public boolean equals(final NodeValue other) throws XPathException {
        if(other.getImplementationType() != NodeValue.IN_MEMORY_SAXON_NODE) {
            return false;
        }
        final NodeImpl o = (NodeImpl) other;
        return tinyTreeWithId == o.tinyTreeWithId && nodeNr == o.nodeNr &&
                getNodeType() == o.getNodeType();
    }

    @Override
    public boolean before(NodeValue other, boolean isPreceding) throws XPathException {
        if (other.getImplementationType() != NodeValue.IN_MEMORY_SAXON_NODE) {
            throw new XPathException("cannot compare persistent node with in-memory Saxon node");
        }
        return nodeNr < ((NodeImpl)other).nodeNr;
    }

    @Override
    public boolean after(final NodeValue other, final boolean isFollowing) throws XPathException {
        if (other.getImplementationType() != NodeValue.IN_MEMORY_SAXON_NODE) {
            throw new XPathException("cannot compare persistent node with in-memory Saxon node");
        }
        return nodeNr > ((NodeImpl)other).nodeNr;
    }

    @Override
    public int getImplementationType() {
        return NodeValue.IN_MEMORY_SAXON_NODE;
    }

    @Override
    public void addContextNode(final int contextId, final NodeValue node) {
        throw unsupported();
    }

    @Override
    public Node getNode() {
        return this;
    }
    // </editor-fold>


    // <editor-fold desc="org.exist.xquery.value.Item implementation">
    @Override
    public String getStringValue() {
        final TinyTree tt = tinyTreeWithId.tinyTree;
        final int level = tt.getNodeDepthArray()[nodeNr];
        int next = nodeNr + 1;
        int startOffset = 0;
        int len = -1;

        while (next < tt.getNumberOfNodes() && tt.getNodeDepthArray()[next] > level) {
            if(
                    (tt.getNodeKind(next) == Node.TEXT_NODE)
                            || (tt.getNodeKind(next) == Node.CDATA_SECTION_NODE)
                            || (tt.getNodeKind(next) == Node.PROCESSING_INSTRUCTION_NODE)
            ) {
                if (len < 0) {
                    startOffset = tt.getAlphaArray()[next];
                    len = tt.getBetaArray()[next];
                } else {
                    len += tt.getBetaArray()[next];
                }
            } else {
                return getStringValueSlow();
            }
            ++next;
        }

        return len < 0 ? "" : tt.getCharacterBuffer().subSequence(startOffset, startOffset + len).toString();
    }

    private String getStringValueSlow() {
        final TinyTree tt = tinyTreeWithId.tinyTree;
        final int level = tt.getNodeDepthArray()[nodeNr];
        StringBuilder buf = null;
        int next = nodeNr + 1;

        while (next < tt.getNumberOfNodes() && tt.getNodeDepthArray()[next] > level) {
            switch (tt.getNodeKind(next)) {
                case Type.TEXT:
                //case Type.CDATA_SECTION_NODE:
                case Type.PROCESSING_INSTRUCTION: {
                    if(buf == null) {
                        buf = new StringBuilder();
                    }

                    buf.append(
                            tt.getCharacterBuffer().subSequence(tt.getAlphaArray()[next], tt.getAlphaArray()[next] + tt.getBetaArray()[next])
                    );
                    break;
                }
                //TODO(AR) figure this out
//                case REFERENCE_NODE: {
//                    if(buf == null) {
//                        buf = new StringBuilder();
//                    }
//                    buf.append(document.references[document.alpha[next]].getStringValue());
//                    break;
//                }
            }
            ++next;
        }
        return ((buf == null) ? "" : buf.toString());
    }

    @Override
    public Sequence toSequence() {
        return this;
    }

    @Override
    public void destroy(final XQueryContext context, final Sequence contextSequence) {
        //no-op
    }

    @Override
    public AtomicValue convertTo(final int requiredType) throws XPathException {
        return UntypedAtomicValue.convertTo(null, getStringValue(), requiredType);
    }

    @Override
    public AtomicValue atomize() throws XPathException {
        return new UntypedAtomicValue(getStringValue());
    }

    @Override
    public void toSAX(final DBBroker broker, final ContentHandler handler, final Properties properties) throws SAXException {
        final Serializer serializer = broker.getSerializer();
        serializer.reset();
        serializer.setProperty(Serializer.GENERATE_DOC_EVENTS, "false");

        if(properties != null) {
            serializer.setProperties(properties);
        }

        if(handler instanceof LexicalHandler) {
            serializer.setSAXHandlers(handler, (LexicalHandler) handler);
        } else {
            serializer.setSAXHandlers(handler, null);
        }

        serializer.toSAX(this);
    }

    @Override
    public void copyTo(final DBBroker broker, final DocumentBuilderReceiver receiver) throws SAXException {
        tinyTreeWithId.copyTo(nodeNr, receiver);
    }

    public void streamTo(final Serializer serializer, final Receiver receiver) throws SAXException {
        tinyTreeWithId.streamTo(serializer, nodeNr, receiver);
    }

    @Override
    public int conversionPreference(final Class<?> javaClass) {
        final int preference;
        if(javaClass.isAssignableFrom(NodeImpl.class)) {
            preference = 0;
        } else if(javaClass.isAssignableFrom(Node.class)) {
            preference = 1;
        } else if((javaClass == String.class) || (javaClass == CharSequence.class)) {
            preference = 2;
        } else if((javaClass == Character.class) || (javaClass == char.class)) {
            preference = 2;
        } else if((javaClass == Double.class) || (javaClass == double.class)) {
            preference = 10;
        } else if((javaClass == Float.class) || (javaClass == float.class)) {
            preference = 11;
        } else if((javaClass == Long.class) || (javaClass == long.class)) {
            preference = 12;
        } else if((javaClass == Integer.class) || (javaClass == int.class)) {
            preference = 13;
        } else if((javaClass == Short.class) || (javaClass == short.class)) {
            preference = 14;
        } else if((javaClass == Byte.class) || (javaClass == byte.class)) {
            preference = 15;
        } else if((javaClass == Boolean.class) || (javaClass == boolean.class)) {
            preference = 16;
        } else if(javaClass == Object.class) {
            preference = 20;
        } else {
            preference = Integer.MAX_VALUE;
        }
        return preference;
    }

    @Override
    public <T> T toJavaObject(final Class<T> target) throws XPathException {
        if(target.isAssignableFrom(NodeImpl.class) || target.isAssignableFrom(Node.class) || target == Object.class) {
            return (T) this;
        } else {
            final StringValue v = new StringValue(getStringValue());
            return v.toJavaObject(target);
        }
    }

    @Override
    public void nodeMoved(final NodeId oldNodeId, final NodeHandle newNode) {
        //no-op
    }
    // </editor-fold>


    // <editor-fold desc="Sequence implementation">

    @Override
    public void add(final Item item) throws XPathException {
        throw unsupported();
    }

    @Override
    public void addAll(final Sequence other) throws XPathException {
        throw unsupported();
    }

    @Override
    public int getItemType() {
        return org.exist.xquery.value.Type.NODE;
    }

    @Override
    public SequenceIterator iterate() throws XPathException {
        return new SingleNodeIterator(this);
    }

    @Override
    public SequenceIterator unorderedIterator() throws XPathException {
        return iterate();
    }

    @Override
    public int getItemCount() {
        return 1;
    }

    @Override
    public long getItemCountLong() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public boolean hasOne() {
        return true;
    }

    @Override
    public boolean hasMany() {
        return false;
    }

    @Override
    public void removeDuplicates() {
        // no-op
    }

    @Override
    public int getCardinality() {
        return Cardinality.EXACTLY_ONE;
    }

    @Override
    public Item itemAt(final int pos) {
        return pos == 0 ? this : null;
    }

    @Override
    public Sequence tail() throws XPathException {
        return Sequence.EMPTY_SEQUENCE;
    }

    @Override
    public boolean effectiveBooleanValue() throws XPathException {
        //A node evaluates to true()
        return true;
    }

    @Override
    public NodeSet toNodeSet() throws XPathException {
        final ValueSequence seq = new ValueSequence();
        seq.add(this);
        return seq.toNodeSet();
    }

    @Override
    public MemoryNodeSet toMemNodeSet() throws XPathException {
        return new ValueSequence(this).toMemNodeSet();
    }

    @Override
    public DocumentSet getDocumentSet() {
        return DocumentSet.EMPTY_DOCUMENT_SET;
    }

    @Override
    public Iterator<Collection> getCollectionIterator() {
        return EmptyNodeSet.EMPTY_COLLECTION_ITERATOR;
    }

    @Override
    public boolean isCached() {
        // always return false
        return false;
    }

    @Override
    public void setIsCached(final boolean cached) {
        throw unsupported();
    }

    @Override
    public void clearContext(final int contextId) throws XPathException {
        //no-op
    }

    @Override
    public void setSelfAsContext(final int contextId) throws XPathException {
        throw unsupported();
    }

    @Override
    public boolean isPersistentSet() {
        return false;
    }

    @Override
    public boolean isCacheable() {
        return true;
    }

    @Override
    public int getState() {
        return 0;
    }

    @Override
    public boolean hasChanged(final int previousState) {
        return false;
    }
    // </editor-fold>


    // <editor-fold desc="Comparable implementation">
    @Override
    public int compareTo(final NodeImpl other) {
        if (other.tinyTreeWithId == tinyTreeWithId) {
            if (nodeNr == other.nodeNr && getNodeType() == other.getNodeType()) {
                return Constants.EQUAL;
            } else if(nodeNr < other.nodeNr) {
                return Constants.INFERIOR;
            } else {
                return Constants.SUPERIOR;
            }
        } else if(tinyTreeWithId.tinyTree.getDocumentNumber() < other.tinyTreeWithId.tinyTree.getDocumentNumber()) {
            return Constants.INFERIOR;
        } else {
            return Constants.SUPERIOR;
        }
    }
    // </editor-fold>


    protected DOMException unsupported() {
        return new DOMException(DOMException.NOT_SUPPORTED_ERR, "not implemented on class: " + getClass().getName());
    }

    private final static class SingleNodeIterator implements SequenceIterator {
        private NodeImpl node;

        public SingleNodeIterator(final NodeImpl node) {
            this.node = node;
        }

        @Override
        public boolean hasNext() {
            return node != null;
        }

        @Override
        public Item nextItem() {
            final NodeImpl next = node;
            node = null;
            return next;
        }

        @Override
        public long skippable() {
            if (node != null) {
                return 1;
            }
            return 0;
        }

        @Override
        public long skip(final long n) {
            final long skip = Math.min(n, node != null ? 1 : 0);
            if (skip == 1) {
                node = null;
            }
            return skip;
        }
    }

    static void disallowUpdate() {
        throw new UnsupportedOperationException("Wrapper - The Saxon DOM implementation cannot be updated");
    }

    //TODO(AR) TEMP BELOW this line <--- until we figure out a better interface - we shouldn't always have to build a DocumentImpl for just a node

    public void selectDescendants(final boolean includeSelf, final NodeTest test, final Sequence result)
            throws XPathException {
        if(includeSelf && test.matches(this)) {
            result.add(this);
        }
    }
}
