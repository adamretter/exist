
/*
 *  Collection.java - eXist Open Source Native XML Database
 *  Copyright (C) 2001 Wolfgang M. Meier
 *  meier@ifs.tu-darmstadt.de
 *  http://exist.sourceforge.net
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 * 
 * $Id:
 */
package org.exist.dom;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.TreeMap;
import org.apache.log4j.Category;
import org.exist.security.Permission;
import org.exist.security.User;
import org.exist.storage.*;
import org.exist.util.SyntaxException;
import org.exist.util.VariableByteInputStream;
import org.exist.util.VariableByteOutputStream;

public class Collection implements Comparable {

    protected static Category LOG =
        Category.getInstance(Collection.class.getName());
    private DBBroker broker;

    private short collectionId = -1;
    private TreeMap documents = new TreeMap();
    private String name;
    private Permission permissions = new Permission(0755);
    private ArrayList subcollections = new ArrayList();

    public Collection(DBBroker broker) {
        this.broker = broker;
    }

    public Collection(DBBroker broker, String name) {
        this(broker);
        this.name = name;
    }

    /**
     *  Adds a feature to the Collection attribute of the Collection object
     *
     *@param  name  The feature to be added to the Collection attribute
     */
    public void addCollection(String name) {
        if (!subcollections.contains(name))
            subcollections.add(name);

    }

    /**
     *  Adds a feature to the Document attribute of the Collection object
     *
     *@param  doc  The feature to be added to the Document attribute
     */
    public void addDocument(DocumentImpl doc) {
        addDocument(new User("admin", null, "dba"), doc);
    }

    /**
     *  Adds a feature to the Document attribute of the Collection object
     *
     *@param  user  The feature to be added to the Document attribute
     *@param  doc   The feature to be added to the Document attribute
     */
    public void addDocument(User user, DocumentImpl doc) {
        if (doc.getDocId() < 0)
            doc.setDocId(broker.getNextDocId(this));
        documents.put(doc.getFileName(), doc);
    }

    /**
     *  Description of the Method
     *
     *@return    Description of the Return Value
     */
    public Iterator collectionIterator() {
        return subcollections.iterator();
    }

    /**
     *  Description of the Method
     *
     *@param  obj  Description of the Parameter
     *@return      Description of the Return Value
     */
    public int compareTo(Object obj) {
        Collection other = (Collection) obj;
        if (collectionId == other.collectionId)
            return 0;
        else if (collectionId < other.collectionId)
            return -1;
        else
            return 1;
    }

    public boolean equals(Object obj) {
        if (!(obj instanceof Collection))
            return false;
        return ((Collection) obj).collectionId == collectionId;
    }

    /**
     *  Gets the childCollectionCount attribute of the Collection object
     *
     *@return    The childCollectionCount value
     */
    public int getChildCollectionCount() {
        return subcollections.size();
    }

    /**
     *  Gets the document attribute of the Collection object
     *
     *@param  name  Description of the Parameter
     *@return       The document value
     */
    public DocumentImpl getDocument(String name) {
        return (DocumentImpl) documents.get(name);
    }

    /**
     *  Gets the documentCount attribute of the Collection object
     *
     *@return    The documentCount value
     */
    public int getDocumentCount() {
        return documents.size();
    }

    /**
     *  Gets the id attribute of the Collection object
     *
     *@return    The id value
     */
    public short getId() {
        return collectionId;
    }

    /**
     *  Gets the name attribute of the Collection object
     *
     *@return    The name value
     */
    public String getName() {
        return name;
    }

    /**
     *  Gets the parent attribute of the Collection object
     *
     *@return    The parent value
     */
    public Collection getParent() {
        if (name.equals("/db"))
            return null;
        String parent =
            (name.lastIndexOf("/") < 1
                ? "/"
                : name.substring(0, name.lastIndexOf("/")));
        return broker.getCollection(parent);
    }

    /**
     *  Gets the permissions attribute of the Collection object
     *
     *@return    The permissions value
     */
    public Permission getPermissions() {
        return permissions;
    }

    /**
     *  Gets the symbols attribute of the Collection object
     *
     *@return    The symbols value
     */
    public SymbolTable getSymbols() {
        return null;
    }

    /**
     *  Description of the Method
     *
     *@param  name  Description of the Parameter
     *@return       Description of the Return Value
     */
    public boolean hasDocument(String name) {
        return documents.containsKey(name);
    }

