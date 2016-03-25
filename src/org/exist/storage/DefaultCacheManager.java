/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *
 *  $Id$
 */
package org.exist.storage;

import net.jcip.annotations.ThreadSafe;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.exist.management.Agent;
import org.exist.management.AgentFactory;
import org.exist.storage.cache.Cache;
import org.exist.util.Configuration;
import org.exist.util.DatabaseConfigurationException;

import java.text.NumberFormat;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * CacheManager maintains a global memory pool available to all page caches. All caches start with a low default setting, but CacheManager can grow
 * individual caches until the total memory is reached. Caches can also be shrinked if their "load" remains below a given threshold between check
 * intervals.The check interval is determined by the global sync background thread.
 *
 * <p>The class computes the available memory in terms of pages.</p>
 *
 * @author  wolf
 */
//TODO(AR) consider if the synchronization semantics around cache.resize and currentPageCount are strong enough?
@ThreadSafe
public class DefaultCacheManager implements CacheManager<Cache>, BrokerPoolService {

    private static final Logger LOG = LogManager.getLogger(DefaultCacheManager.class);

    /** The maximum fraction of the total memory that can be used by a single cache. */
    public static final double  MAX_MEM_USE = 0.9;

    /** The minimum size a cache needs to have to be considered for shrinking, defined in terms of a fraction of the overall memory. */
    public static final double  MIN_SHRINK_FACTOR = 0.5;

    /** The amount by which a large cache will be shrinked if other caches request a resize. */
    public static final double  SHRINK_FACTOR = 0.7;

    /**
     * The minimum number of pages that must be read from a cache between check intervals to be not considered for shrinking. This is a measure for
     * the "load" of the cache. Caches with high load will never be shrinked. A negative value means that shrinkage will not be performed.
     */
    public static final int DEFAULT_SHRINK_THRESHOLD = 10000;
    public static final String DEFAULT_SHRINK_THRESHOLD_STRING = "10000";

    public static final int DEFAULT_CACHE_SIZE = 64;
    public static final String CACHE_SIZE_ATTRIBUTE = "cacheSize";
    public static final String PROPERTY_CACHE_SIZE = "db-connection.cache-size";

    public static final String DEFAULT_CACHE_CHECK_MAX_SIZE_STRING = "true";
    public static final String CACHE_CHECK_MAX_SIZE_ATTRIBUTE = "checkMaxCacheSize";
    public static final String PROPERTY_CACHE_CHECK_MAX_SIZE = "db-connection.check-max-cache-size";

    public static final String SHRINK_THRESHOLD_ATTRIBUTE = "cacheShrinkThreshold";
    public static final String SHRINK_THRESHOLD_PROPERTY = "db-connection.cache-shrink-threshold";

    private final long totalMem;

    /** The total maximum amount of pages shared between all caches. */
    private final int totalPageCount;

    /** The maximum number of pages that can be allocated by a single cache. */
    private final int maxCacheSize;

    private final int pageSize;

    /**
     * The minimum number of pages that must be read from a cache between check intervals to be not considered for shrinking. This is a measure for
     * the "load" of the cache. Caches with high load will never be shrinked. A negative value means that shrinkage will not be performed.
     */

    private final int shrinkThreshold;

    private final String instanceName;

    /** Caches maintained by this class. */
    private final List<Cache> caches = new CopyOnWriteArrayList<>();

    /** The number of pages currently used by the active caches. */
    private final AtomicInteger currentPageCount = new AtomicInteger();

    /**
     * Signals that a resize had been requested by a cache, but the request could not be accepted during normal operations. The manager might try to
     * shrink the largest cache during the next sync event.
     */
    private final AtomicBoolean lastRequest = new AtomicBoolean();


