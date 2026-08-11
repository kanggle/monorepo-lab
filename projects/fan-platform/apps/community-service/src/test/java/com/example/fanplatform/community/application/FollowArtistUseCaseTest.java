package com.example.fanplatform.community.application;

import com.example.fanplatform.community.application.exception.AlreadyFollowingException;
import com.example.fanplatform.community.application.exception.SelfFollowForbiddenException;
import com.example.fanplatform.community.application.exception.UnknownArtistAccountException;
import com.example.fanplatform.community.domain.follow.ArtistAccountChecker;
import com.example.fanplatform.community.domain.follow.Follow;
import com.example.fanplatform.community.domain.follow.FollowRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class FollowArtistUseCaseTest {

    private static final String TENANT = "fan-platform";

    @Mock FollowRepository followRepository;

    /**
     * TASK-FAN-BE-045 AC-6. The use case now takes
     * {@code (FollowRepository, ArtistAccountChecker)} — before this port existed
     * the follow target was stored verbatim, so the feed join
     * ({@code posts.author_account_id ⋈ follows.artist_account_id}) held only by
     * coincidence.
     */
    @Mock ArtistAccountChecker artistAccountChecker;

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
        when(artistAccountChecker.isArtistAccount("artist-1", TENANT)).thenReturn(true);
        when(followRepository.exists("fan-1", "artist-1", TENANT)).thenReturn(true);
        ActorContext actor = new ActorContext("fan-1", TENANT, Set.of("FAN"));
        assertThatThrownBy(() -> useCase.execute("artist-1", actor))
                .isInstanceOf(AlreadyFollowingException.class);
    }

    @Test
    @DisplayName("신규 팔로우 → 저장 후 결과 반환")
    void newFollowSucceeds() {
        when(artistAccountChecker.isArtistAccount("artist-1", TENANT)).thenReturn(true);
        when(followRepository.exists("fan-1", "artist-1", TENANT)).thenReturn(false);
        when(followRepository.save(any(Follow.class))).thenAnswer(inv -> inv.getArgument(0));

        ActorContext actor = new ActorContext("fan-1", TENANT, Set.of("FAN"));
        FollowArtistUseCase.FollowResult result = useCase.execute("artist-1", actor);
        assertThat(result.fanAccountId()).isEqualTo("fan-1");
        assertThat(result.artistAccountId()).isEqualTo("artist-1");
        assertThat(result.tenantId()).isEqualTo(TENANT);
    }

    // ─── TASK-FAN-BE-045 AC-6 — follow-target validation ──────────────────────

    /**
     * The negative half of AC-6. Asserting only the thrown exception would leave a
     * "throws AND still writes" implementation green, so the write path is pinned
     * separately: {@code save} must never be reached, and neither must the
     * duplicate-check read (the checker gates before both).
     */
    @Test
    @DisplayName("checker 가 false → UnknownArtistAccountException 이고 followRepository.save 는 절대 호출되지 않는다")
    void unverifiedArtistAccount_rejectedAndNeverPersisted() {
        when(artistAccountChecker.isArtistAccount("not-an-artist", TENANT)).thenReturn(false);
        ActorContext actor = new ActorContext("fan-1", TENANT, Set.of("FAN"));

        assertThatThrownBy(() -> useCase.execute("not-an-artist", actor))
                .isInstanceOf(UnknownArtistAccountException.class)
                .hasMessageContaining("not-an-artist");

        verify(followRepository, never()).save(any(Follow.class));
        verifyNoInteractions(followRepository);
    }

    /**
     * The positive half of AC-6: a confirmed target is persisted with exactly the
     * id that was checked. Captures the saved aggregate rather than trusting the
     * returned record — the row is what the feed join reads.
     */
    @Test
    @DisplayName("checker 가 true → 확인된 그 id 로 follows 행이 저장된다")
    void confirmedArtistAccount_persistsTheCheckedId() {
        when(artistAccountChecker.isArtistAccount("artist-7", TENANT)).thenReturn(true);
        when(followRepository.exists("fan-2", "artist-7", TENANT)).thenReturn(false);
        when(followRepository.save(any(Follow.class))).thenAnswer(inv -> inv.getArgument(0));

        ActorContext actor = new ActorContext("fan-2", TENANT, Set.of("FAN"));
        FollowArtistUseCase.FollowResult result = useCase.execute("artist-7", actor);

        ArgumentCaptor<Follow> saved = ArgumentCaptor.forClass(Follow.class);
        verify(followRepository).save(saved.capture());
        assertThat(saved.getValue().getFanAccountId()).isEqualTo("fan-2");
        assertThat(saved.getValue().getArtistAccountId())
                .as("the persisted target must be the id the checker confirmed")
                .isEqualTo("artist-7");
        assertThat(saved.getValue().getTenantId()).isEqualTo(TENANT);
        assertThat(result.artistAccountId()).isEqualTo("artist-7");
    }

    /**
     * Ordering pin: the self-follow guard must fire BEFORE the remote check. If the
     * order ever flips, every self-follow attempt becomes a network call to
     * artist-service (and a fan account, correctly not being an artist, would then
     * answer 422 UNKNOWN_ARTIST_ACCOUNT instead of 422 SELF_FOLLOW_FORBIDDEN —
     * same status, different contract code).
     */
    @Test
    @DisplayName("자기 자신 팔로우는 checker 를 부르기 전에 거절된다 (checker 무호출)")
    void selfFollow_shortCircuitsBeforeCheckerIsConsulted() {
        ActorContext actor = new ActorContext("me", TENANT, Set.of("FAN"));

        assertThatThrownBy(() -> useCase.execute("me", actor))
                .isInstanceOf(SelfFollowForbiddenException.class);

        verify(artistAccountChecker, never()).isArtistAccount(anyString(), anyString());
        verifyNoInteractions(artistAccountChecker);
    }
}
