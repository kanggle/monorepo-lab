package com.example.product.application.service;

import com.example.product.application.port.SellerAccountProvisioner;
import com.example.product.domain.exception.SellerInvitationAlreadyUsedException;
import com.example.product.domain.exception.SellerInvitationEmailMismatchException;
import com.example.product.domain.exception.SellerInvitationExpiredException;
import com.example.product.domain.exception.SellerInvitationNotFoundException;
import com.example.product.domain.exception.SellerNotActiveException;
import com.example.product.domain.model.Seller;
import com.example.product.domain.model.SellerInvitationStatus;
import com.example.product.domain.model.SellerMember;
import com.example.product.domain.model.SellerMemberStatus;
import com.example.product.domain.model.SellerStatus;
import com.example.product.domain.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * TASK-MONO-752 (ADR-MONO-079 D5) — seller members through the REAL service, persistence collaborator and
 * {@link RegisterSellerService}, over {@link InMemorySellerStores} (no Docker).
 *
 * <p>🔴 AC-1 is one control-group test: the uninvited, the other-email account, the expired and the reused
 * invitation are refused, and in the same test only the right person's accept links and grants.
 */
@DisplayName("SellerMemberService — 초대 · 수락 · 정지 회수 (TASK-MONO-752)")
class SellerMemberServiceTest {

    private static final String TENANT = "ecommerce";
    private static final Instant T0 = Instant.parse("2026-10-03T00:00:00Z");
    private static final String INVITED = "member@example.com";
    private static final String PERSON = "0199de70-0000-7000-8000-00000000a752";
    private static final String OTHER_PERSON = "0199de70-0000-7000-8000-00000000b752";
    private static final String STRANGER = "0199de70-0000-7000-8000-00000000c752";

    private InMemorySellerStores stores;
    private SellerMemberService service;

    private static Seller activeSeller(String id, String machineAccount) {
        return Seller.reconstitute(id, "Seller " + id, SellerStatus.ACTIVE, machineAccount, null, T0, T0);
    }

