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

import com.evolvedbinary.j8fu.Either;
import com.evolvedbinary.j8fu.tuple.Tuple2;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.storage.NativeBroker;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.txn.Txn;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.*;

import static org.exist.storage.lock.LockTable.LockAction.Action.*;

/**
 * The Lock Table holds the details of
 * threads awaiting to acquire a Lock
 * and threads that have acquired a lock
 *
 * It is arranged by the id of the lock
 * which is typically an indicator of the
 * lock subject
 *
 * @author Adam Retter <adam@evolvedbinary.com>
 */
public class LockTable {

    private final static Logger LOG = LogManager.getLogger(LockTable.class);
    private final static LockTable instance = new LockTable();

    //TODO(AR) make configurable
    private volatile boolean enableLogEvents = true;    // set to false to disable all events
    //TODO(AR) make configurable from conf.xml
    private volatile int traceStackDepth = 5;   // whether we should try and determine a reason for the lock, -1 means all stack, 0 means no stack, n means n stack frames

    //TODO(AR) {@link #attempting) and {@link #acquired} are at class member level so that they can later be exposed via XQuery methods etc for reporting

    /**
     * List of threads attempting to acquire a lock
     *
     * Map<Id, Map<Lock Type, List<Map<Lock Mode, Thread Name>>>>
     */
    private final ConcurrentMap<String, Map<LockType, List<Tuple2<LockMode, String>>>> attempting = new ConcurrentHashMap<>();

    /**
     * Reference count of acquired locks by id and type
     *
     * Map<Id, Map<Lock Type, List<Map<Lock Mode, Reference Count>>>>
     */
    private final ConcurrentMap<String, Map<LockType, Map<LockMode, Integer>>> acquired = new ConcurrentHashMap<>();

    /**
     * The {@link #queue} holds lock events and lock listener events
     * and is processed by the single thread {@link #queueConsumer} which uses
     * {@link QueueConsumer} to ensure serializability of locking events and monitoring
     */
    private final TransferQueue<Either<ListenerAction, LockAction>> queue = new LinkedTransferQueue<>();
    private final Future<?> queueConsumer;

    private LockTable() {
        final ExecutorService executorService = Executors.newSingleThreadExecutor();
        this.queueConsumer = executorService.submit(new QueueConsumer(queue, attempting, acquired));

        // add a log listener if trace level logging is enabled
        if(LOG.isTraceEnabled()) {
            registerListener(new LockEventLogListener(LOG, Level.TRACE));
        }
    }

