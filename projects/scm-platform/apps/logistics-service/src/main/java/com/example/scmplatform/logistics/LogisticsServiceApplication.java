package com.example.scmplatform.logistics;

import com.example.messaging.outbox.OutboxMetricsAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * logistics-service — scm's 5th domain service (ADR-MONO-053 Phase 1).
 *
 * <p>Takes a confirmed shipment (goods already left a wms warehouse) and gets it onto a
 * carrier. Phase 1 is <b>carrier dispatch for one vendor (EasyPost)</b> plus the operator
 * {@code :retry} recovery surface — the foundational skeleton.
 *
 * <p>Service Type: event-consumer + rest-api. Architecture: Hexagonal
 * (domain / application / adapter / config).
 *
 * <p><b>Deliberately NOT wired in this slice</b> (ADR-053 §D7 phasing):
 * <ul>
 *   <li>the Kafka seam consumer {@code ShippingConfirmedConsumer} → TASK-SCM-BE-044
 *       (only the Kafka config scaffold + {@code processed_events} table land here);</li>
 *   <li>the 굿스플로 adapter + {@code CarrierRouter} → TASK-SCM-BE-043;</li>
 *   <li>the {@code FulfillmentRouter} self-branch → TASK-SCM-BE-044.</li>
 * </ul>
 * With one vendor there is no router — {@code DispatchShipmentUseCase} calls the port directly.
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
