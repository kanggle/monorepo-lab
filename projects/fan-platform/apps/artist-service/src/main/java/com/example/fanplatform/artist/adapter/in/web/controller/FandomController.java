package com.example.fanplatform.artist.adapter.in.web.controller;

import com.example.fanplatform.artist.adapter.in.web.dto.request.CreateFandomRequest;
import com.example.fanplatform.artist.adapter.in.web.dto.request.UpdateFandomRequest;
import com.example.fanplatform.artist.adapter.in.web.dto.response.ApiEnvelope;
import com.example.fanplatform.artist.adapter.in.web.security.ActorContextResolver;
import com.example.fanplatform.artist.application.ActorContext;
import com.example.fanplatform.artist.application.port.in.CreateFandomUseCase;
import com.example.fanplatform.artist.application.port.in.CreateFandomUseCase.CreateFandomCommand;
import com.example.fanplatform.artist.application.port.in.FandomView;
import com.example.fanplatform.artist.application.port.in.GetFandomUseCase;
import com.example.fanplatform.artist.application.port.in.UpdateFandomUseCase;
import com.example.fanplatform.artist.application.port.in.UpdateFandomUseCase.UpdateFandomCommand;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/fandoms")
@RequiredArgsConstructor
public class FandomController {

    private final CreateFandomUseCase createUseCase;
    private final UpdateFandomUseCase updateUseCase;
    private final GetFandomUseCase getUseCase;

    @GetMapping("/{artistId}")
    public ResponseEntity<ApiEnvelope<FandomView>> get(@PathVariable String artistId) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        return ResponseEntity.ok(ApiEnvelope.of(getUseCase.getByArtistId(actor, artistId)));
    }

    /** Create a fandom for the artist. 422 FANDOM_ALREADY_EXISTS if one already exists. */
    @PostMapping("/{artistId}")
    public ResponseEntity<ApiEnvelope<FandomView>> create(@PathVariable String artistId,
                                                          @Valid @RequestBody CreateFandomRequest req) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        FandomView view = createUseCase.create(new CreateFandomCommand(
                actor, artistId, req.fandomName(), req.colorHex(), req.foundedAt(), req.slogan()));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiEnvelope.of(view));
    }

    /** Update an existing fandom. 404 FANDOM_NOT_FOUND if none exists for the artist. */
    @PatchMapping("/{artistId}")
    public ResponseEntity<ApiEnvelope<FandomView>> update(@PathVariable String artistId,
                                                          @Valid @RequestBody UpdateFandomRequest req) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        FandomView view = updateUseCase.update(new UpdateFandomCommand(
                actor, artistId, req.fandomName(), req.colorHex(), req.foundedAt(), req.slogan()));
        return ResponseEntity.ok(ApiEnvelope.of(view));
    }
}
