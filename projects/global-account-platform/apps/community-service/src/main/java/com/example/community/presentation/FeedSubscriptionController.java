package com.example.community.presentation;

import com.example.community.application.ActorContext;
import com.example.community.application.FollowArtistUseCase;
import com.example.community.infrastructure.security.ActorContextResolver;
import com.example.community.presentation.dto.FollowResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/community/subscriptions/artists")
@RequiredArgsConstructor
public class FeedSubscriptionController {

    private final FollowArtistUseCase followArtistUseCase;

    @PostMapping("/{artistAccountId}")
    public ResponseEntity<FollowResponse> follow(@PathVariable String artistAccountId) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        FollowArtistUseCase.FollowResult r = followArtistUseCase.follow(actor.accountId(), artistAccountId);
        return ResponseEntity.ok(new FollowResponse(r.fanAccountId(), r.artistAccountId(), r.followedAt()));
    }

    @DeleteMapping("/{artistAccountId}")
    public ResponseEntity<Void> unfollow(@PathVariable String artistAccountId) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        followArtistUseCase.unfollow(actor.accountId(), artistAccountId);
        return ResponseEntity.noContent().build();
    }
}
