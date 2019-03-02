/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2017 The eXist Project
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
package org.exist.storage.lock;

import com.evolvedbinary.j8fu.tuple.Tuple2;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.storage.NativeBroker;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.lock.Lock.LockType;
import org.exist.storage.txn.Txn;
import org.jctools.maps.NonBlockingHashMap;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.*;

import static com.evolvedbinary.j8fu.tuple.Tuple.Tuple;
import static org.exist.storage.lock.LockTable.Action.*;

/**
 * The Lock Table holds the details of
 * threads awaiting to acquire a Lock
 * and threads that have acquired a lock.
 *
 * It is arranged by the id of the lock
 * which is typically an indicator of the
 * lock subject.
 *
 * @author Adam Retter <adam@evolvedbinary.com>
 */
public class LockTable {

    public static final String PROP_DISABLE = "exist.locktable.disable";
    public static final String PROP_SANITY_CHECK = "exist.locktable.sanity.check";
    public static final String PROP_TRACE_STACK_DEPTH = "exist.locktable.trace.stack.depth";

    private static final Logger LOG = LogManager.getLogger(LockTable.class);
    private static final String THIS_CLASS_NAME = LockTable.class.getName();

    /**
     * Set to false to disable all events
     */
    private volatile boolean disableEvents = Boolean.getBoolean(PROP_DISABLE);

    /**
     * Set to true to enable sanity checking of lock leases
     */
    private volatile boolean sanityCheck = Boolean.getBoolean(PROP_SANITY_CHECK);

    /**
     * Whether we should try and trace the stack for the lock event, -1 means all stack,
     * 0 means no stack, n means n stack frames, 5 is a reasonable value
     */
    private volatile int traceStackDepth = Optional.ofNullable(Integer.getInteger(PROP_TRACE_STACK_DEPTH))
            .orElse(0);

    private final List<LockEventListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * List of threads attempting to acquire a lock
     *
     * Map<Id, Map<Lock Type, List<LockModeOwner>>>
     */
    private final ConcurrentMap<String, Map<LockType, List<LockModeOwner>>> attempting = new NonBlockingHashMap<>();

    /**
     * Reference count of acquired locks by id and type
     *
     * Map<Id, Map<Lock Type, Map<Lock Mode, Map<Owner, LockCountTraces>>>>
     */
    private final ConcurrentMap<String, Map<LockType, Map<LockMode, Map<String, LockCountTraces>>>> acquired = new NonBlockingHashMap<>();

    /**
     * Holds a count of READ and WRITE locks by id.
     * Only used for debugging, see {@link #sanityCheckLockLifecycles(Action, String, LockMode)}.
     */
    private final Map<String, Tuple2<Long, Long>> lockCounts = new HashMap<>();

    LockTable(final String brokerPoolId, final ThreadGroup threadGroup) {
        // add a log listener if trace level logging is enabled
        if(LOG.isTraceEnabled()) {
            registerListener(new LockEventLogListener(LOG, Level.TRACE));
        }
    }

    /**
     * Shuts down the lock table processor.
     *
     * After calling this, no further lock
     * events will be reported.
     */
    public void shutdown() {
    }

    /**
     * Set the depth at which we should trace lock events through the stack
     *
     * @param traceStackDepth -1 traces the whole stack, 0 means no stack traces, n means n stack frames
     */
    public void setTraceStackDepth(final int traceStackDepth) {
        this.traceStackDepth = traceStackDepth;
    }

    public void attempt(final long groupId, final String id, final LockType lockType, final LockMode mode) {
        event(Attempt, groupId, id, lockType, mode);
    }

    public void attemptFailed(final long groupId, final String id, final LockType lockType, final LockMode mode) {
        event(AttemptFailed, groupId, id, lockType, mode);
    }

    public void acquired(final long groupId, final String id, final LockType lockType, final LockMode mode) {
        event(Acquired, groupId, id, lockType, mode);
    }

    public void released(final long groupId, final String id, final LockType lockType, final LockMode mode) {
        event(Released, groupId, id, lockType, mode);
    }

