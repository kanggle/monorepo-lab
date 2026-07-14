package com.example.shipping;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
// Scan the whole infrastructure tree, not just `.persistence`: carrier webhook
// idempotency (TASK-BE-294) put ProcessedCarrierWebhookJpaRepository under
// `.webhook`, which the narrower scope missed → the repository bean was never
// created → APPLICATION FAILED TO START in the full-stack boot (TASK-BE-358).
@EnableJpaRepositories(basePackages = "com.example.shipping.infrastructure")
@EntityScan(basePackages = "com.example.shipping")
@EnableScheduling
public class ShippingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShippingServiceApplication.class, args);
    }
}
