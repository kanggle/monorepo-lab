package com.example.membership.presentation;

import com.example.membership.application.ActivateSubscriptionUseCase;
import com.example.membership.application.CancelSubscriptionUseCase;
import com.example.membership.application.GetMySubscriptionsUseCase;
import com.example.membership.application.exception.AccountNotEligibleException;
import com.example.membership.application.exception.AccountStatusUnavailableException;
import com.example.membership.application.exception.SubscriptionAlreadyActiveException;
import com.example.membership.application.exception.SubscriptionNotActiveException;
import com.example.membership.application.exception.SubscriptionNotFoundException;
import com.example.membership.application.exception.SubscriptionPermissionDeniedException;
import com.example.membership.application.result.ActivateSubscriptionResult;
import com.example.membership.application.result.MySubscriptionsResult;
import com.example.membership.application.result.SubscriptionResult;
import com.example.membership.domain.account.AccountStatus;
import com.example.membership.domain.plan.PlanLevel;
import com.example.membership.domain.subscription.status.SubscriptionStatus;
import com.example.membership.presentation.exception.GlobalExceptionHandler;
import com.example.membership.support.MembershipJwtTestFixture;
import com.example.membership.support.SliceTestSecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SubscriptionController.class)
@Import({SliceTestSecurityConfig.class, GlobalExceptionHandler.class})
@DisplayName("SubscriptionController slice tests")
class SubscriptionControllerTest {

    private static final MembershipJwtTestFixture JWT;

    static {
        JWT = new MembershipJwtTestFixture();
        SliceTestSecurityConfig.useFixture(JWT);
    }

