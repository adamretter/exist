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

import com.ibm.icu.text.Collator;
import io.lacuna.bifurcan.IEntry;
import io.lacuna.bifurcan.IMap;
import io.lacuna.bifurcan.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.function.BiPredicate;
import java.util.function.ToIntFunction;

/**
 * Full implementation of the XDM map() type based on an
 * immutable hash-map.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Rettter</a>
 */
public class MapType extends AbstractMapType {

    private static final ToIntFunction<AtomicValue> KEY_HASH_FN = AtomicValue::hashCode;

    protected final static Logger LOG = LogManager.getLogger(MapType.class);

    private IMap<AtomicValue, Sequence> map;
    private int type = Type.ANY_TYPE;

    private static IMap<AtomicValue, Sequence> newMap(@Nullable final Collator collator) {
        final BiPredicate<AtomicValue, AtomicValue> keyEqualsFn = (k1, k2) -> {
            if (collator != null) {
                try {
                    return ValueComparison.compareAtomic(collator, k1, k2, Constants.StringTruncationOperator.NONE, Constants.Comparison.EQ);
                } catch (final XPathException e) {
                    LOG.warn("Unable to compare with collation '" + collator + "', will fallback to non-collation comparision. Error: " + e.getMessage(), e);
                }
            }
            return k1.equals(k2);
        };

        return new Map(KEY_HASH_FN, keyEqualsFn);
    }

    public MapType(final XQueryContext context) {
        this(context,null);
    }

    public MapType(final XQueryContext context, @Nullable final Collator collator) {
        super(context);
        // if there's no collation, we'll use a hash map for better performance
        this.map = newMap(collator);
    }

    public MapType(final XQueryContext context, @Nullable final Collator collator, final AtomicValue key, final Sequence value) {
        super(context);
        this.map = newMap(collator).put(key, value);
        this.type = key.getType();
    }

    private MapType(final XQueryContext context, final IMap<AtomicValue, Sequence> other, final int type) {
        super(context);
        this.map = other;
        this.type = type;
    }

    public void add(final AbstractMapType other) {
        setKeyType(other.getKey() != null ? other.getKey().getType() : Type.ANY_TYPE);

        if(other instanceof MapType) {
            //TODO(AR) is the union in the correct direction i.e. keys from `other` overwrite `this`
            map = map.union(((MapType)other).map);
        } else {

            // TODO(AR) could the class member `map` remain `linear` ?

            // create a transient map
            final IMap<AtomicValue, Sequence> newMap = map.linear();

            for (final java.util.Map.Entry<AtomicValue, Sequence> entry : other) {
                newMap.put(entry.getKey(), entry.getValue());
            }

            // return to immutable map
            map = newMap.forked();
        }
    }

    public void add(final AtomicValue key, final Sequence value) {
        setKeyType(key.getType());
        map = map.put(key, value);
    }

    @Override
    public Sequence get(AtomicValue key) {
        key = convert(key);
        if (key == null) {
            return Sequence.EMPTY_SEQUENCE;
        }

        final Sequence result = map.get(key, null);
        return result == null ? Sequence.EMPTY_SEQUENCE : result;
    }

    @Override
    public AbstractMapType put(final AtomicValue key, final Sequence value) {
        final IMap<AtomicValue, Sequence> newMap = map.put(key, value);
        return new MapType(this.context, newMap, type == key.getType() ? type : Type.ITEM);
    }

    @Override
    public boolean contains(AtomicValue key) {
        key = convert(key);
        if (key == null) {
            return false;
        }

        return map.contains(key);
    }

    @Override
    public Sequence keys() {
        final ValueSequence seq = new ValueSequence();
        for (final AtomicValue key: map.keys()) {
            seq.add(key);
        }
        return seq;
    }

    public AbstractMapType remove(final AtomicValue[] keysAtomicValues) {

        // create a transient map
        IMap<AtomicValue, Sequence> newMap = map.linear();

        for (final AtomicValue key: keysAtomicValues) {
            newMap = newMap.remove(key);
        }

        // return an immutable map
        return new MapType(context, newMap.forked(), type);
    }

    @Override
    public int size() {
        return (int)map.size();
    }

    @Override
    public Iterator<java.util.Map.Entry<AtomicValue, Sequence>> iterator() {
        return map.toMap().entrySet().iterator();
    }

    @Override
    public AtomicValue getKey() {
        if (map.size() > 0) {
            final IEntry<AtomicValue, Sequence> entry = map.nth(0);
            if (entry != null) {
                return entry.key();
            }
        }

        return null;
    }

    @Override
    public Sequence getValue() {
        if (map.size() > 0) {
            final IEntry<AtomicValue, Sequence> entry = map.nth(0);
            if (entry != null) {
                return entry.value();
            }
        }

        return null;
    }

    private void setKeyType(final int newType) {
        if (type == Type.ANY_TYPE) {
            type = newType;
        }
        else if (type != newType) {
            type = Type.ITEM;
//            try {
//                final Map.Transient<AtomicValue, Sequence> newTransientMap = PersistentTrieMap.<AtomicValue, Sequence>of().asTransient();
//                newTransientMap.__putAllEquivalent(map, EqualityComparator.fromComparator((Comparator)getComparator(null)));   //NOTE: getComparator(null) returns a default distinct values comparator
//                map = newTransientMap.freeze();
//            } catch (final XPathException e) {
//                LOG.error(e);
//            }
        }
    }

    private AtomicValue convert(final AtomicValue key) {
        if (type != Type.ANY_TYPE && type != Type.ITEM) {
            try {
                return key.convertTo(type);
            } catch (final XPathException e) {
                return null;
            }
        }
        return key;
    }

    @Override
    public int getKeyType() {
        return type;
    }
}
