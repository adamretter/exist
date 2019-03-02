/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2017 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.storage.lock;

import net.jcip.annotations.NotThreadSafe;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;

/**
 * A lock event listener which sends events to Log4j
 *
 * @author Adam Retter <adam@evolvedbinary.com>
 */
@NotThreadSafe
public class LockEventLogListener implements LockTable.LockEventListener {
    private final Logger log;
    private final Level level;

    /**
     * @param log The Log4j log
     * @param level The level at which to to log the lock events to Log4j
     */
    public LockEventLogListener(final Logger log, final Level level) {
        this.log = log;
        this.level = level;
    }

    @Override
    public void accept(final LockTable.Action action, final long groupId, final String id, final Lock.LockType lockType,
            final Lock.LockMode mode, final String threadName, final int count, final long timestamp,
            @Nullable final StackTraceElement[] stackTrace) {
        if(log.isEnabled(level)) {
            log.log(level, LockTable.asString(action, groupId, id, lockType, mode, threadName, count, timestamp,
                    stackTrace));
        }
    }
}
