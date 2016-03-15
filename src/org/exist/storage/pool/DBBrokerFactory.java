/**
 * Copyright (C) 2016 Evolved Binary Ltd
 */
package org.exist.storage.pool;

import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.NativeBroker;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Adam Retter <adam.retter@googlemail.com>
 */
public class DBBrokerFactory extends BasePooledObjectFactory<DBBroker> {

    private final BrokerPool brokerPool;
    private final AtomicLong brokerId = new AtomicLong();

    public DBBrokerFactory(final BrokerPool brokerPool) {
        this.brokerPool = brokerPool;
    }

    @Override
    public DBBroker create() throws Exception {
        final DBBroker broker = new NativeBroker(brokerPool, brokerPool.getConfiguration());
        broker.setId("NativeBroker" + '_' + brokerPool.getId() + "_" + brokerId.incrementAndGet());
        return broker;
    }

    @Override
    public PooledObject<DBBroker> wrap(final DBBroker broker) {
        return new DefaultPooledObject<>(broker);
    }
}
