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
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_key", nullable = false, length = 220)
    private String sourceKey;

    @Column(name = "journal_date", nullable = false)
    private LocalDate journalDate;

    @Column(name = "ft_reference", length = 100)
    private String ftReference;

    @Column(name = "transaction_code", length = 30)
    private String transactionCode;

    @Column(name = "account_number", length = 60)
    private String accountNumber;

    @Column(name = "account_name", length = 180)
    private String accountName;

    @Column(name = "credit_amount", length = 40)
    private String creditAmount;

    @Column(name = "debit_amount", length = 40)
    private String debitAmount;

    @Column(name = "customer_number", length = 40)
    private String customerNumber;

    @Column(name = "narration", length = 500)
    private String narration;

    @Column(name = "line_number")
    private Integer lineNumber;

    @Column(name = "raw_record", nullable = false, columnDefinition = "TEXT")
    private String rawRecord;

    protected JournalSpoolRecord() { }

    public JournalSpoolRecord(String sourceKey, LocalDate journalDate, String ftReference, String transactionCode,
                              String accountNumber, String accountName, String creditAmount, String debitAmount,
                              String customerNumber, String narration, Integer lineNumber, String rawRecord) {
        this.sourceKey = sourceKey;
        this.journalDate = journalDate;
        this.ftReference = ftReference;
        this.transactionCode = transactionCode;
        this.accountNumber = accountNumber;
        this.accountName = accountName;
        this.creditAmount = creditAmount;
        this.debitAmount = debitAmount;
        this.customerNumber = customerNumber;
        this.narration = narration;
        this.lineNumber = lineNumber;
        this.rawRecord = rawRecord;
    }

    public Long getId() { return id; }
    public String getSourceKey() { return sourceKey; }
    public LocalDate getJournalDate() { return journalDate; }
    public String getFtReference() { return ftReference; }
    public String getTransactionCode() { return transactionCode; }
    public String getAccountNumber() { return accountNumber; }
    public String getAccountName() { return accountName; }
    public String getCreditAmount() { return creditAmount; }
    public String getDebitAmount() { return debitAmount; }
    public String getCustomerNumber() { return customerNumber; }
    public String getNarration() { return narration; }
    public Integer getLineNumber() { return lineNumber; }
    public String getRawRecord() { return rawRecord; }
}
