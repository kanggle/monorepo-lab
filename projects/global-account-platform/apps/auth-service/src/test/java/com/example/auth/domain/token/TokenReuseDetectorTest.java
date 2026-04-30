package com.example.auth.domain.token;

import com.example.auth.domain.repository.RefreshTokenRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenReuseDetectorTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private static final String JTI = "jti-1";
    private static final String ACCOUNT_ID = "acc-1";

    private RefreshToken token(boolean revoked) {
        return new RefreshToken(1L, JTI, ACCOUNT_ID,
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600),
                null, revoked, "fp-1");
    }

    @Test
    @DisplayName("Normal chain: token has not been rotated → not reuse")
    void normalChain() {
        TokenReuseDetector detector = new TokenReuseDetector(refreshTokenRepository);
        when(refreshTokenRepository.existsByRotatedFrom(JTI)).thenReturn(false);

        assertThat(detector.isReuse(token(false))).isFalse();
    }

    @Test
    @DisplayName("Reuse chain: token was already rotated → reuse")
    void reuseChain() {
        TokenReuseDetector detector = new TokenReuseDetector(refreshTokenRepository);
        when(refreshTokenRepository.existsByRotatedFrom(JTI)).thenReturn(true);

        assertThat(detector.isReuse(token(false))).isTrue();
    }

    @Test
    @DisplayName("Already-revoked token with child still counts as reuse")
    void alreadyRevokedChainStillReuse() {
        TokenReuseDetector detector = new TokenReuseDetector(refreshTokenRepository);
        when(refreshTokenRepository.existsByRotatedFrom(JTI)).thenReturn(true);

        assertThat(detector.isReuse(token(true))).isTrue();
    }

    @Test
    @DisplayName("Null token argument is rejected")
    void rejectsNull() {
        TokenReuseDetector detector = new TokenReuseDetector(refreshTokenRepository);

        assertThatThrownBy(() -> detector.isReuse(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Null repository is rejected at construction")
    void rejectsNullRepository() {
        assertThatThrownBy(() -> new TokenReuseDetector(null))
                .isInstanceOf(NullPointerException.class);
    }
}
