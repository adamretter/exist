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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package org.exist;

import java.io.File;
import java.util.List;

import org.exist.collections.CollectionConfigurationManager;
import org.exist.collections.triggers.CollectionTrigger;
import org.exist.collections.triggers.DocumentTrigger;
import org.exist.debuggee.Debuggee;
import org.exist.dom.persistent.SymbolTable;
import org.exist.indexing.IndexManager;
import org.exist.numbering.NodeIdFactory;
import org.exist.plugin.PluginsManager;
import org.exist.scheduler.Scheduler;
import org.exist.security.AuthenticationException;
import org.exist.security.SecurityManager;
import org.exist.security.Subject;
import org.exist.storage.CacheManager;
import org.exist.storage.DBBroker;
import org.exist.storage.MetaStorage;
import org.exist.storage.NotificationService;
import org.exist.storage.ProcessMonitor;
import org.exist.storage.txn.TransactionManager;
import org.exist.util.Configuration;
import org.exist.xquery.PerformanceStats;

/**
 * Database controller, all operation synchronized by this instance. (singleton)
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 * 
 */
public interface Database {

    // TODO: javadocs

    public String getId();

    /**
     * 
     * @return SecurityManager
     */
    public SecurityManager getSecurityManager();

    /**
     * 
     * @return IndexManager
     */
    public IndexManager getIndexManager();

    /**
     * 
     * @return TransactionManager
     */
    public TransactionManager getTransactionManager();

    /**
     * 
     * @return CacheManager
     */
    public CacheManager getCacheManager();

    /**
     * 
     * @return Scheduler
     */
    public Scheduler getScheduler();

    /**
	 * 
	 */
    public void shutdown();

    /**
     * 
     * @return Subject
     */
    public Subject getSubject();

    /**
     * 
     * @param subject
     */
    public boolean setSubject(Subject subject);

    // TODO: remove 'throws EXistException'?
    public DBBroker getBroker() throws EXistException; 

    public DBBroker authenticate(String username, Object credentials) throws AuthenticationException;

    /*
     * @Deprecated ? 
     * 
     * try { 
     *     broker = database.authenticate(account, credentials);
     * 
     *     broker1 = database.get(); 
     *     broker2 = database.get(); 
     *     ... 
     *     brokerN = database.get();
     * 
     * } finally { 
     *     database.release(broker);
     * }
     */
    public DBBroker get(Subject subject) throws EXistException;

    public DBBroker getActiveBroker(); // throws EXistException;

    public void release(DBBroker broker);

    /**
     * Returns the number of brokers currently serving requests for the database
     * instance.
     * 
     * @return The brokers count
     */
    public int countActiveBrokers();

    /**
     * 
     * @return Debuggee
     */
    public Debuggee getDebuggee();

    public PerformanceStats getPerformanceStats();

    // old configuration
    public Configuration getConfiguration();

    public NodeIdFactory getNodeFactory();

    public File getStoragePlace();

    public CollectionConfigurationManager getConfigurationManager();

    /**
     * Master document triggers.
     */
    public List<Class<? extends DocumentTrigger>> getGlobalDocumentTriggers();

    /**
     * Master Collection triggers.
     */
    public List<Class<? extends CollectionTrigger>> getGlobalCollectionTriggers();

    public void registerGlobalDocumentTrigger(Class<? extends DocumentTrigger> clazz);

    public void registerGlobalCollectionTrigger(Class<? extends CollectionTrigger> clazz);

    public ProcessMonitor getProcessMonitor();

    public boolean isReadOnly();

    public NotificationService getNotificationService();

    public PluginsManager getPluginsManager();

    public SymbolTable getSymbols();

    public MetaStorage getMetaStorage();
}
