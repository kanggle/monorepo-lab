package com.example.product.application.service;

import com.example.product.application.command.RegisterSellerCommand;
import com.example.product.application.port.SellerAccountProvisioner;
import com.example.product.application.port.SellerAccountProvisioner.ProvisioningResult;
import com.example.product.domain.exception.SellerNotFoundException;
import com.example.product.domain.model.Seller;
import com.example.product.domain.model.SellerStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("RegisterSellerService 단위 테스트 (ADR-MONO-042 onboarding + provisioning + deactivation; HTTP-out-of-tx)")
class RegisterSellerServiceTest {

    /**
     * The SHORT-tx persistence collaborator is mocked directly (TASK-BE-402 M1): the service
     * orchestrates short txns through it and performs the IAM HTTP call between them, OUTSIDE
     * any DB tx. The unit test verifies that orchestration.
     */
    @Mock
    private SellerLifecyclePersistence persistence;

    @Mock
    private SellerAccountProvisioner provisioner;

    @InjectMocks
    private RegisterSellerService service;

    private static Seller active(String sellerId, String accountId) {
        Instant now = Instant.now();
        return Seller.reconstitute(sellerId, "Seller", SellerStatus.ACTIVE,
                accountId, "id-1", now, now);
    }

    private static Seller activeNullIdentity(String sellerId, String accountId) {
        Instant now = Instant.now();
        return Seller.reconstitute(sellerId, "Seller", SellerStatus.ACTIVE,
                accountId, null, now, now);
    }

    // ─── register (D2/D3) ──────────────────────────────────────────────

    @Test
    @DisplayName("register - 성공 provisioning 이면 PENDING 으로 저장 후 ACTIVE 로 update (AC-2)")
    void register_successfulProvisioning_marksActive() {
        given(persistence.save(any(Seller.class)))
                .willAnswer(inv -> inv.getArgument(0)); // returns the PENDING aggregate
        given(provisioner.provision(anyString(), eq("seller-a1"), anyString()))
                .willReturn(ProvisioningResult.success("acct-1", "id-1"));

        String id = service.register(new RegisterSellerCommand("seller-a1", "셀러 A1"));

        assertThat(id).isEqualTo("seller-a1");
        ArgumentCaptor<Seller> updated = ArgumentCaptor.forClass(Seller.class);
        verify(persistence).update(updated.capture());
        assertThat(updated.getValue().getStatus()).isEqualTo(SellerStatus.ACTIVE);
        assertThat(updated.getValue().getAccountId()).isEqualTo("acct-1");
        assertThat(updated.getValue().getIdentityId()).isEqualTo("id-1");
    }

