package com.example.fanplatform.community.application;

import com.example.fanplatform.community.application.exception.AlreadyFollowingException;
import com.example.fanplatform.community.application.exception.SelfFollowForbiddenException;
import com.example.fanplatform.community.domain.follow.Follow;
import com.example.fanplatform.community.domain.follow.FollowRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class FollowArtistUseCaseTest {

    private static final String TENANT = "fan-platform";

    @Mock FollowRepository followRepository;

    @InjectMocks FollowArtistUseCase useCase;

    @Test
    @DisplayName("자기 자신 팔로우 → SelfFollowForbiddenException")
    void selfFollowRejected() {
        ActorContext actor = new ActorContext("me", TENANT, Set.of("FAN"));
        assertThatThrownBy(() -> useCase.execute("me", actor))
                .isInstanceOf(SelfFollowForbiddenException.class);
    }

    @Test
    @DisplayName("이미 팔로우 중 → AlreadyFollowingException")
    void duplicateFollowRejected() {
        when(followRepository.exists("fan-1", "artist-1", TENANT)).thenReturn(true);
        ActorContext actor = new ActorContext("fan-1", TENANT, Set.of("FAN"));
        assertThatThrownBy(() -> useCase.execute("artist-1", actor))
                .isInstanceOf(AlreadyFollowingException.class);
    }

    @Test
    @DisplayName("신규 팔로우 → 저장 후 결과 반환")
    void newFollowSucceeds() {
        when(followRepository.exists("fan-1", "artist-1", TENANT)).thenReturn(false);
        when(followRepository.save(any(Follow.class))).thenAnswer(inv -> inv.getArgument(0));

        ActorContext actor = new ActorContext("fan-1", TENANT, Set.of("FAN"));
        FollowArtistUseCase.FollowResult result = useCase.execute("artist-1", actor);
        assertThat(result.fanAccountId()).isEqualTo("fan-1");
        assertThat(result.artistAccountId()).isEqualTo("artist-1");
        assertThat(result.tenantId()).isEqualTo(TENANT);
    }
}
