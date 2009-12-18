package com.rho;

import java.io.InputStream;

import com.rho.RhoEmptyLogger;
import com.rho.RhoLogger;
import com.xruby.runtime.lang.*;
import com.xruby.runtime.builtin.*;
import java.io.IOException;
import com.rho.db.DBAdapter;
import com.rho.sync.SyncThread;
import com.rho.Properties;
//import net.rim.device.api.system.CodeModuleManager;
import com.rho.location.GeoLocation;

public class RhoRuby {

	private static final RhoLogger LOG = RhoLogger.RHO_STRIP_LOG ? new RhoEmptyLogger() : 
		new RhoLogger("RhoRuby");
	private static final RhoProfiler PROF = RhoProfiler.RHO_STRIP_PROFILER ? new RhoEmptyProfiler() : 
		new RhoProfiler();
	
	public static final RubyID serveID = RubyID.intern("serve_hash");
	public static final RubyID serveIndexID = RubyID.intern("serve_index_hash");
	public static final RubyID raiseRhoError = RubyID.intern("raise_rhoerror");
	public static final RubyID initApp = RubyID.intern("init_app");
//	public static final RubyID getStartPath = RubyID.intern("get_start_path");
//	public static final RubyID getOptionsPath = RubyID.intern("get_options_path");
	
	static RubyValue receiver;
	static RubyProgram mainObj;
	static RubyClass m_classRhoError;
	static RubyMethod m_RhoError_err_message;
	
	public static final int ERR_NONE = 0;
	public static final int ERR_NETWORK = 1;
	public static final int ERR_REMOTESERVER = 2;
	public static final int ERR_RUNTIME = 3;
	public static final int ERR_UNEXPECTEDSERVERRESPONSE = 4;
	public static final int ERR_DIFFDOMAINSINSYNCSRC = 5;
	public static final int ERR_NOSERVERRESPONSE = 6;
	public static final int ERR_CLIENTISNOTLOGGEDIN = 7;
	public static final int ERR_CUSTOMSYNCSERVER = 8;
	public static final int ERR_UNATHORIZED = 9;
	public static final int ERR_CANCELBYUSER = 10;
	
	public static int getNetErrorCode(Exception exc)
	{
		if ( exc instanceof IOException )
		{
			String strMsg = exc.getMessage(); 
			
	    	return strMsg != null && strMsg.indexOf("timed out") >= 0 ? RhoRuby.ERR_NOSERVERRESPONSE : RhoRuby.ERR_NETWORK;
		}
		
		return ERR_NONE;
	}

	public static int getErrorCode(Exception exc)
	{
		return ERR_RUNTIME; 
	}
	
	public static String getErrorText(int nError)
	{
		if ( nError == ERR_NONE )
			return "";
		
		if ( m_classRhoError == null )
		{
			RubyModule modRho = (RubyModule)RubyRuntime.ObjectClass.getConstant("Rho");
			m_classRhoError = (RubyClass)modRho.getConstant("RhoError");
			m_RhoError_err_message = m_classRhoError.findMethod( RubyID.intern("err_message") );
		}
		
		RubyValue res = m_RhoError_err_message.invoke( m_classRhoError, ObjectFactory.createInteger(nError), null );
		
		return res.toStr();
	}
	
	public static void RhoRubyStart(String szAppPath){
		String[] args = new String[0];

		IRhoRubyHelper helper = null;
        try{

			RubyRuntime.init(args);
	
	        DBAdapter.initMethods(RubyRuntime.DatabaseClass);
	        SyncThread.initMethods(RubyRuntime.SyncEngineClass);
	        GeoLocation.initMethods(RubyRuntime.GeoLocationClass);
	        
	        helper = RhoClassFactory.createRhoRubyHelper();
	        helper.initRubyExtensions();
	        
    		//TODO: implement recursive dir creation
    		RhoClassFactory.createFile().getDirPath("apps");
    		RhoClassFactory.createFile().getDirPath("apps/public");
    		RhoClassFactory.createFile().getDirPath("apps/public/db-files");
	        
        }catch(Throwable exc){
        	LOG.ERROR("Cannot init ruby", exc);
        }
        
        try{
        	//Class mainRuby = Class.forName("xruby.ServeME.main");
        	if ( helper != null )
        	{
        		DBAdapter.getInstance().startTransaction();
	    		mainObj = helper.createMainObject();//new xruby.ServeME.main();//(RubyProgram)mainRuby.newInstance();
	    		receiver = mainObj.invoke();
        		DBAdapter.getInstance().commit();
        		
        		RubyModule modRhom = (RubyModule)RubyRuntime.ObjectClass.getConstant("Rhom");
        	}
        	
        /*}catch(ClassNotFoundException exc){
        	LOG.ERROR("Cannot create ServeME object", exc);*/
        }catch(Throwable exc){
        	LOG.ERROR("Cannot create ServeME object", exc);
        }
	}
	