    @Test
    @DisplayName("register - provisioning 실패(IAM down)면 PENDING 유지, onboarding 은 성공 (AC-3 fail-soft, F1)")
    void register_failedProvisioning_staysPending() {
        given(persistence.save(any(Seller.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(provisioner.provision(anyString(), eq("seller-a1"), anyString()))
                .willReturn(ProvisioningResult.failed());

        String id = service.register(new RegisterSellerCommand("seller-a1", "셀러 A1"));

        // onboarding did NOT throw — the seller id is returned
        assertThat(id).isEqualTo("seller-a1");
        // and the seller was NOT transitioned to ACTIVE (no update with a stored account)
        verify(persistence, never()).update(any(Seller.class));
    }

    @Test
    @DisplayName("register - 이미 ACTIVE 인 셀러 재-onboard 는 provisioning 을 다시 호출하지 않는다 (AC-4 idempotent)")
    void register_alreadyActive_skipsProvisioning() {
        // save() returns the existing ACTIVE seller (idempotent register on conflict)
        given(persistence.save(any(Seller.class)))
                .willReturn(active("seller-a1", "acct-1"));

        service.register(new RegisterSellerCommand("seller-a1", "셀러 A1"));

        verify(provisioner, never()).provision(anyString(), anyString(), anyString());
        verify(persistence, never()).update(any(Seller.class));
    }

    // ─── provisionPending (D3 retry + m2 identity reconciliation) ───────

    @Test
    @DisplayName("provisionPending - PENDING 셀러를 재-provision 하여 ACTIVE 로 전이")
    void provisionPending_pending_provisionsToActive() {
        given(persistence.getOrThrow("seller-a1"))
                .willReturn(Seller.register("seller-a1", "셀러 A1"));
        given(provisioner.provision(anyString(), eq("seller-a1"), anyString()))
                .willReturn(ProvisioningResult.success("acct-1", "id-1"));

        service.provisionPending("seller-a1");

        verify(persistence).update(any(Seller.class));
    }

    @Test
    @DisplayName("provisionPending - 완전 provisioned(account+identity) ACTIVE 면 no-op (provisioning 호출 안 함)")
    void provisionPending_fullyProvisioned_noop() {
        given(persistence.getOrThrow("seller-a1"))
                .willReturn(active("seller-a1", "acct-1")); // identity 'id-1' present

        service.provisionPending("seller-a1");

        verify(provisioner, never()).provision(anyString(), anyString(), anyString());
        verify(persistence, never()).update(any(Seller.class));
    }

    @Test
    @DisplayName("provisionPending - identity_id 가 null 인 ACTIVE 셀러는 identity 를 top-up 한다 (m2 reconciliation)")
    void provisionPending_activeNullIdentity_reconcilesIdentity() {
        given(persistence.getOrThrow("seller-a1"))
                .willReturn(activeNullIdentity("seller-a1", "acct-1"));
        given(provisioner.provision(anyString(), eq("seller-a1"), anyString()))
                .willReturn(ProvisioningResult.success("acct-1", "id-9"));

        service.provisionPending("seller-a1");

        ArgumentCaptor<Seller> updated = ArgumentCaptor.forClass(Seller.class);
        verify(persistence).update(updated.capture());
        assertThat(updated.getValue().getStatus()).isEqualTo(SellerStatus.ACTIVE);
        assertThat(updated.getValue().getIdentityId()).isEqualTo("id-9");
    }

    @Test
    @DisplayName("provisionPending - 부재 셀러는 SellerNotFoundException")
    void provisionPending_missing_throws() {
        given(persistence.getOrThrow("ghost"))
                .willThrow(new SellerNotFoundException("ghost"));

        assertThatThrownBy(() -> service.provisionPending("ghost"))
                .isInstanceOf(SellerNotFoundException.class);
    }

    // ─── suspend / close (D4 deactivation) ─────────────────────────────

    @Test
    @DisplayName("suspend - ACTIVE 셀러 SUSPENDED 전이 + 백킹 계정 lock 1회 (AC-5)")
    void suspend_locksBackingAccount() {
        given(persistence.getOrThrow("seller-a1"))
                .willReturn(active("seller-a1", "acct-1"));

        service.suspend("seller-a1");

        verify(persistence).update(any(Seller.class));
        verify(provisioner).lockAccount(anyString(), eq("acct-1"));
    }

    @Test
    @DisplayName("suspend - 이미 SUSPENDED 면 no-op, 두 번째 lock 호출 없음 (idempotent)")
    void suspend_idempotent_noSecondLock() {
        Instant now = Instant.now();
        Seller suspended = Seller.reconstitute("seller-a1", "Seller", SellerStatus.SUSPENDED,
                "acct-1", "id-1", now, now);
        given(persistence.getOrThrow("seller-a1")).willReturn(suspended);

        service.suspend("seller-a1");

        verify(persistence, never()).update(any(Seller.class));
        verify(provisioner, never()).lockAccount(anyString(), anyString());
    }

    @Test
    @DisplayName("suspend - account 가 null 인 legacy/PENDING 셀러는 lock 호출 없이 전이 (null-safe net-zero)")
    void suspend_nullAccount_netZero() {
        given(persistence.getOrThrow("legacy-1"))
                .willReturn(active("legacy-1", null));

        service.suspend("legacy-1");

        verify(persistence).update(any(Seller.class));
        // lockAccount is still called but with null — the provisioner no-ops it (net-zero).
        verify(provisioner).lockAccount(anyString(), eq(null));
    }

    @Test
    @DisplayName("close - 셀러 CLOSED 전이 + 백킹 계정 deactivate 1회")
    void close_deactivatesBackingAccount() {
        given(persistence.getOrThrow("seller-a1"))
                .willReturn(active("seller-a1", "acct-1"));

        service.close("seller-a1");

        verify(persistence).update(any(Seller.class));
        verify(provisioner).deactivateAccount(anyString(), eq("acct-1"));
    }

    @Test
    @DisplayName("close - 이미 CLOSED 면 no-op (idempotent terminal)")
    void close_idempotent() {
        Instant now = Instant.now();
        Seller closed = Seller.reconstitute("seller-a1", "Seller", SellerStatus.CLOSED,
                "acct-1", "id-1", now, now);
        given(persistence.getOrThrow("seller-a1")).willReturn(closed);

        service.close("seller-a1");

        verify(persistence, never()).update(any(Seller.class));
        verify(provisioner, never()).deactivateAccount(anyString(), anyString());
    }

    @Test
    @DisplayName("close - PENDING 셀러도 close 가능, account null 이면 deactivate 는 net-zero (m1 intentional)")
    void close_pendingSeller_allowedNetZero() {
        given(persistence.getOrThrow("pending-1"))
                .willReturn(Seller.register("pending-1", "Pending One")); // PENDING, null account

        service.close("pending-1");

        ArgumentCaptor<Seller> updated = ArgumentCaptor.forClass(Seller.class);
        verify(persistence).update(updated.capture());
        assertThat(updated.getValue().getStatus()).isEqualTo(SellerStatus.CLOSED);
        // null account → deactivate is net-zero (no-op inside the provisioner).
        verify(provisioner).deactivateAccount(anyString(), eq(null));
    }

    @Test
    @DisplayName("ensureDefaultSeller - default 셀러는 provisioning 하지 않는다 (D8 anchor)")
    void ensureDefaultSeller_neverProvisions() {
        given(persistence.ensureDefaultSeller()).willReturn(Seller.defaultSeller());

        String id = service.ensureDefaultSeller();

        assertThat(id).isEqualTo(Seller.DEFAULT_SELLER_ID);
        verify(provisioner, never()).provision(anyString(), anyString(), anyString());
    }
}