    public DefaultCacheManager(final BrokerPool pool) {
        this.instanceName = pool.getId();

        final int configuredPageSize = pool.getConfiguration().getInteger(BrokerPool.PROPERTY_PAGE_SIZE);
        if(configuredPageSize < 0) {
            //TODO : should we share the page size with the native broker ?
            this.pageSize = BrokerPool.DEFAULT_PAGE_SIZE;
        } else {
            this.pageSize = configuredPageSize;
        }

        this.shrinkThreshold = pool.getConfiguration().getInteger(SHRINK_THRESHOLD_PROPERTY);

        final int cacheSize;
        final int configuredCacheSize = pool.getConfiguration().getInteger(PROPERTY_CACHE_SIZE);
        if(configuredCacheSize < 0) {
            cacheSize = DEFAULT_CACHE_SIZE;
        } else {
            cacheSize = configuredCacheSize;
        }

        long totalMemSafe = cacheSize * 1024L * 1024L;

        final Boolean checkMaxCache = (Boolean)pool.getConfiguration().getProperty(PROPERTY_CACHE_CHECK_MAX_SIZE);
        if(checkMaxCache == null || checkMaxCache.booleanValue()) {
			final long max = Runtime.getRuntime().maxMemory();
			final long maxCache   = (max >= (768 * 1024 * 1024)) ? (max / 2) : (max / 3);

			if(totalMemSafe > maxCache) {
                totalMemSafe = maxCache;

				LOG.warn("The cacheSize=\"" + cacheSize +
                        "\" setting in conf.xml is too large. Java has only " + (max / 1024) + "k available. Cache manager will not use more than " + (totalMemSafe / 1024L) + "k " +
                        "to avoid memory issues which may lead to database corruptions."
				);
			}
		} else {
			LOG.warn("Checking of Max Cache Size disabled by user, this could cause memory issues which may lead to database corruptions if you don't have enough memory allocated to your JVM!");
		}

        this.totalMem = totalMemSafe;

        this.totalPageCount = (int)(totalMem / pageSize);
        this.maxCacheSize = (int)(totalPageCount * MAX_MEM_USE);
        final NumberFormat nf = NumberFormat.getNumberInstance();

        LOG.info("Cache settings: " + nf.format(totalMem / 1024L) + "k; totalPages: " + nf.format(totalPageCount) +
                "; maxCacheSize: " + nf.format(maxCacheSize) +
                "; cacheShrinkThreshold: " + nf.format( shrinkThreshold)
        );

        registerMBean();
    }

    @Override
    public void registerCache(final Cache cache) {
        final int cacheBuffers = cache.getBuffers(); //TODO(AR) do we need additional sync here?
        if(caches.add(cache)) {
            currentPageCount.addAndGet(cacheBuffers);
            registerMBean(cache);
        }
    }


    @Override
    public void deregisterCache(final Cache cache) {
        final int cacheBuffers = cache.getBuffers(); //TODO(AR) do we need additional sync here?
        if(caches.remove(cache)) {
            currentPageCount.addAndGet(-cacheBuffers);
        }
    }

    //TODO(AR) should only be called by one thread-per-cache, maybe synchronized(cache)
    @Override
    public int requestMem(final Cache cache) {
        //de-reference just once to get a copy
        final int cacheBuffers = cache.getBuffers();
        final int currentPageCount = this.currentPageCount.get();

        if(currentPageCount >= totalPageCount) {
            if(cacheBuffers < maxCacheSize) {
                lastRequest.set(true);

//                lastRequestLock.writeLock().lock();
//                try {
//                    lastRequest = cache;
//                } finally {
//                    lastRequestLock.writeLock().unlock();
//                }
            }

            // no free pages available
//            LOG.debug("Cache " + cache.getFileName() + " cannot be resized");
            return -1;
        }

        if((cache.getGrowthFactor() > 1.0 ) && (cacheBuffers < maxCacheSize)) {
            if(currentPageCount >= totalPageCount) {
                // another cache has been resized. Give up
                return -1;
            }

            // calculate new cache size
            int newCacheSize = (int)(cacheBuffers * cache.getGrowthFactor());
            if(newCacheSize > maxCacheSize) {
                // new cache size is too large: adjust
                newCacheSize = maxCacheSize;
            }

            if((currentPageCount + newCacheSize) > totalPageCount) {
                // new cache size exceeds total: adjust
                newCacheSize = cacheBuffers + (totalPageCount - currentPageCount);
            }

            if(LOG.isDebugEnabled()) {
                final NumberFormat nf = NumberFormat.getNumberInstance();
                LOG.debug("Growing cache " + cache.getFileName() + " (a " + cache.getClass().getName() + ") from " + nf.format(cacheBuffers) + " to " + nf.format(newCacheSize));
            }

            this.currentPageCount.addAndGet(-cacheBuffers);
            // resize the cache
            cache.resize(newCacheSize);
            this.currentPageCount.addAndGet(newCacheSize);

//                LOG.debug("currentPageCount = " + currentPageCount + "; max = " + totalPageCount);
            return newCacheSize;
        }
        return -1;
    }


