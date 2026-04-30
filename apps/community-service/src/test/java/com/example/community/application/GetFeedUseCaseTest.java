package com.example.community.application;

import com.example.community.domain.access.AccountProfileLookup;
import com.example.community.domain.access.ContentAccessChecker;
import com.example.community.domain.comment.CommentRepository;
import com.example.community.domain.post.Post;
import com.example.community.domain.post.PostRepository;
import com.example.community.domain.post.PostType;
import com.example.community.domain.post.PostVisibility;
import com.example.community.domain.post.status.ActorType;
import com.example.community.domain.reaction.ReactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class GetFeedUseCaseTest {

    @Mock PostRepository postRepository;
    @Mock CommentRepository commentRepository;
    @Mock ReactionRepository reactionRepository;
    @Mock ContentAccessChecker contentAccessChecker;
    @Mock AccountProfileLookup accountProfileLookup;

    GetFeedUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetFeedUseCase(postRepository, commentRepository, reactionRepository, contentAccessChecker, accountProfileLookup);
    }

    @Test
    @DisplayName("구독 중인 아티스트의 공개 포스트가 피드에 반환된다")
    void execute_publicPosts_returnsFeedItems() {
        Post post = Post.createDraft("artist-1", PostType.ARTIST_POST, PostVisibility.PUBLIC, "Title", "Body", null);
        post.publish(ActorType.AUTHOR);
        ActorContext actor = new ActorContext("fan-1", Set.of("FAN"));
        List<String> postIds = List.of(post.getId());

        when(postRepository.findFeedForFan(eq("fan-1"), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(post)));
        when(commentRepository.countsByPostIds(postIds)).thenReturn(Map.of(post.getId(), 2L));
        when(reactionRepository.countsByPostIds(postIds)).thenReturn(Map.of(post.getId(), 5L));
        when(accountProfileLookup.displayNameOf("artist-1")).thenReturn("Artist Name");

        FeedPage result = useCase.execute(actor, 0, 20);

        assertThat(result.content()).hasSize(1);
        FeedItemView item = result.content().get(0);
        assertThat(item.locked()).isFalse();
        assertThat(item.title()).isEqualTo("Title");
        assertThat(item.authorDisplayName()).isEqualTo("Artist Name");
        assertThat(item.commentCount()).isEqualTo(2L);
        assertThat(item.reactionCount()).isEqualTo(5L);
    }

    @Test
    @DisplayName("빈 피드 조회 시 빈 목록이 반환된다")
    void execute_emptyFeed_returnsEmptyPage() {
        ActorContext actor = new ActorContext("fan-1", Set.of("FAN"));

        when(postRepository.findFeedForFan(eq("fan-1"), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(commentRepository.countsByPostIds(List.of())).thenReturn(Map.of());
        when(reactionRepository.countsByPostIds(List.of())).thenReturn(Map.of());

        FeedPage result = useCase.execute(actor, 0, 20);

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
    }

    @Test
    @DisplayName("멤버십 전용 포스트에 멤버십 없는 팬 접근 시 locked=true 로 제목과 본문이 숨겨진다")
    void execute_membersOnlyPost_nonMemberFan_returnsLockedItem() {
        Post post = Post.createDraft("artist-1", PostType.ARTIST_POST, PostVisibility.MEMBERS_ONLY, "Secret", "Secret Body", null);
        post.publish(ActorType.AUTHOR);
        ActorContext actor = new ActorContext("fan-1", Set.of("FAN"));
        List<String> postIds = List.of(post.getId());

        when(postRepository.findFeedForFan(eq("fan-1"), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(post)));
        when(commentRepository.countsByPostIds(postIds)).thenReturn(Map.of());
        when(reactionRepository.countsByPostIds(postIds)).thenReturn(Map.of());
        when(contentAccessChecker.check("fan-1", GetPostUseCase.REQUIRED_PLAN_LEVEL)).thenReturn(false);
        when(accountProfileLookup.displayNameOf("artist-1")).thenReturn("Artist");

        FeedPage result = useCase.execute(actor, 0, 20);

        FeedItemView item = result.content().get(0);
        assertThat(item.locked()).isTrue();
        assertThat(item.title()).isNull();
        assertThat(item.bodyPreview()).isNull();
    }

    @Test
    @DisplayName("멤버십 전용 포스트의 작성자 본인 접근 시 잠금 없이 반환된다")
    void execute_membersOnlyPost_authorAccess_returnsUnlocked() {
        Post post = Post.createDraft("fan-1", PostType.ARTIST_POST, PostVisibility.MEMBERS_ONLY, "Own Post", "Content", null);
        post.publish(ActorType.AUTHOR);
        ActorContext actor = new ActorContext("fan-1", Set.of("ARTIST"));
        List<String> postIds = List.of(post.getId());

        when(postRepository.findFeedForFan(eq("fan-1"), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(post)));
        when(commentRepository.countsByPostIds(postIds)).thenReturn(Map.of());
        when(reactionRepository.countsByPostIds(postIds)).thenReturn(Map.of());
        when(accountProfileLookup.displayNameOf("fan-1")).thenReturn("Self");

        FeedPage result = useCase.execute(actor, 0, 20);

        FeedItemView item = result.content().get(0);
        assertThat(item.locked()).isFalse();
        assertThat(item.title()).isEqualTo("Own Post");
    }
}
