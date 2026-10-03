package com.example.fanplatform.artist.application.service;

import com.example.common.page.PageResult;
import com.example.fanplatform.artist.application.ActorContext;
import com.example.fanplatform.artist.application.exception.StoreSellerClosedException;
import com.example.fanplatform.artist.application.exception.StoreSellerLookupUnavailableException;
import com.example.fanplatform.artist.application.exception.StoreSellerNotFoundException;
import com.example.fanplatform.artist.application.port.out.AgencyRepository;
import com.example.fanplatform.artist.application.port.out.ArtistDirectoryCache;
import com.example.fanplatform.artist.application.port.out.ArtistEventPublisher;
import com.example.fanplatform.artist.application.port.out.ArtistGroupRepository;
import com.example.fanplatform.artist.application.port.out.ArtistRepository;
import com.example.fanplatform.artist.config.StoreSellerDirectoryConfig;
import com.example.fanplatform.artist.domain.agency.Agency;
import com.example.fanplatform.artist.domain.agency.AgencyId;
import com.example.fanplatform.artist.testsupport.FakeIdpAndStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * TASK-MONO-759 AC-1 · AC-2 — the agency → store-seller link over the <b>real transport path</b>:
 * the real {@link AgencyService} and the real adapter (built by the production
 * {@link StoreSellerDirectoryConfig}) talk real HTTP to an IdP + store stand-in. Nothing between
 * the use case and the socket is a fake; the port is NOT stubbed (the AC's «포트 대역이 아니라»).
 *
 * <p>«Saved» is read back from the repository after each call — a refusal that still persisted
 * would pass an exception-only assertion. The repository is an in-memory copy-on-write store so
 * an in-place mutation of the loaded entity cannot masquerade as a save.
 *
 * <p>Docker-free, so it runs in {@code :artist-service:check}. The Spring-wired variant over the
 * real schema is {@code StoreSellerLinkTransportIntegrationTest} (Testcontainers, CI).
 */
@DisplayName("Agency → store seller link over the real transport (TASK-MONO-759 AC-1 · AC-2)")
class AgencyStoreSellerTransportTest {

    private static final String TENANT = "fan-platform";
    private static final ActorContext ADMIN = new ActorContext("admin-1", TENANT, Set.of("ADMIN"));
    private static final String AGENCY = "ag-1";

    private FakeIdpAndStore fake;
    private InMemoryAgencies agencies;
    private AgencyService service;

    @BeforeEach
    void setUp() throws Exception {
        fake = new FakeIdpAndStore();
        agencies = new InMemoryAgencies();
        agencies.update(Agency.create(AgencyId.of(AGENCY), TENANT, "Aurora Entertainment"));
        service = new AgencyService(agencies, mock(ArtistRepository.class), mock(ArtistGroupRepository.class),
                new StoreSellerDirectoryConfig().httpStoreSellerDirectory(
                        fake.tokenUri(), "artist-service-client", "secret", "store.seller.read",
                        "ecommerce", fake.baseUrl(), 1000, 1000),
                mock(ArtistEventPublisher.class), mock(ArtistDirectoryCache.class));
    }

    @AfterEach
    void tearDown() throws Exception {
        fake.close();
    }

    private String saved() {
        return agencies.findById(AgencyId.of(AGENCY), TENANT).orElseThrow().getStoreSellerId();
    }

    @Test
    @DisplayName("AC-1 — existing ACTIVE seller → saved · SUSPENDED → saved · missing / CLOSED → refused, unchanged")
    void ac1_rulesOverTheWire() {
        fake.active("good", "ACTIVE");
        fake.active("paused", "SUSPENDED");
        fake.active("gone", "CLOSED");

        assertThat(service.linkStoreSeller(ADMIN, AGENCY, "good").storeSellerId()).isEqualTo("good");
        assertThat(saved()).isEqualTo("good");

        assertThatThrownBy(() -> service.linkStoreSeller(ADMIN, AGENCY, "ghost"))
                .isInstanceOf(StoreSellerNotFoundException.class);
        assertThat(saved()).isEqualTo("good");

        assertThatThrownBy(() -> service.linkStoreSeller(ADMIN, AGENCY, "gone"))
                .isInstanceOf(StoreSellerClosedException.class);
        assertThat(saved()).isEqualTo("good");

        // Edge Case 1: SUSPENDED is reversible, D2 refuses only «없거나 CLOSED».
        assertThat(service.linkStoreSeller(ADMIN, AGENCY, "paused").storeSellerId()).isEqualTo("paused");
        assertThat(saved()).isEqualTo("paused");

        // Every one of those decisions was a real store answer, not a short-circuit.
        assertThat(fake.storeCalls()).isEqualTo(4);
    }

    @Test
    @DisplayName("AC-1 — a lookup failure (store 500) → not saved (fail-closed)")
    void ac1_lookupFailureNotSaved() {
        fake.active("good", "ACTIVE");
        service.linkStoreSeller(ADMIN, AGENCY, "good");
        fake.overrides.put("flaky", new Object[] {500, "{\"code\":\"INTERNAL_ERROR\"}"});

        assertThatThrownBy(() -> service.linkStoreSeller(ADMIN, AGENCY, "flaky"))
                .isInstanceOf(StoreSellerLookupUnavailableException.class);
        assertThat(saved()).isEqualTo("good");
    }

    /**
     * AC-2 — «열린 경로 + 닫힌 경로» in ONE test: the same service, the same seller, the same
     * request; only the availability of the store / IdP changes. If the 503 cells were caused by
     * the request rather than by the outage, the open cells around them would fail too.
     */
    @Test
    @DisplayName("🔴 AC-2 — open → store down (503, unchanged) → open → IdP down (503, unchanged) → open")
    void ac2_openClosedOpen() {
        fake.active("s-1", "ACTIVE");
        fake.active("s-2", "ACTIVE");

        // open
        assertThat(service.linkStoreSeller(ADMIN, AGENCY, "s-1").storeSellerId()).isEqualTo("s-1");
        assertThat(saved()).isEqualTo("s-1");

        // closed: the store is down
        fake.storeUp = false;
        assertThatThrownBy(() -> service.linkStoreSeller(ADMIN, AGENCY, "s-2"))
                .isInstanceOf(StoreSellerLookupUnavailableException.class);
        assertThat(saved()).as("store down → nothing saved").isEqualTo("s-1");

        // open again — same seller that was just refused
        fake.storeUp = true;
        assertThat(service.linkStoreSeller(ADMIN, AGENCY, "s-2").storeSellerId()).isEqualTo("s-2");
        assertThat(saved()).isEqualTo("s-2");

        // closed: the IdP is down. A fresh adapter, so no cached token can carry it through.
        fake.idpUp = false;
        AgencyService coldService = new AgencyService(agencies, mock(ArtistRepository.class),
                mock(ArtistGroupRepository.class),
                new StoreSellerDirectoryConfig().httpStoreSellerDirectory(
                        fake.tokenUri(), "artist-service-client", "secret", "store.seller.read",
                        "ecommerce", fake.baseUrl(), 1000, 1000),
                mock(ArtistEventPublisher.class), mock(ArtistDirectoryCache.class));
        long storeCallsBefore = fake.storeCalls();
        assertThatThrownBy(() -> coldService.linkStoreSeller(ADMIN, AGENCY, "s-1"))
                .isInstanceOf(StoreSellerLookupUnavailableException.class);
        assertThat(saved()).as("IdP down → nothing saved").isEqualTo("s-2");
        assertThat(fake.storeCalls()).as("no token → the store is never asked").isEqualTo(storeCallsBefore);

        // open again
        fake.idpUp = true;
        assertThat(coldService.linkStoreSeller(ADMIN, AGENCY, "s-1").storeSellerId()).isEqualTo("s-1");
        assertThat(saved()).isEqualTo("s-1");
    }

    @Test
    @DisplayName("clearing (null) needs no lookup — works with the store AND the IdP down")
    void clearNeedsNoLookup() {
        fake.active("good", "ACTIVE");
        service.linkStoreSeller(ADMIN, AGENCY, "good");
        fake.storeUp = false;
        fake.idpUp = false;
        long before = fake.requests.size();

        assertThat(service.linkStoreSeller(ADMIN, AGENCY, null).storeSellerId()).isNull();
        assertThat(saved()).isNull();
        assertThat(fake.requests).hasSize((int) before);
    }

    // ------------------------------------------------------------------ in-memory store

    /** Copy-on-read / copy-on-write so only {@link #update} can change what is «saved». */
    private static final class InMemoryAgencies implements AgencyRepository {
        private final Map<String, Agency> rows = new HashMap<>();

        private static Agency copy(Agency a) {
            return Agency.reconstitute(a.getId(), a.getTenantId(), a.getName(), a.getStatus(),
                    a.getStoreSellerId(), a.getCreatedAt(), a.getUpdatedAt(), a.getVersion());
        }

        @Override public Agency insert(Agency agency) { return update(agency); }

        @Override public Agency update(Agency agency) {
            rows.put(agency.getId().value(), copy(agency));
            return copy(agency);
        }

        @Override public Optional<Agency> findById(AgencyId id, String tenantId) {
            Agency a = rows.get(id.value());
            return a == null || !a.getTenantId().equals(tenantId) ? Optional.empty() : Optional.of(copy(a));
        }

        @Override public boolean existsByTenantIdAndName(String tenantId, String name) { return false; }

        @Override public PageResult<Agency> findPage(String tenantId, int page, int size) {
            return new PageResult<>(List.of(), page, size, 0L, 0);
        }

        @Override public Map<String, String> findNamesByIds(String tenantId, Collection<String> ids) {
            return Map.of();
        }
    }
}