	public static void RhoRubyInitApp(){
		RubyAPI.callPublicNoArgMethod(receiver, null, initApp);
	}
	
	public static void RhoRubyStop(){
		
		receiver = null;
		mainObj = null;
		
		com.xruby.runtime.lang.RubyRuntime.fini();	
	}
	
	static public InputStream loadFile(String path){
		try {
			return RhoClassFactory.createFile().getResourceAsStream(mainObj.getClass(), path);
		} catch (Exception e) {
			e.printStackTrace();
		}	
		return null;
	}

	public static RubyValue processIndexRequest(String strIndexArg ){
		
		String strIndex = strIndexArg.replace('\\', '/');
/*		int nAppsIndex = strIndex.indexOf("/apps/");
		if ( nAppsIndex >= 0 ){
			int endIndex = strIndex.indexOf('/', nAppsIndex+6);
			if ( endIndex >= 0 )
				RhoSupport.setCurAppPath( strIndex.substring(nAppsIndex, endIndex+1));
		}*/
		
		RubyValue value = RubyAPI.callPublicOneArgMethod(receiver, ObjectFactory.createString(strIndex), null, serveIndexID);
		
		return value;
	}

	public static void raise_RhoError(int errCode)
	{
		RubyAPI.callPublicOneArgMethod(receiver, ObjectFactory.createInteger(errCode), null, raiseRhoError);
	}
	
	public static String getStartPage()
	{
		return RhoConf.getInstance().getString("start_path");
		//RubyValue value = RubyAPI.callPublicNoArgMethod(receiver, null, getStartPath);
		//return value.toString();
	}

	public static String getOptionsPage()
	{
		return RhoConf.getInstance().getString("options_path");

		//RubyValue value = RubyAPI.callPublicNoArgMethod(receiver, null, getOptionsPath);
		//return value.toString();
	}
	
	public static RubyValue processRequest(Properties reqHash, Properties reqHeaders, Properties resHeaders )throws IOException{
		RubyHash rh = ObjectFactory.createHash();
		for( int i = 0; i < reqHash.size(); i++ ){
			if ( reqHash.getValueAt(i) != null)
				addStrToHash(rh, reqHash.getKeyAt(i), reqHash.getValueAt(i) );
		}

		RubyHash headers = ObjectFactory.createHash();
		for( int i = 0; i < reqHeaders.size(); i++ ){
			if ( reqHeaders.getValueAt(i) != null){
				String strKey = reqHeaders.getKeyAt(i);
				if ( strKey.equalsIgnoreCase("content-type"))
					strKey = "Content-Type";
				else if ( strKey.equalsIgnoreCase("content-length"))
					strKey = "Content-Length";
				
				addStrToHash(headers, strKey, reqHeaders.getValueAt(i) );
			}
		}
		
		addHashToHash( rh, "headers", headers );
		
		RubyValue res = callFramework(rh); 
		return res; 
	}
	
	static RubyValue callFramework(RubyValue hashReq) {
		RubyValue value = RubyAPI.callPublicOneArgMethod(receiver, hashReq, null, serveID);
		
		return value;
	}
	
	public static RubyHash createHash() {
		return ObjectFactory.createHash();
	}

	public static RubyArray create_array() {
		return new RubyArray();
	}

	public static RubyString create_string(String str) {
		return ObjectFactory.createString(str);
	}
	
	public static void add_to_array(RubyValue ar, RubyValue val)
	{
		((RubyArray)ar).add(val);
	}
	
	public static RubyValue addTimeToHash(RubyHash hash, String key, long val) {
		return hash.add( ObjectFactory.createString(key), ObjectFactory.createTime(val) );
	}

	public static RubyValue addIntToHash(RubyHash hash, String key, int val) {
		return hash.add( ObjectFactory.createString(key), ObjectFactory.createInteger(val));
	}

	public static RubyValue addStrToHash(RubyHash hash, String key, String val) {
		return hash.add( ObjectFactory.createString(key), ObjectFactory.createString(val));
	}

	public static RubyValue addHashToHash(RubyHash hash, String key, RubyValue val) {
		return hash.add( ObjectFactory.createString(key), val);	
	}
	
}
