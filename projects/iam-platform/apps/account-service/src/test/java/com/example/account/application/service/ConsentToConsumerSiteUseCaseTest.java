package com.example.account.application.service;

import com.example.account.application.event.AccountEventPublisher;
import com.example.account.application.result.ConsumerSiteMembershipResult;
import com.example.account.domain.account.Account;
import com.example.account.domain.consumerpool.ConsumerSiteLeftBy;
import com.example.account.domain.consumerpool.ConsumerSiteMembership;
import com.example.account.domain.consumerpool.ConsumerSiteMembershipStatus;
import com.example.account.domain.profile.Profile;
import com.example.account.domain.repository.AccountRepository;
import com.example.account.domain.repository.ConsumerSiteMembershipRepository;
import com.example.account.domain.repository.ProfileRepository;
import com.example.account.domain.repository.TenantRepository;
import com.example.account.domain.tenant.Tenant;
import com.example.account.domain.tenant.TenantId;
import com.example.account.domain.tenant.TenantStatus;
import com.example.account.domain.tenant.TenantType;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * TASK-BE-616 — the first-visit consent write (multi-tenancy.md § 소비자 계정 풀 § 4 · § 6): one ACTIVE
 * membership and one {@code account.created(site)} per {@code (account, site)}; every "no" writes nothing.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("ConsentToConsumerSiteUseCase (TASK-BE-616)")
class ConsentToConsumerSiteUseCaseTest {

    private static final String ACCOUNT = "0199de70-0000-7000-8000-000000616000";
    private static final TenantId FAN = new TenantId("fan-platform");

    @Mock private TenantRepository tenantRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private ProfileRepository profileRepository;
    @Mock private ConsumerSiteMembershipRepository membershipRepository;
    @Mock private AccountEventPublisher eventPublisher;
    @Mock private GetConsumerSiteMembershipUseCase read;
    @InjectMocks private ConsentToConsumerSiteUseCase useCase;

    private static Tenant tenant(String id, TenantType type, TenantStatus status) {
        return Tenant.reconstitute(new TenantId(id), id, type, status, Instant.EPOCH, Instant.EPOCH);
    }

    private static ConsumerSiteMembershipResult answer(String site, boolean consumerSite, String status) {
        return new ConsumerSiteMembershipResult(ACCOUNT, site, consumerSite, "B2C_CONSUMER", status, List.of());
    }

