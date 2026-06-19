package com.example.fanplatform.community.application;

import com.example.fanplatform.community.application.port.out.FeedCache;
import com.example.fanplatform.community.domain.comment.CommentRepository;
import com.example.fanplatform.community.domain.membership.MembershipChecker;
import com.example.fanplatform.community.domain.post.PageResult;
import com.example.fanplatform.community.domain.post.Post;
import com.example.fanplatform.community.domain.post.PostRepository;
import com.example.fanplatform.community.domain.post.PostType;
import com.example.fanplatform.community.domain.post.PostVisibility;
import com.example.fanplatform.community.domain.post.status.ActorType;
import com.example.fanplatform.community.domain.reaction.ReactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GetFeedUseCase#isLocked} — specifically the PREMIUM
 * gate introduced in TASK-FAN-BE-019 (fail-close, mirrors PostAccessGuard).
 *
 * <p>All tests exercise {@code isLocked} indirectly through
 * {@link GetFeedUseCase#execute} by asserting the {@code locked} field on the
 * returned {@link FeedItemView}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class GetFeedUseCaseTest {

    private static final String TENANT   = "fan-platform";
    private static final String AUTHOR   = "artist-1";
    private static final String FAN      = "fan-1";
    private static final String POST_ID  = "post-abc";

    @Mock PostRepository      postRepository;
    @Mock CommentRepository   commentRepository;
    @Mock ReactionRepository  reactionRepository;
    @Mock MembershipChecker   membershipChecker;
    @Mock FeedCache           feedCache;

    @InjectMocks GetFeedUseCase useCase;

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private ActorContext fanActor() {
        return new ActorContext(FAN, TENANT, Set.of("FAN"));
    }

    private ActorContext authorActor() {
        return new ActorContext(AUTHOR, TENANT, Set.of("ARTIST"));
    }

    private ActorContext operatorActor() {
        return new ActorContext("op-1", TENANT, Set.of("OPERATOR"));
    }

    private Post premiumPost() {
        Post p = Post.createDraft(POST_ID, TENANT, AUTHOR,
                PostType.ARTIST_POST, PostVisibility.PREMIUM,
                "title", "body", null);
        p.publish(ActorType.AUTHOR);
        return p;
    }

    /** Stubs all repository calls needed to return a single-post feed page. */
    private void stubFeedWithPost(Post post) {
        PageResult<Post> page = new PageResult<>(List.of(post), 0, 10, 1, 1);
        when(postRepository.findFeedForFan(FAN, TENANT, 0, 10)).thenReturn(page);
        when(commentRepository.countsByPostIds(List.of(POST_ID), TENANT)).thenReturn(Map.of());
        when(reactionRepository.countsByPostIds(List.of(POST_ID), TENANT)).thenReturn(Map.of());
        when(feedCache.readPage(TENANT, FAN, 0, 10)).thenReturn(Optional.empty());
    }

    /** Same but for actor whose accountId != FAN (author or operator). */
    private void stubFeedWithPostForActor(Post post, ActorContext actor) {
        PageResult<Post> page = new PageResult<>(List.of(post), 0, 10, 1, 1);
        when(postRepository.findFeedForFan(actor.accountId(), TENANT, 0, 10)).thenReturn(page);
        when(commentRepository.countsByPostIds(List.of(POST_ID), TENANT)).thenReturn(Map.of());
        when(reactionRepository.countsByPostIds(List.of(POST_ID), TENANT)).thenReturn(Map.of());
        when(feedCache.readPage(TENANT, actor.accountId(), 0, 10)).thenReturn(Optional.empty());
    }

    private FeedItemView firstItem(ActorContext actor) {
        return useCase.execute(actor, 0, 10).content().get(0);
    }

    // -----------------------------------------------------------------------
    // AC-3a: non-subscriber viewing a PREMIUM post → locked=true
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("AC-3a: PREMIUM post + non-subscriber fan → locked=true (fail-close)")
    void premium_nonSubscriber_isLocked() {
        Post post = premiumPost();
        stubFeedWithPost(post);
        when(membershipChecker.hasAccess(FAN, PostVisibility.PREMIUM.name(), TENANT))
                .thenReturn(false);

        FeedItemView item = firstItem(fanActor());

        assertThat(item.locked()).isTrue();
        assertThat(item.title()).isNull();
        assertThat(item.bodyPreview()).isNull();
    }

    // -----------------------------------------------------------------------
    // AC-3b: subscriber → locked=false
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("AC-3b: PREMIUM post + premium subscriber → locked=false")
    void premium_subscriber_isUnlocked() {
        Post post = premiumPost();
        stubFeedWithPost(post);
        when(membershipChecker.hasAccess(FAN, PostVisibility.PREMIUM.name(), TENANT))
                .thenReturn(true);

        FeedItemView item = firstItem(fanActor());

        assertThat(item.locked()).isFalse();
        assertThat(item.title()).isEqualTo("title");
        assertThat(item.bodyPreview()).isEqualTo("body");
    }

    // -----------------------------------------------------------------------
    // AC-3c: author → locked=false, no membershipChecker call
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("AC-3c: PREMIUM post + author → locked=false (bypasses membershipChecker)")
    void premium_author_isUnlocked_noCheckerCall() {
        Post post = premiumPost();
        stubFeedWithPostForActor(post, authorActor());

        FeedItemView item = firstItem(authorActor());

        assertThat(item.locked()).isFalse();
        // STRICT_STUBS verifies that membershipChecker.hasAccess was NOT called
        // (any unexpected interaction with membershipChecker will fail the test).
        verify(membershipChecker, never()).hasAccess(AUTHOR, PostVisibility.PREMIUM.name(), TENANT);
    }

    // -----------------------------------------------------------------------
    // AC-3c: operator → locked=false, no membershipChecker call
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("AC-3c: PREMIUM post + operator → locked=false (bypasses membershipChecker)")
    void premium_operator_isUnlocked_noCheckerCall() {
        Post post = premiumPost();
        ActorContext op = operatorActor();
        stubFeedWithPostForActor(post, op);

        FeedItemView item = firstItem(op);

        assertThat(item.locked()).isFalse();
        verify(membershipChecker, never()).hasAccess(op.accountId(),
                PostVisibility.PREMIUM.name(), TENANT);
    }

    // -----------------------------------------------------------------------
    // AC-3d: membershipChecker IS called with "PREMIUM" for non-author non-op
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("AC-3d: membershipChecker invoked with tier=PREMIUM for non-author fan")
    void premium_checkerCalledWithPremiumTier() {
        Post post = premiumPost();
        stubFeedWithPost(post);
        when(membershipChecker.hasAccess(FAN, PostVisibility.PREMIUM.name(), TENANT))
                .thenReturn(false);

        useCase.execute(fanActor(), 0, 10);

        // STRICT_STUBS verifies this stub was consumed (i.e., the call was made).
        verify(membershipChecker).hasAccess(FAN, PostVisibility.PREMIUM.name(), TENANT);
    }

    // -----------------------------------------------------------------------
    // AC-5: MEMBERS_ONLY regression guard — existing behaviour unchanged
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("AC-5: MEMBERS_ONLY + non-member → locked=true (no regression)")
    void membersOnly_nonMember_isLocked() {
        Post post = Post.createDraft(POST_ID, TENANT, AUTHOR,
                PostType.ARTIST_POST, PostVisibility.MEMBERS_ONLY,
                "title", "body", null);
        post.publish(ActorType.AUTHOR);

        stubFeedWithPost(post);
        when(membershipChecker.hasAccess(FAN, PostVisibility.MEMBERS_ONLY.name(), TENANT))
                .thenReturn(false);

        FeedItemView item = firstItem(fanActor());

        assertThat(item.locked()).isTrue();
    }

    @Test
    @DisplayName("AC-5: MEMBERS_ONLY + member → locked=false (no regression)")
    void membersOnly_member_isUnlocked() {
        Post post = Post.createDraft(POST_ID, TENANT, AUTHOR,
                PostType.ARTIST_POST, PostVisibility.MEMBERS_ONLY,
                "title", "body", null);
        post.publish(ActorType.AUTHOR);

        stubFeedWithPost(post);
        when(membershipChecker.hasAccess(FAN, PostVisibility.MEMBERS_ONLY.name(), TENANT))
                .thenReturn(true);

        FeedItemView item = firstItem(fanActor());

        assertThat(item.locked()).isFalse();
    }
}
