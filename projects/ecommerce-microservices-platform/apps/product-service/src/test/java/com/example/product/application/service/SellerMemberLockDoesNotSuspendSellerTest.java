package com.example.product.application.service;

import com.example.product.application.port.SellerAccountProvisioner;
import com.example.product.domain.model.Seller;
import com.example.product.domain.model.SellerMemberStatus;
import com.example.product.domain.model.SellerStatus;
import com.example.product.domain.tenant.TenantContext;
import com.example.product.infrastructure.event.AccountStatusChangedSellerConsumer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * TASK-MONO-752 AC-4 (ADR-MONO-079 D5) — the reverse projection {@code account.status.changed → LOCKED} reacts to
 * the seller's MACHINE account only. Pinned through the real {@link AccountStatusChangedSellerConsumer} →
 * {@link RegisterSellerService} → {@link SellerLifecyclePersistence}, with a seller that has BOTH a machine
 * account and an ACTIVE person member.
 *
 * <p>🔴 A member being locked must not suspend the seller — whether the event carries the pool tenant
 * ({@code consumer-pool}, what IAM actually emits for a pool account) or the store tenant (the worse case: the
 * lookup must still not find the seller through a member). The machine account's lock still suspends it, as
 * before (ADR-042 D4-C), and that suspension revokes the member's role.
 */
@DisplayName("AC-4 — 구성원 잠금 ≠ 셀러 정지 · 기계 계정 잠금 = 정지 (TASK-MONO-752)")
class SellerMemberLockDoesNotSuspendSellerTest {

    private static final String TENANT = "ecommerce";
    private static final String MACHINE = "machine-1";
    private static final String PERSON = "0199de70-0000-7000-8000-00000000a752";
    private static final Instant T0 = Instant.parse("2026-10-03T00:00:00Z");

    private InMemorySellerStores stores;
    private AccountStatusChangedSellerConsumer consumer;

    private static String locked(String accountId, String tenantId) {
        return """
                {"accountId":"%s","tenantId":"%s","previousStatus":"ACTIVE","currentStatus":"LOCKED",
                 "reasonCode":"ADMIN_LOCK","actorType":"operator","actorId":"op-1","occurredAt":"2026-10-03T00:00:00Z"}
                """.formatted(accountId, tenantId);
    }

    @BeforeEach
    void setUp() {
        stores = new InMemorySellerStores();
        stores.putSeller(TENANT, Seller.reconstitute("s-1", "S", SellerStatus.ACTIVE, MACHINE, null, T0, T0));
        stores.iamEmails.put(PERSON, "member@example.com");
        SellerMemberService memberService = stores.memberService(Clock.fixed(T0, ZoneOffset.UTC));
        TenantContext.set(TENANT);
        memberService.accept(memberService.invite("s-1", "member@example.com", "op-1").token(), PERSON);
        TenantContext.clear();

        RegisterSellerService registerSellerService = new RegisterSellerService(
                new SellerLifecyclePersistence(stores.sellerRepository), mock(SellerAccountProvisioner.class),
                memberService);
        consumer = new AccountStatusChangedSellerConsumer(registerSellerService,
                new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("구성원(풀 계정) 잠금, tenantId=consumer-pool → 셀러 ACTIVE · 역할 그대로")
    void memberLocked_poolTenant_sellerStaysActive() {
        consumer.onMessage(locked(PERSON, "consumer-pool"));

        assertThat(stores.seller(TENANT, "s-1").getStatus()).isEqualTo(SellerStatus.ACTIVE);
        assertThat(stores.revokeCalls).isEmpty();
    }

    @Test
    @DisplayName("구성원 잠금이 스토어 테넌트로 와도 → 셀러 ACTIVE (구성원으로 셀러를 찾지 않는다)")
    void memberLocked_storeTenant_sellerStaysActive() {
        consumer.onMessage(locked(PERSON, TENANT));

        assertThat(stores.seller(TENANT, "s-1").getStatus()).isEqualTo(SellerStatus.ACTIVE);
        assertThat(stores.revokeCalls).isEmpty();
    }

    @Test
    @DisplayName("대조군: 기계 계정 잠금 → 셀러 SUSPENDED (지금처럼) + 구성원 SELLER 회수")
    void machineLocked_suspendsSeller_andRevokesMembers() {
        consumer.onMessage(locked(MACHINE, TENANT));

        assertThat(stores.seller(TENANT, "s-1").getStatus()).isEqualTo(SellerStatus.SUSPENDED);
        assertThat(stores.revokeCalls).containsExactly(TENANT + "|" + PERSON);
        TenantContext.set(TENANT);
        assertThat(stores.member(TENANT, "s-1", PERSON).getStatus()).isEqualTo(SellerMemberStatus.REVOKED);
    }
}
