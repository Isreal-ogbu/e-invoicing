package com.accionmfb.omnix.einvoice.journal;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface JournalSpoolRepository extends JpaRepository<JournalSpoolRecord, Long> {
    Optional<JournalSpoolRecord> findBySourceKey(String sourceKey);
    List<JournalSpoolRecord> findByJournalDateOrderByIdAsc(LocalDate journalDate);
    List<JournalSpoolRecord> findByJournalDate(LocalDate journalDate);
}
