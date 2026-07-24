package com.example.scmplatform.logistics;

import com.example.messaging.outbox.OutboxMetricsAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * logistics-service — scm's 5th domain service (ADR-MONO-053 Phase 1).
 *
 * <p>Takes a confirmed shipment (goods already left a wms warehouse) and gets it onto a
 * carrier. Phase 1 is <b>multi-vendor carrier dispatch</b> (EasyPost international + 굿스플로
 * domestic, selected per shipment by {@code CarrierRouter}, with a credential-free standalone
 * stub) plus the operator {@code :retry} recovery surface.
 *
 * <p>Service Type: event-consumer + rest-api. Architecture: Hexagonal
 * (domain / application / adapter / config).
 *
 * <p><b>Deliberately NOT wired yet</b> (ADR-053 §D7 phasing):
 * <ul>
 *   <li>the Kafka seam consumer {@code ShippingConfirmedConsumer} → TASK-SCM-BE-044
 *       (only the Kafka config scaffold + {@code processed_events} table exist; the live
 *       {@code carrierCode} routing signal is fed by that consumer — here it is seeded/stored);</li>
 *   <li>the {@code FulfillmentRouter} self-branch → TASK-SCM-BE-044.</li>
 * </ul>
 *
 * <p>This is a <b>terminal service</b> in Phase 1 — it publishes no domain event and runs
 * <b>no transactional outbox</b>: {@link OutboxMetricsAutoConfiguration} from
 * {@code libs/java-messaging} stays excluded (nothing here publishes). In-transit / tracking
 * emissions are Phase 2/3 (ADR-053 §D5/§D6), at which point the outbox decision is revisited.
 */
@SpringBootApplication(exclude = OutboxMetricsAutoConfiguration.class)
@ConfigurationPropertiesScan
public class LogisticsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LogisticsServiceApplication.class, args);
    }
}
