package com.example.fanplatform.artist.adapter.in.web.controller;

import com.example.fanplatform.artist.adapter.in.web.dto.request.AddGroupMemberRequest;
import com.example.fanplatform.artist.adapter.in.web.dto.request.CreateArtistGroupRequest;
import com.example.fanplatform.artist.adapter.in.web.dto.response.ApiEnvelope;
import com.example.fanplatform.artist.adapter.in.web.security.ActorContextResolver;
import com.example.fanplatform.artist.application.ActorContext;
import com.example.fanplatform.artist.application.port.in.AddGroupMemberUseCase;
import com.example.fanplatform.artist.application.port.in.ArtistGroupView;
import com.example.fanplatform.artist.application.port.in.CreateArtistGroupUseCase;
import com.example.fanplatform.artist.application.port.in.CreateArtistGroupUseCase.CreateArtistGroupCommand;
import com.example.fanplatform.artist.application.port.in.GetArtistGroupUseCase;
import com.example.fanplatform.artist.application.port.in.RemoveGroupMemberUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/artist-groups")
@RequiredArgsConstructor
public class ArtistGroupController {

    private final CreateArtistGroupUseCase createUseCase;
    private final GetArtistGroupUseCase getUseCase;
    private final AddGroupMemberUseCase addMemberUseCase;
    private final RemoveGroupMemberUseCase removeMemberUseCase;

    @PostMapping
    public ResponseEntity<ApiEnvelope<ArtistGroupView>> create(
            @Valid @RequestBody CreateArtistGroupRequest req) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        ArtistGroupView view = createUseCase.create(new CreateArtistGroupCommand(
                actor, req.name(), req.debutDate(), req.agency(), req.profileImageRef()));
        return ResponseEntity.created(URI.create("/api/artist-groups/" + view.id()))
                .body(ApiEnvelope.of(view));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiEnvelope<ArtistGroupView>> getById(@PathVariable String id) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        return ResponseEntity.ok(ApiEnvelope.of(getUseCase.getById(actor, id)));
    }

    @PostMapping("/{id}/members")
    public ResponseEntity<ApiEnvelope<ArtistGroupView>> addMember(
            @PathVariable String id,
            @Valid @RequestBody AddGroupMemberRequest req) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        ArtistGroupView view = addMemberUseCase.addMember(actor, id, req.artistId(), req.role());
        return ResponseEntity.ok(ApiEnvelope.of(view));
    }

    @DeleteMapping("/{id}/members/{artistId}")
    public ResponseEntity<Void> removeMember(@PathVariable String id,
                                             @PathVariable String artistId) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        removeMemberUseCase.removeMember(actor, id, artistId);
        return ResponseEntity.noContent().build();
    }
}
