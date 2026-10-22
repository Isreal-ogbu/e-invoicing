package com.accionmfb.omnix.einvoice.invoice;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EInvoiceDocumentRepository extends JpaRepository<EInvoiceDocument, Long> {
    Optional<EInvoiceDocument> findByInvoiceNumber(String invoiceNumber);
    List<EInvoiceDocument> findByJournalDateOrderByIdAsc(LocalDate journalDate);
}
