package com.accionmfb.omnix.einvoice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "e-invoice.journal")
public class EInvoiceProperties {
    /** Local/mounted directory fallback. Prefer SMB settings when credentials are present. */
    private String directory = "//192.168.1.39/t24 daily reports/JOURNAL";
    private String filePattern = "JOURNAL_{date}.csv";
    private String schedule = "0 15 2 * * *";
    private String zone = "Africa/Lagos";

    /** SMB/CIFS share hosting the daily journal export. */
    private String smbHost = "192.168.1.39";
    private String smbShare = "t24 daily reports";
    private String smbPath = "JOURNAL";
    private String smbDomain = "";
    private String smbUsername = "";
    private String smbPassword = "";
    private boolean smbEnabled = true;

    private String ofsUrl = "";
    private String ofsUsername = "";
    private String ofsPassword = "";
    private String tafjUsername = "";
    private String tafjPassword = "";
    private String invoiceApiUrl = "";
    private String invoiceApiToken = "";
    /** FIRS invoice type. Sample payload uses the description until the numeric code is confirmed. */
    private String invoiceTypeCode = "Self-billed Invoice";
    private boolean submitEnabled = false;
    private boolean t24EnrichmentEnabled = true;
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
    public String getZone() { return zone; }
    public void setZone(String zone) { this.zone = zone; }

    public String getSmbHost() { return smbHost; }
    public void setSmbHost(String smbHost) { this.smbHost = smbHost; }
    public String getSmbShare() { return smbShare; }
    public void setSmbShare(String smbShare) { this.smbShare = smbShare; }
    public String getSmbPath() { return smbPath; }
    public void setSmbPath(String smbPath) { this.smbPath = smbPath; }
    public String getSmbDomain() { return smbDomain; }
    public void setSmbDomain(String smbDomain) { this.smbDomain = smbDomain; }
    public String getSmbUsername() { return smbUsername; }
    public void setSmbUsername(String smbUsername) { this.smbUsername = smbUsername; }
    public String getSmbPassword() { return smbPassword; }
    public void setSmbPassword(String smbPassword) { this.smbPassword = smbPassword; }
    public boolean isSmbEnabled() { return smbEnabled; }
    public void setSmbEnabled(boolean smbEnabled) { this.smbEnabled = smbEnabled; }

    public boolean hasSmbCredentials() {
        return smbEnabled
                && notBlank(smbHost)
                && notBlank(smbShare)
                && notBlank(smbUsername)
                && notBlank(smbPassword);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

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
    public boolean isSubmitEnabled() { return submitEnabled; }
    public void setSubmitEnabled(boolean submitEnabled) { this.submitEnabled = submitEnabled; }
    public boolean isT24EnrichmentEnabled() { return t24EnrichmentEnabled; }
    public void setT24EnrichmentEnabled(boolean t24EnrichmentEnabled) { this.t24EnrichmentEnabled = t24EnrichmentEnabled; }
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
