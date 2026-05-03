package com.example.fanplatform.community.presentation.controller;

import com.example.fanplatform.community.application.ActorContext;
import com.example.fanplatform.community.application.GetFeedUseCase;
import com.example.fanplatform.community.infrastructure.security.ActorContextResolver;
import com.example.fanplatform.community.presentation.dto.ApiEnvelope;
import com.example.fanplatform.community.presentation.dto.FeedResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/community/feed")
@RequiredArgsConstructor
public class FeedController {

    private final GetFeedUseCase getFeedUseCase;

    @GetMapping
    public ResponseEntity<ApiEnvelope<FeedResponse>> feed(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        return ResponseEntity.ok(
                ApiEnvelope.of(FeedResponse.from(getFeedUseCase.execute(actor, page, size))));
    }
}
