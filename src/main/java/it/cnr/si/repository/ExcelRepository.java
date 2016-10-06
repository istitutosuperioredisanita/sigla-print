package it.cnr.si.repository;

import it.cnr.si.domain.sigla.ExcelSpooler;

import java.util.Date;

import javax.persistence.LockModeType;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Created by francesco on 12/09/16.
 */
@Repository
public interface ExcelRepository extends CrudRepository<ExcelSpooler, Long> {
	@Query("SELECT MIN(pgEstrazione) FROM ExcelSpooler WHERE "
			+ "((stato = 'C' and dtProssimaEsecuzione is null) or (stato IN ('C', 'S') and dtProssimaEsecuzione BETWEEN ?1 AND ?2 ))")	
	public Long findExcelToExecute(@Param("dataInizio")Date dataInizio, @Param("dataFine")Date dataFine);
			
	@Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ExcelSpooler p where p.pgEstrazione = :pgEstrazione")
	ExcelSpooler findOneForUpdate(@Param("pgEstrazione") Long pgEstrazione);
}