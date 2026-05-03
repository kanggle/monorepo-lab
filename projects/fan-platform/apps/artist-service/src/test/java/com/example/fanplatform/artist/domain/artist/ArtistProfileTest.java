package com.example.fanplatform.artist.domain.artist;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArtistProfileTest {

    @Test
    @DisplayName("blank stageName -> IllegalArgumentException")
    void blankStageNameRejected() {
        assertThatThrownBy(() -> new ArtistProfile("", null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("over-length stageName rejected")
    void overLengthStageName() {
        String longName = "x".repeat(121);
        assertThatThrownBy(() -> new ArtistProfile(longName, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("nulls allowed for optional fields")
    void allOptionalNullsOk() {
        assertThatCode(() -> new ArtistProfile("ok", null, null, null, null, null))
                .doesNotThrowAnyException();
    }
}
