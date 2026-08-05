package com.example.fanplatform.community.application;

import com.example.common.page.PageResult;
import com.example.fanplatform.community.application.port.out.FeedCache;
import com.example.fanplatform.community.domain.comment.CommentRepository;
import com.example.fanplatform.community.domain.membership.MembershipChecker;
import com.example.fanplatform.community.domain.post.PostRepository;
import com.example.fanplatform.community.domain.post.PostType;
import com.example.fanplatform.community.domain.post.PostVisibility;
import com.example.fanplatform.community.domain.reaction.ReactionRepository;
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
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * TASK-FAN-BE-046 — the feed must not serve an authorization decision that has stopped being
 * true.
 *
 * <p>Every test here drives a cache <strong>hit</strong>, because that is the path where the
 * defect lived: the entry was written while the fan was entitled and kept being returned
 * verbatim for the TTL. {@code GetFeedUseCaseTest} already covers the miss path.
 *
 * <p>The measured symptom this pins: after {@code POST /memberships/{id}/cancel} the detail
 * route returned 403 immediately while the feed still answered {@code locked:false} with the
 * gated title and a 200-character body preview, and deleting the Redis key alone flipped it.
 *
 * <p>Both directions are asserted deliberately. A suite that only proves locking cannot tell
 * "the gate is evaluated at read time" from "the gate is stuck closed", and a fan who
 * subscribes would then wait out the TTL staring at a locked post — the same bug, mirrored.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class GetFeedUseCaseEntitlementFreshnessTest {

    private static final String TENANT = "fan-platform";
    private static final String AUTHOR = "artist-1";
    private static final String FAN = "fan-1";

    @Mock PostRepository postRepository;
    @Mock CommentRepository commentRepository;
    @Mock ReactionRepository reactionRepository;
    @Mock MembershipChecker membershipChecker;
    @Mock FeedCache feedCache;

    @InjectMocks GetFeedUseCase useCase;

    private ActorContext fan() {
        return new ActorContext(FAN, TENANT, Set.of("FAN"));
    }

    private FeedItemSnapshot snapshot(String id, PostVisibility visibility, String author) {
        return new FeedItemSnapshot(id, PostType.ARTIST_POST, visibility, author,
                "작업실 이야기", "멤버십 가입해 주셔서 감사합니다", 0L, 0L, Instant.EPOCH);
    }

    /** Primes the cache with a page whose titles are present — i.e. written while entitled. */
    private void cacheHit(FeedItemSnapshot... items) {
        when(feedCache.readPage(TENANT, FAN, 0, 10))
                .thenReturn(Optional.of(new PageResult<>(List.of(items), 0, 10, items.length, 1)));
    }

    private List<FeedItemView> feed() {
        return useCase.execute(fan(), 0, 10).content();
    }

    // ------------------------------------------------------------------
    // AC-1 — entitlement lost: locks on the very next request, cache intact
    // ------------------------------------------------------------------

    @Test
    @DisplayName("AC-1: 자격을 잃으면 캐시를 비우지 않아도 즉시 locked=true 이고 제목·미리보기가 사라진다")
    void entitlementLost_locksOnTheNextRequest_withoutEvictingTheCache() {
        cacheHit(snapshot("p1", PostVisibility.MEMBERS_ONLY, AUTHOR));
        when(membershipChecker.hasAccess(FAN, PostVisibility.MEMBERS_ONLY.name(), TENANT))
                .thenReturn(false);

        FeedItemView item = feed().get(0);

        assertThat(item.locked()).isTrue();
        // The two fields the ticket measured leaking: the title and the 200-char preview.
        assertThat(item.title()).isNull();
        assertThat(item.bodyPreview()).isNull();
        // Nothing was invalidated — the fix is that there is no decision in the cache to
        // invalidate. Asserting this is what separates "recomputed" from "evicted and rebuilt".
        verify(feedCache, never()).cachePage(anyString(), anyString(), anyInt(), anyInt(), any());
        verifyNoInteractions(postRepository);
    }

    // ------------------------------------------------------------------
    // AC-2 — the other direction. Subscribing must open just as fast.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("AC-2 음성 대조: 자격을 얻으면 캐시가 살아 있어도 즉시 열린다 (구독 후 5분 공백 없음)")
    void entitlementGained_unlocksOnTheNextRequest() {
        cacheHit(snapshot("p1", PostVisibility.MEMBERS_ONLY, AUTHOR));
        when(membershipChecker.hasAccess(FAN, PostVisibility.MEMBERS_ONLY.name(), TENANT))
                .thenReturn(true);

        FeedItemView item = feed().get(0);

        assertThat(item.locked()).isFalse();
        assertThat(item.title()).isEqualTo("작업실 이야기");
        assertThat(item.bodyPreview()).isEqualTo("멤버십 가입해 주셔서 감사합니다");
    }

    @Test
    @DisplayName("AC-2: 같은 캐시 엔트리가 자격 판정에 따라 두 답을 낸다 — 캐시가 답을 고정하지 않는다")
    void sameCachedEntry_yieldsBothAnswers() {
        when(feedCache.readPage(TENANT, FAN, 0, 10)).thenReturn(
                Optional.of(new PageResult<>(
                        List.of(snapshot("p1", PostVisibility.PREMIUM, AUTHOR)), 0, 10, 1, 1)));
        when(membershipChecker.hasAccess(FAN, PostVisibility.PREMIUM.name(), TENANT))
                .thenReturn(true, false);

        boolean firstLocked = feed().get(0).locked();
        boolean secondLocked = feed().get(0).locked();

        // One cached page, two requests, two different answers. If the cache held the
        // decision this could not happen — which is precisely how the defect presented.
        assertThat(firstLocked).isFalse();
        assertThat(secondLocked).isTrue();
    }

    // ------------------------------------------------------------------
    // AC-3 — the cache must still be doing its job
    // ------------------------------------------------------------------

    @Test
    @DisplayName("AC-3: 캐시 히트는 여전히 DB 왕복 0회다 (무력화가 아니라 수정임을 못박는다)")
    void cacheHit_stillCostsZeroDatabaseRoundTrips() {
        cacheHit(snapshot("p1", PostVisibility.PUBLIC, AUTHOR));

        feed();

        // Fixing correctness by simply not caching would also make AC-1/AC-2 pass. This is the
        // assertion that would fail in that case.
        verifyNoInteractions(postRepository, commentRepository, reactionRepository);
    }

    @Test
    @DisplayName("AC-3: 한 페이지에 같은 티어가 여러 건이어도 멤버십 조회는 티어당 1회다")
    void oneCheckPerTier_notPerItem() {
        cacheHit(
                snapshot("p1", PostVisibility.MEMBERS_ONLY, AUTHOR),
                snapshot("p2", PostVisibility.MEMBERS_ONLY, AUTHOR),
                snapshot("p3", PostVisibility.MEMBERS_ONLY, AUTHOR));
        when(membershipChecker.hasAccess(FAN, PostVisibility.MEMBERS_ONLY.name(), TENANT))
                .thenReturn(false);

        feed();

        // Per-item checking is what the old miss path did (up to 50 remote calls per page).
        // Moving the gate to read time would have made the hit path inherit that shape.
        verify(membershipChecker, times(1))
                .hasAccess(FAN, PostVisibility.MEMBERS_ONLY.name(), TENANT);
    }

    @Test
    @DisplayName("AC-3: 공개 글만 있는 페이지는 멤버십 서비스를 아예 부르지 않는다")
    void publicOnlyPage_callsMembershipServiceZeroTimes() {
        cacheHit(
                snapshot("p1", PostVisibility.PUBLIC, AUTHOR),
                snapshot("p2", PostVisibility.PUBLIC, AUTHOR));

        feed();

        verifyNoInteractions(membershipChecker);
    }

    @Test
    @DisplayName("AC-3: 두 티어가 섞이면 정확히 2회 — 항목 수와 무관하다")
    void mixedTiers_costExactlyTwoChecks() {
        cacheHit(
                snapshot("p1", PostVisibility.MEMBERS_ONLY, AUTHOR),
                snapshot("p2", PostVisibility.PREMIUM, AUTHOR),
                snapshot("p3", PostVisibility.MEMBERS_ONLY, AUTHOR),
                snapshot("p4", PostVisibility.PREMIUM, AUTHOR),
                snapshot("p5", PostVisibility.PUBLIC, AUTHOR));
        when(membershipChecker.hasAccess(eq(FAN), anyString(), eq(TENANT))).thenReturn(false);

        feed();

        verify(membershipChecker, times(2)).hasAccess(eq(FAN), anyString(), eq(TENANT));
    }

    // ------------------------------------------------------------------
    // Author short-circuit survives the move
    // ------------------------------------------------------------------

    @Test
    @DisplayName("작성자 본인의 유료 글은 캐시 히트에서도 자격 조회 없이 열린다")
    void ownPost_isUnlockedWithoutAskingMembershipService() {
        cacheHit(snapshot("p1", PostVisibility.PREMIUM, FAN));

        FeedItemView item = feed().get(0);

        assertThat(item.locked()).isFalse();
        assertThat(item.title()).isEqualTo("작업실 이야기");
        verifyNoInteractions(membershipChecker);
    }

    // ------------------------------------------------------------------
    // The cache is written with the projection, decision-free
    // ------------------------------------------------------------------

    @Test
    @DisplayName("캐시에 쓰이는 값에는 자격 판정이 없다 — 잠긴 글도 제목을 들고 저장된다")
    void whatGetsCached_carriesNoDecision() {
        when(feedCache.readPage(TENANT, FAN, 0, 10)).thenReturn(Optional.empty());
        var post = com.example.fanplatform.community.domain.post.Post.createDraft(
                "p1", TENANT, AUTHOR, PostType.ARTIST_POST, PostVisibility.MEMBERS_ONLY,
                "작업실 이야기", "본문", null);
        post.publish(com.example.fanplatform.community.domain.post.status.ActorType.AUTHOR);
        when(postRepository.findFeedForFan(FAN, TENANT, 0, 10))
                .thenReturn(new PageResult<>(List.of(post), 0, 10, 1, 1));
        when(commentRepository.countsByPostIds(List.of("p1"), TENANT)).thenReturn(java.util.Map.of());
        when(reactionRepository.countsByPostIds(List.of("p1"), TENANT)).thenReturn(java.util.Map.of());
        when(membershipChecker.hasAccess(FAN, PostVisibility.MEMBERS_ONLY.name(), TENANT))
                .thenReturn(false);

        FeedItemView returned = feed().get(0);

        var captor = org.mockito.ArgumentCaptor.forClass(PageResult.class);
        verify(feedCache).cachePage(eq(TENANT), eq(FAN), eq(0), eq(10), captor.capture());
        @SuppressWarnings("unchecked")
        FeedItemSnapshot cached =
                ((PageResult<FeedItemSnapshot>) captor.getValue()).content().get(0);

        // The reader is denied, so the response hides the title...
        assertThat(returned.locked()).isTrue();
        assertThat(returned.title()).isNull();
        // ...but the cached projection keeps it, so the same entry can answer "unlocked"
        // the moment this fan subscribes. Caching the nulled-out view is what made the
        // decision sticky in the first place.
        assertThat(cached.title()).isEqualTo("작업실 이야기");
        assertThat(cached.bodyPreview()).isEqualTo("본문");
    }
}
