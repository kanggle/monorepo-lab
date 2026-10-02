package com.example.account.application.service;

import com.example.account.application.port.AuthServicePort;
import com.example.account.application.result.LegacyMoveOutcome;
import com.example.account.domain.consumerpool.LegacySiteAccount;
import com.example.account.domain.repository.AccountRepository;
import com.example.account.domain.repository.ConsumerPoolLegacyMoveRepository;
import com.example.account.domain.status.AccountStatus;
import com.example.account.domain.tenant.TenantId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * TASK-BE-618 — one account's move: the binding order (lock → checks → membership → roles copy → roles delete
 * → tenant change → auth LAST), the account-side skips (nothing written), and the auth refusal propagating
 * (so the transaction rolls back). The rollback itself is a real-database property — see
 * {@code ConsumerPoolLegacyMoveIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("ConsumerPoolLegacyAccountMover (TASK-BE-618)")
class ConsumerPoolLegacyAccountMoverTest {

    private static final TenantId FAN = new TenantId("fan-platform");
    private static final TenantId SHOP = new TenantId("ecommerce");
    private static final List<TenantId> SITES = List.of(FAN, SHOP);
    private static final String ACCOUNT = "0199de70-0000-7000-8000-000000618001";
    private static final String IDENTITY = "0199de71-0000-7000-8000-000000618001";
    private static final String EMAIL = "fan-618@example.com";

    @Mock private ConsumerPoolLegacyMoveRepository moveRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private AuthServicePort authServicePort;
    @InjectMocks private ConsumerPoolLegacyAccountMover mover;

    private void lockedFanAccount() {
        given(moveRepository.lockSiteAccount(FAN, ACCOUNT)).willReturn(Optional.of(new LegacySiteAccount(
                ACCOUNT, FAN, EMAIL, AccountStatus.ACTIVE, IDENTITY, Instant.parse("2026-01-01T00:00:00Z"))));
    }

    private void noAccountSideSkip() {
        given(moveRepository.findSiteRoleNames(FAN, ACCOUNT)).willReturn(List.of("ARTIST", "FAN"));
        given(accountRepository.existsByEmail(SHOP, EMAIL)).willReturn(false);
        given(accountRepository.existsByEmail(TenantId.CONSUMER_POOL, EMAIL)).willReturn(false);
        given(moveRepository.identityWouldConflict(FAN, IDENTITY, ACCOUNT)).willReturn(false);
        given(moveRepository.moveAccountRowsToPool(FAN, ACCOUNT, IDENTITY)).willReturn(1);
    }

    @Test
    @DisplayName("이동: 멤버십 → 역할 복사 → 역할 삭제(테넌트 변경 전) → 테넌트 변경 → auth 이동(마지막)")
    void move_bindingOrder_authLast() {
        lockedFanAccount();
        noAccountSideSkip();

        assertThat(mover.move(FAN, ACCOUNT, SITES)).isEqualTo(LegacyMoveOutcome.MOVED);

        InOrder order = inOrder(moveRepository, authServicePort);
        order.verify(moveRepository).lockSiteAccount(FAN, ACCOUNT);
        order.verify(moveRepository).insertActiveMembershipConsentedAtCreation(FAN, ACCOUNT);
        order.verify(moveRepository).copySiteRolesToConsumerSiteRoles(FAN, ACCOUNT);
        order.verify(moveRepository).deleteSiteRoles(FAN, ACCOUNT);
        order.verify(moveRepository).moveAccountRowsToPool(FAN, ACCOUNT, IDENTITY);
        order.verify(authServicePort).moveCredentialToConsumerPool(ACCOUNT, "fan-platform");
    }

    @Test
    @DisplayName("auth 거절 → 예외가 그대로 나간다(트랜잭션 롤백) · 쓰기 단계는 이미 호출됨")
    void authRefusal_propagates() {
        lockedFanAccount();
        noAccountSideSkip();
        willThrow(new AuthServicePort.CredentialPoolMoveRefused(ACCOUNT, "OPERATOR_FACETED"))
                .given(authServicePort).moveCredentialToConsumerPool(ACCOUNT, "fan-platform");

        assertThatThrownBy(() -> mover.move(FAN, ACCOUNT, SITES))
                .isInstanceOf(AuthServicePort.CredentialPoolMoveRefused.class);
    }

    @Test
    @DisplayName("잠그고 보니 더는 사이트 계정이 아님 → NO_LONGER_CANDIDATE · 아무것도 쓰지 않는다")
    void noLongerCandidate() {
        given(moveRepository.lockSiteAccount(FAN, ACCOUNT)).willReturn(Optional.empty());

        assertThat(mover.move(FAN, ACCOUNT, SITES)).isEqualTo(LegacyMoveOutcome.NO_LONGER_CANDIDATE);
        verify(moveRepository, never()).insertActiveMembershipConsentedAtCreation(any(), anyString());
        verifyNoInteractions(authServicePort);
    }

    @Test
    @DisplayName("SELLER 역할 → SELLER · 아무것도 쓰지 않고 auth 도 부르지 않는다")
    void seller_skipped() {
        lockedFanAccount();
        given(moveRepository.findSiteRoleNames(FAN, ACCOUNT)).willReturn(List.of("SELLER"));

        assertThat(mover.move(FAN, ACCOUNT, SITES)).isEqualTo(LegacyMoveOutcome.SELLER);
        verify(moveRepository, never()).insertActiveMembershipConsentedAtCreation(any(), anyString());
        verifyNoInteractions(authServicePort);
    }

    @Test
    @DisplayName("같은 이메일이 다른 소비자 사이트에 → TWO_SITE")
    void twoSite_skipped() {
        lockedFanAccount();
        given(moveRepository.findSiteRoleNames(FAN, ACCOUNT)).willReturn(List.of());
        given(accountRepository.existsByEmail(SHOP, EMAIL)).willReturn(true);

        assertThat(mover.move(FAN, ACCOUNT, SITES)).isEqualTo(LegacyMoveOutcome.TWO_SITE);
        verifyNoInteractions(authServicePort);
    }

    @Test
    @DisplayName("같은 이메일의 풀 계정 → POOL_EMAIL_EXISTS")
    void poolEmailExists_skipped() {
        lockedFanAccount();
        given(moveRepository.findSiteRoleNames(FAN, ACCOUNT)).willReturn(List.of());
        given(accountRepository.existsByEmail(SHOP, EMAIL)).willReturn(false);
        given(accountRepository.existsByEmail(TenantId.CONSUMER_POOL, EMAIL)).willReturn(true);

        assertThat(mover.move(FAN, ACCOUNT, SITES)).isEqualTo(LegacyMoveOutcome.POOL_EMAIL_EXISTS);
        verifyNoInteractions(authServicePort);
    }

    @Test
    @DisplayName("신원 충돌 → IDENTITY_CONFLICT")
    void identityConflict_skipped() {
        lockedFanAccount();
        given(moveRepository.findSiteRoleNames(FAN, ACCOUNT)).willReturn(List.of());
        given(accountRepository.existsByEmail(SHOP, EMAIL)).willReturn(false);
        given(accountRepository.existsByEmail(TenantId.CONSUMER_POOL, EMAIL)).willReturn(false);
        given(moveRepository.identityWouldConflict(FAN, IDENTITY, ACCOUNT)).willReturn(true);

        assertThat(mover.move(FAN, ACCOUNT, SITES)).isEqualTo(LegacyMoveOutcome.IDENTITY_CONFLICT);
        verifyNoInteractions(authServicePort);
    }
}
