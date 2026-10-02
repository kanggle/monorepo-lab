package com.example.auth.application;

import com.example.auth.application.MoveCredentialToConsumerPoolUseCase.Outcome;
import com.example.auth.application.exception.OperatorFacetUnavailableException;
import com.example.auth.application.port.OperatorFacetPort;
import com.example.auth.domain.credentials.Credential;
import com.example.auth.domain.credentials.CredentialHash;
import com.example.auth.domain.repository.CredentialRepository;
import com.example.auth.domain.repository.SocialIdentityRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * TASK-BE-618 — the auth_db half of the consumer-pool legacy move: decision order, only
 * {@code credentials.tenant_id} written, every refusal writes nothing, the facet check fail-closed.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("MoveCredentialToConsumerPoolUseCase (TASK-BE-618)")
class MoveCredentialToConsumerPoolUseCaseTest {

    private static final String ACCOUNT = "0199de70-0000-7000-8000-000000618020";
    private static final String IDENTITY = "0199de71-0000-7000-8000-000000618020";
    private static final String EMAIL = "fan-618@example.com";
    private static final String FAN = "fan-platform";

    @Mock private CredentialRepository credentialRepository;
    @Mock private SocialIdentityRepository socialIdentityRepository;
    @Mock private OperatorFacetPort operatorFacetPort;
    @InjectMocks private MoveCredentialToConsumerPoolUseCase useCase;

    private static Credential credential(String accountId, String tenant) {
        return Credential.create(accountId, tenant, EMAIL, CredentialHash.argon2id("$argon2id$x"), Instant.EPOCH);
    }

    private void siteCredential() {
        given(credentialRepository.findByAccountId(ACCOUNT)).willReturn(Optional.of(credential(ACCOUNT, FAN)));
    }

    @Test
    @DisplayName("이동: 소셜 없음 · 풀 쌍둥이 없음 · 운영자 측면 없음 → tenant_id 만 consumer-pool 로")
    void moves() {
        siteCredential();
        given(socialIdentityRepository.existsByAccountId(ACCOUNT)).willReturn(false);
        given(credentialRepository.findPoolCredentialByEmail(EMAIL)).willReturn(Optional.empty());
        given(credentialRepository.findIdentityId(ACCOUNT)).willReturn(Optional.of(IDENTITY));
        given(operatorFacetPort.isOperatorFaceted(ACCOUNT, IDENTITY)).willReturn(false);
        given(credentialRepository.moveToTenant(ACCOUNT, FAN, "consumer-pool")).willReturn(1);

        assertThat(useCase.execute(ACCOUNT, FAN)).isEqualTo(Outcome.MOVED);
    }

    @Test
    @DisplayName("이미 풀 → ALREADY_IN_POOL · 쓰기 없음 · admin 질문 없음 (멱등)")
    void alreadyInPool() {
        given(credentialRepository.findByAccountId(ACCOUNT))
                .willReturn(Optional.of(credential(ACCOUNT, "consumer-pool")));

        assertThat(useCase.execute(ACCOUNT, FAN)).isEqualTo(Outcome.ALREADY_IN_POOL);
        verify(credentialRepository, never()).moveToTenant(anyString(), anyString(), anyString());
        verifyNoInteractions(operatorFacetPort, socialIdentityRepository);
    }

    @Test
    @DisplayName("자격 없음 → NO_CREDENTIAL")
    void noCredential() {
        given(credentialRepository.findByAccountId(ACCOUNT)).willReturn(Optional.empty());

        assertThat(useCase.execute(ACCOUNT, FAN)).isEqualTo(Outcome.NO_CREDENTIAL);
        verifyNoInteractions(operatorFacetPort);
    }

    @Test
    @DisplayName("자격이 다른 테넌트 → CREDENTIAL_TENANT_MISMATCH")
    void tenantMismatch() {
        given(credentialRepository.findByAccountId(ACCOUNT)).willReturn(Optional.of(credential(ACCOUNT, "ecommerce")));

        assertThat(useCase.execute(ACCOUNT, FAN)).isEqualTo(Outcome.CREDENTIAL_TENANT_MISMATCH);
        verifyNoInteractions(operatorFacetPort);
    }

    @Test
    @DisplayName("소셜 신원 있음 → SOCIAL_LINKED · 쓰기 없음")
    void socialLinked() {
        siteCredential();
        given(socialIdentityRepository.existsByAccountId(ACCOUNT)).willReturn(true);

        assertThat(useCase.execute(ACCOUNT, FAN)).isEqualTo(Outcome.SOCIAL_LINKED);
        verify(credentialRepository, never()).moveToTenant(anyString(), anyString(), anyString());
        verifyNoInteractions(operatorFacetPort);
    }

    @Test
    @DisplayName("같은 이메일 풀 자격(다른 계정) → POOL_CREDENTIAL_EXISTS")
    void poolTwin() {
        siteCredential();
        given(socialIdentityRepository.existsByAccountId(ACCOUNT)).willReturn(false);
        given(credentialRepository.findPoolCredentialByEmail(EMAIL))
                .willReturn(Optional.of(credential("someone-else", "consumer-pool")));

        assertThat(useCase.execute(ACCOUNT, FAN)).isEqualTo(Outcome.POOL_CREDENTIAL_EXISTS);
        verifyNoInteractions(operatorFacetPort);
    }

    @Test
    @DisplayName("운영자 측면 → OPERATOR_FACETED · 쓰기 없음")
    void operatorFaceted() {
        siteCredential();
        given(socialIdentityRepository.existsByAccountId(ACCOUNT)).willReturn(false);
        given(credentialRepository.findPoolCredentialByEmail(EMAIL)).willReturn(Optional.empty());
        given(credentialRepository.findIdentityId(ACCOUNT)).willReturn(Optional.empty());
        given(operatorFacetPort.isOperatorFaceted(ACCOUNT, null)).willReturn(true);

        assertThat(useCase.execute(ACCOUNT, FAN)).isEqualTo(Outcome.OPERATOR_FACETED);
        verify(credentialRepository, never()).moveToTenant(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("admin 무응답 → FACET_UNAVAILABLE · 쓰기 없음 (fail-closed)")
    void facetUnavailable() {
        siteCredential();
        given(socialIdentityRepository.existsByAccountId(ACCOUNT)).willReturn(false);
        given(credentialRepository.findPoolCredentialByEmail(EMAIL)).willReturn(Optional.empty());
        given(credentialRepository.findIdentityId(ACCOUNT)).willReturn(Optional.of(IDENTITY));
        given(operatorFacetPort.isOperatorFaceted(ACCOUNT, IDENTITY))
                .willThrow(new OperatorFacetUnavailableException("down", null));

        assertThat(useCase.execute(ACCOUNT, FAN)).isEqualTo(Outcome.FACET_UNAVAILABLE);
        verify(credentialRepository, never()).moveToTenant(anyString(), anyString(), anyString());
    }
}
