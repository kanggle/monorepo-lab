package com.example.account.application.service;

import com.example.account.application.event.AccountEventPublisher;
import com.example.account.application.exception.AccountNotFoundException;
import com.example.account.application.port.ConsumerPoolFlag;
import com.example.account.application.port.EmailVerificationNotifier;
import com.example.account.application.result.GdprDeleteResult;
import com.example.account.application.result.LeaveConsumerSiteResult;
import com.example.account.application.result.ProvisionedStatusChangeResult;
import com.example.account.domain.account.Account;
import com.example.account.domain.consumerpool.ConsumerSiteLeftBy;
import com.example.account.domain.history.AccountStatusHistoryEntry;
import com.example.account.domain.profile.Profile;
import com.example.account.domain.repository.AccountRepository;
import com.example.account.domain.repository.AccountStatusHistoryRepository;
import com.example.account.domain.repository.EmailVerificationTokenStore;
import com.example.account.domain.repository.ProfileRepository;
import com.example.account.domain.repository.TenantRepository;
import com.example.account.domain.status.AccountStatus;
import com.example.account.domain.status.AccountStatusMachine;
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

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * TASK-BE-616 (multi-tenancy.md § 소비자 계정 풀 § 5) — every single-account lookup keyed on a request
 * site tenant includes the site's ACTIVE pool members ({@link SiteAccountLookup}), and the non-member
 * control holds: the widened query is the ONLY way in, so a pool account that the repository does not
 * return for that site (not an ACTIVE member) is still a 404.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("TASK-BE-616 — 사이트 테넌트 단건 조회의 풀 멤버 포함 (§ 5)")
class PoolMemberSiteLookupTest {

    private static final String POOL_ACCOUNT = "0199de70-0000-7000-8000-000000616500";
    private static final TenantId ECOMMERCE = new TenantId("ecommerce");
    private static final ConsumerPoolFlag ON = () -> true;
    private static final ConsumerPoolFlag OFF = () -> false;

    @Mock private AccountRepository accountRepository;

    private static Account poolAccount() {
        return Account.reconstitute(POOL_ACCOUNT, TenantId.CONSUMER_POOL, "pool@example.com", null,
                AccountStatus.ACTIVE, Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-10-01T00:00:00Z"),
                null, null, null, 0);
    }

    // ── the rule itself ───────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("플래그 ON · 사이트 입력 → 넓힌 질의(사이트 자기 계정 ∪ 그 사이트 ACTIVE 풀 멤버)만 · 정확 조회 없음")
    void on_site_usesWidenedQuery() {
        given(accountRepository.findByIdInSiteIncludingPoolMembers(ECOMMERCE, POOL_ACCOUNT))
                .willReturn(Optional.of(poolAccount()));

        assertThat(SiteAccountLookup.find(accountRepository, ON, ECOMMERCE, POOL_ACCOUNT)).isPresent();
        verify(accountRepository, never()).findById(any(), anyString());
    }

    @Test
    @DisplayName("대조군: 그 사이트의 멤버가 아닌 풀 계정 → 넓힌 질의가 비어 있으면 404 — 정확 조회로 «다시» 찾지 않는다")
    void on_nonMember_notFound() {
        given(accountRepository.findByIdInSiteIncludingPoolMembers(new TenantId("fan-platform"), POOL_ACCOUNT))
                .willReturn(Optional.empty());

        assertThat(SiteAccountLookup.find(accountRepository, ON, new TenantId("fan-platform"), POOL_ACCOUNT)).isEmpty();
        verify(accountRepository, never()).findById(any(), anyString());
    }

    @Test
    @DisplayName("입력이 consumer-pool 자신 → 정확 조회 (auth-service 의 풀 principal 상태 조회)")
    void on_poolTenantInput_exact() {
        given(accountRepository.findById(TenantId.CONSUMER_POOL, POOL_ACCOUNT)).willReturn(Optional.of(poolAccount()));

        assertThat(SiteAccountLookup.find(accountRepository, ON, TenantId.CONSUMER_POOL, POOL_ACCOUNT)).isPresent();
        verify(accountRepository, never()).findByIdInSiteIncludingPoolMembers(any(), anyString());
    }

