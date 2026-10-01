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
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * TASK-BE-615 AC-8 (owner decision 2026-10-01: operator creation keeps the pre-pool rule) — the
 * narrow ask {@code excludePoolMembers=true} answers with the site's OWN accounts only even while the
 * pool is on; the default ask keeps the § 5 widening (control, both ways).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("AccountSearchQueryService — excludePoolMembers (TASK-BE-615 AC-8)")
class AccountSearchExcludePoolMembersTest {

    @Mock private AccountQueryPort accountQueryPort;
    @Mock private ConsumerAccountPool consumerAccountPool;
    @InjectMocks private AccountSearchQueryService service;

    private static final AccountSearchResult.Item POOL_SHOPPER =
            new AccountSearchResult.Item("acc-pool", "shopper@example.com", "ACTIVE", Instant.EPOCH);

    @Test
    @DisplayName("풀 켜짐 + excludePoolMembers=true: 이메일 검색은 옛 쿼리 — 풀 가입 쇼핑객은 «없음»")
    void emailSearch_poolOn_excluded_usesSiteOnlyQuery() {
        given(accountQueryPort.findByEmail("ecommerce", "shopper@example.com")).willReturn(List.of());

        AccountSearchResult result = service.search("ecommerce", "shopper@example.com", null, 0, 20, true);

        assertThat(result.totalElements()).isZero();
        verify(accountQueryPort, never()).findByEmailIncludingPoolMembers(anyString(), anyString());
        // The flag is not even consulted: the narrow ask does not depend on it.
        verifyNoInteractions(consumerAccountPool);
    }

    @Test
    @DisplayName("풀 켜짐 + excludePoolMembers=true: 목록도 옛 쿼리")
    void list_poolOn_excluded_usesSiteOnlyQuery() {
        given(accountQueryPort.findAll("ecommerce", null, 0, 20))
                .willReturn(new AccountSearchResult(List.of(), 0, 0, 20, 0));

        service.search("ecommerce", null, null, 0, 20, true);

        verify(accountQueryPort, never()).findAllIncludingPoolMembers(anyString(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("대조군: 풀 켜짐 + excludePoolMembers=false(콘솔 계정 운영 목록) → 풀 멤버 포함 쿼리, 쇼핑객이 나온다")
    void emailSearch_poolOn_notExcluded_includesPoolMember() {
        given(consumerAccountPool.lookupsIncludePoolMembers()).willReturn(true);
        given(accountQueryPort.findByEmailIncludingPoolMembers("ecommerce", "shopper@example.com"))
                .willReturn(List.of(POOL_SHOPPER));

        AccountSearchResult result = service.search("ecommerce", "shopper@example.com", null, 0, 20, false);

        assertThat(result.content()).containsExactly(POOL_SHOPPER);
        verify(accountQueryPort, never()).findByEmail(anyString(), anyString());
    }

    @Test
    @DisplayName("대조군: 5-인자 search 는 excludePoolMembers=false 와 같다 (기존 호출자 무변경)")
    void fiveArgSearch_equalsNotExcluded() {
        given(consumerAccountPool.lookupsIncludePoolMembers()).willReturn(true);
        given(accountQueryPort.findByEmailIncludingPoolMembers("ecommerce", "shopper@example.com"))
                .willReturn(List.of(POOL_SHOPPER));

        assertThat(service.search("ecommerce", "shopper@example.com", null, 0, 20).content())
                .containsExactly(POOL_SHOPPER);
    }
}
