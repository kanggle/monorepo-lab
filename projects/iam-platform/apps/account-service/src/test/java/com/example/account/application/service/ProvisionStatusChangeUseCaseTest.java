package com.example.account.application.service;

import com.example.account.application.event.AccountEventPublisher;
import com.example.account.application.exception.AccountNotFoundException;
import com.example.account.application.port.ConsumerPoolFlag;
import com.example.account.application.result.GdprDeleteResult;
import com.example.account.application.result.ProvisionedStatusChangeResult;
import com.example.account.domain.account.Account;
import com.example.account.domain.consumerpool.ConsumerSiteLeftBy;
import com.example.account.domain.consumerpool.ConsumerSiteMembership;
import com.example.account.domain.consumerpool.ConsumerSiteMembershipStatus;
import com.example.account.domain.history.AccountStatusHistoryEntry;
import com.example.account.domain.repository.AccountRepository;
import com.example.account.domain.repository.AccountStatusHistoryRepository;
import com.example.account.domain.repository.ConsumerSiteMembershipRepository;
import com.example.account.domain.repository.TenantRepository;
import com.example.account.domain.status.AccountStatus;
import com.example.account.domain.status.AccountStatusMachine;
import com.example.account.domain.status.StateTransitionException;
import com.example.account.domain.tenant.Tenant;
import com.example.account.domain.tenant.TenantId;
import com.example.account.domain.tenant.TenantStatus;
import com.example.account.domain.tenant.TenantType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * TASK-BE-622 — {@code PATCH /internal/tenants/{t}/accounts/{id}/status} by a site backend changes only the path
 * site's membership of a consumer-POOL member (621's lock / unlock, 619's leave), never the pool account; a site's
 * OWN account (the seller-operator account — the only production caller's target) changes as before.
 *
 * <p>The 621 / 619 use cases are the REAL ones over mocked repositories, so a cell here fails if the site branch
 * is gone (the account would be saved, a history row written, an event published).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("TASK-BE-622 — 프로비저닝 상태 PATCH: 풀 멤버는 그 사이트 멤버십만 · 사이트 자기 계정은 계정")
class ProvisionStatusChangeUseCaseTest {

    private static final String POOL_ACCOUNT = "0199de70-0000-7000-8000-000000622000";
    private static final String SELLER_ACCOUNT = "0199de70-0000-7000-8000-000000622001";
    private static final TenantId STORE = new TenantId("ecommerce");
    private static final ConsumerPoolFlag ON = () -> true;
    private static final ConsumerPoolFlag OFF = () -> false;

    @Mock private TenantRepository tenantRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private AccountStatusHistoryRepository historyRepository;
    @Mock private AccountEventPublisher eventPublisher;
    @Mock private ConsumerSiteMembershipRepository membershipRepository;

    private ProvisionStatusChangeUseCase useCase(ConsumerPoolFlag flag) {
        return new ProvisionStatusChangeUseCase(tenantRepository, accountRepository, historyRepository,
                new AccountStatusMachine(), eventPublisher, flag,
                new SiteMembershipLockUseCase(membershipRepository),
                new LeaveConsumerSiteUseCase(tenantRepository, accountRepository, membershipRepository),
                membershipRepository);
    }

    private void storeExists() {
        given(tenantRepository.findById(STORE)).willReturn(Optional.of(Tenant.reconstitute(
                STORE, "ecommerce", TenantType.B2C_CONSUMER, TenantStatus.ACTIVE, Instant.EPOCH, Instant.EPOCH)));
    }

    private static Account poolAccount(AccountStatus status) {
        return Account.reconstitute(POOL_ACCOUNT, TenantId.CONSUMER_POOL, "pool@example.com", null,
                status, Instant.EPOCH, Instant.EPOCH, null, null, null, 0);
    }

    /** The ecommerce seller-operator account — a site's own account (tenant = the path), not a pool account. */
    private static Account sellerOperatorAccount() {
        return Account.reconstitute(SELLER_ACCOUNT, STORE, "seller+ecommerce+s-1@marketplace.local", null,
                AccountStatus.ACTIVE, Instant.EPOCH, Instant.EPOCH, null, null, null, 0);
    }

    private static ConsumerSiteMembership storeMembership(ConsumerSiteMembershipStatus status) {
        return switch (status) {
            case ACTIVE -> ConsumerSiteMembership.joinOnSignup(POOL_ACCOUNT, STORE, Instant.EPOCH);
            case LOCKED -> ConsumerSiteMembership.reconstitute(POOL_ACCOUNT, STORE, status, Instant.EPOCH,
                    null, null, null, Instant.EPOCH, "op-earlier");
            case LEFT -> ConsumerSiteMembership.reconstitute(POOL_ACCOUNT, STORE, status, Instant.EPOCH,
                    Instant.EPOCH, ConsumerSiteLeftBy.SELF, POOL_ACCOUNT);
        };
    }

    private void poolMemberFoundThroughStore(Account account) {
        given(accountRepository.findByIdInSiteIncludingPoolMembers(STORE, POOL_ACCOUNT))
                .willReturn(Optional.of(account));
    }

    private ConsumerSiteMembership writtenMembership() {
        ArgumentCaptor<ConsumerSiteMembership> written = ArgumentCaptor.forClass(ConsumerSiteMembership.class);
        verify(membershipRepository).update(written.capture());
        return written.getValue();
    }

    /** Nothing account-wide happened: no account write, no history row, no outbox event. */
    private void accountUntouched() {
        verify(accountRepository, never()).save(any());
        verifyNoInteractions(historyRepository, eventPublisher);
    }

    // ── AC-2: a pool member — that site's membership only ───────────────────────────────────────────

    @Test
    @DisplayName("AC-2 LOCKED → 스토어 멤버십 ACTIVE→LOCKED(잠금 기록=호출자) · 계정 ACTIVE · 이력·이벤트 0 · scope=SITE_MEMBERSHIP")
    void locked_poolMember_locksTheSiteMembershipOnly() {
        storeExists();
        Account account = poolAccount(AccountStatus.ACTIVE);
        poolMemberFoundThroughStore(account);
        given(membershipRepository.find(STORE, POOL_ACCOUNT))
                .willReturn(Optional.of(storeMembership(ConsumerSiteMembershipStatus.ACTIVE)));
        given(membershipRepository.update(any())).willReturn(true);

        ProvisionedStatusChangeResult r = useCase(ON).execute("ecommerce", POOL_ACCOUNT, AccountStatus.LOCKED,
                "sys-store");

        ConsumerSiteMembership written = writtenMembership();
        assertThat(written.getSiteTenantId()).isEqualTo(STORE);
        assertThat(written.getStatus()).isEqualTo(ConsumerSiteMembershipStatus.LOCKED);
        assertThat(written.getLockedByActorId()).isEqualTo("sys-store");
        assertThat(r.scope()).isEqualTo(GdprDeleteResult.SCOPE_SITE_MEMBERSHIP);
        assertThat(r.tenantId()).isEqualTo("ecommerce");
        assertThat(r.previousStatus()).isEqualTo("ACTIVE");
        assertThat(r.currentStatus()).isEqualTo("LOCKED");
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        accountUntouched();
    }

    @Test
    @DisplayName("AC-2 ACTIVE → 스토어 멤버십 LOCKED→ACTIVE(잠금 기록 지움) · 계정·이력·이벤트 무변경")
    void active_poolMember_unlocksTheSiteMembership() {
        storeExists();
        poolMemberFoundThroughStore(poolAccount(AccountStatus.ACTIVE));
        given(membershipRepository.find(STORE, POOL_ACCOUNT))
                .willReturn(Optional.of(storeMembership(ConsumerSiteMembershipStatus.LOCKED)));
        given(membershipRepository.update(any())).willReturn(true);

        ProvisionedStatusChangeResult r = useCase(ON).execute("ecommerce", POOL_ACCOUNT, AccountStatus.ACTIVE,
                "sys-store");

        ConsumerSiteMembership written = writtenMembership();
        assertThat(written.getStatus()).isEqualTo(ConsumerSiteMembershipStatus.ACTIVE);
        assertThat(written.getLockedAt()).isNull();
        assertThat(r.scope()).isEqualTo(GdprDeleteResult.SCOPE_SITE_MEMBERSHIP);
        assertThat(r.previousStatus()).isEqualTo("LOCKED");
        assertThat(r.currentStatus()).isEqualTo("ACTIVE");
        accountUntouched();
    }

    @Test
    @DisplayName("Edge 2 — 계정 전체가 LOCKED 인 풀 멤버에 사이트 ACTIVE → 멤버십만 보고(멱등) 계정 잠금은 풀지 않는다")
    void active_poolMember_doesNotLiftWholeAccountLock() {
        storeExists();
        Account account = poolAccount(AccountStatus.LOCKED);
        poolMemberFoundThroughStore(account);
        given(membershipRepository.find(STORE, POOL_ACCOUNT))
                .willReturn(Optional.of(storeMembership(ConsumerSiteMembershipStatus.ACTIVE)));

        ProvisionedStatusChangeResult r = useCase(ON).execute("ecommerce", POOL_ACCOUNT, AccountStatus.ACTIVE,
                "sys-store");

        assertThat(account.getStatus()).isEqualTo(AccountStatus.LOCKED);
        assertThat(r.scope()).isEqualTo(GdprDeleteResult.SCOPE_SITE_MEMBERSHIP);
        assertThat(r.previousStatus()).isEqualTo("ACTIVE");
        assertThat(r.currentStatus()).isEqualTo("ACTIVE");
        verify(membershipRepository, never()).update(any());
        accountUntouched();
    }

    @Test
    @DisplayName("AC-2 DELETED → 스토어 멤버십 LEFT(OPERATOR, 행위자=호출자) · 사이트 역할 삭제 · 계정 DELETED 아님 · 이력·이벤트 0")
    void deleted_poolMember_leavesTheSiteOnly() {
        storeExists();
        Account account = poolAccount(AccountStatus.ACTIVE);
        poolMemberFoundThroughStore(account);
        given(accountRepository.findById(TenantId.CONSUMER_POOL, POOL_ACCOUNT)).willReturn(Optional.of(account));
        given(membershipRepository.find(STORE, POOL_ACCOUNT))
                .willReturn(Optional.of(storeMembership(ConsumerSiteMembershipStatus.ACTIVE)));
        given(membershipRepository.update(any())).willReturn(true);
        given(membershipRepository.removeAllSiteRoles(STORE, POOL_ACCOUNT)).willReturn(1);

        ProvisionedStatusChangeResult r = useCase(ON).execute("ecommerce", POOL_ACCOUNT, AccountStatus.DELETED,
                "sys-store");

        ConsumerSiteMembership written = writtenMembership();
        assertThat(written.getStatus()).isEqualTo(ConsumerSiteMembershipStatus.LEFT);
        assertThat(written.getLeftBy()).isEqualTo(ConsumerSiteLeftBy.OPERATOR);
        assertThat(written.getLeftByActorId()).isEqualTo("sys-store");
        verify(membershipRepository).removeAllSiteRoles(STORE, POOL_ACCOUNT);
        assertThat(r.scope()).isEqualTo(GdprDeleteResult.SCOPE_SITE_MEMBERSHIP);
        assertThat(r.previousStatus()).isEqualTo("ACTIVE");
        assertThat(r.currentStatus()).isEqualTo("LEFT");
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        accountUntouched();
    }

    @Test
    @DisplayName("operatorId 없음 → 멤버십 행의 행위자 = 경로 테넌트")
    void noOperatorId_actorIsThePathTenant() {
        storeExists();
        poolMemberFoundThroughStore(poolAccount(AccountStatus.ACTIVE));
        given(membershipRepository.find(STORE, POOL_ACCOUNT))
                .willReturn(Optional.of(storeMembership(ConsumerSiteMembershipStatus.ACTIVE)));
        given(membershipRepository.update(any())).willReturn(true);

        useCase(ON).execute("ecommerce", POOL_ACCOUNT, AccountStatus.LOCKED, null);

        assertThat(writtenMembership().getLockedByActorId()).isEqualTo("ecommerce");
    }

    @Test
    @DisplayName("그 밖의 상태(DORMANT) 풀 멤버 → 409 STATE_TRANSITION_INVALID · 아무것도 안 씀")
    void dormant_poolMember_conflict() {
        storeExists();
        poolMemberFoundThroughStore(poolAccount(AccountStatus.ACTIVE));

        assertThatThrownBy(() -> useCase(ON).execute("ecommerce", POOL_ACCOUNT, AccountStatus.DORMANT, "sys-store"))
                .isInstanceOf(StateTransitionException.class);
        verifyNoInteractions(membershipRepository);
        accountUntouched();
    }

    // ── AC-3: the control — a site's own account (the seller-operator account) changes the ACCOUNT ──────────

    @Test
    @DisplayName("AC-3 대조군 — 셀러 운영 계정(사이트 자기 계정) LOCKED → 계정 LOCKED · 이력 행(ecommerce) · account.locked · scope=ACCOUNT · 멤버십 0")
    void locked_sellerOperatorAccount_locksTheAccount() {
        storeExists();
        Account seller = sellerOperatorAccount();
        given(accountRepository.findByIdInSiteIncludingPoolMembers(STORE, SELLER_ACCOUNT))
                .willReturn(Optional.of(seller));

        ProvisionedStatusChangeResult r = useCase(ON).execute("ecommerce", SELLER_ACCOUNT, AccountStatus.LOCKED,
                "product-service");

        assertThat(r.scope()).isEqualTo(GdprDeleteResult.SCOPE_ACCOUNT);
        assertThat(r.previousStatus()).isEqualTo("ACTIVE");
        assertThat(r.currentStatus()).isEqualTo("LOCKED");
        assertThat(seller.getStatus()).isEqualTo(AccountStatus.LOCKED);
        verify(accountRepository).save(seller);
        ArgumentCaptor<AccountStatusHistoryEntry> row = ArgumentCaptor.forClass(AccountStatusHistoryEntry.class);
        verify(historyRepository).save(row.capture());
        assertThat(row.getValue().getTenantId()).isEqualTo("ecommerce");
        verify(eventPublisher).publishAccountLocked(eq(seller), eq("ecommerce"), anyString(), anyString(),
                eq("product-service"), any());
        verifyNoInteractions(membershipRepository);
    }

    // ── AC-4: a pool account that is not a member of the path site ──────────────────────────────────────

    @Test
    @DisplayName("AC-4 — 스토어 멤버십이 없는 풀 계정에 LOCKED → 404 · 멤버십·계정 무변경")
    void locked_nonMember_notFound() {
        storeExists();
        given(accountRepository.findByIdInSiteIncludingPoolMembers(STORE, POOL_ACCOUNT)).willReturn(Optional.empty());

        assertThatThrownBy(() -> useCase(ON).execute("ecommerce", POOL_ACCOUNT, AccountStatus.LOCKED, "sys-store"))
                .isInstanceOf(AccountNotFoundException.class);
        verifyNoInteractions(membershipRepository);
        accountUntouched();
    }

    @Test
    @DisplayName("AC-4 — 스토어 멤버십 행이 없는 풀 계정에 DELETED → 404 · 탈퇴 쓰기 0")
    void deleted_nonMember_notFound() {
        storeExists();
        given(accountRepository.findByIdInSiteIncludingPoolMembers(STORE, POOL_ACCOUNT)).willReturn(Optional.empty());
        given(membershipRepository.find(STORE, POOL_ACCOUNT)).willReturn(Optional.empty());

        assertThatThrownBy(() -> useCase(ON).execute("ecommerce", POOL_ACCOUNT, AccountStatus.DELETED, "sys-store"))
                .isInstanceOf(AccountNotFoundException.class);
        verify(membershipRepository, never()).update(any());
        accountUntouched();
    }

    // ── Edge 1: the membership is already LEFT ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Edge 1 — 이미 LEFT(본인) 인 풀 멤버에 DELETED → 200 멱등 · OPERATOR 로 다시 기록 · scope=SITE_MEMBERSHIP")
    void deleted_leftMember_idempotent() {
        storeExists();
        given(accountRepository.findByIdInSiteIncludingPoolMembers(STORE, POOL_ACCOUNT)).willReturn(Optional.empty());
        given(accountRepository.findById(TenantId.CONSUMER_POOL, POOL_ACCOUNT))
                .willReturn(Optional.of(poolAccount(AccountStatus.ACTIVE)));
        given(membershipRepository.find(STORE, POOL_ACCOUNT))
                .willReturn(Optional.of(storeMembership(ConsumerSiteMembershipStatus.LEFT)));
        given(membershipRepository.update(any())).willReturn(true);
        given(membershipRepository.removeAllSiteRoles(STORE, POOL_ACCOUNT)).willReturn(0);

        ProvisionedStatusChangeResult r = useCase(ON).execute("ecommerce", POOL_ACCOUNT, AccountStatus.DELETED,
                "sys-store");

        assertThat(r.scope()).isEqualTo(GdprDeleteResult.SCOPE_SITE_MEMBERSHIP);
        assertThat(r.previousStatus()).isEqualTo("LEFT");
        assertThat(r.currentStatus()).isEqualTo("LEFT");
        assertThat(writtenMembership().getLeftBy()).isEqualTo(ConsumerSiteLeftBy.OPERATOR);
        accountUntouched();
    }

    @Test
    @DisplayName("Edge 1 — 이미 LEFT 인 풀 멤버에 LOCKED → 404(되살리지 않는다) · 멤버십 읽기·쓰기 0")
    void locked_leftMember_notFound() {
        storeExists();
        given(accountRepository.findByIdInSiteIncludingPoolMembers(STORE, POOL_ACCOUNT)).willReturn(Optional.empty());

        assertThatThrownBy(() -> useCase(ON).execute("ecommerce", POOL_ACCOUNT, AccountStatus.LOCKED, "sys-store"))
                .isInstanceOf(AccountNotFoundException.class);
        verifyNoInteractions(membershipRepository);
        accountUntouched();
    }

    @Test
    @DisplayName("플래그 OFF — 사이트 경로는 정확 조회 · 없으면 DELETED 도 404 · 멤버십 읽기 0")
    void flagOff_deleted_exactLookupOnly() {
        storeExists();
        given(accountRepository.findById(STORE, POOL_ACCOUNT)).willReturn(Optional.empty());

        assertThatThrownBy(() -> useCase(OFF).execute("ecommerce", POOL_ACCOUNT, AccountStatus.DELETED, "sys-store"))
                .isInstanceOf(AccountNotFoundException.class);
        verifyNoInteractions(membershipRepository);
    }
}
