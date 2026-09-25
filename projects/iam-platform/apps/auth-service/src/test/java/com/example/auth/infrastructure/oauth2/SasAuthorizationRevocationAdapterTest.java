package com.example.auth.infrastructure.oauth2;

import com.example.auth.domain.credentials.Credential;
import com.example.auth.domain.credentials.CredentialHash;
import com.example.auth.domain.repository.CredentialRepository;
import com.example.auth.domain.repository.RefreshTokenRepository;
import com.example.auth.domain.session.PrincipalDetailKeys;
import com.example.auth.domain.social.SocialIdentity;
import com.example.auth.domain.token.RefreshToken;
import com.example.auth.infrastructure.persistence.SocialIdentityJpaEntity;
import com.example.auth.infrastructure.persistence.SocialIdentityJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import java.security.Principal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * TASK-BE-601 — the SAS half of a session revoke. The facts this pins:
 * a SAS session is found by its principal NAME (the login email), and is revoked only when
 * the principal's stored {@code account_id} is the target account.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SasAuthorizationRevocationAdapter 단위 테스트 — TASK-BE-601")
class SasAuthorizationRevocationAdapterTest {

    private static final String ACCOUNT_ID = "0199aaaa-0000-7000-8000-000000000001";
    private static final String OTHER_ACCOUNT_ID = "0199aaaa-0000-7000-8000-000000000002";
    private static final String EMAIL = "shopper@example.com";
    private static final String SOCIAL_EMAIL = "shopper@gmail.example";

    @Mock private JdbcOperations jdbcOperations;
    @Mock private OAuth2AuthorizationService authorizationService;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private CredentialRepository credentialRepository;
    @Mock private SocialIdentityJpaRepository socialIdentityJpaRepository;

    private SasAuthorizationRevocationAdapter adapter;
    private RegisteredClient client;

    @BeforeEach
    void setUp() {
        adapter = new SasAuthorizationRevocationAdapter(jdbcOperations, authorizationService,
                refreshTokenRepository, credentialRepository, socialIdentityJpaRepository);
        client = RegisteredClient.withId("client-1")
                .clientId("web-store-client")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri("http://localhost:3000/callback")
                .scope("openid")
                .build();
    }

    private void credentialEmail(String email) {
        given(credentialRepository.findByAccountId(ACCOUNT_ID)).willReturn(Optional.of(
                Credential.create(ACCOUNT_ID, "ecommerce", email, CredentialHash.argon2id("h"), Instant.now())));
    }

    private void candidates(String principalName, String... ids) {
        given(jdbcOperations.queryForList(eq(SasAuthorizationRevocationAdapter.SELECT_CANDIDATES_SQL),
                eq(String.class), eq(principalName), any(Timestamp.class)))
                .willReturn(List.of(ids));
    }

    /** The principal the login paths store: name = email, details carry the account id. */
    private OAuth2Authorization authorization(String id, String principalName, String detailAccountId,
                                              boolean refreshInvalidated) {
        Map<String, Object> details = new HashMap<>();
        details.put(PrincipalDetailKeys.TENANT_ID, "ecommerce");
        if (detailAccountId != null) {
            details.put(PrincipalDetailKeys.ACCOUNT_ID, detailAccountId);
        }
        UsernamePasswordAuthenticationToken principal = new UsernamePasswordAuthenticationToken(
                principalName, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        principal.setDetails(details);

        Instant now = Instant.now();
        OAuth2RefreshToken refresh = new OAuth2RefreshToken("rt-" + id, now, now.plusSeconds(3600));
        OAuth2AccessToken access = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER, "at-" + id, now, now.plusSeconds(300));
        return OAuth2Authorization.withRegisteredClient(client)
                .id(id)
                .principalName(principalName)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizedScopes(Set.of("openid"))
                .attribute(Principal.class.getName(), principal)
                .token(access)
                .token(refresh, m -> {
                    if (refreshInvalidated) {
                        m.put(OAuth2Authorization.Token.INVALIDATED_METADATA_NAME, true);
                    }
                })
                .build();
    }

