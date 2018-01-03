package it.cnr.si.service;

import it.cnr.si.config.DatabaseConfiguration;
import it.cnr.si.domain.sigla.ExcelSpooler;
import it.cnr.si.domain.sigla.ExcelSpoolerParam;
import it.cnr.si.domain.sigla.PrintState;
import it.cnr.si.domain.sigla.TipoIntervallo;
import it.cnr.si.exception.JasperRuntimeException;
import it.cnr.si.repository.ExcelRepository;
import org.apache.commons.io.IOUtils;
import org.apache.poi.hssf.usermodel.*;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import javax.persistence.OptimisticLockException;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.OptimisticLockException;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.sql.*;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.Date;
import java.util.stream.Collectors;

@Service
public class ExcelService {
	private static final Logger LOGGER = LoggerFactory.getLogger(ExcelService.class);	
	Integer MAX_RIGHE_FOGLIO = new Integer(65530);
	
	@Value("${file.separator}")
	private String fileSeparator;	
	@Value("${print.server.url}")
	private String serverURL;

	@Autowired
	private PrintStorageService storageService;

	@Autowired
	private ExcelRepository excelRepository;
	@Autowired
	private DatabaseConfiguration databaseConfiguration;
	@Autowired
	private MailService mailService;
	
	@Transactional(propagation=Propagation.REQUIRES_NEW)
	public ExcelSpooler print() {
		Long pgEstrazione = excelRepository.findExcelToExecute();    
		if (pgEstrazione != null) {
			try {
				ExcelSpooler excelSpooler = excelRepository.findOne(pgEstrazione);
				excelSpooler.setStato(PrintState.X.name());
				excelSpooler.setDuva(Date.from(ZonedDateTime.now().toInstant()));
				excelRepository.save(excelSpooler);
				return excelSpooler;
			} catch (OptimisticLockException _ex) {
				LOGGER.warn("Cannot obtain lock pgEstrazione: {}", pgEstrazione, _ex);
			}			
		}
		return null;
	}