    private void event(final Action action, final long groupId, final String id, final LockType lockType, final LockMode mode) {
        if(disableEvents) {
            return;
        }

        final long timestamp = System.nanoTime();
        final Thread currentThread = Thread.currentThread();
        final String threadName = currentThread.getName();
        @Nullable final StackTraceElement[] stackTrace = getStackTrace(currentThread);

        if(ignoreEvent(threadName, id)) {
            return;
        }

        /**
         * Very useful for debugging Lock life cycles
         */
        if(sanityCheck) {
            sanityCheckLockLifecycles(action, id, mode);
        }

        switch (action) {
            case Attempt:
                notifyListeners(action, groupId, id, lockType, mode, threadName, 1, timestamp, stackTrace);
                addToAttempting(id, lockType, mode, threadName);
                break;

            case AttemptFailed:
                removeFromAttempting(id, lockType, mode, threadName);
                notifyListeners(action, groupId, id, lockType, mode, threadName, 1, timestamp, stackTrace);
                break;

            case Acquired:
                removeFromAttempting(id, lockType, mode, threadName);
                incrementAcquired(action, groupId, id, lockType, mode, threadName, timestamp, stackTrace);
                break;

            case Released:
                decrementAcquired(action, groupId, id, lockType, mode, threadName, timestamp, stackTrace);
                break;
        }
    }

    /**
     * Simple filtering to ignore events that are not of interest
     *
     * @param threadName The name of the thread that triggered the event
     * @param id The id of the lock
     *
     * @return true if the event should be ignored
     */
    private boolean ignoreEvent(final String threadName, final String id) {
        return false;

        // useful for debugging specific log events
//        return threadName.startsWith("DefaultQuartzScheduler_")
//                || id.equals("dom.dbx")
//                || id.equals("collections.dbx")
//                || id.equals("collections.dbx")
//                || id.equals("structure.dbx")
//                || id.equals("values.dbx")
//                || id.equals("CollectionCache");
    }

    @Nullable
    private StackTraceElement[] getStackTrace(final Thread thread) {
        if(traceStackDepth == 0) {
            return null;
        } else {
            final StackTraceElement[] stackTrace = thread.getStackTrace();
            final int lastStackTraceElementIdx = stackTrace.length - 1;

            final int from = findFirstExternalFrame(stackTrace);
            final int to;
            if (traceStackDepth == -1) {
                to = lastStackTraceElementIdx;
            } else {
                final int calcTo = from + traceStackDepth;
                if (calcTo > lastStackTraceElementIdx) {
                    to = lastStackTraceElementIdx;
                } else {
                    to = calcTo;
                }
            }

            return Arrays.copyOfRange(stackTrace, from, to);
        }
    }

    private int findFirstExternalFrame(final StackTraceElement[] stackTrace) {
        // we start with i = 1 to avoid Thread#getStackTrace() frame
        for(int i = 1; i < stackTrace.length; i++) {
            if(!THIS_CLASS_NAME.equals(stackTrace[i].getClassName())) {
                return i;
            }
        }
        return 0;
    }

    public void registerListener(final LockEventListener lockEventListener) {
        listeners.add(lockEventListener);
        lockEventListener.registered();
    }

    public void deregisterListener(final LockEventListener lockEventListener) {
        listeners.remove(lockEventListener);
        lockEventListener.unregistered();
    }

    /**
     * Get's a copy of the current lock attempt information
     *
     * @return lock attempt information
     */
    public Map<String, Map<LockType, List<LockModeOwner>>> getAttempting() {
        return new HashMap<>(attempting);
    }

    /**
     * Get's a copy of the current acquired lock information
     *
     * @return acquired lock information
     */
    public Map<String, Map<LockType, Map<LockMode, Map<String, LockCountTraces>>>> getAcquired() {
        return new HashMap<>(acquired);
    }

    public static class LockModeOwner {
        final LockMode lockMode;
        final String ownerThread;

        public LockModeOwner(final LockMode lockMode, final String ownerThread) {
            this.lockMode = lockMode;
            this.ownerThread = ownerThread;
        }

        public LockMode getLockMode() {
            return lockMode;
        }

        public String getOwnerThread() {
            return ownerThread;
        }
    }

    public static class LockCountTraces {
        int count;
        @Nullable final List<StackTraceElement[]> traces;

        public LockCountTraces(final int count, @Nullable final List<StackTraceElement[]> traces) {
            this.count = count;
            this.traces = traces;
        }

        public int getCount() {
            return count;
        }

        @Nullable
        public List<StackTraceElement[]> getTraces() {
            return traces;
        }
    }

    private void notifyListeners(final Action action, final long groupId, final String id, final LockType lockType, final LockMode mode, final String threadName, final int count, final long timestamp, @Nullable final StackTraceElement[] stackTrace) {
        for(final LockEventListener listener : listeners) {
            try {
                listener.accept(action, groupId, id, lockType, mode, threadName, count, timestamp, stackTrace);
            } catch (final Exception e) {
                LOG.error("Listener '{}' error: ", listener.getClass().getName(), e);
            }
        }
    }

