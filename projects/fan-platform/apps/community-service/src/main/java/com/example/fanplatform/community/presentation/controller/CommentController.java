package com.example.fanplatform.community.presentation.controller;

import com.example.fanplatform.community.application.ActorContext;
import com.example.fanplatform.community.application.AddCommentUseCase;
import com.example.fanplatform.community.application.DeleteCommentUseCase;
import com.example.fanplatform.community.infrastructure.security.CurrentActor;
import com.example.fanplatform.community.presentation.dto.AddCommentRequest;
import com.example.fanplatform.community.presentation.dto.ApiEnvelope;
import com.example.fanplatform.community.presentation.dto.CommentResponse;
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
@RequestMapping("/api/community/posts/{postId}/comments")
@RequiredArgsConstructor
public class CommentController {

    private final AddCommentUseCase addCommentUseCase;
    private final DeleteCommentUseCase deleteCommentUseCase;

    @PostMapping
    public ResponseEntity<ApiEnvelope<CommentResponse>> add(
            @CurrentActor ActorContext actor,
            @PathVariable String postId,
            @Valid @RequestBody AddCommentRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiEnvelope.of(CommentResponse.from(
                        addCommentUseCase.execute(postId, req.body(), actor))));
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> delete(@CurrentActor ActorContext actor,
                                       @PathVariable String postId,
                                       @PathVariable String commentId) {
        deleteCommentUseCase.execute(postId, commentId, actor);
        return ResponseEntity.noContent().build();
    }
}
