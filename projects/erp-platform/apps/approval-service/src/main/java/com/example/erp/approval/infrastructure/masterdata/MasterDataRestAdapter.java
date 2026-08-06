package com.example.erp.approval.infrastructure.masterdata;

import com.example.erp.approval.application.port.outbound.MasterDataPort;
import com.example.erp.approval.domain.request.ApprovalSubject;
import com.example.erp.approval.domain.request.SubjectType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Synchronous masterdata-service reference-integrity adapter (E1, ADR-MONO-005
 * Category B). Resolves the approval subject via
 * {@code GET /api/erp/masterdata/{departments|employees}/{id}} and reports
 * whether it EXISTS and is ACTIVE.
 *
 * <h2>Caller-identity propagation (TASK-ERP-BE-041)</h2>
 *
 * masterdata-service is an independent OIDC <strong>resource server</strong> behind the
 * gateway ({@code ServiceLevelOAuth2Config}), not an unauthenticated internal endpoint.
 * This adapter therefore forwards the <strong>caller's own bearer token</strong>, read off
 * the request-scoped {@link JwtAuthenticationToken} the resource-server filter installed.
 *
 * <p>Propagation is the chosen mechanism over a {@code client_credentials} workload token
 * because the workload token is issued with {@code tenant_id = erp} and therefore cannot
 * see another tenant's masters at all — measured against the running demo stack, a
 * workload-token {@code GET /masterdata/departments} returns 200 with
 * {@code totalElements = 0} while the same query on the operator's assumed
 * {@code demo-corp} token returns the three seeded departments. A workload token would
 * have kept every single-tenant test green and left the demo broken. Propagation also
 * preserves the caller's data scope, so the reference check can never resolve a subject
 * the caller is not allowed to see.
 *
 * <p>The {@code tenantId} argument is not decoration: it is checked against the propagated
 * token's own {@code tenant_id} claim before the call goes out. A mismatch means the
 * request-scoped identity and the use case's tenant have diverged — the call is refused
 * rather than executed under whichever identity happens to be in the context.
 *
 * <h2>Outcome mapping (architecture.md § Reference Integrity model)</h2>
 * <ul>
 *   <li>200 + {@code status == ACTIVE} → {@code true} (submit proceeds).</li>
 *   <li><strong>404</strong> / 200 + {@code status == RETIRED} → {@code false}: the subject
 *       was <em>resolved</em> and the answer is "absent / not active". This is the only
 *       negative that is <strong>not</strong> a resolve failure, so it does not touch
 *       {@code approval_subject_resolve_failures_total}.</li>
 *   <li>401 / 403 / other 4xx / 5xx / timeout / unreachable / missing caller credential →
 *       {@code false} with the failure <strong>counted under its own {@code cause} tag and
 *       logged at WARN</strong>. Submit is still refused (no inference, E1/E5 spirit) but
 *       an operator can tell "we could not ask" from "we asked and the subject is gone".</li>
 * </ul>
 *
 * <p>Before TASK-ERP-BE-041 a blanket {@code onStatus(is4xxClientError, swallow)} folded
 * every 4xx into {@code envelope == null → false}, so the 401 produced by the missing
 * Authorization header surfaced to the operator as 422 {@code subject_unresolved} — an
 * authentication failure translated into a domain verdict about the customer's data, with
 * no counter and no log line anywhere. The status is now classified before the body is
 * touched.
 */
@Slf4j
@Component
public class MasterDataRestAdapter implements MasterDataPort {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String CLAIM_TENANT_ID = "tenant_id";

    /**
     * {@code cause} tag values for {@code approval_subject_resolve_failures_total}
     * (architecture.md § Observability). Every value here means "the reference check did
     * not get an answer" — a 404 is an answer and is deliberately absent from this list.
     */
    static final String CAUSE_NO_CREDENTIALS = "no_credentials";
    static final String CAUSE_TENANT_MISMATCH = "tenant_mismatch";
    static final String CAUSE_AUTH = "auth";
    static final String CAUSE_CLIENT_ERROR = "client_error";
    static final String CAUSE_UNREACHABLE = "unreachable";

    private final RestClient restClient;
    private final MeterRegistry meterRegistry;

