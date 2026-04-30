package com.example.account.application.service;

import com.example.account.application.exception.AccountNotFoundException;
import com.example.account.application.result.DataExportResult;
import com.example.account.domain.account.Account;
import com.example.account.domain.profile.Profile;
import com.example.account.domain.repository.AccountRepository;
import com.example.account.domain.repository.ProfileRepository;
import com.example.account.domain.status.AccountStatus;
import com.example.account.domain.tenant.TenantId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("DataExportUseCase 단위 테스트")
class DataExportUseCaseTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private ProfileRepository profileRepository;

    @InjectMocks
    private DataExportUseCase dataExportUseCase;

    private static final String ACCOUNT_ID = "acc-1";
    private static final String EMAIL = "user@example.com";
    private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");

    private Account sampleAccount() {
        return Account.reconstitute(
                ACCOUNT_ID, TenantId.FAN_PLATFORM, EMAIL, null,
                AccountStatus.ACTIVE,
                CREATED_AT, CREATED_AT, null, null, null, 0);
    }

    private Profile sampleProfile() {
        return Profile.reconstitute(
                1L, ACCOUNT_ID, "John Doe", "+82-10-1234-5678",
                LocalDate.of(1990, 1, 15),
                "ko-KR", "Asia/Seoul", null, Instant.now(), null);
    }

    @Test
    @DisplayName("계정과 프로필이 모두 존재 — 프로필 데이터 포함된 DataExportResult 반환")
    void execute_accountAndProfileExist_returnsResultWithProfile() {
        given(accountRepository.findById(TenantId.FAN_PLATFORM, ACCOUNT_ID)).willReturn(Optional.of(sampleAccount()));
        given(profileRepository.findByAccountId(ACCOUNT_ID)).willReturn(Optional.of(sampleProfile()));

        DataExportResult result = dataExportUseCase.execute(ACCOUNT_ID);

        assertThat(result.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(result.email()).isEqualTo(EMAIL);
        assertThat(result.status()).isEqualTo("ACTIVE");
        assertThat(result.createdAt()).isEqualTo(CREATED_AT);
        assertThat(result.exportedAt()).isNotNull();

        assertThat(result.profile()).isNotNull();
        assertThat(result.profile().displayName()).isEqualTo("John Doe");
        assertThat(result.profile().phoneNumber()).isEqualTo("+82-10-1234-5678");
        assertThat(result.profile().birthDate()).isEqualTo(LocalDate.of(1990, 1, 15));
        assertThat(result.profile().locale()).isEqualTo("ko-KR");
        assertThat(result.profile().timezone()).isEqualTo("Asia/Seoul");
    }

    @Test
    @DisplayName("계정은 존재하지만 프로필이 없는 경우 — profile=null 인 DataExportResult 반환")
    void execute_accountWithoutProfile_returnsResultWithNullProfile() {
        given(accountRepository.findById(TenantId.FAN_PLATFORM, ACCOUNT_ID)).willReturn(Optional.of(sampleAccount()));
        given(profileRepository.findByAccountId(ACCOUNT_ID)).willReturn(Optional.empty());

        DataExportResult result = dataExportUseCase.execute(ACCOUNT_ID);

        assertThat(result.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(result.email()).isEqualTo(EMAIL);
        assertThat(result.status()).isEqualTo("ACTIVE");
        assertThat(result.profile()).isNull();
        // exportedAt timestamp must be non-null even without profile
        assertThat(result.exportedAt()).isNotNull();
    }

    @Test
    @DisplayName("계정 미존재 — AccountNotFoundException 발생")
    void execute_accountNotFound_throwsAccountNotFoundException() {
        given(accountRepository.findById(TenantId.FAN_PLATFORM, ACCOUNT_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> dataExportUseCase.execute(ACCOUNT_ID))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining(ACCOUNT_ID);
    }
}
