package com.accionmfb.omnix.einvoice.client;

import com.accionmfb.omnix.einvoice.config.EInvoiceProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Component
public class T24EnquiryClient {
    private final EInvoiceProperties properties;
    private final ObjectMapper mapper;
    private final RestTemplate restTemplate = new RestTemplate();

    public T24EnquiryClient(EInvoiceProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
    }

    public Map<String, String> transaction(String ftReference) {
        return enquire("FUNDS.TRANSFER,/S/", JournalRef.normalize(ftReference));
    }

    public Map<String, String> customer(String customerNumber) {
        return enquire("CUSTOMER,/S/", customerNumber);
    }

    private Map<String, String> enquire(String operation, String reference) {
        if (blank(properties.getOfsUrl()) || blank(properties.getOfsUsername()) || blank(properties.getOfsPassword())
                || blank(properties.getTafjUsername()) || blank(properties.getTafjPassword())) {
            throw new IllegalStateException("TAFJ URL/HTTP credentials and T24 OFS credentials must be configured before invoice enrichment");
        }
        // Sample: {"ofsRequest":"FUNDS.TRANSFER,/S/,MOBUSER1/Password123#,FT2625981PS5"}
        String ofs = operation + "," + properties.getOfsUsername() + "/" + properties.getOfsPassword() + "," + reference;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("id-token", "AUTH");
        String basic = properties.getTafjUsername() + ":" + properties.getTafjPassword();
        headers.set("Authorization", "Basic " + Base64.getEncoder().encodeToString(basic.getBytes(StandardCharsets.UTF_8)));
        Map<String, String> body = new HashMap<>();
        body.put("ofsRequest", ofs);
        String response = restTemplate.postForObject(properties.getOfsUrl(), new HttpEntity<>(body, headers), String.class);
        if (response == null) throw new IllegalStateException("Empty response from TAFJ/OFS");
        try {
            JsonNode root = mapper.readTree(response);
            String ofsResponse = root.path("ofsResponse").asText(response);
            if (ofsResponse.startsWith("!")) throw new IllegalStateException("OFS enquiry failed: " + ofsResponse);
            return parseOfsFields(ofsResponse);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to parse TAFJ response", e);
        }
    }

    static Map<String, String> parseOfsFields(String response) {
        Map<String, String> fields = new HashMap<>();
        for (String part : response.split(",")) {
            int separator = part.indexOf('=');
            if (separator > 0) {
                fields.put(part.substring(0, separator).trim(),
                        part.substring(separator + 1).replace("\"", "").trim());
            }
        }
        return fields;
    }

    private static boolean blank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** Tiny helper so FT refs with BNK suffix still enquire cleanly. */
    static final class JournalRef {
        static String normalize(String reference) {
            if (reference == null) return "";
            String value = reference.trim();
            String upper = value.toUpperCase();
            if (upper.endsWith("BNK") && value.length() > 3) {
                return value.substring(0, value.length() - 3);
            }
            return value;
        }
    }
}
