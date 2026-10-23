package com.accionmfb.omnix.einvoice.invoice;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "einvoice_document", uniqueConstraints = @UniqueConstraint(name = "uk_einvoice_invoice_number", columnNames = "invoice_number"))
public class EInvoiceDocument {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_number", nullable = false, length = 120)
    private String invoiceNumber;

    @Column(name = "ft_reference", nullable = false, length = 100)
    private String ftReference;

    @Column(name = "journal_date", nullable = false)
    private LocalDate journalDate;

    @Column(name = "tax_category", nullable = false, length = 60)
    private String taxCategory;

    @Column(name = "tax_amount", length = 40)
    private String taxAmount;

    @Column(name = "taxable_amount", length = 40)
    private String taxableAmount;

    @Column(name = "customer_name", length = 180)
    private String customerName;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Lob
    @Column(name = "request_payload")
    private String requestPayload;

    @Lob
    @Column(name = "response_payload")
    private String responsePayload;

    @Column(name = "last_error", length = 2000)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected EInvoiceDocument() { }

    public EInvoiceDocument(String invoiceNumber, String ftReference, LocalDate journalDate, String taxCategory) {
        this.invoiceNumber = invoiceNumber;
        this.ftReference = ftReference;
        this.journalDate = journalDate;
        this.taxCategory = taxCategory;
        this.status = "PENDING";
        this.createdAt = LocalDateTime.now();
        this.updatedAt = createdAt;
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
    public String getRequestPayload() { return requestPayload; }
    public String getResponsePayload() { return responsePayload; }
    public String getLastError() { return lastError; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setStatus(String status) { this.status = status; this.updatedAt = LocalDateTime.now(); }
    public void setRequestPayload(String requestPayload) { this.requestPayload = requestPayload; this.updatedAt = LocalDateTime.now(); }
    public void setResponsePayload(String responsePayload) { this.responsePayload = responsePayload; this.updatedAt = LocalDateTime.now(); }
    public void setLastError(String lastError) { this.lastError = lastError; this.updatedAt = LocalDateTime.now(); }
    public void setTaxAmount(String taxAmount) { this.taxAmount = taxAmount; this.updatedAt = LocalDateTime.now(); }
    public void setTaxableAmount(String taxableAmount) { this.taxableAmount = taxableAmount; this.updatedAt = LocalDateTime.now(); }
    public void setCustomerName(String customerName) { this.customerName = customerName; this.updatedAt = LocalDateTime.now(); }
}
