package com.example.admin.infrastructure.persistence.rbac;

import com.example.admin.application.port.AdminOperatorPort.OperatorLookupView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-MONO-777 — the adapter half of {@code GET /api/admin/operators/lookup}: the
 * {@code confined_tenant_id} filter (TASK-MONO-751 — a confined operator can enter ONLY its
 * confined tenant, so it can never accept a link in another one), the {@code '*'} branch, the
 * projection ({@code oidc_subject} → accountId) and the deterministic order.
 */
@ExtendWith(MockitoExtension.class)
class JpaAdminOperatorAdapterLookupTest {

    @Mock
    AdminOperatorJpaRepository operatorRepository;
    @Mock
    AdminRoleJpaRepository roleRepository;
    @Mock
    AdminOperatorRoleJpaRepository operatorRoleRepository;

    @InjectMocks
    JpaAdminOperatorAdapter adapter;

    private static AdminOperatorJpaEntity op(String home, String subject, String confinedTo) {
        AdminOperatorJpaEntity e = AdminOperatorJpaEntity.create(
                "op-" + home, "same@x.example", null, "Op " + home, "ACTIVE", home, Instant.now());
        e.setOidcSubject(subject, Instant.now());
        ReflectionTestUtils.setField(e, "confinedTenantId", confinedTo);
        return e;
    }

    @Test
    void tenant_scope_drops_operators_confined_elsewhere_and_sorts_by_home_tenant() {
        when(operatorRepository.findLookupCandidatesInTenant("demo-corp", "same@x.example"))
                .thenReturn(List.of(
                        op("zeta-corp", "acc-z", null),          // assigned, unconfined → kept
                        op("demo-corp", "acc-d", "demo-corp"),   // confined to THIS tenant → kept
                        op("beta-corp", "acc-b", "beta-corp"))); // confined elsewhere → dropped

        List<OperatorLookupView> out = adapter.findLookupCandidates("demo-corp", "same@x.example");

        assertThat(out).containsExactly(
                new OperatorLookupView("acc-d", "Op demo-corp", "demo-corp"),
                new OperatorLookupView("acc-z", "Op zeta-corp", "zeta-corp"));
        verify(operatorRepository, never()).findLookupCandidatesAnyTenant(anyString());
    }

    @Test
    void platform_scope_reads_every_tenant_without_the_confinement_filter() {
        when(operatorRepository.findLookupCandidatesAnyTenant("same@x.example"))
                .thenReturn(List.of(op("beta-corp", "acc-b", "beta-corp")));

        assertThat(adapter.findLookupCandidates("*", "same@x.example"))
                .containsExactly(new OperatorLookupView("acc-b", "Op beta-corp", "beta-corp"));
    }
}
