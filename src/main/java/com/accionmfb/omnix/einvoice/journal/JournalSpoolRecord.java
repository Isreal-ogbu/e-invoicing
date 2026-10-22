package com.accionmfb.omnix.einvoice.journal;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import java.time.LocalDate;

@Entity
@Table(name = "einvoice_journal_spool", uniqueConstraints = @UniqueConstraint(name = "uk_einvoice_journal_source", columnNames = {"source_key"}))
public class JournalSpoolRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "source_key", nullable = false, length = 180)
    private String sourceKey;
    @Column(name = "journal_date", nullable = false)
    private LocalDate journalDate;
    @Column(name = "ft_reference", length = 100)
    private String ftReference;
    @Column(name = "transaction_code", length = 30)
    private String transactionCode;
    @Column(name = "credit_amount", length = 40)
    private String creditAmount;
    @Column(name = "debit_amount", length = 40)
    private String debitAmount;
    @Column(name = "customer_number", length = 40)
    private String customerNumber;
    @Column(name = "raw_record", nullable = false, columnDefinition = "TEXT")
    private String rawRecord;

    protected JournalSpoolRecord() { }

    public JournalSpoolRecord(String sourceKey, LocalDate journalDate, String ftReference, String transactionCode,
                              String creditAmount, String debitAmount, String customerNumber, String rawRecord) {
        this.sourceKey = sourceKey;
        this.journalDate = journalDate;
        this.ftReference = ftReference;
        this.transactionCode = transactionCode;
        this.creditAmount = creditAmount;
        this.debitAmount = debitAmount;
        this.customerNumber = customerNumber;
        this.rawRecord = rawRecord;
    }
    public Long getId() { return id; }
    public String getSourceKey() { return sourceKey; }
    public LocalDate getJournalDate() { return journalDate; }
    public String getFtReference() { return ftReference; }
    public String getTransactionCode() { return transactionCode; }
    public String getCreditAmount() { return creditAmount; }
    public String getDebitAmount() { return debitAmount; }
    public String getCustomerNumber() { return customerNumber; }
    public String getRawRecord() { return rawRecord; }
}
