/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
 *  wolfgang@exist-db.org
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
 *  $Id$
 */
package org.exist.storage.cache;

import com.evolvedbinary.j8fu.tuple.Tuple2;
import net.jcip.annotations.GuardedBy;
import net.jcip.annotations.ThreadSafe;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.storage.CacheManager;
import org.exist.util.hashtable.SequencedLongHashMap;

import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A simple cache implementing a Last Recently Used policy. This
 * cache implementation is based on a 
 * {@link org.exist.util.hashtable.SequencedLongHashMap}. Contrary
 * to the other {@link org.exist.storage.cache.Cache} implementations,
 * LRUCache ignores reference counts or timestamps.
 * 
 * @author wolf
 * @author Adam Retter <adam@evolvedbinary.com>
 */
@ThreadSafe
public class LRUCache<T extends Cacheable> implements Cache<T> {

	private final static Logger LOG = LogManager.getLogger(LRUCache.class);

	@GuardedBy("cacheLock") protected int max;
	@GuardedBy("cacheLock") protected SequencedLongHashMap<T> map;

	protected final ReadWriteLock cacheLock = new ReentrantReadWriteLock();		//TODO(AR) could switch to cheaper TimestampLock by avoiding re-entrancy, would need to adjust CollectionCache too

	//TODO(AR) looks like accounting isn't used outside of LRUCache or sub-class so we could just use cacheLock to protect it
    protected final Accounting accounting;
    protected final double growthFactor;
    protected final CacheManager cacheManager;
	private final AtomicInteger hitsOld = new AtomicInteger();
    private final String type;
	private final String fileName;

    public LRUCache(final CacheManager cacheManager, final int size, final double growthFactor, double growthThreshold, final String type, final String fileName) {
		this.cacheManager = cacheManager;
		this.max = size;
        this.growthFactor = growthFactor;
		this.map = new SequencedLongHashMap<>(size * 2);
        this.accounting = new Accounting(growthThreshold);
        accounting.setTotalSize(max);
        this.type = type;
		this.fileName = fileName;
    }

	@Override
	public void add(final T item, final int initialRefCount) {
		add(item);
	}

    @Override
	public void add(final T item) {
		cacheLock.writeLock().lock();
		try {
			if (map.size() == max) {
				removeOne(item);
			}
			map.put(item.getKey(), item);
		} finally {
			cacheLock.writeLock().unlock();
		}
	}

	@Override
	public String getType() {
		return type;
	}

	@Override
	public T get(final T item) {
		return get(item.getKey());
	}

	@Override
	public T get(final long key) {
		final T obj;

		cacheLock.readLock().lock();
		try {
			obj = map.get(key);
		} finally {
			cacheLock.readLock().unlock();
		}

		if(obj == null) {
			accounting.missesIncrement();
		} else {
			accounting.hitIncrement();
		}
		return obj;
	}

	@Override
	public void remove(final T item) {
		cacheLock.writeLock().lock();
		try {
			map.remove(item.getKey());
		} finally {
			cacheLock.writeLock().unlock();
		}
	}

	@Override
	public boolean flush() {
		boolean flushed = false;

		//lock to ensure the iterator is stable
		cacheLock.readLock().lock();
		try {
			final Iterator<T> iterator = map.valueIterator();
			while(iterator.hasNext()) {
				final T cacheable =  iterator.next();
				if(cacheable.isDirty()) {
					flushed = flushed | cacheable.sync(false);
				}
			}
		} finally {
			cacheLock.readLock().unlock();
		}

		return flushed;
	}

    @Override
    public boolean hasDirtyItems() {
		//lock to ensure the iterator is stable
		cacheLock.readLock().lock();
		try {
			final Iterator<T> iterator = map.valueIterator();
			while(iterator.hasNext()) {
				final Cacheable cacheable = iterator.next();
				if(cacheable.isDirty()) {
					return true;
				}
			}
		} finally {
			cacheLock.readLock().unlock();
		}

        return false;
    }

	@Override
	public int getBuffers() {
		cacheLock.readLock().lock();
		try {
			return max;
		} finally {
			cacheLock.readLock().unlock();
		}
	}

	@Override
	public int getUsedBuffers() {
		cacheLock.readLock().lock();
		try {
			return map.size();
		} finally {
			cacheLock.readLock().unlock();
		}
	}

	@Override
	public int getHits() {
		return accounting.getHits();
	}

	@Override
	public int getFails() {
		return accounting.getMisses();
	}

	//TODO(AR) is accounting.getThrashing thread-safe?
    public int getThrashing() {
        return accounting.getThrashing();
    }

	@Override
    public String getFileName() {
        return fileName;
    }
    
	protected void removeOne(final Cacheable item) {
		long removeKey = -1;

		cacheLock.writeLock().lock();
		try {
			final Iterator<Tuple2<Long, T>> iterator = map.entrySetIterator();
			while (iterator.hasNext()) {
				final Tuple2<Long, T> cached = iterator.next();
				if (cached._2.allowUnload() && cached._2.getKey() != item.getKey()) {
					cached._2.sync(true);
					removeKey = cached._1;
					break;
				}
			}

			if (removeKey > -1) {
				map.remove(removeKey);
			} else {
				LOG.warn("Unable to remove entry");
			}
		} finally {
			cacheLock.writeLock().unlock();
		}

		//TODO(AR) what to do about cacheManager thread-safety? see also CollectionCache#removeOne, BTreeCache#removeNext
        accounting.replacedPage(item);
        if (growthFactor > 1.0 && accounting.resizeNeeded()) {
            cacheManager.requestMem(this);
        }
	}

    @Override
    public double getGrowthFactor() {
        return growthFactor;
    }
    
    @Override
    public void resize(final int newSize) {
		cacheLock.writeLock().lock();
		try {
			if (newSize < max) {
				shrink(newSize);
			} else {
				final SequencedLongHashMap<T> newMap = new SequencedLongHashMap<>(newSize * 2);

				final Iterator<Tuple2<Long, T>> iterator = map.entrySetIterator();
				while (iterator.hasNext()) {
					final Tuple2<Long, T> cacheable = iterator.next();
					newMap.put(cacheable._2.getKey(), cacheable._2);
				}
				max = newSize;
				map = newMap;
				accounting.reset();
				accounting.setTotalSize(max);
			}
		} finally {
			cacheLock.writeLock().unlock();
		}
    }
    
    protected void shrink(final int newSize) {
		cacheLock.writeLock().lock();
		try {
			flush();
			this.map = new SequencedLongHashMap<>(newSize);
			this.max = newSize;
			accounting.reset();
			accounting.setTotalSize(max);
		} finally {
			cacheLock.writeLock().unlock();
		}
    }

	/**
	 * Get the current load on the cache
	 *
	 * This method only provides a weakly-consistent load
	 * calculation
	 */
	@Override
    public int getLoad() {
		if (hitsOld.compareAndSet(0, accounting.getHits())) {
			return Integer.MAX_VALUE;
		}

		final int load = accounting.getHits() - hitsOld.get();
		hitsOld.set(accounting.getHits());
		return load;
    }
}