    @Test
    @DisplayName("첫 동의 → 멤버십 ACTIVE(동의 시각) · account.created 는 그 사이트로 1회 · 역할은 쓰지 않는다")
    void firstConsent_writesMembership_andPublishesSiteEvent() {
        Account account = mock(Account.class);
        Profile profile = mock(Profile.class);
        given(profile.getLocale()).willReturn("ko-KR");
        given(tenantRepository.findById(FAN)).willReturn(Optional.of(
                tenant("fan-platform", TenantType.B2C_CONSUMER, TenantStatus.ACTIVE)));
        given(accountRepository.findById(TenantId.CONSUMER_POOL, ACCOUNT)).willReturn(Optional.of(account));
        given(membershipRepository.find(FAN, ACCOUNT)).willReturn(Optional.empty());
        given(profileRepository.findByAccountId(ACCOUNT)).willReturn(Optional.of(profile));
        given(read.execute("fan-platform", ACCOUNT)).willReturn(answer("fan-platform", true, "ACTIVE"));
        Instant before = Instant.now();

        ConsumerSiteMembershipResult r = useCase.execute("fan-platform", ACCOUNT);

        ArgumentCaptor<ConsumerSiteMembership> written = ArgumentCaptor.forClass(ConsumerSiteMembership.class);
        verify(membershipRepository).insert(written.capture());
        assertThat(written.getValue().getAccountId()).isEqualTo(ACCOUNT);
        assertThat(written.getValue().getSiteTenantId()).isEqualTo(FAN);
        assertThat(written.getValue().getStatus()).isEqualTo(ConsumerSiteMembershipStatus.ACTIVE);
        assertThat(written.getValue().getConsentedAt()).isAfterOrEqualTo(before);
        verify(eventPublisher).publishAccountCreated(account, "fan-platform", "ko-KR");
        assertThat(r.membershipStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("이미 ACTIVE 멤버(재제출·뒤로가기) → 쓰기 0 · 이벤트 0 · 같은 답 (멱등)")
    void alreadyMember_isNoOp() {
        given(tenantRepository.findById(FAN)).willReturn(Optional.of(
                tenant("fan-platform", TenantType.B2C_CONSUMER, TenantStatus.ACTIVE)));
        given(accountRepository.findById(TenantId.CONSUMER_POOL, ACCOUNT)).willReturn(Optional.of(mock(Account.class)));
        given(membershipRepository.find(FAN, ACCOUNT)).willReturn(Optional.of(ConsumerSiteMembership.reconstitute(
                ACCOUNT, FAN, ConsumerSiteMembershipStatus.ACTIVE, Instant.EPOCH)));
        given(read.execute("fan-platform", ACCOUNT)).willReturn(answer("fan-platform", true, "ACTIVE"));

        assertThat(useCase.execute("fan-platform", ACCOUNT).membershipStatus()).isEqualTo("ACTIVE");

        verify(membershipRepository, never()).insert(any());
        verifyNoInteractions(eventPublisher, profileRepository);
    }

    @Test
    @DisplayName("TASK-BE-619 — 본인이 떠난(LEFT·SELF) 멤버십 → 다시 동의하면 ACTIVE 로 돌아온다 · account.created 는 다시 내지 않는다")
    void selfLeftMembership_isReopenedByConsent_withoutEvent() {
        given(tenantRepository.findById(FAN)).willReturn(Optional.of(
                tenant("fan-platform", TenantType.B2C_CONSUMER, TenantStatus.ACTIVE)));
        given(accountRepository.findById(TenantId.CONSUMER_POOL, ACCOUNT)).willReturn(Optional.of(mock(Account.class)));
        given(membershipRepository.find(FAN, ACCOUNT)).willReturn(Optional.of(ConsumerSiteMembership.reconstitute(
                ACCOUNT, FAN, ConsumerSiteMembershipStatus.LEFT, Instant.EPOCH,
                Instant.parse("2026-10-02T00:00:00Z"), ConsumerSiteLeftBy.SELF, ACCOUNT)));
        given(read.execute("fan-platform", ACCOUNT)).willReturn(answer("fan-platform", true, "ACTIVE"));
        Instant before = Instant.now();

        assertThat(useCase.execute("fan-platform", ACCOUNT).membershipStatus()).isEqualTo("ACTIVE");

        ArgumentCaptor<ConsumerSiteMembership> written = ArgumentCaptor.forClass(ConsumerSiteMembership.class);
        verify(membershipRepository).update(written.capture());
        assertThat(written.getValue().getStatus()).isEqualTo(ConsumerSiteMembershipStatus.ACTIVE);
        assertThat(written.getValue().getConsentedAt()).isAfterOrEqualTo(before);
        assertThat(written.getValue().getLeftBy()).isNull();
        assertThat(written.getValue().getLeftAt()).isNull();
        verify(membershipRepository, never()).insert(any());
        verifyNoInteractions(eventPublisher, profileRepository);
    }

    @Test
    @DisplayName("TASK-BE-619 — 사이트 운영자가 내보낸(LEFT·OPERATOR) 멤버십 → 동의가 다시 열지 않는다 · 쓰기 0 · 이벤트 0")
    void operatorLeftMembership_isNotReopened() {
        given(tenantRepository.findById(FAN)).willReturn(Optional.of(
                tenant("fan-platform", TenantType.B2C_CONSUMER, TenantStatus.ACTIVE)));
        given(accountRepository.findById(TenantId.CONSUMER_POOL, ACCOUNT)).willReturn(Optional.of(mock(Account.class)));
        given(membershipRepository.find(FAN, ACCOUNT)).willReturn(Optional.of(ConsumerSiteMembership.reconstitute(
                ACCOUNT, FAN, ConsumerSiteMembershipStatus.LEFT, Instant.EPOCH,
                Instant.parse("2026-10-02T00:00:00Z"), ConsumerSiteLeftBy.OPERATOR, "op-1")));
        given(read.execute("fan-platform", ACCOUNT)).willReturn(answer("fan-platform", true, "LEFT"));

        assertThat(useCase.execute("fan-platform", ACCOUNT).membershipStatus()).isEqualTo("LEFT");

        verify(membershipRepository, never()).insert(any());
        verify(membershipRepository, never()).update(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("기록된 작성자가 없는 LEFT 멤버십 → 운영자 쪽으로 읽는다(보수) · 동의가 다시 열지 않는다 · 쓰기 0 · 이벤트 0")
    void leftMembership_isNotReopened() {
        given(tenantRepository.findById(FAN)).willReturn(Optional.of(
                tenant("fan-platform", TenantType.B2C_CONSUMER, TenantStatus.ACTIVE)));
        given(accountRepository.findById(TenantId.CONSUMER_POOL, ACCOUNT)).willReturn(Optional.of(mock(Account.class)));
        given(membershipRepository.find(FAN, ACCOUNT)).willReturn(Optional.of(ConsumerSiteMembership.reconstitute(
                ACCOUNT, FAN, ConsumerSiteMembershipStatus.LEFT, Instant.EPOCH)));
        given(read.execute("fan-platform", ACCOUNT)).willReturn(answer("fan-platform", true, "LEFT"));

        assertThat(useCase.execute("fan-platform", ACCOUNT).membershipStatus()).isEqualTo("LEFT");

        verify(membershipRepository, never()).insert(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("소비자 사이트가 아님(B2B erp) → 계정 조회도 쓰기도 없다")
    void nonConsumerSite_writesNothing() {
        given(tenantRepository.findById(new TenantId("erp"))).willReturn(Optional.of(
                tenant("erp", TenantType.B2B_ENTERPRISE, TenantStatus.ACTIVE)));
        given(read.execute("erp", ACCOUNT)).willReturn(answer("erp", false, null));

        assertThat(useCase.execute("erp", ACCOUNT).consumerSite()).isFalse();

        verifyNoInteractions(accountRepository, membershipRepository, eventPublisher);
    }

    @Test
    @DisplayName("풀 테넌트 자신을 사이트로 → consumerSite=false · 쓰기 0 (도메인 불변식 이전에 막힌다)")
    void poolTenantAsSite_writesNothing() {
        given(tenantRepository.findById(TenantId.CONSUMER_POOL)).willReturn(Optional.of(
                tenant("consumer-pool", TenantType.B2C_CONSUMER, TenantStatus.ACTIVE)));
        given(read.execute("consumer-pool", ACCOUNT)).willReturn(answer("consumer-pool", false, null));

        useCase.execute("consumer-pool", ACCOUNT);

        verifyNoInteractions(accountRepository, membershipRepository, eventPublisher);
    }

    @Test
    @DisplayName("정지된 소비자 사이트 → 쓰기 0 · 이벤트 0")
    void suspendedSite_writesNothing() {
        given(tenantRepository.findById(FAN)).willReturn(Optional.of(
                tenant("fan-platform", TenantType.B2C_CONSUMER, TenantStatus.SUSPENDED)));
        given(accountRepository.findById(TenantId.CONSUMER_POOL, ACCOUNT)).willReturn(Optional.of(mock(Account.class)));
        given(read.execute("fan-platform", ACCOUNT)).willReturn(answer("fan-platform", true, null));

        assertThat(useCase.execute("fan-platform", ACCOUNT).membershipStatus()).isNull();

        verify(membershipRepository, never()).insert(any());
        verify(membershipRepository, never()).find(any(), anyString());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("풀 계정이 아님(사이트 계정 id) → 쓰기 0 · 이벤트 0 — 사이트 계정에 멤버십을 붙이지 않는다")
    void notAPoolAccount_writesNothing() {
        given(tenantRepository.findById(FAN)).willReturn(Optional.of(
                tenant("fan-platform", TenantType.B2C_CONSUMER, TenantStatus.ACTIVE)));
        given(accountRepository.findById(TenantId.CONSUMER_POOL, ACCOUNT)).willReturn(Optional.empty());
        given(read.execute("fan-platform", ACCOUNT)).willReturn(answer("fan-platform", true, null));

        useCase.execute("fan-platform", ACCOUNT);

        verifyNoInteractions(membershipRepository, eventPublisher);
    }
}
