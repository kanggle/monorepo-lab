package com.example.fanplatform.membership.presentation.controller;

import com.example.fanplatform.membership.application.ActorContext;
import com.example.fanplatform.membership.application.CancelMembershipUseCase;
import com.example.fanplatform.membership.application.GetMembershipUseCase;
import com.example.fanplatform.membership.application.ListMembershipsUseCase;
import com.example.fanplatform.membership.application.RenewCommand;
import com.example.fanplatform.membership.application.RenewMembershipUseCase;
import com.example.fanplatform.membership.application.SubscribeCommand;
import com.example.fanplatform.membership.application.SubscribeUseCase;
import com.example.fanplatform.membership.application.exception.MembershipTierInvalidException;
import com.example.fanplatform.membership.domain.membership.MembershipTier;
import com.example.fanplatform.membership.presentation.dto.ApiEnvelope;
import com.example.fanplatform.membership.presentation.security.CurrentActor;
import com.example.fanplatform.membership.presentation.dto.CancelRequest;
import com.example.fanplatform.membership.presentation.dto.MembershipListResponse;
import com.example.fanplatform.membership.presentation.dto.MembershipResponse;
import com.example.fanplatform.membership.presentation.dto.RenewRequest;
import com.example.fanplatform.membership.presentation.dto.SubscribeRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public membership endpoints (subscribe / cancel / list / detail) under
 * {@code /api/fan/memberships}. The fan-platform gateway maps
 * {@code /api/v1/memberships/**} → {@code /api/fan/memberships/**}.
 */
@RestController
@RequestMapping("/api/fan/memberships")
@RequiredArgsConstructor
public class MembershipController {

    private final SubscribeUseCase subscribeUseCase;
    private final RenewMembershipUseCase renewMembershipUseCase;
    private final CancelMembershipUseCase cancelMembershipUseCase;
    private final ListMembershipsUseCase listMembershipsUseCase;
    private final GetMembershipUseCase getMembershipUseCase;

    @PostMapping
    public ResponseEntity<ApiEnvelope<MembershipResponse>> subscribe(
            @CurrentActor ActorContext actor,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody SubscribeRequest req) {
        MembershipTier tier = parseTier(req.tier());
        SubscribeCommand cmd = new SubscribeCommand(
                actor, tier, req.planMonths(), req.paymentId(), idempotencyKey);
        MembershipResponse body = MembershipResponse.from(subscribeUseCase.execute(cmd));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiEnvelope.of(body));
    }

    @PostMapping("/{id}/renew")
    public ResponseEntity<ApiEnvelope<MembershipResponse>> renew(
            @CurrentActor ActorContext actor,
            @PathVariable String id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody RenewRequest req) {
        RenewCommand cmd = new RenewCommand(
                actor, id, req.planMonths(), req.paymentId(), idempotencyKey);
        MembershipResponse body = MembershipResponse.from(renewMembershipUseCase.execute(cmd));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiEnvelope.of(body));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiEnvelope<MembershipResponse>> cancel(
            @CurrentActor ActorContext actor,
            @PathVariable String id,
            @Valid @RequestBody(required = false) CancelRequest req) {
        String reason = req == null ? null : req.reason();
        MembershipResponse body = MembershipResponse.from(
                cancelMembershipUseCase.execute(id, actor, reason));
        return ResponseEntity.ok(ApiEnvelope.of(body));
    }

    @GetMapping
    public ResponseEntity<ApiEnvelope<MembershipListResponse>> list(@CurrentActor ActorContext actor) {
        MembershipListResponse body = MembershipListResponse.from(
                listMembershipsUseCase.execute(actor));
        return ResponseEntity.ok(ApiEnvelope.of(body));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiEnvelope<MembershipResponse>> get(@CurrentActor ActorContext actor,
                                                               @PathVariable String id) {
        MembershipResponse body = MembershipResponse.from(
                getMembershipUseCase.execute(id, actor));
        return ResponseEntity.ok(ApiEnvelope.of(body));
    }

    private static MembershipTier parseTier(String raw) {
        try {
            return MembershipTier.valueOf(raw);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new MembershipTierInvalidException(raw);
        }
    }
}
