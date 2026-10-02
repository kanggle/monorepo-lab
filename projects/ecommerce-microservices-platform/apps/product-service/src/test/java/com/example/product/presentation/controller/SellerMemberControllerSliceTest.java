package com.example.product.presentation.controller;

import com.example.product.TestProductServiceApplication;
import com.example.product.application.service.SellerMemberService;
import com.example.product.domain.exception.SellerInvitationAlreadyUsedException;
import com.example.product.domain.exception.SellerInvitationEmailMismatchException;
import com.example.product.domain.exception.SellerInvitationExpiredException;
import com.example.product.domain.exception.SellerInvitationNotFoundException;
import com.example.product.domain.exception.SellerMemberAccountNotEligibleException;
import com.example.product.domain.exception.SellerNotActiveException;
import com.example.product.domain.exception.SellerRoleServiceUnavailableException;
import com.example.product.domain.model.SellerInvitation;
import com.example.product.domain.model.SellerInvitationStatus;
import com.example.product.domain.model.SellerMember;
import com.example.product.domain.model.SellerMemberRole;
import com.example.product.domain.model.SellerMemberStatus;
import com.example.product.presentation.advice.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** TASK-MONO-752 — seller-member HTTP surface (product-api.md § Seller members). */
@WebMvcTest(controllers = {AdminSellerMemberController.class, SellerInvitationController.class})
@ContextConfiguration(classes = TestProductServiceApplication.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("셀러 구성원 컨트롤러 슬라이스 (TASK-MONO-752)")
class SellerMemberControllerSliceTest {

    private static final Instant T0 = Instant.parse("2026-10-03T00:00:00Z");
    private static final String PERSON = "0199de70-0000-7000-8000-00000000a752";
    private static final String ACCEPT_BODY = "{\"token\":\"tok\"}";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private SellerMemberService sellerMemberService;

    @Test
    @DisplayName("GET members — 운영자 → 200 · 구성원 + 초대(토큰 없음)")
    void members_200() throws Exception {
        SellerInvitation invitation = SellerInvitation.reconstitute("inv-1", "s-1", "member@example.com", "hash",
                SellerInvitationStatus.PENDING, T0.plusSeconds(60), "op-1", T0, null, null);
        given(sellerMemberService.list("s-1")).willReturn(new SellerMemberService.SellerMembersView(
                List.of(SellerMember.reconstitute("s-1", PERSON, SellerMemberRole.MEMBER, SellerMemberStatus.ACTIVE, T0)),
                List.of(invitation), T0.plusSeconds(120)));

        mockMvc.perform(get("/api/admin/sellers/s-1/members").header("X-User-Role", "ECOMMERCE_OPERATOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members[0].accountId").value(PERSON))
                .andExpect(jsonPath("$.members[0].role").value("MEMBER"))
                .andExpect(jsonPath("$.invitations[0].email").value("member@example.com"))
                .andExpect(jsonPath("$.invitations[0].expired").value(true))
                .andExpect(jsonPath("$.invitations[0].token").doesNotExist())
                .andExpect(jsonPath("$.invitations[0].tokenHash").doesNotExist());
    }

    @Test
    @DisplayName("운영자 아님 → 403 · 서비스 호출 없음")
    void members_notOperator_403() throws Exception {
        mockMvc.perform(get("/api/admin/sellers/s-1/members").header("X-User-Role", "CUSTOMER"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/sellers/s-1/invitations").header("X-User-Role", "CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"a@b.co\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(sellerMemberService);
    }

    @Test
    @DisplayName("POST invitations → 201 · token 은 여기서 한 번")
    void invite_201() throws Exception {
        SellerInvitation invitation = SellerInvitation.reconstitute("inv-1", "s-1", "member@example.com", "hash",
                SellerInvitationStatus.PENDING, T0.plusSeconds(60), "op-1", T0, null, null);
        given(sellerMemberService.invite("s-1", "Member@Example.com", "op-1"))
                .willReturn(new SellerMemberService.IssuedInvitation(invitation, "raw-token"));

        mockMvc.perform(post("/api/admin/sellers/s-1/invitations")
                        .header("X-User-Role", "ECOMMERCE_OPERATOR").header("X-User-Id", "op-1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"Member@Example.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invitationId").value("inv-1"))
                .andExpect(jsonPath("$.email").value("member@example.com"))
                .andExpect(jsonPath("$.token").value("raw-token"));
    }

    @Test
    @DisplayName("POST invitations — 이메일 형식 오류 → 400 · 셀러 비활성 → 409 SELLER_NOT_ACTIVE")
    void invite_validationAndInactive() throws Exception {
        mockMvc.perform(post("/api/admin/sellers/s-1/invitations").header("X-User-Role", "ECOMMERCE_OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        given(sellerMemberService.invite("s-1", "a@b.co", null)).willThrow(new SellerNotActiveException());
        mockMvc.perform(post("/api/admin/sellers/s-1/invitations").header("X-User-Role", "ECOMMERCE_OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"a@b.co\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELLER_NOT_ACTIVE"));
    }

    @Test
    @DisplayName("accept → 200 · 계정은 X-User-Id 에서만")
    void accept_200() throws Exception {
        given(sellerMemberService.accept("tok", PERSON)).willReturn(
                SellerMember.reconstitute("s-1", PERSON, SellerMemberRole.MEMBER, SellerMemberStatus.ACTIVE, T0));

        mockMvc.perform(post("/api/seller-invitations/accept").header("X-User-Id", PERSON)
                        .contentType(MediaType.APPLICATION_JSON).content(ACCEPT_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sellerId").value("s-1"))
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("accept — 로그인 없음(X-User-Id 없음) → 401 · 서비스 호출 없음")
    void accept_noUser_401() throws Exception {
        mockMvc.perform(post("/api/seller-invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON).content(ACCEPT_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        verifyNoInteractions(sellerMemberService);
    }

    @Test
    @DisplayName("accept 거절 매핑 — 404 · 409 · 410 · 403 · 409 · 409 · 503")
    void accept_refusals() throws Exception {
        Object[][] cases = {
                {new SellerInvitationNotFoundException(), 404, "SELLER_INVITATION_NOT_FOUND"},
                {new SellerInvitationAlreadyUsedException(), 409, "SELLER_INVITATION_ALREADY_USED"},
                {new SellerInvitationExpiredException(), 410, "SELLER_INVITATION_EXPIRED"},
                {new SellerInvitationEmailMismatchException(), 403, "SELLER_INVITATION_EMAIL_MISMATCH"},
                {new SellerMemberAccountNotEligibleException(), 409, "SELLER_MEMBER_ACCOUNT_NOT_ELIGIBLE"},
                {new SellerNotActiveException(), 409, "SELLER_NOT_ACTIVE"},
                {new SellerRoleServiceUnavailableException(), 503, "SERVICE_UNAVAILABLE"},
        };
        for (Object[] c : cases) {
            willThrow((RuntimeException) c[0]).given(sellerMemberService).accept(anyString(), anyString());
            mockMvc.perform(post("/api/seller-invitations/accept").header("X-User-Id", PERSON)
                            .contentType(MediaType.APPLICATION_JSON).content(ACCEPT_BODY))
                    .andExpect(status().is((Integer) c[1]))
                    .andExpect(jsonPath("$.code").value((String) c[2]));
        }
    }
}