    @Test
    @DisplayName("자기 계정의 SAS 인가 → refresh·access 무효화 저장 + 미러 행 revoke, 1 건")
    void ownedAuthorization_isInvalidated_andMirrorRowRevoked() {
        credentialEmail(EMAIL);
        candidates(EMAIL, "auth-1");
        given(authorizationService.findById("auth-1")).willReturn(authorization("auth-1", EMAIL, ACCOUNT_ID, false));
        RefreshToken mirror = RefreshToken.create("rt-auth-1", EMAIL, "ecommerce", Instant.now(),
                Instant.now().plusSeconds(3600), null, null, null);
        given(refreshTokenRepository.findByJti("rt-auth-1")).willReturn(Optional.of(mirror));

        int revoked = adapter.revokeActiveRefreshTokens(ACCOUNT_ID);

        assertThat(revoked).isEqualTo(1);
        ArgumentCaptor<OAuth2Authorization> saved = ArgumentCaptor.forClass(OAuth2Authorization.class);
        verify(authorizationService).save(saved.capture());
        // What SasRefreshTokenAuthenticationProvider reads: refreshTokenHolder.isActive().
        assertThat(saved.getValue().getRefreshToken().isActive()).isFalse();
        assertThat(saved.getValue().getRefreshToken().isInvalidated()).isTrue();
        assertThat(saved.getValue().getAccessToken().isInvalidated()).isTrue();
        assertThat(mirror.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(mirror);
    }

    @Test
    @DisplayName("같은 이메일 · 다른 계정(다른 테넌트)의 인가 → 손대지 않는다")
    void sameEmailOtherAccount_isLeftAlone() {
        credentialEmail(EMAIL);
        candidates(EMAIL, "mine", "theirs");
        given(authorizationService.findById("mine")).willReturn(authorization("mine", EMAIL, ACCOUNT_ID, false));
        given(authorizationService.findById("theirs"))
                .willReturn(authorization("theirs", EMAIL, OTHER_ACCOUNT_ID, false));
        given(refreshTokenRepository.findByJti("rt-mine")).willReturn(Optional.empty());

        int revoked = adapter.revokeActiveRefreshTokens(ACCOUNT_ID);

        assertThat(revoked).isEqualTo(1);
        ArgumentCaptor<OAuth2Authorization> saved = ArgumentCaptor.forClass(OAuth2Authorization.class);
        verify(authorizationService, times(1)).save(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo("mine");
        verify(refreshTokenRepository, never()).findByJti("rt-theirs");
    }

    @Test
    @DisplayName("principal 에 account_id 가 없으면 귀속 불가 → 손대지 않는다")
    void authorizationWithoutAccountId_isLeftAlone() {
        credentialEmail(EMAIL);
        candidates(EMAIL, "legacy");
        given(authorizationService.findById("legacy")).willReturn(authorization("legacy", EMAIL, null, false));

        assertThat(adapter.revokeActiveRefreshTokens(ACCOUNT_ID)).isZero();
        verify(authorizationService, never()).save(any());
    }

    @Test
    @DisplayName("이미 무효화된 refresh → 다시 저장하지 않고 세지 않는다 (재호출 = 0)")
    void alreadyInvalidated_isNotCountedTwice() {
        credentialEmail(EMAIL);
        candidates(EMAIL, "done");
        given(authorizationService.findById("done")).willReturn(authorization("done", EMAIL, ACCOUNT_ID, true));

        assertThat(adapter.revokeActiveRefreshTokens(ACCOUNT_ID)).isZero();
        verify(authorizationService, never()).save(any());
    }

    @Test
    @DisplayName("소셜 로그인 세션 — 소셜 식별자의 provider 이메일로도 찾는다 (자격 행이 없는 계정)")
    void socialOnlyAccount_isFoundByProviderEmail() {
        given(credentialRepository.findByAccountId(ACCOUNT_ID)).willReturn(Optional.empty());
        given(socialIdentityJpaRepository.findByAccountId(ACCOUNT_ID)).willReturn(List.of(
                SocialIdentityJpaEntity.fromDomain(SocialIdentity.create(
                        ACCOUNT_ID, "fan-platform", "GOOGLE", "g-123", SOCIAL_EMAIL))));
        candidates(SOCIAL_EMAIL, "social-1");
        given(authorizationService.findById("social-1"))
                .willReturn(authorization("social-1", SOCIAL_EMAIL, ACCOUNT_ID, false));
        given(refreshTokenRepository.findByJti("rt-social-1")).willReturn(Optional.empty());

        assertThat(adapter.revokeActiveRefreshTokens(ACCOUNT_ID)).isEqualTo(1);
    }

    @Test
    @DisplayName("자격 행도 소셜 식별자도 없다 → 조회 없이 0")
    void noPrincipalNames_noLookup() {
        given(credentialRepository.findByAccountId(ACCOUNT_ID)).willReturn(Optional.empty());
        given(socialIdentityJpaRepository.findByAccountId(ACCOUNT_ID)).willReturn(List.of());

        assertThat(adapter.revokeActiveRefreshTokens(ACCOUNT_ID)).isZero();
        verifyNoInteractions(jdbcOperations, authorizationService);
    }

    @Test
    @DisplayName("저장 실패는 삼키지 않는다")
    void saveFailure_propagates() {
        credentialEmail(EMAIL);
        candidates(EMAIL, "auth-1");
        given(authorizationService.findById("auth-1")).willReturn(authorization("auth-1", EMAIL, ACCOUNT_ID, false));
        org.mockito.BDDMockito.willThrow(new IllegalStateException("db down"))
                .given(authorizationService).save(any());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> adapter.revokeActiveRefreshTokens(ACCOUNT_ID))
                .isInstanceOf(IllegalStateException.class);
    }
}