    @Test
    @DisplayName("플래그 OFF(kill switch) → 옛 정확 조회 바이트 그대로")
    void off_exact() {
        given(accountRepository.findById(ECOMMERCE, POOL_ACCOUNT)).willReturn(Optional.empty());

        assertThat(SiteAccountLookup.find(accountRepository, OFF, ECOMMERCE, POOL_ACCOUNT)).isEmpty();
        verify(accountRepository, never()).findByIdInSiteIncludingPoolMembers(any(), anyString());
    }

    // ── the widened surfaces (one cell each: the site tenant reaches the pool member) ──────────────

    @Test
    @DisplayName("/api/accounts/me (ProfileUseCase.getMe) — ecommerce 헤더로 풀 멤버의 내 정보")
    void profile_getMe_poolMember() {
        ProfileRepository profiles = mock(ProfileRepository.class);
        Profile profile = mock(Profile.class);
        given(accountRepository.findByIdInSiteIncludingPoolMembers(ECOMMERCE, POOL_ACCOUNT))
                .willReturn(Optional.of(poolAccount()));
        given(profiles.findByAccountId(POOL_ACCOUNT)).willReturn(Optional.of(profile));

        assertThat(new ProfileUseCase(accountRepository, profiles, ON).getMe(POOL_ACCOUNT, ECOMMERCE).accountId())
                .isEqualTo(POOL_ACCOUNT);
    }

    @Test
    @DisplayName("/api/accounts/me/status (AccountStatusUseCase.getStatus) — 풀 멤버 200 · 비멤버 404")
    void status_poolMember_and_nonMember() {
        AccountStatusHistoryRepository history = mock(AccountStatusHistoryRepository.class);
        AccountStatusUseCase useCase = new AccountStatusUseCase(accountRepository, history,
                new AccountStatusMachine(), mock(AccountEventPublisher.class), 30, ON,
                mock(LeaveConsumerSiteUseCase.class), mock(SiteMembershipLockUseCase.class));
        given(accountRepository.findByIdInSiteIncludingPoolMembers(ECOMMERCE, POOL_ACCOUNT))
                .willReturn(Optional.of(poolAccount()));
        given(accountRepository.findByIdInSiteIncludingPoolMembers(new TenantId("fan-platform"), POOL_ACCOUNT))
                .willReturn(Optional.empty());

        assertThat(useCase.getStatus(POOL_ACCOUNT, ECOMMERCE).status()).isEqualTo("ACTIVE");
        assertThatThrownBy(() -> useCase.getStatus(POOL_ACCOUNT, new TenantId("fan-platform")))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    @DisplayName("TASK-BE-619 — GDPR 삭제 (사이트 운영자, ecommerce) — 풀 계정은 지우지 않는다: 그 사이트 멤버십만 LEFT(OPERATOR) · 이벤트 0")
    void gdprDelete_siteOperator_poolMember_leavesTheSiteOnly() {
        AccountEventPublisher events = mock(AccountEventPublisher.class);
        ProfileRepository profiles = mock(ProfileRepository.class);
        LeaveConsumerSiteUseCase leave = mock(LeaveConsumerSiteUseCase.class);
        GdprDeleteUseCase useCase = new GdprDeleteUseCase(accountRepository, profiles,
                mock(AccountStatusHistoryRepository.class), new AccountStatusMachine(), events, ON, leave);
        Account account = poolAccount();
        given(accountRepository.findByIdInSiteIncludingPoolMembers(ECOMMERCE, POOL_ACCOUNT))
                .willReturn(Optional.of(account));
        given(leave.execute("ecommerce", POOL_ACCOUNT, ConsumerSiteLeftBy.OPERATOR, "op-1"))
                .willReturn(new LeaveConsumerSiteResult(POOL_ACCOUNT, "ecommerce", "LEFT", "OPERATOR",
                        Instant.now(), true, "ACTIVE"));

        GdprDeleteResult result = useCase.execute(POOL_ACCOUNT, "op-1", ECOMMERCE);

        assertThat(result.scope()).isEqualTo(GdprDeleteResult.SCOPE_SITE_MEMBERSHIP);
        assertThat(result.siteTenantId()).isEqualTo("ecommerce");
        assertThat(result.status()).isEqualTo("ACTIVE");
        assertThat(result.maskedAt()).isNull();
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getEmail()).isEqualTo("pool@example.com");
        verify(accountRepository, never()).save(any());
        verify(profiles, never()).findByAccountId(anyString());
        org.mockito.Mockito.verifyNoInteractions(events);
    }

