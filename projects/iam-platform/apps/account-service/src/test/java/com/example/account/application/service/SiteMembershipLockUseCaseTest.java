package com.example.account.application.service;

import com.example.account.application.exception.AccountNotFoundException;
import com.example.account.application.result.GdprDeleteResult;
import com.example.account.application.result.StatusChangeResult;
import com.example.account.domain.account.Account;
import com.example.account.domain.consumerpool.ConsumerSiteLeftBy;
import com.example.account.domain.consumerpool.ConsumerSiteMembership;
import com.example.account.domain.consumerpool.ConsumerSiteMembershipStatus;
import com.example.account.domain.repository.ConsumerSiteMembershipRepository;
import com.example.account.domain.status.AccountStatus;
import com.example.account.domain.status.StateTransitionException;
import com.example.account.domain.status.StatusChangeReason;
import com.example.account.domain.tenant.TenantId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * TASK-BE-621 — a site operator's lock / unlock of a pool member writes ONE site's membership and nothing
 * else (no account change, no event — the use case owns no account repository or event publisher at all).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("SiteMembershipLockUseCase (TASK-BE-621)")
class SiteMembershipLockUseCaseTest {

    private static final String ACCOUNT = "0199de70-0000-7000-8000-000000621000";
    private static final TenantId STORE = new TenantId("ecommerce");

    @Mock private ConsumerSiteMembershipRepository membershipRepository;
    @InjectMocks private SiteMembershipLockUseCase useCase;

    private static Account poolAccount(AccountStatus status) {
        return Account.reconstitute(ACCOUNT, TenantId.CONSUMER_POOL, "pool@example.com", null,
                status, Instant.EPOCH, Instant.EPOCH, null, null, null, 0);
    }

    private static ConsumerSiteMembership active() {
        return ConsumerSiteMembership.joinOnSignup(ACCOUNT, STORE, Instant.EPOCH);
    }

    @Test
    @DisplayName("잠금: ACTIVE 멤버십 → LOCKED (운영자 · 시각 기록) · scope=SITE_MEMBERSHIP · 계정은 ACTIVE 그대로")
    void lock_activeMembership_becomesLocked() {
        Account account = poolAccount(AccountStatus.ACTIVE);
        given(membershipRepository.find(STORE, ACCOUNT)).willReturn(Optional.of(active()));
        given(membershipRepository.update(any())).willReturn(true);

        StatusChangeResult r = useCase.execute(STORE, account, AccountStatus.LOCKED,
                StatusChangeReason.ADMIN_LOCK, "op-store");

        ArgumentCaptor<ConsumerSiteMembership> written = ArgumentCaptor.forClass(ConsumerSiteMembership.class);
        verify(membershipRepository).update(written.capture());
        assertThat(written.getValue().getSiteTenantId()).isEqualTo(STORE);
        assertThat(written.getValue().getStatus()).isEqualTo(ConsumerSiteMembershipStatus.LOCKED);
        assertThat(written.getValue().getLockedByActorId()).isEqualTo("op-store");
        assertThat(written.getValue().getLockedAt()).isNotNull();
        assertThat(r.scope()).isEqualTo(GdprDeleteResult.SCOPE_SITE_MEMBERSHIP);
        assertThat(r.siteTenantId()).isEqualTo("ecommerce");
        assertThat(r.previousStatus()).isEqualTo("ACTIVE");
        assertThat(r.currentStatus()).isEqualTo("LOCKED");
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        verify(membershipRepository, never()).removeAllSiteRoles(any(), any());
    }

    @Test
    @DisplayName("잠금 멱등: 이미 LOCKED → 쓰기 0 · 200 (LOCKED/LOCKED)")
    void lock_alreadyLocked_isNoOp() {
        given(membershipRepository.find(STORE, ACCOUNT)).willReturn(Optional.of(
                active().lockBySiteOperator("op-store", Instant.EPOCH)));

        StatusChangeResult r = useCase.execute(STORE, poolAccount(AccountStatus.ACTIVE), AccountStatus.LOCKED,
                StatusChangeReason.ADMIN_LOCK, "op-store");

        assertThat(r.previousStatus()).isEqualTo("LOCKED");
        assertThat(r.currentStatus()).isEqualTo("LOCKED");
        verify(membershipRepository, never()).update(any());
    }

