package com.example.notification.adapter.in.rest;

import com.example.common.page.PageResult;
import com.example.notification.application.result.TemplateResult;
import com.example.notification.application.port.in.ManageTemplateUseCase;
import com.example.notification.domain.exception.TemplateAlreadyExistsException;
import com.example.notification.domain.exception.TemplateNotFoundException;
import com.example.notification.domain.model.NotificationChannel;
import com.example.notification.domain.model.NotificationTemplate;
import com.example.notification.domain.model.TemplateType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TemplateController.class)
@DisplayName("TemplateController 슬라이스 테스트")
class TemplateControllerSliceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ManageTemplateUseCase templateService;

    @Test
    @DisplayName("GET /api/notifications/templates - 템플릿 목록 조회 성공")
    void getTemplates_returns200() throws Exception {
        NotificationTemplate template = NotificationTemplate.create(
                TemplateType.ORDER_PLACED, NotificationChannel.EMAIL,
                "Subject", "Body");

        PageResult<NotificationTemplate> pageResult = new PageResult<>(List.of(template), 0, 20, 1L, 1);
        given(templateService.getTemplates(any()))
                .willReturn(pageResult);

        mockMvc.perform(get("/api/notifications/templates")
                        .header("X-User-Role", "ECOMMERCE_OPERATOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].type").value("ORDER_PLACED"))
                .andExpect(jsonPath("$.content[0].channel").value("EMAIL"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/notifications/templates - 멀티 role(콤마조인) 헤더에 ECOMMERCE_OPERATOR 포함 시 200")
    void getTemplates_multiRoleContainingAdmin_returns200() throws Exception {
        // The gateway (JwtHeaderEnrichmentFilter, ADR-MONO-035 4b-2a) joins the
        // roles claim with ","; a multi-domain operator presents e.g.
        // "ECOMMERCE_OPERATOR,WMS_OPERATOR". ECOMMERCE_OPERATOR membership must be admitted (not exact-equals).
        PageResult<NotificationTemplate> pageResult = new PageResult<>(List.of(), 0, 20, 0L, 0);
        given(templateService.getTemplates(any()))
                .willReturn(pageResult);

        mockMvc.perform(get("/api/notifications/templates")
                        .header("X-User-Role", "ECOMMERCE_OPERATOR,WMS_OPERATOR"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/notifications/templates/{id} - 템플릿 상세 조회 성공 (body 포함)")
    void getTemplate_returns200WithBody() throws Exception {
        NotificationTemplate template = NotificationTemplate.create(
                TemplateType.ORDER_PLACED, NotificationChannel.EMAIL,
                "Order {{orderId}}", "Your order {{orderId}} placed.");
        given(templateService.getTemplate(template.getTemplateId()))
                .willReturn(template);

        mockMvc.perform(get("/api/notifications/templates/" + template.getTemplateId())
                        .header("X-User-Role", "ECOMMERCE_OPERATOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templateId").value(template.getTemplateId()))
                .andExpect(jsonPath("$.type").value("ORDER_PLACED"))
                .andExpect(jsonPath("$.channel").value("EMAIL"))
                .andExpect(jsonPath("$.subject").value("Order {{orderId}}"))
                .andExpect(jsonPath("$.body").value("Your order {{orderId}} placed."));
    }

    @Test
    @DisplayName("GET /api/notifications/templates/{id} - 존재하지/다른 테넌트 템플릿이면 404")
    void getTemplate_notFound_returns404() throws Exception {
        given(templateService.getTemplate("template-999"))
                .willThrow(new TemplateNotFoundException("template-999"));

        mockMvc.perform(get("/api/notifications/templates/template-999")
                        .header("X-User-Role", "ECOMMERCE_OPERATOR"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TEMPLATE_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/notifications/templates/{id} - 비관리자 역할(USER)로 요청 시 403 반환")
    void getTemplate_nonAdminRole_returns403() throws Exception {
        mockMvc.perform(get("/api/notifications/templates/template-1")
                        .header("X-User-Role", "USER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("POST /api/notifications/templates - 템플릿 생성 성공")
    void createTemplate_returns201() throws Exception {
        given(templateService.createTemplate(any()))
                .willReturn(new TemplateResult("template-1"));

        mockMvc.perform(post("/api/notifications/templates")
                        .header("X-User-Role", "ECOMMERCE_OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"ORDER_PLACED\",\"channel\":\"EMAIL\"," +
                                "\"subject\":\"Order confirmed\",\"body\":\"Your order has been placed.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.templateId").value("template-1"));
    }

    @Test
    @DisplayName("POST /api/notifications/templates - 깨진 JSON 본문 시 400 / VALIDATION_ERROR")
    void createTemplate_malformedBody_returns400() throws Exception {
        mockMvc.perform(post("/api/notifications/templates")
                        .header("X-User-Role", "ECOMMERCE_OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    @Test
    @DisplayName("POST /api/notifications/templates - 중복 템플릿 생성 시 409")
    void createTemplate_duplicate_returns409() throws Exception {
        given(templateService.createTemplate(any()))
                .willThrow(new TemplateAlreadyExistsException("ORDER_PLACED", "EMAIL"));

        mockMvc.perform(post("/api/notifications/templates")
                        .header("X-User-Role", "ECOMMERCE_OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"ORDER_PLACED\",\"channel\":\"EMAIL\"," +
                                "\"subject\":\"Subject\",\"body\":\"Body\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TEMPLATE_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("PUT /api/notifications/templates/{id} - 템플릿 수정 성공")
    void updateTemplate_returns200() throws Exception {
        given(templateService.updateTemplate(any()))
                .willReturn(new TemplateResult("template-1"));

        mockMvc.perform(put("/api/notifications/templates/template-1")
                        .header("X-User-Role", "ECOMMERCE_OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"Updated subject\",\"body\":\"Updated body\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templateId").value("template-1"));
    }

    @Test
    @DisplayName("PUT /api/notifications/templates/{id} - 존재하지 않는 템플릿 수정 시 404")
    void updateTemplate_notFound_returns404() throws Exception {
        given(templateService.updateTemplate(any()))
                .willThrow(new TemplateNotFoundException("template-999"));

        mockMvc.perform(put("/api/notifications/templates/template-999")
                        .header("X-User-Role", "ECOMMERCE_OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"Subject\",\"body\":\"Body\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TEMPLATE_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /api/notifications/templates - 필수 필드 누락 시 400")
    void createTemplate_missingField_returns400() throws Exception {
        mockMvc.perform(post("/api/notifications/templates")
                        .header("X-User-Role", "ECOMMERCE_OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"ORDER_PLACED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("GET /api/notifications/templates - X-User-Role 헤더 없는 요청 시 403 반환")
    void getTemplates_missingRoleHeader_returns403() throws Exception {
        mockMvc.perform(get("/api/notifications/templates"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("POST /api/notifications/templates - X-User-Role 헤더 없는 요청 시 403 반환")
    void createTemplate_missingRoleHeader_returns403() throws Exception {
        mockMvc.perform(post("/api/notifications/templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"ORDER_PLACED\",\"channel\":\"EMAIL\"," +
                                "\"subject\":\"Subject\",\"body\":\"Body\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("GET /api/notifications/templates - 비관리자 역할(USER)로 요청 시 403 반환")
    void getTemplates_nonAdminRole_returns403() throws Exception {
        mockMvc.perform(get("/api/notifications/templates")
                        .header("X-User-Role", "USER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("POST /api/notifications/templates - 비관리자 역할(USER)로 요청 시 403 반환")
    void createTemplate_nonAdminRole_returns403() throws Exception {
        mockMvc.perform(post("/api/notifications/templates")
                        .header("X-User-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"ORDER_PLACED\",\"channel\":\"EMAIL\"," +
                                "\"subject\":\"Subject\",\"body\":\"Body\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("PUT /api/notifications/templates/{id} - 비관리자 역할(USER)로 요청 시 403 반환")
    void updateTemplate_nonAdminRole_returns403() throws Exception {
        mockMvc.perform(put("/api/notifications/templates/template-1")
                        .header("X-User-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"Subject\",\"body\":\"Body\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }
}
