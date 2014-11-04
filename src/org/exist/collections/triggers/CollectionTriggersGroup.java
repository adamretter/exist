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
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.xmldb.XmldbURI;

import java.util.Collections;
import java.util.List;

/**
 * Represents a Group of Collection Triggers
 *
 * These Triggers may be used across multiple threads
 * each Trigger is responsible for maintaining it's own
 * state.
 *
 * @author Adam Retter <adam.retter@googlemail.com>
 */
public class CollectionTriggersGroup extends AbstractTriggerGroup<LazyCollectionTrigger> implements CollectionTrigger {

    public final static CollectionTriggersGroup EMPTY = new CollectionTriggersGroup(Collections.EMPTY_LIST);

    public CollectionTriggersGroup(final List<LazyCollectionTrigger> triggers) {
        super(triggers);
    }

    @Override
    public void beforeCreateCollection(final DBBroker broker, final Txn txn, final XmldbURI uri) throws TriggerException {
        for(final LazyCollectionTrigger trigger : getTriggers()) {
            trigger.get().beforeCreateCollection(broker, txn, uri);
        }
    }

    @Override
    public void afterCreateCollection(final DBBroker broker, final Txn txn, final Collection collection) {
        for(final LazyCollectionTrigger trigger : getTriggers()) {
            try {
                trigger.get().afterCreateCollection(broker, txn, collection);
            } catch (Exception e) {
                Trigger.LOG.error(e.getMessage(), e);
            }
        }
    }

    @Override
    public void beforeCopyCollection(final DBBroker broker, final Txn txn, final Collection collection, final XmldbURI newUri) throws TriggerException {
        for(final LazyCollectionTrigger trigger : getTriggers()) {
            trigger.get().beforeCopyCollection(broker, txn, collection, newUri);
        }
    }

    @Override
    public void afterCopyCollection(final DBBroker broker, final Txn txn, final Collection collection, final XmldbURI oldUri) {
        for(final LazyCollectionTrigger trigger : getTriggers()) {
            try {
                trigger.get().afterCopyCollection(broker, txn, collection, oldUri);
            } catch (Exception e) {
                Trigger.LOG.error(e.getMessage(), e);
            }
        }
    }

    @Override
    public void beforeMoveCollection(final DBBroker broker, final Txn txn, final Collection collection, final XmldbURI newUri) throws TriggerException {
        for(final LazyCollectionTrigger trigger : getTriggers()) {
            trigger.get().beforeMoveCollection(broker, txn, collection, newUri);
        }
    }

    @Override
    public void afterMoveCollection(final DBBroker broker, final Txn txn, final Collection collection, final XmldbURI oldUri) {
        for(final LazyCollectionTrigger trigger : getTriggers()) {
            try {
                trigger.get().afterMoveCollection(broker, txn, collection, oldUri);
            } catch (Exception e) {
                Trigger.LOG.error(e.getMessage(), e);
            }
        }
    }

    @Override
    public void beforeDeleteCollection(final DBBroker broker, final Txn txn, final Collection collection) throws TriggerException {
        for(final LazyCollectionTrigger trigger : getTriggers()) {
            trigger.get().beforeDeleteCollection(broker, txn, collection);
        }
    }

    @Override
    public void afterDeleteCollection(final DBBroker broker, final Txn txn, final XmldbURI uri) {
        for(final LazyCollectionTrigger trigger : getTriggers()) {
            try {
                trigger.get().afterDeleteCollection(broker, txn, uri);
            } catch (Exception e) {
                Trigger.LOG.error(e.getMessage(), e);
            }
        }
    }
}
