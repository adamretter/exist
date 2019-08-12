package org.exist.dom.memory;

import net.sf.saxon.dom.DOMNodeList;
import net.sf.saxon.dom.NodeOverNodeInfo;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.expr.parser.Location;
import net.sf.saxon.om.*;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.tiny.TinyNodeImpl;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.SimpleType;
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
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.w3c.dom.*;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.ext.LexicalHandler;

import java.util.*;

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

    public NodeImpl(final TinyTreeWithId tinyTreeWithId, final NodeOverNodeInfo nodeOverNodeInfo) {
        this.tinyTreeWithId = tinyTreeWithId;
        final NodeInfo underlyingNode = Objects.requireNonNull(nodeOverNodeInfo).getUnderlyingNodeInfo();
        if (underlyingNode instanceof net.sf.saxon.tree.NamespaceNode) {
            this.nodeNr = -1;
        } else {
            this.nodeNr = ((TinyNodeImpl) underlyingNode).getNodeNumber();
        }
        this.node = (N) nodeOverNodeInfo;
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
        return TinyTreeWithId.wrap(tinyTreeWithId, node.getParentNode());
    }

    @Override
    public NodeList getChildNodes() {
        return new DOMNodeListWrapper(tinyTreeWithId, (DOMNodeList) node.getChildNodes());
    }

    @Override
    public Node getFirstChild() {
        return TinyTreeWithId.wrap(tinyTreeWithId, node.getFirstChild());
    }

    @Override
    public Node getLastChild() {
        return TinyTreeWithId.wrap(tinyTreeWithId, node.getLastChild());
    }

    @Override
    public Node getPreviousSibling() {
        return TinyTreeWithId.wrap(tinyTreeWithId, node.getPreviousSibling());
    }

    @Override
    public Node getNextSibling() {
        return TinyTreeWithId.wrap(tinyTreeWithId, node.getNextSibling());
    }

    @Override
    public NamedNodeMap getAttributes() {
        final NamedNodeMap namedNodeMap = node.getAttributes();
        if (namedNodeMap == null) {
            return null;
        }
        return new DOMAttributeMapWrapper(tinyTreeWithId, namedNodeMap);
    }

    @Override
    public Node insertBefore(final Node newChild, final Node refChild) throws DOMException {
        return TinyTreeWithId.wrap(tinyTreeWithId, node.insertBefore(newChild, refChild));
    }

    @Override
    public Node replaceChild(final Node newChild, final Node oldChild) throws DOMException {
        return TinyTreeWithId.wrap(tinyTreeWithId, node.replaceChild(newChild, oldChild));
    }

    @Override
    public Node removeChild(final Node oldChild) throws DOMException {
        return TinyTreeWithId.wrap(tinyTreeWithId, node.removeChild(oldChild));
    }

    @Override
    public Node appendChild(final Node newChild) throws DOMException {
        return TinyTreeWithId.wrap(tinyTreeWithId, node.appendChild(newChild));
    }

    @Override
    public boolean hasChildNodes() {
        return node.hasChildNodes();
    }

    @Override
    public Node cloneNode(final boolean deep) {
        return TinyTreeWithId.wrap(tinyTreeWithId, node.cloneNode(deep));
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
            return (DocumentImpl) this;
        } else {
            final Document ownerDocument = node.getOwnerDocument();
            if (ownerDocument == null) {
                return null;
            }
            return (DocumentImpl) TinyTreeWithId.wrap(tinyTreeWithId, ownerDocument);
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

    @Override
    public boolean equals(final Object other) {
        if(!(other instanceof NodeImpl)) {
            return false;
        }
        final NodeImpl o = (NodeImpl) other;
        return tinyTreeWithId == o.tinyTreeWithId && nodeNr == o.nodeNr &&
                getNodeType() == o.getNodeType();
    }

    // <editor-fold desc="NodeValue implementation">
    @Override
    public boolean equals(final NodeValue other) throws XPathException {
        if (other.getImplementationType() != NodeValue.IN_MEMORY_SAXON_NODE) {
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
        return nodeNr < ((NodeImpl) other).nodeNr;
    }

    @Override
    public boolean after(final NodeValue other, final boolean isFollowing) throws XPathException {
        if (other.getImplementationType() != NodeValue.IN_MEMORY_SAXON_NODE) {
            throw new XPathException("cannot compare persistent node with in-memory Saxon node");
        }
        return nodeNr > ((NodeImpl) other).nodeNr;
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
        return getTinyNode(node).getStringValue();
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
        //TODO(AR) replace with  tinyNode.copy(new ReceiverAdapter(tinyNode, receiver), 0, tinyNode);
        //tinyTreeWithId.copyTo(nodeNr, receiver);

        final TinyNodeImpl tinyNode = getTinyNode(node);
        try {
            tinyNode.copy(new ReceiverAdapter(tinyNode, receiver), 0, tinyNode);
        } catch (net.sf.saxon.trans.XPathException e) {
            if (e.getCause() instanceof SAXException) {
                throw (SAXException)e.getCause();
            } else {
                throw new SAXException(e);
            }
        }
    }

    public void streamTo(final Serializer serializer, final Receiver receiver) throws SAXException {
        final TinyNodeImpl tinyNode = getTinyNode(node);
        try {
            tinyNode.copy(new ReceiverAdapter(tinyNode, receiver), 0, tinyNode);
        } catch (net.sf.saxon.trans.XPathException e) {
            if (e.getCause() instanceof SAXException) {
                throw (SAXException)e.getCause();
            } else {
                throw new SAXException(e);
            }
        }
    }

    private static class ReceiverAdapter implements net.sf.saxon.event.Receiver {
        private final Receiver receiver;
        private PipelineConfiguration pipe;
        private String systemId;
        private final Deque<QName> elemNames = new ArrayDeque<>();

        public ReceiverAdapter(final TinyNodeImpl tinyNode, final Receiver receiver) {
            this.pipe = tinyNode.getConfiguration().makePipelineConfiguration();
            this.receiver = receiver;
        }

        @Override
        public void setPipelineConfiguration(final PipelineConfiguration pipe) {
            this.pipe = pipe;
        }

        @Override
        public PipelineConfiguration getPipelineConfiguration() {
            return pipe;
        }

        @Override
        public void setSystemId(final String systemId) {
            this.systemId = systemId;
        }

        @Override
        public String getSystemId() {
            return systemId;
        }

        @Override
        public void open() throws net.sf.saxon.trans.XPathException {
        }

        @Override
        public void startDocument(int properties) throws net.sf.saxon.trans.XPathException {
            try {
                receiver.startDocument();
            } catch (SAXException e) {
                throw new net.sf.saxon.trans.XPathException(e);
            }
        }

        @Override
        public void endDocument() throws net.sf.saxon.trans.XPathException {
            try {
                receiver.endDocument();
            } catch (SAXException e) {
                throw new net.sf.saxon.trans.XPathException(e);
            }
        }

        @Override
        public void setUnparsedEntity(String name, String systemID, String publicID) throws net.sf.saxon.trans.XPathException {

        }

        @Override
        public void startElement(NodeName elemName, SchemaType typeCode, Location location, int properties) throws net.sf.saxon.trans.XPathException {
            try {
                final QName name = QName.fromJavaQName(elemName.getStructuredQName().toJaxpQName());
                receiver.startElement(name, null);
                elemNames.push(name);
            } catch (SAXException e) {
                throw new net.sf.saxon.trans.XPathException(e);
            }
        }

        @Override
        public void namespace(NamespaceBindingSet namespaceBindings, int properties) throws net.sf.saxon.trans.XPathException {
            //TODO(AR) implement
            final Iterator<NamespaceBinding> iterator = namespaceBindings.iterator();
            try {
                while (iterator.hasNext()) {
                    final NamespaceBinding binding = iterator.next();
                    receiver.startPrefixMapping(binding.getPrefix(), binding.getURI());
                }
            } catch (SAXException e) {
                throw new net.sf.saxon.trans.XPathException(e);
            }
        }

        @Override
        public void attribute(NodeName attName, SimpleType typeCode, CharSequence value, Location location, int properties) throws net.sf.saxon.trans.XPathException {
            try {
                receiver.attribute(QName.fromJavaQName(attName.getStructuredQName().toJaxpQName()), value.toString());
            } catch (SAXException e) {
                throw new net.sf.saxon.trans.XPathException(e);
            }
        }

        @Override
        public void startContent() throws net.sf.saxon.trans.XPathException {

        }

        @Override
        public void endElement() throws net.sf.saxon.trans.XPathException {
            try {
                receiver.endElement(elemNames.pop());
            } catch (SAXException e) {
                throw new net.sf.saxon.trans.XPathException(e);
            }
        }

        @Override
        public void characters(CharSequence chars, Location location, int properties) throws net.sf.saxon.trans.XPathException {
            try {
                receiver.characters(chars);
            } catch (SAXException e) {
                throw new net.sf.saxon.trans.XPathException(e);
            }
        }

        @Override
        public void processingInstruction(String name, CharSequence data, Location location, int properties) throws net.sf.saxon.trans.XPathException {
            try {
                receiver.processingInstruction(name, data.toString());
            } catch (SAXException e) {
                throw new net.sf.saxon.trans.XPathException(e);
            }
        }

        @Override
        public void comment(CharSequence content, Location location, int properties) throws net.sf.saxon.trans.XPathException {
            final char[] data = content.toString().toCharArray();
            try {
                receiver.comment(data, 0, data.length);
            } catch (SAXException e) {
                throw new net.sf.saxon.trans.XPathException(e);
            }
        }

        @Override
        public void close() throws net.sf.saxon.trans.XPathException {

        }
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

    // TODO(AR) this really shouldn't be public!
    public int getNodeNumber() {
        return nodeNr;
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

    static TinyNodeImpl getTinyNode(final Node node) {
        return (TinyNodeImpl) ((NodeOverNodeInfo)node).getUnderlyingNodeInfo();
    }

    public abstract void selectAttributes(final NodeTest test, final Sequence result) throws XPathException;

    public abstract void selectDescendantAttributes(final NodeTest test, final Sequence result) throws XPathException;

    public abstract void selectChildren(final NodeTest test, final Sequence result) throws XPathException;

    public void selectDescendants(final boolean includeSelf, final NodeTest test, final Sequence result)
            throws XPathException {
        if(includeSelf && test.matches(this)) {
            result.add(this);
        }
    }

    public void selectAncestors(final boolean includeSelf, final NodeTest test, final Sequence result)
            throws XPathException {
        try (final AxisIterator iterator = ((NodeOverNodeInfo) node).getUnderlyingNodeInfo().iterateAxis(includeSelf ? AxisInfo.ANCESTOR_OR_SELF : AxisInfo.ANCESTOR)) {
            NodeInfo item;
            while ((item = iterator.next()) != null) {
                final NodeImpl node = TinyTreeWithId.wrap(tinyTreeWithId, NodeOverNodeInfo.wrap(item));
                if (test.matches(node)) {
                    result.add(node);
                }
            }
        }
    }

    public void selectPrecedingSiblings(final NodeTest test, final Sequence result)
            throws XPathException {
        try (final AxisIterator iterator = ((NodeOverNodeInfo) node).getUnderlyingNodeInfo().iterateAxis(AxisInfo.PRECEDING_SIBLING)) {
            NodeInfo item;
            while ((item = iterator.next()) != null) {
                final NodeImpl node = TinyTreeWithId.wrap(tinyTreeWithId, NodeOverNodeInfo.wrap(item));
                if (test.matches(node)) {
                    result.add(node);
                }
            }
        }
    }

    public void selectPreceding(final NodeTest test, final Sequence result, final int position)
            throws XPathException {
        final NodeId myNodeId = getNodeId();
        int count = 0;

        try (final AxisIterator iterator = ((NodeOverNodeInfo) node).getUnderlyingNodeInfo().iterateAxis(AxisInfo.PRECEDING)) {
            NodeInfo item;
            while ((item = iterator.next()) != null) {
                final NodeImpl node = TinyTreeWithId.wrap(tinyTreeWithId, NodeOverNodeInfo.wrap(item));

                if (!myNodeId.isDescendantOf(node.getNodeId()) && test.matches(node)) {
                    if ((position < 0) || (++count == position)) {
                        result.add(node);
                    }
                    if (count == position) {
                        break;
                    }
                }
            }
        }
    }

    public void selectFollowingSiblings(final NodeTest test, final Sequence result)
            throws XPathException {
        try (final AxisIterator iterator = ((NodeOverNodeInfo) node).getUnderlyingNodeInfo().iterateAxis(AxisInfo.FOLLOWING_SIBLING)) {
            NodeInfo item;
            while ((item = iterator.next()) != null) {
                final NodeImpl node = TinyTreeWithId.wrap(tinyTreeWithId, NodeOverNodeInfo.wrap(item));
                if (test.matches(node)) {
                    result.add(node);
                }
            }
        }
    }

    public void selectFollowing(final NodeTest test, final Sequence result, final int position)
            throws XPathException {
        try (final AxisIterator iterator = ((NodeOverNodeInfo) node).getUnderlyingNodeInfo().iterateAxis(AxisInfo.FOLLOWING)) {
            NodeInfo item;
            while ((item = iterator.next()) != null) {
                final NodeImpl node = TinyTreeWithId.wrap(tinyTreeWithId, NodeOverNodeInfo.wrap(item));
                if (test.matches(node)) {
                    result.add(node);
                }
            }
        }
    }

    public Node selectParentNode() {
        final NodeInfo parent = ((NodeOverNodeInfo) node).getUnderlyingNodeInfo().getParent();
        if (parent == null) {
            return null;
        } else {
            return TinyTreeWithId.wrap(tinyTreeWithId, NodeOverNodeInfo.wrap(parent));
        }
    }

    public boolean matchAttributes(final NodeTest test) {
        // do nothing
        return false;
    }

    public boolean matchDescendantAttributes(final NodeTest test) throws XPathException {
        // do nothing
        return false;
    }

    public boolean matchChildren(final NodeTest test) throws XPathException {
        return false;
    }

    public boolean matchDescendants(final boolean includeSelf, final NodeTest test) throws XPathException {
        return includeSelf && test.matches(this);
    }

    public void deepCopy() throws DOMException {
//        final DocumentImpl newDoc = document.expandRefs(this);
//        if(newDoc != document) {
//            // we received a new document
//            this.nodeNumber = 1;
//            this.document = newDoc;
//        }
        throw new UnsupportedOperationException("TODO(AR) - implement");
    }
}