    /**
     *  Description of the Method
     *
     *@param  name  Description of the Parameter
     *@return       Description of the Return Value
     */
    public boolean hasSubcollection(String name) {
        return subcollections.contains(name);
    }

    /**
     *  Description of the Method
     *
     *@return    Description of the Return Value
     */
    public Iterator iterator() {
        return documents.values().iterator();
    }

    /**
     *  Description of the Method
     *
     *@param  istream          Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void read(DataInput istream) throws IOException {
        collectionId = istream.readShort();
        name = istream.readUTF();
        int collLen = istream.readInt();
        for (int i = 0; i < collLen; i++)
            subcollections.add(istream.readUTF());
        permissions.read(istream);
        DocumentImpl doc;
        try {
            while (true) {
                doc = new DocumentImpl(broker, this);
                doc.read(istream);
                addDocument(doc);
            }
        } catch (EOFException e) {
        }
    }

    public void read(VariableByteInputStream istream) throws IOException {
        collectionId = istream.readShort();
        name = istream.readUTF();
        final int collLen = istream.readInt();
        for (int i = 0; i < collLen; i++) {
            final String sub = istream.readUTF();
            subcollections.add(sub);
        }
        permissions.read(istream);
        try {
            while (true) {
                final DocumentImpl doc = new DocumentImpl(broker, this);
                doc.read(istream);
                addDocument(doc);
            }
        } catch (EOFException e) {
        }
    }

    /**
     *  Description of the Method
     *
     *@param  name  Description of the Parameter
     */
    public void removeCollection(String name) {
        subcollections.remove(subcollections.indexOf(name));
    }

    /**
     *  Description of the Method
     *
     *@param  name  Description of the Parameter
     */
    public void removeDocument(String name) {
        DocumentImpl doc = (DocumentImpl) documents.remove(name);
        if (doc == null) {
            LOG.debug("could not remove document " + name);
            return;
        }
    }

    /**
     *  Description of the Method
     *
     *@param  oldName  Description of the Parameter
     *@param  newName  Description of the Parameter
     *@return          Description of the Return Value
     */
    public boolean renameDocument(String oldName, String newName) {
        DocumentImpl doc = getDocument(oldName);
        if (doc == null) {      
            LOG.debug("document " + oldName + " not found");
            return false;
        }
        doc.setFileName(newName);
        documents.remove(oldName);
        documents.put(newName, doc);
        return true;
    }

    /**
     *  Sets the id attribute of the Collection object
     *
     *@param  id  The new id value
     */
    public void setId(short id) {
        this.collectionId = id;
    }

    /**
     *  Sets the name attribute of the Collection object
     *
     *@param  name  The new name value
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     *  Sets the permissions attribute of the Collection object
     *
     *@param  mode  The new permissions value
     */
    public void setPermissions(int mode) {
        permissions.setPermissions(mode);
    }

    /**
     *  Sets the permissions attribute of the Collection object
     *
     *@param  mode                 The new permissions value
     *@exception  SyntaxException  Description of the Exception
     */
    public void setPermissions(String mode) throws SyntaxException {
        permissions.setPermissions(mode);
    }

    /**
     *  Description of the Method
     *
     *@param  ostream          Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void write(DataOutput ostream) throws IOException {
        ostream.writeShort(collectionId);
        ostream.writeUTF(name);
        ostream.writeInt(subcollections.size());
        for (Iterator i = collectionIterator(); i.hasNext();)
            ostream.writeUTF((String) i.next());

        //        symbols.write(ostream);
        permissions.write(ostream);
        ostream.writeInt(documents.size());
        DocumentImpl doc;
        for (Iterator i = iterator(); i.hasNext();) {
            doc = (DocumentImpl) i.next();
            doc.write(ostream);
        }
    }

    public void write(VariableByteOutputStream ostream) throws IOException {
        ostream.writeShort(collectionId);
        ostream.writeUTF(name);
        ostream.writeInt(subcollections.size());
        for (Iterator i = collectionIterator(); i.hasNext();)
            ostream.writeUTF((String) i.next());
        permissions.write(ostream);
        for (Iterator i = iterator(); i.hasNext();) {
            final DocumentImpl doc = (DocumentImpl) i.next();
            doc.write(ostream);
        }
    }
}