    @Test
    @DisplayName("TASK-BE-619 — GDPR 삭제 (플랫폼 관리자 — 테넌트를 말하지 않음) — 풀 계정 하나를 지운다: DELETED · 이벤트 테넌트 = consumer-pool")
    void gdprDelete_platformAdmin_deletesThePoolAccount_eventOnPool() {
        AccountEventPublisher events = mock(AccountEventPublisher.class);
        ProfileRepository profiles = mock(ProfileRepository.class);
        LeaveConsumerSiteUseCase leave = mock(LeaveConsumerSiteUseCase.class);
        GdprDeleteUseCase useCase = new GdprDeleteUseCase(accountRepository, profiles,
                mock(AccountStatusHistoryRepository.class), new AccountStatusMachine(), events, ON, leave);
        Account account = poolAccount();
        given(accountRepository.findByIdResolvingTenant(POOL_ACCOUNT)).willReturn(Optional.of(account));
        given(profiles.findByAccountId(POOL_ACCOUNT)).willReturn(Optional.empty());

        GdprDeleteResult result = useCase.executeResolvingTenant(POOL_ACCOUNT, "op-super");

        assertThat(result.scope()).isEqualTo(GdprDeleteResult.SCOPE_ACCOUNT);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.DELETED);
        verify(accountRepository).save(account);
        verify(events).publishStatusChanged(eq(account), eq("consumer-pool"), anyString(), anyString(),
                anyString(), anyString(), any());
        verify(events).publishAccountDeletedAnonymized(eq(account), eq("consumer-pool"), anyString(),
                anyString(), anyString(), any(), any());
        org.mockito.Mockito.verifyNoInteractions(leave);
    }

    @Test
    @DisplayName("TASK-BE-619 — 사이트의 자기 계정(풀 아님)은 그 사이트 운영자의 GDPR 삭제로 그대로 지워진다 — 대조군")
    void gdprDelete_siteOperator_siteOwnAccount_stillErased() {
        AccountEventPublisher events = mock(AccountEventPublisher.class);
        ProfileRepository profiles = mock(ProfileRepository.class);
        LeaveConsumerSiteUseCase leave = mock(LeaveConsumerSiteUseCase.class);
        GdprDeleteUseCase useCase = new GdprDeleteUseCase(accountRepository, profiles,
                mock(AccountStatusHistoryRepository.class), new AccountStatusMachine(), events, ON, leave);
        Account siteAccount = Account.reconstitute(POOL_ACCOUNT, ECOMMERCE, "shop@example.com", null,
                AccountStatus.ACTIVE, Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-10-01T00:00:00Z"),
                null, null, null, 0);
        given(accountRepository.findByIdInSiteIncludingPoolMembers(ECOMMERCE, POOL_ACCOUNT))
                .willReturn(Optional.of(siteAccount));
        given(profiles.findByAccountId(POOL_ACCOUNT)).willReturn(Optional.empty());

        GdprDeleteResult result = useCase.execute(POOL_ACCOUNT, "op-1", ECOMMERCE);

        assertThat(result.scope()).isEqualTo(GdprDeleteResult.SCOPE_ACCOUNT);
        assertThat(siteAccount.getStatus()).isEqualTo(AccountStatus.DELETED);
        org.mockito.Mockito.verifyNoInteractions(leave);
    }

    // ── TASK-BE-621: lock / unlock — a site operator's is that site's membership, a platform admin's the account ──

    private AccountStatusUseCase lockUseCase(AccountEventPublisher events, AccountStatusHistoryRepository history,
                                             SiteMembershipLockUseCase siteLock) {
        return new AccountStatusUseCase(accountRepository, history, new AccountStatusMachine(), events, 30, ON,
                mock(LeaveConsumerSiteUseCase.class), siteLock);
    }

    private static com.example.account.application.command.ChangeStatusCommand lockCommand(
            AccountStatus target, com.example.account.domain.status.StatusChangeReason reason, String operator) {
        return new com.example.account.application.command.ChangeStatusCommand(
                POOL_ACCOUNT, target, reason, "operator", operator, null);
    }

    @Test
    @DisplayName("TASK-BE-621 — 잠금 (사이트 운영자, ecommerce) — 풀 계정은 잠그지 않는다: 그 사이트 멤버십만 · 계정 저장 · 이력 · 이벤트 0")
    void lock_siteOperator_poolMember_locksTheSiteOnly() {
        AccountEventPublisher events = mock(AccountEventPublisher.class);
        AccountStatusHistoryRepository history = mock(AccountStatusHistoryRepository.class);
        SiteMembershipLockUseCase siteLock = mock(SiteMembershipLockUseCase.class);
        Account account = poolAccount();
        given(accountRepository.findByIdInSiteIncludingPoolMembers(ECOMMERCE, POOL_ACCOUNT))
                .willReturn(Optional.of(account));
        given(siteLock.execute(ECOMMERCE, account, AccountStatus.LOCKED,
                com.example.account.domain.status.StatusChangeReason.ADMIN_LOCK, "op-store"))
                .willReturn(com.example.account.application.result.StatusChangeResult.siteMembership(
                        POOL_ACCOUNT, "ACTIVE", "LOCKED", Instant.now(), "ecommerce"));

        var result = lockUseCase(events, history, siteLock).changeStatusAsTenantOperator(
                lockCommand(AccountStatus.LOCKED, com.example.account.domain.status.StatusChangeReason.ADMIN_LOCK,
                        "op-store"), ECOMMERCE);

        assertThat(result.scope()).isEqualTo(GdprDeleteResult.SCOPE_SITE_MEMBERSHIP);
        assertThat(result.siteTenantId()).isEqualTo("ecommerce");
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        verify(accountRepository, never()).save(any());
        org.mockito.Mockito.verifyNoInteractions(events, history);
    }

    @Test
    @DisplayName("TASK-BE-621 — 잠금 (플랫폼 관리자 — 테넌트를 말하지 않음) — 풀 계정 전체 LOCKED · 이벤트 테넌트 = consumer-pool · 사이트 갈래 0")
    void lock_platformAdmin_locksThePoolAccount() {
        AccountEventPublisher events = mock(AccountEventPublisher.class);
        AccountStatusHistoryRepository history = mock(AccountStatusHistoryRepository.class);
        SiteMembershipLockUseCase siteLock = mock(SiteMembershipLockUseCase.class);
        Account account = poolAccount();
        given(accountRepository.findByIdResolvingTenant(POOL_ACCOUNT)).willReturn(Optional.of(account));

        var result = lockUseCase(events, history, siteLock).changeStatusResolvingTenant(
                lockCommand(AccountStatus.LOCKED, com.example.account.domain.status.StatusChangeReason.ADMIN_LOCK,
                        "op-super"));

        assertThat(result.scope()).isEqualTo(GdprDeleteResult.SCOPE_ACCOUNT);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.LOCKED);
        verify(accountRepository).save(account);
        verify(events).publishStatusChanged(eq(account), eq("consumer-pool"), anyString(), anyString(),
                anyString(), anyString(), any());
        org.mockito.Mockito.verifyNoInteractions(siteLock);
    }

    @Test
    @DisplayName("TASK-BE-621 대조군 — 사이트의 자기 계정(풀 아님)은 그 사이트 운영자의 잠금으로 계정이 잠긴다")
    void lock_siteOperator_siteOwnAccount_locksTheAccount() {
        AccountStatusHistoryRepository history = mock(AccountStatusHistoryRepository.class);
        SiteMembershipLockUseCase siteLock = mock(SiteMembershipLockUseCase.class);
        Account siteAccount = Account.reconstitute(POOL_ACCOUNT, ECOMMERCE, "shop@example.com", null,
                AccountStatus.ACTIVE, Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-10-01T00:00:00Z"),
                null, null, null, 0);
        given(accountRepository.findByIdInSiteIncludingPoolMembers(ECOMMERCE, POOL_ACCOUNT))
                .willReturn(Optional.of(siteAccount));

        var result = lockUseCase(mock(AccountEventPublisher.class), history, siteLock).changeStatusAsTenantOperator(
                lockCommand(AccountStatus.LOCKED, com.example.account.domain.status.StatusChangeReason.ADMIN_LOCK,
                        "op-store"), ECOMMERCE);

        assertThat(result.scope()).isEqualTo(GdprDeleteResult.SCOPE_ACCOUNT);
        assertThat(siteAccount.getStatus()).isEqualTo(AccountStatus.LOCKED);
        verify(accountRepository).save(siteAccount);
        org.mockito.Mockito.verifyNoInteractions(siteLock);
    }

    @Test
    @DisplayName("TASK-BE-621 대조군 — 휴면 스케줄러 경로 changeStatus(cmd) 는 풀 멤버도 계정 전체(사이트 갈래 없음)")
    void dormantPath_poolMember_staysAccountWide() {
        SiteMembershipLockUseCase siteLock = mock(SiteMembershipLockUseCase.class);
        Account account = poolAccount();
        given(accountRepository.findByIdInSiteIncludingPoolMembers(TenantId.FAN_PLATFORM, POOL_ACCOUNT))
                .willReturn(Optional.of(account));

        lockUseCase(mock(AccountEventPublisher.class), mock(AccountStatusHistoryRepository.class), siteLock)
                .changeStatus(new com.example.account.application.command.ChangeStatusCommand(POOL_ACCOUNT,
                        AccountStatus.DORMANT, com.example.account.domain.status.StatusChangeReason.DORMANT_365D,
                        "system", null, null));

        assertThat(account.getStatus()).isEqualTo(AccountStatus.DORMANT);
        org.mockito.Mockito.verifyNoInteractions(siteLock);
    }

    @Test
    @DisplayName("GDPR 내보내기 (DataExportUseCase) — ecommerce 로 풀 멤버를 내보낸다")
    void dataExport_poolMember() {
        ProfileRepository profiles = mock(ProfileRepository.class);
        given(accountRepository.findByIdInSiteIncludingPoolMembers(ECOMMERCE, POOL_ACCOUNT))
                .willReturn(Optional.of(poolAccount()));
        given(profiles.findByAccountId(POOL_ACCOUNT)).willReturn(Optional.empty());

        assertThat(new DataExportUseCase(accountRepository, profiles, ON).execute(POOL_ACCOUNT, ECOMMERCE).accountId())
                .isEqualTo(POOL_ACCOUNT);
    }

    @Test
    @DisplayName("이메일 인증 재발송 — 사이트로 찾되 토큰에는 계정 자신의 테넌트(consumer-pool) — 인증 단계는 정확 조회 그대로")
    void sendVerification_tokenCarriesPoolTenant() {
        EmailVerificationTokenStore tokens = mock(EmailVerificationTokenStore.class);
        given(accountRepository.findByIdInSiteIncludingPoolMembers(ECOMMERCE, POOL_ACCOUNT))
                .willReturn(Optional.of(poolAccount()));
        given(tokens.tryAcquireResendSlot(eq(POOL_ACCOUNT), any(Duration.class))).willReturn(true);

        new SendVerificationEmailUseCase(accountRepository, tokens, mock(EmailVerificationNotifier.class), ON)
                .execute(POOL_ACCOUNT, ECOMMERCE);

        verify(tokens).save(anyString(), eq("consumer-pool"), eq(POOL_ACCOUNT), any(Duration.class));
    }

    @Test
    @DisplayName("PATCH /internal/tenants/ecommerce/accounts/{id}/status — 풀 멤버의 상태 전이(계정 전체) · 이력 행 테넌트 = consumer-pool")
    void provisionStatusChange_poolMember_historyOnPool() {
        TenantRepository tenants = mock(TenantRepository.class);
        AccountStatusHistoryRepository history = mock(AccountStatusHistoryRepository.class);
        given(tenants.findById(ECOMMERCE)).willReturn(Optional.of(Tenant.reconstitute(
                ECOMMERCE, "ecommerce", TenantType.B2C_CONSUMER, TenantStatus.ACTIVE, Instant.EPOCH, Instant.EPOCH)));
        given(accountRepository.findByIdInSiteIncludingPoolMembers(ECOMMERCE, POOL_ACCOUNT))
                .willReturn(Optional.of(poolAccount()));

        ProvisionedStatusChangeResult r = new ProvisionStatusChangeUseCase(tenants, accountRepository, history,
                new AccountStatusMachine(), mock(AccountEventPublisher.class), ON)
                .execute("ecommerce", POOL_ACCOUNT, AccountStatus.LOCKED, "op-1");

        assertThat(r.currentStatus()).isEqualTo("LOCKED");
        ArgumentCaptor<AccountStatusHistoryEntry> row = ArgumentCaptor.forClass(AccountStatusHistoryEntry.class);
        verify(history).save(row.capture());
        assertThat(row.getValue().getTenantId()).isEqualTo("consumer-pool");
    }
}
