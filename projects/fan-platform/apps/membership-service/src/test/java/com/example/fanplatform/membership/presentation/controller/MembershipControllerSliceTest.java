package com.example.fanplatform.membership.presentation.controller;

import com.example.fanplatform.membership.application.ActorContext;
import com.example.fanplatform.membership.application.CancelMembershipUseCase;
import com.example.fanplatform.membership.application.GetMembershipUseCase;
import com.example.fanplatform.membership.application.ListMembershipsUseCase;
import com.example.fanplatform.membership.application.MembershipView;
import com.example.fanplatform.membership.application.RenewCommand;
import com.example.fanplatform.membership.application.RenewMembershipUseCase;
import com.example.fanplatform.membership.application.SubscribeCommand;
import com.example.fanplatform.membership.application.SubscribeUseCase;
import com.example.fanplatform.membership.application.exception.MembershipNotRenewableException;
import com.example.fanplatform.membership.application.exception.PaymentDeclinedException;
import com.example.fanplatform.membership.domain.membership.MembershipTier;
import com.example.fanplatform.membership.domain.membership.status.MembershipStatus;
import com.example.fanplatform.membership.presentation.advice.GlobalExceptionHandler;
import com.example.fanplatform.membership.testsupport.JwtTestHelper;
import com.example.fanplatform.membership.testsupport.SliceTestSecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for {@link MembershipController} (envelope, validation,
 * Idempotency-Key requirement, auth, tier validity, PG decline mapping).
 */
@WebMvcTest(controllers = MembershipController.class)
@Import({SliceTestSecurityConfig.class, GlobalExceptionHandler.class})
class MembershipControllerSliceTest {

    private static final JwtTestHelper jwt;

    static {
        jwt = new JwtTestHelper();
        SliceTestSecurityConfig.useFixture(jwt);
    }

    @Autowired
    MockMvc mockMvc;

    @MockitoBean SubscribeUseCase subscribeUseCase;
    @MockitoBean RenewMembershipUseCase renewMembershipUseCase;
    @MockitoBean CancelMembershipUseCase cancelMembershipUseCase;
    @MockitoBean ListMembershipsUseCase listMembershipsUseCase;
    @MockitoBean GetMembershipUseCase getMembershipUseCase;

    private String fanBearer(String sub) {
        return "Bearer " + jwt.signFanToken(sub);
    }

    private static MembershipView view() {
        Instant now = Instant.parse("2026-06-09T00:00:00Z");
        return new MembershipView("m1", "fan-platform", "acc1",
                MembershipTier.PREMIUM, MembershipStatus.ACTIVE,
                now, now.plusSeconds(100), 1, "pgmock_x", true, now, null);
    }

    @Test
    @DisplayName("POST (no Authorization) → 401 UNAUTHORIZED")
    void subscribeWithoutAuth() throws Exception {
        mockMvc.perform(post("/api/fan/memberships")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tier\":\"PREMIUM\",\"planMonths\":1}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("POST (valid, Idempotency-Key) → 201 + envelope { data, meta }")
    void subscribe201() throws Exception {
        when(subscribeUseCase.execute(any(SubscribeCommand.class))).thenReturn(view());

        mockMvc.perform(post("/api/fan/memberships")
                        .header("Authorization", fanBearer("acc1"))
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tier\":\"PREMIUM\",\"planMonths\":1,\"paymentId\":\"tok_visa_demo\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.membershipId").value("m1"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.tier").value("PREMIUM"))
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.meta.timestamp").exists());
    }

    @Test
    @DisplayName("POST missing Idempotency-Key → 400 VALIDATION_ERROR")
    void subscribeMissingIdempotencyKey() throws Exception {
        mockMvc.perform(post("/api/fan/memberships")
                        .header("Authorization", fanBearer("acc1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tier\":\"PREMIUM\",\"planMonths\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST planMonths < 1 → 422 VALIDATION_ERROR")
    void subscribePlanMonthsTooLow() throws Exception {
        mockMvc.perform(post("/api/fan/memberships")
                        .header("Authorization", fanBearer("acc1"))
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tier\":\"PREMIUM\",\"planMonths\":0}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST unknown tier → 422 MEMBERSHIP_TIER_INVALID")
    void subscribeUnknownTier() throws Exception {
        mockMvc.perform(post("/api/fan/memberships")
                        .header("Authorization", fanBearer("acc1"))
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tier\":\"GOLD\",\"planMonths\":1}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MEMBERSHIP_TIER_INVALID"));
    }

    @Test
    @DisplayName("POST decline → 422 PAYMENT_DECLINED")
    void subscribeDeclined() throws Exception {
        when(subscribeUseCase.execute(any(SubscribeCommand.class)))
                .thenThrow(new PaymentDeclinedException());

        mockMvc.perform(post("/api/fan/memberships")
                        .header("Authorization", fanBearer("acc1"))
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tier\":\"PREMIUM\",\"planMonths\":1,\"paymentId\":\"tok_decline\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PAYMENT_DECLINED"));
    }

    @Test
    @DisplayName("POST /{id}/renew (valid, Idempotency-Key) → 201 + envelope")
    void renew201() throws Exception {
        when(renewMembershipUseCase.execute(any(RenewCommand.class))).thenReturn(view());

        mockMvc.perform(post("/api/fan/memberships/m0/renew")
                        .header("Authorization", fanBearer("acc1"))
                        .header("Idempotency-Key", "renew-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planMonths\":1,\"paymentId\":\"tok_visa_demo\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.membershipId").value("m1"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.meta.timestamp").exists());
    }

    @Test
    @DisplayName("POST /{id}/renew on a canceled membership → 422 MEMBERSHIP_NOT_RENEWABLE")
    void renewNotRenewable() throws Exception {
        when(renewMembershipUseCase.execute(any(RenewCommand.class)))
                .thenThrow(new MembershipNotRenewableException("m0"));

        mockMvc.perform(post("/api/fan/memberships/m0/renew")
                        .header("Authorization", fanBearer("acc1"))
                        .header("Idempotency-Key", "renew-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planMonths\":1}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MEMBERSHIP_NOT_RENEWABLE"));
    }

    @Test
    @DisplayName("POST /{id}/renew missing Idempotency-Key → 400 VALIDATION_ERROR")
    void renewMissingIdempotencyKey() throws Exception {
        mockMvc.perform(post("/api/fan/memberships/m0/renew")
                        .header("Authorization", fanBearer("acc1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planMonths\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("cross-tenant token (tenant_id=wms) → 403 TENANT_FORBIDDEN")
    void subscribeCrossTenant() throws Exception {
        String wmsToken = "Bearer " + jwt.signCrossTenantToken("operator-1");
        mockMvc.perform(post("/api/fan/memberships")
                        .header("Authorization", wmsToken)
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tier\":\"PREMIUM\",\"planMonths\":1}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_FORBIDDEN"));
    }

    // keep an unused reference so the imports stay meaningful for future tests
    @SuppressWarnings("unused")
    private ActorContext actor() {
        return new ActorContext("acc1", "fan-platform", java.util.Set.of("FAN"));
    }
}
