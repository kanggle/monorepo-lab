package com.example.auth.domain.oauth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuthProviderTest {

    @Test
    @DisplayName("from parses lowercase provider names")
    void fromParsesLowercase() {
        assertThat(OAuthProvider.from("google")).isEqualTo(OAuthProvider.GOOGLE);
        assertThat(OAuthProvider.from("kakao")).isEqualTo(OAuthProvider.KAKAO);
        assertThat(OAuthProvider.from("microsoft")).isEqualTo(OAuthProvider.MICROSOFT);
    }

    @Test
    @DisplayName("from parses uppercase provider names")
    void fromParsesUppercase() {
        assertThat(OAuthProvider.from("MICROSOFT")).isEqualTo(OAuthProvider.MICROSOFT);
    }

    @Test
    @DisplayName("from rejects null, blank, and unsupported providers")
    void fromRejectsInvalid() {
        assertThatThrownBy(() -> OAuthProvider.from(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OAuthProvider.from(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OAuthProvider.from("apple"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("loginMethod prefixes provider name with OAUTH_")
    void loginMethod() {
        assertThat(OAuthProvider.GOOGLE.loginMethod()).isEqualTo("OAUTH_GOOGLE");
        assertThat(OAuthProvider.KAKAO.loginMethod()).isEqualTo("OAUTH_KAKAO");
        assertThat(OAuthProvider.MICROSOFT.loginMethod()).isEqualTo("OAUTH_MICROSOFT");
    }
}
