package com.example.gateway.route;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Reactive health indicator that checks reachability of downstream services
 * (auth-service, account-service) configured in gateway routes.
 * Returns UP if all upstreams are reachable, DOWN otherwise.
 * A single check failure is treated as WARNING (not DOWN) per spec.
 */
@Slf4j
@Component
public class UpstreamHealthIndicator implements ReactiveHealthIndicator {

    private static final Duration CHECK_TIMEOUT = Duration.ofSeconds(3);

    private final RouteDefinitionLocator routeDefinitionLocator;
    private final WebClient.Builder webClientBuilder;

    public UpstreamHealthIndicator(RouteDefinitionLocator routeDefinitionLocator,
                                   WebClient.Builder webClientBuilder) {
        this.routeDefinitionLocator = routeDefinitionLocator;
        this.webClientBuilder = webClientBuilder;
    }

    @Override
    public Mono<Health> health() {
        return routeDefinitionLocator.getRouteDefinitions()
                .collectList()
                .flatMap(routes -> {
                    if (routes.isEmpty()) {
                        return Mono.just(Health.up().withDetail("upstreams", "none configured").build());
                    }

                    return Flux.fromIterable(routes)
                            .flatMap(this::checkUpstream)
                            .collectList()
                            .map(results -> {
                                Map<String, Object> details = new HashMap<>();
                                boolean anyDown = false;

                                for (UpstreamStatus status : results) {
                                    details.put(status.routeId(), status.reachable() ? "UP" : "DOWN");
                                    if (!status.reachable()) {
                                        anyDown = true;
                                    }
                                }

                                if (anyDown) {
                                    // Single check failure = WARNING, not DOWN
                                    return Health.status("WARNING")
                                            .withDetails(details)
                                            .build();
                                }
                                return Health.up().withDetails(details).build();
                            });
                })
                .onErrorResume(e -> {
                    log.warn("Upstream health check failed: {}", e.getMessage());
                    return Mono.just(Health.status("WARNING")
                            .withDetail("error", e.getMessage())
                            .build());
                });
    }

    private Mono<UpstreamStatus> checkUpstream(RouteDefinition route) {
        URI uri = route.getUri();
        String routeId = route.getId();

        // Only check http/https URIs
        if (uri == null || (!uri.getScheme().startsWith("http"))) {
            return Mono.just(new UpstreamStatus(routeId, true));
        }

        String healthUrl = uri.toString();
        // Try actuator health endpoint of downstream
        if (!healthUrl.endsWith("/")) {
            healthUrl += "/";
        }
        healthUrl += "actuator/health";

        return webClientBuilder.build()
                .get()
                .uri(healthUrl)
                .retrieve()
                .toBodilessEntity()
                .map(response -> new UpstreamStatus(routeId, response.getStatusCode().is2xxSuccessful()))
                .timeout(CHECK_TIMEOUT)
                .onErrorResume(e -> {
                    log.debug("Upstream {} health check failed: {}", routeId, e.getMessage());
                    return Mono.just(new UpstreamStatus(routeId, false));
                });
    }

    private record UpstreamStatus(String routeId, boolean reachable) {
    }
}
