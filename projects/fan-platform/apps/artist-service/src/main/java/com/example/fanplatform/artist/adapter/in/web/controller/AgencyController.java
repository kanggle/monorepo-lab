package com.example.fanplatform.artist.adapter.in.web.controller;

import com.example.common.page.PageResult;
import com.example.fanplatform.artist.adapter.in.web.dto.request.ChangeAgencyStatusRequest;
import com.example.fanplatform.artist.adapter.in.web.dto.request.CreateAgencyRequest;
import com.example.fanplatform.artist.adapter.in.web.dto.request.LinkStoreSellerRequest;
import com.example.fanplatform.artist.adapter.in.web.dto.request.RenameAgencyRequest;
import com.example.fanplatform.artist.adapter.in.web.dto.response.ApiEnvelope;
import com.example.fanplatform.artist.adapter.in.web.dto.response.PageMeta;
import com.example.fanplatform.artist.application.ActorContext;
import com.example.fanplatform.artist.application.port.in.AgencyView;
import com.example.fanplatform.artist.application.port.in.LinkAgencyStoreSellerUseCase;
import com.example.fanplatform.artist.application.port.in.ManageAgencyUseCase;
import com.example.fanplatform.artist.domain.agency.AgencyStatus;
import com.example.security.servlet.actor.CurrentActor;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * {@code /api/agencies} — agency CRUD + store-seller link (TASK-MONO-748,
 * ADR-MONO-079 D1 · D2; contract {@code artist-api.md} § Agencies). Writes are
 * admin-tier (SecurityConfig + application service); reads any authenticated tenant
 * member.
 */
@RestController
@RequestMapping("/api/agencies")
@RequiredArgsConstructor
public class AgencyController {

    private final ManageAgencyUseCase manageUseCase;
    private final LinkAgencyStoreSellerUseCase linkUseCase;

    @PostMapping
    public ResponseEntity<ApiEnvelope<AgencyView>> create(@CurrentActor ActorContext actor,
                                                          @Valid @RequestBody CreateAgencyRequest req) {
        AgencyView view = manageUseCase.create(actor, req.name());
        return ResponseEntity.created(URI.create("/api/agencies/" + view.id()))
                .body(ApiEnvelope.of(view));
    }

    @GetMapping
    public ResponseEntity<ApiEnvelope<List<AgencyView>>> list(@CurrentActor ActorContext actor,
                                                              @RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "20") int size) {
        PageResult<AgencyView> result = manageUseCase.list(actor, page, size);
        return ResponseEntity.ok(ApiEnvelope.of(result.content(),
                PageMeta.of(result.page(), result.size(), result.totalElements(), result.totalPages())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiEnvelope<AgencyView>> getById(@CurrentActor ActorContext actor,
                                                           @PathVariable String id) {
        return ResponseEntity.ok(ApiEnvelope.of(manageUseCase.getById(actor, id)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiEnvelope<AgencyView>> rename(@CurrentActor ActorContext actor,
                                                          @PathVariable String id,
                                                          @Valid @RequestBody RenameAgencyRequest req) {
        return ResponseEntity.ok(ApiEnvelope.of(manageUseCase.rename(actor, id, req.name())));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiEnvelope<AgencyView>> changeStatus(@CurrentActor ActorContext actor,
                                                                @PathVariable String id,
                                                                @Valid @RequestBody ChangeAgencyStatusRequest req) {
        if (req.status() != AgencyStatus.ARCHIVED) {
            throw new IllegalArgumentException("status: only ARCHIVED is a valid transition target");
        }
        return ResponseEntity.ok(ApiEnvelope.of(manageUseCase.archive(actor, id)));
    }

    @PatchMapping("/{id}/store-seller")
    public ResponseEntity<ApiEnvelope<AgencyView>> linkStoreSeller(@CurrentActor ActorContext actor,
                                                                   @PathVariable String id,
                                                                   @Valid @RequestBody LinkStoreSellerRequest req) {
        return ResponseEntity.ok(ApiEnvelope.of(
                linkUseCase.linkStoreSeller(actor, id, req.storeSellerId())));
    }
}
