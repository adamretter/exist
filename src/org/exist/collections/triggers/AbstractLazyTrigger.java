/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2014 The eXist Project
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
package org.exist.collections.triggers;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.xmldb.XmldbURI;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Represents a lazily instantiated Trigger
 *
 * @author Adam Retter <adam.retter@googlemail.com>
 */
public abstract class AbstractLazyTrigger<T extends Trigger> implements LazyTrigger<T> {

    private static final Logger LOG = Logger.getLogger(AbstractLazyTrigger.class);

    private final Class<? extends T> clazz;
    private final Map<String, List<? extends Object>> parameters;

    private final AtomicReference<T> trigger = new AtomicReference<>();
    private final XmldbURI configuredCollection;

    public AbstractLazyTrigger(final XmldbURI configuredCollection, final Class<? extends T> clazz) {
        this(configuredCollection, clazz, Collections.EMPTY_MAP);
    }
    
    public AbstractLazyTrigger(final XmldbURI configuredCollection, final Class<? extends T> clazz, final Map<String, List<? extends Object>> parameters) {
        this.configuredCollection = configuredCollection;
        this.clazz = clazz;
        this.parameters = parameters;
    }

    protected final Class<? extends T> getClazz() {
        return clazz;
    }

    protected final Map<String, List<? extends Object>> getParameters() {
        return parameters;
    }

    protected final T newInstance() throws EXistException, PermissionDeniedException, IllegalAccessException, InstantiationException, TriggerException {
        Collection collection = null;
        try(final DBBroker broker = BrokerPool.getInstance().getBroker()) {
            if(configuredCollection != null) {
                collection = broker.openCollection(configuredCollection, Lock.READ_LOCK);
            }
            return TriggerFactory.newInstance(getClazz(), getParameters(), broker, collection);
        } finally {
            if(collection != null) {
                collection.getLock().release(Lock.READ_LOCK);
            }
        }
    }

    @Override
    public final T get() throws TriggerException {

        //TODO switch from StampedReference to AtomicStampedReference
        //and use the stamp to manage not re-calling newInstance if it throws
        //an exception the first time.

        final T current = trigger.get();
        if(current == null) {
            try {
                final T updated = newInstance();
                if(trigger.compareAndSet(current, updated) && LOG.isDebugEnabled()) {
                    LOG.debug("Instantiated Trigger '" + getClazz().getName() + "' instance for configured Collection: " + configuredCollection);
                }
            } catch(final EXistException | PermissionDeniedException | IllegalAccessException | InstantiationException ie) {
                throw new TriggerException("Unable to instantiate Trigger '" + getClazz().getName() + "' for configured Collection '" + configuredCollection + "': " + ie.getMessage(), ie);
            }
            return trigger.get();
        } else {
            return current;
        }
    }
}
