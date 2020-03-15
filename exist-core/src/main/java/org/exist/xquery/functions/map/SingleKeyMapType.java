/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery.functions.map;

import com.github.krukow.clj_lang.IPersistentMap;
import org.exist.xquery.Constants;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.Sequence;

import java.util.AbstractMap;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;

public class SingleKeyMapType extends AbstractMapType {

    private AtomicValue key;
    private Sequence value;
    private Comparator<AtomicValue> comparator;

    public SingleKeyMapType(XQueryContext context, String collation, AtomicValue key, Sequence value) throws XPathException {
        super(context);
        this.key = key;
        this.value = value;
        this.comparator = getComparator(collation);
    }

    @Override
    public int getKeyType() {
        return key.getType();
    }

    @Override
    public Sequence get(AtomicValue key) {
        if (comparator.compare(this.key, key) == Constants.EQUAL)
            {return this.value;}
        return null;
    }

    @Override
    public AbstractMapType put(final AtomicValue key, final Sequence value) throws XPathException {
        final MapType map = new MapType(context);
        map.add(this);
        return map.put(key, value);
    }

    @Override
    public boolean contains(AtomicValue key) {
        return (comparator.compare(this.key, key) == Constants.EQUAL);
    }

    @Override
    public Sequence keys() {
        return key == null ? Sequence.EMPTY_SEQUENCE : key;
    }

    @Override
    public int size() {
        return key == null ? 0 : 1;
    }

    @Override
    public AbstractMapType remove(final AtomicValue[] keysAtomicValues) throws XPathException {
        for (final AtomicValue key: keysAtomicValues) {
            if (key != this.key) {
                continue;
            }
            this.key = null;
            value = null;
            return new MapType(context);
        }
        final MapType map = new MapType(context);
        map.add(this);
        return map;
    }

    @Override
    public AtomicValue getKey() {
        return key;
    }

    @Override
    public Sequence getValue() {
        return value;
    }

    @Override
    public Iterator<Map.Entry<AtomicValue, Sequence>> iterator() {
        return new SingleKeyMapIterator();
    }

    private class SingleKeyMapIterator implements Iterator<Map.Entry<AtomicValue, Sequence>> {

        boolean hasMore = true;

        @Override
        public boolean hasNext() {
            return hasMore;
        }

        @Override
        public Map.Entry<AtomicValue, Sequence> next() {
            if (!hasMore) {
                return null;
            }
            hasMore = false;
            return new AbstractMap.SimpleEntry<>(key, value);
        }
    }
}