package com.example.erp.readmodel.adapter.inbound.messaging;

import com.example.erp.readmodel.application.command.DelegationFactCommand;
import com.example.erp.readmodel.domain.delegation.DelegationFactStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DelegationEnvelopeToCommandMapper} scope extraction
 * (TASK-ERP-BE-018). The {@code scope}/{@code scopeRequestId} are extracted only on
 * a {@code delegated} (ACTIVE) event — a {@code revoked} restates neither (the
 * revoke payload carries no scope), and a {@code GLOBAL} grant carries no
 * {@code scopeRequestId}.
 */
class DelegationEnvelopeToCommandMapperTest {

    private final ObjectMapper objectMapper =
            new ObjectMapper().registerModule(new JavaTimeModule());
    private final DelegationEnvelopeToCommandMapper mapper =
            new DelegationEnvelopeToCommandMapper(objectMapper, "erp");

    private static final String DELEGATED_REQUEST_JSON = """
            { "eventId": "evt-1", "eventType": "erp.approval.delegated",
              "occurredAt": "2026-06-01T00:00:00Z", "tenantId": "erp",
              "source": "erp-platform-approval-service", "aggregateType": "DelegationGrant",
              "aggregateId": "dgr-1",
              "payload": { "grantId": "dgr-1", "delegatorId": "emp-a", "delegateId": "emp-d",
                "validFrom": "2026-06-01T00:00:00Z", "validTo": "2026-06-30T00:00:00Z",
                "reason": "vacation", "scope": "REQUEST", "scopeRequestId": "appr-1",
                "tenantId": "erp", "occurredAt": "2026-06-01T00:00:00Z", "actor": "emp-a" } }
            """;

    private static final String DELEGATED_GLOBAL_JSON = """
            { "eventId": "evt-2", "eventType": "erp.approval.delegated",
              "occurredAt": "2026-06-01T00:00:00Z", "tenantId": "erp",
              "source": "erp-platform-approval-service", "aggregateType": "DelegationGrant",
              "aggregateId": "dgr-2",
              "payload": { "grantId": "dgr-2", "delegatorId": "emp-a", "delegateId": "emp-d",
                "validFrom": "2026-06-01T00:00:00Z", "validTo": "2026-06-30T00:00:00Z",
                "reason": "vacation", "scope": "GLOBAL",
                "tenantId": "erp", "occurredAt": "2026-06-01T00:00:00Z", "actor": "emp-a" } }
            """;

    private static final String REVOKED_JSON = """
            { "eventId": "evt-3", "eventType": "erp.approval.delegation.revoked",
              "occurredAt": "2026-06-10T00:00:00Z", "tenantId": "erp",
              "source": "erp-platform-approval-service", "aggregateType": "DelegationGrant",
              "aggregateId": "dgr-1",
              "payload": { "grantId": "dgr-1", "delegatorId": "emp-a", "delegateId": "emp-d",
                "reason": "back", "tenantId": "erp",
                "occurredAt": "2026-06-10T00:00:00Z", "actor": "emp-a" } }
            """;

    @Test
    void delegatedRequestCarriesScopeAndScopeRequestId() {
        DelegationFactCommand cmd = mapper.map(DELEGATED_REQUEST_JSON,
                "erp.approval.delegated.v1", DelegationFactStatus.ACTIVE);

        assertThat(cmd.status()).isEqualTo(DelegationFactStatus.ACTIVE);
        assertThat(cmd.scope()).isEqualTo("REQUEST");
        assertThat(cmd.scopeRequestId()).isEqualTo("appr-1");
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