	@Transactional(propagation=Propagation.REQUIRES_NEW)	
	public Long executeExcel(ExcelSpooler excelSpooler) {
		String query = excelSpooler.getQuery();
		Connection conn = null;
		Statement statement = null;
		ResultSet rs = null;

		HSSFWorkbook wb = new HSSFWorkbook(); // Istanzio la classe workbook
		HSSFSheet s = wb.createSheet(excelSpooler.getSheetName()); // creo un foglio
		HSSFRow r = null; // dichiaro r di tipo riga
		HSSFCell c = null; // dichiaro c di tipo cella
		s.setDefaultColumnWidth((short)20);
		HSSFCellStyle cellStyle = wb.createCellStyle();
		cellStyle.setAlignment(HSSFCellStyle.ALIGN_CENTER_SELECTION);
		HSSFFont font = wb.createFont();
		font.setBoldweight(HSSFFont.BOLDWEIGHT_BOLD);
		cellStyle.setFont(font);
			  
		HSSFCellStyle cellHeaderStyle = wb.createCellStyle();
		cellHeaderStyle.setAlignment(HSSFCellStyle.ALIGN_CENTER_SELECTION);
		HSSFFont fontHeader = wb.createFont();
		fontHeader.setBoldweight(HSSFFont.BOLDWEIGHT_BOLD);
		fontHeader.setColor(HSSFFont.COLOR_RED);
		cellHeaderStyle.setFont(fontHeader);
		int numSheet = 1;
		
		short cellnum = (short) -1;
		int rownum = 0;

		try {
			conn = databaseConfiguration.connection();
			statement = conn.createStatement();
			rs = statement.executeQuery(query);

			rownum = creaIntestazioni(excelSpooler.getExcelSpoolerParams(), cellnum, rownum, s, cellStyle, cellHeaderStyle);
			while(rs.next()) {
				LOGGER.debug("Creazione riga:{}", rownum);
				if (new Integer(rownum).compareTo(MAX_RIGHE_FOGLIO) > 0){
					rownum = 0;
					numSheet++;
					s = wb.createSheet(excelSpooler.getSheetName() + " n°" + numSheet);
					s.setDefaultColumnWidth((short)20);
					cellnum = (short) -1;
					rownum = creaIntestazioni(excelSpooler.getExcelSpoolerParams(), cellnum, rownum, s, cellStyle, cellHeaderStyle);
				}
				r = s.createRow(rownum++);
				cellnum = (short) -1;
				for (ExcelSpoolerParam column : excelSpooler.getExcelSpoolerParams()) {
					cellnum++;
					c = r.createCell((short)cellnum);
                    String valoreRC = rs.getString(
                            Optional.ofNullable(column.getColumnName())
                                    .filter(columnName -> columnName.indexOf(".") != -1)
                                    .map(columnName -> columnName.substring(columnName.lastIndexOf(".") + 1))
                                    .orElseGet(() -> column.getColumnName())
                    );
					String valoreStringa = column.getExcelSpoolerParamColumns().isEmpty()? valoreRC : valoreRC==null ? valoreRC:
						(String)column.getParamColumns(valoreRC);
					if(valoreStringa != null){
					 if (column.getColumnType() != null){
						if (column.getColumnType().equalsIgnoreCase("VARCHAR")){								   
						  c.setCellType(HSSFCell.CELL_TYPE_STRING);
						  c.setCellValue(new HSSFRichTextString(valoreStringa));
						}else if (column.getColumnType().equalsIgnoreCase("DECIMAL")){								   
						  c.setCellType(HSSFCell.CELL_TYPE_NUMERIC);
						  c.setCellValue(new BigDecimal(valoreStringa).doubleValue());
						}else if (column.getColumnType().equalsIgnoreCase("TIMESTAMP")){								   
						  c.setCellType(HSSFCell.CELL_TYPE_STRING);
						  c.setCellValue(new HSSFRichTextString(valoreStringa));
						}else{								   
						  c.setCellType(HSSFCell.CELL_TYPE_STRING);
						  c.setCellValue(new HSSFRichTextString(valoreStringa));
						}    						 											
					 }else{
						c.setCellType(HSSFCell.CELL_TYPE_STRING);
						c.setCellValue(new HSSFRichTextString(valoreStringa));						 	
					 }
					}  
				}
			}
			List<String> strings = Arrays.asList(excelSpooler.getUtcr(), excelSpooler.getName());
			String collect = strings.stream().collect(Collectors.joining(fileSeparator));

			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			wb.write(baos);
			byte[] byteArray = baos.toByteArray();

			storageService.write(collect, byteArray);

			File output = File.createTempFile(collect, null);
			FileWriter fileWriter = new FileWriter(output);
			IOUtils.write(byteArray, fileWriter, Charset.defaultCharset());
			fileWriter.flush();
			fileWriter.close();

			if (excelSpooler.getDtProssimaEsecuzione() != null){
                GregorianCalendar data_da = (GregorianCalendar) GregorianCalendar.getInstance();
                data_da.setTime(excelSpooler.getDtProssimaEsecuzione());
                int addType = Calendar.DATE;
                if (excelSpooler.getTiIntervallo().equals(TipoIntervallo.G))
                	addType = Calendar.DATE;
                else if (excelSpooler.getTiIntervallo().equals(TipoIntervallo.S))
                	addType = Calendar.WEEK_OF_YEAR;
                else if (excelSpooler.getTiIntervallo().equals(TipoIntervallo.M))
                	addType = Calendar.MONTH;
                data_da.add(addType, excelSpooler.getIntervallo());
                excelSpooler.setDtProssimaEsecuzione(new Timestamp(data_da.getTimeInMillis()));
	        }			
			excelSpooler.setStato(PrintState.S.name());
			excelSpooler.setServer(serverURL.concat("/api/v1/get/excel"));
			excelSpooler.setNomeFile(excelSpooler.getName());
			excelSpooler.setDuva(Date.from(ZonedDateTime.now().toInstant()));
			excelRepository.save(excelSpooler);
            if (excelSpooler.getFlEmail()){
            	try {
                	StringBuffer bodyText = new StringBuffer(excelSpooler.getEmailBody()==null?"":excelSpooler.getEmailBody());
                	bodyText.append("<html><body bgcolor=\"#ffffff\" text=\"#000000\"><BR><BR><b>Nota di riservatezza:</b><br>");
                	bodyText.append("La presente comunicazione ed i suoi allegati sono di competenza solamente del sopraindicato destinatario. ");
                	bodyText.append("Qualsiasi suo utilizzo, comunicazione o diffusione non autorizzata e' proibita.<br>");
                	bodyText.append("Qualora riceviate detta e-mail per errore, vogliate distruggerla.<br><br>");
                	bodyText.append("<b>Attenzione: </b><br>");
                	bodyText.append("Questa e' una e-mail generata automaticamente da un server non presidiato, La preghiamo di non rispondere. ");
                	bodyText.append("Questa casella di posta elettronica non e' abilitata alla ricezione di messaggi.<br>");
                	if (excelSpooler.getDtProssimaEsecuzione() != null){
                		StringTokenizer indirizzi = new StringTokenizer(excelSpooler.getEmailA(),",");
            			bodyText.append("Se non desidera piu' far parte della lista di distribuzione della stampa allegata clicchi ");
                		while(indirizzi.hasMoreElements()){
                			String indirizzo = (String)indirizzi.nextElement();
                			String messaggio = "<a href=\"https://contab.cnr.it/SIGLA/cancellaSchedulazioneExcel.do?pg=pg"+
                					String.valueOf(excelSpooler.getPgEstrazione()).trim()+"&indirizzoEMail="+indirizzo+"\">qui</a> per cancellarsi.<br></body></html>";
                			mailService.send(excelSpooler.getEmailSubject(), bodyText.toString().concat(messaggio), indirizzo, 
                					excelSpooler.getEmailCc(), excelSpooler.getEmailCcn(), output, excelSpooler.getName());
                		}
                	}else{
                    	bodyText.append("</body></html>");
            			mailService.send(excelSpooler.getEmailSubject(), bodyText.toString(), excelSpooler.getEmailA(), 
            					excelSpooler.getEmailCc(), excelSpooler.getEmailCcn(), output, excelSpooler.getName());
                	}            		
            	} catch (Exception ex) {
            		LOGGER.error("Error while sending email for report pgStampa: {}", excelSpooler.getPgEstrazione(), ex);
            	}
            }
		} catch (SQLException | IOException e) {
			LOGGER.error("Error executing report pgStampa: {}", excelSpooler.getPgEstrazione(), e);
			excelSpooler.setStato(PrintState.E.name());
			excelSpooler.setDuva(Date.from(ZonedDateTime.now().toInstant()));
			excelSpooler.setErrore(e.getMessage());
			excelRepository.save(excelSpooler);			
		}finally {
			try {
				rs.close();
				statement.close();
				conn.close();
			} catch (SQLException e) {
				throw new JasperRuntimeException("unable to process excel", e);
			}
		}
		return excelSpooler.getPgEstrazione();
	}

