package com.example.fanplatform.artist.application.service;

import com.example.fanplatform.artist.application.ActorContext;
import com.example.fanplatform.artist.application.exception.AdminRoleRequiredException;
import com.example.fanplatform.artist.application.exception.AgencyArchivedException;
import com.example.fanplatform.artist.application.exception.AgencyNameConflictException;
import com.example.fanplatform.artist.application.exception.AgencyNotFoundException;
import com.example.fanplatform.artist.application.exception.StoreSellerClosedException;
import com.example.fanplatform.artist.application.exception.StoreSellerLookupUnavailableException;
import com.example.fanplatform.artist.application.exception.StoreSellerNotFoundException;
import com.example.fanplatform.artist.application.port.in.AgencyView;
import com.example.fanplatform.artist.application.port.in.ArtistGroupView;
import com.example.fanplatform.artist.application.port.in.ArtistView;
import com.example.fanplatform.artist.application.port.out.AgencyRepository;
import com.example.fanplatform.artist.application.port.out.ArtistDirectoryCache;
import com.example.fanplatform.artist.application.port.out.ArtistEventPublisher;
import com.example.fanplatform.artist.application.port.out.ArtistGroupRepository;
import com.example.fanplatform.artist.application.port.out.ArtistRepository;
import com.example.fanplatform.artist.application.port.out.StoreSellerDirectory;
import com.example.fanplatform.artist.domain.agency.Agency;
import com.example.fanplatform.artist.domain.agency.AgencyId;
import com.example.fanplatform.artist.domain.artist.Artist;
import com.example.fanplatform.artist.domain.artist.ArtistId;
import com.example.fanplatform.artist.domain.artist.ArtistProfile;
import com.example.fanplatform.artist.domain.artist.ArtistType;
import com.example.fanplatform.artist.domain.group.ArtistGroup;
import com.example.fanplatform.artist.domain.group.ArtistGroupId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * TASK-MONO-748 — AC-2 (CRUD · duplicate name 409 · affiliation change) and AC-3
 * (store seller link: ACTIVE → saved · missing / CLOSED → refused · lookup failure →
 * NOT saved) at the application-service level, against the {@link StoreSellerDirectory}
 * port.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class AgencyServiceTest {

    private static final String TENANT = "fan-platform";
    private static final ActorContext ADMIN = new ActorContext("admin-1", TENANT, Set.of("ADMIN"));
    private static final ActorContext FAN = new ActorContext("fan-1", TENANT, Set.of("FAN"));

    @Mock AgencyRepository agencyRepository;
    @Mock ArtistRepository artistRepository;
    @Mock ArtistGroupRepository groupRepository;
    @Mock StoreSellerDirectory storeSellerDirectory;
    @Mock ArtistEventPublisher eventPublisher;
    @Mock ArtistDirectoryCache directoryCache;
    @InjectMocks AgencyService service;

    private static Agency activeAgency(String id, String name) {
        return Agency.create(AgencyId.of(id), TENANT, name);
    }

    private static Artist publishedArtist() {
        Artist a = Artist.register(ArtistId.of("a-1"), TENANT, "acc-1", ArtistType.SOLO,
                new ArtistProfile("LUMI", null, LocalDate.of(2021, 3, 14),
                        "Aurora Entertainment", null, null));
        a.publish();
        return a;
    }

    // ----- AC-2: CRUD ----------------------------------------------------------

    @Nested
    @DisplayName("AC-2 — CRUD")
    class Crud {

        @Test
        @DisplayName("create: non-admin → 403, nothing persisted")
        void create_nonAdmin() {
            assertThatThrownBy(() -> service.create(FAN, "Nova Sound"))
                    .isInstanceOf(AdminRoleRequiredException.class);
            verifyNoInteractions(agencyRepository);
        }

        @Test
        @DisplayName("create: name is normalised before the duplicate check and the insert")
        void create_normalises() {
            when(agencyRepository.existsByTenantIdAndName(TENANT, "Nova Sound")).thenReturn(false);
            when(agencyRepository.insert(any(Agency.class))).thenAnswer(inv -> inv.getArgument(0));

            AgencyView view = service.create(ADMIN, "  Nova   Sound ");

            assertThat(view.name()).isEqualTo("Nova Sound");
            assertThat(view.status().name()).isEqualTo("ACTIVE");
            assertThat(view.storeSellerId()).isNull();
        }

        @Test
        @DisplayName("create: duplicate (after normalisation) → 409 AGENCY_NAME_CONFLICT, no insert")
        void create_duplicate() {
            when(agencyRepository.existsByTenantIdAndName(TENANT, "Nova Sound")).thenReturn(true);

            assertThatThrownBy(() -> service.create(ADMIN, "Nova  Sound"))
                    .isInstanceOf(AgencyNameConflictException.class);
            verify(agencyRepository, never()).insert(any());
        }

        @Test
        @DisplayName("rename: to another agency's name → 409; to own name → no-op")
        void rename_conflictAndNoop() {
            Agency ag = activeAgency("ag-1", "Nova Sound");
            when(agencyRepository.findById(AgencyId.of("ag-1"), TENANT)).thenReturn(Optional.of(ag));
            when(agencyRepository.existsByTenantIdAndName(TENANT, "Aurora Entertainment")).thenReturn(true);

            assertThatThrownBy(() -> service.rename(ADMIN, "ag-1", "Aurora Entertainment"))
                    .isInstanceOf(AgencyNameConflictException.class);
            assertThat(service.rename(ADMIN, "ag-1", " Nova Sound ").name()).isEqualTo("Nova Sound");
            verify(agencyRepository, never()).update(any());
        }

        @Test
        @DisplayName("rename: saves and invalidates the directory cache (it caches the display name)")
        void rename_invalidatesCache() {
            Agency ag = activeAgency("ag-1", "Nova Sound");
            when(agencyRepository.findById(AgencyId.of("ag-1"), TENANT)).thenReturn(Optional.of(ag));
            when(agencyRepository.existsByTenantIdAndName(TENANT, "Nova Records")).thenReturn(false);
            when(agencyRepository.update(any(Agency.class))).thenAnswer(inv -> inv.getArgument(0));

            assertThat(service.rename(ADMIN, "ag-1", "Nova Records").name()).isEqualTo("Nova Records");
            verify(directoryCache).invalidateAll(TENANT);
        }

        @Test
        @DisplayName("archive: ACTIVE → ARCHIVED; archiving again → 422 AGENCY_ARCHIVED")
        void archive() {
            Agency ag = activeAgency("ag-1", "Nova Sound");
            when(agencyRepository.findById(AgencyId.of("ag-1"), TENANT)).thenReturn(Optional.of(ag));
            when(agencyRepository.update(any(Agency.class))).thenAnswer(inv -> inv.getArgument(0));

            assertThat(service.archive(ADMIN, "ag-1").status().name()).isEqualTo("ARCHIVED");
            assertThatThrownBy(() -> service.archive(ADMIN, "ag-1"))
                    .isInstanceOf(AgencyArchivedException.class);
        }

        @Test
        @DisplayName("get: missing / cross-tenant → 404 AGENCY_NOT_FOUND")
        void get_missing() {
            when(agencyRepository.findById(AgencyId.of("nope"), TENANT)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.getById(FAN, "nope"))
                    .isInstanceOf(AgencyNotFoundException.class);
        }
    }

    // ----- AC-2: affiliation change ---------------------------------------------

    @Nested
    @DisplayName("AC-2 — 소속 변경 (affiliation change)")
    class Affiliation {

        @Test
        @DisplayName("artist: assign → agencyId set, display name from the ENTITY, event + cache invalidation")
        void artist_assign() {
            Artist artist = publishedArtist();
            Agency ag = activeAgency("ag-2", "Nova Sound");
            when(artistRepository.findById(ArtistId.of("a-1"), TENANT)).thenReturn(Optional.of(artist));
            when(agencyRepository.findById(AgencyId.of("ag-2"), TENANT)).thenReturn(Optional.of(ag));
            when(artistRepository.update(any(Artist.class))).thenAnswer(inv -> inv.getArgument(0));
            when(agencyRepository.findNamesByIds(eq(TENANT), any())).thenReturn(Map.of("ag-2", "Nova Sound"));

            ArtistView view = service.changeArtistAgency(ADMIN, "a-1", "ag-2");

            assertThat(view.agencyId()).isEqualTo("ag-2");
            assertThat(view.agency()).isEqualTo("Nova Sound");
            verify(eventPublisher).publishArtistUpdated(eq(ArtistId.of("a-1")), eq(TENANT),
                    eq(List.of("agencyId")), eq("admin-1"), any());
            verify(directoryCache).invalidateAll(TENANT);
        }

        @Test
        @DisplayName("artist: clear (null) → agencyId null AND the old free text does not resurface")
        void artist_clear() {
            Artist artist = publishedArtist();
            artist.changeAgency(AgencyId.of("ag-1"), "Aurora Entertainment");
            when(artistRepository.findById(ArtistId.of("a-1"), TENANT)).thenReturn(Optional.of(artist));
            when(artistRepository.update(any(Artist.class))).thenAnswer(inv -> inv.getArgument(0));

            ArtistView view = service.changeArtistAgency(ADMIN, "a-1", null);

            assertThat(view.agencyId()).isNull();
            assertThat(view.agency()).isNull();
        }

        @Test
        @DisplayName("artist: ARCHIVED agency → 422 AGENCY_ARCHIVED, nothing saved")
        void artist_archivedAgency() {
            Agency ag = activeAgency("ag-2", "Nova Sound");
            ag.archive();
            when(artistRepository.findById(ArtistId.of("a-1"), TENANT)).thenReturn(Optional.of(publishedArtist()));
            when(agencyRepository.findById(AgencyId.of("ag-2"), TENANT)).thenReturn(Optional.of(ag));

            assertThatThrownBy(() -> service.changeArtistAgency(ADMIN, "a-1", "ag-2"))
                    .isInstanceOf(AgencyArchivedException.class);
            verify(artistRepository, never()).update(any());
        }

        @Test
        @DisplayName("artist: unknown / cross-tenant agency → 404 AGENCY_NOT_FOUND, nothing saved")
        void artist_unknownAgency() {
            when(artistRepository.findById(ArtistId.of("a-1"), TENANT)).thenReturn(Optional.of(publishedArtist()));
            when(agencyRepository.findById(AgencyId.of("ag-x"), TENANT)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.changeArtistAgency(ADMIN, "a-1", "ag-x"))
                    .isInstanceOf(AgencyNotFoundException.class);
            verify(artistRepository, never()).update(any());
        }

        @Test
        @DisplayName("group: assign → agencyId + entity name")
        void group_assign() {
            ArtistGroup g = ArtistGroup.create(ArtistGroupId.of("g-1"), TENANT, "STELLAR",
                    null, "Aurora Entertainment", null);
            Agency ag = activeAgency("ag-2", "Nova Sound");
            when(groupRepository.findById(ArtistGroupId.of("g-1"), TENANT)).thenReturn(Optional.of(g));
            when(agencyRepository.findById(AgencyId.of("ag-2"), TENANT)).thenReturn(Optional.of(ag));
            when(groupRepository.update(any(ArtistGroup.class))).thenAnswer(inv -> inv.getArgument(0));
            when(groupRepository.findAllMembers(ArtistGroupId.of("g-1"), TENANT)).thenReturn(List.of());
            when(agencyRepository.findNamesByIds(eq(TENANT), any())).thenReturn(Map.of("ag-2", "Nova Sound"));

            ArtistGroupView view = service.changeGroupAgency(ADMIN, "g-1", "ag-2");

            assertThat(view.agencyId()).isEqualTo("ag-2");
            assertThat(view.agency()).isEqualTo("Nova Sound");
        }

        @Test
        @DisplayName("non-admin → 403 before any lookup")
        void nonAdmin() {
            assertThatThrownBy(() -> service.changeArtistAgency(FAN, "a-1", "ag-2"))
                    .isInstanceOf(AdminRoleRequiredException.class);
            verifyNoInteractions(artistRepository, agencyRepository);
        }
    }

    // ----- AC-3: store seller link ----------------------------------------------

    @Nested
    @DisplayName("AC-3 — 셀러 연결 (fail-closed)")
    class SellerLink {

        private Agency agency;

        private void givenAgency() {
            agency = activeAgency("ag-1", "Aurora Entertainment");
            when(agencyRepository.findById(AgencyId.of("ag-1"), TENANT)).thenReturn(Optional.of(agency));
        }

        @Test
        @DisplayName("existing ACTIVE seller → saved")
        void active_saved() {
            givenAgency();
            when(storeSellerDirectory.findStatus("aurora-goods")).thenReturn(Optional.of("ACTIVE"));
            when(agencyRepository.update(any(Agency.class))).thenAnswer(inv -> inv.getArgument(0));

            AgencyView view = service.linkStoreSeller(ADMIN, "ag-1", "aurora-goods");

            assertThat(view.storeSellerId()).isEqualTo("aurora-goods");
            ArgumentCaptor<Agency> saved = ArgumentCaptor.forClass(Agency.class);
            verify(agencyRepository).update(saved.capture());
            assertThat(saved.getValue().getStoreSellerId()).isEqualTo("aurora-goods");
        }

        @ParameterizedTest(name = "seller status {0} → saved (ADR-079 D2 refuses only missing / CLOSED)")
        @ValueSource(strings = {"SUSPENDED", "PENDING_PROVISIONING"})
        void nonClosedStates_saved(String status) {
            givenAgency();
            when(storeSellerDirectory.findStatus("s-1")).thenReturn(Optional.of(status));
            when(agencyRepository.update(any(Agency.class))).thenAnswer(inv -> inv.getArgument(0));

            assertThat(service.linkStoreSeller(ADMIN, "ag-1", "s-1").storeSellerId()).isEqualTo("s-1");
        }

        @Test
        @DisplayName("seller the store does not have → 422 STORE_SELLER_NOT_FOUND, NOT saved")
        void missing_refused() {
            givenAgency();
            when(storeSellerDirectory.findStatus("ghost")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.linkStoreSeller(ADMIN, "ag-1", "ghost"))
                    .isInstanceOf(StoreSellerNotFoundException.class);
            verify(agencyRepository, never()).update(any());
            assertThat(agency.getStoreSellerId()).isNull();
        }

        @Test
        @DisplayName("CLOSED seller → 422 STORE_SELLER_CLOSED, NOT saved")
        void closed_refused() {
            givenAgency();
            when(storeSellerDirectory.findStatus("gone")).thenReturn(Optional.of("CLOSED"));

            assertThatThrownBy(() -> service.linkStoreSeller(ADMIN, "ag-1", "gone"))
                    .isInstanceOf(StoreSellerClosedException.class);
            verify(agencyRepository, never()).update(any());
        }

        @Test
        @DisplayName("🔴 lookup failure → 503 STORE_SELLER_LOOKUP_UNAVAILABLE, NOT saved (Failure Scenario 2)")
        void lookupFailure_notSaved() {
            givenAgency();
            when(storeSellerDirectory.findStatus("aurora-goods"))
                    .thenThrow(new StoreSellerLookupUnavailableException("store down"));

            assertThatThrownBy(() -> service.linkStoreSeller(ADMIN, "ag-1", "aurora-goods"))
                    .isInstanceOf(StoreSellerLookupUnavailableException.class);
            verify(agencyRepository, never()).update(any());
            assertThat(agency.getStoreSellerId()).isNull();
        }

        @Test
        @DisplayName("🔴 an adapter leaking a raw exception is still «could not verify» → NOT saved")
        void rawException_notSaved() {
            givenAgency();
            when(storeSellerDirectory.findStatus(anyString())).thenThrow(new IllegalStateException("boom"));

            assertThatThrownBy(() -> service.linkStoreSeller(ADMIN, "ag-1", "aurora-goods"))
                    .isInstanceOf(StoreSellerLookupUnavailableException.class);
            verify(agencyRepository, never()).update(any());
        }

        @Test
        @DisplayName("🔴 an unrecognised status is not an answer → NOT saved")
        void unknownStatus_notSaved() {
            givenAgency();
            when(storeSellerDirectory.findStatus("s-1")).thenReturn(Optional.of("BANANA"));

            assertThatThrownBy(() -> service.linkStoreSeller(ADMIN, "ag-1", "s-1"))
                    .isInstanceOf(StoreSellerLookupUnavailableException.class);
            verify(agencyRepository, never()).update(any());
        }

        @Test
        @DisplayName("clear (null) → saved WITHOUT asking the store")
        void clear_noLookup() {
            givenAgency();
            agency.linkStoreSeller("old");
            when(agencyRepository.update(any(Agency.class))).thenAnswer(inv -> inv.getArgument(0));

            assertThat(service.linkStoreSeller(ADMIN, "ag-1", null).storeSellerId()).isNull();
            verifyNoInteractions(storeSellerDirectory);
        }

        @Test
        @DisplayName("ARCHIVED agency → 422, store never asked")
        void archivedAgency() {
            givenAgency();
            agency.archive();

            assertThatThrownBy(() -> service.linkStoreSeller(ADMIN, "ag-1", "s-1"))
                    .isInstanceOf(AgencyArchivedException.class);
            verifyNoInteractions(storeSellerDirectory);
        }

        @Test
        @DisplayName("non-admin → 403, store never asked")
        void nonAdmin() {
            assertThatThrownBy(() -> service.linkStoreSeller(FAN, "ag-1", "s-1"))
                    .isInstanceOf(AdminRoleRequiredException.class);
            verifyNoInteractions(storeSellerDirectory, agencyRepository);
        }
    }
}
