package com.example.fanplatform.membership;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * fan-platform membership-service entrypoint.
 *
 * <p>Layered + explicit state-machine REST service. Subscribe / cancel / list /
 * detail over a single {@code Membership} subscription aggregate (ACTIVE /
 * CANCELED) plus one internal workload-identity access-check endpoint consumed
 * by community-service. Kafka publication is via the transactional outbox
 * ({@code MembershipEventPublisher} → outbox → {@code MembershipOutboxPollingScheduler}).
 */
@SpringBootApplication
@EnableScheduling
public class MembershipServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MembershipServiceApplication.class, args);
    }
}