	private int creaIntestazioni(List<ExcelSpoolerParam> columns, short cellnum, int rownum, HSSFSheet s, HSSFCellStyle cellStyle, HSSFCellStyle cellHeaderStyle){
		boolean presenteHeader = false;
		for (ExcelSpoolerParam excelSpoolerParam : columns) {
			if (excelSpoolerParam.getHeaderLabel() != null){
				presenteHeader = true;
				break;
			}				
		}
		HSSFRow r = s.createRow(rownum++);
		if(!presenteHeader){
			for (ExcelSpoolerParam column : columns) {
				cellnum++;
				HSSFCell c = r.createCell(cellnum);
				c.setCellValue(new HSSFRichTextString(column.getColumnLabel()));
				c.setCellStyle(cellStyle);
				c.setCellType(HSSFCell.CELL_TYPE_STRING);
			}			  		
		}else{
			for (ExcelSpoolerParam column : columns) {
				cellnum++;
				if(column.getHeaderLabel() != null){
					HSSFCell c = r.createCell(cellnum);
					c.setCellValue(new HSSFRichTextString(column.getHeaderLabel()));
					c.setCellStyle(cellHeaderStyle);
					c.setCellType(HSSFCell.CELL_TYPE_STRING);						
				}
		  }
		  r = s.createRow(rownum++); //creo la seconda
		  cellnum = (short) -1;
		  for (ExcelSpoolerParam column : columns) {
			  cellnum++;
			  HSSFCell c = r.createCell(cellnum);
			  c.setCellValue(new HSSFRichTextString(column.getColumnLabel()));
			  c.setCellStyle(cellStyle);
			  c.setCellType(HSSFCell.CELL_TYPE_STRING);
		  }			  		
		} 
		return rownum;   	
    }
	
	@Transactional(propagation=Propagation.REQUIRES_NEW)	
	public void deleteXls(Long pgEstrazione) {
		LOGGER.info("Try to delete excel pgEstrazione: {}", pgEstrazione);
		ExcelSpooler excelSpooler = excelRepository.findOne(pgEstrazione);
        String path = Arrays.asList(excelSpooler.getUtcr(), excelSpooler.getName()).stream().collect(Collectors.joining(fileSeparator));

        storageService.delete(path);

        excelRepository.delete(excelSpooler);
	}
	
	public void deleteXls() {
    	Iterable<Long> findXlsToDelete = excelRepository.findXlsToDelete();
    	for (Long pgEstrazione : findXlsToDelete) {
    		deleteXls(pgEstrazione);
		}		
	}
}
