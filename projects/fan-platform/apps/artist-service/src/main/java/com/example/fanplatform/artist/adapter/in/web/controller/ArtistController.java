package com.example.fanplatform.artist.adapter.in.web.controller;

import com.example.fanplatform.artist.adapter.in.web.dto.request.ChangeArtistStatusRequest;
import com.example.fanplatform.artist.adapter.in.web.dto.request.RegisterArtistRequest;
import com.example.fanplatform.artist.adapter.in.web.dto.request.UpdateArtistRequest;
import com.example.fanplatform.artist.adapter.in.web.dto.response.ApiEnvelope;
import com.example.fanplatform.artist.adapter.in.web.security.CurrentActor;
import com.example.fanplatform.artist.application.ActorContext;
import com.example.fanplatform.artist.application.port.in.ArchiveArtistUseCase;
import com.example.fanplatform.artist.application.port.in.ArtistView;
import com.example.fanplatform.artist.application.port.in.GetArtistUseCase;
import com.example.fanplatform.artist.application.port.in.PublishArtistUseCase;
import com.example.fanplatform.artist.application.port.in.RegisterArtistUseCase;
import com.example.fanplatform.artist.application.port.in.RegisterArtistUseCase.RegisterArtistCommand;
import com.example.fanplatform.artist.application.port.in.UpdateArtistUseCase;
import com.example.fanplatform.artist.application.port.in.UpdateArtistUseCase.UpdateArtistCommand;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * /api/artists CRUD + status. POST/PATCH require admin (enforced both at
 * Spring Security layer via {@code SecurityConfig} and at the application
 * service layer for defense-in-depth).
 */
@RestController
@RequestMapping("/api/artists")
@RequiredArgsConstructor
public class ArtistController {

    private final RegisterArtistUseCase registerUseCase;
    private final UpdateArtistUseCase updateUseCase;
    private final PublishArtistUseCase publishUseCase;
    private final ArchiveArtistUseCase archiveUseCase;
    private final GetArtistUseCase getUseCase;

    @PostMapping
    public ResponseEntity<ApiEnvelope<ArtistView>> register(
            @CurrentActor ActorContext actor,
            @Valid @RequestBody RegisterArtistRequest req) {
        ArtistView view = registerUseCase.register(new RegisterArtistCommand(
                actor, req.artistType(), req.stageName(), req.realName(),
                req.debutDate(), req.agency(), req.bio(), req.profileImageRef()));
        return ResponseEntity.created(URI.create("/api/artists/" + view.id()))
                .body(ApiEnvelope.of(view));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiEnvelope<ArtistView>> getById(@CurrentActor ActorContext actor,
                                                           @PathVariable String id) {
        return ResponseEntity.ok(ApiEnvelope.of(getUseCase.getById(actor, id)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiEnvelope<ArtistView>> update(@CurrentActor ActorContext actor,
                                                          @PathVariable String id,
                                                          @Valid @RequestBody UpdateArtistRequest req) {
        UpdateArtistCommand cmd = new UpdateArtistCommand(
                actor, id, req.stageName(), req.realName(),
                req.debutDate(), req.agency(), req.bio(), req.profileImageRef());
        return ResponseEntity.ok(ApiEnvelope.of(updateUseCase.update(cmd)));
    }

    /**
     * State transitions: {@code DRAFT → PUBLISHED} or
     * {@code {DRAFT, PUBLISHED} → ARCHIVED}. Other targets fail validation —
     * sending {@code status: DRAFT} raises {@link IllegalArgumentException}
     * which {@code GlobalExceptionHandler} maps to a 422 {@code VALIDATION_ERROR}
     * envelope (consistent with the other 422 error responses).
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiEnvelope<ArtistView>> changeStatus(@CurrentActor ActorContext actor,
                                                                @PathVariable String id,
                                                                @Valid @RequestBody ChangeArtistStatusRequest req) {
        return switch (req.status()) {
            case PUBLISHED -> ResponseEntity.ok(ApiEnvelope.of(publishUseCase.publish(actor, id)));
            case ARCHIVED -> ResponseEntity.ok(
                    ApiEnvelope.of(archiveUseCase.archive(actor, id, req.reason())));
            case DRAFT -> throw new IllegalArgumentException(
                    "status: DRAFT is not a valid transition target");
        };
    }
}
