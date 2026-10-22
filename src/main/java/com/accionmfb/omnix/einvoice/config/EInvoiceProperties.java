package com.accionmfb.omnix.einvoice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "e-invoice.journal")
public class EInvoiceProperties {
    private String directory = "//192.168.1.39/t24 daily reports/JOURNAL";
    private String filePattern = "JOURNAL_{date}.csv";
    private String schedule = "0 15 2 * * *";
    private String ofsUrl = "";
    private String ofsUsername = "";
    private String ofsPassword = "";
    private String tafjUsername = "";
    private String tafjPassword = "";
    private String invoiceApiUrl = "";
    private String invoiceApiToken = "";
    private String invoiceTypeCode = "";
    private String supplierName = "ACCION MFB";
    private String supplierTin = "1441432424";
    private String supplierEmail = "info@accionmfb.com";
    private String supplierTelephone = "07000 222 466";
    private String supplierBusinessDescription = "MFB";
    private String supplierStreet = "154 Ikorodu Road, Onipanu";
    private String supplierCity = "Lagos";
    private String supplierCountry = "Nigeria";

    public String getDirectory() { return directory; }
    public void setDirectory(String directory) { this.directory = directory; }
    public String getFilePattern() { return filePattern; }
    public void setFilePattern(String filePattern) { this.filePattern = filePattern; }
    public String getSchedule() { return schedule; }
    public void setSchedule(String schedule) { this.schedule = schedule; }
    public String getOfsUrl() { return ofsUrl; }
    public void setOfsUrl(String ofsUrl) { this.ofsUrl = ofsUrl; }
    public String getOfsUsername() { return ofsUsername; }
    public void setOfsUsername(String ofsUsername) { this.ofsUsername = ofsUsername; }
    public String getOfsPassword() { return ofsPassword; }
    public void setOfsPassword(String ofsPassword) { this.ofsPassword = ofsPassword; }
    public String getTafjUsername() { return tafjUsername; }
    public void setTafjUsername(String tafjUsername) { this.tafjUsername = tafjUsername; }
    public String getTafjPassword() { return tafjPassword; }
    public void setTafjPassword(String tafjPassword) { this.tafjPassword = tafjPassword; }
    public String getInvoiceApiUrl() { return invoiceApiUrl; }
    public void setInvoiceApiUrl(String invoiceApiUrl) { this.invoiceApiUrl = invoiceApiUrl; }
    public String getInvoiceApiToken() { return invoiceApiToken; }
    public void setInvoiceApiToken(String invoiceApiToken) { this.invoiceApiToken = invoiceApiToken; }
    public String getInvoiceTypeCode() { return invoiceTypeCode; }
    public void setInvoiceTypeCode(String invoiceTypeCode) { this.invoiceTypeCode = invoiceTypeCode; }
    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }
    public String getSupplierTin() { return supplierTin; }
    public void setSupplierTin(String supplierTin) { this.supplierTin = supplierTin; }
    public String getSupplierEmail() { return supplierEmail; }
    public void setSupplierEmail(String supplierEmail) { this.supplierEmail = supplierEmail; }
    public String getSupplierTelephone() { return supplierTelephone; }
    public void setSupplierTelephone(String supplierTelephone) { this.supplierTelephone = supplierTelephone; }
    public String getSupplierBusinessDescription() { return supplierBusinessDescription; }
    public void setSupplierBusinessDescription(String supplierBusinessDescription) { this.supplierBusinessDescription = supplierBusinessDescription; }
    public String getSupplierStreet() { return supplierStreet; }
    public void setSupplierStreet(String supplierStreet) { this.supplierStreet = supplierStreet; }
    public String getSupplierCity() { return supplierCity; }
    public void setSupplierCity(String supplierCity) { this.supplierCity = supplierCity; }
    public String getSupplierCountry() { return supplierCountry; }
    public void setSupplierCountry(String supplierCountry) { this.supplierCountry = supplierCountry; }
}