    private static String bearer(String accountId) {
        return "Bearer " + JWT.token(accountId, List.of("FAN"));
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ActivateSubscriptionUseCase activateSubscriptionUseCase;

    @MockitoBean
    private CancelSubscriptionUseCase cancelSubscriptionUseCase;

    @MockitoBean
    private GetMySubscriptionsUseCase getMySubscriptionsUseCase;

    private SubscriptionResult sampleSub() {
        return new SubscriptionResult(
                "sub-1", "acc-1", PlanLevel.FAN_CLUB, SubscriptionStatus.ACTIVE,
                LocalDateTime.of(2026, 4, 13, 12, 0),
                LocalDateTime.of(2026, 5, 13, 12, 0),
                null);
    }

    @Test
    @DisplayName("POST /api/membership/subscriptions returns 201 on fresh activation")
    void activate_fresh_returns201() throws Exception {
        given(activateSubscriptionUseCase.activate(any()))
                .willReturn(ActivateSubscriptionResult.created(sampleSub()));

        mockMvc.perform(post("/api/membership/subscriptions")
                        .header("Authorization", bearer("acc-1"))
                        .header("X-Account-Id", "acc-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planLevel\":\"FAN_CLUB\",\"idempotencyKey\":\"k-1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subscriptionId").value("sub-1"))
                .andExpect(jsonPath("$.planLevel").value("FAN_CLUB"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("POST /api/membership/subscriptions returns 200 on idempotent replay")
    void activate_replay_returns200() throws Exception {
        given(activateSubscriptionUseCase.activate(any()))
                .willReturn(ActivateSubscriptionResult.replayed(sampleSub()));

        mockMvc.perform(post("/api/membership/subscriptions")
                        .header("Authorization", bearer("acc-1"))
                        .header("X-Account-Id", "acc-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planLevel\":\"FAN_CLUB\",\"idempotencyKey\":\"k-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscriptionId").value("sub-1"));
    }

    @Test
    @DisplayName("POST subscription: LOCKED account returns 409 ACCOUNT_NOT_ELIGIBLE")
    void activate_lockedAccount_returns409() throws Exception {
        given(activateSubscriptionUseCase.activate(any()))
                .willThrow(new AccountNotEligibleException(AccountStatus.LOCKED));

        mockMvc.perform(post("/api/membership/subscriptions")
                        .header("Authorization", bearer("acc-1"))
                        .header("X-Account-Id", "acc-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planLevel\":\"FAN_CLUB\",\"idempotencyKey\":\"k-1\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_ELIGIBLE"));
    }

    @Test
    @DisplayName("POST subscription: duplicate active returns 409 SUBSCRIPTION_ALREADY_ACTIVE")
    void activate_duplicate_returns409() throws Exception {
        given(activateSubscriptionUseCase.activate(any()))
                .willThrow(new SubscriptionAlreadyActiveException("already active"));

        mockMvc.perform(post("/api/membership/subscriptions")
                        .header("Authorization", bearer("acc-1"))
                        .header("X-Account-Id", "acc-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planLevel\":\"FAN_CLUB\",\"idempotencyKey\":\"k-1\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SUBSCRIPTION_ALREADY_ACTIVE"));
    }

    @Test
    @DisplayName("POST subscription: account-service 503 returns 503 ACCOUNT_STATUS_UNAVAILABLE")
    void activate_accountUnavailable_returns503() throws Exception {
        given(activateSubscriptionUseCase.activate(any()))
                .willThrow(new AccountStatusUnavailableException("cb open"));

        mockMvc.perform(post("/api/membership/subscriptions")
                        .header("Authorization", bearer("acc-1"))
                        .header("X-Account-Id", "acc-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planLevel\":\"FAN_CLUB\",\"idempotencyKey\":\"k-1\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ACCOUNT_STATUS_UNAVAILABLE"));
    }

    @Test
    @DisplayName("POST subscription: invalid planLevel returns 400")
    void activate_invalidPlanLevel_returns400() throws Exception {
        mockMvc.perform(post("/api/membership/subscriptions")
                        .header("Authorization", bearer("acc-1"))
                        .header("X-Account-Id", "acc-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planLevel\":\"VIP\",\"idempotencyKey\":\"k-1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("DELETE subscription returns 204")
    void cancel_returns204() throws Exception {
        Mockito.doNothing().when(cancelSubscriptionUseCase).cancel("sub-1", "acc-1");

        mockMvc.perform(delete("/api/membership/subscriptions/sub-1")
                        .header("Authorization", bearer("acc-1"))
                        .header("X-Account-Id", "acc-1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE subscription by non-owner returns 403")
    void cancel_nonOwner_returns403() throws Exception {
        Mockito.doThrow(new SubscriptionPermissionDeniedException("nope"))
                .when(cancelSubscriptionUseCase).cancel("sub-1", "acc-1");

        mockMvc.perform(delete("/api/membership/subscriptions/sub-1")
                        .header("Authorization", bearer("acc-1"))
                        .header("X-Account-Id", "acc-1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    @DisplayName("DELETE subscription not found returns 404")
    void cancel_notFound_returns404() throws Exception {
        Mockito.doThrow(new SubscriptionNotFoundException("sub-1"))
                .when(cancelSubscriptionUseCase).cancel("sub-1", "acc-1");

        mockMvc.perform(delete("/api/membership/subscriptions/sub-1")
                        .header("Authorization", bearer("acc-1"))
                        .header("X-Account-Id", "acc-1"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE subscription already cancelled returns 409")
    void cancel_notActive_returns409() throws Exception {
        Mockito.doThrow(new SubscriptionNotActiveException("sub-1"))
                .when(cancelSubscriptionUseCase).cancel("sub-1", "acc-1");

        mockMvc.perform(delete("/api/membership/subscriptions/sub-1")
                        .header("Authorization", bearer("acc-1"))
                        .header("X-Account-Id", "acc-1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SUBSCRIPTION_NOT_ACTIVE"));
    }

    @Test
    @DisplayName("GET /me returns 200 with active plan level")
    void getMine_returns200() throws Exception {
        given(getMySubscriptionsUseCase.getMine("acc-1"))
                .willReturn(new MySubscriptionsResult(
                        "acc-1",
                        List.of(sampleSub()),
                        PlanLevel.FAN_CLUB));

        mockMvc.perform(get("/api/membership/subscriptions/me")
                        .header("Authorization", bearer("acc-1"))
                        .header("X-Account-Id", "acc-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("acc-1"))
                .andExpect(jsonPath("$.activePlanLevel").value("FAN_CLUB"))
                .andExpect(jsonPath("$.subscriptions[0].subscriptionId").value("sub-1"));
    }
}
