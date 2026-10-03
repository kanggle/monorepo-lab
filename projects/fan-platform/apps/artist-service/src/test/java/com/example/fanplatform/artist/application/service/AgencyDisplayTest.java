package com.example.fanplatform.artist.application.service;

import com.example.common.page.PageResult;
import com.example.fanplatform.artist.application.ActorContext;
import com.example.fanplatform.artist.application.port.in.ArtistView;
import com.example.fanplatform.artist.application.port.in.RegisterArtistUseCase.RegisterArtistCommand;
import com.example.fanplatform.artist.application.port.in.SearchArtistDirectoryUseCase.DirectorySearchResult;
import com.example.fanplatform.artist.application.port.in.SearchArtistDirectoryUseCase.SearchArtistDirectoryQuery;
import com.example.fanplatform.artist.application.port.out.AgencyRepository;
import com.example.fanplatform.artist.application.port.out.ArtistDirectoryCache;
import com.example.fanplatform.artist.application.port.out.ArtistEventPublisher;
import com.example.fanplatform.artist.application.port.out.ArtistRepository;
import com.example.fanplatform.artist.domain.agency.Agency;
import com.example.fanplatform.artist.domain.agency.AgencyId;
import com.example.fanplatform.artist.domain.artist.Artist;
import com.example.fanplatform.artist.domain.artist.ArtistId;
import com.example.fanplatform.artist.domain.artist.ArtistProfile;
import com.example.fanplatform.artist.domain.artist.ArtistType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-MONO-748 AC-4 (no regression in the agency display) at unit level: the
 * {@code agency} key keeps its meaning — a display name — but is now read from the
 * agency ENTITY, with the free text as the fallback only for unaffiliated rows.
 */
class AgencyDisplayTest {

    private static final String TENANT = "fan-platform";
    private static final ActorContext ADMIN = new ActorContext("admin-1", TENANT, Set.of("ADMIN"));

    private static Artist artist(String id, String stage, String freeText) {
        Artist a = Artist.register(ArtistId.of(id), TENANT, "acc-" + id, ArtistType.SOLO,
                new ArtistProfile(stage, null, LocalDate.of(2021, 1, 1), freeText, null, null));
        a.publish();
        return a;
    }

    @Test
    @DisplayName("affiliated → the ENTITY name wins over a stale free text")
    void entityNameWins() {
        Artist a = Artist.reconstitute(ArtistId.of("a-1"), TENANT, "acc", ArtistType.SOLO,
                com.example.fanplatform.artist.domain.artist.ArtistStatus.PUBLISHED,
                new ArtistProfile("LUMI", null, null, "Aurora Ent. (old text)", null, null),
                AgencyId.of("ag-1"), null, null, null, null, 0L);

        ArtistView v = ArtistView.from(a, Map.of("ag-1", "Aurora Entertainment"));

        assertThat(v.agency()).isEqualTo("Aurora Entertainment");
        assertThat(v.agencyId()).isEqualTo("ag-1");
    }

    @Test
    @DisplayName("unaffiliated → free text unchanged (the pre-748 behaviour)")
    void unaffiliatedKeepsFreeText() {
        ArtistView v = ArtistView.from(artist("a-2", "NOAH", "Indie"), Map.of());
        assertThat(v.agency()).isEqualTo("Indie");
        assertThat(v.agencyId()).isNull();
    }

    @Test
    @DisplayName("solo with no agency at all → both null (Edge Case 2)")
    void soloNoAgency() {
        ArtistView v = ArtistView.from(artist("a-3", "YUNO", null), Map.of());
        assertThat(v.agency()).isNull();
        assertThat(v.agencyId()).isNull();
    }

