package com.example.fanplatform.community.application;

import com.example.fanplatform.community.domain.comment.CommentRepository;
import com.example.fanplatform.community.domain.post.Post;
import com.example.fanplatform.community.domain.post.PostRepository;
import com.example.fanplatform.community.domain.post.PostType;
import com.example.fanplatform.community.domain.post.PostVisibility;
import com.example.fanplatform.community.domain.post.status.ActorType;
import com.example.fanplatform.community.domain.reaction.Reaction;
import com.example.fanplatform.community.domain.reaction.ReactionRepository;
import com.example.fanplatform.community.domain.reaction.ReactionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link GetPostUseCase} — TASK-FAN-BE-051 § AC-3 / AC-4 at the use-case layer.
 *
 * <p>AC-3's three cells (no reaction / LIKE / LIKE→LOVE) must differ, not just
 * each individually pass — a hardcoded {@code null} would pass the first cell
 * alone. AC-4 (isolation) is asserted here as the call contract: {@link
 * ReactionRepository#find} must be invoked with the CALLER's own
 * {@code accountId}, never the post author's or a third party's — the
 * authoritative end-to-end isolation proof (seeding another fan's row and
 * reading as a different fan) lives in
 * {@code ReactionMyStatusReadIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class GetPostUseCaseTest {

    private static final String TENANT = "fan-platform";

    @Mock PostRepository postRepository;
    @Mock CommentRepository commentRepository;
    @Mock ReactionRepository reactionRepository;
    @Mock PostAccessGuard postAccessGuard;

    @InjectMocks GetPostUseCase useCase;

    private static Post published() {
        Post p = Post.createDraft("p1", TENANT, "author-1",
                PostType.ARTIST_POST, PostVisibility.PUBLIC, "t", "b", null);
        p.publish(ActorType.AUTHOR);
        return p;
    }

    private ActorContext viewer(String accountId) {
        return new ActorContext(accountId, TENANT, Set.of("FAN"));
    }

    @Test
    @DisplayName("AC-3 cell 1 — 반응 안 함 → myReaction = null")
    void noReaction_myReactionIsNull() {
        Post post = published();
        when(postRepository.findById("p1", TENANT)).thenReturn(Optional.of(post));
        when(commentRepository.countByPostId("p1", TENANT)).thenReturn(0L);
        when(reactionRepository.countByPostId("p1", TENANT)).thenReturn(0L);
        when(reactionRepository.find("p1", "fan-1", TENANT)).thenReturn(Optional.empty());

        PostView view = useCase.execute("p1", viewer("fan-1"));

        assertThat(view.myReaction()).isNull();
    }

    @Test
    @DisplayName("AC-3 cell 2 — LIKE 반응 중 → myReaction = LIKE")
    void likeReaction_myReactionIsLike() {
        Post post = published();
        when(postRepository.findById("p1", TENANT)).thenReturn(Optional.of(post));
        when(commentRepository.countByPostId("p1", TENANT)).thenReturn(0L);
        when(reactionRepository.countByPostId("p1", TENANT)).thenReturn(1L);
        when(reactionRepository.find("p1", "fan-1", TENANT))
                .thenReturn(Optional.of(Reaction.create("p1", "fan-1", TENANT, ReactionType.LIKE)));

        PostView view = useCase.execute("p1", viewer("fan-1"));

        assertThat(view.myReaction()).isEqualTo(ReactionType.LIKE);
    }

    @Test
    @DisplayName("AC-3 cell 3 — LIKE → LOVE 로 바꾼 뒤 → myReaction = LOVE (이전 값이 남지 않음)")
    void changedReaction_myReactionIsTheNewType() {
        Post post = published();
        when(postRepository.findById("p1", TENANT)).thenReturn(Optional.of(post));
        when(commentRepository.countByPostId("p1", TENANT)).thenReturn(0L);
        when(reactionRepository.countByPostId("p1", TENANT)).thenReturn(1L);
        // The upsert mutates the row in place (Reaction.changeType) — the repository
        // find() on the GET path sees only the CURRENT type, never the prior one.
        Reaction row = Reaction.create("p1", "fan-1", TENANT, ReactionType.LIKE);
        row.changeType(ReactionType.LOVE);
        when(reactionRepository.find("p1", "fan-1", TENANT)).thenReturn(Optional.of(row));

        PostView view = useCase.execute("p1", viewer("fan-1"));

        assertThat(view.myReaction()).isEqualTo(ReactionType.LOVE);
    }

    @Test
    @DisplayName("🔴 AC-3 대조군 — 세 칸이 서로 달라야 한다(상수 null 구현이면 실패)")
    void theThreeCellsDiffer() {
        Post post = published();
        when(postRepository.findById("p1", TENANT)).thenReturn(Optional.of(post));
        when(commentRepository.countByPostId("p1", TENANT)).thenReturn(0L);
        when(reactionRepository.countByPostId("p1", TENANT)).thenReturn(0L, 1L, 1L);
        when(reactionRepository.find("p1", "fan-1", TENANT))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(Reaction.create("p1", "fan-1", TENANT, ReactionType.LIKE)))
                .thenReturn(Optional.of(Reaction.create("p1", "fan-1", TENANT, ReactionType.LOVE)));

        ReactionType none = useCase.execute("p1", viewer("fan-1")).myReaction();
        ReactionType like = useCase.execute("p1", viewer("fan-1")).myReaction();
        ReactionType love = useCase.execute("p1", viewer("fan-1")).myReaction();

        assertThat(none).isNull();
        assertThat(like).isEqualTo(ReactionType.LIKE);
        assertThat(love).isEqualTo(ReactionType.LOVE);
        assertThat(Set.of(String.valueOf(none), String.valueOf(like), String.valueOf(love)))
                .as("the three cells must be pairwise distinct — a constant implementation "
                        + "would collapse this set to size 1")
                .hasSize(3);
    }

    @Test
    @DisplayName("AC-4 (호출 계약) — reactionRepository.find 는 호출자 자신의 accountId 로만 질의한다")
    void queriesOnlyTheCallersOwnAccountId() {
        Post post = published();
        when(postRepository.findById("p1", TENANT)).thenReturn(Optional.of(post));
        when(commentRepository.countByPostId("p1", TENANT)).thenReturn(0L);
        when(reactionRepository.countByPostId("p1", TENANT)).thenReturn(1L);
        when(reactionRepository.find("p1", "viewer-fan", TENANT)).thenReturn(Optional.empty());

        useCase.execute("p1", viewer("viewer-fan"));

        // Never the post author's id, never any other fan's — exactly the caller's own.
        verify(reactionRepository).find(eq("p1"), eq("viewer-fan"), eq(TENANT));
    }
}
