package it.cnr.si.domain.sigla;

import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Version;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@Entity
@Table(name="PRINT_SPOOLER")
public class PrintSpooler {
	private static final String JRXML = ".jrxml";
	private static final String JASPER = ".jasper";
	public static final java.text.DateFormat DATE_FORMAT = new java.text.SimpleDateFormat("yyyy/MM/dd");
	public static final java.text.DateFormat TIMESTAMP_FORMAT = new java.text.SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
	public static final DateFormat PDF_DATE_FORMAT = new SimpleDateFormat("yyyyMMdd");
	private static final Logger LOGGER = LoggerFactory.getLogger(PrintSpooler.class);

	/**
	 * PG_STAMPA NUMBER (10) {null} NOT NULL Progressivo della stampa
	 */
	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)	
	@Column(name="PG_STAMPA")
	private java.lang.Long pgStampa;
	/**
	 * DS_STAMPA VARCHAR2 (300) {null} {null} Descrizione della stampa (Letta da
	 * PRINT_PRIORITY)
	 */
	@Column(name="DS_STAMPA")
	private java.lang.String dsStampa;
	/**
	 * DS_UTENTE VARCHAR2 (300) {null} {null} Descrizione utente della stampa
	 */
	@Column(name="DS_UTENTE")
	private java.lang.String dsUtente;
	/**
	 * DT_SCADENZA DATE {null} {null} Data di scadenza
	 */
	@Column(name="DT_SCADENZA")
	private java.sql.Timestamp dtScadenza;
	/**
	 * ID_REPORT_GENERICO NUMBER {null} {null} Codice del report generico
	 * contenente i dati della stampa
	 */
	@Column(name="ID_REPORT_GENERICO")
	private java.math.BigDecimal idReportGenerico;
	/**
	 * INTERVALLO_FINE NUMBER (4) {null} {null} Attualmente non usato
	 */
	@Column(name="INTERVALLO_FINE")
	private java.lang.Integer intervalloFine;
	/**
	 * INTERVALLO_INIZIO NUMBER (4) {null} {null} Attualmente non usato
	 */
	@Column(name="INTERVALLO_INIZIO")
	private java.lang.Integer intervalloInizio;
	/**
	 * NOME_FILE VARCHAR2 (300) {null} {null} Nome del file PDF contenente la
	 * stampa realizzata
	 */
	@Column(name="NOME_FILE")
	private java.lang.String nomeFile;
	/**
	 * PRIORITA NUMBER (1) 9 NOT NULL Attualmente fixed a 0 ma potrebbe essere
	 * utilizato per indicare la prioritÃ  delle stampe appartenenti ad una data
	 * PRIORITA_SERVER
	 */
	@Column(name="PRIORITA")
	private java.lang.Integer priorita;
	/**
	 * PRIORITA_SERVER NUMBER (1) 0 NOT NULL PrioritÃ  della stampa 0-9 0 ->
	 * Massima prioritÃ  9 -> Minima prioritÃ  Deve esistere un server di stampa
	 * che ascolta richieste di stampa di una data prioritÃ  perchÃ¨ queste
	 * possano essere processate
	 */
	@Column(name="PRIORITA_SERVER")
	private java.lang.Integer prioritaServer;
	/**
	 * REPORT VARCHAR2 (300) {null} {null} Report Jasper da eseguire
	 */
	@Column(name="REPORT")
	private java.lang.String report;
	/**
	 * SERVER VARCHAR2 (200) {null} {null} Viene riempito dal print server con
	 * l'URL da cui recuperare la stampa realizzata in formato PDF
	 */
	@Column(name="SERVER")
	private java.lang.String server;
	/**
	 * DT_PROSSIMA_ESECUZIONE DATE {null} {null} Data di prossima esecuzione del
	 * batch
	 */
	@Column(name="DT_PROSSIMA_ESECUZIONE")
	private java.sql.Timestamp dtProssimaEsecuzione;
	/**
	 * STATO CHAR (1) {null} {null} C - In coda, X - In esecuzione, E - Errore,
	 * S - Eseguita
	 */
	@Column(name="STATO")
	@Enumerated(EnumType.STRING)
	private PrintState stato;
	/**
	 * TI_VISIBILITA CHAR (1) {null} NOT NULL U - Utente, C - CDR, O - Unita
	 * org., S - Cds, P - Publica, N - CNR
	 */
	@Column(name="TI_VISIBILITA")
	@Enumerated(EnumType.STRING)
	private PrintVisibility tiVisibilita;
	/**
	 * ERRORE VARCHAR2 (4000) {null} {null} Descrizione errore dei Report
	 */
	@Column(name="ERRORE")
	private String errore;
	/**
	 * FL_EMAIL CHAR(1) Indica se inviare l'E-Mail con la stampa allegata.
	 * Dominio: N = Invia E-Mail Y = Non inviare E-Mail
	 */
	@Column(name="FL_EMAIL")
	private String flEmail;
	/**
	 * EMAIL_A VARCHAR2(250) Destinatario dell'E-Mail
	 */
	@Column(name="EMAIL_A")
	private String emailA;
	/**
	 * EMAIL_CC VARCHAR2 (250) Destinatario per conoscenza dell'E-Mail
	 */
	@Column(name="EMAIL_CC")
	private String emailCc;
	/**
	 * EMAIL_CCN VARCHAR2 (250) Destinatario per conoscenza nascosta dell'E-Mail
	 */
	@Column(name="EMAIL_CCN")
	private String emailCcn;
	/**
	 * EMAIL_SUBJECT VARCHAR2 (250) Oggetto dell'E-Mail
	 */
	@Column(name="EMAIL_SUBJECT")
	private String emailSubject;
	/**
	 * EMAIL_BODY VARCHAR2 (4000) Corpo del testo dell'E-Mail
	 */
	@Column(name="EMAIL_BODY")
	private String emailBody;
	/**
	 * DT_PARTENZA DATE {null} {null} Data di inizio esecuzione del batch
	 */
	@Column(name="DT_PARTENZA")
	private java.sql.Timestamp dtPartenza;
	/**
	 * INTERVALLO NUMBER (10) {null} {null} Intervallo di esecuzione del batch;
	 * in giorni
	 */
	@Column(name="INTERVALLO")
	private Integer intervallo;
	/**
	 * TI_INTERVALLO CHAR (1) {null} {null} Tipo Intervallo di esecuzione del
	 * batch, G - GIORNI, S - SETTIMANE, M - MESI
	 */
	@Column(name="TI_INTERVALLO")
	@Enumerated(EnumType.STRING)
	private TipoIntervallo tiIntervallo;

	@Column(name="dacr", nullable=false)
	private Date dacr;

	@Column(name="utcr", nullable=false)
	private String utcr;

	@Column(name="duva", nullable=false)
	private Date duva;

	@Column(name="utuv", nullable=false)
	private String utuv;

    @Version
    private Long pg_ver_rec;
	
	@OneToMany(fetch=FetchType.EAGER)
	@JoinColumn(name = "PG_STAMPA")	
	private Set<PrintSpoolerParam> params;

	public PrintSpooler() {
		super();
	}

	public PrintSpooler(Long pgStampa) {
		super();
		this.pgStampa = pgStampa;
	}

	public java.lang.Long getPgStampa() {
		return pgStampa;
	}

	public void setPgStampa(java.lang.Long pgStampa) {
		this.pgStampa = pgStampa;
	}

	public java.lang.String getDsStampa() {
		return dsStampa;
	}

	public void setDsStampa(java.lang.String dsStampa) {
		this.dsStampa = dsStampa;
	}

	public java.lang.String getDsUtente() {
		return dsUtente;
	}

	public void setDsUtente(java.lang.String dsUtente) {
		this.dsUtente = dsUtente;
	}

	public java.sql.Timestamp getDtScadenza() {
		return dtScadenza;
	}

	public void setDtScadenza(java.sql.Timestamp dtScadenza) {
		this.dtScadenza = dtScadenza;
	}

	public java.math.BigDecimal getIdReportGenerico() {
		return idReportGenerico;
	}

	public void setIdReportGenerico(java.math.BigDecimal idReportGenerico) {
		this.idReportGenerico = idReportGenerico;
	}

	public java.lang.Integer getIntervalloFine() {
		return intervalloFine;
	}

	public void setIntervalloFine(java.lang.Integer intervalloFine) {
		this.intervalloFine = intervalloFine;
	}

	public java.lang.Integer getIntervalloInizio() {
		return intervalloInizio;
	}

	public void setIntervalloInizio(java.lang.Integer intervalloInizio) {
		this.intervalloInizio = intervalloInizio;
	}

	public java.lang.String getNomeFile() {
		return nomeFile;
	}

	public void setNomeFile(java.lang.String nomeFile) {
		this.nomeFile = nomeFile;
	}

	public java.lang.Integer getPriorita() {
		return priorita;
	}

	public void setPriorita(java.lang.Integer priorita) {
		this.priorita = priorita;
	}

	public java.lang.Integer getPrioritaServer() {
		return prioritaServer;
	}

	public void setPrioritaServer(java.lang.Integer prioritaServer) {
		this.prioritaServer = prioritaServer;
	}

	public java.lang.String getReport() {
		return report;
	}

	public void setReport(java.lang.String report) {
		this.report = report;
	}

	public java.lang.String getServer() {
		return server;
	}

	public void setServer(java.lang.String server) {
		this.server = server;
	}

	public PrintState getStato() {
		return stato;
	}

	public void setStato(PrintState stato) {
		this.stato = stato;
	}

	public PrintVisibility getTiVisibilita() {
		return tiVisibilita;
	}

	public void setTiVisibilita(PrintVisibility tiVisibilita) {
		this.tiVisibilita = tiVisibilita;
	}

	public java.sql.Timestamp getDtProssimaEsecuzione() {
		return dtProssimaEsecuzione;
	}

	public void setDtProssimaEsecuzione(java.sql.Timestamp dtProssimaEsecuzione) {
		this.dtProssimaEsecuzione = dtProssimaEsecuzione;
	}

	public String getErrore() {
		return errore;
	}

	public void setErrore(String errore) {
		this.errore = errore;
	}

	public Boolean getFlEmail() {
		return flEmail.equals("Y");
	}

	public void setFlEmail(String flEmail) {
		this.flEmail = flEmail;
	}

	public void setFlEmail(Boolean flEmail) {
		this.flEmail = flEmail?"Y":"N";
	}

	public String getEmailA() {
		return emailA;
	}

	public void setEmailA(String emailA) {
		this.emailA = emailA;
	}

	public String getEmailCc() {
		return emailCc;
	}

	public void setEmailCc(String emailCc) {
		this.emailCc = emailCc;
	}

	public String getEmailCcn() {
		return emailCcn;
	}

	public void setEmailCcn(String emailCcn) {
		this.emailCcn = emailCcn;
	}

	public String getEmailSubject() {
		return emailSubject;
	}

	public void setEmailSubject(String emailSubject) {
		this.emailSubject = emailSubject;
	}

	public String getEmailBody() {
		return emailBody;
	}

	public void setEmailBody(String emailBody) {
		this.emailBody = emailBody;
	}

	public java.sql.Timestamp getDtPartenza() {
		return dtPartenza;
	}

	public void setDtPartenza(java.sql.Timestamp dtPartenza) {
		this.dtPartenza = dtPartenza;
	}

	public Integer getIntervallo() {
		return intervallo;
	}

	public void setIntervallo(Integer intervallo) {
		this.intervallo = intervallo;
	}

	public TipoIntervallo getTiIntervallo() {
		return tiIntervallo;
	}

	public void setTiIntervallo(TipoIntervallo tiIntervallo) {
		this.tiIntervallo = tiIntervallo;
	}

	
	public Date getDacr() {
		return dacr;
	}

	public void setDacr(Date dacr) {
		this.dacr = dacr;
	}

	public String getUtcr() {
		return utcr;
	}

	public void setUtcr(String utcr) {
		this.utcr = utcr;
	}

	public Date getDuva() {
		return duva;
	}

	public void setDuva(Date duva) {
		this.duva = duva;
	}

	public String getUtuv() {
		return utuv;
	}

	public void setUtuv(String utuv) {
		this.utuv = utuv;
	}

	public Long getPg_ver_rec() {
		return pg_ver_rec;
	}

	public Set<PrintSpoolerParam> getParams() {
		return params;
	}

	public void setParams(Set<PrintSpoolerParam> params) {
		this.params = params;
	}

	public String getKey() {
		return getReport().substring(0, getReport().indexOf(JASPER)).concat(JRXML);
	}	
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public HashMap<String, Object> getParameters() {
		HashMap<String, Object> parameters = new HashMap<String, Object>();
		for (it.cnr.si.domain.sigla.PrintSpoolerParam printSpoolerParam : getParams()) {
			Serializable valoreParametro = null;
			try{
				Class classe = Class.forName(printSpoolerParam.getParamType());
				if (classe.equals(java.util.Date.class)){
					valoreParametro = DATE_FORMAT.parse(printSpoolerParam.getValoreParam());
				}else if (classe.equals(java.util.Date.class)){
					valoreParametro = TIMESTAMP_FORMAT.parse(printSpoolerParam.getValoreParam());
				}else{
					Constructor costr =  classe.getConstructor(String.class);
					valoreParametro = (Serializable) costr.newInstance(printSpoolerParam.getValoreParam());
				}
			}catch(ClassCastException _ex){
				valoreParametro = printSpoolerParam.getValoreParam();
			}catch(Exception _ex){
				LOGGER.error("Error in parameter conversion", _ex);
			}
			parameters.put(printSpoolerParam.getKey().getNomeParam(), valoreParametro);
		}
		parameters.put("DIR_IMAGE", "/img/");        
		parameters.put("DIR_SUBREPORT", getPath());
		parameters.put("SUBREPORT_DIR", getPath());
		return parameters;
	}
	
	public String getPath() {
		return getReport().substring(0, getReport().lastIndexOf("/") + 1);
	}

	public String getName() {
    	String fileName = getReport();
    	fileName = fileName.replace('/', '_');
    	fileName = fileName.replace('\\', '_');
    	if(fileName.startsWith("_"))
    		fileName = fileName.substring(1);
    	if(fileName.endsWith(JASPER))
    		fileName = fileName.substring(0, fileName.length() - 7);
    	fileName = fileName + ".pdf";
    	fileName = PDF_DATE_FORMAT.format(new java.util.Date()) + '_' + getPgStampa() + '_' + fileName;
    	return fileName;
	}
}