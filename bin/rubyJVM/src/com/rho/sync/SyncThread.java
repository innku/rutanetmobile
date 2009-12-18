/*
 *  rhodes
 *
 *  Copyright (C) 2008 Rhomobile, Inc. All rights reserved.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.rho.sync;

import j2me.util.LinkedList;

import com.rho.Mutex;
import com.rho.RhoClassFactory;
import com.rho.RhoConf;
import com.rho.RhoEmptyLogger;
import com.rho.RhoLogger;
import com.rho.RhoRuby;
import com.rho.RhoThread;
import com.rho.TimeInterval;
import com.rho.db.DBAdapter;
import com.rho.db.IDBResult;
import com.xruby.runtime.builtin.ObjectFactory;
import com.xruby.runtime.builtin.RubyArray;
import com.xruby.runtime.lang.RubyBlock;
import com.xruby.runtime.lang.RubyClass;
import com.xruby.runtime.lang.RubyConstant;
import com.xruby.runtime.lang.RubyException;
import com.xruby.runtime.lang.RubyNoArgMethod;
import com.xruby.runtime.lang.RubyNoOrOneArgMethod;
import com.xruby.runtime.lang.RubyOneArgMethod;
import com.xruby.runtime.lang.RubyOneOrTwoArgMethod;
import com.xruby.runtime.lang.RubyTwoArgMethod;
import com.xruby.runtime.lang.RubyRuntime;
import com.xruby.runtime.lang.RubyValue;
import com.xruby.runtime.lang.RubyVarArgMethod;

public class SyncThread extends RhoThread
{
	private static final RhoLogger LOG = RhoLogger.RHO_STRIP_LOG ? new RhoEmptyLogger() : 
		new RhoLogger("Sync");
	private static final int SYNC_POLL_INTERVAL_SECONDS = 300;
	private static final int SYNC_POLL_INTERVAL_INFINITE = Integer.MAX_VALUE/1000;
	private static final int SYNC_WAIT_BEFOREKILL_SECONDS  = 3;
	
	static SyncThread m_pInstance;

   	public final static int scNone = 0, scSyncAll = 2, scSyncOne = 3, scChangePollInterval=4, scExit=5, scLogin = 6, scSearchOne=7; 
    
   	static private class SyncCommand
   	{
   		int m_nCmdCode;
   		int m_nCmdParam;
   		String m_strCmdParam;
   		
   		SyncCommand(int nCode, int nParam)
   		{
   			m_nCmdCode = nCode;
   			m_nCmdParam = nParam;
   		}
   		SyncCommand(int nCode, String strParam)
   		{
   			m_nCmdCode = nCode;
   			m_strCmdParam = strParam;
   		}
	    SyncCommand(int nCode, String strParam, int nCmdParam)
	    {
		    m_nCmdCode = nCode;
		    m_strCmdParam = strParam;
            m_nCmdParam = nCmdParam;
	    }
   		
   		SyncCommand(int nCode)
   		{
   			m_nCmdCode = nCode;
   			m_nCmdParam = 0;
   		}
   		
   		public boolean equals(Object obj)
   		{
   			SyncCommand oSyncCmd = (SyncCommand)obj;
   			return m_nCmdCode == oSyncCmd.m_nCmdCode && m_nCmdParam == oSyncCmd.m_nCmdParam &&
   				(m_strCmdParam == oSyncCmd.m_strCmdParam ||
   				(m_strCmdParam != null && oSyncCmd.m_strCmdParam != null && m_strCmdParam.equals(oSyncCmd.m_strCmdParam)));  		
   		}
   	};
   	static private class SyncLoginCommand extends SyncCommand
   	{
   		String m_strName, m_strPassword;
   		public SyncLoginCommand(String name, String password, String callback)
   		{
   			super(scLogin,callback);
   			
   			m_strName = name;
   			m_strPassword = password;
   		}
   	};
    static class SyncSearchCommand extends SyncCommand
    {
	    String m_strFrom;
	    boolean   m_bSyncChanges;
	    int     m_nProgressStep;
        public SyncSearchCommand(String from, String params, int source_id, boolean sync_changes, int nProgressStep)
	    {
        	super(scSearchOne,params,source_id);
		    m_strFrom = from;
		    m_bSyncChanges = sync_changes;
		    m_nProgressStep = nProgressStep;
	    }
    };
   	
    SyncEngine  m_oSyncEngine;
    RhoClassFactory m_ptrFactory;
	int           m_nPollInterval;
	Object        m_mxStackCommands;// = new Mutex();
	LinkedList	  m_stackCommands = new LinkedList();	         
	
	public static SyncThread Create(RhoClassFactory factory)throws Exception
	{
	    if ( m_pInstance != null) 
	        return m_pInstance;
	
	    m_pInstance = new SyncThread(factory);
	    return m_pInstance;
	}

	public void Destroy()
	{
	    m_oSyncEngine.exitSync();
	    stop(SYNC_WAIT_BEFOREKILL_SECONDS);
	    LOG.INFO( "Sync engine thread shutdown" );
		
	    m_pInstance = null;
	}

	SyncThread(RhoClassFactory factory)throws Exception
	{
		super(factory);
		
		m_oSyncEngine = new SyncEngine(DBAdapter.getInstance());
		m_nPollInterval = SYNC_POLL_INTERVAL_SECONDS;
		if( RhoConf.getInstance().isExist("sync_poll_interval") )
			m_nPollInterval = RhoConf.getInstance().getInt("sync_poll_interval");
		
		m_ptrFactory = factory;
	
	    m_oSyncEngine.setFactory(factory);
	    m_mxStackCommands = getSyncObject();
	    	
	    ClientRegister.Create(factory);
	    	    
	    start(epLow);
	}

    public static SyncThread getInstance(){ return m_pInstance; }
    public static SyncEngine getSyncEngine(){ return m_pInstance.m_oSyncEngine; }
    static DBAdapter getDBAdapter(){ return DBAdapter.getInstance(); }

    void addSyncCommand(SyncCommand oSyncCmd)
    { 
    	LOG.INFO( "addSyncCommand: " + oSyncCmd.m_nCmdCode );
    	synchronized(m_mxStackCommands)
    	{
    		boolean bExist = false;
    		for ( int i = 0; i < m_stackCommands.size(); i++ )
    		{
    			if ( m_stackCommands.get(i).equals(oSyncCmd) )
    			{
    				bExist = true;
    				break;
    			}
    		}
    		
    		if ( !bExist )
    			m_stackCommands.add(oSyncCmd);
    	}
    	stopWait(); 
    }
    
	
    int getLastSyncInterval()
    {
    	try{
	    	TimeInterval nowTime = TimeInterval.getCurrentTime();
	    	
		    IDBResult res = m_oSyncEngine.getDB().executeSQL("SELECT last_updated from sources");
		    long latestTimeUpdated = 0;
		    for ( ; !res.isEnd(); res.next() )
		    { 
		        long timeUpdated = res.getLongByIdx(0);
		        if ( latestTimeUpdated < timeUpdated )
		        	latestTimeUpdated = timeUpdated;
		    }
	    	
	    	return latestTimeUpdated > 0 ? (int)(nowTime.toULong()-latestTimeUpdated) : 0;
    	}catch(Exception exc)
    	{
    		LOG.ERROR("isStartSyncNow failed.", exc);
    	}
    	return 0;
    }
    
	public void run()
	{
		LOG.INFO( "Starting sync engine main routine..." );
	
		int nLastSyncInterval = getLastSyncInterval();
		while( m_oSyncEngine.getState() != SyncEngine.esExit )
		{
	        int nWait = m_nPollInterval > 0 ? m_nPollInterval : SYNC_POLL_INTERVAL_INFINITE;

	        if ( m_nPollInterval > 0 && nLastSyncInterval > 0 )
	            nWait = (m_nPollInterval*1000 - nLastSyncInterval)/1000;

	        synchronized(m_mxStackCommands)
	        {
				if ( nWait >= 0 && m_oSyncEngine.getState() != SyncEngine.esExit && 
					 isNoCommands() )
				{
					LOG.INFO( "Sync engine blocked for " + nWait + " seconds..." );
			        wait(nWait);
				}
	        }
	        nLastSyncInterval = 0;
			
	        if ( m_oSyncEngine.getState() != SyncEngine.esExit )
	        {
	        	try{
	        		processCommands();
	        	}catch(Exception e)
	        	{
	        		LOG.ERROR("processCommand failed", e);
	        	}
	        }
		}
	}
	
	boolean isNoCommands()
	{
		boolean bEmpty = false;
    	synchronized(m_mxStackCommands)
    	{		
    		bEmpty = m_stackCommands.isEmpty();
    	}

    	return bEmpty;
	}
	
	void processCommands()throws Exception
	{
		if ( isNoCommands() )
			addSyncCommand(new SyncCommand(scNone));
    	
		while(!isNoCommands())
		{
			SyncCommand oSyncCmd = null;
	    	synchronized(m_mxStackCommands)
	    	{
	    		oSyncCmd = (SyncCommand)m_stackCommands.removeFirst();
	    	}
			
			processCommand(oSyncCmd);
		}
	}
	
	void processCommand(SyncCommand oSyncCmd)throws Exception
	{
	    switch(oSyncCmd.m_nCmdCode)
	    {
	    case scNone:
	        if ( m_nPollInterval > 0 )
	            m_oSyncEngine.doSyncAllSources();
	        break;
	    case scSyncAll:
	        m_oSyncEngine.doSyncAllSources();
	        break;
	    case scChangePollInterval:
	        break;
	    case scSyncOne:
	    	m_oSyncEngine.doSyncSource(oSyncCmd.m_nCmdParam,oSyncCmd.m_strCmdParam,"","", false, -1 );
	        break;
	    case scSearchOne:
	        m_oSyncEngine.doSyncSource(oSyncCmd.m_nCmdParam,"",oSyncCmd.m_strCmdParam, 
	            ((SyncSearchCommand)oSyncCmd).m_strFrom, ((SyncSearchCommand)oSyncCmd).m_bSyncChanges,
	            ((SyncSearchCommand)oSyncCmd).m_nProgressStep);
	        break;
	        
	    case scLogin:
	    	{
	    		SyncLoginCommand oLoginCmd = (SyncLoginCommand)oSyncCmd;
	    		m_oSyncEngine.login(oLoginCmd.m_strName, oLoginCmd.m_strPassword, oLoginCmd.m_strCmdParam );
	    	}
	        break;
	        
	    }
	}

	static ISyncStatusListener m_statusListener = null;
	public boolean setStatusListener(ISyncStatusListener listener) {
		m_statusListener = listener;
		if (m_oSyncEngine != null) {
			m_oSyncEngine.getNotify().setSyncStatusListener(listener);
			return true;
		}
		return false;
	}
	
	public void setPollInterval(int nInterval)
	{ 
	    m_nPollInterval = nInterval; 
	    if ( m_nPollInterval == 0 )
	        m_oSyncEngine.stopSync();
	
	    addSyncCommand(new SyncCommand(scChangePollInterval)); 
	}
	
	public static void doSyncAllSources(boolean bShowStatus)
	{
		if (bShowStatus&&(m_statusListener != null)) {
			getInstance().m_oSyncEngine.getNotify().setSyncStatusListener(m_statusListener);
			m_statusListener.createStatusPopup();
		}else
			getInstance().m_oSyncEngine.getNotify().setSyncStatusListener(null);
		
		getInstance().addSyncCommand(new SyncCommand(SyncThread.scSyncAll));
	}

	public static void doSyncSource(int nSrcID, boolean bShowStatus)
	{
		if (bShowStatus&&(m_statusListener != null)) {
			m_statusListener.createStatusPopup();
		}
		
		getInstance().addSyncCommand(new SyncCommand(SyncThread.scSyncOne, nSrcID) );
	}

	public static void doSyncSource(String strSrcUrl, boolean bShowStatus)
	{
		if (bShowStatus&&(m_statusListener != null)) {
			m_statusListener.createStatusPopup();
		}
		
		getInstance().addSyncCommand(new SyncCommand(SyncThread.scSyncOne, strSrcUrl) );
	}
	
	public static void stopSync()throws Exception
	{
		if ( getSyncEngine().isSyncing() )
		{
			getSyncEngine().stopSyncByUser();
			int nWait = 0;
			//while( nWait < 30000 && getSyncEngine().getState() != SyncEngine.esNone )
			while( nWait < 30000 && getSyncEngine().getDB().isInsideTransaction() )
				try{ Thread.sleep(100); nWait += 100; }catch(Exception e){}
				
			if (getSyncEngine().getState() != SyncEngine.esNone)
			{
				getSyncEngine().exitSync();
				getInstance().stop(0);
				RhoClassFactory ptrFactory = getInstance().m_ptrFactory;
				m_pInstance = null;
				
				Create(ptrFactory);
			}
		}
	}
	
	public void addobjectnotify_bysrcname(String strSrcName, String strObject)
	{
		getSyncEngine().getNotify().addObjectNotify(strSrcName, strObject);
	}
	
	public static void initMethods(RubyClass klass) {
		klass.getSingletonClass().defineMethod("dosync", new RubyNoOrOneArgMethod(){ 
			protected RubyValue run(RubyValue receiver, RubyBlock block )
			{
				try {
					doSyncAllSources(true);
				} catch(Exception e) {
					LOG.ERROR("dosync failed", e);
					throw (e instanceof RubyException ? (RubyException)e : new RubyException(e.getMessage()));
				}
				return RubyConstant.QNIL;
			}
			protected RubyValue run(RubyValue receiver, RubyValue arg, RubyBlock block )
			{
				try {
					String str = arg.asString();
					boolean show = arg.equals(RubyConstant.QTRUE)||"true".equalsIgnoreCase(str);
					doSyncAllSources(show);
				} catch(Exception e) {
					LOG.ERROR("dosync failed", e);
					throw (e instanceof RubyException ? (RubyException)e : new RubyException(e.getMessage()));
				}
				return RubyConstant.QNIL;
			}
		});		
		klass.getSingletonClass().defineMethod("dosync_source", new RubyOneOrTwoArgMethod(){ 
			protected RubyValue run(RubyValue receiver, RubyValue arg, RubyBlock block )
			{
				try {
					doSyncSource(arg.toInt(), true);
				} catch(Exception e) {
					LOG.ERROR("dosync_source failed", e);
					throw (e instanceof RubyException ? (RubyException)e : new RubyException(e.getMessage()));
				}
				return RubyConstant.QNIL;
			}
			protected RubyValue run(RubyValue receiver, RubyValue arg0, RubyValue arg1, RubyBlock block )
			{
				try {
					String str = arg1.asString();
					boolean show = arg1.equals(RubyConstant.QTRUE)||"true".equalsIgnoreCase(str);
					doSyncSource(arg0.toInt(), show);
				} catch(Exception e) {
					LOG.ERROR("dosync_source failed", e);
					throw (e instanceof RubyException ? (RubyException)e : new RubyException(e.getMessage()));
				}
				return RubyConstant.QNIL;
			}
		});
		
		klass.getSingletonClass().defineMethod("dosearch_source",
			new RubyVarArgMethod() {
				protected RubyValue run(RubyValue receiver, RubyArray args, RubyBlock block) {
					if ( args.size() != 5 )
						throw new RubyException(RubyRuntime.ArgumentErrorClass, 
								"in SyncEngine.dosearch_source: wrong number of arguments ( " + args.size() + " for " + 5 + " )");			
					
					try{
						int source_id = args.get(0).toInt();
						String from = args.get(1).toStr();
						String params = args.get(2).toStr();
						
						String str = args.get(3).asString();
						int nProgressStep = args.get(4).toInt();
						boolean bSearchSyncChanges = args.get(3).equals(RubyConstant.QTRUE)||"true".equalsIgnoreCase(str);
						stopSync();
						
						getInstance().addSyncCommand(new SyncSearchCommand(from,params,source_id,bSearchSyncChanges, nProgressStep) );
					}catch(Exception e)
					{
						LOG.ERROR("SyncEngine.login", e);
						RhoRuby.raise_RhoError(RhoRuby.ERR_RUNTIME);
					}
					
					return RubyConstant.QNIL;
				    
				}
			});
		
		klass.getSingletonClass().defineMethod("stop_sync", new RubyNoArgMethod() {
			protected RubyValue run(RubyValue receiver, RubyBlock block) {
				try{
					stopSync();
				}catch(Exception e)
				{
					LOG.ERROR("stop_sync failed", e);
					throw (e instanceof RubyException ? (RubyException)e : new RubyException(e.getMessage()));
				}
				
				return RubyConstant.QNIL;
			}
		});
		
		klass.getSingletonClass().defineMethod("lock_sync_mutex",
			new RubyNoArgMethod() {
				protected RubyValue run(RubyValue receiver, RubyBlock block) {
					try{
					    DBAdapter db = getDBAdapter();
					    db.setUnlockDB(true);
					    db.Lock();
					}catch(Exception e)
					{
						LOG.ERROR("lock_sync_mutex failed", e);
						throw (e instanceof RubyException ? (RubyException)e : new RubyException(e.getMessage()));
					}
				    
				    return RubyConstant.QNIL;
				}
			});
		klass.getSingletonClass().defineMethod("unlock_sync_mutex",
			new RubyNoArgMethod() {
				protected RubyValue run(RubyValue receiver, RubyBlock block) {
					try{
					    DBAdapter db = getDBAdapter();
					    db.Unlock();
					}catch(Exception e)
					{
						LOG.ERROR("unlock_sync_mutex failed", e);
						throw (e instanceof RubyException ? (RubyException)e : new RubyException(e.getMessage()));
					}
					
				    return RubyConstant.QNIL;
				}
			});
		klass.getSingletonClass().defineMethod("login",
				new RubyVarArgMethod() {
					protected RubyValue run(RubyValue receiver, RubyArray args, RubyBlock block) {
						if ( args.size() != 3 )
							throw new RubyException(RubyRuntime.ArgumentErrorClass, 
									"in SyncEngine.login: wrong number of arguments ( " + args.size() + " for " + 3 + " )");			
						
						try{
							String name = args.get(0).toStr();
							String password = args.get(1).toStr();
							String callback = args.get(2).toStr();
							
							stopSync();
							
							getInstance().addSyncCommand(new SyncLoginCommand(name, password, callback) );
						}catch(Exception e)
						{
							LOG.ERROR("SyncEngine.login", e);
							RhoRuby.raise_RhoError(RhoRuby.ERR_RUNTIME);
						}
						
						return RubyConstant.QNIL;
					    
					}
				});
		
		klass.getSingletonClass().defineMethod("logged_in",
			new RubyNoArgMethod() {
				protected RubyValue run(RubyValue receiver, RubyBlock block) {
					DBAdapter db = getDBAdapter();

					try{
						db.setUnlockDB(true);
					    return getSyncEngine().isLoggedIn() ? 
					    		ObjectFactory.createInteger(1) : ObjectFactory.createInteger(0);
					}catch(Exception e)
					{
						LOG.ERROR("logged_in failed", e);
						throw (e instanceof RubyException ? (RubyException)e : new RubyException(e.getMessage()));
					}finally
					{
						db.setUnlockDB(false);
					}
				    
				}
			});
		
		klass.getSingletonClass().defineMethod("logout",
			new RubyNoArgMethod() {
				protected RubyValue run(RubyValue receiver, RubyBlock block) {
					DBAdapter db = getDBAdapter();

					try{
						stopSync();
						
						db.setUnlockDB(true);
					    getSyncEngine().logout();
					}catch(Exception e)
					{
						LOG.ERROR("logout failed", e);
						throw (e instanceof RubyException ? (RubyException)e : new RubyException(e.getMessage()));
					}finally
					{
						db.setUnlockDB(false);
					}
					
				    return RubyConstant.QNIL;
				}
			});
		
		klass.getSingletonClass().defineMethod("set_notification",
			new RubyVarArgMethod() {
				protected RubyValue run(RubyValue receiver, RubyArray args, RubyBlock block) {
					
					try{
						int source_id = args.get(0).toInt();
						String url = args.get(1).toStr();
						String params = args.get(2).toStr();
						getSyncEngine().getNotify().setSyncNotification(source_id, url, params);
					}catch(Exception e)
					{
						LOG.ERROR("set_notification failed", e);
						throw (e instanceof RubyException ? (RubyException)e : new RubyException(e.getMessage()));
					}
					return RubyConstant.QNIL;
				}
			});
		klass.getSingletonClass().defineMethod("clear_notification",
			new RubyOneArgMethod() {
				protected RubyValue run(RubyValue receiver, RubyValue arg1, RubyBlock block) {
					try{
						int source_id = arg1.toInt();
						getSyncEngine().getNotify().clearSyncNotification(source_id);
					}catch(Exception e)
					{
						LOG.ERROR("clear_notification failed", e);
						throw (e instanceof RubyException ? (RubyException)e : new RubyException(e.getMessage()));
					}
					
					
					return RubyConstant.QNIL;
				}
			});
		klass.getSingletonClass().defineMethod("set_pollinterval",
			new RubyOneArgMethod() {
				protected RubyValue run(RubyValue receiver, RubyValue arg1, RubyBlock block) {
					try{
						int nInterval = arg1.toInt();
						getInstance().setPollInterval(nInterval);
					}catch(Exception e)
					{
						LOG.ERROR("set_pollinterval failed", e);
						throw (e instanceof RubyException ? (RubyException)e : new RubyException(e.getMessage()));
					}
					
					return RubyConstant.QNIL;
				}
			});
		klass.getSingletonClass().defineMethod("set_syncserver",
				new RubyOneArgMethod() {
					protected RubyValue run(RubyValue receiver, RubyValue arg1, RubyBlock block) {
						try{
							String url = arg1.toStr();
							RhoConf.getInstance().setPropertyByName("syncserver", url);
							RhoConf.getInstance().saveToFile();
							RhoConf.getInstance().loadConf();
							getSyncEngine().logout();
						}catch(Exception e)
						{
							LOG.ERROR("set_syncserver failed", e);
							throw (e instanceof RubyException ? (RubyException)e : new RubyException(e.getMessage()));
						}
						
						return RubyConstant.QNIL;
					}
			});
		
		klass.getSingletonClass().defineMethod("get_src_attrs",
				new RubyOneArgMethod() {
					protected RubyValue run(RubyValue receiver, RubyValue arg1, RubyBlock block) {
						try{
							int nSrcID = arg1.toInt();
							return getDBAdapter().getAttrMgr().getAttrsBySrc(nSrcID);
						}catch(Exception e)
						{
							LOG.ERROR("get_src_attrs failed", e);
							throw (e instanceof RubyException ? (RubyException)e : new RubyException(e.getMessage()));
						}
					}
			});

		klass.getSingletonClass().defineMethod("set_objectnotify_url",
				new RubyOneArgMethod() {
					protected RubyValue run(RubyValue receiver, RubyValue arg1, RubyBlock block) {
						try{
							String url = arg1.toStr();
							SyncNotify.setObjectNotifyUrl(url);
						}catch(Exception e)
						{
							LOG.ERROR("set_objectnotify_url failed", e);
							throw (e instanceof RubyException ? (RubyException)e : new RubyException(e.getMessage()));
						}
						
						return RubyConstant.QNIL;
					}
			});

		klass.getSingletonClass().defineMethod("add_objectnotify",
				new RubyTwoArgMethod() {
					protected RubyValue run(RubyValue receiver, RubyValue arg1, RubyValue arg2, RubyBlock block) {
						try{
							Integer nSrcID = new Integer(arg1.toInt());
							String strObject = arg2.toStr();
							
							getSyncEngine().getNotify().addObjectNotify(nSrcID, strObject);
						}catch(Exception e)
						{
							LOG.ERROR("add_objectnotify failed", e);
							throw (e instanceof RubyException ? (RubyException)e : new RubyException(e.getMessage()));
						}
						
						return RubyConstant.QNIL;
					}
			});
		klass.getSingletonClass().defineMethod("clean_objectnotify",
				new RubyNoArgMethod() {
					protected RubyValue run(RubyValue receiver, RubyBlock block) {
						try{
							getSyncEngine().getNotify().cleanObjectNotifications();
						}catch(Exception e)
						{
							LOG.ERROR("clean_objectnotify failed", e);
							throw (e instanceof RubyException ? (RubyException)e : new RubyException(e.getMessage()));
						}
						
						return RubyConstant.QNIL;
					}
			});
		
		klass.getSingletonClass().defineMethod("get_lastsync_objectcount",
				new RubyOneArgMethod() {
					protected RubyValue run(RubyValue receiver, RubyValue arg1, RubyBlock block) {
						try{
							Integer nSrcID = new Integer(arg1.toInt());
							int nCount = getSyncEngine().getNotify().getLastSyncObjectCount(nSrcID);
							
							return ObjectFactory.createInteger(nCount);
						}catch(Exception e)
						{
							LOG.ERROR("get_lastsync_objectcount failed", e);
							throw (e instanceof RubyException ? (RubyException)e : new RubyException(e.getMessage()));
						}
					}
			});
		klass.getSingletonClass().defineMethod("get_pagesize",
				new RubyNoArgMethod() {
					protected RubyValue run(RubyValue receiver, RubyBlock block) {
						try{
							return ObjectFactory.createInteger(getSyncEngine().getSyncPageSize());
						}catch(Exception e)
						{
							LOG.ERROR("get_pagesize failed", e);
							throw (e instanceof RubyException ? (RubyException)e : new RubyException(e.getMessage()));
						}
					}
			});
		
		klass.getSingletonClass().defineMethod("set_pagesize",
				new RubyOneArgMethod() {
					protected RubyValue run(RubyValue receiver, RubyValue arg1, RubyBlock block) {
						try{
							getSyncEngine().setSyncPageSize(arg1.toInt());
						}catch(Exception e)
						{
							LOG.ERROR("set_pagesize failed", e);
							throw (e instanceof RubyException ? (RubyException)e : new RubyException(e.getMessage()));
						}
						
						return RubyConstant.QNIL;
					}
			});
		
	}

}
