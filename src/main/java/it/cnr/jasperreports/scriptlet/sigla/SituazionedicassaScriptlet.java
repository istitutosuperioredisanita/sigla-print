package it.cnr.jasperreports.scriptlet.sigla;
import it.cnr.jasperreports.scriptlet.SIGLAScriptlet;
import net.sf.jasperreports.engine.*;


public class SituazionedicassaScriptlet extends SIGLAScriptlet {
    
	/** Creates a new instance of JRIreportDefaultScriptlet */
	public SituazionedicassaScriptlet() {
	}
	
	public void beforeReportInit() throws JRScriptletException{
		java.sql.Connection conn = (java.sql.Connection)getParameterValue("REPORT_CONNECTION");
		java.sql.CallableStatement cs = null; 
		try{	        	
			cs = conn.prepareCall("{call PRT_S_SIT_CASSA_J(?,?,?,?,?,?)}");
			cs.setObject(1, (java.lang.Integer)getParameterValue("inEs"));
			cs.setObject(2,(java.lang.String)getParameterValue("CDS"));
			cs.setObject(3,(java.lang.String)getParameterValue("uo"));
			cs.setObject(4,(java.lang.String)getParameterValue("EM_INV_RIS"));
			cs.setObject(5,(new java.text.SimpleDateFormat("yyyy/MM/dd")).format((java.util.Date)getParameterValue("DA_DATA")));
			cs.setObject(6,(new java.text.SimpleDateFormat("yyyy/MM/dd")).format((java.util.Date)getParameterValue("A_DATA")));
			cs.executeQuery();
		}catch (Throwable e) {
			throw new JRScriptletException(e.getMessage());
		} finally {
			if (cs != null) 
				try {
					cs.close();
				} catch (java.sql.SQLException e1) {
					throw new JRScriptletException(e1.getMessage());
				}
		}		
	}
}