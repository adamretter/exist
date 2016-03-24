/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2015 The eXist Project
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
package org.exist.storage.txn;

import java.util.ArrayList;
import java.util.List;

import org.exist.Transaction;
import org.exist.storage.lock.Lock;
import org.exist.util.LockException;

/**
 * @author wolf
 *
 */
public class Txn implements Transaction {

    public enum State { STARTED, ABORTED, COMMITTED, CLOSED }

    private final TransactionManager tm;
    private final long id;
    private State state;
    private String originId;

    private List<LockInfo> locksHeld = new ArrayList<>();
    private List<TxnListener> listeners = new ArrayList<>();

    public Txn(final TransactionManager tm, final long transactionId) {
        this.tm = tm;
        this.id = transactionId;
        this.state = State.STARTED;
    }

    public State getState() {
        return state;
    }

    protected void setState(final State state) {
        this.state = state;
    }

    public long getId() {
        return id;
    }

    /**
     * Registers a lock with the Transaction
     *
     * Note - registering does not acquire the lock,
     * to acquire and register use {@link Txn#acquireLock(Lock, int)}
     *
     * @param lock The lock to register
     * @param lockMode The mode of the lock
     */
    public void registerLock(final Lock lock, final int lockMode) {
        locksHeld.add(new LockInfo(lock, lockMode));
    }

    /**
     * De-registers a lock from the Transaction
     * that was previously registered with {@link Txn#registerLock(Lock, int)}
     * or acquired and registered with {@link Txn#acquireLock(Lock, int)}
     *
     * Note - de-registering does not release the lock
     *
     * @param lock The lock to de-register
     * @param lockMode The mode of the lock
     *
     * @throws IllegalStateException if the provided lock and mode were not previously
     * registered with the transaction
     */
    public void deregisterLock(final Lock lock, final int lockMode) {
        int removeIdx = -1;
        for(int i = 0; i < locksHeld.size(); i++) {
            final LockInfo held = locksHeld.get(i);
            if(held.lock == lock && held.lockMode == lockMode) {
                removeIdx = i;
                break;
            }
        }
        if(removeIdx > -1) {
            locksHeld.remove(removeIdx);
        } else {
            throw new IllegalStateException("The lock was not previously registered with the transaction");
        }
    }

    /**
     * Acquire a lock and register it with the Transaction
     *
     * @param lock The lock to acquire and register
     * @param lockMode The mode of the lock
     */
    public void acquireLock(final Lock lock, final int lockMode) throws LockException {
        lock.acquire(lockMode);
        locksHeld.add(new LockInfo(lock, lockMode));
    }
    
    public void releaseAll() {
        for (int i = locksHeld.size() - 1; i >= 0; i--) {
            final LockInfo info = locksHeld.get(i);
            info.lock.release(info.lockMode);
        }
        locksHeld.clear();
    }

    public void registerListener(final TxnListener listener) {
        listeners.add(listener);
    }

    protected void signalAbort() {
        state = State.ABORTED;
        for (int i = 0; i < listeners.size(); i++) {
            listeners.get(i).abort();
        }
    }

    protected void signalCommit() {
        state = State.COMMITTED;
        for (int i = 0; i < listeners.size(); i++) {
            listeners.get(i).commit();
        }
    }

    private static class LockInfo {
        final Lock lock;
        final int lockMode;
        
        public LockInfo(final Lock lock, final int lockMode) {
            this.lock = lock;
            this.lockMode = lockMode;
        }
    }
 
    @Override
    public void success() throws TransactionException {
        commit();
    }

    @Override
    public void commit() throws TransactionException {
        tm.commit(this);
    }

    @Override
    public void failure() {
        abort();
    }
 
    @Override
    public void abort() {
        tm.abort(this);
    }

    @Override
    public void close() {
        tm.close(this);
    }

    /**
     * Get origin of transaction
     * @return Id
     */
    @Deprecated
    public String getOriginId() {
        return originId;
    }

    /**
     *  Set origin of transaction. Purpose is to be able to
     * see the origin of the transaction.
     *
     * @param id  Identifier of origin, FQN or URI.
     */
    @Deprecated
    public void setOriginId(String id) {
        originId = id;
    }

}

