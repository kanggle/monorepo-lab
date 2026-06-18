package com.example.product.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Seller 애그리거트 단위 테스트 (ADR-MONO-030 §3.1 + ADR-MONO-042 lifecycle)")
class SellerTest {

    @Test
    @DisplayName("register 시 PENDING_PROVISIONING 상태로 온보딩된다 (ADR-042 D3)")
    void register_createsPendingSeller() {
        Seller seller = Seller.register("seller-a1", "셀러 A1");

        assertThat(seller.getSellerId()).isEqualTo("seller-a1");
        assertThat(seller.getDisplayName()).isEqualTo("셀러 A1");
        assertThat(seller.getStatus()).isEqualTo(SellerStatus.PENDING_PROVISIONING);
        assertThat(seller.isPendingProvisioning()).isTrue();
        assertThat(seller.isActive()).isFalse();
        assertThat(seller.getAccountId()).isNull();
        assertThat(seller.getIdentityId()).isNull();
        assertThat(seller.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("defaultSeller 는 seller_id='default' 의 ACTIVE 셀러 (D8 anchor, never provisioned)")
    void defaultSeller_isActiveDefaultId() {
        Seller seller = Seller.defaultSeller();

        assertThat(seller.getSellerId()).isEqualTo(Seller.DEFAULT_SELLER_ID);
        assertThat(seller.getSellerId()).isEqualTo("default");
        assertThat(seller.getStatus()).isEqualTo(SellerStatus.ACTIVE);
        assertThat(seller.isActive()).isTrue();
        assertThat(seller.getAccountId()).isNull();
    }

    @Test
    @DisplayName("markProvisioned 시 account/identity 저장 + PENDING → ACTIVE 전이 (ADR-042 D3 success)")
    void markProvisioned_transitionsToActive() {
        Seller seller = Seller.register("seller-a1", "셀러 A1");

        seller.markProvisioned("acct-1", "id-1");

        assertThat(seller.getStatus()).isEqualTo(SellerStatus.ACTIVE);
        assertThat(seller.getAccountId()).isEqualTo("acct-1");
        assertThat(seller.getIdentityId()).isEqualTo("id-1");
        assertThat(seller.hasBackingAccount()).isTrue();
    }

    @Test
    @DisplayName("markProvisioned 는 멱등 + no-overwrite — 저장된 account 는 덮어쓰지 않는다 (AC-4 / F2)")
    void markProvisioned_isIdempotentAndNoOverwrite() {
        Seller seller = Seller.register("seller-a1", "셀러 A1");
        seller.markProvisioned("acct-1", "id-1");

        // re-provision with different ids must NOT overwrite the stored non-null values
        seller.markProvisioned("acct-2", "id-2");

        assertThat(seller.getStatus()).isEqualTo(SellerStatus.ACTIVE);
        assertThat(seller.getAccountId()).isEqualTo("acct-1");
        assertThat(seller.getIdentityId()).isEqualTo("id-1");
    }

    @Test
    @DisplayName("markProvisioned 가 null account 면 PENDING 유지 (identity-만-성공 fail-soft)")
    void markProvisioned_nullAccount_staysPending() {
        Seller seller = Seller.register("seller-a1", "셀러 A1");

        seller.markProvisioned(null, "id-1");

        assertThat(seller.getStatus()).isEqualTo(SellerStatus.PENDING_PROVISIONING);
        assertThat(seller.getIdentityId()).isEqualTo("id-1");
        assertThat(seller.getAccountId()).isNull();
    }

    @Test
    @DisplayName("markProvisioned 는 변경이 있을 때만 true; 완전 provisioned 재호출은 false (no redundant update)")
    void markProvisioned_returnsChangedFlag() {
        Seller seller = Seller.register("seller-a1", "셀러 A1");

        assertThat(seller.markProvisioned("acct-1", "id-1")).isTrue();   // PENDING→ACTIVE + ids
        assertThat(seller.markProvisioned("acct-1", "id-1")).isFalse();  // no-op (already set)
    }

    @Test
    @DisplayName("needsIdentityReconciliation - ACTIVE + identity null 이면 true, markProvisioned 가 top-up (m2)")
    void needsIdentityReconciliation_topUp() {
        Instant now = Instant.now();
        Seller seller = Seller.reconstitute("seller-a1", "셀러", SellerStatus.ACTIVE,
                "acct-1", null, now, now);

        assertThat(seller.needsIdentityReconciliation()).isTrue();
        assertThat(seller.markProvisioned("acct-1", "id-late")).isTrue(); // fills identity only
        assertThat(seller.getIdentityId()).isEqualTo("id-late");
        assertThat(seller.needsIdentityReconciliation()).isFalse();
    }

    @Test
    @DisplayName("needsIdentityReconciliation - identity 가 이미 있으면 false; PENDING 도 false")
    void needsIdentityReconciliation_falseCases() {
        Instant now = Instant.now();
        Seller activeWithId = Seller.reconstitute("s-1", "셀러", SellerStatus.ACTIVE,
                "acct-1", "id-1", now, now);
        assertThat(activeWithId.needsIdentityReconciliation()).isFalse();

        Seller pending = Seller.register("s-2", "셀러");
        assertThat(pending.needsIdentityReconciliation()).isFalse();
    }

    @Test
    @DisplayName("suspend - PENDING_PROVISIONING 셀러도 SUSPENDED 로 전이 가능 (m1 intentional)")
    void suspend_fromPending_allowed() {
        Seller seller = Seller.register("seller-a1", "셀러 A1"); // PENDING

        boolean transitioned = seller.suspend();

        assertThat(transitioned).isTrue();
        assertThat(seller.getStatus()).isEqualTo(SellerStatus.SUSPENDED);
    }

    @Test
    @DisplayName("close - PENDING_PROVISIONING 셀러도 CLOSED 로 전이 가능 (m1 intentional)")
    void close_fromPending_allowed() {
        Seller seller = Seller.register("seller-a1", "셀러 A1"); // PENDING

        assertThat(seller.close()).isTrue();
        assertThat(seller.getStatus()).isEqualTo(SellerStatus.CLOSED);
    }

    @Test
    @DisplayName("suspend 시 ACTIVE → SUSPENDED 전이하고 true 반환 (계정 lock 필요)")
    void suspend_transitionsAndSignalsLock() {
        Seller seller = Seller.register("seller-a1", "셀러 A1");
        seller.markProvisioned("acct-1", "id-1");

        boolean transitioned = seller.suspend();

        assertThat(transitioned).isTrue();
        assertThat(seller.getStatus()).isEqualTo(SellerStatus.SUSPENDED);
    }

    @Test
    @DisplayName("suspend 는 멱등 — 이미 SUSPENDED 면 false 반환 (재-lock 호출 불필요)")
    void suspend_isIdempotent() {
        Seller seller = Seller.register("seller-a1", "셀러 A1");
        seller.markProvisioned("acct-1", "id-1");
        seller.suspend();

        boolean again = seller.suspend();

        assertThat(again).isFalse();
        assertThat(seller.getStatus()).isEqualTo(SellerStatus.SUSPENDED);
    }

    @Test
    @DisplayName("close 시 → CLOSED(terminal) 전이하고 true 반환; 재-close 는 false (멱등)")
    void close_transitionsThenIdempotent() {
        Seller seller = Seller.register("seller-a1", "셀러 A1");
        seller.markProvisioned("acct-1", "id-1");

        assertThat(seller.close()).isTrue();
        assertThat(seller.getStatus()).isEqualTo(SellerStatus.CLOSED);
        assertThat(seller.close()).isFalse();
    }

    @Test
    @DisplayName("CLOSED 셀러는 다시 provision 할 수 없다")
    void markProvisioned_onClosed_throws() {
        Seller seller = Seller.register("seller-a1", "셀러 A1");
        seller.close();

        assertThatThrownBy(() -> seller.markProvisioned("acct-1", "id-1"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("seller_id 가 blank 면 예외")
    void register_blankSellerId_throws() {
        assertThatThrownBy(() -> Seller.register("  ", "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("displayName 이 blank 면 예외")
    void register_blankDisplayName_throws() {
        assertThatThrownBy(() -> Seller.register("s-1", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("reconstitute 로 영속 상태(account/identity 포함)에서 복원된다")
    void reconstitute_restoresState() {
        Instant now = Instant.now();
        Seller seller = Seller.reconstitute("s-1", "셀러", SellerStatus.ACTIVE,
                "acct-1", "id-1", now, now);

        assertThat(seller.getSellerId()).isEqualTo("s-1");
        assertThat(seller.getStatus()).isEqualTo(SellerStatus.ACTIVE);
        assertThat(seller.getAccountId()).isEqualTo("acct-1");
        assertThat(seller.getIdentityId()).isEqualTo("id-1");
    }

    @Test
    @DisplayName("reconstitute 는 legacy 셀러(null account/identity)도 복원한다 (net-zero backfill)")
    void reconstitute_legacyNullLinkage() {
        Instant now = Instant.now();
        Seller seller = Seller.reconstitute("legacy-1", "Legacy", SellerStatus.ACTIVE,
                null, null, now, now);

        assertThat(seller.isActive()).isTrue();
        assertThat(seller.hasBackingAccount()).isFalse();
    }
}
