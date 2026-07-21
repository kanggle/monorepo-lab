package com.example.fanplatform.community.presentation.controller;

import com.example.fanplatform.community.application.ActorContext;
import com.example.fanplatform.community.application.ChangePostStatusUseCase;
import com.example.fanplatform.community.application.DeletePostUseCase;
import com.example.fanplatform.community.application.GetPostUseCase;
import com.example.fanplatform.community.application.PublishPostCommand;
import com.example.fanplatform.community.application.PublishPostUseCase;
import com.example.fanplatform.community.application.UpdatePostUseCase;
import com.example.fanplatform.community.infrastructure.security.CurrentActor;
import com.example.fanplatform.community.presentation.dto.ApiEnvelope;
import com.example.fanplatform.community.presentation.dto.ChangePostStatusRequest;
import com.example.fanplatform.community.presentation.dto.PostResponse;
import com.example.fanplatform.community.presentation.dto.PublishPostRequest;
import com.example.fanplatform.community.presentation.dto.UpdatePostRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/community/posts")
@RequiredArgsConstructor
public class PostController {

    private final PublishPostUseCase publishPostUseCase;
    private final GetPostUseCase getPostUseCase;
    private final UpdatePostUseCase updatePostUseCase;
    private final ChangePostStatusUseCase changePostStatusUseCase;
    private final DeletePostUseCase deletePostUseCase;

    @PostMapping
    public ResponseEntity<ApiEnvelope<PostResponse>> publish(
            @CurrentActor ActorContext actor,
            @Valid @RequestBody PublishPostRequest req) {
        PublishPostCommand cmd = new PublishPostCommand(
                actor, req.postType(), req.visibility(), req.title(), req.body(), req.mediaRefs()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiEnvelope.of(PostResponse.from(publishPostUseCase.execute(cmd))));
    }

    @GetMapping("/{postId}")
    public ResponseEntity<ApiEnvelope<PostResponse>> get(
            @CurrentActor ActorContext actor,
            @PathVariable String postId) {
        return ResponseEntity.ok(
                ApiEnvelope.of(PostResponse.from(getPostUseCase.execute(postId, actor))));
    }

    @PatchMapping("/{postId}")
    public ResponseEntity<ApiEnvelope<PostResponse>> update(
            @CurrentActor ActorContext actor,
            @PathVariable String postId,
            @Valid @RequestBody UpdatePostRequest req) {
        return ResponseEntity.ok(
                ApiEnvelope.of(PostResponse.from(updatePostUseCase.execute(
                        postId, actor, req.title(), req.body(), req.mediaRefs()))));
    }

    @PatchMapping("/{postId}/status")
    public ResponseEntity<Void> changeStatus(
            @CurrentActor ActorContext actor,
            @PathVariable String postId,
            @Valid @RequestBody ChangePostStatusRequest req) {
        changePostStatusUseCase.execute(postId, req.status(), actor, req.reason());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> delete(
            @CurrentActor ActorContext actor,
            @PathVariable String postId) {
        deletePostUseCase.execute(postId, actor, null);
        return ResponseEntity.noContent().build();
    }
}
