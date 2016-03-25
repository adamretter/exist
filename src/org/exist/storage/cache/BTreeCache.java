/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
 *  http://exist-db.org
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * \$Id\$
 */

package org.exist.storage.cache;

import com.evolvedbinary.j8fu.tuple.Tuple2;
import net.jcip.annotations.ThreadSafe;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.storage.CacheManager;

import java.util.Iterator;
import java.util.concurrent.locks.ReadWriteLock;

/**
 * This cache implementation always tries to keep the inner btree pages in
 * cache, while the leaf pages can be removed.
 *
 * @author wolf
 * @author Adam Retter <adam@evolvedbinary.com>
 */
@ThreadSafe
public class BTreeCache extends LRUCache<BTreeCacheable> {

    private final static Logger LOG = LogManager.getLogger(BTreeCache.class);

    public BTreeCache(final CacheManager cacheManager, final int size, final double growthFactor, final double growthThreshold, final String type, final String fileName) {
        super(cacheManager, size, growthFactor, growthThreshold, type, fileName);
    }

    @Override
    public void add(final BTreeCacheable item, final int initialRefCount) {
        add(item);
    }

    @Override
    public void add(final BTreeCacheable item) {
        cacheLock.writeLock().lock();
        try {
            map.put(item.getKey(), item);
            if (map.size() >= max + 1) {
                removeNext(item);
            }
        } finally {
            cacheLock.writeLock().unlock();
        }
    }

    protected void removeNext(final BTreeCacheable item) {
        if(!attemptRemoveNext(item, false)) {
            if(!attemptRemoveNext(item, true)) {
                LOG.warn("Unable to remove item");
            }
        }

        //TODO(AR) what to do about cacheManager thread-safety? see also CollectionCache#removeOne, LRUCache#removeOne
        accounting.replacedPage(item);
        if (growthFactor > 1.0 && accounting.resizeNeeded()) {
            cacheManager.requestMem(this);
        }
    }

    protected boolean attemptRemoveNext(final BTreeCacheable item, final boolean mustRemoveInner) {
        long removeKey = -1;

        cacheLock.writeLock().lock();
        try {
            final Iterator<Tuple2<Long, BTreeCacheable>> iterator = map.entrySetIterator();
            while (iterator.hasNext()) {
                final Tuple2<Long, BTreeCacheable> cached = iterator.next();

                if (cached._2.allowUnload() && cached._2.getKey() != item.getKey() &&
                        (mustRemoveInner || !cached._2.isInnerPage())) {
                    cached._2.sync(true);
                    removeKey = cached._1;
                    break;
                }
            }

            if (removeKey > -1) {
                map.remove(removeKey);
                return true;
            } else {
                return false;
            }
        } finally {
            cacheLock.writeLock().unlock();
        }
    }
}
