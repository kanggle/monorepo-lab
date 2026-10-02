package com.example.fanplatform.artist.application.service;

import com.example.common.id.UuidV7;
import com.example.common.page.PageResult;
import com.example.fanplatform.artist.application.ActorContext;
import com.example.fanplatform.artist.application.exception.AgencyArchivedException;
import com.example.fanplatform.artist.application.exception.AgencyNameConflictException;
import com.example.fanplatform.artist.application.exception.ArtistGroupNotFoundException;
import com.example.fanplatform.artist.application.exception.ArtistNotFoundException;
import com.example.fanplatform.artist.application.exception.StoreSellerClosedException;
import com.example.fanplatform.artist.application.exception.StoreSellerLookupUnavailableException;
import com.example.fanplatform.artist.application.exception.StoreSellerNotFoundException;
import com.example.fanplatform.artist.application.port.in.AgencyView;
import com.example.fanplatform.artist.application.port.in.ArtistGroupView;
import com.example.fanplatform.artist.application.port.in.ArtistView;
import com.example.fanplatform.artist.application.port.in.ChangeAgencyAffiliationUseCase;
import com.example.fanplatform.artist.application.port.in.LinkAgencyStoreSellerUseCase;
import com.example.fanplatform.artist.application.port.in.ManageAgencyUseCase;
import com.example.fanplatform.artist.application.port.out.AgencyRepository;
import com.example.fanplatform.artist.application.port.out.ArtistDirectoryCache;
import com.example.fanplatform.artist.application.port.out.ArtistEventPublisher;
import com.example.fanplatform.artist.application.port.out.ArtistGroupRepository;
import com.example.fanplatform.artist.application.port.out.ArtistRepository;
import com.example.fanplatform.artist.application.port.out.StoreSellerDirectory;
import com.example.fanplatform.artist.domain.agency.Agency;
import com.example.fanplatform.artist.domain.agency.AgencyId;
import com.example.fanplatform.artist.domain.artist.Artist;
import com.example.fanplatform.artist.domain.group.ArtistGroup;
import com.example.fanplatform.artist.domain.group.ArtistGroupId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Agency CRUD, affiliation changes and the store-seller link — TASK-MONO-748
 * (ADR-MONO-079 D1 · D2).
 *
 * <p>Every write is admin-tier (today's {@code ADMIN_ROLES} gate, same as the rest of
 * the directory — {@code TASK-MONO-750} opens the platform-operator path).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgencyService implements
        ManageAgencyUseCase,
        LinkAgencyStoreSellerUseCase,
        ChangeAgencyAffiliationUseCase {

    /**
     * Store seller states that may be linked. ADR-MONO-079 D2 refuses exactly «없거나
     * {@code CLOSED}»; a SUSPENDED seller is a temporary state the store can lift, and
     * PENDING_PROVISIONING is a seller the store has registered but not yet provisioned —
     * neither is a reason to refuse the link. Anything NOT in this set and not
     * {@code CLOSED} is an answer this service does not understand → fail-closed.
     */
    static final Set<String> LINKABLE_SELLER_STATUSES =
            Set.of("ACTIVE", "SUSPENDED", "PENDING_PROVISIONING");
    static final String CLOSED_SELLER_STATUS = "CLOSED";

    private static final int MAX_PAGE_SIZE = 100;

    private final AgencyRepository agencyRepository;
    private final ArtistRepository artistRepository;
    private final ArtistGroupRepository groupRepository;
    private final StoreSellerDirectory storeSellerDirectory;
    private final ArtistEventPublisher eventPublisher;
    private final ArtistDirectoryCache directoryCache;

    // ----- CRUD ---------------------------------------------------------------

    @Override
    @Transactional
    public AgencyView create(ActorContext actor, String name) {
        ActorGuard.requireAdmin(actor);
        String tenantId = actor.tenantId();
        Agency agency = Agency.create(AgencyId.of(UuidV7.randomString()), tenantId, name);
        // Pre-check on the NORMALISED name for a clean 409; the UNIQUE constraint is the
        // race guard (translated to the same exception by the adapter).
        if (agencyRepository.existsByTenantIdAndName(tenantId, agency.getName())) {
            throw new AgencyNameConflictException(agency.getName());
        }
        return AgencyView.from(agencyRepository.insert(agency));
    }

    @Override
    @Transactional
    public AgencyView rename(ActorContext actor, String agencyId, String name) {
        ActorGuard.requireAdmin(actor);
        String tenantId = actor.tenantId();
        Agency agency = AgencySupport.load(agencyRepository, agencyId, tenantId);
        if (!agency.isActive()) {
            throw new AgencyArchivedException(agencyId);
        }
        String normalized = Agency.normalizeName(name);
        if (normalized != null && normalized.equals(agency.getName())) {
            return AgencyView.from(agency);
        }
        if (normalized != null && agencyRepository.existsByTenantIdAndName(tenantId, normalized)) {
            throw new AgencyNameConflictException(normalized);
        }
        agency.rename(name);
        Agency saved = agencyRepository.update(agency);
        // Directory pages cache the displayed agency name.
        directoryCache.invalidateAll(tenantId);
        return AgencyView.from(saved);
    }

    @Override
    @Transactional
    public AgencyView archive(ActorContext actor, String agencyId) {
        ActorGuard.requireAdmin(actor);
        Agency agency = AgencySupport.load(agencyRepository, agencyId, actor.tenantId());
        if (!agency.isActive()) {
            throw new AgencyArchivedException(agencyId);
        }
        // Existing affiliations are kept — their display must not vanish (Failure
        // Scenario 1); only NEW affiliations are refused from now on.
        agency.archive();
        return AgencyView.from(agencyRepository.update(agency));
    }

    @Override
    @Transactional(readOnly = true)
    public AgencyView getById(ActorContext actor, String agencyId) {
        return AgencyView.from(AgencySupport.load(agencyRepository, agencyId, actor.tenantId()));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<AgencyView> list(ActorContext actor, int page, int size) {
        int p = Math.max(0, page);
        int s = Math.max(1, Math.min(MAX_PAGE_SIZE, size));
        PageResult<Agency> result = agencyRepository.findPage(actor.tenantId(), p, s);
        List<AgencyView> items = result.content().stream().map(AgencyView::from).toList();
        return new PageResult<>(items, result.page(), result.size(),
                result.totalElements(), result.totalPages());
    }

    // ----- store seller link (ADR-079 D2, AC-3) ---------------------------------

    @Override
    @Transactional
    public AgencyView linkStoreSeller(ActorContext actor, String agencyId, String storeSellerId) {
        ActorGuard.requireAdmin(actor);
        Agency agency = AgencySupport.load(agencyRepository, agencyId, actor.tenantId());
        if (!agency.isActive()) {
            throw new AgencyArchivedException(agencyId);
        }
        if (storeSellerId == null) {
            // Clearing needs no lookup — there is nothing to verify.
            agency.linkStoreSeller(null);
            return AgencyView.from(agencyRepository.update(agency));
        }
        if (storeSellerId.isBlank() || storeSellerId.length() > Agency.STORE_SELLER_ID_MAX) {
            throw new IllegalArgumentException(
                    "storeSellerId must be 1.." + Agency.STORE_SELLER_ID_MAX + " chars or null");
        }
        verifySeller(storeSellerId);
        agency.linkStoreSeller(storeSellerId);
        return AgencyView.from(agencyRepository.update(agency));
    }

    /**
     * Fail-closed verification. Returns normally ONLY when the store definitively
     * answered that the seller exists in a linkable state; every other outcome throws,
     * and the caller saves nothing.
     */
    private void verifySeller(String sellerId) {
        Optional<String> status;
        try {
            status = storeSellerDirectory.findStatus(sellerId);
        } catch (StoreSellerLookupUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            // An adapter that leaks a raw exception is still «could not verify».
            log.warn("store seller lookup failed (seller={}) → not saved: {}", sellerId, e.toString());
            throw new StoreSellerLookupUnavailableException(
                    "store seller lookup failed; the link was not saved", e);
        }
        if (status == null) {
            throw new StoreSellerLookupUnavailableException(
                    "store seller lookup returned no answer; the link was not saved");
        }
        if (status.isEmpty()) {
            throw new StoreSellerNotFoundException(sellerId);
        }
        String s = status.get();
        if (CLOSED_SELLER_STATUS.equals(s)) {
            throw new StoreSellerClosedException(sellerId);
        }
        if (!LINKABLE_SELLER_STATUSES.contains(s)) {
            throw new StoreSellerLookupUnavailableException(
                    "store returned an unrecognised seller status '" + s + "'; the link was not saved");
        }
    }

    // ----- affiliation (AC-2 «소속 변경») -----------------------------------------

    @Override
    @Transactional
    public ArtistView changeArtistAgency(ActorContext actor, String artistId, String agencyId) {
        ActorGuard.requireAdmin(actor);
        String tenantId = actor.tenantId();
        Artist artist = artistRepository.findById(ActorGuard.parseArtistId(artistId), tenantId)
                .orElseThrow(() -> new ArtistNotFoundException(artistId));
        Agency agency = agencyId == null
                ? null
                : AgencySupport.loadAffiliable(agencyRepository, agencyId, tenantId);
        artist.changeAgency(agency == null ? null : agency.getId(),
                agency == null ? null : agency.getName());
        Artist saved = artistRepository.update(artist);
        eventPublisher.publishArtistUpdated(saved.getId(), tenantId,
                List.of("agencyId"), actor.accountId(), Instant.now());
        if (saved.isPublished()) {
            directoryCache.invalidateAll(tenantId);
        }
        return ArtistView.from(saved,
                AgencySupport.names(agencyRepository, tenantId, saved.getAgencyId()));
    }

    @Override
    @Transactional
    public ArtistGroupView changeGroupAgency(ActorContext actor, String groupId, String agencyId) {
        ActorGuard.requireAdmin(actor);
        String tenantId = actor.tenantId();
        ArtistGroupId gid;
        try {
            gid = ArtistGroupId.of(groupId);
        } catch (IllegalArgumentException e) {
            throw new ArtistGroupNotFoundException(groupId);
        }
        ArtistGroup group = groupRepository.findById(gid, tenantId)
                .orElseThrow(() -> new ArtistGroupNotFoundException(groupId));
        Agency agency = agencyId == null
                ? null
                : AgencySupport.loadAffiliable(agencyRepository, agencyId, tenantId);
        group.changeAgency(agency == null ? null : agency.getId(),
                agency == null ? null : agency.getName());
        ArtistGroup saved = groupRepository.update(group);
        return ArtistGroupView.from(saved, groupRepository.findAllMembers(saved.getId(), tenantId),
                AgencySupport.names(agencyRepository, tenantId, saved.getAgencyId()));
    }
}
