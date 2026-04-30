package com.example.community.presentation;

import com.example.community.application.ActorContext;
import com.example.community.application.GetFeedUseCase;
import com.example.community.infrastructure.security.ActorContextResolver;
import com.example.community.presentation.dto.FeedResponse;
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
    public ResponseEntity<FeedResponse> feed(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        return ResponseEntity.ok(FeedResponse.from(getFeedUseCase.execute(actor, page, size)));
    }
}
