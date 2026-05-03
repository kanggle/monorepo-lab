package com.example.fanplatform.community.application;

import com.example.fanplatform.community.application.exception.MembershipRequiredException;
import com.example.fanplatform.community.application.exception.PostNotFoundException;
import com.example.fanplatform.community.domain.membership.MembershipChecker;
import com.example.fanplatform.community.domain.post.Post;
import com.example.fanplatform.community.domain.post.PostRepository;
import com.example.fanplatform.community.domain.post.PostType;
import com.example.fanplatform.community.domain.post.PostVisibility;
import com.example.fanplatform.community.domain.post.status.ActorType;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class PostAccessGuardTest {

    private static final String TENANT = "fan-platform";
    private static final String AUTHOR = "artist-1";
    private static final String OTHER = "fan-1";

    @Mock PostRepository postRepository;
    @Mock MembershipChecker membershipChecker;

    @InjectMocks PostAccessGuard guard;

    @Test
    @DisplayName("PUBLIC + PUBLISHED → 누구나 통과")
    void publicPublishedAllowsAnyone() {
        Post post = publishedPost(PostVisibility.PUBLIC);
        when(postRepository.findById("p1", TENANT)).thenReturn(Optional.of(post));

        assertThat(guard.requirePublishedAccess("p1", actor(OTHER))).isSameAs(post);
    }

    @Test
    @DisplayName("MEMBERS_ONLY + 비-멤버 → MembershipRequiredException")
    void membersOnlyNonMemberRejected() {
        Post post = publishedPost(PostVisibility.MEMBERS_ONLY);
        when(postRepository.findById("p1", TENANT)).thenReturn(Optional.of(post));
        when(membershipChecker.hasAccess(OTHER, PostVisibility.MEMBERS_ONLY.name(), TENANT))
                .thenReturn(false);

        assertThatThrownBy(() -> guard.requirePublishedAccess("p1", actor(OTHER)))
                .isInstanceOf(MembershipRequiredException.class);
    }

    @Test
    @DisplayName("MEMBERS_ONLY + 작성자 본인 → 멤버십 검사 없이 통과")
    void membersOnlyAuthorBypassesCheck() {
        Post post = publishedPost(PostVisibility.MEMBERS_ONLY);
        when(postRepository.findById("p1", TENANT)).thenReturn(Optional.of(post));

        assertThat(guard.requirePublishedAccess("p1", actor(AUTHOR))).isSameAs(post);
    }

    @Test
    @DisplayName("MEMBERS_ONLY + 멤버십 보유 → 통과")
    void membersOnlyWithMembershipPasses() {
        Post post = publishedPost(PostVisibility.MEMBERS_ONLY);
        when(postRepository.findById("p1", TENANT)).thenReturn(Optional.of(post));
        when(membershipChecker.hasAccess(OTHER, PostVisibility.MEMBERS_ONLY.name(), TENANT))
                .thenReturn(true);

        assertThat(guard.requirePublishedAccess("p1", actor(OTHER))).isSameAs(post);
    }

    @Test
    @DisplayName("PREMIUM v1 → 항상 통과 (membership-service 미존재, WARN log)")
    void premiumAlwaysPassesV1() {
        Post post = publishedPost(PostVisibility.PREMIUM);
        when(postRepository.findById("p1", TENANT)).thenReturn(Optional.of(post));

        assertThat(guard.requirePublishedAccess("p1", actor(OTHER))).isSameAs(post);
    }

    @Test
    @DisplayName("Cross-tenant 조회 → PostNotFoundException (404, 존재 누설 차단)")
    void crossTenantReturnsNotFound() {
        when(postRepository.findById("p1", TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guard.requirePublishedAccess("p1", actor(OTHER)))
                .isInstanceOf(PostNotFoundException.class);
    }

    private static ActorContext actor(String accountId) {
        return new ActorContext(accountId, TENANT, Set.of("FAN"));
    }

    private static Post publishedPost(PostVisibility visibility) {
        Post p = Post.createDraft("p1", TENANT, AUTHOR,
                PostType.ARTIST_POST, visibility, "title", "body", null);
        p.publish(ActorType.AUTHOR);
        return p;
    }
}
