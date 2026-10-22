package com.accionmfb.omnix.einvoice.workflow;

import com.accionmfb.omnix.einvoice.client.EInvoiceApiClient;
import com.accionmfb.omnix.einvoice.client.T24EnquiryClient;
import com.accionmfb.omnix.einvoice.config.EInvoiceProperties;
import com.accionmfb.omnix.einvoice.invoice.EInvoiceDocument;
import com.accionmfb.omnix.einvoice.invoice.EInvoiceDocumentRepository;
import com.accionmfb.omnix.einvoice.journal.JournalSpoolRecord;
import com.accionmfb.omnix.einvoice.journal.JournalSpoolService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class EInvoiceWorkflowService {
    private static final Logger LOG = Logger.getLogger(EInvoiceWorkflowService.class.getName());
    private static final int CUSTOMER = 0, ACCOUNT = 1, ACCOUNT_NAME = 2, CREDIT = 4, DEBIT = 5,
            TXN_CODE = 6, TRANS_REF = 11, OUR_REF = 12, NARRATION = 22;
    private final JournalSpoolService spool;
    private final EInvoiceDocumentRepository invoices;
    private final T24EnquiryClient t24;
    private final EInvoiceApiClient api;
    private final EInvoiceProperties properties;
    private final ObjectMapper mapper;

    public EInvoiceWorkflowService(JournalSpoolService spool, EInvoiceDocumentRepository invoices,
                                   T24EnquiryClient t24, EInvoiceApiClient api,
                                   EInvoiceProperties properties, ObjectMapper mapper) {
        this.spool = spool; this.invoices = invoices; this.t24 = t24; this.api = api;
        this.properties = properties; this.mapper = mapper;
    }

    public RunResult run(LocalDate date) throws IOException {
        return run(date, null);
    }

    public RunResult run(LocalDate date, String ftReferenceFilter) throws IOException {
        List<JournalSpoolRecord> records = spool.spool(date);
        Map<String, List<Row>> groups = new HashMap<>();
        for (JournalSpoolRecord record : records) {
            Row row = Row.from(record.getRawRecord());
            if (row == null || row.groupReference().isEmpty()) continue;
            groups.computeIfAbsent(row.groupReference(), ignored -> new ArrayList<>()).add(row);
        }
        if (ftReferenceFilter != null && !ftReferenceFilter.trim().isEmpty()) {
            String requested = ftReferenceFilter.trim();
            groups.entrySet().removeIf(entry -> !entry.getKey().equalsIgnoreCase(requested)
                    && entry.getValue().stream().noneMatch(row -> row.invoiceReference().equalsIgnoreCase(requested)));
        }
        int candidates = 0, submitted = 0, failed = 0, skipped = 0;
        for (Map.Entry<String, List<Row>> group : groups.entrySet()) {
            Map<String, List<Row>> invoiceTaxes = new HashMap<>();
            Map<String, List<TaxRule>> invoiceRules = new HashMap<>();
            for (Row row : group.getValue()) {
                TaxRule rule = TaxRule.match(row);
                if (rule != null) {
                    String invoiceNumber = row.invoiceReference().isEmpty() ? group.getKey() + "ORI" : row.invoiceReference();
                    invoiceTaxes.computeIfAbsent(invoiceNumber, ignored -> new ArrayList<>()).add(row);
                    invoiceRules.computeIfAbsent(invoiceNumber, ignored -> new ArrayList<>()).add(rule);
                }
            }
            for (Map.Entry<String, List<Row>> invoiceGroup : invoiceTaxes.entrySet()) {
                List<Row> taxes = invoiceGroup.getValue();
                List<TaxRule> rules = invoiceRules.get(invoiceGroup.getKey());
                candidates += taxes.size();
                String invoiceNumber = invoiceGroup.getKey();
                String taxLabel = rules.size() == 1 ? rules.get(0).kind : "MULTIPLE";
                Optional<EInvoiceDocument> existing = invoices.findByInvoiceNumber(invoiceNumber);
                if (existing.isPresent() && "SUBMITTED".equals(existing.get().getStatus())) { skipped++; continue; }
                EInvoiceDocument document;
                if (existing.isPresent()) document = existing.get();
                else document = invoices.save(new EInvoiceDocument(invoiceNumber, group.getKey(), date, taxLabel));
                try {
                    Map<String, Object> payload = buildPayload(taxes, rules, group.getValue(), group.getKey(), invoiceNumber);
                    String serialized = mapper.writeValueAsString(payload);
                    document.setRequestPayload(serialized);
                    document.setStatus("PENDING");
                    invoices.save(document);
                    String response = api.submit(serialized);
                    document.setResponsePayload(response);
                    document.setLastError(null);
                    document.setStatus("SUBMITTED");
                    invoices.save(document);
                    submitted++;
                } catch (Exception e) {
                    document.setStatus("FAILED");
                    document.setLastError(truncate(e.getMessage(), 1900));
                    invoices.save(document);
                    failed++;
                    LOG.log(Level.WARNING, "E-invoice processing failed for FT {0}: {1}", new Object[]{group.getKey(), e.getMessage()});
                }
            }
        }
        RunResult result = new RunResult(date, records.size(), candidates, submitted, failed, skipped);
        LOG.log(Level.INFO, "E-invoice run date={0}, journalRows={1}, candidateTaxes={2}, submitted={3}, failed={4}, skipped={5}",
                new Object[]{date, records.size(), candidates, submitted, failed, skipped});
        return result;
    }

    @Transactional
    protected Map<String, Object> buildPayload(List<Row> taxes, List<TaxRule> rules, List<Row> related, String ftReference, String invoiceNumber) {
        if (isBlank(properties.getInvoiceTypeCode())) throw new IllegalStateException("Configure the Self-billed Invoice type code from the FIRS lookup");
        Map<String, String> transaction = t24.transaction(ftReference);
        String customerNumber = val(transaction, "DEBIT.CUSTOMER:1:1");
        if (customerNumber.isEmpty()) customerNumber = related.stream().map(r -> r.value(CUSTOMER)).filter(v -> !v.isEmpty()).findFirst().orElse("");
        if (customerNumber.isEmpty()) throw new IllegalStateException("Debit customer number not found for FT " + ftReference);
        Map<String, String> customer = t24.customer(customerNumber);
        BigDecimal totalTax = BigDecimal.ZERO, totalTaxable = BigDecimal.ZERO;
        List<Map<String, Object>> taxTotals = new ArrayList<>(), invoiceLines = new ArrayList<>();
        for (int i = 0; i < taxes.size(); i++) {
            Row tax = taxes.get(i); TaxRule rule = rules.get(i);
            BigDecimal taxAmount = money(tax.value(CREDIT));
            BigDecimal taxableAmount = determineTaxableAmount(tax, rule, related, transaction, taxAmount);
            totalTax = totalTax.add(taxAmount); totalTaxable = totalTaxable.add(taxableAmount);
            Map<String, Object> taxCategory = map("id", rule.taxCategory, "percent", rule.percent);
            Map<String, Object> subtotal = map("taxableAmount", taxableAmount, "taxAmount", taxAmount, "taxCategory", taxCategory);
            taxTotals.add(map("taxAmount", taxAmount, "taxSubtotal", Collections.singletonList(subtotal)));
            Row charge = related.stream().filter(r -> r.code().equals("790") && hasAmount(r))
                    .filter(r -> rule.kind.equals("VAT") && money(r.value(CREDIT)).add(money(r.value(DEBIT))).multiply(rule.rate)
                            .setScale(2, RoundingMode.HALF_UP).compareTo(taxAmount) == 0).findFirst().orElse(tax);
            String itemName = firstNonEmpty(tax.value(ACCOUNT_NAME), "TAX CHARGE");
            String description = firstNonEmpty(charge.value(NARRATION), tax.value(NARRATION), itemName);
            Map<String, Object> lineItem = map("name", itemName, "description", description, "sellersItemIdentification", tax.value(ACCOUNT));
            Map<String, Object> price = map("priceAmount", taxableAmount, "baseQuantity", 1, "priceUnit", taxableAmount.toPlainString());
            invoiceLines.add(map("hsnCode", "", "discountRate", BigDecimal.ZERO, "discountAmount", BigDecimal.ZERO,
                    "feeRate", BigDecimal.ZERO, "feeAmount", BigDecimal.ZERO, "invoicedQuantity", 1,
                    "lineExtensionAmount", BigDecimal.ZERO, "item", lineItem, "price", price));
        }
        BigDecimal inclusive = totalTaxable.add(totalTax).setScale(2, RoundingMode.HALF_UP);
        Row primaryTax = taxes.get(0);
        String issueDate = issueDate(val(transaction, "DATE.TIME:1:1"));
        String issueTime = issueDate.substring(11, 19);
        String customerName = firstNonEmpty(val(customer, "SHORT.NAME:1:1"), val(customer, "NAME.1:1:1"), val(customer, "MNEMONIC:1:1"));
        String address = firstNonEmpty(val(customer, "STREET:1:1"), val(customer, "ADDRESS:1:1"));
        Map<String, Object> supplier = party(properties.getSupplierName(), properties.getSupplierTin(), true,
                properties.getSupplierEmail(), properties.getSupplierTelephone(), properties.getSupplierBusinessDescription(),
                properties.getSupplierStreet(), properties.getSupplierCity(), "", properties.getSupplierCountry());
        String customerTin = firstNonEmpty(val(customer, "TIN:1:1"), val(customer, "TAX.IDENTIFICATION.NO:1:1"));
        Map<String, Object> buyer = party(customerName, customerTin, false,
                firstNonEmpty(val(customer, "EMAIL.1:1:1"), val(customer, "E.MAIL.ADDRESS:1:1")),
                val(customer, "TEL.MOBILE:1:1"), "", address,
                firstNonEmpty(val(customer, "CUS.BUS.CITY:1:1"), "LAGOS"), "", firstNonEmpty(val(customer, "NATIONALITY:1:1"), "NIG"));
        Map<String, Object> totals = map("lineExtensionAmount", BigDecimal.ZERO, "taxExclusiveAmount", totalTaxable,
                "taxInclusiveAmount", inclusive, "payableAmount", totalTax);
        Map<String, Object> result = map("invoiceNumber", invoiceNumber, "issueDate", issueDate, "dueDate", issueDate,
                "issueTime", issueTime, "invoiceTypeCode", properties.getInvoiceTypeCode(), "paymentStatus", "COMPLETED",
                "documentCurrencyCode", "NGN", "taxCurrencyCode", "NGN", "accountingSupplierParty", supplier,
                "accountingCustomerParty", buyer, "taxTotals", taxTotals,
                "legalMonetaryTotal", totals, "invoiceLines", invoiceLines);
        if (!primaryTax.value(NARRATION).isEmpty()) result.put("note", primaryTax.value(NARRATION));
        return result;
    }

    private BigDecimal determineTaxableAmount(Row tax, TaxRule rule, List<Row> related,
                                              Map<String, String> transaction, BigDecimal taxAmount) {
        if (rule.kind.equals("VAT")) {
            return related.stream().filter(r -> r.code().equals("790") && hasAmount(r))
                    .map(r -> money(r.value(CREDIT)).add(money(r.value(DEBIT))))
                    .filter(amount -> amount.multiply(rule.rate).setScale(2, RoundingMode.HALF_UP).compareTo(taxAmount) == 0)
                    .findFirst().orElseThrow(() -> new IllegalStateException("Could not match VAT credit to a related charge"));
        }
        if (rule.kind.equals("LOAN_STAMP")) {
            BigDecimal original = money(val(transaction, "DEBIT.AMOUNT:1:1"));
            if (original.signum() > 0) {
                if (original.multiply(rule.rate).setScale(2, RoundingMode.HALF_UP).compareTo(taxAmount) != 0)
                    throw new IllegalStateException("Loan stamp amount does not reconcile to the original transaction amount");
                return original;
            }
            throw new IllegalStateException("Original loan amount is missing from the transaction enquiry");
        }
        BigDecimal original = money(val(transaction, "DEBIT.AMOUNT:1:1"));
        if (original.compareTo(new BigDecimal("10000")) < 0)
            throw new IllegalStateException("Stamp duty transaction is below the configured NGN 10,000 threshold");
        return original;
    }

    private Map<String, Object> party(String name, String tin, boolean validated, String email, String telephone,
                                      String description, String street, String city, String postalZone, String country) {
        return map("partyName", name, "tin", tin, "isTinValidated", validated,
                "email", email, "telephone", telephone, "businessDescription", description,
                "postalAddress", map("streetName", street, "cityName", city, "postalZone", postalZone, "country", country));
    }

    private static String issueDate(String value) {
        String candidate = value == null ? "" : value.trim();
        try { return OffsetDateTime.parse(candidate).withOffsetSameInstant(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")); }
        catch (DateTimeParseException ignored) { }
        try {
            String normalized = candidate.replace("/", " ").trim();
            String[] parts = normalized.split("\\s+");
            if (parts.length >= 2 && parts[0].matches("\\d{8}")) {
                String time = parts[1].length() == 5 ? parts[1] + ":00" : parts[1];
                LocalDateTime parsed = LocalDateTime.parse(parts[0] + "T" + time, DateTimeFormatter.ofPattern("yyyyMMdd'T'HH:mm:ss"));
                return parsed.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"));
            }
        } catch (Exception ignored) { }
        throw new IllegalStateException("Transaction enquiry did not return a usable DATE.TIME field");
    }

    private static boolean hasAmount(Row r) { return money(r.value(CREDIT)).signum() > 0 || money(r.value(DEBIT)).signum() > 0; }
    private static BigDecimal money(String value) { try { return new BigDecimal(value.replace(",", "").trim()); } catch (Exception ignored) { return BigDecimal.ZERO; } }
    private static String val(Map<String, String> values, String key) { return values.getOrDefault(key, "").trim(); }
    private static String firstNonEmpty(String... values) { return Arrays.stream(values).filter(v -> v != null && !v.trim().isEmpty()).findFirst().orElse(""); }
    private static String truncate(String value, int max) { if (value == null) return "Processing failed"; return value.length() <= max ? value : value.substring(0, max); }
    private static boolean isBlank(String value) { return value == null || value.trim().isEmpty(); }
    private static Map<String, Object> map(Object... pairs) { Map<String, Object> result = new HashMap<>(); for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]); return result; }

    private static class TaxRule {
        final String kind, taxCategory; final BigDecimal rate, percent;
        TaxRule(String kind, String taxCategory, String rate, String percent) { this.kind = kind; this.taxCategory = taxCategory; this.rate = new BigDecimal(rate); this.percent = new BigDecimal(percent); }
        static TaxRule match(Row row) {
            if (!row.isCredit()) return null;
            if (row.code().equals("780") && row.value(ACCOUNT).startsWith("NGN16120")) return new TaxRule("VAT", "SERVICE_TAX", "0.075", "7.5");
            if (row.code().equals("258") && row.value(ACCOUNT).equals("1103824527")) return new TaxRule("LOAN_STAMP", "SERVICE_TAX", "0.00125", "0.125");
            if (row.code().equals("158") && row.value(ACCOUNT).startsWith("NGN16048")) return new TaxRule("TRANSACTION_STAMP", "SERVICE_TAX", "50", "0");
            return null;
        }
    }

    private static class Row {
        final List<String> columns;
        Row(List<String> columns) { this.columns = columns; }
        static Row from(String line) { List<String> cols = JournalSpoolService.parseCsvLine(line); return cols.size() >= 23 ? new Row(cols) : null; }
        String value(int index) { return index < columns.size() ? columns.get(index).trim().replaceAll("^'", "") : ""; }
        String code() { return value(TXN_CODE); }
        String groupReference() { return firstNonEmpty(value(OUR_REF), value(TRANS_REF)); }
        String invoiceReference() { return value(TRANS_REF); }
        boolean isCredit() { return money(value(CREDIT)).signum() > 0 && money(value(DEBIT)).signum() == 0; }
    }

    public static class RunResult {
        private final LocalDate date; private final int journalRecords, candidateTaxes, submitted, failed, skipped;
        RunResult(LocalDate date, int journalRecords, int candidateTaxes, int submitted, int failed, int skipped) {
            this.date = date; this.journalRecords = journalRecords; this.candidateTaxes = candidateTaxes;
            this.submitted = submitted; this.failed = failed; this.skipped = skipped;
        }
        public LocalDate getDate() { return date; } public int getJournalRecords() { return journalRecords; }
        public int getCandidateTaxes() { return candidateTaxes; } public int getSubmitted() { return submitted; }
        public int getFailed() { return failed; } public int getSkipped() { return skipped; }
    }
}
