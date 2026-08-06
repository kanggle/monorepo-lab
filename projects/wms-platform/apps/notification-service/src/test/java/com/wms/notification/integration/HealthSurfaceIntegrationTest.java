package com.wms.notification.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.HealthContributorRegistry;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.context.WebApplicationContext;

/**
 * TASK-BE-572 — the management surface must actually exist, and its verdict must
 * be about <em>this</em> container.
 *
 * <p>Before BE-572 this service had no web starter, so Spring Boot booted a
 * non-web application: {@code server.port} was ignored, nothing listened, and the
 * Dockerfile / compose {@code curl http://localhost:8085/actuator/health} health
 * check could never pass. Every existing integration test here runs with
 * {@link SpringBootTest.WebEnvironment#NONE}, so none of them ever touched that
 * surface — which is why the gap survived a full green suite. This class is the
 * one that boots a real server, so removing the web starter again fails here.
 *
 * <p>The membership assertions matter as much as the 200: a health endpoint that
 * answers is only useful if it answers about the right thing. {@code db} must be
 * in the aggregate (it is what makes a broken container report unhealthy), and
 * the Slack circuit breaker must not be (an unreachable external webhook is not a
 * reason to restart this process — see {@code application.yml}
 * {@code management.health.circuitbreakers}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
// Spring Boot Test turns metrics export OFF in test contexts by default, so
// /actuator/prometheus is a 404 there no matter what the service configures.
// Measured: without this the prometheus assertion below fails 404 — a failure
// about the test harness, not about the service.
// tracing = false mirrors MasterServiceIntegrationBase: the OTLP exporter points
// at a host that does not exist under test.
@AutoConfigureObservability(tracing = false)
@TestPropertySource(
        properties = {
            // A different webEnvironment is a different context cache key, so this
            // class boots a SECOND application context that Spring keeps cached and
            // running alongside the NONE context the sibling ITs share. Left as-is
            // it would add a full extra set of Kafka consumers against the same
            // broker and extra pollers against the same outbox and delivery tables
            // — group churn and contention that surface as awaitility timeouts in
            // whichever consumer IT happens to run next, not here.
            //
            // Health consults neither Kafka nor the schedulers, so switching them
            // off costs this test nothing and keeps its blast radius to the HTTP
            // surface it is actually about. (Same lever BE-529 used on the retry
            // poller, for the same "background worker races the test" reason.)
            "spring.kafka.listener.auto-startup=false",
            "wms.notification.outbox.polling-interval-ms=3600000",
            "wms.notification.outbox.initial-delay-ms=3600000",
            "wms.notification.delivery.retry-poll-interval-ms=3600000",
            "wms.notification.delivery.retry-initial-delay-ms=3600000"
        })
@DisplayName("actuator health surface (TASK-BE-572)")
class HealthSurfaceIntegrationTest extends NotificationServiceIntegrationBase {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private HealthContributorRegistry healthContributorRegistry;

    @Test
    @DisplayName("boots as a servlet web application — the surface the health check assumes")
    void bootsAsAServletWebApplication() {
        assertThat(applicationContext)
                .as(
                        "no web starter => Spring Boot boots a non-web application, "
                                + "server.port is ignored and /actuator/health is unreachable")
                .isInstanceOf(WebApplicationContext.class);
        assertThat(port).isGreaterThan(0);
    }

    @Test
    @DisplayName("GET /actuator/health returns 200 UP over HTTP")
    void healthEndpointAnswersOverHttp() {
        ResponseEntity<String> response =
                restTemplate.getForEntity(url("/actuator/health"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    @DisplayName("GET /actuator/prometheus is reachable — metrics were exported nowhere before")
    void prometheusEndpointIsReachable() {
        ResponseEntity<String> response =
                restTemplate.getForEntity(url("/actuator/prometheus"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("application=\"notification-service\"");
    }

    @Test
    @DisplayName("the datasource participates in the health verdict — this is what makes it fail")
    void datasourceContributesToHealth() {
        assertThat(contributorNames())
                .as(
                        "without a db contributor the endpoint reports UP with a dead database, "
                                + "which is the same lie as an always-red check with the sign flipped")
                .contains("db");
    }

    @Test
    @DisplayName("the Slack circuit breaker does NOT participate in the health verdict")
    void slackCircuitBreakerIsExcludedFromHealth() {
        assertThat(contributorNames())
                .as(
                        "Slack is an external webhook; in demo/e2e topologies it points at a "
                                + "deliberately unreachable stub. An open breaker must not make this "
                                + "container unhealthy — restarting it fixes nothing.")
                .noneMatch(name -> name.toLowerCase().contains("circuitbreaker"));
    }

    private Set<String> contributorNames() {
        return StreamSupport.stream(healthContributorRegistry.spliterator(), false)
                .map(entry -> entry.getName())
                .collect(Collectors.toSet());
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