    @Test
    @DisplayName("directory page: agency names fetched in ONE batch, and none when no row is affiliated")
    void directoryBatchesNames() {
        ArtistRepository repo = mock(ArtistRepository.class);
        ArtistDirectoryCache cache = mock(ArtistDirectoryCache.class);
        AgencyRepository agencies = mock(AgencyRepository.class);
        ArtistDirectoryService service = new ArtistDirectoryService(repo, cache, agencies);

        Artist a1 = artist("a-1", "LUMI", "Aurora Entertainment");
        a1.changeAgency(AgencyId.of("ag-1"), "Aurora Entertainment");
        Artist a2 = artist("a-2", "SEA", "Aurora Entertainment");
        a2.changeAgency(AgencyId.of("ag-1"), "Aurora Entertainment");
        Artist a3 = artist("a-3", "YUNO", null);
        when(cache.get(eq(TENANT), anyString())).thenReturn(Optional.empty());
        when(repo.findPublishedDirectoryPage(eq(TENANT), any(), any(), eq(0), eq(20)))
                .thenReturn(new PageResult<>(List.of(a1, a2, a3), 0, 20, 3, 1));
        when(agencies.findNamesByIds(eq(TENANT), any()))
                .thenReturn(Map.of("ag-1", "Aurora Entertainment"));

        DirectorySearchResult r = service.search(new SearchArtistDirectoryQuery(ADMIN, null, null, 0, 20));

        assertThat(r.items()).extracting(ArtistView::agency)
                .containsExactly("Aurora Entertainment", "Aurora Entertainment", null);
        verify(agencies, times(1)).findNamesByIds(eq(TENANT), any());

        // control: a page with no affiliated row costs no agency query
        when(repo.findPublishedDirectoryPage(eq(TENANT), eq("YUNO"), any(), eq(0), eq(20)))
                .thenReturn(new PageResult<>(List.of(a3), 0, 20, 1, 1));
        service.search(new SearchArtistDirectoryQuery(ADMIN, "YUNO", null, 0, 20));
        verify(agencies, times(1)).findNamesByIds(eq(TENANT), any());
    }

    @Test
    @DisplayName("register with agencyId → affiliated, display from the entity")
    void registerWithAgency() {
        ArtistRepository repo = mock(ArtistRepository.class);
        AgencyRepository agencies = mock(AgencyRepository.class);
        ArtistManagementService service = new ArtistManagementService(repo,
                mock(ArtistEventPublisher.class), mock(ArtistDirectoryCache.class), agencies);
        Agency ag = Agency.create(AgencyId.of("ag-1"), TENANT, "Aurora Entertainment");
        when(agencies.findById(AgencyId.of("ag-1"), TENANT)).thenReturn(Optional.of(ag));
        when(repo.insert(any(Artist.class))).thenAnswer(inv -> inv.getArgument(0));
        when(agencies.findNamesByIds(eq(TENANT), any(Collection.class)))
                .thenReturn(Map.of("ag-1", "Aurora Entertainment"));

        ArtistView v = service.register(new RegisterArtistCommand(ADMIN, "acc-9", ArtistType.SOLO,
                "NEW", null, null, null, null, null, "ag-1"));

        assertThat(v.agencyId()).isEqualTo("ag-1");
        assertThat(v.agency()).isEqualTo("Aurora Entertainment");
    }

    @Test
    @DisplayName("register with an ARCHIVED agency → refused before insert")
    void registerWithArchivedAgency() {
        ArtistRepository repo = mock(ArtistRepository.class);
        AgencyRepository agencies = mock(AgencyRepository.class);
        ArtistManagementService service = new ArtistManagementService(repo,
                mock(ArtistEventPublisher.class), mock(ArtistDirectoryCache.class), agencies);
        Agency ag = Agency.create(AgencyId.of("ag-1"), TENANT, "Aurora Entertainment");
        ag.archive();
        when(agencies.findById(AgencyId.of("ag-1"), TENANT)).thenReturn(Optional.of(ag));

        assertThatThrownBy(() -> service.register(new RegisterArtistCommand(ADMIN, "acc-9",
                ArtistType.SOLO, "NEW", null, null, null, null, null, "ag-1")))
                .isInstanceOf(com.example.fanplatform.artist.application.exception.AgencyArchivedException.class);
        verify(repo, never()).insert(any());
    }

    // TASK-MONO-759: `unwiredDirectoryIsFailClosed` moved to StoreSellerDirectoryConfigTest
    // (AC-4) when UnwiredStoreSellerDirectory was deleted — the property it pinned («no
    // permissive StoreSellerDirectory») is now asserted against the wiring that replaced it.
}
