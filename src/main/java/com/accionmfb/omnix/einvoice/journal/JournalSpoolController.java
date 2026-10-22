package com.accionmfb.omnix.einvoice.journal;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.time.LocalDate;
import com.accionmfb.omnix.einvoice.workflow.EInvoiceWorkflowService;
import com.accionmfb.omnix.einvoice.invoice.EInvoiceDocumentRepository;
import com.accionmfb.omnix.einvoice.invoice.EInvoiceDocument;

@RestController
@RequestMapping("/api/v1/e-invoices")
public class JournalSpoolController {
    private final JournalSpoolService spoolService;
    private final EInvoiceWorkflowService workflow;
    private final EInvoiceDocumentRepository invoiceRepository;
    public JournalSpoolController(JournalSpoolService spoolService, EInvoiceWorkflowService workflow,
                                  EInvoiceDocumentRepository invoiceRepository) {
        this.spoolService = spoolService; this.workflow = workflow; this.invoiceRepository = invoiceRepository;
    }

    @GetMapping("/spool")
    public List<JournalSpoolRecord> spool(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) throws IOException {
        return spoolService.spool(date);
    }

    @org.springframework.web.bind.annotation.PostMapping("/process")
    public EInvoiceWorkflowService.RunResult process(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                                     @RequestParam(required = false) String ftReference) throws IOException {
        return workflow.run(date, ftReference);
    }

    @GetMapping
    public List<EInvoiceDocument> invoices(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return invoiceRepository.findByJournalDateOrderByIdAsc(date);
    }

    @ExceptionHandler(JournalSpoolService.JournalFileNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse missingJournal(JournalSpoolService.JournalFileNotFoundException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    @ExceptionHandler(IOException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse readFailure(IOException exception) { return new ErrorResponse("Unable to read journal file"); }

    public static class ErrorResponse {
        private final String message;
        public ErrorResponse(String message) { this.message = message; }
        public String getMessage() { return message; }
    }
}
