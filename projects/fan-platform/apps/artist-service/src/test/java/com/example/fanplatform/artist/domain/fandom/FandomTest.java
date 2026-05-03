package com.example.fanplatform.artist.domain.fandom;

import com.example.fanplatform.artist.domain.artist.ArtistId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FandomTest {

    @Test
    @DisplayName("create: valid name + #RRGGBB color")
    void create_valid() {
        Fandom f = Fandom.create(ArtistId.of("a-1"), "fan-platform",
                "Hearts", "#FF00AA", LocalDate.of(2020, 1, 1), "Forever");
        assertThat(f.getFandomName()).isEqualTo("Hearts");
        assertThat(f.getColorHex()).isEqualTo("#FF00AA");
    }

    @Test
    @DisplayName("create: invalid color rejected")
    void create_invalidColorRejected() {
        assertThatThrownBy(() -> Fandom.create(ArtistId.of("a-1"), "fan-platform",
                "Hearts", "FFAA00", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("create: blank name rejected")
    void create_blankName() {
        assertThatThrownBy(() -> Fandom.create(ArtistId.of("a-1"), "fan-platform",
                " ", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("create: null color allowed")
    void create_nullColorOk() {
        assertThatCode(() -> Fandom.create(ArtistId.of("a-1"), "fan-platform",
                "Hearts", null, null, null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("update: applies new fields and bumps updatedAt")
    void update_appliesFields() {
        Fandom f = Fandom.create(ArtistId.of("a-1"), "fan-platform",
                "Hearts", null, null, null);
        f.update("HEARTS", "#000000", LocalDate.of(2021, 1, 1), "yo");

        assertThat(f.getFandomName()).isEqualTo("HEARTS");
        assertThat(f.getColorHex()).isEqualTo("#000000");
        assertThat(f.getSlogan()).isEqualTo("yo");
    }
}
