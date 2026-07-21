package com.example.shipping.interfaces.rest.controller;

import com.example.shipping.TestShippingServiceApplication;
import com.example.shipping.application.command.CarrierWebhookCommand;
import com.example.shipping.application.exception.WebhookSignatureException;
import com.example.shipping.application.service.ProcessCarrierWebhookService;
import com.example.shipping.application.service.ProcessCarrierWebhookService.WebhookOutcome;
import com.example.shipping.interfaces.rest.security.CarrierWebhookVerifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CarrierWebhookController.class)
@ContextConfiguration(classes = TestShippingServiceApplication.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("CarrierWebhookController 슬라이스 테스트")
class CarrierWebhookControllerSliceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CarrierWebhookVerifier carrierWebhookVerifier;

    @MockitoBean
    private ProcessCarrierWebhookService processCarrierWebhookService;

    private static final String VALID_BODY =
            "{\"deliveryId\":\"d-1\",\"shippingId\":\"s-1\",\"status\":\"DELIVERED\"}";

    @Test
    @DisplayName("유효 서명 + 처리 성공 시 200, 서비스에 커맨드 위임")
    void validSignedDelivery_returns200_andDelegates() throws Exception {
        given(processCarrierWebhookService.ingest(any())).willReturn(WebhookOutcome.ADVANCED);

        mockMvc.perform(post("/api/shippings/carrier-webhook")
                        .header("X-Carrier-Signature", "sha256=deadbeef")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk());

        ArgumentCaptor<CarrierWebhookCommand> captor = ArgumentCaptor.forClass(CarrierWebhookCommand.class);
        verify(processCarrierWebhookService).ingest(captor.capture());
        assertThat(captor.getValue().deliveryId()).isEqualTo("d-1");
        assertThat(captor.getValue().shippingId()).isEqualTo("s-1");
        assertThat(captor.getValue().rawStatus()).isEqualTo("DELIVERED");
    }

    @Test
    @DisplayName("처리 결과가 IGNORED/DUPLICATE 여도 200 ack")
    void ignoredOutcome_stillReturns200() throws Exception {
        given(processCarrierWebhookService.ingest(any())).willReturn(WebhookOutcome.DUPLICATE);

        mockMvc.perform(post("/api/shippings/carrier-webhook")
                        .header("X-Carrier-Signature", "sha256=deadbeef")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("서명 검증 실패 시 401, 서비스 미호출")
    void badSignature_returns401_andServiceNotCalled() throws Exception {
        doThrow(new WebhookSignatureException("bad")).when(carrierWebhookVerifier).verify(any(), any());

        mockMvc.perform(post("/api/shippings/carrier-webhook")
                        .header("X-Carrier-Signature", "sha256=wrong")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized());

        verify(processCarrierWebhookService, never()).ingest(any());
    }

    @Test
    @DisplayName("서명 통과 후 본문 파싱 불가 시 400")
    void malformedBody_returns400() throws Exception {
        mockMvc.perform(post("/api/shippings/carrier-webhook")
                        .header("X-Carrier-Signature", "sha256=deadbeef")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest());

        verify(processCarrierWebhookService, never()).ingest(any());
    }

    @Test
    @DisplayName("필수 필드 누락 시 400")
    void missingRequiredField_returns400() throws Exception {
        mockMvc.perform(post("/api/shippings/carrier-webhook")
                        .header("X-Carrier-Signature", "sha256=deadbeef")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deliveryId\":\"\",\"shippingId\":\"s-1\",\"status\":\"DELIVERED\"}"))
                .andExpect(status().isBadRequest());

        verify(processCarrierWebhookService, never()).ingest(any());
    }
}