    public static LockTable getInstance() {
        return instance;
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

    @Deprecated
    public void released(final long groupId, final String id, final LockType lockType, final LockMode mode, final int count) {
        event(Released, groupId, id, lockType, mode, count);
    }

    private void event(final LockAction.Action action, final long groupId, final String id, final LockType lockType, final LockMode mode) {
        event(action, groupId, id, lockType, mode, 1);
    }

    private void event(final LockAction.Action action, final long groupId, final String id, final LockType lockType, final LockMode mode, final int count) {
        if(!enableLogEvents) {
            return;
        }

        final long timestamp = System.nanoTime();
        final Thread currentThread = Thread.currentThread();
        final String threadName = currentThread.getName();
        @Nullable final StackTraceElement[] stackTrace = getStackTrace(currentThread);

        //TODO(AR) temp for filtering
        if (threadName.startsWith("DefaultQuartzScheduler_") || id.equals("dom.dbx") || id.equals("collections.dbx") || id.equals("collections.dbx") || id.equals("structure.dbx") || id.equals("values.dbx") || id.equals("CollectionCache")) return;

        final LockAction lockAction = new LockAction(action, groupId, id, lockType, mode, threadName, count, timestamp, stackTrace);

        /**
         * Very useful for debugging Lock life cycles
         */
        if(LOG.isTraceEnabled()) {
            sanityCheckLockLifecycles(lockAction);
        }

        queue.add(Either.Right(lockAction));
    }

    public boolean hasPendingEvents() {
        return !queue.isEmpty();
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

    private static final String THIS_CLASS_NAME = LockTable.class.getName();

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
        final ListenerAction listenerAction = new ListenerAction(ListenerAction.Action.Register, lockEventListener);
        queue.add(Either.Left(listenerAction));
    }

    public void deregisterListener(final LockEventListener lockEventListener) {
        final ListenerAction listenerAction = new ListenerAction(ListenerAction.Action.Deregister, lockEventListener);
        queue.add(Either.Left(listenerAction));
    }

    private static class QueueConsumer implements Runnable {
        private final TransferQueue<Either<ListenerAction, LockAction>> queue;
        private final ConcurrentMap<String, Map<LockType, List<Tuple2<LockMode, String>>>> attempting;
        private final ConcurrentMap<String, Map<LockType, Map<LockMode, Integer>>> acquired;
        private final List<LockEventListener> listeners = new ArrayList<>();

        QueueConsumer(final TransferQueue<Either<ListenerAction, LockAction>> queue,
                      final ConcurrentMap<String, Map<LockType, List<Tuple2<LockMode, String>>>> attempting,
                      final ConcurrentMap<String, Map<LockType, Map<LockMode, Integer>>> acquired) {
            this.queue = queue;
            this.attempting = attempting;
            this.acquired = acquired;
        }

        @Override
        public void run() {
            while (true) {
                try {
                    final Either<ListenerAction, LockAction> event = queue.take();

                    if(event.isLeft()) {
                        processListenerAction(event.left().get());
                    } else {
                        processLockAction(event.right().get());
                    }

                } catch (final InterruptedException e) {
                    LOG.fatal("LockTable.QueueConsumer was interrupted");
                }
            }
        }

        private void processListenerAction(final ListenerAction listenerAction) {
            if(listenerAction.action == ListenerAction.Action.Register) {
                listeners.add(listenerAction.lockEventListener);
                listenerAction.lockEventListener.registered();
            } else if(listenerAction.action == ListenerAction.Action.Deregister) {
                listeners.remove(listenerAction.lockEventListener);
                listenerAction.lockEventListener.unregistered();
            }
        }

        private void processLockAction(final LockAction lockAction) {
            if (lockAction.action == Attempt) {
                notifyListenersOfAttempt(lockAction);
                addToAttempting(lockAction);

            } else if (lockAction.action == AttemptFailed) {
                removeFromAttempting(lockAction);
                notifyListenersOfAttemptFailed(lockAction);

            } else if (lockAction.action == Acquired) {
                removeFromAttempting(lockAction);
                incrementAcquired(lockAction);

            } else if (lockAction.action == Released) {
                decrementAcquired(lockAction);
            }
        }

        private void notifyListenersOfAttempt(final LockAction lockAction) {
            for(final LockEventListener listener : listeners) {
                try {
                    listener.accept(lockAction);
                } catch (final Exception e) {
                    LOG.error("Listener '{}' error: ", listener.getClass().getName(), e);
                }
            }
        }

        private void notifyListenersOfAttemptFailed(final LockAction lockAction) {
            for(final LockEventListener listener : listeners) {
                try {
                    listener.accept(lockAction);
                } catch (final Exception e) {
                    LOG.error("Listener '{}' error: ", listener.getClass().getName(), e);
                }
            }
        }

        private void notifyListenersOfAcquire(final LockAction lockAction, final int newReferenceCount) {
            final LockAction newLockAction = lockAction.withCount(newReferenceCount);
            for(final LockEventListener listener : listeners) {
                try {
                    listener.accept(newLockAction);
                } catch (final Exception e) {
                    LOG.error("Listener '{}' error: ", listener.getClass().getName(), e);
                }
            }
        }

        private void notifyListenersOfRelease(final LockAction lockAction, final int newReferenceCount) {
            final LockAction newLockAction = lockAction.withCount(newReferenceCount);
            for(final LockEventListener listener : listeners) {
                try {
                    listener.accept(newLockAction);
                } catch (final Exception e) {
                    LOG.error("Listener '{}' error: ", listener.getClass().getName(), e);
                }
            }
        }

        private void addToAttempting(final LockAction lockAction) {
            attempting.compute(lockAction.id, (id, attempts) -> {
                if (attempts == null) {
                    attempts = new HashMap<>();
                }

                attempts.compute(lockAction.lockType, (lockType, v) -> {
                    if (v == null) {
                        v = new ArrayList<>();
                    }

                    v.add(new Tuple2<>(lockAction.mode, lockAction.threadName));
                    return v;
                });

                return attempts;
            });
        }

        private void removeFromAttempting(final LockAction lockAction) {
            attempting.compute(lockAction.id, (id, attempts) -> {
                if (attempts == null) {
                    return null;
                } else {
                    attempts.compute(lockAction.lockType, (lockType, v) -> {
                        if (v == null) {
                            return null;
                        }

                        v.removeIf(val -> val._1 == lockAction.mode && val._2.equals(lockAction.threadName));
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

        private void incrementAcquired(final LockAction lockAction) {
            acquired.compute(lockAction.id, (id, acqu) -> {
                if (acqu == null) {
                    acqu = new HashMap<>();
                }

                acqu.compute(lockAction.lockType, (lockType, v) -> {
                    if (v == null) {
                        v = new HashMap<>();
                    }

                    v.compute(lockAction.mode, (mode, referenceCount) -> {
                        if (referenceCount == null) {
                            referenceCount = 1;
                        } else {
                            referenceCount++;
                        }

                        notifyListenersOfAcquire(lockAction, referenceCount);

                        return referenceCount;
                    });

                    return v;
                });

                return acqu;
            });
        }

        private void decrementAcquired(final LockAction lockAction) {
            acquired.compute(lockAction.id, (id, acqu) -> {
                if (acqu == null) {
                    return null;
                }

                acqu.compute(lockAction.lockType, (lockType, v) -> {
                    if (v == null) {
                        return null;
                    }

                    v.compute(lockAction.mode, (mode, referenceCount) -> {
                        if (referenceCount == null) {
                            return null;
                        } else {
                            referenceCount--;

                            notifyListenersOfRelease(lockAction, referenceCount);

                            if (referenceCount <= 0) {
                                return null;
                            } else {
                                return referenceCount;
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
    }

    public interface LockEventListener {
        default void registered() {}
        void accept(final LockAction lockAction);
        default void unregistered() {}
    }

    private static class ListenerAction {
        enum Action {
            Register,
            Deregister
        }

        private final Action action;
        private final LockEventListener lockEventListener;

        public ListenerAction(final Action action, final LockEventListener lockEventListener) {
            this.action = action;
            this.lockEventListener = lockEventListener;
        }

        @Override
        public String toString() {
            return action.name() + " " + lockEventListener.getClass().getName();
        }
    }

    public static class LockAction {
        public enum Action {
            Attempt,
            AttemptFailed,
            Acquired,
            Released
        }

        public final Action action;
        public final long groupId;
        public final String id;
        public final LockType lockType;
        public final LockMode mode;
        public final String threadName;
        public final int count;
        /**
         * System#nanoTime()
         */
        public final long timestamp;
        @Nullable public final StackTraceElement[] stackTrace;

        LockAction(final Action action, final long groupId, final String id, final LockType lockType, final LockMode mode, final String threadName, final int count, final long timestamp, @Nullable final StackTraceElement[] stackTrace) {
            this.action = action;
            this.groupId = groupId;
            this.id = id;
            this.lockType = lockType;
            this.mode = mode;
            this.threadName = threadName;
            this.count = count;
            this.timestamp = timestamp;
            this.stackTrace = stackTrace;
        }

        public LockAction withCount(final int count) {
            return new LockAction(action, groupId, id, lockType, mode, threadName, count, timestamp, stackTrace);
        }

        @Override
        public String toString() {
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
                final String reason = getSimpleStackReason();
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
                    .append(Integer.toString(count));
            }

            return builder.toString();
        }

        private static final String NATIVE_BROKER_CLASS_NAME = NativeBroker.class.getName();
        private static final String COLLECTION_STORE_CLASS_NAME = NativeBroker.class.getName();
        private static final String TXN_CLASS_NAME = Txn.class.getName();

        @Nullable
        public String getSimpleStackReason() {
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

    public enum LockType {
        @Deprecated LEGACY_COLLECTION,
        @Deprecated LEGACY_DOCUMENT,

        COLLECTION,
        DOCUMENT,

        COLLECTION_CACHE
    }

    /** debugging tools below **/


    /**
     * Holds a count of READ and WRITE locks by {@link LockAction#id}
     */
    private final Map<String, Tuple2<Long, Long>> lockCounts = new HashMap<>();

    /**
     * Checks that there are not more releases and then acquires
     */
    private void sanityCheckLockLifecycles(final LockAction lockAction) {
        synchronized(lockCounts) {
            long read = 0;
            long write = 0;

            final Tuple2<Long, Long> lockCount = lockCounts.get(lockAction.id);
            if(lockCount != null) {
                read = lockCount._1;
                write = lockCount._2;
            }

            if(lockAction.action == LockAction.Action.Acquired) {
                if(lockAction.mode == LockMode.READ_LOCK) {
                    read++;
                } else if(lockAction.mode == LockMode.WRITE_LOCK) {
                    write++;
                }
            } else if(lockAction.action == LockAction.Action.Released) {
                if(lockAction.mode == LockMode.READ_LOCK) {
                    if(read == 0) {
                        LOG.error("Negative READ_LOCKs", new IllegalStateException());
                    }
                    read--;
                } else if(lockAction.mode == LockMode.WRITE_LOCK) {
                    if(write == 0) {
                        LOG.error("Negative WRITE_LOCKs", new IllegalStateException());
                    }
                    write--;
                }
            }

            LOG.trace("QUEUE: {} (read={} write={})", lockAction.toString(), read, write);

            lockCounts.put(lockAction.id, new Tuple2<>(read, write));
        }
    }

}
