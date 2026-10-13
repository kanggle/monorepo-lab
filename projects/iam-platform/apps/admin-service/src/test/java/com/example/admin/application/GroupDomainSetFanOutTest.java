package com.example.admin.application;

import com.example.admin.application.port.DomainPermissionSetPort;
import com.example.admin.application.port.DomainPermissionSetPort.GroupSetGrantView;
import com.example.admin.application.port.DomainPermissionSetPort.SetView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * TASK-BE-631 slice B (ADR-MONO-083 D4) — {@link GroupDomainSetFanOut}: rows carry {@code GROUP:<id>} +
 * {@code group_origin = id} in the GROUP's tenant, one per member / template with no skip (the port is not even asked
 * whether another source holds the set), and every delete is the group's own source only.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class GroupDomainSetFanOutTest {

    private static final Instant NOW = Instant.parse("2026-10-14T09:00:00Z");

    @Mock DomainPermissionSetPort setPort;

    @InjectMocks GroupDomainSetFanOut fanOut;

    private static SetView set(long id) {
        return new SetView(id, "s" + id, "acme", null, "n", null, List.of(), Instant.EPOCH, Instant.EPOCH);
    }

    @Test
    @DisplayName("a set onto every current member: one GROUP:<id> row each in the group tenant — no existence check, no skip")
    void setToMembers_oneRowEach_noSkip() {
        int n = fanOut.fanOutSetToMembers(9L, "acme", 11L, List.of(7L, 8L), 2L, NOW);

        assertThat(n).isEqualTo(2);
        verify(setPort).insertGrant(7L, "acme", 11L, "GROUP:9", 9L, 2L, NOW);
        verify(setPort).insertGrant(8L, "acme", 11L, "GROUP:9", 9L, 2L, NOW);
        verifyNoMoreInteractions(setPort);
    }

    @Test
    @DisplayName("a new member gets every template of the group as GROUP:<id> rows in the group tenant")
    void setsToMember_everyTemplate() {
        when(setPort.findGroupGrants(9L)).thenReturn(List.of(
                new GroupSetGrantView(9L, set(11L), null, Instant.EPOCH),
                new GroupSetGrantView(9L, set(12L), null, Instant.EPOCH)));

        int n = fanOut.fanOutSetsToMember(9L, "acme", 7L, 2L, NOW);

        assertThat(n).isEqualTo(2);
        verify(setPort).insertGrant(7L, "acme", 11L, "GROUP:9", 9L, 2L, NOW);
        verify(setPort).insertGrant(7L, "acme", 12L, "GROUP:9", 9L, 2L, NOW);
    }

    @Test
    @DisplayName("revokes are the group's own source only: member → its GROUP:<id> rows; group → all GROUP:<id> rows + templates")
    void revokes_ownSourceOnly() {
        when(setPort.deleteGroupSourceRowsOfOperator(9L, 7L)).thenReturn(1);
        when(setPort.deleteGroupSourceRows(9L)).thenReturn(4);

        assertThat(fanOut.revokeMember(9L, 7L)).isEqualTo(1);
        assertThat(fanOut.revokeGroup(9L)).isEqualTo(4);
        verify(setPort).deleteGroupGrants(9L);
    }

    @Test
    @DisplayName("an unpersisted group (null id) has no sets and touches nothing")
    void nullGroupId_noop() {
        assertThat(fanOut.groupHasSets(null)).isFalse();
        assertThat(fanOut.fanOutSetsToMember(null, "acme", 7L, 2L, NOW)).isZero();
        assertThat(fanOut.revokeMember(null, 7L)).isZero();
        assertThat(fanOut.revokeGroup(null)).isZero();
        verifyNoInteractions(setPort);
    }
}
