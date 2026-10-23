package com.accionmfb.omnix.einvoice.invoice;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * API projection that returns metadata plus the parsed invoice JSON body saved during ingestion.
 */
public class EInvoiceView {
    private final Long id;
    private final String invoiceNumber;
    private final String ftReference;
    private final LocalDate journalDate;
    private final String taxCategory;
    private final String taxAmount;
    private final String taxableAmount;
    private final String customerName;
    private final String status;
    private final String lastError;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
    private final Map<String, Object> invoice;
    private final Object submissionResponse;

    public EInvoiceView(Long id, String invoiceNumber, String ftReference, LocalDate journalDate,
                        String taxCategory, String taxAmount, String taxableAmount, String customerName,
                        String status, String lastError, LocalDateTime createdAt, LocalDateTime updatedAt,
                        Map<String, Object> invoice, Object submissionResponse) {
        this.id = id;
        this.invoiceNumber = invoiceNumber;
        this.ftReference = ftReference;
        this.journalDate = journalDate;
        this.taxCategory = taxCategory;
        this.taxAmount = taxAmount;
        this.taxableAmount = taxableAmount;
        this.customerName = customerName;
        this.status = status;
        this.lastError = lastError;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.invoice = invoice;
        this.submissionResponse = submissionResponse;
    }

    public Long getId() { return id; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public String getFtReference() { return ftReference; }
    public LocalDate getJournalDate() { return journalDate; }
    public String getTaxCategory() { return taxCategory; }
    public String getTaxAmount() { return taxAmount; }
    public String getTaxableAmount() { return taxableAmount; }
    public String getCustomerName() { return customerName; }
    public String getStatus() { return status; }
    public String getLastError() { return lastError; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public Map<String, Object> getInvoice() { return invoice; }
    public Object getSubmissionResponse() { return submissionResponse; }
}
