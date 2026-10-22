package com.accionmfb.omnix.einvoice.journal;

import com.accionmfb.omnix.einvoice.config.EInvoiceProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.HashSet;
import java.util.Set;

@Service
public class JournalSpoolService {
    // T24 journal export order, zero based. The source file is headerless.
    private static final int CUSTOMER_ID = 0;
    private static final int CREDIT_AMOUNT = 4;
    private static final int DEBIT_AMOUNT = 5;
    private static final int TRANSACTION_CODE = 6;
    private static final int VALUE_DATE = 7;
    private static final int TRANS_REF = 11;
    private static final int OUR_REF = 12;
    private static final int PROCESSING_DATE = 9;

    private final EInvoiceProperties properties;
    private final JournalSpoolRepository repository;

    public JournalSpoolService(EInvoiceProperties properties, JournalSpoolRepository repository) {
        this.properties = properties;
        this.repository = repository;
    }

    @Transactional
    public List<JournalSpoolRecord> spool(LocalDate date) throws IOException {
        String datePart = date.format(DateTimeFormatter.ofPattern("dd_MMM_yyyy", Locale.ENGLISH)).toUpperCase(Locale.ENGLISH);
        String filename = properties.getFilePattern().replace("{date}", datePart);
        Path file = Paths.get(properties.getDirectory()).resolve(filename);
        if (!Files.isRegularFile(file) || !Files.isReadable(file)) {
            throw new JournalFileNotFoundException("Journal file is not readable: " + file);
        }
        Set<String> existingKeys = new HashSet<>();
        for (JournalSpoolRecord record : repository.findByJournalDate(date)) existingKeys.add(record.getSourceKey());
        List<JournalSpoolRecord> newRecords = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.trim().isEmpty()) continue;
                List<String> fields = parseCsvLine(line);
                if (fields.size() < 13) continue;
                String recordDate = first(fields.get(PROCESSING_DATE), fields.get(VALUE_DATE));
                if (!date.format(DateTimeFormatter.BASIC_ISO_DATE).equals(recordDate.replace("'", "").trim())) continue;
                String transactionCode = clean(fields.get(TRANSACTION_CODE));
                String ft = first(fields.get(OUR_REF), fields.get(TRANS_REF));
                String key = date + "|" + ft + "|" + transactionCode + "|" + clean(fields.get(CREDIT_AMOUNT)) + "|" + clean(fields.get(DEBIT_AMOUNT)) + "|" + lineNumber;
                if (existingKeys.add(key)) {
                    newRecords.add(new JournalSpoolRecord(key, date, ft, transactionCode,
                            clean(fields.get(CREDIT_AMOUNT)), clean(fields.get(DEBIT_AMOUNT)),
                            clean(fields.get(CUSTOMER_ID)), line));
                }
            }
        }
        repository.saveAll(newRecords);
        return repository.findByJournalDateOrderByIdAsc(date);
    }

    private static String first(String a, String b) {
        String value = clean(a);
        return value.isEmpty() ? clean(b) : value;
    }
    private static String clean(String s) { return s == null ? "" : s.trim().replaceAll("^'", ""); }

    // Supports quoted CSV fields and doubled quote escaping without an additional runtime dependency.
    public static List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') { value.append('"'); i++; }
                else quoted = !quoted;
            } else if (c == ',' && !quoted) { fields.add(value.toString()); value.setLength(0); }
            else value.append(c);
        }
        fields.add(value.toString());
        return fields;
    }

    public static class JournalFileNotFoundException extends RuntimeException {
        public JournalFileNotFoundException(String message) { super(message); }
    }
}
