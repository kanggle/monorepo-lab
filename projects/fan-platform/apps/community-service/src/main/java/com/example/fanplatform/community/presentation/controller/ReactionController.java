package com.example.fanplatform.community.presentation.controller;

import com.example.fanplatform.community.application.ActorContext;
import com.example.fanplatform.community.application.AddReactionUseCase;
import com.example.fanplatform.community.application.RemoveReactionUseCase;
import com.example.fanplatform.community.infrastructure.security.ActorContextResolver;
import com.example.fanplatform.community.presentation.dto.AddReactionRequest;
import com.example.fanplatform.community.presentation.dto.ApiEnvelope;
import com.example.fanplatform.community.presentation.dto.ReactionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/community/posts/{postId}/reactions")
@RequiredArgsConstructor
public class ReactionController {

    private final AddReactionUseCase addReactionUseCase;
    private final RemoveReactionUseCase removeReactionUseCase;

    @PutMapping
    public ResponseEntity<ApiEnvelope<ReactionResponse>> upsert(
            @PathVariable String postId,
            @Valid @RequestBody AddReactionRequest req) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        AddReactionUseCase.ReactionResult r = addReactionUseCase.execute(
                postId, req.reactionType(), actor);
        return ResponseEntity.ok(ApiEnvelope.of(ReactionResponse.from(r)));
    }

    @DeleteMapping
    public ResponseEntity<Void> remove(@PathVariable String postId) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        removeReactionUseCase.execute(postId, actor);
        return ResponseEntity.noContent().build();
    }
}
