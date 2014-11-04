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

import org.exist.collections.Collection;
import org.exist.collections.CollectionConfiguration;
import org.exist.storage.DBBroker;
import org.exist.xmldb.XmldbURI;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Factory for instantiating instances of Triggers
 *
 * @author Adam Retter <adam.retter@googlemail.com>
 */
public class TriggerFactory {

    /**
     * Creates a new instance of a trigger and configures it
     *
     * @param clazz The class of the Trigger to instantiate
     * @param broker A database broker for configuring the Trigger
     * @param collection The collection for which the Trigger is configured
     *
     * @return A configured instance of the Trigger given in {@see clazz}
     */
    public static <T extends Trigger> T newInstance(final Class<T> clazz, final DBBroker broker, final Collection collection) throws TriggerException {
        return (T)newInstance(clazz, Collections.EMPTY_MAP, broker, collection);
    }

    /**
     * Creates a new instance of a trigger and configures it
     *
     * @param clazz The class of the Trigger to instantiate
     * @param parameters Any configuration parameters for the Trigger
     * @param broker A database broker for configuring the Trigger
     * @param collection The collection for which the Trigger is configured
     *
     * @return A configured instance of the Trigger given in {@see clazz}
     */
    public static <T extends Trigger> T newInstance(final Class<T> clazz, final Map<String, List<? extends Object>> parameters, final DBBroker broker, final Collection collection) throws TriggerException {
        try {
            final T trigger = clazz.newInstance();
            trigger.configure(broker, collection, parameters);
            return trigger;
        } catch (final InstantiationException | IllegalAccessException ie) {
            throw new TriggerException("Unable to instantiate Trigger '"  + clazz.getName() + "': " + ie.getMessage(), ie);
        }
    }

    public static List<LazyDocumentTrigger> toLazyDocumentTriggers(final XmldbURI configuredCollection, final List<Class<? extends DocumentTrigger>> classes) {
        final List<LazyDocumentTrigger> lazyTriggers = new ArrayList<>(classes.size());
        for(final Class<? extends DocumentTrigger> clazz : classes) {
            lazyTriggers.add(new LazyDocumentTrigger(configuredCollection, clazz));
        }
        return lazyTriggers;
    }

    public static List<LazyCollectionTrigger> toLazyCollectionTriggers(final XmldbURI configuredCollection, final List<Class<? extends CollectionTrigger>> classes) {
        final List<LazyCollectionTrigger> lazyTriggers = new ArrayList<>(classes.size());
        for(final Class<? extends CollectionTrigger> clazz : classes) {
            lazyTriggers.add(new LazyCollectionTrigger(configuredCollection, clazz));
        }
        return lazyTriggers;
    }

    public static DocumentTriggersGroup getDocumentTriggers(final DBBroker broker, final Collection collection) {
        final CollectionConfiguration colConf = collection.getConfiguration(broker);
        if(colConf != null) {
            //all triggers
            return colConf.documentTriggers();
        } else {
            //fallback, just database triggers;

            //TODO (AR) this could be improved by maintaining a single TriggerGroup for GlobalDocumentTriggers, then we would not need to call 'new'
            return new DocumentTriggersGroup(TriggerFactory.toLazyDocumentTriggers(null, broker.getBrokerPool().getGlobalDocumentTriggers()));
        }
    }

    public static CollectionTriggersGroup getCollectionTriggers(final DBBroker broker, final Collection collection) {
        final CollectionConfiguration colConf = collection.getConfiguration(broker);
        if(colConf != null) {
            //all triggers
            return colConf.collectionTriggers();
        } else {
            //just database triggers
            //TODO (AR) this could be improved by maintaining a single TriggerGroup for GlobalDocumentTriggers, then we would not need to call 'new'
            return new CollectionTriggersGroup(TriggerFactory.toLazyCollectionTriggers(null, broker.getBrokerPool().getGlobalCollectionTriggers()));
        }
    }
}