    public MasterDataRestAdapter(
            RestClient.Builder restClientBuilder,
            @Value("${erpplatform.approval.masterdata.base-url:http://masterdata-service:8080}")
            String baseUrl,
            MeterRegistry meterRegistry) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.meterRegistry = meterRegistry;
        // Pre-register every cause so the series exists at zero — a dashboard that only
        // sees a tag once it first fires cannot distinguish "no failures" from "no metric".
        for (String cause : new String[]{CAUSE_NO_CREDENTIALS, CAUSE_TENANT_MISMATCH,
                CAUSE_AUTH, CAUSE_CLIENT_ERROR, CAUSE_UNREACHABLE}) {
            resolveFailures(cause);
        }
    }

    @Override
    public boolean isSubjectActive(ApprovalSubject subject, String tenantId) {
        Jwt caller = currentCallerToken();
        if (caller == null) {
            return refuse(CAUSE_NO_CREDENTIALS, subject,
                    "no bearer token on the SecurityContext — the masterdata call would go out "
                            + "anonymously and be rejected by its resource server");
        }
        String callerTenant = caller.getClaimAsString(CLAIM_TENANT_ID);
        if (tenantId != null && !tenantId.equals(callerTenant)) {
            return refuse(CAUSE_TENANT_MISMATCH, subject,
                    "use-case tenant '" + tenantId + "' != propagated token tenant '"
                            + callerTenant + "'");
        }

        String path = pathFor(subject.subjectType()) + subject.subjectId();
        try {
            MasterEnvelope envelope = restClient.get()
                    .uri(path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + caller.getTokenValue())
                    .retrieve()
                    // Classify by status BEFORE decoding: an error body is not a
                    // MasterEnvelope, and letting it fall through to the decoder would
                    // relabel every 4xx as a decode failure.
                    .onStatus(HttpStatusCode::isError,
                            (req, res) -> {
                                throw new SubjectResolveHttpException(res.getStatusCode());
                            })
                    .body(MasterEnvelope.class);
            if (envelope == null || envelope.data() == null) {
                return false;
            }
            return STATUS_ACTIVE.equalsIgnoreCase(envelope.data().status());
        } catch (SubjectResolveHttpException e) {
            if (e.status.value() == HttpStatus.NOT_FOUND.value()) {
                // The only negative that is an ANSWER: masterdata resolved the id and it
                // is absent. Not counted — see the CAUSE_* javadoc.
                return false;
            }
            return refuse(causeFor(e.status), subject, "masterdata returned " + e.status.value());
        } catch (Exception e) {
            // transient 5xx / timeout / unreachable → no inference, refuse submit.
            return refuse(CAUSE_UNREACHABLE, subject, String.valueOf(e.getMessage()));
        }
    }

    /**
     * The caller's verified token, or {@code null} when nothing OAuth2-shaped is on the
     * context. {@code ActorAuthenticationToken} (ADR-MONO-058 § D1) extends
     * {@link JwtAuthenticationToken}, so the erp actor principal is covered by this check
     * without the adapter depending on the actor type.
     *
     * <p>Kept local to erp rather than promoted next to
     * {@code libs/java-security-servlet}'s {@code ActorContextResolver}: approval-service
     * is currently the <strong>only</strong> outbound service-to-service caller in the
     * fleet that needs propagation. Promote when a <strong>second</strong> service needs
     * the same six lines — that count, not "it looks generic", is the trigger.
     */
    private static Jwt currentCallerToken() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken();
        }
        return null;
    }

    /** 401/403 are their own signal; any other non-404 4xx is a contract/routing defect. */
    private static String causeFor(HttpStatusCode status) {
        if (status.value() == HttpStatus.UNAUTHORIZED.value()
                || status.value() == HttpStatus.FORBIDDEN.value()) {
            return CAUSE_AUTH;
        }
        return status.is4xxClientError() ? CAUSE_CLIENT_ERROR : CAUSE_UNREACHABLE;
    }

    /** Count + log the failure, then refuse the submit (fail-closed, no inference). */
    private boolean refuse(String cause, ApprovalSubject subject, String detail) {
        resolveFailures(cause).increment();
        log.warn("masterdata subject resolve failed cause={} type={} id={}: {}",
                cause, subject.subjectType(), subject.subjectId(), detail);
        return false;
    }

    private Counter resolveFailures(String cause) {
        return Counter.builder("approval_subject_resolve_failures_total")
                .description("masterdata subject reference-check failures (E1, Category B). "
                        + "A 404 is an answer, not a failure, and is not counted here.")
                .tag("cause", cause)
                .register(meterRegistry);
    }

    private static String pathFor(SubjectType type) {
        return switch (type) {
            case DEPARTMENT -> "/api/erp/masterdata/departments/";
            case EMPLOYEE -> "/api/erp/masterdata/employees/";
        };
    }

    /** Carries the response status out of the {@code onStatus} handler for classification. */
    private static final class SubjectResolveHttpException extends RuntimeException {
        private final transient HttpStatusCode status;

        private SubjectResolveHttpException(HttpStatusCode status) {
            super("masterdata responded " + status.value(), null, false, false);
            this.status = status;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MasterEnvelope(MasterData data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MasterData(String id, String status) {
    }
}
