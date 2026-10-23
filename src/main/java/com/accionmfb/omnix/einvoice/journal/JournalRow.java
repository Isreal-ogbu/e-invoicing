package com.accionmfb.omnix.einvoice.journal;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One journal CSV line with named access to the T24 export columns.
 */
public class JournalRow {
    public static final String[] DEFAULT_HEADERS = {
            "CUS.ID", "ACC.PL", "AC.PC.NAME", "OLD.AC.NO", "CR.AMT", "DR.AMT", "TRANS.CODE",
            "VALUE.DATE", "EXPOSURE.DATE", "PROCESSING.DATE", "THEIR.REF", "TRANS.REF", "OUR.REF",
            "ACCT.OFF", "BOOKING.DATE", "PDT.CAT", "CURRENCY", "SYS.ID", "YCOM.ID", "YMNE",
            "INP", "AUT", "NARR", "YPAN.ID"
    };

    private final Map<String, String> values;
    private final String rawLine;
    private final int lineNumber;

    public JournalRow(Map<String, String> values, String rawLine, int lineNumber) {
        this.values = values;
        this.rawLine = rawLine;
        this.lineNumber = lineNumber;
    }

    public static JournalRow fromFields(List<String> fields, List<String> headers, String rawLine, int lineNumber) {
        Map<String, String> map = new HashMap<>();
        List<String> effectiveHeaders = headers == null || headers.isEmpty()
                ? java.util.Arrays.asList(DEFAULT_HEADERS) : headers;
        for (int i = 0; i < effectiveHeaders.size(); i++) {
            String header = effectiveHeaders.get(i).trim().toUpperCase(Locale.ROOT);
            String value = i < fields.size() ? clean(fields.get(i)) : "";
            map.put(header, value);
        }
        // Keep positional fallbacks for headerless files.
        if (headers == null || headers.isEmpty()) {
            for (int i = 0; i < fields.size() && i < DEFAULT_HEADERS.length; i++) {
                map.putIfAbsent(DEFAULT_HEADERS[i], clean(fields.get(i)));
            }
        }
        return new JournalRow(map, rawLine, lineNumber);
    }

    public static boolean looksLikeHeader(List<String> fields) {
        if (fields == null || fields.isEmpty()) return false;
        String first = clean(fields.get(0)).toUpperCase(Locale.ROOT);
        return first.contains("CUS.ID") || first.equals("CUS.ID") || first.contains("CUSTOMER");
    }

    public String get(String header) {
        return values.getOrDefault(header.toUpperCase(Locale.ROOT), "");
    }

    public String customerId() { return get("CUS.ID"); }
    public String account() { return get("ACC.PL"); }
    public String accountName() { return get("AC.PC.NAME"); }
    public String creditAmount() { return get("CR.AMT"); }
    public String debitAmount() { return get("DR.AMT"); }
    public String transactionCode() { return get("TRANS.CODE"); }
    public String valueDate() { return get("VALUE.DATE"); }
    public String processingDate() { return get("PROCESSING.DATE"); }
    public String theirRef() { return get("THEIR.REF"); }
    public String transRef() { return get("TRANS.REF"); }
    public String ourRef() { return get("OUR.REF"); }
    public String narration() { return get("NARR"); }
    public String currency() { return firstNonEmpty(get("CURRENCY"), "NGN"); }
    public String rawLine() { return rawLine; }
    public int lineNumber() { return lineNumber; }
    public Map<String, String> values() { return Collections.unmodifiableMap(values); }

    public BigDecimal credit() { return money(creditAmount()); }
    public BigDecimal debit() { return money(debitAmount()); }
    public BigDecimal amount() { return credit().add(debit()); }

    public boolean isCredit() {
        return credit().signum() > 0 && debit().signum() == 0;
    }

    public boolean hasAmount() {
        return credit().signum() > 0 || debit().signum() > 0;
    }

    /** Prefer OUR.REF, fall back to TRANS.REF, strip trailing channel markers such as BNK. */
    public String groupReference() {
        return normalizeReference(firstNonEmpty(ourRef(), transRef()));
    }

    public String invoiceBaseReference() {
        return normalizeReference(firstNonEmpty(ourRef(), transRef()));
    }

    public String recordDate() {
        return firstNonEmpty(processingDate(), valueDate()).replace("'", "").trim();
    }

    public static String normalizeReference(String reference) {
        String value = clean(reference);
        if (value.isEmpty()) return "";
        String upper = value.toUpperCase(Locale.ROOT);
        if (upper.endsWith("BNK") && upper.length() > 3) {
            return value.substring(0, value.length() - 3);
        }
        return value;
    }

    public static BigDecimal money(String value) {
        try {
            String cleaned = clean(value).replace(",", "");
            if (cleaned.isEmpty()) return BigDecimal.ZERO;
            return new BigDecimal(cleaned);
        } catch (Exception ignored) {
            return BigDecimal.ZERO;
        }
    }

    public static String clean(String value) {
        if (value == null) return "";
        String trimmed = value.trim();
        if (trimmed.startsWith("'")) trimmed = trimmed.substring(1).trim();
        return trimmed;
    }

    public static String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return "";
    }
}
