package com.example.account.application.service;

import com.example.account.application.port.AccountQueryPort;
import com.example.account.application.result.AccountDetailResult;
import com.example.account.application.result.AccountSearchResult;
import com.example.account.domain.status.AccountStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    @DisplayName("TASK-BE-357: tenantId blank — IllegalArgumentException (fail-closed)")
    void search_blankTenantId_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> service.search("  ", null, null, 0, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");
        assertThatThrownBy(() -> service.search(null, "a@example.com", null, 0, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");
    }

    @Test
    @DisplayName("size > 100 — IllegalArgumentException")
    void search_sizeExceedsMax_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> service.search("fan-platform", null, null, 0, 101))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("100");
    }

    @Test
    @DisplayName("email blank — findAll(tenantId, null status) 위임")
    void search_blankEmail_delegatesToFindAll() {
        AccountSearchResult expected = new AccountSearchResult(List.of(), 0, 0, 20, 0);
        when(accountQueryPort.findAll("fan-platform", null, 0, 20)).thenReturn(expected);

        AccountSearchResult result = service.search("fan-platform", "  ", null, 0, 20);

        assertThat(result).isEqualTo(expected);
        verify(accountQueryPort).findAll("fan-platform", null, 0, 20);
    }

    @Test
    @DisplayName("email null — findAll(tenantId, null status) 위임")
    void search_nullEmail_delegatesToFindAll() {
        AccountSearchResult expected = new AccountSearchResult(List.of(), 0, 0, 10, 0);
        when(accountQueryPort.findAll("ecommerce", null, 0, 10)).thenReturn(expected);

        AccountSearchResult result = service.search("ecommerce", null, null, 0, 10);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("TASK-BE-475: status 필터 — 파싱해서 findAll(tenantId, LOCKED) 위임")
    void search_statusFilter_delegatesWithParsedStatus() {
        AccountSearchResult expected = new AccountSearchResult(List.of(), 3, 0, 1, 3);
        when(accountQueryPort.findAll("ecommerce", AccountStatus.LOCKED, 0, 1)).thenReturn(expected);

        AccountSearchResult result = service.search("ecommerce", null, "LOCKED", 0, 1);

        assertThat(result).isEqualTo(expected);
        verify(accountQueryPort).findAll("ecommerce", AccountStatus.LOCKED, 0, 1);
    }

    @Test
    @DisplayName("TASK-BE-475: status 대소문자 무관 — 'locked' → LOCKED")
    void search_statusFilter_caseInsensitive() {
        when(accountQueryPort.findAll("ecommerce", AccountStatus.LOCKED, 0, 20))
                .thenReturn(new AccountSearchResult(List.of(), 0, 0, 20, 0));

        service.search("ecommerce", null, "locked", 0, 20);

        verify(accountQueryPort).findAll("ecommerce", AccountStatus.LOCKED, 0, 20);
    }

    @Test
    @DisplayName("TASK-BE-475: status blank — 필터 없음(null)로 위임")
    void search_statusBlank_noFilter() {
        when(accountQueryPort.findAll("ecommerce", null, 0, 20))
                .thenReturn(new AccountSearchResult(List.of(), 0, 0, 20, 0));

        service.search("ecommerce", null, "  ", 0, 20);

        verify(accountQueryPort).findAll("ecommerce", null, 0, 20);
    }

    @Test
    @DisplayName("TASK-BE-475: 잘못된 status — IllegalArgumentException (fail-closed, 400)")
    void search_invalidStatus_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> service.search("ecommerce", null, "BOGUS", 0, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BOGUS");
    }

    @Test
    @DisplayName("email 있고 계정 존재 — 단건 결과 반환 (status 무시, tenant-scoped)")
    void search_emailFound_returnsSingleItemResult() {
        AccountSearchResult.Item item = new AccountSearchResult.Item(
                "acc-1", "found@example.com", "ACTIVE", Instant.now());
        when(accountQueryPort.findByEmail("ecommerce", "found@example.com")).thenReturn(List.of(item));

        // status is passed but MUST be ignored on the email single-lookup branch (no parse/filter).
        AccountSearchResult result = service.search("ecommerce", "found@example.com", "LOCKED", 0, 20);

        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.totalPages()).isEqualTo(1);
        assertThat(result.content()).containsExactly(item);
    }

    @Test
    @DisplayName("email 있고 계정 없음 — 빈 결과 반환")
    void search_emailNotFound_returnsEmptyResult() {
        when(accountQueryPort.findByEmail("fan-platform", "missing@example.com")).thenReturn(List.of());

        AccountSearchResult result = service.search("fan-platform", "missing@example.com", null, 0, 20);

        assertThat(result.totalElements()).isZero();
        assertThat(result.totalPages()).isZero();
        assertThat(result.content()).isEmpty();
    }

    @Test
    @DisplayName("email 있고 '*' 크로스테넌트 다건 매칭 — 전부 반환")
    void search_emailStar_returnsAllTenantMatches() {
        AccountSearchResult.Item a = new AccountSearchResult.Item(
                "acc-fan", "dup@example.com", "ACTIVE", Instant.now());
        AccountSearchResult.Item b = new AccountSearchResult.Item(
                "acc-ecom", "dup@example.com", "ACTIVE", Instant.now());
        when(accountQueryPort.findByEmail("*", "dup@example.com")).thenReturn(List.of(a, b));

        AccountSearchResult result = service.search("*", "dup@example.com", null, 0, 20);

        assertThat(result.totalElements()).isEqualTo(2);
        assertThat(result.content()).containsExactly(a, b);
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
