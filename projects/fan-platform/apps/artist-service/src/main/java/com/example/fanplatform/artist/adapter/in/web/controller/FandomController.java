package com.example.fanplatform.artist.adapter.in.web.controller;

import com.example.fanplatform.artist.adapter.in.web.dto.request.UpdateFandomRequest;
import com.example.fanplatform.artist.adapter.in.web.dto.response.ApiEnvelope;
import com.example.fanplatform.artist.adapter.in.web.security.ActorContextResolver;
import com.example.fanplatform.artist.application.ActorContext;
import com.example.fanplatform.artist.application.port.in.FandomView;
import com.example.fanplatform.artist.application.port.in.GetFandomUseCase;
import com.example.fanplatform.artist.application.port.in.UpdateFandomUseCase;
import com.example.fanplatform.artist.application.port.in.UpdateFandomUseCase.UpdateFandomCommand;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/fandoms")
@RequiredArgsConstructor
public class FandomController {

    private final UpdateFandomUseCase updateUseCase;
    private final GetFandomUseCase getUseCase;

    @GetMapping("/{artistId}")
    public ResponseEntity<ApiEnvelope<FandomView>> get(@PathVariable String artistId) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        return ResponseEntity.ok(ApiEnvelope.of(getUseCase.getByArtistId(actor, artistId)));
    }

    /** PUT-style upsert: creates on first call (artist must be PUBLISHED) or updates. */
    @PutMapping("/{artistId}")
    public ResponseEntity<ApiEnvelope<FandomView>> upsert(@PathVariable String artistId,
                                                          @Valid @RequestBody UpdateFandomRequest req) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        FandomView view = updateUseCase.upsert(new UpdateFandomCommand(
                actor, artistId, req.fandomName(), req.colorHex(), req.foundedAt(), req.slogan()));
        return ResponseEntity.ok(ApiEnvelope.of(view));
    }
}
