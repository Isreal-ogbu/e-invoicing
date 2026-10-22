package com.accionmfb.omnix.einvoice.client;

import com.accionmfb.omnix.einvoice.config.EInvoiceProperties;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class EInvoiceApiClient {
    private final EInvoiceProperties properties;
    private final RestTemplate restTemplate = new RestTemplate();
    public EInvoiceApiClient(EInvoiceProperties properties) { this.properties = properties; }

    public String submit(String payload) {
        if (properties.getInvoiceApiUrl() == null || properties.getInvoiceApiUrl().trim().isEmpty())
            throw new IllegalStateException("E-invoice submission URL is not configured");
        HttpHeaders headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);
        if (properties.getInvoiceApiToken() != null && !properties.getInvoiceApiToken().isEmpty())
            headers.set("Authorization", "Bearer " + properties.getInvoiceApiToken());
        return restTemplate.postForObject(properties.getInvoiceApiUrl(), new HttpEntity<>(payload, headers), String.class);
    }
}