    private void addToAttempting(final String id, final LockType lockType, final LockMode mode, final String threadName) {
        attempting.compute(id, (_id, attempts) -> {
            if (attempts == null) {
                attempts = new NonBlockingHashMap<>();
            }

            attempts.compute(lockType, (_lockType, v) -> {
                if (v == null) {
                    v = new CopyOnWriteArrayList<>();
                }

                v.add(new LockModeOwner(mode, threadName));
                return v;
            });

            return attempts;
        });
    }

    private void removeFromAttempting(final String id, final LockType lockType, final LockMode mode, final String threadName) {
        attempting.compute(id, (_id, attempts) -> {
            if (attempts == null) {
                return null;
            } else {
                attempts.compute(lockType, (_lockType, v) -> {
                    if (v == null) {
                        return null;
                    }

                    v.removeIf(val -> val.getLockMode() == mode && val.getOwnerThread().equals(threadName));
                    if (v.isEmpty()) {
                        return null;
                    } else {
                        return v;
                    }
                });

                if (attempts.isEmpty()) {
                    return null;
                } else {
                    return attempts;
                }
            }
        });
    }

    private void incrementAcquired(final Action action, final long groupId, final String id, final LockType lockType, final LockMode mode, final String threadName, final long timestamp, @Nullable final StackTraceElement[] stackTrace) {
        acquired.compute(id, (_id, acqu) -> {
            if (acqu == null) {
                acqu = new NonBlockingHashMap<>();
            }

            acqu.compute(lockType, (_lockType, v) -> {
                if (v == null) {
                    v = new NonBlockingHashMap<>();
                }

                v.compute(mode, (_mode, ownerHolds) -> {
                    if (ownerHolds == null) {
                        ownerHolds = new NonBlockingHashMap<>();
                    }

                    ownerHolds.compute(threadName, (_threadName, holdCount) -> {
                        if(holdCount == null) {
                            holdCount = new LockCountTraces(1, List(stackTrace));
                        } else {
                            holdCount = append(holdCount, stackTrace);
                        }
                        return holdCount;
                    });

                    int lockModeHolds = 0;
                    for (final LockCountTraces lockCountTraces : ownerHolds.values()) {
                        lockModeHolds += lockCountTraces.getCount();
                    }

                    notifyListeners(action, groupId, id, lockType, mode, threadName, lockModeHolds, timestamp, stackTrace);

                    return ownerHolds;
                });

                return v;
            });

            return acqu;
        });
    }

    private static @Nullable <T> List<T> List(@Nullable final T item) {
        if (item == null) {
            return null;
        }

        final List<T> list = new ArrayList<>();
        list.add(item);
        return list;
    }

    private static LockCountTraces append(final LockCountTraces holdCount, @Nullable final StackTraceElement[] trace) {
        List<StackTraceElement[]> traces = holdCount.traces;
        if (traces != null) {
            traces.add(trace);
        }
        holdCount.count++;
        return holdCount;
    }

    private static LockCountTraces removeLast(final LockCountTraces holdCount) {
        List<StackTraceElement[]> traces = holdCount.traces;
        if (traces != null) {
            traces.remove(traces.size() - 1);
        }
        holdCount.count--;
        return holdCount;
    }

    private void decrementAcquired(final Action action, final long groupId, final String id, final LockType lockType, final LockMode mode, final String threadName, final long timestamp, @Nullable final StackTraceElement[] stackTrace) {
        acquired.compute(id, (_id, acqu) -> {
            if (acqu == null) {
                LOG.error("No entry found when trying to decrementAcquired for: id={}" + id);
                return null;
            }

            acqu.compute(lockType, (_lockType, v) -> {
                if (v == null) {
                    LOG.error("No entry found when trying to decrementAcquired for: id={}, lockType={}", id, lockType);
                    return null;
                }

                v.compute(mode, (_mode, ownerHolds) -> {
                    if (ownerHolds == null) {
                        LOG.error("No entry found when trying to decrementAcquired for: id={}, lockType={}, lockMode={}", id, lockType, mode);
                        return null;
                    } else {
                        ownerHolds.compute(threadName, (_threadName, holdCount) -> {
                            if(holdCount == null) {
                                LOG.error("No entry found when trying to decrementAcquired for: id={}, lockType={}, lockMode={}, threadName={}", id, lockType, mode, threadName);
                                return null;
                            } else if(holdCount.count == 0) {
                                LOG.error("Negative release when trying to decrementAcquired for: id={}, lockType={}, lockMode={}, threadName={}", id, lockType, mode, threadName);
                                return null;
                            } else if(holdCount.count == 1) {
                                return null;
                            } else {
                                return removeLast(holdCount);
                            }
                        });

                        int lockModeHolds = 0;
                        for (final LockCountTraces lockCountTraces : ownerHolds.values()) {
                            lockModeHolds += lockCountTraces.getCount();
                        }

                        notifyListeners(action, groupId, id, lockType, mode, threadName, lockModeHolds, timestamp, stackTrace);

                        if (ownerHolds.isEmpty()) {
                            return null;
                        } else {
                            return ownerHolds;
                        }
                    }
                });

                if (v.isEmpty()) {
                    return null;
                } else {
                    return v;
                }
            });

            if (acqu.isEmpty()) {
                return null;
            } else {
                return acqu;
            }
        });
    }

