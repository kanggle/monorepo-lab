package com.example.membership.presentation.internal;

import com.example.membership.application.CheckContentAccessUseCase;
import com.example.membership.application.result.AccessCheckResult;
import com.example.membership.domain.plan.PlanLevel;
import com.example.membership.presentation.dto.AccessCheckResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/membership")
public class ContentAccessController {

    private final CheckContentAccessUseCase checkContentAccessUseCase;

    @GetMapping("/access")
    public ResponseEntity<AccessCheckResponse> check(
            @RequestParam("accountId") String accountId,
            @RequestParam("requiredPlanLevel") String requiredPlanLevel) {
        PlanLevel required = PlanLevel.parse(requiredPlanLevel);
        AccessCheckResult result = checkContentAccessUseCase.check(accountId, required);
        return ResponseEntity.ok(AccessCheckResponse.from(result));
    }
}
