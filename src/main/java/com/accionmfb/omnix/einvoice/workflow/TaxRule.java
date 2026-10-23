package com.accionmfb.omnix.einvoice.workflow;

import com.accionmfb.omnix.einvoice.journal.JournalRow;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * Included remittance rules from the E-Invoicing integration workbook.
 * Account matching uses category codes: first five digits after NGN for PL accounts,
 * or the exact account number for non-branch-specific accounts such as loan stamp duty.
 */
public final class TaxRule {
    public final String kind;
    public final String taxCategoryId;
    public final BigDecimal rate;
    public final BigDecimal percent;
    public final String invoiceSuffix;
    public final String defaultItemName;

    private TaxRule(String kind, String taxCategoryId, String rate, String percent,
                    String invoiceSuffix, String defaultItemName) {
        this.kind = kind;
        this.taxCategoryId = taxCategoryId;
        this.rate = new BigDecimal(rate);
        this.percent = new BigDecimal(percent);
        this.invoiceSuffix = invoiceSuffix;
        this.defaultItemName = defaultItemName;
    }

    public static TaxRule match(JournalRow row) {
        if (row == null || !row.isCredit()) return null;
        String code = row.transactionCode();
        String account = row.account();

        // VAT on corporate transactions: category 16120, txn code 780, 7.5%
        if ("780".equals(code) && matchesCategory(account, "16120")) {
            return new TaxRule("VAT", "SERVICE_TAX", "0.075", "7.5", "ORI",
                    "VAT PAYABLE - CORPORATE TRANSACTION");
        }
        // Stamp duty on loans: exact account 1103824527, txn code 258, 0.125%
        if ("258".equals(code) && "1103824527".equals(account)) {
            return new TaxRule("LOAN_STAMP", "SERVICE_TAX", "0.00125", "0.125", "LSD",
                    "STAMP DUTY ON LOANS");
        }
        // Stamp duty on electronic money transfer: category 16048, txn code 158, flat N50
        if ("158".equals(code) && matchesCategory(account, "16048")) {
            return new TaxRule("TRANSACTION_STAMP", "SERVICE_TAX", "50", "0", "TSD",
                    "ACC PAY - ELECTRONIC MONEY TRANSFER");
        }
        return null;
    }

    /**
     * PL accounts look like NGN1612000010001. Category is the five digits after NGN.
     * Numeric-only accounts (loan stamp) are compared exactly by callers.
     */
    static boolean matchesCategory(String account, String category) {
        if (account == null || category == null) return false;
        String value = account.trim().toUpperCase(Locale.ROOT);
        if (value.startsWith("NGN") && value.length() >= 8) {
            return value.substring(3, 8).equals(category);
        }
        return value.startsWith("NGN" + category);
    }

    public String invoiceNumber(String ftReference) {
        String base = JournalRow.normalizeReference(ftReference);
        return base + invoiceSuffix;
    }
}