    public interface LockEventListener {
        default void registered() {}
        void accept(final Action action, final long groupId, final String id, final LockType lockType, final LockMode mode, final String threadName, final int count, final long timestamp, @Nullable final StackTraceElement[] stackTrace);
        default void unregistered() {}
    }

    public enum Action {
        Attempt,
        AttemptFailed,
        Acquired,
        Released
    }

    /** debugging tools below **/

    /**
     * Checks that there are not more releases that there are acquires
     */
    private void sanityCheckLockLifecycles(final Action action, final String id, final LockMode mode) {
        synchronized(lockCounts) {
            long read = 0;
            long write = 0;

            final Tuple2<Long, Long> lockCount = lockCounts.get(id);
            if(lockCount != null) {
                read = lockCount._1;
                write = lockCount._2;
            }

            if(action == Action.Acquired) {
                if(mode == LockMode.READ_LOCK) {
                    read++;
                } else if(mode == LockMode.WRITE_LOCK) {
                    write++;
                }
            } else if(action == Action.Released) {
                if(mode == LockMode.READ_LOCK) {
                    if(read == 0) {
                        LOG.error("Negative READ_LOCKs", new IllegalStateException());
                    }
                    read--;
                } else if(mode == LockMode.WRITE_LOCK) {
                    if(write == 0) {
                        LOG.error("Negative WRITE_LOCKs", new IllegalStateException());
                    }
                    write--;
                }
            }

            if(LOG.isTraceEnabled()) {
                LOG.trace("QUEUE: {} (read={} write={})", action.toString(), read, write);
            }

            lockCounts.put(id, Tuple(read, write));
        }
    }

    public static String asString(final Action action, final long groupId, final String id, final LockType lockType,
            final LockMode mode, final String threadName, final int count, final long timestamp,
            @Nullable final StackTraceElement[] stackTrace) {
        final StringBuilder builder = new StringBuilder()
                .append(action.toString())
                .append(' ')
                .append(lockType.name());

        if(groupId > -1) {
            builder
                    .append("#")
                    .append(groupId);
        }

        builder.append('(')
                .append(mode.toString())
                .append(") of ")
                .append(id);

        if(stackTrace != null) {
            final String reason = getSimpleStackReason(stackTrace);
            if(reason != null) {
                builder
                        .append(" for #")
                        .append(reason);
            }
        }

        builder
                .append(" by ")
                .append(threadName)
                .append(" at ")
                .append(timestamp);

        if (action == Acquired || action == Released) {
            builder
                    .append(". count=")
                    .append(count);
        }

        return builder.toString();
    }

    private static final String NATIVE_BROKER_CLASS_NAME = NativeBroker.class.getName();
    private static final String COLLECTION_STORE_CLASS_NAME = NativeBroker.class.getName();
    private static final String TXN_CLASS_NAME = Txn.class.getName();

    @Nullable
    public static String getSimpleStackReason(final StackTraceElement[] stackTrace) {
        for (final StackTraceElement stackTraceElement : stackTrace) {
            final String className = stackTraceElement.getClassName();

            if (className.equals(NATIVE_BROKER_CLASS_NAME) || className.equals(COLLECTION_STORE_CLASS_NAME) || className.equals(TXN_CLASS_NAME)) {
                if (!(stackTraceElement.getMethodName().endsWith("LockCollection") || stackTraceElement.getMethodName().equals("lockCollectionCache"))) {
                    return stackTraceElement.getMethodName() + '(' + stackTraceElement.getLineNumber() + ')';
                }
            }
        }

        return null;
    }
}
