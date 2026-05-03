package com.example.fanplatform.artist.domain.group;

import com.example.fanplatform.artist.domain.artist.ArtistId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArtistGroupTest {

    private ArtistGroup sampleGroup() {
        return ArtistGroup.create(ArtistGroupId.of("g-1"), "fan-platform",
                "Group X", LocalDate.of(2020, 1, 1), "Agency", "img/g.jpg");
    }

    @Test
    @DisplayName("create: valid name, status=ACTIVE")
    void create_active() {
        ArtistGroup g = sampleGroup();
        assertThat(g.getStatus()).isEqualTo(ArtistGroupStatus.ACTIVE);
        assertThat(g.getName()).isEqualTo("Group X");
    }

    @Test
    @DisplayName("create: blank name rejected")
    void create_blankNameRejected() {
        assertThatThrownBy(() -> ArtistGroup.create(ArtistGroupId.of("g-1"), "fan-platform",
                "  ", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rename: ARCHIVED rejects rename")
    void rename_archivedRejects() {
        ArtistGroup g = sampleGroup();
        g.archive();
        assertThatThrownBy(() -> g.rename("New"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("archive: idempotent rejects double archive")
    void archive_doubleArchiveRejected() {
        ArtistGroup g = sampleGroup();
        g.archive();
        assertThatThrownBy(g::archive).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("prepareMembership: FORMER_MEMBER role rejected on add")
    void prepareMembership_formerRoleRejected() {
        ArtistGroup g = sampleGroup();
        assertThatThrownBy(() ->
                g.prepareMembership(ArtistId.of("a-1"), GroupRole.FORMER_MEMBER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("prepareMembership: ARCHIVED group rejects new members")
    void prepareMembership_archivedGroupRejects() {
        ArtistGroup g = sampleGroup();
        g.archive();
        assertThatThrownBy(() ->
                g.prepareMembership(ArtistId.of("a-1"), GroupRole.MEMBER))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("prepareMembership: MEMBER role yields active membership")
    void prepareMembership_returnsActive() {
        ArtistGroup g = sampleGroup();
        GroupMembership m = g.prepareMembership(ArtistId.of("a-1"), GroupRole.MEMBER);
        assertThat(m.isActive()).isTrue();
        assertThat(m.role()).isEqualTo(GroupRole.MEMBER);
        assertThat(m.leftAt()).isNull();
    }
}
