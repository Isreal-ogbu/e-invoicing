package com.accionmfb.omnix.einvoice.workflow;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
public class EInvoiceScheduler {
    private static final Logger LOG = Logger.getLogger(EInvoiceScheduler.class.getName());
    private final EInvoiceWorkflowService workflow;
    private final ZoneId zoneId;

    public EInvoiceScheduler(EInvoiceWorkflowService workflow,
                             @Value("${e-invoice.journal.zone:Africa/Lagos}") String zone) {
        this.workflow = workflow;
        this.zoneId = ZoneId.of(zone);
    }

    @Scheduled(cron = "${e-invoice.journal.schedule:0 15 2 * * *}", zone = "${e-invoice.journal.zone:Africa/Lagos}")
    public void processPreviousDay() {
        LocalDate journalDate = LocalDate.now(zoneId).minusDays(1);
        try {
            EInvoiceWorkflowService.RunResult result = workflow.run(journalDate);
            LOG.log(Level.INFO,
                    "E-invoice daily run completed for {0}: candidates={1}, built={2}, submitted={3}, failed={4}",
                    new Object[]{journalDate, result.getCandidateTaxes(), result.getBuilt(),
                            result.getSubmitted(), result.getFailed()});
        } catch (Exception exception) {
            LOG.log(Level.SEVERE, "E-invoice daily run failed for " + journalDate, exception);
        }
    }
}
