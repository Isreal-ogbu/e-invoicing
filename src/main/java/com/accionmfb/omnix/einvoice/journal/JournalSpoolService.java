package com.accionmfb.omnix.einvoice.journal;

import com.accionmfb.omnix.einvoice.config.EInvoiceProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class JournalSpoolService {
    private static final Logger LOG = Logger.getLogger(JournalSpoolService.class.getName());

    private final EInvoiceProperties properties;
    private final JournalSpoolRepository repository;
    private final SmbJournalClient smbJournalClient;

    public JournalSpoolService(EInvoiceProperties properties,
                               JournalSpoolRepository repository,
                               SmbJournalClient smbJournalClient) {
        this.properties = properties;
        this.repository = repository;
        this.smbJournalClient = smbJournalClient;
    }

    @Transactional
    public List<JournalSpoolRecord> spool(LocalDate date) throws IOException {
        ResolvedJournal source = resolveJournal(date);
        String expectedDate = date.format(DateTimeFormatter.BASIC_ISO_DATE);
        Set<String> existingKeys = new HashSet<>();
        for (JournalSpoolRecord record : repository.findByJournalDate(date)) {
            existingKeys.add(record.getSourceKey());
        }

        List<JournalSpoolRecord> newRecords = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ByteArrayInputStream(source.content), StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            List<String> headers = null;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.trim().isEmpty()) continue;
                List<String> fields = parseCsvLine(line);
                if (fields.isEmpty()) continue;

                if (lineNumber == 1 && JournalRow.looksLikeHeader(fields)) {
                    headers = new ArrayList<>();
                    for (String field : fields) {
                        headers.add(JournalRow.clean(field).toUpperCase(Locale.ROOT));
                    }
                    continue;
                }
                if (fields.size() < 13) continue;

                JournalRow row = JournalRow.fromFields(fields, headers, line, lineNumber);
                String recordDate = row.recordDate();
                if (!expectedDate.equals(recordDate)
                        && !expectedDate.equals(row.valueDate())
                        && !expectedDate.equals(row.processingDate())) {
                    continue;
                }

                String ft = row.groupReference();
                String key = date + "|" + ft + "|" + row.transactionCode() + "|" + row.creditAmount()
                        + "|" + row.debitAmount() + "|" + row.account() + "|" + lineNumber;
                if (!existingKeys.add(key)) continue;

                newRecords.add(new JournalSpoolRecord(
                        key,
                        date,
                        ft,
                        row.transactionCode(),
                        row.account(),
                        truncate(row.accountName(), 180),
                        row.creditAmount(),
                        row.debitAmount(),
                        row.customerId(),
                        truncate(row.narration(), 500),
                        lineNumber,
                        line
                ));
            }
        }
        if (!newRecords.isEmpty()) {
            repository.saveAll(newRecords);
        }
        LOG.log(Level.INFO, "Journal spool source={0}, date={1}, newRows={2}, totalForDay={3}",
                new Object[]{source.location, date, newRecords.size(),
                        repository.findByJournalDateOrderByIdAsc(date).size()});
        return repository.findByJournalDateOrderByIdAsc(date);
    }

    public List<JournalRow> loadRows(LocalDate date) throws IOException {
        List<JournalSpoolRecord> records = spool(date);
        List<JournalRow> rows = new ArrayList<>(records.size());
        for (JournalSpoolRecord record : records) {
            List<String> fields = parseCsvLine(record.getRawRecord());
            rows.add(JournalRow.fromFields(fields, Arrays.asList(JournalRow.DEFAULT_HEADERS),
                    record.getRawRecord(), record.getLineNumber() == null ? 0 : record.getLineNumber()));
        }
        return rows;
    }

    private ResolvedJournal resolveJournal(LocalDate date) throws IOException {
        String datePart = date.format(DateTimeFormatter.ofPattern("dd_MMM_yyyy", Locale.ENGLISH)).toUpperCase(Locale.ENGLISH);
        String filename = properties.getFilePattern().replace("{date}", datePart);
        List<String> attempted = new ArrayList<>();

        // 1) Authenticated SMB share (primary production path)
        if (properties.hasSmbCredentials()) {
            String smbLocation = "smb://\\" + properties.getSmbHost() + "\\" + properties.getSmbShare()
                    + "\\" + SmbJournalClient.joinPath(properties.getSmbPath(), filename).replace('/', '\\');
            attempted.add(smbLocation);
            try {
                byte[] content = smbJournalClient.readFile(filename);
                return new ResolvedJournal(smbLocation, content);
            } catch (IOException smbError) {
                LOG.log(Level.WARNING, "SMB journal read failed for {0}: {1}",
                        new Object[]{smbLocation, smbError.getMessage()});
            }
        } else {
            attempted.add("smb:(disabled or missing credentials)");
        }

        // 2) Local/mounted filesystem path
        Path configured = Paths.get(properties.getDirectory()).resolve(filename);
        attempted.add(configured.toString());
        if (Files.isRegularFile(configured) && Files.isReadable(configured)) {
            return new ResolvedJournal(configured.toString(), Files.readAllBytes(configured));
        }

        // 3) Classpath sample (local dry-run)
        ClassPathResource resource = new ClassPathResource(filename);
        attempted.add("classpath:" + filename);
        if (resource.exists()) {
            byte[] bytes = StreamUtils.copyToByteArray(resource.getInputStream());
            return new ResolvedJournal("classpath:" + filename, bytes);
        }

        throw new JournalFileNotFoundException("Journal file is not readable. Tried: " + String.join(" | ", attempted));
    }

    private static String truncate(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }

    public static List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    value.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == ',' && !quoted) {
                fields.add(value.toString());
                value.setLength(0);
            } else {
                value.append(c);
            }
        }
        fields.add(value.toString());
        return fields;
    }

    private static final class ResolvedJournal {
        final String location;
        final byte[] content;
        ResolvedJournal(String location, byte[] content) {
            this.location = location;
            this.content = content;
        }
    }

    public static class JournalFileNotFoundException extends RuntimeException {
        public JournalFileNotFoundException(String message) {
            super(message);
        }
    }
}
