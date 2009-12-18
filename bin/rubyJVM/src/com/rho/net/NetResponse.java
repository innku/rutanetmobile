package com.rho.net;

public class NetResponse {
	private String m_strData = null;
	private int    m_nRespCode;
	
	public NetResponse( String strData ){ m_strData = strData; m_nRespCode = -1; }
	public NetResponse( String strData, int nRespCode ){ m_strData = strData; m_nRespCode = nRespCode; }
	
	public String getCharData()
    {
    	return m_strData;
    }
	public void setCharData(String strData)
    {
    	m_strData = strData;
    }
    
	public int getDataSize()
    {
    	return m_strData != null ? m_strData.length() : 0;
    }
	
	public int getRespCode()
	{
		return m_nRespCode;
	}
	
	public boolean isOK()
	{
		return m_nRespCode == IHttpConnection.HTTP_OK;
	}

	public boolean isUnathorized()
	{
		return m_nRespCode == IHttpConnection.HTTP_UNAUTHORIZED;
	}
	
}
