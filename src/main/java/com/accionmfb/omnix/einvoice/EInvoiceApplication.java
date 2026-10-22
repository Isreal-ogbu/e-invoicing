package com.accionmfb.omnix.einvoice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EInvoiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(EInvoiceApplication.class, args);
    }
}