    /**
     * Called from the global major sync event to check if caches can be shrinked. To be shrinked, the size of a cache needs to be larger than the
     * factor defined by {@link #MIN_SHRINK_FACTOR} and its load needs to be lower than {@link #DEFAULT_SHRINK_THRESHOLD}.
     *
     * <p>If shrinked, the cache will be reset to the default initial cache size.</p>
     */
    @Override
    public void checkCaches() {
        final int minSize = (int)(totalPageCount * MIN_SHRINK_FACTOR);

        if(shrinkThreshold >= 0) {
            for(final Cache cache : caches) {
                final int cacheBuffers = cache.getBuffers();

                if(cache.getGrowthFactor() > 1.0) {
                    final int load = cache.getLoad();

                    if(cacheBuffers > minSize && load < shrinkThreshold) {
                        if(LOG.isDebugEnabled()) {
                            final NumberFormat nf = NumberFormat.getNumberInstance();
                            LOG.debug( "Shrinking cache: " + cache.getFileName() + " (a " + cache.getClass().getName() + ") to " + nf.format( cache.getBuffers() ) );
                        }

                        currentPageCount.addAndGet(-cache.getBuffers());
                        cache.resize(getDefaultInitialSize());
                        currentPageCount.addAndGet(getDefaultInitialSize());
                    }
                }
            }
        }
    }


    @Override
    public void checkDistribution() {
        if(lastRequest.compareAndSet(true, false)) {
            final int minSize = (int) (totalPageCount * MIN_SHRINK_FACTOR);

            for (final Cache cache : caches) {
                final int cacheBuffers = cache.getBuffers();
                if (cacheBuffers >= minSize) {
                    final int newSize = (int) (cacheBuffers * SHRINK_FACTOR);

                    if (LOG.isDebugEnabled()) {
                        final NumberFormat nf = NumberFormat.getNumberInstance();
                        LOG.debug("Shrinking cache: " + cache.getFileName() + " (a " + cache.getClass().getName() + ") to " + nf.format(newSize));
                    }

                    currentPageCount.addAndGet(-cacheBuffers);
                    cache.resize(newSize);
                    currentPageCount.addAndGet(newSize);
                    break;
                }
            }
        }
    }

    /**
     * @return Maximum size of all Caches in pages
     */
    @Override
    public long getMaxTotal() {
        return totalPageCount;
    }

    /**
     * @return Current size of all Caches in bytes
     */
    @Override
    public long getCurrentSize() {
        return currentPageCount.get() * pageSize;
    }

    /**
     * @return Maximum size of a single Cache in bytes
     */
    @Override
    public long getMaxSingle() {
        return maxCacheSize;
    }

    public long getTotalMem() {
        return totalMem;
    }

    /**
     * Returns the default initial size for all caches.
     *
     * @return  Default initial size 64.
     */
    public int getDefaultInitialSize() {
        return DEFAULT_CACHE_SIZE;
    }

    private void registerMBean() {
        final Agent agent = AgentFactory.getInstance();
        try {
            agent.addMBean(instanceName, "org.exist.management." + instanceName + ":type=CacheManager", new org.exist.management.CacheManager(this));
        } catch(final DatabaseConfigurationException e) {
            LOG.warn("Exception while registering cache mbean.", e);
        }
    }

    private void registerMBean(final Cache cache) {
        final Agent agent = AgentFactory.getInstance();

        try {
            agent.addMBean(instanceName, "org.exist.management." + instanceName + ":type=CacheManager.Cache,name=" + cache.getFileName() + ",cache-type=" + cache.getType(), new org.exist.management.Cache(cache));
        } catch(final DatabaseConfigurationException e) {
            LOG.warn("Exception while registering cache mbean.", e);
        }
    }
}
