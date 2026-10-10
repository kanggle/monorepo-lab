package com.example.account.application.service;

import com.example.account.application.port.AccountQueryPort;
import com.example.account.application.result.AccountSearchResult;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * TASK-BE-614 § 5 — the console's account-operations search follows the pool flag: pool on → a site search
 * includes that site's pool members; pool off → the site's own accounts only.
 *
 * <p>TASK-MONO-772 S7 retired the TASK-BE-615 AC-8 {@code excludePoolMembers} narrowing (its only caller, the
 * TASK-MONO-334 operator-create account check, is gone) — so there is no longer an ask that ignores the flag.
 * This file was {@code AccountSearchExcludePoolMembersTest}; its two control cells stay, its narrowing cells went.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("AccountSearchQueryService — 풀 멤버 포함 규칙 (TASK-BE-614 § 5)")
class AccountSearchPoolMembersTest {

    @Mock private AccountQueryPort accountQueryPort;
    @Mock private ConsumerAccountPool consumerAccountPool;
    @InjectMocks private AccountSearchQueryService service;

    private static final AccountSearchResult.Item POOL_SHOPPER =
            new AccountSearchResult.Item("acc-pool", "shopper@example.com", "ACTIVE", Instant.EPOCH);

    @Test
    @DisplayName("풀 켜짐: 이메일 검색은 풀 멤버 포함 쿼리 — 풀 가입 쇼핑객이 나온다")
    void emailSearch_poolOn_includesPoolMember() {
        given(consumerAccountPool.lookupsIncludePoolMembers()).willReturn(true);
        given(accountQueryPort.findByEmailIncludingPoolMembers("ecommerce", "shopper@example.com"))
                .willReturn(List.of(POOL_SHOPPER));

        AccountSearchResult result = service.search("ecommerce", "shopper@example.com", null, 0, 20);

        assertThat(result.content()).containsExactly(POOL_SHOPPER);
        verify(accountQueryPort, never()).findByEmail(anyString(), anyString());
    }

    @Test
    @DisplayName("대조군: 풀 꺼짐 → 옛 쿼리(사이트 자기 계정만) — 이메일 · 목록 둘 다")
    void poolOff_usesSiteOnlyQueries() {
        given(consumerAccountPool.lookupsIncludePoolMembers()).willReturn(false);
        given(accountQueryPort.findByEmail("ecommerce", "shopper@example.com")).willReturn(List.of());
        given(accountQueryPort.findAll("ecommerce", null, 0, 20))
                .willReturn(new AccountSearchResult(List.of(), 0, 0, 20, 0));

        assertThat(service.search("ecommerce", "shopper@example.com", null, 0, 20).totalElements()).isZero();
        service.search("ecommerce", null, null, 0, 20);

        verify(accountQueryPort, never()).findByEmailIncludingPoolMembers(anyString(), anyString());
        verify(accountQueryPort, never()).findAllIncludingPoolMembers(anyString(), any(), anyInt(), anyInt());
    }
}