    private static Clock at(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT);
        stores = new InMemorySellerStores();
        stores.putSeller(TENANT, activeSeller("s-1", "machine-1"));
        stores.putSeller(TENANT, activeSeller("s-2", "machine-2"));
        stores.iamEmails.put(PERSON, INVITED);
        stores.iamEmails.put(OTHER_PERSON, "other@example.com");
        service = stores.memberService(at(T0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("🔴 AC-1 대조군: 초대 없음 · 다른 이메일 · 만료 · 재사용 → 거절 / 올바른 수락만 성공")
    void ac1_controlGroup() {
        // 초대받지 않은 사람 — 아무 토큰이나 들고 온다
        assertThatThrownBy(() -> service.accept("not-a-real-token", STRANGER))
                .isInstanceOf(SellerInvitationNotFoundException.class);

        String token = service.invite("s-1", "  Member@Example.com ", "op-1").token();

        // 다른 이메일 계정 — 링크를 받았어도 셀러가 되지 않는다 (Failure Scenario 1)
        assertThatThrownBy(() -> service.accept(token, OTHER_PERSON))
                .isInstanceOf(SellerInvitationEmailMismatchException.class);
        assertThat(stores.member(TENANT, "s-1", OTHER_PERSON)).isNull();
        assertThat(stores.sellerRoles).doesNotContain(TENANT + "|" + OTHER_PERSON);

        // 거절은 초대를 소모하지 않는다 — 올바른 사람은 여전히 수락할 수 있다
        SellerMember joined = service.accept(token, PERSON);
        assertThat(joined.getStatus()).isEqualTo(SellerMemberStatus.ACTIVE);
        assertThat(joined.getRole().name()).isEqualTo("MEMBER");
        assertThat(stores.sellerRoles).containsExactly(TENANT + "|" + PERSON);

        // 재사용 — 다른 계정이 같은 링크로 → 거절
        stores.iamEmails.put(STRANGER, INVITED); // even an account with the same address cannot reuse it
        assertThatThrownBy(() -> service.accept(token, STRANGER))
                .isInstanceOf(SellerInvitationAlreadyUsedException.class);
        assertThat(stores.member(TENANT, "s-1", STRANGER)).isNull();

        // 만료 — 7일 뒤의 새 초대 수락
        String late = service.invite("s-1", INVITED, "op-1").token();
        SellerMemberService eightDaysLater = stores.memberService(at(T0.plus(Duration.ofDays(8))));
        assertThatThrownBy(() -> eightDaysLater.accept(late, PERSON))
                .isInstanceOf(SellerInvitationExpiredException.class);
    }

    @Test
    @DisplayName("같은 사람의 두 번째 수락(이중 제출) → 200 · 같은 구성원 · IAM 재호출 없음")
    void sameAccountDoubleSubmit_isIdempotent() {
        String token = service.invite("s-1", INVITED, "op-1").token();
        SellerMember first = service.accept(token, PERSON);
        stores.sellerRoles.clear(); // if accept called IAM again, the role would come back

        SellerMember second = service.accept(token, PERSON);

        assertThat(second.getJoinedAt()).isEqualTo(first.getJoinedAt());
        assertThat(stores.sellerRoles).isEmpty();
    }

    @Test
    @DisplayName("초대는 원문 토큰을 저장하지 않는다 — SHA-256 hex 만 · 이메일은 소문자")
    void invitationStoresOnlyHash() {
        SellerMemberService.IssuedInvitation issued = service.invite("s-1", " Member@Example.COM", "op-1");

        assertThat(issued.invitation().getTokenHash())
                .isEqualTo(SellerMemberService.sha256Hex(issued.token()))
                .isNotEqualTo(issued.token())
                .hasSize(64);
        assertThat(issued.invitation().getEmail()).isEqualTo("member@example.com");
        assertThat(issued.invitation().getExpiresAt()).isEqualTo(T0.plus(Duration.ofDays(7)));
        assertThat(issued.invitation().getStatus()).isEqualTo(SellerInvitationStatus.PENDING);
    }

    @Test
    @DisplayName("ACTIVE 가 아닌 셀러 → 초대 · 수락 모두 SELLER_NOT_ACTIVE")
    void inactiveSeller_refused() {
        String token = service.invite("s-1", INVITED, "op-1").token();
        stores.putSeller(TENANT, Seller.reconstitute("s-1", "S", SellerStatus.SUSPENDED, "machine-1", null, T0, T0));

        assertThatThrownBy(() -> service.accept(token, PERSON)).isInstanceOf(SellerNotActiveException.class);
        assertThatThrownBy(() -> service.invite("s-1", INVITED, "op-1")).isInstanceOf(SellerNotActiveException.class);
        assertThat(stores.sellerRoles).isEmpty();
    }

    @Test
    @DisplayName("IAM 이 부여한 뒤 셀러가 정지되면 → 연결 안 됨 · 초대 PENDING · 보상 회수로 역할을 되돌린다")
    void sellerSuspendedWhileIamAnswered_compensatingRevoke() {
        SellerMemberService.IssuedInvitation issued = service.invite("s-1", INVITED, "op-1");
        stores.afterGrant = () -> stores.putSeller(TENANT,
                Seller.reconstitute("s-1", "S", SellerStatus.SUSPENDED, "machine-1", null, T0, T0));

        assertThatThrownBy(() -> service.accept(issued.token(), PERSON)).isInstanceOf(SellerNotActiveException.class);

        assertThat(stores.sellerRoles).isEmpty();
        assertThat(stores.revokeCalls).containsExactly(TENANT + "|" + PERSON);
        assertThat(stores.member(TENANT, "s-1", PERSON)).isNull();
        assertThat(stores.invitations.values()).extracting(i -> i.getStatus())
                .containsExactly(SellerInvitationStatus.PENDING);
    }

    @Test
    @DisplayName("다른 테넌트의 토큰 → 찾지 못한다 (테넌트가 경계)")
    void foreignTenantToken_notFound() {
        String token = service.invite("s-1", INVITED, "op-1").token();
        TenantContext.set("other-store");

        assertThatThrownBy(() -> service.accept(token, PERSON)).isInstanceOf(SellerInvitationNotFoundException.class);
    }

    @Nested
    @DisplayName("AC-3 — 셀러 정지 · 폐점 = SELLER 회수 (계정 잠금 아님)")
    class Revocation {

        private RegisterSellerService registerSellerService;
        private SellerAccountProvisioner provisioner;

        @BeforeEach
        void wire() {
            provisioner = mock(SellerAccountProvisioner.class);
            registerSellerService = new RegisterSellerService(
                    new SellerLifecyclePersistence(stores.sellerRepository), provisioner, service);
            service.accept(service.invite("s-1", INVITED, "op-1").token(), PERSON);
        }

        @Test
        @DisplayName("정지 → IAM revoke(SELLER) · 구성원 REVOKED · 사람 계정은 lock 호출 대상이 아니다")
        void suspend_revokesMemberRole_neverLocksPerson() {
            registerSellerService.suspend("s-1");

            assertThat(stores.revokeCalls).containsExactly(TENANT + "|" + PERSON);
            assertThat(stores.sellerRoles).isEmpty();
            assertThat(stores.member(TENANT, "s-1", PERSON).getStatus()).isEqualTo(SellerMemberStatus.REVOKED);
            // the only lock is the seller's MACHINE account
            org.mockito.Mockito.verify(provisioner).lockAccount(TENANT, "machine-1");
            org.mockito.Mockito.verify(provisioner, org.mockito.Mockito.never())
                    .lockAccount(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(PERSON));
        }

        @Test
        @DisplayName("폐점도 같은 회수")
        void close_revokesMemberRole() {
            registerSellerService.close("s-1");

            assertThat(stores.sellerRoles).isEmpty();
            assertThat(stores.member(TENANT, "s-1", PERSON).getStatus()).isEqualTo(SellerMemberStatus.REVOKED);
        }

        @Test
        @DisplayName("Edge: 다른 ACTIVE 셀러의 ACTIVE 구성원이면 역할을 남긴다 — 그 셀러도 정지되면 그때 회수")
        void memberOfAnotherActiveSeller_keepsRole_untilThatOneStops() {
            service.accept(service.invite("s-2", INVITED, "op-1").token(), PERSON);

            registerSellerService.suspend("s-1");
            assertThat(stores.revokeCalls).isEmpty();
            assertThat(stores.sellerRoles).containsExactly(TENANT + "|" + PERSON);
            assertThat(stores.member(TENANT, "s-1", PERSON).getStatus()).isEqualTo(SellerMemberStatus.REVOKED);

            registerSellerService.suspend("s-2");
            assertThat(stores.revokeCalls).containsExactly(TENANT + "|" + PERSON);
            assertThat(stores.sellerRoles).isEmpty();
        }

        @Test
        @DisplayName("IAM 회수 실패 → 구성원 ACTIVE 로 남고, 이미 정지된 셀러에 SUSPEND 재전송이 회수만 재시도한다")
        void failedRevoke_isRetriedByResuspend() {
            stores.failRevokes = true;
            registerSellerService.suspend("s-1");
            assertThat(stores.member(TENANT, "s-1", PERSON).getStatus()).isEqualTo(SellerMemberStatus.ACTIVE);
            assertThat(stores.sellerRoles).containsExactly(TENANT + "|" + PERSON);

            stores.failRevokes = false;
            registerSellerService.suspend("s-1"); // already SUSPENDED

            assertThat(stores.member(TENANT, "s-1", PERSON).getStatus()).isEqualTo(SellerMemberStatus.REVOKED);
            assertThat(stores.sellerRoles).isEmpty();
            // the machine-account lock was not re-sent by the retry
            org.mockito.Mockito.verify(provisioner, org.mockito.Mockito.times(1)).lockAccount(TENANT, "machine-1");
        }

        @Test
        @DisplayName("회수된 사람을 다시 초대해 수락 → 같은 행이 ACTIVE 로 돌아온다")
        void reinvite_afterRevocation_reactivates() {
            registerSellerService.suspend("s-2"); // unrelated seller, no-op for this person
            stores.putSeller(TENANT, activeSeller("s-3", "machine-3"));
            service.accept(service.invite("s-3", INVITED, "op-1").token(), PERSON);
            registerSellerService.suspend("s-3");
            assertThat(stores.member(TENANT, "s-3", PERSON).getStatus()).isEqualTo(SellerMemberStatus.REVOKED);

            stores.putSeller(TENANT, activeSeller("s-3", "machine-3"));
            service.accept(service.invite("s-3", INVITED, "op-1").token(), PERSON);
            assertThat(stores.member(TENANT, "s-3", PERSON).getStatus()).isEqualTo(SellerMemberStatus.ACTIVE);
        }
    }
}
