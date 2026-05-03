package com.example.fanplatform.artist.domain.artist;

import com.example.fanplatform.artist.domain.artist.Artist.IllegalStateTransitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArtistTest {

    private static ArtistProfile sampleProfile() {
        return new ArtistProfile("STAGE", "real", LocalDate.of(2020, 1, 1),
                "Agency", "bio", "img/x.jpg");
    }

    @Test
    @DisplayName("register: creates artist in DRAFT with createdAt=updatedAt")
    void register_createsDraft() {
        Artist a = Artist.register(ArtistId.of("id-1"), "fan-platform",
                ArtistType.SOLO, sampleProfile());

        assertThat(a.getStatus()).isEqualTo(ArtistStatus.DRAFT);
        assertThat(a.getTenantId()).isEqualTo("fan-platform");
        assertThat(a.getArtistType()).isEqualTo(ArtistType.SOLO);
        assertThat(a.getCreatedAt()).isEqualTo(a.getUpdatedAt());
        assertThat(a.getPublishedAt()).isNull();
        assertThat(a.getArchivedAt()).isNull();
    }

    @Test
    @DisplayName("publish: DRAFT -> PUBLISHED, sets publishedAt")
    void publish_fromDraftSetsPublishedAt() {
        Artist a = Artist.register(ArtistId.of("id-1"), "fan-platform",
                ArtistType.SOLO, sampleProfile());

        a.publish();

        assertThat(a.getStatus()).isEqualTo(ArtistStatus.PUBLISHED);
        assertThat(a.getPublishedAt()).isNotNull();
    }

    @Test
    @DisplayName("publish: PUBLISHED -> PUBLISHED is forbidden")
    void publish_alreadyPublishedThrows() {
        Artist a = Artist.register(ArtistId.of("id-1"), "fan-platform",
                ArtistType.SOLO, sampleProfile());
        a.publish();

        assertThatThrownBy(a::publish)
                .isInstanceOf(IllegalStateTransitionException.class);
    }

    @Test
    @DisplayName("archive: DRAFT -> ARCHIVED, sets archivedAt")
    void archive_fromDraft() {
        Artist a = Artist.register(ArtistId.of("id-1"), "fan-platform",
                ArtistType.SOLO, sampleProfile());

        a.archive();

        assertThat(a.getStatus()).isEqualTo(ArtistStatus.ARCHIVED);
        assertThat(a.getArchivedAt()).isNotNull();
    }

    @Test
    @DisplayName("archive: PUBLISHED -> ARCHIVED")
    void archive_fromPublished() {
        Artist a = Artist.register(ArtistId.of("id-1"), "fan-platform",
                ArtistType.SOLO, sampleProfile());
        a.publish();

        a.archive();

        assertThat(a.getStatus()).isEqualTo(ArtistStatus.ARCHIVED);
    }

    @Test
    @DisplayName("archive: already ARCHIVED -> forbidden")
    void archive_alreadyArchivedThrows() {
        Artist a = Artist.register(ArtistId.of("id-1"), "fan-platform",
                ArtistType.SOLO, sampleProfile());
        a.archive();

        assertThatThrownBy(a::archive)
                .isInstanceOf(IllegalStateTransitionException.class);
    }

    @Test
    @DisplayName("updateProfile: ARCHIVED rejects updates")
    void updateProfile_archivedRejects() {
        Artist a = Artist.register(ArtistId.of("id-1"), "fan-platform",
                ArtistType.SOLO, sampleProfile());
        a.archive();

        assertThatThrownBy(() -> a.updateProfile(sampleProfile()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("updateProfile: PUBLISHED accepts updates and bumps updatedAt")
    void updateProfile_publishedSucceeds() {
        Artist a = Artist.register(ArtistId.of("id-1"), "fan-platform",
                ArtistType.SOLO, sampleProfile());
        a.publish();
        ArtistProfile newer = sampleProfile().withStageName("NEW");

        a.updateProfile(newer);

        assertThat(a.getProfile().stageName()).isEqualTo("NEW");
    }

    @Test
    @DisplayName("isPublished: only true when status=PUBLISHED")
    void isPublished_correct() {
        Artist a = Artist.register(ArtistId.of("id-1"), "fan-platform",
                ArtistType.SOLO, sampleProfile());
        assertThat(a.isPublished()).isFalse();
        a.publish();
        assertThat(a.isPublished()).isTrue();
        a.archive();
        assertThat(a.isPublished()).isFalse();
    }
}
