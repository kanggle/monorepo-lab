package com.example.account.application.service;

import com.example.account.application.exception.ConsumerPoolDisabledException;
import com.example.account.application.port.AuthServicePort;
import com.example.account.application.port.ConsumerPoolFlag;
import com.example.account.application.result.ConsumerPoolLegacyMoveResult;
import com.example.account.application.result.LegacyMoveOutcome;
import com.example.account.domain.consumerpool.LegacyMoveCandidate;
import com.example.account.domain.repository.ConsumerPoolLegacyMoveRepository;
import com.example.account.domain.repository.TenantRepository;
import com.example.account.domain.tenant.Tenant;
import com.example.account.domain.tenant.TenantId;
import com.example.account.domain.tenant.TenantStatus;
import com.example.account.domain.tenant.TenantType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * TASK-BE-618 — the batch orchestrator of the consumer-pool legacy move: flag gate, per-account isolation
 * (a refusal is a skip, any other exception a failure, neither stops the run), the report and the cursor.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("ConsumerPoolLegacyMoveUseCase (TASK-BE-618)")
class ConsumerPoolLegacyMoveUseCaseTest {

    private static final TenantId FAN = new TenantId("fan-platform");
    private static final TenantId SHOP = new TenantId("ecommerce");

    @Mock private ConsumerPoolFlag flag;
    @Mock private TenantRepository tenantRepository;
    @Mock private ConsumerPoolLegacyMoveRepository moveRepository;
    @Mock private ConsumerPoolLegacyAccountMover mover;
    @InjectMocks private ConsumerPoolLegacyMoveUseCase useCase;

    private static Tenant tenant(String id) {
        return Tenant.reconstitute(new TenantId(id), id, TenantType.B2C_CONSUMER, TenantStatus.ACTIVE,
                Instant.EPOCH, Instant.EPOCH);
    }

    private void consumerTenants() {
        given(tenantRepository.findAllByTenantType(TenantType.B2C_CONSUMER))
                .willReturn(List.of(tenant("fan-platform"), tenant("ecommerce"), tenant("consumer-pool")));
    }

    @Test
    @DisplayName("플래그 꺼짐 → CONSUMER_POOL_DISABLED · 후보 조회도 이동도 없다")
    void flagOff_refusesWholeRun() {
        given(flag.isEnabled()).willReturn(false);

        assertThatThrownBy(() -> useCase.execute(null, null)).isInstanceOf(ConsumerPoolDisabledException.class);
        verifyNoInteractions(tenantRepository, moveRepository, mover);
    }

    @Test
    @DisplayName("후보는 소비자 사이트만(풀 제외) · 이동/건너뜀/거절/실패를 계정마다 따로 집계 · 한 계정의 실패가 다음 계정을 막지 않는다")
    void aggregatesEachAccountIndependently() {
        given(flag.isEnabled()).willReturn(true);
        consumerTenants();
        List<TenantId> sites = List.of(FAN, SHOP);
        given(moveRepository.findCandidates(sites, null, ConsumerPoolLegacyMoveUseCase.DEFAULT_LIMIT))
                .willReturn(List.of(
                        new LegacyMoveCandidate("a1", FAN),
                        new LegacyMoveCandidate("a2", SHOP),
                        new LegacyMoveCandidate("a3", FAN),
                        new LegacyMoveCandidate("a4", FAN)));
        given(mover.move(FAN, "a1", sites)).willReturn(LegacyMoveOutcome.MOVED);
        given(mover.move(SHOP, "a2", sites)).willReturn(LegacyMoveOutcome.SELLER);
        given(mover.move(FAN, "a3", sites))
                .willThrow(new AuthServicePort.CredentialPoolMoveRefused("a3", "SOCIAL_LINKED"));
        given(mover.move(FAN, "a4", sites))
                .willThrow(new AuthServicePort.AuthServiceUnavailable("down", null));

        ConsumerPoolLegacyMoveResult result = useCase.execute(null, null);

        assertThat(result.scanned()).isEqualTo(4);
        assertThat(result.moved()).isEqualTo(1);
        assertThat(result.movedAccountIds()).containsExactly("a1");
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.failedAccountIds()).containsExactly("a4");
        assertThat(result.skipped()).containsEntry("SELLER", 1).containsEntry("SOCIAL_LINKED", 1)
                .containsEntry("TWO_SITE", 0).doesNotContainKey("MOVED");
        assertThat(result.skipped()).hasSize(LegacyMoveOutcome.values().length - 1);
        assertThat(result.nextAfterAccountId()).as("fewer than the limit → the end was reached").isNull();
    }

    @Test
    @DisplayName("limit 을 채우면 nextAfterAccountId = 마지막 id · 커서는 그대로 저장소에 전달")
    void fullPage_returnsCursor() {
        given(flag.isEnabled()).willReturn(true);
        consumerTenants();
        List<TenantId> sites = List.of(FAN, SHOP);
        given(moveRepository.findCandidates(eq(sites), eq("a0"), eq(2)))
                .willReturn(List.of(new LegacyMoveCandidate("a1", FAN), new LegacyMoveCandidate("a2", FAN)));
        given(mover.move(FAN, "a1", sites)).willReturn(LegacyMoveOutcome.TWO_SITE);
        given(mover.move(FAN, "a2", sites)).willReturn(LegacyMoveOutcome.TWO_SITE);

        ConsumerPoolLegacyMoveResult result = useCase.execute(2, "a0");

        assertThat(result.scanned()).isEqualTo(2);
        assertThat(result.nextAfterAccountId()).isEqualTo("a2");
        assertThat(result.skipped()).containsEntry("TWO_SITE", 2);
    }

    @Test
    @DisplayName("limit 범위 밖 → IllegalArgumentException (컨트롤러가 먼저 400)")
    void limitOutOfRange() {
        given(flag.isEnabled()).willReturn(true);

        assertThatThrownBy(() -> useCase.execute(1001, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.execute(0, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
