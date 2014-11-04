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

import org.exist.storage.DBBroker;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A group of related Triggers
 *
 * @author Adam Retter <adam.retter@googlemail.com>
 */
public abstract class AbstractTriggerGroup<L extends LazyTrigger> implements Trigger {

    private final List<L> triggers;
    private Map<String, List<? extends Object>> parameters = Collections.EMPTY_MAP;

    public AbstractTriggerGroup(final List<L> triggers) {
        this.triggers = triggers;
    }

    protected final List<L> getTriggers() {
        return triggers;
    }

    @Override
    public void configure(final DBBroker broker, final org.exist.collections.Collection col, final Map<String, List<? extends Object>> parameters) throws TriggerException {
        this.parameters = parameters;
    }
}
