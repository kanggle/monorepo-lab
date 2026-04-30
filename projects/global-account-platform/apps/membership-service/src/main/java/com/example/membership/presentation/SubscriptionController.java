package com.example.membership.presentation;

import com.example.membership.application.ActivateSubscriptionUseCase;
import com.example.membership.application.CancelSubscriptionUseCase;
import com.example.membership.application.GetMySubscriptionsUseCase;
import com.example.membership.application.command.ActivateSubscriptionCommand;
import com.example.membership.application.result.ActivateSubscriptionResult;
import com.example.membership.application.result.MySubscriptionsResult;
import com.example.membership.domain.plan.PlanLevel;
import com.example.membership.presentation.dto.ActivateSubscriptionRequest;
import com.example.membership.presentation.dto.MySubscriptionsResponse;
import com.example.membership.presentation.dto.SubscriptionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/membership/subscriptions")
public class SubscriptionController {

    private final ActivateSubscriptionUseCase activateSubscriptionUseCase;
    private final CancelSubscriptionUseCase cancelSubscriptionUseCase;
    private final GetMySubscriptionsUseCase getMySubscriptionsUseCase;

    @PostMapping
    public ResponseEntity<SubscriptionResponse> activate(
            @RequestHeader("X-Account-Id") String accountId,
            @Valid @RequestBody ActivateSubscriptionRequest request) {
        PlanLevel planLevel = PlanLevel.parse(request.planLevel());
        ActivateSubscriptionResult result = activateSubscriptionUseCase.activate(
                new ActivateSubscriptionCommand(accountId, planLevel, request.idempotencyKey()));
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(SubscriptionResponse.from(result.subscription()));
    }

    @DeleteMapping("/{subscriptionId}")
    public ResponseEntity<Void> cancel(
            @RequestHeader("X-Account-Id") String accountId,
            @PathVariable String subscriptionId) {
        cancelSubscriptionUseCase.cancel(subscriptionId, accountId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<MySubscriptionsResponse> getMine(
            @RequestHeader("X-Account-Id") String accountId) {
        MySubscriptionsResult result = getMySubscriptionsUseCase.getMine(accountId);
        return ResponseEntity.ok(MySubscriptionsResponse.from(result));
    }
}
