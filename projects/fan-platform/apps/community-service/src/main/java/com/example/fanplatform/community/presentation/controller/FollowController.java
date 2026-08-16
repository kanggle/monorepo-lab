package com.example.fanplatform.community.presentation.controller;

import com.example.fanplatform.community.application.ActorContext;
import com.example.fanplatform.community.application.FollowArtistUseCase;
import com.example.fanplatform.community.application.IsFollowingArtistUseCase;
import com.example.fanplatform.community.application.UnfollowArtistUseCase;
import com.example.security.servlet.actor.CurrentActor;
import com.example.fanplatform.community.presentation.dto.ApiEnvelope;
import com.example.fanplatform.community.presentation.dto.FollowArtistRequest;
import com.example.fanplatform.community.presentation.dto.FollowResponse;
import com.example.fanplatform.community.presentation.dto.FollowStatusResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/community/follows")
@RequiredArgsConstructor
public class FollowController {

    private final FollowArtistUseCase followArtistUseCase;
    private final UnfollowArtistUseCase unfollowArtistUseCase;
    private final IsFollowingArtistUseCase isFollowingArtistUseCase;

    @PostMapping
    public ResponseEntity<ApiEnvelope<FollowResponse>> follow(
            @CurrentActor ActorContext actor,
            @Valid @RequestBody FollowArtistRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiEnvelope.of(FollowResponse.from(
                        followArtistUseCase.execute(req.artistAccountId(), actor))));
    }

    /**
     * "Am I following this artist?" — TASK-FAN-BE-049.
     *
     * <p>🔴 Always 200, never 404, even when the answer is "no". The sibling
     * {@code DELETE} on this exact path answers 404 {@code NOT_FOLLOWING} for the
     * same underlying condition, which makes 404 the locally consistent choice and
     * the wrong one here: on a read, 404 also means "no such artist", and a UI
     * rendering a follow button off that status cannot tell an unfollowed live
     * artist from a nonexistent one. Rationale in full: {@code community-api.md}
     * § "Why this is 200 + a boolean and never 404".
     */
    @GetMapping("/{artistAccountId}")
    public ResponseEntity<ApiEnvelope<FollowStatusResponse>> isFollowing(
            @CurrentActor ActorContext actor,
            @PathVariable String artistAccountId) {
        return ResponseEntity.ok(ApiEnvelope.of(FollowStatusResponse.from(
                isFollowingArtistUseCase.execute(artistAccountId, actor))));
    }

    @DeleteMapping("/{artistAccountId}")
    public ResponseEntity<Void> unfollow(
            @CurrentActor ActorContext actor,
            @PathVariable String artistAccountId) {
        unfollowArtistUseCase.execute(artistAccountId, actor);
        return ResponseEntity.noContent().build();
    }
}
