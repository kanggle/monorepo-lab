package com.example.fanplatform.community.application;

import com.example.common.page.PageResult;
import com.example.fanplatform.community.application.port.out.FeedCache;
import com.example.fanplatform.community.domain.comment.CommentRepository;
import com.example.fanplatform.community.domain.membership.MembershipChecker;
import com.example.fanplatform.community.domain.post.Post;
import com.example.fanplatform.community.domain.post.PostRepository;
import com.example.fanplatform.community.domain.post.PostType;
import com.example.fanplatform.community.domain.post.PostVisibility;
import com.example.fanplatform.community.domain.post.status.ActorType;
import com.example.fanplatform.community.domain.reaction.ReactionRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * TASK-FAN-BE-053 — the feed's {@code commentCount}/{@code reactionCount} are the values at the
 * time of the read, not the values at cache-fill time.
 *
 * <p>The measured symptom this pins (35th demo window, 2026-10-25 09:23Z): right after a
 * reaction PUT, {@code GET /posts/{id}} said {@code reactionCount 1} while {@code GET /feed} said
 * {@code 0}, with 297 seconds left on the Redis key. The counts had been baked into the cached
 * {@link FeedItemSnapshot} page and were served verbatim until it expired.
 *
 * <p>Every freshness cell drives a cache <strong>hit</strong> with a snapshot whose baked counts
 * differ from what the repositories return now — that is the only arrangement in which "served
 * the live value" and "served the cached value" give different answers. Same construction as
 * {@code GetFeedUseCaseEntitlementFreshnessTest} (TASK-FAN-BE-046), which solved the same class of
 * problem for the {@code locked} decision.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class GetFeedUseCaseCountFreshnessTest {

    private static final String TENANT = "fan-platform";
    private static final String AUTHOR = "artist-1";
    private static final String FAN = "fan-1";

    @Mock PostRepository postRepository;
    @Mock CommentRepository commentRepository;
    @Mock ReactionRepository reactionRepository;
    @Mock MembershipChecker membershipChecker;
    @Mock FeedCache feedCache;

    SimpleMeterRegistry meterRegistry;
    GetFeedUseCase useCase;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        useCase = new GetFeedUseCase(
                new FeedReader(postRepository, commentRepository, reactionRepository),
                membershipChecker, feedCache, meterRegistry);
    }

    private ActorContext fan() {
        return new ActorContext(FAN, TENANT, Set.of("FAN"));
    }

    /** A snapshot as it sat in Redis: counts frozen at whatever they were when it was written. */
    private FeedItemSnapshot snapshot(String id, PostVisibility visibility,
                                      long cachedComments, long cachedReactions) {
        return new FeedItemSnapshot(id, PostType.ARTIST_POST, visibility, AUTHOR,
                "무대 뒤", "리허설 사진", List.of("https://images.example.com/stage.jpg"),
                cachedComments, cachedReactions, Instant.EPOCH);
    }

    private void cacheHit(FeedItemSnapshot... items) {
        when(feedCache.readPage(TENANT, FAN, 0, 10))
                .thenReturn(Optional.of(new PageResult<>(List.of(items), 0, 10, items.length, 1)));
    }

    private List<FeedItemView> feed() {
        return useCase.execute(fan(), 0, 10).content();
    }

    private double fallbackCount() {
        return meterRegistry.counter("community_feed_live_counts_fallback_total").count();
    }

    // ------------------------------------------------------------------
    // AC-2 — hit: the live value wins over the cached one
    // ------------------------------------------------------------------

    @Test
    @DisplayName("🔴 AC-2: 캐시에 reactionCount 0 이 구워져 있어도 지금 1 이면 피드는 1 을 낸다 (35차 창 재현)")
    void cacheHit_servesLiveCounts_notTheBakedOnes() {
        cacheHit(snapshot("p1", PostVisibility.PUBLIC, 0L, 0L));
        when(commentRepository.countsByPostIds(List.of("p1"), TENANT)).thenReturn(Map.of("p1", 2L));
        when(reactionRepository.countsByPostIds(List.of("p1"), TENANT)).thenReturn(Map.of("p1", 1L));

        FeedItemView item = feed().get(0);

        assertThat(item.reactionCount()).isEqualTo(1L);
        assertThat(item.commentCount()).isEqualTo(2L);
        // The page itself still came from the cache — this is an overlay, not a rebuild.
        verifyNoInteractions(postRepository);
        verify(feedCache, never()).cachePage(anyString(), anyString(), anyInt(), anyInt(), any());
        assertThat(fallbackCount()).isZero();
    }

    @Test
    @DisplayName("AC-2 대조: 수치가 줄어도(반응 취소) 캐시 값이 아니라 지금 값이다")
    void cacheHit_liveCountsCanAlsoGoDown() {
        cacheHit(snapshot("p1", PostVisibility.PUBLIC, 4L, 12L));
        when(commentRepository.countsByPostIds(List.of("p1"), TENANT)).thenReturn(Map.of("p1", 3L));
        when(reactionRepository.countsByPostIds(List.of("p1"), TENANT)).thenReturn(Map.of("p1", 11L));

        FeedItemView item = feed().get(0);

        assertThat(item.commentCount()).isEqualTo(3L);
        assertThat(item.reactionCount()).isEqualTo(11L);
    }

    @Test
    @DisplayName("Edge: 캐시 이후 삭제된 글은 집계 맵에 없다 → 0 (음수·예외 아님)")
    void cacheHit_postGoneSinceCaching_readsZero() {
        cacheHit(snapshot("p1", PostVisibility.PUBLIC, 5L, 7L));
        when(commentRepository.countsByPostIds(List.of("p1"), TENANT)).thenReturn(Map.of());
        when(reactionRepository.countsByPostIds(List.of("p1"), TENANT)).thenReturn(Map.of());

        FeedItemView item = feed().get(0);

        assertThat(item.commentCount()).isZero();
        assertThat(item.reactionCount()).isZero();
    }

    @Test
    @DisplayName("Edge: 잠긴 항목도 수치는 잠금과 무관하게 지금 값이다 (계약은 title/body/mediaRefs 만 가린다)")
    void lockedItem_stillCarriesLiveCounts() {
        cacheHit(snapshot("p1", PostVisibility.MEMBERS_ONLY, 0L, 0L));
        when(commentRepository.countsByPostIds(List.of("p1"), TENANT)).thenReturn(Map.of("p1", 1L));
        when(reactionRepository.countsByPostIds(List.of("p1"), TENANT)).thenReturn(Map.of("p1", 9L));
        when(membershipChecker.hasAccess(FAN, PostVisibility.MEMBERS_ONLY.name(), TENANT))
                .thenReturn(false);

        FeedItemView item = feed().get(0);

        assertThat(item.locked()).isTrue();
        assertThat(item.title()).isNull();
        assertThat(item.reactionCount()).isEqualTo(9L);
        assertThat(item.commentCount()).isEqualTo(1L);
    }

    // ------------------------------------------------------------------
    // AC-4 — cost boundary
    // ------------------------------------------------------------------

    @Test
    @DisplayName("AC-4: 한 페이지에 글이 여럿이어도 저장소마다 정확히 1회, 페이지 전체 id 로")
    void cacheHit_oneGroupedQueryPerRepository_perPage() {
        cacheHit(
                snapshot("p1", PostVisibility.PUBLIC, 0L, 0L),
                snapshot("p2", PostVisibility.PUBLIC, 0L, 0L),
                snapshot("p3", PostVisibility.PUBLIC, 0L, 0L));
        when(commentRepository.countsByPostIds(List.of("p1", "p2", "p3"), TENANT))
                .thenReturn(Map.of("p1", 1L, "p3", 3L));
        when(reactionRepository.countsByPostIds(List.of("p1", "p2", "p3"), TENANT))
                .thenReturn(Map.of("p2", 20L));

        List<FeedItemView> items = feed();

        verify(commentRepository, times(1)).countsByPostIds(List.of("p1", "p2", "p3"), TENANT);
        verify(reactionRepository, times(1)).countsByPostIds(List.of("p1", "p2", "p3"), TENANT);
        assertThat(items).extracting(FeedItemView::commentCount).containsExactly(1L, 0L, 3L);
        assertThat(items).extracting(FeedItemView::reactionCount).containsExactly(0L, 20L, 0L);
    }

    @Test
    @DisplayName("AC-4: 빈 페이지 히트는 집계 질의를 하지 않는다")
    void emptyCachedPage_runsNoCountQuery() {
        cacheHit();

        List<FeedItemView> items = feed();

        assertThat(items).isEmpty();
        verifyNoInteractions(postRepository, commentRepository, reactionRepository);
    }

    // ------------------------------------------------------------------
    // AC-3 — miss path: counts read once, not twice
    // ------------------------------------------------------------------

    @Test
    @DisplayName("AC-3: 미스 경로는 페이지를 만들며 읽은 수치를 그대로 쓴다 — 저장소마다 1회 (4회 아님)")
    void cacheMiss_readsCountsOnce_andServesThem() {
        when(feedCache.readPage(TENANT, FAN, 0, 10)).thenReturn(Optional.empty());
        Post post = Post.createDraft("p1", TENANT, AUTHOR, PostType.ARTIST_POST,
                PostVisibility.PUBLIC, "무대 뒤", "리허설", null);
        post.publish(ActorType.AUTHOR);
        when(postRepository.findFeedForFan(FAN, TENANT, 0, 10))
                .thenReturn(new PageResult<>(List.of(post), 0, 10, 1, 1));
        when(commentRepository.countsByPostIds(List.of("p1"), TENANT)).thenReturn(Map.of("p1", 2L));
        when(reactionRepository.countsByPostIds(List.of("p1"), TENANT)).thenReturn(Map.of("p1", 1L));

        FeedItemView item = feed().get(0);

        assertThat(item.commentCount()).isEqualTo(2L);
        assertThat(item.reactionCount()).isEqualTo(1L);
        verify(commentRepository, times(1)).countsByPostIds(List.of("p1"), TENANT);
        verify(reactionRepository, times(1)).countsByPostIds(List.of("p1"), TENANT);
        verify(feedCache).cachePage(anyString(), anyString(), anyInt(), anyInt(), any());
    }

    // ------------------------------------------------------------------
    // AC-5 — the count query fails
    // ------------------------------------------------------------------

    @Test
    @DisplayName("AC-5: 히트에서 집계가 DB 오류로 실패하면 200 + 캐시의 수치로 폴백, 카운터 +1")
    void cacheHit_countQueryFails_fallsBackToCachedCounts() {
        cacheHit(snapshot("p1", PostVisibility.PUBLIC, 4L, 12L));
        when(commentRepository.countsByPostIds(List.of("p1"), TENANT))
                .thenThrow(new DataAccessResourceFailureException("connection refused"));

        FeedItemView item = feed().get(0);

        assertThat(item.commentCount()).isEqualTo(4L);
        assertThat(item.reactionCount()).isEqualTo(12L);
        // The rest of the page is untouched: the fallback is about the numbers only.
        assertThat(item.title()).isEqualTo("무대 뒤");
        assertThat(item.mediaRefs()).containsExactly("https://images.example.com/stage.jpg");
        assertThat(fallbackCount()).isEqualTo(1.0);
        verifyNoInteractions(postRepository);
        verify(feedCache, never()).cachePage(anyString(), anyString(), anyInt(), anyInt(), any());
    }

    @Test
    @DisplayName("AC-5: 트랜잭션조차 못 열면(풀 고갈·DB 다운) 같은 폴백")
    void cacheHit_cannotOpenTransaction_fallsBackToCachedCounts() {
        cacheHit(snapshot("p1", PostVisibility.PUBLIC, 4L, 12L));
        when(commentRepository.countsByPostIds(List.of("p1"), TENANT)).thenReturn(Map.of("p1", 5L));
        when(reactionRepository.countsByPostIds(List.of("p1"), TENANT))
                .thenThrow(new CannotCreateTransactionException("pool exhausted"));

        FeedItemView item = feed().get(0);

        // All-or-nothing: half-live counts would be a third state nobody reasons about.
        assertThat(item.commentCount()).isEqualTo(4L);
        assertThat(item.reactionCount()).isEqualTo(12L);
        assertThat(fallbackCount()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("AC-5 대조: DB 오류가 아닌 예외(버그)는 삼키지 않는다")
    void cacheHit_nonDatabaseFailure_propagates() {
        cacheHit(snapshot("p1", PostVisibility.PUBLIC, 4L, 12L));
        when(commentRepository.countsByPostIds(List.of("p1"), TENANT))
                .thenThrow(new IllegalStateException("bug"));

        assertThatThrownBy(this::feed).isInstanceOf(IllegalStateException.class);
        assertThat(fallbackCount()).isZero();
    }

    @Test
    @DisplayName("AC-5: 미스 경로의 질의 실패는 기존대로 전파한다 — 폴백할 낡은 값이 없고, 캐시에도 안 쓴다")
    void cacheMiss_queryFails_propagatesAsBefore() {
        when(feedCache.readPage(TENANT, FAN, 0, 10)).thenReturn(Optional.empty());
        Post post = Post.createDraft("p1", TENANT, AUTHOR, PostType.ARTIST_POST,
                PostVisibility.PUBLIC, "t", "b", null);
        post.publish(ActorType.AUTHOR);
        when(postRepository.findFeedForFan(FAN, TENANT, 0, 10))
                .thenReturn(new PageResult<>(List.of(post), 0, 10, 1, 1));
        when(commentRepository.countsByPostIds(List.of("p1"), TENANT))
                .thenThrow(new DataAccessResourceFailureException("connection refused"));

        assertThatThrownBy(this::feed).isInstanceOf(DataAccessResourceFailureException.class);
        verify(feedCache, never()).cachePage(anyString(), anyString(), anyInt(), anyInt(), any());
        assertThat(fallbackCount()).isZero();
    }

    // ------------------------------------------------------------------
    // The fallback is only real at runtime if no transaction surrounds it
    // ------------------------------------------------------------------

    @Test
    @DisplayName("🔴 AC-5 배선: execute 는 @Transactional 이 아니다 — 감싸면 잡은 실패가 커밋에서 500 이 된다")
    void execute_isNotTransactional_readsAre() throws NoSuchMethodException {
        // Hibernate marks the transaction rollback-only when a query fails; a surrounding
        // transaction would then end in UnexpectedRollbackException at commit, turning the
        // fallback above back into a 500. A mock cannot show that, so the boundary is pinned here.
        assertThat(GetFeedUseCase.class.isAnnotationPresent(Transactional.class)).isFalse();
        assertThat(GetFeedUseCase.class
                .getMethod("execute", ActorContext.class, int.class, int.class)
                .isAnnotationPresent(Transactional.class)).isFalse();

        Transactional overlay = FeedReader.class
                .getMethod("liveCounts", List.class, String.class).getAnnotation(Transactional.class);
        Transactional build = FeedReader.class
                .getMethod("buildPage", String.class, String.class, int.class, int.class)
                .getAnnotation(Transactional.class);
        assertThat(overlay).isNotNull();
        assertThat(overlay.readOnly()).isTrue();
        assertThat(build).isNotNull();
        assertThat(build.readOnly()).isTrue();
    }
}
