package com.accionmfb.omnix.einvoice;

import com.accionmfb.omnix.einvoice.config.EInvoiceProperties;
import com.accionmfb.omnix.einvoice.journal.JournalRow;
import com.accionmfb.omnix.einvoice.journal.JournalSpoolService;
import com.accionmfb.omnix.einvoice.workflow.EInvoiceWorkflowService;
import com.accionmfb.omnix.einvoice.workflow.TaxRule;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TaxRuleAndPayloadTest {

    @Test
    void matchesVatAndLoanStampFromSampleFt() throws Exception {
        List<JournalRow> rows = loadSampleRows();
        List<JournalRow> group = new ArrayList<>();
        for (JournalRow row : rows) {
            if ("FT2625981PS5".equals(row.groupReference())) {
                group.add(row);
            }
        }
        assertTrue(group.size() >= 3);

        JournalRow vat = group.stream().filter(r -> "780".equals(r.transactionCode())).findFirst().orElse(null);
        JournalRow loanStamp = group.stream().filter(r -> "258".equals(r.transactionCode()) && "1103824527".equals(r.account())).findFirst().orElse(null);
        assertNotNull(vat);
        assertNotNull(loanStamp);

        TaxRule vatRule = TaxRule.match(vat);
        TaxRule loanRule = TaxRule.match(loanStamp);
        assertNotNull(vatRule);
        assertNotNull(loanRule);
        assertEquals("VAT", vatRule.kind);
        assertEquals("FT2625981PS5ORI", vatRule.invoiceNumber("FT2625981PS5"));
        assertEquals("LOAN_STAMP", loanRule.kind);
    }

    @Test
    void buildsSampleShapedVatPayload() throws Exception {
        List<JournalRow> rows = loadSampleRows();
        List<JournalRow> group = new ArrayList<>();
        for (JournalRow row : rows) {
            if ("FT2625981PS5".equals(row.groupReference())) group.add(row);
        }
        JournalRow vat = group.stream().filter(r -> "780".equals(r.transactionCode())).findFirst().orElseThrow(IllegalStateException::new);
        TaxRule rule = TaxRule.match(vat);

        EInvoiceProperties properties = new EInvoiceProperties();
        properties.setT24EnrichmentEnabled(false);
        properties.setInvoiceTypeCode("Self-billed Invoice");

        EInvoiceWorkflowService workflow = new EInvoiceWorkflowService(
                null, null, null, null, properties, new ObjectMapper());

        Map<String, Object> payload = workflow.buildPayload(
                vat, rule, group, "FT2625981PS5", "FT2625981PS5ORI", LocalDate.of(2026, 9, 16));

        assertEquals("FT2625981PS5ORI", payload.get("invoiceNumber"));
        assertEquals("Self-billed Invoice", payload.get("invoiceTypeCode"));
        assertEquals("COMPLETED", payload.get("paymentStatus"));
        assertEquals("NGN", payload.get("documentCurrencyCode"));
        assertEquals("NGN", payload.get("taxCurrencyCode"));
        assertTrue(payload.get("issueDate").toString().startsWith("2026-09-16"));
        assertEquals("00:00:00", payload.get("issueTime"));

        @SuppressWarnings("unchecked")
        Map<String, Object> supplier = (Map<String, Object>) payload.get("accountingSupplierParty");
        assertEquals("ACCION MFB", supplier.get("partyName"));
        assertEquals("1441432424", supplier.get("tin"));
        assertEquals(true, supplier.get("isTinValidated"));

        @SuppressWarnings("unchecked")
        Map<String, Object> totals = (Map<String, Object>) payload.get("legalMonetaryTotal");
        assertEquals(new BigDecimal("0.50"), new BigDecimal(totals.get("taxExclusiveAmount").toString()));
        assertEquals(new BigDecimal("0.04"), new BigDecimal(totals.get("payableAmount").toString()));
        assertEquals(new BigDecimal("0.54"), new BigDecimal(totals.get("taxInclusiveAmount").toString()));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> taxTotals = (List<Map<String, Object>>) payload.get("taxTotals");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sub = (List<Map<String, Object>>) taxTotals.get(0).get("taxSubtotal");
        @SuppressWarnings("unchecked")
        Map<String, Object> category = (Map<String, Object>) sub.get(0).get("taxCategory");
        assertEquals("SERVICE_TAX", category.get("id"));
        assertEquals(new BigDecimal("7.5"), new BigDecimal(category.get("percent").toString()));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) payload.get("invoiceLines");
        @SuppressWarnings("unchecked")
        Map<String, Object> item = (Map<String, Object>) lines.get(0).get("item");
        assertEquals("VAT PAYABLE - CORPORATE TRANSACTION", item.get("name"));
        @SuppressWarnings("unchecked")
        Map<String, Object> price = (Map<String, Object>) lines.get(0).get("price");
        assertEquals(new BigDecimal("0.50"), new BigDecimal(price.get("priceAmount").toString()));
    }

    private static List<JournalRow> loadSampleRows() throws Exception {
        Path path = Paths.get("src/test/resources/JOURNAL_16_SEP_2026.csv");
        if (!Files.exists(path)) {
            path = Paths.get("e-invoicing/src/test/resources/JOURNAL_16_SEP_2026.csv");
        }
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        List<String> headers = JournalSpoolService.parseCsvLine(lines.get(0));
        for (int i = 0; i < headers.size(); i++) {
            headers.set(i, headers.get(i).trim().toUpperCase());
        }
        List<JournalRow> rows = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).trim().isEmpty()) continue;
            List<String> fields = JournalSpoolService.parseCsvLine(lines.get(i));
            rows.add(JournalRow.fromFields(fields, headers, lines.get(i), i + 1));
        }
        return rows;
    }
}
