package com.accionmfb.omnix.einvoice.workflow;

import com.accionmfb.omnix.einvoice.client.EInvoiceApiClient;
import com.accionmfb.omnix.einvoice.client.T24EnquiryClient;
import com.accionmfb.omnix.einvoice.config.EInvoiceProperties;
import com.accionmfb.omnix.einvoice.invoice.EInvoiceDocument;
import com.accionmfb.omnix.einvoice.invoice.EInvoiceDocumentRepository;
import com.accionmfb.omnix.einvoice.journal.JournalRow;
import com.accionmfb.omnix.einvoice.journal.JournalSpoolService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class EInvoiceWorkflowService {
    private static final Logger LOG = Logger.getLogger(EInvoiceWorkflowService.class.getName());
    private static final BigDecimal STAMP_THRESHOLD = new BigDecimal("10000");
    private static final DateTimeFormatter ISSUE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    private final JournalSpoolService spool;
    private final EInvoiceDocumentRepository invoices;
    private final T24EnquiryClient t24;
    private final EInvoiceApiClient api;
    private final EInvoiceProperties properties;
    private final ObjectMapper mapper;

    public EInvoiceWorkflowService(JournalSpoolService spool, EInvoiceDocumentRepository invoices,
                                   T24EnquiryClient t24, EInvoiceApiClient api,
                                   EInvoiceProperties properties, ObjectMapper mapper) {
        this.spool = spool;
        this.invoices = invoices;
        this.t24 = t24;
        this.api = api;
        this.properties = properties;
        this.mapper = mapper;
    }

    public RunResult run(LocalDate date) throws IOException {
        return run(date, null, false);
    }

    public RunResult run(LocalDate date, String ftReferenceFilter) throws IOException {
        return run(date, ftReferenceFilter, false);
    }

    /**
     * @param dryRun when true, build and persist invoice payloads without calling the FIRS API
     */
    @Transactional
    public RunResult run(LocalDate date, String ftReferenceFilter, boolean dryRun) throws IOException {
        List<JournalRow> rows = spool.loadRows(date);
        Map<String, List<JournalRow>> groups = new LinkedHashMap<>();
        for (JournalRow row : rows) {
            String groupKey = row.groupReference();
            if (groupKey.isEmpty()) continue;
            groups.computeIfAbsent(groupKey, ignored -> new ArrayList<>()).add(row);
        }

        if (ftReferenceFilter != null && !ftReferenceFilter.trim().isEmpty()) {
            String requested = JournalRow.normalizeReference(ftReferenceFilter.trim());
            groups.entrySet().removeIf(entry -> !entry.getKey().equalsIgnoreCase(requested));
        }

        int candidates = 0, built = 0, submitted = 0, failed = 0, skipped = 0;
        for (Map.Entry<String, List<JournalRow>> group : groups.entrySet()) {
            String ftReference = group.getKey();
            List<JournalRow> related = group.getValue();
            for (JournalRow taxRow : related) {
                TaxRule rule = TaxRule.match(taxRow);
                if (rule == null) continue;
                candidates++;
                String invoiceNumber = rule.invoiceNumber(ftReference);
                Optional<EInvoiceDocument> existing = invoices.findByInvoiceNumber(invoiceNumber);
                if (existing.isPresent() && "SUBMITTED".equals(existing.get().getStatus())) {
                    skipped++;
                    continue;
                }
                EInvoiceDocument document = existing.orElseGet(() ->
                        invoices.save(new EInvoiceDocument(invoiceNumber, ftReference, date, rule.kind)));
                try {
                    Map<String, Object> payload = buildPayload(taxRow, rule, related, ftReference, invoiceNumber, date);
                    String serialized = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
                    document.setRequestPayload(serialized);
                    document.setTaxAmount(taxRow.credit().toPlainString());
                    document.setTaxableAmount(String.valueOf(payload.get("legalMonetaryTotal") instanceof Map
                            ? ((Map<?, ?>) payload.get("legalMonetaryTotal")).get("taxExclusiveAmount")
                            : ""));
                    document.setCustomerName(extractCustomerName(payload));
                    document.setLastError(null);

                    boolean shouldSubmit = !dryRun && properties.isSubmitEnabled()
                            && properties.getInvoiceApiUrl() != null
                            && !properties.getInvoiceApiUrl().trim().isEmpty();
                    if (shouldSubmit) {
                        document.setStatus("PENDING");
                        invoices.save(document);
                        String response = api.submit(serialized);
                        document.setResponsePayload(response);
                        document.setStatus("SUBMITTED");
                        invoices.save(document);
                        submitted++;
                    } else {
                        document.setStatus(dryRun ? "DRY_RUN" : "BUILT");
                        document.setResponsePayload(null);
                        invoices.save(document);
                        built++;
                    }
                } catch (Exception e) {
                    document.setStatus("FAILED");
                    document.setLastError(truncate(e.getMessage(), 1900));
                    invoices.save(document);
                    failed++;
                    LOG.log(Level.WARNING, "E-invoice processing failed for {0}: {1}",
                            new Object[]{invoiceNumber, e.getMessage()});
                }
            }
        }
        RunResult result = new RunResult(date, rows.size(), candidates, built, submitted, failed, skipped);
        LOG.log(Level.INFO,
                "E-invoice run date={0}, journalRows={1}, candidates={2}, built={3}, submitted={4}, failed={5}, skipped={6}",
                new Object[]{date, rows.size(), candidates, built, submitted, failed, skipped});
        return result;
    }

    public Map<String, Object> buildPayload(JournalRow taxRow, TaxRule rule, List<JournalRow> related,
                                     String ftReference, String invoiceNumber, LocalDate journalDate) {
        if (isBlank(properties.getInvoiceTypeCode())) {
            throw new IllegalStateException("Configure e-invoice.journal.invoice-type-code (Self-billed Invoice)");
        }

        Map<String, String> transaction = enquireTransaction(ftReference);
        String customerNumber = firstNonEmpty(
                val(transaction, "DEBIT.CUSTOMER:1:1"),
                val(transaction, "DEBIT.CUSTOMER"),
                related.stream().map(JournalRow::customerId).filter(v -> !v.isEmpty()).findFirst().orElse(""));
        Map<String, String> customer = enquireCustomer(customerNumber);

        BigDecimal taxAmount = taxRow.credit().setScale(2, RoundingMode.HALF_UP);
        ChargeMatch charge = determineTaxableAmount(taxRow, rule, related, transaction, taxAmount);
        BigDecimal taxableAmount = charge.amount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal inclusive = taxableAmount.add(taxAmount).setScale(2, RoundingMode.HALF_UP);

        String issueDate = resolveIssueDate(val(transaction, "DATE.TIME:1:1"), val(transaction, "DATE.TIME"), journalDate);
        String issueTime = issueDate.length() >= 19 ? issueDate.substring(11, 19) : "00:00:00";

        Map<String, Object> supplier = party(
                properties.getSupplierName(),
                properties.getSupplierTin(),
                true,
                properties.getSupplierEmail(),
                properties.getSupplierTelephone(),
                properties.getSupplierBusinessDescription(),
                properties.getSupplierStreet(),
                properties.getSupplierCity(),
                "",
                properties.getSupplierCountry()
        );

        String customerName = firstNonEmpty(
                val(customer, "SHORT.NAME:1:1"),
                val(customer, "NAME.1:1:1"),
                val(customer, "MNEMONIC:1:1"),
                related.stream().map(JournalRow::accountName)
                        .filter(name -> name != null && !name.isEmpty() && !name.startsWith("VAT")
                                && !name.startsWith("STAMP") && !name.startsWith("ACC PAY")
                                && !name.startsWith("RECORD."))
                        .findFirst().orElse(""),
                customerNumber
        );
        String customerTin = firstNonEmpty(val(customer, "TIN:1:1"), val(customer, "TAX.IDENTIFICATION.NO:1:1"));
        String customerEmail = firstNonEmpty(val(customer, "EMAIL.1:1:1"), val(customer, "E.MAIL.ADDRESS:1:1"));
        String customerPhone = firstNonEmpty(val(customer, "TEL.MOBILE:1:1"), val(customer, "SMS.1:1:1"),
                val(customer, "PHONE.1:1:1"));
        String street = firstNonEmpty(val(customer, "STREET:1:1"), val(customer, "ADDRESS:1:1"),
                val(customer, "TOWN.COUNTRY:1:1"));
        String city = firstNonEmpty(val(customer, "CUS.BUS.CITY:1:1"), val(customer, "TOWN.COUNTRY:1:1"), "LAGOS");
        String country = firstNonEmpty(val(customer, "NATIONALITY:1:1"), val(customer, "COUNTRY:1:1"), "NIG");

        Map<String, Object> buyer = party(
                customerName,
                customerTin,
                !customerTin.isEmpty(),
                customerEmail,
                customerPhone,
                firstNonEmpty(val(customer, "INDUSTRY:1:1"), val(customer, "SECTOR:1:1"), ""),
                street,
                city,
                "",
                country
        );

        Map<String, Object> taxCategory = map("id", rule.taxCategoryId, "percent", rule.percent);
        Map<String, Object> subtotal = map(
                "taxableAmount", taxableAmount,
                "taxAmount", taxAmount,
                "taxCategory", taxCategory
        );
        List<Map<String, Object>> taxTotals = Collections.singletonList(
                map("taxAmount", taxAmount, "taxSubtotal", Collections.singletonList(subtotal))
        );

        String itemName = firstNonEmpty(taxRow.accountName(), rule.defaultItemName);
        String description = firstNonEmpty(charge.narration, taxRow.narration(), itemName);
        Map<String, Object> lineItem = map(
                "name", itemName,
                "description", description,
                "sellersItemIdentification", firstNonEmpty(taxRow.account(), "")
        );
        Map<String, Object> price = map(
                "priceAmount", taxableAmount,
                "baseQuantity", 1,
                "priceUnit", taxableAmount.toPlainString()
        );
        Map<String, Object> invoiceLine = map(
                "hsnCode", "",
                "discountRate", BigDecimal.ZERO,
                "discountAmount", BigDecimal.ZERO,
                "feeRate", BigDecimal.ZERO,
                "feeAmount", BigDecimal.ZERO,
                "invoicedQuantity", 1,
                "lineExtensionAmount", BigDecimal.ZERO,
                "item", lineItem,
                "price", price
        );

        Map<String, Object> totals = map(
                "lineExtensionAmount", BigDecimal.ZERO,
                "taxExclusiveAmount", taxableAmount,
                "taxInclusiveAmount", inclusive,
                "payableAmount", taxAmount
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("invoiceNumber", invoiceNumber);
        result.put("issueDate", issueDate);
        result.put("dueDate", issueDate);
        result.put("issueTime", issueTime);
        result.put("invoiceTypeCode", properties.getInvoiceTypeCode());
        result.put("paymentStatus", "COMPLETED");
        result.put("documentCurrencyCode", "NGN");
        result.put("taxCurrencyCode", "NGN");
        result.put("accountingSupplierParty", supplier);
        result.put("accountingCustomerParty", buyer);
        result.put("taxTotals", taxTotals);
        result.put("legalMonetaryTotal", totals);
        result.put("invoiceLines", Collections.singletonList(invoiceLine));
        if (!taxRow.narration().isEmpty()) {
            result.put("note", taxRow.narration());
        }
        return result;
    }

    private ChargeMatch determineTaxableAmount(JournalRow taxRow, TaxRule rule, List<JournalRow> related,
                                               Map<String, String> transaction, BigDecimal taxAmount) {
        if ("VAT".equals(rule.kind)) {
            // Prefer income/charge legs (790) where amount * 7.5% rounds UP to the VAT credit.
            Optional<JournalRow> matched = related.stream()
                    .filter(r -> "790".equals(r.transactionCode()) && r.hasAmount())
                    .filter(r -> vatRoundsTo(r.amount(), rule.rate, taxAmount))
                    .findFirst();
            if (!matched.isPresent()) {
                matched = related.stream()
                        .filter(r -> r.hasAmount())
                        .filter(r -> !r.transactionCode().equals(taxRow.transactionCode()) || r.credit().compareTo(taxAmount) != 0)
                        .filter(r -> !r.account().equals(taxRow.account()))
                        .filter(r -> vatRoundsTo(r.amount(), rule.rate, taxAmount))
                        .findFirst();
            }
            if (matched.isPresent()) {
                JournalRow charge = matched.get();
                return new ChargeMatch(charge.amount(), firstNonEmpty(charge.narration(), taxRow.narration()));
            }
            BigDecimal derived = deriveTaxableFromRate(taxAmount, rule.rate);
            if (derived != null) {
                return new ChargeMatch(derived, taxRow.narration());
            }
            throw new IllegalStateException("Could not match VAT credit " + taxAmount + " to a related charge for FT "
                    + taxRow.groupReference());
        }

        if ("LOAN_STAMP".equals(rule.kind)) {
            BigDecimal original = firstPositive(
                    money(val(transaction, "DEBIT.AMOUNT:1:1")),
                    money(val(transaction, "DEBIT.AMOUNT")),
                    money(val(transaction, "AMOUNT:1:1")),
                    related.stream()
                            .filter(r -> "213".equals(r.transactionCode()) && r.debit().signum() > 0)
                            .map(JournalRow::debit)
                            .findFirst().orElse(BigDecimal.ZERO)
            );
            if (original.signum() > 0) {
                BigDecimal expected = original.multiply(rule.rate).setScale(2, RoundingMode.HALF_UP);
                if (expected.compareTo(taxAmount) != 0) {
                    BigDecimal derived = taxAmount.divide(rule.rate, 2, RoundingMode.HALF_UP);
                    return new ChargeMatch(derived, taxRow.narration());
                }
                return new ChargeMatch(original, taxRow.narration());
            }
            BigDecimal derived = taxAmount.divide(rule.rate, 2, RoundingMode.HALF_UP);
            return new ChargeMatch(derived, taxRow.narration());
        }

        // TRANSACTION_STAMP: flat N50 for transfers >= 10,000
        BigDecimal original = firstPositive(
                money(val(transaction, "DEBIT.AMOUNT:1:1")),
                money(val(transaction, "DEBIT.AMOUNT")),
                related.stream()
                        .filter(r -> r.debit().compareTo(STAMP_THRESHOLD) >= 0)
                        .map(JournalRow::debit)
                        .findFirst().orElse(BigDecimal.ZERO),
                related.stream()
                        .filter(r -> r.credit().compareTo(STAMP_THRESHOLD) >= 0)
                        .map(JournalRow::credit)
                        .findFirst().orElse(BigDecimal.ZERO)
        );
        if (original.signum() > 0 && original.compareTo(STAMP_THRESHOLD) < 0) {
            throw new IllegalStateException("Stamp duty transaction is below the configured NGN 10,000 threshold");
        }
        if (original.signum() <= 0) {
            original = STAMP_THRESHOLD;
        }
        return new ChargeMatch(original, taxRow.narration());
    }

    private static boolean vatRoundsTo(BigDecimal charge, BigDecimal rate, BigDecimal taxAmount) {
        if (charge == null || charge.signum() <= 0) return false;
        BigDecimal calculated = charge.multiply(rate).setScale(2, RoundingMode.UP);
        return calculated.compareTo(taxAmount) == 0;
    }

    private static BigDecimal deriveTaxableFromRate(BigDecimal taxAmount, BigDecimal rate) {
        if (rate == null || rate.signum() <= 0) return null;
        BigDecimal derived = taxAmount.divide(rate, 4, RoundingMode.HALF_UP);
        if (vatRoundsTo(derived, rate, taxAmount)) {
            return derived.setScale(2, RoundingMode.HALF_UP);
        }
        // Try a two-decimal candidate around the division result.
        BigDecimal scaled = derived.setScale(2, RoundingMode.HALF_UP);
        if (vatRoundsTo(scaled, rate, taxAmount)) return scaled;
        BigDecimal up = derived.setScale(2, RoundingMode.UP);
        if (vatRoundsTo(up, rate, taxAmount)) return up;
        return scaled;
    }

    private Map<String, String> enquireTransaction(String ftReference) {
        if (!properties.isT24EnrichmentEnabled()) return Collections.emptyMap();
        try {
            return t24.transaction(ftReference);
        } catch (Exception ex) {
            LOG.log(Level.INFO, "T24 FT enquiry unavailable for {0}: {1}", new Object[]{ftReference, ex.getMessage()});
            return Collections.emptyMap();
        }
    }

    private Map<String, String> enquireCustomer(String customerNumber) {
        if (!properties.isT24EnrichmentEnabled() || isBlank(customerNumber)) return Collections.emptyMap();
        try {
            return t24.customer(customerNumber);
        } catch (Exception ex) {
            LOG.log(Level.INFO, "T24 customer enquiry unavailable for {0}: {1}", new Object[]{customerNumber, ex.getMessage()});
            return Collections.emptyMap();
        }
    }

    private static String resolveIssueDate(String primary, String secondary, LocalDate journalDate) {
        for (String candidate : new String[]{primary, secondary}) {
            if (isBlank(candidate)) continue;
            try {
                return OffsetDateTime.parse(candidate.trim())
                        .withOffsetSameInstant(ZoneOffset.UTC)
                        .format(ISSUE_FORMAT);
            } catch (DateTimeParseException ignored) { }
            try {
                String normalized = candidate.replace("/", " ").trim();
                String[] parts = normalized.split("\\s+");
                if (parts.length >= 2 && parts[0].matches("\\d{8}")) {
                    String time = parts[1].length() == 4 ? parts[1].substring(0, 2) + ":" + parts[1].substring(2) + ":00"
                            : parts[1].length() == 5 ? parts[1] + ":00" : parts[1];
                    if (time.matches("\\d{2}:\\d{2}")) time = time + ":00";
                    LocalDateTime parsed = LocalDateTime.parse(parts[0] + "T" + time,
                            DateTimeFormatter.ofPattern("yyyyMMdd'T'HH:mm:ss"));
                    return parsed.atOffset(ZoneOffset.UTC).format(ISSUE_FORMAT);
                }
            } catch (Exception ignored) { }
        }
        LocalDateTime fallback = LocalDateTime.of(journalDate, LocalTime.of(0, 0, 0));
        return fallback.atOffset(ZoneOffset.UTC).format(ISSUE_FORMAT);
    }

    private Map<String, Object> party(String name, String tin, boolean validated, String email, String telephone,
                                      String description, String street, String city, String postalZone, String country) {
        return map(
                "partyName", nullToEmpty(name),
                "tin", nullToEmpty(tin),
                "isTinValidated", validated,
                "email", nullToEmpty(email),
                "telephone", nullToEmpty(telephone),
                "businessDescription", nullToEmpty(description),
                "postalAddress", map(
                        "streetName", nullToEmpty(street),
                        "cityName", nullToEmpty(city),
                        "postalZone", nullToEmpty(postalZone),
                        "country", nullToEmpty(country)
                )
        );
    }

    @SuppressWarnings("unchecked")
    private static String extractCustomerName(Map<String, Object> payload) {
        Object party = payload.get("accountingCustomerParty");
        if (party instanceof Map) {
            Object name = ((Map<String, Object>) party).get("partyName");
            return name == null ? "" : String.valueOf(name);
        }
        return "";
    }

    private static BigDecimal firstPositive(BigDecimal... values) {
        if (values == null) return BigDecimal.ZERO;
        for (BigDecimal value : values) {
            if (value != null && value.signum() > 0) return value;
        }
        return BigDecimal.ZERO;
    }

    private static BigDecimal money(String value) { return JournalRow.money(value); }
    private static String val(Map<String, String> values, String key) {
        if (values == null || key == null) return "";
        return values.getOrDefault(key, "").trim();
    }
    private static String firstNonEmpty(String... values) { return JournalRow.firstNonEmpty(values); }
    private static String truncate(String value, int max) {
        if (value == null) return "Processing failed";
        return value.length() <= max ? value : value.substring(0, max);
    }
    private static boolean isBlank(String value) { return value == null || value.trim().isEmpty(); }
    private static String nullToEmpty(String value) { return value == null ? "" : value; }

    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            result.put((String) pairs[i], pairs[i + 1]);
        }
        return result;
    }

    private static final class ChargeMatch {
        final BigDecimal amount;
        final String narration;
        ChargeMatch(BigDecimal amount, String narration) {
            this.amount = amount;
            this.narration = narration == null ? "" : narration;
        }
    }

    public static class RunResult {
        private final LocalDate date;
        private final int journalRecords;
        private final int candidateTaxes;
        private final int built;
        private final int submitted;
        private final int failed;
        private final int skipped;

        public RunResult(LocalDate date, int journalRecords, int candidateTaxes, int built,
                         int submitted, int failed, int skipped) {
            this.date = date;
            this.journalRecords = journalRecords;
            this.candidateTaxes = candidateTaxes;
            this.built = built;
            this.submitted = submitted;
            this.failed = failed;
            this.skipped = skipped;
        }

        public LocalDate getDate() { return date; }
        public int getJournalRecords() { return journalRecords; }
        public int getCandidateTaxes() { return candidateTaxes; }
        public int getBuilt() { return built; }
        public int getSubmitted() { return submitted; }
        public int getFailed() { return failed; }
        public int getSkipped() { return skipped; }
    }
}
