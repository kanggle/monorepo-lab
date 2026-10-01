package com.example.account.application.service;

import com.example.account.application.port.AccountQueryPort;
import com.example.account.application.result.AccountSearchResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
 * TASK-BE-614 (multi-tenancy.md § 소비자 계정 풀 § 5) — the search behind the console's account
 * operations and admin-service {@code CreateOperatorUseCase} switches to the pool-widened queries
 * only while the flag is on. The query semantics themselves are pinned against MySQL in
 * {@code ConsumerPoolSignupIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AccountSearchQueryService — 소비자 계정 풀 (TASK-BE-614)")
class AccountSearchQueryServiceConsumerPoolTest {

    @Mock private AccountQueryPort accountQueryPort;
    @Mock private ConsumerAccountPool consumerAccountPool;
    @InjectMocks private AccountSearchQueryService service;

    private static final AccountSearchResult.Item POOL_SHOPPER =
            new AccountSearchResult.Item("acc-pool", "shopper@example.com", "ACTIVE", Instant.EPOCH);

    @Test
    @DisplayName("플래그 켜짐: 이메일 검색은 그 사이트의 풀 멤버를 포함하는 쿼리로")
    void emailSearch_flagOn_usesPoolWidenedQuery() {
        given(consumerAccountPool.lookupsIncludePoolMembers()).willReturn(true);
        given(accountQueryPort.findByEmailIncludingPoolMembers("ecommerce", "shopper@example.com"))
                .willReturn(List.of(POOL_SHOPPER));

        AccountSearchResult result = service.search("ecommerce", " shopper@example.com ", null, 0, 20);

        assertThat(result.content()).containsExactly(POOL_SHOPPER);
        assertThat(result.totalElements()).isEqualTo(1);
        verify(accountQueryPort, never()).findByEmail(anyString(), anyString());
    }

    @Test
    @DisplayName("플래그 켜짐: 목록도 풀 멤버를 포함하는 쿼리로")
    void list_flagOn_usesPoolWidenedQuery() {
        given(consumerAccountPool.lookupsIncludePoolMembers()).willReturn(true);
        given(accountQueryPort.findAllIncludingPoolMembers("ecommerce", null, 0, 20))
                .willReturn(new AccountSearchResult(List.of(POOL_SHOPPER), 1, 0, 20, 1));

        assertThat(service.search("ecommerce", null, null, 0, 20).content()).containsExactly(POOL_SHOPPER);
        verify(accountQueryPort, never()).findAll(anyString(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("AC-8 플래그 꺼짐: 이전 쿼리 그대로 — 풀 쿼리는 부르지 않는다")
    void flagOff_usesThePrePoolQueries() {
        given(consumerAccountPool.lookupsIncludePoolMembers()).willReturn(false);
        given(accountQueryPort.findByEmail("ecommerce", "shopper@example.com")).willReturn(List.of());

        service.search("ecommerce", "shopper@example.com", null, 0, 20);

        verify(accountQueryPort, never()).findByEmailIncludingPoolMembers(anyString(), anyString());
    }
}
