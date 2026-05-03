package com.example.fanplatform.community.presentation.controller;

import com.example.fanplatform.community.application.ActorContext;
import com.example.fanplatform.community.application.FollowArtistUseCase;
import com.example.fanplatform.community.application.UnfollowArtistUseCase;
import com.example.fanplatform.community.infrastructure.security.ActorContextResolver;
import com.example.fanplatform.community.presentation.dto.ApiEnvelope;
import com.example.fanplatform.community.presentation.dto.FollowArtistRequest;
import com.example.fanplatform.community.presentation.dto.FollowResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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

    @PostMapping
    public ResponseEntity<ApiEnvelope<FollowResponse>> follow(
            @Valid @RequestBody FollowArtistRequest req) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiEnvelope.of(FollowResponse.from(
                        followArtistUseCase.execute(req.artistAccountId(), actor))));
    }

    @DeleteMapping("/{artistAccountId}")
    public ResponseEntity<Void> unfollow(@PathVariable String artistAccountId) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        unfollowArtistUseCase.execute(artistAccountId, actor);
        return ResponseEntity.noContent().build();
    }
}
