/**
 * Copyright (C) 2016 Evolved Binary Ltd
 */
package org.exist.storage.pool;

import org.apache.commons.pool2.*;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Adam Retter <adam.retter@googlemail.com>
 */
public class DBBrokerPoolTest {

    @Test
    public void renentrant() throws Exception {
        final BrokerPool mockBrokerPool = createMock(BrokerPool.class);

        replay(mockBrokerPool);

        final DBBrokerFactory factory = new MockedDBBrokerFactory(mockBrokerPool);
        final GenericObjectPoolConfig config = new GenericObjectPoolConfig();
        config.setMaxTotal(40);
        final ObjectPool<DBBroker> pool = new ReentrantGenericObjectPool<>(factory, config);

        final DBBroker broker1 = pool.borrowObject();
        assertEquals(1, pool.getNumActive());
        final DBBroker broker2 = pool.borrowObject();
        assertEquals(1, pool.getNumActive());

        final Thread thread2 = new Thread(() -> {
            try {
                pool.borrowObject();
            } catch(final Exception e) {
                fail(e.getMessage());
            }
        });
        thread2.start();
        thread2.join();

        assertEquals(2, pool.getNumActive());

        final Thread thread3 = new Thread(() -> {
            try {
                final DBBroker broker = pool.borrowObject();
                pool.returnObject(broker);
            } catch(final Exception e) {
                fail(e.getMessage());
            }
        });
        thread3.start();
        thread3.join();

        assertEquals(2, pool.getNumActive());

        pool.returnObject(broker1);
        assertEquals(2, pool.getNumActive());
        pool.returnObject(broker2);
        assertEquals(1, pool.getNumActive());

        verify(mockBrokerPool);
    }

    private static class MockedDBBrokerFactory extends DBBrokerFactory {
        public final List<DBBroker> mockBrokers = new ArrayList<>();

        public MockedDBBrokerFactory(final BrokerPool brokerPool) {
            super(brokerPool);
        }

        @Override
        public DBBroker create() throws Exception {
            final DBBroker broker = createMock(DBBroker.class);
            mockBrokers.add(broker);
            return broker;
        }
    }



}
