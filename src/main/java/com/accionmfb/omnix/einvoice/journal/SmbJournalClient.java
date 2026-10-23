package com.accionmfb.omnix.einvoice.journal;

import com.accionmfb.omnix.einvoice.config.EInvoiceProperties;
import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2ShareAccess;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.SmbConfig;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;
import com.hierynomus.smbj.share.File;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reads journal CSV files from the authenticated Windows share:
 * \\{host}\{share}\{path}\{filename}
 */
@Component
public class SmbJournalClient {
    private static final Logger LOG = Logger.getLogger(SmbJournalClient.class.getName());

    private final EInvoiceProperties properties;

    public SmbJournalClient(EInvoiceProperties properties) {
        this.properties = properties;
    }

    public byte[] readFile(String filename) throws IOException {
        if (!properties.hasSmbCredentials()) {
            throw new IOException("SMB journal credentials are not configured");
        }

        String host = properties.getSmbHost().trim();
        String shareName = properties.getSmbShare().trim();
        String relativePath = joinPath(properties.getSmbPath(), filename);

        SmbConfig config = SmbConfig.builder()
                .withTimeout(30, TimeUnit.SECONDS)
                .withSoTimeout(30, TimeUnit.SECONDS)
                .build();

        try (SMBClient client = new SMBClient(config);
             Connection connection = client.connect(host)) {
            String domain = properties.getSmbDomain() == null ? "" : properties.getSmbDomain().trim();
            AuthenticationContext auth = new AuthenticationContext(
                    properties.getSmbUsername().trim(),
                    properties.getSmbPassword().toCharArray(),
                    domain
            );
            Session session = connection.authenticate(auth);
            try (DiskShare share = (DiskShare) session.connectShare(shareName)) {
                if (!share.fileExists(relativePath)) {
                    throw new IOException("Journal file not found on SMB share: \\\\" + host + "\\"
                            + shareName + "\\" + relativePath.replace('/', '\\'));
                }
                try (File file = share.openFile(
                        relativePath,
                        EnumSet.of(AccessMask.GENERIC_READ),
                        null,
                        SMB2ShareAccess.ALL,
                        SMB2CreateDisposition.FILE_OPEN,
                        null);
                     InputStream in = file.getInputStream()) {
                    byte[] content = StreamUtils.copyToByteArray(in);
                    LOG.log(Level.INFO, "Loaded journal over SMB: \\\\{0}\\{1}\\{2} ({3} bytes)",
                            new Object[]{host, shareName, relativePath.replace('/', '\\'), content.length});
                    return content;
                }
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Unable to read journal from SMB share \\\\" + host + "\\"
                    + shareName + "\\" + relativePath.replace('/', '\\') + ": " + e.getMessage(), e);
        }
    }

    public static String joinPath(String folder, String filename) {
        String base = folder == null ? "" : folder.trim().replace('\\', '/');
        while (base.startsWith("/")) base = base.substring(1);
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        String name = filename == null ? "" : filename.trim().replace('\\', '/');
        while (name.startsWith("/")) name = name.substring(1);
        if (base.isEmpty()) return name;
        if (name.isEmpty()) return base;
        return base + "/" + name;
    }
}
