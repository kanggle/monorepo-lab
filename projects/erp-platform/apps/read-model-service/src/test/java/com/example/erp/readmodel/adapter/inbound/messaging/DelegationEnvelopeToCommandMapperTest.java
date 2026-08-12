package com.example.erp.readmodel.adapter.inbound.messaging;

import com.example.erp.readmodel.application.command.DelegationFactCommand;
import com.example.erp.readmodel.domain.delegation.DelegationFactStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link DelegationEnvelopeToCommandMapper} scope extraction
 * (TASK-ERP-BE-018). The {@code scope}/{@code scopeRequestId} are extracted only on
 * a {@code delegated} (ACTIVE) event — a {@code revoked} restates neither (the
 * revoke payload carries no scope), and a {@code GLOBAL} grant carries no
 * {@code scopeRequestId}.
 *
 * <p><b>TASK-ERP-BE-043 — the fixtures now say {@code demo-corp}, not {@code erp}.</b>
 * They carried the literal {@code "erp"} because the mapper demanded it, so the
 * suite was green on a tenant value that <b>no erp row has ever had</b> (measured:
 * one distinct value across every erp table, {@code demo-corp}; zero rows carrying
 * {@code erp}) — a fixture more permissive than reality proves nothing about
 * reality. Running these fixtures against the pre-BE-043 mapper throws
 * {@code Non-erp tenant 'demo-corp'}, which is exactly the production failure.
 */
class DelegationEnvelopeToCommandMapperTest {

    /** The tenant every erp record actually carries (console assume-tenant). */
    private static final String REAL_TENANT = "demo-corp";

    private final ObjectMapper objectMapper =
            new ObjectMapper().registerModule(new JavaTimeModule());
    private final DelegationEnvelopeToCommandMapper mapper =
            new DelegationEnvelopeToCommandMapper(objectMapper);

    private static final String DELEGATED_REQUEST_JSON = """
            { "eventId": "evt-1", "eventType": "erp.approval.delegated",
              "occurredAt": "2026-06-01T00:00:00Z", "tenantId": "demo-corp",
              "source": "erp-platform-approval-service", "aggregateType": "DelegationGrant",
              "aggregateId": "dgr-1",
              "payload": { "grantId": "dgr-1", "delegatorId": "emp-a", "delegateId": "emp-d",
                "validFrom": "2026-06-01T00:00:00Z", "validTo": "2026-06-30T00:00:00Z",
                "reason": "vacation", "scope": "REQUEST", "scopeRequestId": "appr-1",
                "tenantId": "demo-corp", "occurredAt": "2026-06-01T00:00:00Z", "actor": "emp-a" } }
            """;

    private static final String DELEGATED_GLOBAL_JSON = """
            { "eventId": "evt-2", "eventType": "erp.approval.delegated",
              "occurredAt": "2026-06-01T00:00:00Z", "tenantId": "demo-corp",
              "source": "erp-platform-approval-service", "aggregateType": "DelegationGrant",
              "aggregateId": "dgr-2",
              "payload": { "grantId": "dgr-2", "delegatorId": "emp-a", "delegateId": "emp-d",
                "validFrom": "2026-06-01T00:00:00Z", "validTo": "2026-06-30T00:00:00Z",
                "reason": "vacation", "scope": "GLOBAL",
                "tenantId": "demo-corp", "occurredAt": "2026-06-01T00:00:00Z", "actor": "emp-a" } }
            """;

    private static final String REVOKED_JSON = """
            { "eventId": "evt-3", "eventType": "erp.approval.delegation.revoked",
              "occurredAt": "2026-06-10T00:00:00Z", "tenantId": "demo-corp",
              "source": "erp-platform-approval-service", "aggregateType": "DelegationGrant",
              "aggregateId": "dgr-1",
              "payload": { "grantId": "dgr-1", "delegatorId": "emp-a", "delegateId": "emp-d",
                "reason": "back", "tenantId": "demo-corp",
                "occurredAt": "2026-06-10T00:00:00Z", "actor": "emp-a" } }
            """;

    @Test
    void delegatedRequestCarriesScopeAndScopeRequestId() {
        DelegationFactCommand cmd = mapper.map(DELEGATED_REQUEST_JSON,
                "erp.approval.delegated.v1", DelegationFactStatus.ACTIVE);

        assertThat(cmd.status()).isEqualTo(DelegationFactStatus.ACTIVE);
        assertThat(cmd.scope()).isEqualTo("REQUEST");
        assertThat(cmd.scopeRequestId()).isEqualTo("appr-1");
        // TASK-ERP-BE-043 — the customer tenant is carried through, not rejected.
        assertThat(cmd.tenantId()).isEqualTo(REAL_TENANT);
    }

    /**
     * TASK-ERP-BE-043 / ADR-ERP-001 — D. The envelope tenant reaches the command
     * from {@code payload.tenantId} too (the approval producer writes it in both
     * places; a compacted / older wire may carry only the payload copy).
     */
    @Test
    void tenantIsResolvedFromThePayloadWhenTheEnvelopeOmitsIt() {
        String payloadOnly = DELEGATED_REQUEST_JSON
                .replace("\"occurredAt\": \"2026-06-01T00:00:00Z\", \"tenantId\": \"demo-corp\",",
                        "\"occurredAt\": \"2026-06-01T00:00:00Z\",");

        DelegationFactCommand cmd = mapper.map(payloadOnly,
                "erp.approval.delegated.v1", DelegationFactStatus.ACTIVE);

        assertThat(cmd.tenantId()).isEqualTo(REAL_TENANT);
    }

    /**
     * The one tenant condition that survives ADR-ERP-001 — D: an envelope that
     * names <b>no</b> tenant anywhere is still invalid → immediate DLT. Without
     * this the "carried, not compared" change would silently project rows with a
     * null tenant, which is the shape that lets the single-tenant ratchet be
     * evaded rather than enforced.
     */
    @Test
    void anEnvelopeThatNamesNoTenantAnywhereIsRejected() {
        String noTenant = DELEGATED_REQUEST_JSON.replace("\"tenantId\": \"demo-corp\",", "");

        assertThatThrownBy(() -> mapper.map(noTenant,
                "erp.approval.delegated.v1", DelegationFactStatus.ACTIVE))
                .isInstanceOf(InvalidEnvelopeException.class)
                .hasMessageContaining("Missing tenantId");
    }

    @Test
    void delegatedGlobalLeavesScopeRequestIdNull() {
        DelegationFactCommand cmd = mapper.map(DELEGATED_GLOBAL_JSON,
                "erp.approval.delegated.v1", DelegationFactStatus.ACTIVE);

        assertThat(cmd.scope()).isEqualTo("GLOBAL");
        assertThat(cmd.scopeRequestId()).isNull();
    }

    @Test
    void revokedCarriesNeitherScopeNorScopeRequestId() {
        DelegationFactCommand cmd = mapper.map(REVOKED_JSON,
                "erp.approval.delegation.revoked.v1", DelegationFactStatus.REVOKED);

        assertThat(cmd.status()).isEqualTo(DelegationFactStatus.REVOKED);
        assertThat(cmd.scope()).isNull();
        assertThat(cmd.scopeRequestId()).isNull();
    }
}