    @Test
    @DisplayName("해제: LOCKED → ACTIVE · 잠금 기록 지움")
    void unlock_lockedMembership_becomesActive() {
        given(membershipRepository.find(STORE, ACCOUNT)).willReturn(Optional.of(
                active().lockBySiteOperator("op-store", Instant.EPOCH)));
        given(membershipRepository.update(any())).willReturn(true);

        StatusChangeResult r = useCase.execute(STORE, poolAccount(AccountStatus.ACTIVE), AccountStatus.ACTIVE,
                StatusChangeReason.ADMIN_UNLOCK, "op-store");

        ArgumentCaptor<ConsumerSiteMembership> written = ArgumentCaptor.forClass(ConsumerSiteMembership.class);
        verify(membershipRepository).update(written.capture());
        assertThat(written.getValue().isActive()).isTrue();
        assertThat(written.getValue().getLockedAt()).isNull();
        assertThat(r.previousStatus()).isEqualTo("LOCKED");
        assertThat(r.currentStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("D-3: 계정 전체가 LOCKED 인 풀 멤버를 사이트 운영자가 해제 → 멤버십만 본다(ACTIVE 그대로 · 쓰기 0) · 계정 LOCKED 그대로")
    void unlock_bySiteOperator_cannotLiftAWholeAccountLock() {
        Account wholeLocked = poolAccount(AccountStatus.LOCKED);
        given(membershipRepository.find(STORE, ACCOUNT)).willReturn(Optional.of(active()));

        StatusChangeResult r = useCase.execute(STORE, wholeLocked, AccountStatus.ACTIVE,
                StatusChangeReason.ADMIN_UNLOCK, "op-store");

        assertThat(r.scope()).isEqualTo(GdprDeleteResult.SCOPE_SITE_MEMBERSHIP);
        assertThat(r.currentStatus()).isEqualTo("ACTIVE");
        assertThat(wholeLocked.getStatus()).isEqualTo(AccountStatus.LOCKED);
        verify(membershipRepository, never()).update(any());
    }

    @Test
    @DisplayName("LEFT 멤버십 · 멤버십 없음 → 404 (그 사이트 멤버가 아니다) · 쓰기 0")
    void leftOrMissing_notFound() {
        given(membershipRepository.find(STORE, ACCOUNT)).willReturn(Optional.of(
                active().leave(ConsumerSiteLeftBy.OPERATOR, "op-store", Instant.EPOCH)));

        assertThatThrownBy(() -> useCase.execute(STORE, poolAccount(AccountStatus.ACTIVE), AccountStatus.LOCKED,
                StatusChangeReason.ADMIN_LOCK, "op-store"))
                .isInstanceOf(AccountNotFoundException.class);
        verify(membershipRepository, never()).update(any());
    }

    @Test
    @DisplayName("D-8: 계정이 DELETED 인 풀 멤버의 사이트 잠금 → 409 STATE_TRANSITION_INVALID · 멤버십 조회도 없다")
    void deletedAccount_isRefused() {
        assertThatThrownBy(() -> useCase.execute(STORE, poolAccount(AccountStatus.DELETED), AccountStatus.LOCKED,
                StatusChangeReason.ADMIN_LOCK, "op-store"))
                .isInstanceOf(StateTransitionException.class);
        verifyNoInteractions(membershipRepository);
    }

    @Test
    @DisplayName("LOCKED/ACTIVE 가 아닌 목표(예: DORMANT) → 409 · 쓰기 0")
    void otherTarget_isRefused() {
        assertThatThrownBy(() -> useCase.execute(STORE, poolAccount(AccountStatus.ACTIVE), AccountStatus.DORMANT,
                StatusChangeReason.DORMANT_365D, "op-store"))
                .isInstanceOf(StateTransitionException.class);
        verifyNoInteractions(membershipRepository);
    }
}
