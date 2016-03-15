package org.exist.storage.pool;

import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.exist.storage.DBBroker;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by aretter on 14/03/2016.
 */

public class Pool2 implements AutoCloseable {

    private final InternalPool1 pool;

    public Pool2() {
        final PoolFactory1 factory = new PoolFactory1();
        pool = new InternalPool1(factory);
        pool.setMaxTotal(40);
    }

    public final DBBroker borrowBroker() throws Exception {
        return pool.borrowObject().broker;
    }

    public final void returnBroker(final DBBroker broker) {
        pool.returnObject();
    }

    public final void close() {

    }


    public static class InternalPool1 extends GenericObjectPool<ReentrantDBBroker> {
        private final static ThreadLocal<ReentrantDBBroker> localBroker = new ThreadLocal<>();

        public InternalPool1(final PoolFactory1 factory) {
            super(factory);
        }

        @Override
        public ReentrantDBBroker borrowObject(final long borrowMaxWaitMillis) throws Exception {
            ReentrantDBBroker broker = localBroker.get();
            if (broker == null) {
                broker = super.borrowObject(borrowMaxWaitMillis);
                localBroker.set(broker);
            }
            broker.referenceCount.incrementAndGet();
            return broker;
        }

        @Override
        public void returnObject(final ReentrantDBBroker reentrantDBBroker) {
            ReentrantDBBroker broker = localBroker.get();
            if(broker == null || broker != reentrantDBBroker) {
                throw new IllegalStateException("release() has been called from the wrong thread for broker " + reentrantDBBroker.broker.getId());
            } else {
                if(broker.referenceCount.decrementAndGet() == 0) {
                    try {
                        super.returnObject(broker);
                    } finally {
                        localBroker.remove();
                    }
                }
            }
        }
    }

    private static class ReentrantDBBroker {
        final DBBroker broker;
        final AtomicLong referenceCount = new AtomicLong();

        public ReentrantDBBroker(final DBBroker broker) {
            this.broker = broker;
        }
    }

    private static class PoolFactory1 extends BasePooledObjectFactory<ReentrantDBBroker> {

        @Override
        public ReentrantDBBroker create() throws Exception {
            return null; //TODO(AR) TODO
        }

        @Override
        public PooledObject<ReentrantDBBroker> wrap(final ReentrantDBBroker reentrantDBBroker) {
            return new DefaultPooledObject<>(reentrantDBBroker);
        }
    }
}
