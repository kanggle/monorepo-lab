package com.example.erp.notification.infrastructure.masterdata;

import com.example.erp.notification.application.port.outbound.CallerEmployeePort;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * masterdata {@code GET /api/erp/masterdata/employees/me} with the caller's own bearer token
 * (TASK-MONO-776, notification-api.md § v1.1). Mechanism copied from approval-service's
 * {@code MasterDataRestAdapter} (TASK-ERP-BE-041): propagate the caller's token (a workload
 * token would carry {@code tenant_id = erp} and see no {@code demo-corp} employee), classify by
 * status BEFORE decoding, and treat only 404 as an answer.
 *
 * <ul>
 *   <li>200 → LINKED (any status — a RETIRED employee still owns its old notifications).</li>
 *   <li>404 → NOT_LINKED (an answer: the inbox is empty).</li>
 *   <li>no credential / tenant or subject mismatch / 401 / 403 / other 4xx / 5xx / timeout →
 *       UNAVAILABLE, counted on {@code notification_recipient_resolve_failures_total{cause}} and
 *       logged at WARN.</li>
 * </ul>
 *
 * <p>🔵 The token-reading helper is now duplicated in two erp services (approval + here). The
 * approval adapter's javadoc named «a second service needs the same lines» as the trigger for
 * promoting it next to {@code libs/java-security-servlet}'s {@code ActorContextResolver}; that
 * trigger has fired. Promotion is a shared-library change and is left to a follow-up
 * (TASK-MONO-776 report) rather than folded into this slice.
 */
@Slf4j
@Component
public class MasterDataCallerEmployeeAdapter implements CallerEmployeePort {

    private static final String CLAIM_TENANT_ID = "tenant_id";
    static final String CAUSE_NO_CREDENTIALS = "no_credentials";
    static final String CAUSE_IDENTITY_MISMATCH = "identity_mismatch";
    static final String CAUSE_AUTH = "auth";
    static final String CAUSE_CLIENT_ERROR = "client_error";
    static final String CAUSE_UNREACHABLE = "unreachable";

    private final RestClient restClient;
    private final MeterRegistry meterRegistry;

    public MasterDataCallerEmployeeAdapter(
            @Value("${erpplatform.notification.masterdata.base-url:http://masterdata-service:8080}")
            String baseUrl,
            @Value("${erpplatform.notification.masterdata.connect-timeout-ms:2000}") long connectMs,
            @Value("${erpplatform.notification.masterdata.read-timeout-ms:3000}") long readMs,
            MeterRegistry meterRegistry) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(Duration.ofMillis(connectMs))
                .withReadTimeout(Duration.ofMillis(readMs));
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .build();
        this.meterRegistry = meterRegistry;
        // Pre-register so a dashboard sees «0», not «no metric».
        for (String cause : new String[]{CAUSE_NO_CREDENTIALS, CAUSE_IDENTITY_MISMATCH,
                CAUSE_AUTH, CAUSE_CLIENT_ERROR, CAUSE_UNREACHABLE}) {
            failures(cause);
        }
    }

    @Override
    public CallerEmployee resolve(String callerSub, String tenantId) {
        Jwt caller = currentCallerToken();
        if (caller == null) {
            return refuse(CAUSE_NO_CREDENTIALS, "no bearer token on the SecurityContext");
        }
        // A token with no tenant claim reaches here with the controller's fallback tenant; that
        // is not a divergence, so only a PRESENT claim that differs counts.
        String tokenTenant = caller.getClaimAsString(CLAIM_TENANT_ID);
        if ((tenantId != null && tokenTenant != null && !tenantId.equals(tokenTenant))
                || (callerSub != null && !callerSub.equals(caller.getSubject()))) {
            // `/me` answers for whoever's token goes out — refuse rather than read another
            // person's inbox under this caller's name.
            return refuse(CAUSE_IDENTITY_MISMATCH, "use-case caller/tenant != propagated token");
        }
        try {
            Envelope envelope = restClient.get()
                    .uri("/api/erp/masterdata/employees/me")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + caller.getTokenValue())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new StatusException(res.getStatusCode());
                    })
                    .body(Envelope.class);
            if (envelope == null || envelope.data() == null || envelope.data().id() == null) {
                return refuse(CAUSE_CLIENT_ERROR, "2xx without data.id");
            }
            return CallerEmployee.linked(envelope.data().id());
        } catch (StatusException e) {
            int code = e.status.value();
            if (code == HttpStatus.NOT_FOUND.value()) {
                return CallerEmployee.notLinked();
            }
            String cause = code == 401 || code == 403 ? CAUSE_AUTH
                    : e.status.is4xxClientError() ? CAUSE_CLIENT_ERROR : CAUSE_UNREACHABLE;
            return refuse(cause, "masterdata returned " + code);
        } catch (Exception e) {
            return refuse(CAUSE_UNREACHABLE, String.valueOf(e.getMessage()));
        }
    }

    private CallerEmployee refuse(String cause, String detail) {
        failures(cause).increment();
        log.warn("caller employee resolve failed cause={}: {}", cause, detail);
        return CallerEmployee.unavailable();
    }

    private Counter failures(String cause) {
        return Counter.builder("notification_recipient_resolve_failures_total")
                .description("masterdata /employees/me lookups that got no answer "
                        + "(a 404 = «not linked» is an answer and is not counted)")
                .tag("cause", cause)
                .register(meterRegistry);
    }

    private static Jwt currentCallerToken() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth instanceof JwtAuthenticationToken jwtAuth ? jwtAuth.getToken() : null;
    }

    private static final class StatusException extends RuntimeException {
        private final transient HttpStatusCode status;

        private StatusException(HttpStatusCode status) {
            super("masterdata responded " + status.value(), null, false, false);
            this.status = status;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Envelope(Data data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Data(String id, String status) {
    }
}
