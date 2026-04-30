package com.example.community.presentation;

import com.example.community.application.ActorContext;
import com.example.community.application.AddCommentUseCase;
import com.example.community.infrastructure.security.ActorContextResolver;
import com.example.community.presentation.dto.AddCommentRequest;
import com.example.community.presentation.dto.CommentResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/community/posts/{postId}/comments")
@RequiredArgsConstructor
public class CommentController {

    private final AddCommentUseCase addCommentUseCase;

    @PostMapping
    public ResponseEntity<CommentResponse> add(@PathVariable String postId,
                                               @Valid @RequestBody AddCommentRequest req) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommentResponse.from(addCommentUseCase.execute(postId, req.body(), actor)));
    }
}
