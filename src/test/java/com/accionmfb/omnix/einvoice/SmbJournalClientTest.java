package com.accionmfb.omnix.einvoice;

import com.accionmfb.omnix.einvoice.journal.SmbJournalClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SmbJournalClientTest {
    @Test
    void joinsShareRelativePath() {
        assertEquals("JOURNAL/JOURNAL_16_SEP_2026.csv",
                SmbJournalClient.joinPath("JOURNAL", "JOURNAL_16_SEP_2026.csv"));
        assertEquals("JOURNAL/JOURNAL_16_SEP_2026.csv",
                SmbJournalClient.joinPath("\\JOURNAL\\", "\\JOURNAL_16_SEP_2026.csv"));
        assertEquals("JOURNAL_16_SEP_2026.csv",
                SmbJournalClient.joinPath("", "JOURNAL_16_SEP_2026.csv"));
    }
}
