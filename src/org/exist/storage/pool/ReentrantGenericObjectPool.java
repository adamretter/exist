/**
 * Copyright (C) 2016 Evolved Binary Ltd
 */
package org.exist.storage.pool;

import org.apache.commons.pool2.*;
import org.apache.commons.pool2.impl.*;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A Generic Object Pool where only one instance of an object
 * is ever returned to the same thread, no matter how many times
 * that distinct thread calls borrowObject
 *
 * We keep a reference count for each object in the pool,
 * the number of references should be equal to the number
 * of times that {@link ReentrantGenericObjectPool#borrowObject()}]
 * has been called by a distinct thread.
 *
 * Calling {@link ReentrantGenericObjectPool#returnObject(Object)}
 * will decrement the reference count. When the count reaches zero
 * it will be returned to the object pool for reuse by another thread.
 *
 * @author Adam Retter <adam.retter@googlemail.com>
 */
public class ReentrantGenericObjectPool<T> implements ObjectPool<T>, GenericObjectPoolMXBean, UsageTracking<T>, AutoCloseable {

    private final static ThreadLocal<ReentrantObject> localObject = new ThreadLocal<>();
    private final GenericObjectPool<T> pool;

    public ReentrantGenericObjectPool(final PooledObjectFactory<T> factory, final GenericObjectPoolConfig config) {
        this.pool = new GenericObjectPool<>(factory, config);
    }

    @Override
    public final T borrowObject() throws Exception {
        ReentrantObject reentrantObject = localObject.get();
        if (reentrantObject == null) {
            reentrantObject = new ReentrantObject(pool.borrowObject());
            localObject.set(reentrantObject);
        }
        reentrantObject.referenceCount.incrementAndGet();
        return (T)reentrantObject.obj;
    }

    @Override
    public final void returnObject(final T obj) {
        Objects.requireNonNull(obj);
        final ReentrantObject reentrantObj = localObject.get();
        if (reentrantObj == null || reentrantObj.obj != obj) {
            localObject.remove();
            throw new IllegalStateException("returnObject() has been called from the wrong thread for Object");
        } 
        if (reentrantObj.referenceCount.decrementAndGet() > 0) return;
        try {
            pool.returnObject(obj);
        } finally {
            localObject.remove();
        }
    }

    @Override
    public void invalidateObject(final T obj) throws Exception {
        try {
            pool.invalidateObject(obj);
        } finally {
            localObject.remove();
        }
    }

    @Override
    public void addObject() throws Exception, IllegalStateException, UnsupportedOperationException {
        pool.addObject();
    }

    @Override
    public int getNumIdle() {
        return pool.getNumIdle();
    }

    @Override
    public int getNumActive() {
        return pool.getNumActive();
    }

    @Override
    public void clear() throws Exception, UnsupportedOperationException {
        pool.clear();
    }

    @Override
    public final void close() {
        pool.close();
    }


    // <editor-fold desc="JMX methods" defaultstate="collapsed">
    @Override
    public boolean getBlockWhenExhausted() {
        return pool.getBlockWhenExhausted();
    }

    @Override
    public boolean getFairness() {
        return pool.getFairness();
    }

    @Override
    public boolean getLifo() {
        return pool.getLifo();
    }

    @Override
    public int getMaxIdle() {
        return pool.getMaxIdle();
    }

    @Override
    public int getMaxTotal() {
        return pool.getMaxTotal();
    }

    @Override
    public long getMaxWaitMillis() {
        return pool.getMaxWaitMillis();
    }

    @Override
    public long getMinEvictableIdleTimeMillis() {
        return pool.getMinEvictableIdleTimeMillis();
    }

    @Override
    public int getMinIdle() {
        return pool.getMinIdle();
    }

    @Override
    public int getNumTestsPerEvictionRun() {
        return pool.getNumTestsPerEvictionRun();
    }

    @Override
    public boolean getTestOnCreate() {
        return pool.getTestOnCreate();
    }

    @Override
    public boolean getTestOnBorrow() {
        return pool.getTestOnBorrow();
    }

    @Override
    public boolean getTestOnReturn() {
        return pool.getTestOnReturn();
    }

    @Override
    public boolean getTestWhileIdle() {
        return pool.getTestWhileIdle();
    }

    @Override
    public long getTimeBetweenEvictionRunsMillis() {
        return pool.getTimeBetweenEvictionRunsMillis();
    }

    @Override
    public boolean isClosed() {
        return pool.isClosed();
    }

    @Override
    public long getBorrowedCount() {
        return pool.getBorrowedCount();
    }

    @Override
    public long getReturnedCount() {
        return pool.getBorrowedCount();
    }

    @Override
    public long getCreatedCount() {
        return pool.getCreatedCount();
    }

    @Override
    public long getDestroyedCount() {
        return pool.getDestroyedCount();
    }

    @Override
    public long getDestroyedByEvictorCount() {
        return pool.getDestroyedByEvictorCount();
    }

    @Override
    public long getDestroyedByBorrowValidationCount() {
        return pool.getDestroyedByBorrowValidationCount();
    }

    @Override
    public long getMeanActiveTimeMillis() {
        return pool.getMeanActiveTimeMillis();
    }

    @Override
    public long getMeanIdleTimeMillis() {
        return pool.getMeanIdleTimeMillis();
    }

    @Override
    public long getMeanBorrowWaitTimeMillis() {
        return pool.getMeanBorrowWaitTimeMillis();
    }

    @Override
    public long getMaxBorrowWaitTimeMillis() {
        return pool.getMaxBorrowWaitTimeMillis();
    }

    @Override
    public String getCreationStackTrace() {
        return pool.getCreationStackTrace();
    }

    @Override
    public int getNumWaiters() {
        return pool.getNumWaiters();
    }

    @Override
    public boolean isAbandonedConfig() {
        return pool.isAbandonedConfig();
    }

    @Override
    public boolean getLogAbandoned() {
        return pool.getLogAbandoned();
    }

    @Override
    public boolean getRemoveAbandonedOnBorrow() {
        return pool.getRemoveAbandonedOnBorrow();
    }

    @Override
    public boolean getRemoveAbandonedOnMaintenance() {
        return pool.getRemoveAbandonedOnMaintenance();
    }

    @Override
    public int getRemoveAbandonedTimeout() {
        return pool.getRemoveAbandonedTimeout();
    }

    @Override
    public String getFactoryType() {
        return pool.getFactoryType();
    }

    @Override
    public Set<DefaultPooledObjectInfo> listAllObjects() {
        return pool.listAllObjects();
    }

    // </editor-fold>

    @Override
    public void use(final T obj) {
        pool.use(obj);
    }

    private static class ReentrantObject<T> {
        final T obj;
        final AtomicLong referenceCount = new AtomicLong();
        public ReentrantObject(final T obj) {
            this.obj = obj;
        }
    }
}
