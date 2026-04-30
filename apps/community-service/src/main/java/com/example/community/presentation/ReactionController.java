package com.example.community.presentation;

import com.example.community.application.ActorContext;
import com.example.community.application.AddReactionUseCase;
import com.example.community.infrastructure.security.ActorContextResolver;
import com.example.community.presentation.dto.AddReactionRequest;
import com.example.community.presentation.dto.ReactionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/community/posts/{postId}/reactions")
@RequiredArgsConstructor
public class ReactionController {

    private final AddReactionUseCase addReactionUseCase;

    @PostMapping
    public ResponseEntity<ReactionResponse> add(@PathVariable String postId,
                                                @Valid @RequestBody AddReactionRequest req) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        AddReactionUseCase.ReactionResult r = addReactionUseCase.execute(postId, req.emojiCode(), actor);
        return ResponseEntity.ok(new ReactionResponse(r.postId(), r.emojiCode(), r.totalReactions()));
    }
}
