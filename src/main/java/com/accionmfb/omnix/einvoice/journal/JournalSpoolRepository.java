package com.accionmfb.omnix.einvoice.journal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface JournalSpoolRepository extends JpaRepository<JournalSpoolRecord, Long> {
    List<JournalSpoolRecord> findByJournalDate(LocalDate journalDate);
    List<JournalSpoolRecord> findByJournalDateOrderByIdAsc(LocalDate journalDate);
    List<JournalSpoolRecord> findByJournalDateAndFtReferenceIgnoreCaseOrderByIdAsc(LocalDate journalDate, String ftReference);
}
