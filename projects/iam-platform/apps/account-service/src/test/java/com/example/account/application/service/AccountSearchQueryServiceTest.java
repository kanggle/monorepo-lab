package com.example.account.application.service;

import com.example.account.application.port.AccountQueryPort;
import com.example.account.application.result.AccountDetailResult;
import com.example.account.application.result.AccountSearchResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountSearchQueryService 단위 테스트")
class AccountSearchQueryServiceTest {

    @Mock
    private AccountQueryPort accountQueryPort;

    @InjectMocks
    private AccountSearchQueryService service;

    @Test
    @DisplayName("size > 100 — IllegalArgumentException")
    void search_sizeExceedsMax_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> service.search(null, 0, 101))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("100");
    }

    @Test
    @DisplayName("email blank — findAll() 위임")
    void search_blankEmail_delegatesToFindAll() {
        AccountSearchResult expected = new AccountSearchResult(List.of(), 0, 0, 20, 0);
        when(accountQueryPort.findAll(PageRequest.of(0, 20))).thenReturn(expected);

        AccountSearchResult result = service.search("  ", 0, 20);

        assertThat(result).isEqualTo(expected);
        verify(accountQueryPort).findAll(PageRequest.of(0, 20));
    }

    @Test
    @DisplayName("email null — findAll() 위임")
    void search_nullEmail_delegatesToFindAll() {
        AccountSearchResult expected = new AccountSearchResult(List.of(), 0, 0, 10, 0);
        when(accountQueryPort.findAll(PageRequest.of(0, 10))).thenReturn(expected);

        AccountSearchResult result = service.search(null, 0, 10);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("email 있고 계정 존재 — 단건 결과 반환")
    void search_emailFound_returnsSingleItemResult() {
        AccountSearchResult.Item item = new AccountSearchResult.Item(
                "acc-1", "found@example.com", "ACTIVE", Instant.now());
        when(accountQueryPort.findByEmail("found@example.com")).thenReturn(Optional.of(item));

        AccountSearchResult result = service.search("found@example.com", 0, 20);

        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.content()).containsExactly(item);
    }

    @Test
    @DisplayName("email 있고 계정 없음 — 빈 결과 반환")
    void search_emailNotFound_returnsEmptyResult() {
        when(accountQueryPort.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        AccountSearchResult result = service.search("missing@example.com", 0, 20);

        assertThat(result.totalElements()).isZero();
        assertThat(result.content()).isEmpty();
    }

    @Test
    @DisplayName("detail — findDetailById 위임 후 결과 반환")
    void detail_existing_returnsDetailResult() {
        AccountDetailResult detail = new AccountDetailResult(
                "acc-1", "a@example.com", "ACTIVE", Instant.now(),
                new AccountDetailResult.Profile("홍길동", null));
        when(accountQueryPort.findDetailById("acc-1")).thenReturn(Optional.of(detail));

        Optional<AccountDetailResult> result = service.detail("acc-1");

        assertThat(result).contains(detail);
    }

    @Test
    @DisplayName("detail — 계정 없으면 empty 반환")
    void detail_notFound_returnsEmpty() {
        when(accountQueryPort.findDetailById(any())).thenReturn(Optional.empty());

        assertThat(service.detail("no-such")).isEmpty();
    }
}
