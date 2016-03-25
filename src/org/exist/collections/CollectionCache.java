/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2015 The eXist Project
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
 */
package org.exist.collections;

import java.util.Iterator;

import com.evolvedbinary.j8fu.tuple.Tuple2;
import net.jcip.annotations.GuardedBy;
import net.jcip.annotations.ThreadSafe;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.storage.BrokerPool;
import org.exist.storage.BrokerPoolService;
import org.exist.storage.CacheManager;
import org.exist.storage.cache.Cacheable;
import org.exist.storage.cache.LRUCache;
import org.exist.storage.lock.Lock;
import org.exist.util.hashtable.Object2LongHashMap;
import org.exist.util.hashtable.SequencedLongHashMap;
import org.exist.xmldb.XmldbURI;

/**
 * Global cache for {@link org.exist.collections.Collection} objects. The
 * cache is owned by {@link org.exist.storage.index.CollectionStore}. It is not
 * synchronized. Thus a lock should be obtained on the collection store before
 * accessing the cache.
 * 
 * @author wolf
 * @author Adam Retter <adam@evolvedbinary.com>
 */
@ThreadSafe
public class CollectionCache extends LRUCache implements BrokerPoolService {

    private final static Logger LOG = LogManager.getLogger(CollectionCache.class);

    @GuardedBy("cacheLock") private Object2LongHashMap names;
    private final BrokerPool pool;

    public CollectionCache(final CacheManager cacheManager, final BrokerPool pool, final int blockBuffers, final double growthThreshold) {
        super(cacheManager, blockBuffers, 2.0, growthThreshold, CacheManager.DATA_CACHE, "collection cache");
        this.names = new Object2LongHashMap(blockBuffers);
        this.pool = pool;
    }

    public void add(final Collection collection) {
        add(collection, 1);
    }

    public void add(final Collection collection, final int initialRefCount) {
        // don't cache the collection during initialization: SecurityManager is not yet online
        if(!pool.isOperational()) return;

        cacheLock.writeLock().lock();
        try {
            super.add(collection, initialRefCount);
            final String name = collection.getURI().getRawCollectionPath();
            names.put(name, collection.getKey());
        } finally {
            cacheLock.writeLock().unlock();
        }
    }

    public Collection get(final Collection collection) {
        return (Collection) get(collection.getKey());
    }

    public Collection get(final XmldbURI name) {
        cacheLock.readLock().lock();
        try {
            final long key = names.get(name.getRawCollectionPath());
            if (key < 0) {
                return null;
            }
            return (Collection) get(key);
        } finally {
            cacheLock.readLock().unlock();
        }
    }

    /**
     * Overridden to lock collections before they are removed.
     */
    @Override
    protected void removeOne(final Cacheable item) {
        final int maxAttempts = 3;

        boolean removed = false;
        for(int attempt = 1; attempt <= maxAttempts; attempt++) {
            removed = attemptRemoveOne(item);
            if(removed) {
                break;
            }
        }

        if(!removed) {
            LOG.warn("Unable to remove entry");
        }

        //TODO(AR) what to do about cacheManager thread-safety? see also LRUCache#removeOne, BTreeCache#removeNext
        cacheManager.requestMem(this);
    }

    private boolean attemptRemoveOne(final Cacheable item) {
        long removeKey = -1;

        cacheLock.writeLock().lock();
        try {
            final Iterator<Tuple2<Long, Cacheable>> iterator = map.entrySetIterator();
            while (iterator.hasNext()) {
                final Tuple2<Long, Cacheable> cached = iterator.next();
                if (cached._2.getKey() != item.getKey()) {
                    final Collection old = (Collection) cached._2;
                    final Lock lock = old.getLock();
                    if (lock.attempt(Lock.READ_LOCK)) {
                        try {
                            if (cached._2.allowUnload()) {
                                if (pool.getConfigurationManager() != null) { // might be null during db initialization
                                    pool.getConfigurationManager().invalidate(old.getURI(), null);
                                }
                                names.remove(old.getURI().getRawCollectionPath());
                                cached._2.sync(true);
                                removeKey = cached._2.getKey();
                                break;
                            }
                        } finally {
                            lock.release(Lock.READ_LOCK);
                        }
                    }
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

    @Override
    public void remove(final Cacheable item) {
        cacheLock.writeLock().lock();
        try {
            final Collection col = (Collection) item;
            super.remove(item);
            names.remove(col.getURI().getRawCollectionPath());

            // might be null during db initialization
            if (pool.getConfigurationManager() != null) {
                pool.getConfigurationManager().invalidate(col.getURI(), null);
            }
        } finally {
            cacheLock.writeLock().unlock();
        }
    }

    /**
     * Compute and return the in-memory size of all collections
     * currently contained in this cache.
     *
     * @see org.exist.storage.CollectionCacheManager
     * @return in-memory size in bytes.
     */
    public int getRealSize() {
        int size = 0;

        cacheLock.readLock().lock();
        try {
            for (final Iterator<Long> i = names.valueIterator(); i.hasNext(); ) {
                //TODO(AR) if we want this number to be accurate we would haev to read lock the collection too
                final Collection collection = (Collection) get(i.next());
                if (collection != null) {
                    size += collection.getMemorySize();
                }
            }
        } finally {
            cacheLock.readLock().unlock();
        }
        return size;
    }

    @Override
    public void resize(final int newSize) {
        cacheLock.writeLock().lock();
        try {
            if (newSize < max) {
                shrink(newSize);
            } else {
                LOG.debug("Growing collection cache to " + newSize);
                final SequencedLongHashMap<Cacheable> newMap = new SequencedLongHashMap<>(newSize * 2);
                final Object2LongHashMap newNames = new Object2LongHashMap(newSize);

                final Iterator<Tuple2<Long, Cacheable>> iterator = map.entrySetIterator();
                while (iterator.hasNext()) {
                    final Tuple2<Long, Cacheable> cacheable = iterator.next();
                    newMap.put(cacheable._2.getKey(), cacheable._2);
                    newNames.put(((Collection) cacheable._2).getURI().getRawCollectionPath(), cacheable._2.getKey());
                }

                max = newSize;
                map = newMap;
                names = newNames;
                accounting.reset();
                accounting.setTotalSize(max);
            }
        } finally {
            cacheLock.writeLock().unlock();
        }
    }

    @Override
    protected void shrink(final int newSize) {
        cacheLock.writeLock().lock();
        try {
            super.shrink(newSize);
            names = new Object2LongHashMap(newSize);
        } finally {
            cacheLock.writeLock().unlock();
        }
    }
}
