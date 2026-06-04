package com.example.erp.approval.infrastructure.masterdata;

import com.example.erp.approval.application.port.outbound.MasterDataPort;
import com.example.erp.approval.domain.request.ApprovalSubject;
import com.example.erp.approval.domain.request.SubjectType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Synchronous masterdata-service reference-integrity adapter (E1, ADR-MONO-005
 * Category B). Resolves the approval subject via
 * {@code GET /api/erp/masterdata/{departments|employees}/{id}} and reports
 * whether it EXISTS and is ACTIVE.
 *
 * <p>Outcome mapping (architecture.md § Reference Integrity model):
 * <ul>
 *   <li>200 + {@code status == ACTIVE} → {@code true} (submit proceeds).</li>
 *   <li>404 (subject absent) / 200 + {@code status == RETIRED} → {@code false}
 *       (submit refused → APPROVAL_ROUTE_INVALID, request stays DRAFT).</li>
 *   <li>transient 5xx / timeout / unreachable → {@code false} (submit refused;
 *       no inference, E1/E5 spirit; counter increments). A 404 is not retried
 *       (subject genuinely absent); transient failures degrade to refusal.</li>
 * </ul>
 */
@Slf4j
@Component
public class MasterDataRestAdapter implements MasterDataPort {

    private static final String STATUS_ACTIVE = "ACTIVE";

    private final RestClient restClient;
    private final Counter resolveFailures;

    public MasterDataRestAdapter(
            RestClient.Builder restClientBuilder,
            @Value("${erpplatform.approval.masterdata.base-url:http://masterdata-service:8080}")
            String baseUrl,
            MeterRegistry meterRegistry) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.resolveFailures = Counter.builder("approval_subject_resolve_failures_total")
                .description("masterdata subject reference-check failures (E1, Category B).")
                .register(meterRegistry);
    }

    @Override
    public boolean isSubjectActive(ApprovalSubject subject, String tenantId) {
        String path = pathFor(subject.subjectType()) + subject.subjectId();
        try {
            MasterEnvelope envelope = restClient.get()
                    .uri(path)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        // swallow — a 404 means the subject is absent (not ACTIVE).
                    })
                    .body(MasterEnvelope.class);
            if (envelope == null || envelope.data() == null) {
                return false;
            }
            return STATUS_ACTIVE.equalsIgnoreCase(envelope.data().status());
        } catch (Exception e) {
            // transient 5xx / timeout / unreachable → no inference, refuse submit.
            log.warn("masterdata subject resolve failed type={} id={}: {}",
                    subject.subjectType(), subject.subjectId(), e.getMessage());
            resolveFailures.increment();
            return false;
        }
    }

    private static String pathFor(SubjectType type) {
        return switch (type) {
            case DEPARTMENT -> "/api/erp/masterdata/departments/";
            case EMPLOYEE -> "/api/erp/masterdata/employees/";
        };
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MasterEnvelope(MasterData data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MasterData(String id, String status) {
    }
}
