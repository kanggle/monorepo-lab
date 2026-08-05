package com.example.fanplatform.community.application;

import com.example.common.page.PageResult;
import com.example.fanplatform.community.domain.comment.CommentRepository;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-FAN-FE-016 — {@link GetMyPostsUseCase}.
 *
 * <p>The interesting assertions are about scoping: this must ask for the CALLER's posts in
 * the CALLER's tenant, and it must not run the visibility gate. Getting the first wrong leaks
 * someone else's drafts; getting the second wrong hides a fan's own gated post from them,
 * which is the exact "where did my post go" the ticket exists to end.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class GetMyPostsUseCaseTest {

    private static final String TENANT = "fan-platform";
    private static final String FAN = "fan-1";

    @Mock PostRepository postRepository;
    @Mock CommentRepository commentRepository;
    @Mock ReactionRepository reactionRepository;

    @InjectMocks GetMyPostsUseCase useCase;

    private ActorContext fan() {
        return new ActorContext(FAN, TENANT, Set.of("FAN"));
    }

    private Post post(String id, PostVisibility visibility) {
        Post p = Post.createDraft(id, TENANT, FAN, PostType.FAN_POST, visibility,
                "제목", "본문", null);
        p.publish(ActorType.AUTHOR);
        return p;
    }

    private void stub(List<Post> posts, int page, int size) {
        when(postRepository.findByAuthor(FAN, TENANT, page, size))
                .thenReturn(new PageResult<>(posts, page, size, posts.size(), 1));
        List<String> ids = posts.stream().map(Post::getId).toList();
        when(commentRepository.countsByPostIds(ids, TENANT)).thenReturn(Map.of());
        when(reactionRepository.countsByPostIds(ids, TENANT)).thenReturn(Map.of());
    }

    @Test
    @DisplayName("호출자 자신의 글을, 자신의 테넌트에서 조회한다")
    void queriesTheCallersOwnPostsInTheirTenant() {
        stub(List.of(post("p1", PostVisibility.PUBLIC)), 0, 20);

        PageResult<PostView> result = useCase.execute(fan(), 0, 20);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).postId()).isEqualTo("p1");
        verify(postRepository).findByAuthor(eq(FAN), eq(TENANT), eq(0), eq(20));
    }

    @Test
    @DisplayName("🔴 자기 글은 가시성 티어와 무관하게 제목·본문이 그대로 온다")
    void ownGatedPostIsNotRedacted() {
        stub(List.of(post("p1", PostVisibility.PREMIUM)), 0, 20);

        PostView view = useCase.execute(fan(), 0, 20).content().get(0);

        // 저자는 언제나 자기 글에 접근할 수 있다(GetPostUseCase 도 owns() 로 먼저 빠진다).
        // 여기서 게이트를 돌리면 할 수 있는 일은 둘뿐이다: 아무것도 아니거나, 쓴 사람에게서
        // 그 글을 숨기거나.
        assertThat(view.title()).isEqualTo("제목");
        assertThat(view.body()).isEqualTo("본문");
        assertThat(view.visibility()).isEqualTo(PostVisibility.PREMIUM);
    }

    @Test
    @DisplayName("size 는 50 으로, page 는 0 으로 클램프된다 (피드와 같은 경계)")
    void clampsPagingArguments() {
        stub(List.of(), 0, 50);

        useCase.execute(fan(), -3, 999);

        verify(postRepository).findByAuthor(eq(FAN), eq(TENANT), eq(0), eq(50));
    }

    @Test
    @DisplayName("size 가 0 이면 1 로 올린다")
    void clampsZeroSizeToOne() {
        stub(List.of(), 0, 1);

        useCase.execute(fan(), 0, 0);

        verify(postRepository).findByAuthor(eq(FAN), eq(TENANT), eq(0), eq(1));
    }

    @Test
    @DisplayName("댓글·반응 수는 배치 조회 결과에서 채워지고, 없으면 0 이다")
    void fillsCountsFromBatchLookup() {
        Post p1 = post("p1", PostVisibility.PUBLIC);
        Post p2 = post("p2", PostVisibility.PUBLIC);
        when(postRepository.findByAuthor(FAN, TENANT, 0, 20))
                .thenReturn(new PageResult<>(List.of(p1, p2), 0, 20, 2, 1));
        when(commentRepository.countsByPostIds(List.of("p1", "p2"), TENANT))
                .thenReturn(Map.of("p1", 4L));
        when(reactionRepository.countsByPostIds(List.of("p1", "p2"), TENANT))
                .thenReturn(Map.of("p2", 7L));

        List<PostView> views = useCase.execute(fan(), 0, 20).content();

        assertThat(views.get(0).commentCount()).isEqualTo(4L);
        assertThat(views.get(0).reactionCount()).isZero();
        assertThat(views.get(1).commentCount()).isZero();
        assertThat(views.get(1).reactionCount()).isEqualTo(7L);
    }
}
