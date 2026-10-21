/*
 * Copyright (C) 2020  Consiglio Nazionale delle Ricerche
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Affero General Public License as
 *     published by the Free Software Foundation, either version 3 of the
 *     License, or (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Affero General Public License for more details.
 *
 *     You should have received a copy of the GNU Affero General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package it.cnr.si.service;

import it.cnr.si.config.DatabaseConfiguration;
import it.cnr.si.domain.sigla.*;
import it.cnr.si.exception.JasperRuntimeException;
import it.cnr.si.repository.ExcelRepository;
import it.cnr.si.repository.ParametriEnteRepository;
import org.apache.commons.io.IOUtils;
import org.apache.poi.hssf.usermodel.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.OptimisticLockException;
import java.io.*;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ExcelService implements InitializingBean {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExcelService.class);
    Integer MAX_RIGHE_FOGLIO = 65530;

    @Value("${file.separator}")
    private String fileSeparator;
    @Value("${print.server.url}")
    private String serverURL;

    @Autowired
    private PrintStorageService storageService;
    @Autowired
    private ParametriEnteRepository parametriEnteRepository;

    @Autowired
    private ExcelRepository excelRepository;
    @Autowired
    private DatabaseConfiguration databaseConfiguration;
    @Autowired
    private MailService mailService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ExcelSpooler print() {
        Long pgEstrazione = excelRepository.findExcelToExecute();
        if (pgEstrazione != null) {
            try {
                ExcelSpooler excelSpooler = excelRepository.findById(pgEstrazione).orElseThrow(() -> new RuntimeException("Cannot find excel spooler with id:" + pgEstrazione));
                if (excelSpooler.canExecute()) {
                    excelSpooler.setStato(PrintState.X);
                    excelSpooler.setDuva(Timestamp.from(ZonedDateTime.now().toInstant()));
                    excelRepository.save(excelSpooler);
                    return excelSpooler;
                } else {
                    LOGGER.warn("Cannot execute excel {} width stato {} and data prossima esecuzione {}",
                            pgEstrazione, excelSpooler.getStato(), excelSpooler.getDtProssimaEsecuzione());
                    return null;
                }
            } catch (OptimisticLockException _ex) {
                LOGGER.warn("Cannot obtain lock pgEstrazione: {}", pgEstrazione, _ex);
            }
        }
        return null;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long executeExcel(ExcelSpooler excelSpooler) {
        String query = excelSpooler.getQuery();
        Connection conn = null;
        Statement statement = null;
        ResultSet rs = null;

        Workbook wb = new XSSFWorkbook(); // Istanzio la classe workbook
        Sheet s = wb.createSheet(excelSpooler.getSheetName()); // creo un foglio
        Row r = null; // dichiaro r di tipo riga
        Cell c = null; // dichiaro c di tipo cella
        s.setDefaultColumnWidth(20);

        CellStyle cellStyle = wb.createCellStyle();
        cellStyle.setAlignment(HorizontalAlignment.CENTER_SELECTION);
        Font font = wb.createFont();
        font.setBold(true);
        cellStyle.setFont(font);

        CellStyle cellHeaderStyle = wb.createCellStyle();
        cellHeaderStyle.setAlignment(HorizontalAlignment.CENTER_SELECTION);
        Font fontHeader = wb.createFont();
        fontHeader.setBold(true);
        fontHeader.setColor(HSSFFont.COLOR_RED);
        cellHeaderStyle.setFont(fontHeader);
        int numSheet = 1;

        int cellnum = -1;
        int rownum = 0;

        try {
            conn = databaseConfiguration.connection();
            if (Optional.ofNullable(excelSpooler.getBeforeStatement()).isPresent()){
                final CallableStatement callableStatement = conn.prepareCall(excelSpooler.getBeforeStatement());
                callableStatement.execute();
            }

            statement = conn.createStatement();
            rs = statement.executeQuery(query);

            rownum = creaIntestazioni(excelSpooler.getExcelSpoolerParams(), cellnum, rownum, s, cellStyle, cellHeaderStyle);
            while (rs.next()) {
                LOGGER.debug("Creazione riga:{}", rownum);
                if (Integer.valueOf(rownum).compareTo(MAX_RIGHE_FOGLIO) > 0) {
                    rownum = 0;
                    numSheet++;
                    s = wb.createSheet(excelSpooler.getSheetName() + " n°" + numSheet);
                    s.setDefaultColumnWidth(20);
                    cellnum = -1;
                    rownum = creaIntestazioni(excelSpooler.getExcelSpoolerParams(), cellnum, rownum, s, cellStyle, cellHeaderStyle);
                }
                r = s.createRow(rownum++);
                cellnum = -1;
                for (ExcelSpoolerParam column : excelSpooler.getExcelSpoolerParams()) {
                    cellnum++;
                    c = r.createCell(cellnum);
                    String valoreRC = rs.getString(
                            Optional.ofNullable(column.getColumnName())
                                    .filter(columnName -> columnName.contains("."))
                                    .map(columnName -> columnName.substring(columnName.lastIndexOf(".") + 1))
                                    .orElseGet(column::getColumnName)
                    );
                    final List<ExcelSpoolerParamColumn> excelSpoolerParamColumns = excelRepository.findExcelSpoolerParamColumns(column);
                    String valoreStringa = excelSpoolerParamColumns.isEmpty() ? valoreRC : valoreRC == null ? null :
                            excelSpoolerParamColumns
                                    .stream()
                                    .filter(espc -> espc.getId().getIdKey().equals(valoreRC))
                                    .filter(espc -> Optional.ofNullable(espc.getValue()).isPresent())
                                    .map(ExcelSpoolerParamColumn::getValue)
                                    .findAny().orElse(valoreRC);
                    if (valoreStringa != null) {
                        if (column.getColumnType() != null) {
                            if (column.getColumnType().equalsIgnoreCase("VARCHAR")) {
                                c.setCellType(CellType.STRING);
                                c.setCellValue(valoreStringa);
                            } else if (column.getColumnType().equalsIgnoreCase("DECIMAL")) {
                                c.setCellType(CellType.NUMERIC);
                                c.setCellValue(new BigDecimal(valoreStringa).doubleValue());
                            } else if (column.getColumnType().equalsIgnoreCase("TIMESTAMP")) {
                                c.setCellType(CellType.STRING);
                                c.setCellValue(valoreStringa);
                            } else {
                                c.setCellType(CellType.STRING);
                                c.setCellValue(valoreStringa);
                            }
                        } else {
                            c.setCellType(CellType.STRING);
                            c.setCellValue(valoreStringa);
                        }
                    }
                }
            }
            List<String> strings = Arrays.asList(excelSpooler.getUtcr(), excelSpooler.getName());
            String collect = String.join(fileSeparator, strings);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            wb.write(baos);
            byte[] byteArray = baos.toByteArray();

            storageService.write(collect, byteArray).thenAccept(aVoid -> {
                final String oldFileName = excelSpooler.getNomeFile();
                if (excelSpooler.getDtProssimaEsecuzione() != null) {
                    GregorianCalendar data_da = (GregorianCalendar) GregorianCalendar.getInstance();
                    data_da.setTime(excelSpooler.getDtProssimaEsecuzione());
                    int addType = Calendar.DATE;
                    if (excelSpooler.getTiIntervallo().equals(TipoIntervallo.G.name()))
                        addType = Calendar.DATE;
                    else if (excelSpooler.getTiIntervallo().equals(TipoIntervallo.S.name()))
                        addType = Calendar.WEEK_OF_YEAR;
                    else if (excelSpooler.getTiIntervallo().equals(TipoIntervallo.M.name()))
                        addType = Calendar.MONTH;
                    data_da.add(addType, excelSpooler.getIntervallo());
                    excelSpooler.setDtProssimaEsecuzione(new Timestamp(data_da.getTimeInMillis()));
                }
                excelSpooler.setStato(PrintState.S);
                excelSpooler.setServer(serverURL.concat("/api/v1/get/excel"));
                excelSpooler.setNomeFile(excelSpooler.getName());
                excelSpooler.setDuva(Timestamp.from(ZonedDateTime.now().toInstant()));
                excelRepository.save(excelSpooler);
                if (excelSpooler.getFlEmail()) {
                    File output = null;
                    try {
                        output = File.createTempFile(collect, null);
                        final InputStream inputStream = storageService.get(collect);
                        try (FileOutputStream out = new FileOutputStream(output)) {
                            IOUtils.copy(inputStream, out);
                            inputStream.close();
                        }
                        Optional.ofNullable(oldFileName)
                                .map(nomeFile -> String.join(fileSeparator, excelSpooler.getUtcr(), nomeFile))
                                .ifPresent(id -> storageService.delete(id));
                        StringBuilder bodyText = new StringBuilder(excelSpooler.getEmailBody() == null ? "" : excelSpooler.getEmailBody());
                        bodyText.append("<html><body bgcolor=\"#ffffff\" text=\"#000000\"><BR><BR><b>Nota di riservatezza:</b><br>");
                        bodyText.append("La presente comunicazione ed i suoi allegati sono di competenza solamente del sopraindicato destinatario. ");
                        bodyText.append("Qualsiasi suo utilizzo, comunicazione o diffusione non autorizzata e' proibita.<br>");
                        bodyText.append("Qualora riceviate detta e-mail per errore, vogliate distruggerla.<br><br>");
                        bodyText.append("<b>Attenzione: </b><br>");
                        bodyText.append("Questa e' una e-mail generata automaticamente da un server non presidiato, La preghiamo di non rispondere. ");
                        bodyText.append("Questa casella di posta elettronica non e' abilitata alla ricezione di messaggi.<br>");
                        if (excelSpooler.getDtProssimaEsecuzione() != null) {
                            StringTokenizer indirizzi = new StringTokenizer(excelSpooler.getEmailA(), ",");
                            bodyText.append("Se non desidera piu' far parte della lista di distribuzione della stampa allegata clicchi ");
                            while (indirizzi.hasMoreElements()) {
                                String indirizzo = (String) indirizzi.nextElement();
                                String messaggio = "<a href=\"https://contab.cnr.it/SIGLA/cancellaSchedulazioneExcel.do?pg=pg" +
                                        String.valueOf(excelSpooler.getPgEstrazione()).trim() + "&indirizzoEMail=" + indirizzo + "\">qui</a> per cancellarsi.<br></body></html>";
                                mailService.send(excelSpooler.getEmailSubject(), bodyText.toString().concat(messaggio), indirizzo,
                                        excelSpooler.getEmailCc(), excelSpooler.getEmailCcn(), output, excelSpooler.getName());
                            }
                        } else {
                            bodyText.append("</body></html>");
                            mailService.send(excelSpooler.getEmailSubject(), bodyText.toString(), excelSpooler.getEmailA(),
                                    excelSpooler.getEmailCc(), excelSpooler.getEmailCcn(), output, excelSpooler.getName());
                        }
                    } catch (Exception ex) {
                        LOGGER.error("Error while sending email for report pgStampa: {}", excelSpooler.getPgEstrazione(), ex);
                    } finally {
                        Optional.ofNullable(output)
                                .ifPresent(File::delete);
                    }
                }
            });

        } catch (SQLException | IOException e) {
            LOGGER.error("Error executing report pgStampa: {}", excelSpooler.getPgEstrazione(), e);
            excelSpooler.setStato(PrintState.E);
            excelSpooler.setDuva(Timestamp.from(ZonedDateTime.now().toInstant()));
            excelSpooler.setErrore(e.getMessage());
            excelRepository.save(excelSpooler);
        } finally {
            try {
                if (rs != null) rs.close();
                if (statement != null) statement.close();
                if (conn != null) conn.close();
            } catch (SQLException e) {
                throw new JasperRuntimeException("unable to process excel", e);
            }
        }
        return excelSpooler.getPgEstrazione();
    }

    private int creaIntestazioni(List<ExcelSpoolerParam> columns, int cellnum, int rownum, Sheet s, CellStyle cellStyle, CellStyle cellHeaderStyle) {
        boolean presenteHeader = false;
        for (ExcelSpoolerParam excelSpoolerParam : columns) {
            if (excelSpoolerParam.getHeaderLabel() != null) {
                presenteHeader = true;
                break;
            }
        }
        Row r = s.createRow(rownum++);
        if (!presenteHeader) {
            for (ExcelSpoolerParam column : columns) {
                cellnum++;
                Cell c = r.createCell(cellnum);
                c.setCellValue(column.getColumnLabel());
                c.setCellStyle(cellStyle);
                c.setCellType(CellType.STRING);
            }
        } else {
            for (ExcelSpoolerParam column : columns) {
                cellnum++;
                if (column.getHeaderLabel() != null) {
                    Cell c = r.createCell(cellnum);
                    c.setCellValue(column.getHeaderLabel());
                    c.setCellStyle(cellHeaderStyle);
                    c.setCellType(CellType.STRING);
                }
            }
            r = s.createRow(rownum++); //creo la seconda
            cellnum = -1;
            for (ExcelSpoolerParam column : columns) {
                cellnum++;
                Cell c = r.createCell(cellnum);
                c.setCellValue(column.getColumnLabel());
                c.setCellStyle(cellStyle);
                c.setCellType(CellType.STRING);
            }
        }
        return rownum;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteXls(Long pgEstrazione) {
        LOGGER.info("Try to delete excel pgEstrazione: {}", pgEstrazione);
        ExcelSpooler excelSpooler = excelRepository.findById(pgEstrazione).orElseThrow(() -> new RuntimeException("Cannot find excel spooler with id:" + pgEstrazione));
        String path = String.join(fileSeparator, excelSpooler.getUtcr(), excelSpooler.getName());

        storageService.delete(path);

        excelRepository.delete(excelSpooler);
    }

    public void deleteXls() {
        LocalDateTime localDateTime = LocalDateTime.now().minusDays(Optional.ofNullable(parametriEnteRepository.findCancellaStampe())
                .orElse(BigDecimal.valueOf(30)).longValue());
        Iterable<Long> findXlsToDelete = excelRepository.findXlsToDelete(Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant()));
        for (Long pgEstrazione : findXlsToDelete) {
            deleteXls(pgEstrazione);
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        serverURL = Optional.ofNullable(serverURL)
                .filter(s -> s.equalsIgnoreCase("http://localhost:8080"))
                .map(s -> {
                    try {
                        final String hostAddress = InetAddress.getLocalHost().getHostName();
                        return "http://".concat(hostAddress).concat(":8080");
                    } catch (UnknownHostException e) {
                        return s;
                    }
                })
                .orElse(serverURL);
        LOGGER.info("Server URL: {}", serverURL);
    }
}
