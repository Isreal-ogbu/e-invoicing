package com.accionmfb.omnix.einvoice.journal;

import com.accionmfb.omnix.einvoice.invoice.EInvoiceDocument;
import com.accionmfb.omnix.einvoice.invoice.EInvoiceDocumentRepository;
import com.accionmfb.omnix.einvoice.invoice.EInvoiceView;
import com.accionmfb.omnix.einvoice.workflow.EInvoiceWorkflowService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/e-invoices")
public class JournalSpoolController {
    private final JournalSpoolService spoolService;
    private final EInvoiceWorkflowService workflow;
    private final EInvoiceDocumentRepository invoiceRepository;
    private final ObjectMapper objectMapper;

    public JournalSpoolController(JournalSpoolService spoolService,
                                  EInvoiceWorkflowService workflow,
                                  EInvoiceDocumentRepository invoiceRepository,
                                  ObjectMapper objectMapper) {
        this.spoolService = spoolService;
        this.workflow = workflow;
        this.invoiceRepository = invoiceRepository;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/spool")
    public List<JournalSpoolRecord> spool(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) throws IOException {
        return spoolService.spool(date);
    }

    /**
     * Ingest journal rows for the date, build FIRS invoice payloads and optionally submit.
     * Use dryRun=true to only build/save payloads without calling the external invoice API.
     */
    @PostMapping("/process")
    public EInvoiceWorkflowService.RunResult process(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String ftReference,
            @RequestParam(defaultValue = "false") boolean dryRun) throws IOException {
        return workflow.run(date, ftReference, dryRun);
    }

    /**
     * List saved invoices for a journal date. Returns the parsed invoice JSON under {@code invoice}.
     */
    @GetMapping
    public List<EInvoiceView> invoices(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String ftReference) {
        List<EInvoiceDocument> documents;
        if (ftReference != null && !ftReference.trim().isEmpty()) {
            documents = invoiceRepository.findByFtReferenceIgnoreCaseOrderByIdAsc(ftReference.trim()).stream()
                    .filter(doc -> date.equals(doc.getJournalDate()))
                    .collect(Collectors.toList());
        } else if (status != null && !status.trim().isEmpty()) {
            documents = invoiceRepository.findByJournalDateAndStatusIgnoreCaseOrderByIdAsc(date, status.trim());
        } else {
            documents = invoiceRepository.findByJournalDateOrderByIdAsc(date);
        }
        List<EInvoiceView> views = new ArrayList<>(documents.size());
        for (EInvoiceDocument document : documents) {
            views.add(toView(document));
        }
        return views;
    }

    @GetMapping("/{invoiceNumber}")
    public EInvoiceView invoice(@PathVariable String invoiceNumber) {
        return invoiceRepository.findByInvoiceNumber(invoiceNumber)
                .map(this::toView)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Invoice not found: " + invoiceNumber));
    }

    private EInvoiceView toView(EInvoiceDocument document) {
        Map<String, Object> invoiceBody = parseJsonMap(document.getRequestPayload());
        Object response = parseJsonFlexible(document.getResponsePayload());
        return new EInvoiceView(
                document.getId(),
                document.getInvoiceNumber(),
                document.getFtReference(),
                document.getJournalDate(),
                document.getTaxCategory(),
                document.getTaxAmount(),
                document.getTaxableAmount(),
                document.getCustomerName(),
                document.getStatus(),
                document.getLastError(),
                document.getCreatedAt(),
                document.getUpdatedAt(),
                invoiceBody,
                response
        );
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.trim().isEmpty()) return Collections.emptyMap();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() { });
        } catch (Exception ignored) {
            return Collections.singletonMap("raw", json);
        }
    }

    private Object parseJsonFlexible(String json) {
        if (json == null || json.trim().isEmpty()) return null;
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception ignored) {
            return json;
        }
    }

    @ExceptionHandler(JournalSpoolService.JournalFileNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse missingJournal(JournalSpoolService.JournalFileNotFoundException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    @ExceptionHandler(IOException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse readFailure(IOException exception) {
        return new ErrorResponse("Unable to read journal file");
    }

    public static class ErrorResponse {
        private final String message;
        public ErrorResponse(String message) { this.message = message; }
        public String getMessage() { return message; }
    }
}
