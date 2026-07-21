package com.example.user.presentation.controller;

import com.example.user.application.result.AddressResult;
import com.example.user.application.service.AddressService;
import com.example.user.domain.exception.AddressLimitExceededException;
import com.example.user.domain.exception.AddressNotFoundException;
import com.example.user.domain.exception.DefaultAddressCannotBeDeletedException;
import com.example.user.presentation.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AddressController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("AddressController 슬라이스 테스트")
class AddressControllerSliceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AddressService addressService;

    private static final UUID USER_ID = UUID.randomUUID();

    @Nested
    @DisplayName("GET /api/users/me/addresses")
    class GetAddresses {

        @Test
        @DisplayName("주소 목록을 조회하면 200과 주소 배열을 반환한다")
        void getAddresses_validUserId_returns200() throws Exception {
            UUID addrId = UUID.randomUUID();
            var result = new AddressResult(addrId, "집", "홍길동", "010-1234-5678",
                    "12345", "서울시 강남구", null, true);
            given(addressService.getAddresses(USER_ID)).willReturn(List.of(result));

            mockMvc.perform(get("/api/users/me/addresses")
                            .header("X-User-Id", USER_ID.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.addresses").isArray())
                    .andExpect(jsonPath("$.addresses[0].id").value(addrId.toString()))
                    .andExpect(jsonPath("$.addresses[0].label").value("집"))
                    .andExpect(jsonPath("$.addresses[0].isDefault").value(true));
        }

        @Test
        @DisplayName("X-User-Id 헤더 없이 요청하면 401을 반환한다")
        void getAddresses_missingHeader_returns401() throws Exception {
            mockMvc.perform(get("/api/users/me/addresses"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }
    }

    @Nested
    @DisplayName("POST /api/users/me/addresses")
    class CreateAddress {

        @Test
        @DisplayName("유효한 요청으로 주소를 생성하면 201과 id를 반환한다")
        void createAddress_validRequest_returns201() throws Exception {
            UUID newId = UUID.randomUUID();
            given(addressService.createAddress(any())).willReturn(newId);

            mockMvc.perform(post("/api/users/me/addresses")
                            .header("X-User-Id", USER_ID.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "label": "집",
                                      "recipientName": "홍길동",
                                      "phone": "010-1234-5678",
                                      "zipCode": "12345",
                                      "address1": "서울시 강남구",
                                      "address2": "101호",
                                      "isDefault": false
                                    }
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(newId.toString()));
        }

        @Test
        @DisplayName("필수 필드 누락 시 400 VALIDATION_ERROR를 반환한다")
        void createAddress_missingRequiredField_returns400() throws Exception {
            mockMvc.perform(post("/api/users/me/addresses")
                            .header("X-User-Id", USER_ID.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "label": "",
                                      "recipientName": "홍길동",
                                      "phone": "010-1234-5678",
                                      "zipCode": "12345",
                                      "address1": "서울시 강남구"
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("10개 초과 시 422 ADDRESS_LIMIT_EXCEEDED를 반환한다")
        void createAddress_limitExceeded_returns422() throws Exception {
            given(addressService.createAddress(any()))
                    .willThrow(new AddressLimitExceededException("Maximum number of addresses reached (10)"));

            mockMvc.perform(post("/api/users/me/addresses")
                            .header("X-User-Id", USER_ID.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "label": "새주소",
                                      "recipientName": "홍길동",
                                      "phone": "010-1234-5678",
                                      "zipCode": "12345",
                                      "address1": "서울시 강남구"
                                    }
                                    """))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("ADDRESS_LIMIT_EXCEEDED"));
        }
    }

    @Nested
    @DisplayName("PATCH /api/users/me/addresses/{addressId}")
    class UpdateAddress {

        @Test
        @DisplayName("유효한 요청으로 주소를 수정하면 200과 id를 반환한다")
        void updateAddress_validRequest_returns200() throws Exception {
            UUID addressId = UUID.randomUUID();
            var result = new AddressResult(addressId, "새이름", "홍길동", "010-1234-5678",
                    "12345", "서울시 강남구", null, false);
            given(addressService.updateAddress(any())).willReturn(result);

            mockMvc.perform(patch("/api/users/me/addresses/{addressId}", addressId)
                            .header("X-User-Id", USER_ID.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"label\":\"새이름\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(addressId.toString()));
        }

        @Test
        @DisplayName("존재하지 않는 주소 수정 시 404 ADDRESS_NOT_FOUND를 반환한다")
        void updateAddress_nonExisting_returns404() throws Exception {
            UUID addressId = UUID.randomUUID();
            given(addressService.updateAddress(any()))
                    .willThrow(new AddressNotFoundException(addressId));

            mockMvc.perform(patch("/api/users/me/addresses/{addressId}", addressId)
                            .header("X-User-Id", USER_ID.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"label\":\"새이름\"}"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("ADDRESS_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("DELETE /api/users/me/addresses/{addressId}")
    class DeleteAddress {

        @Test
        @DisplayName("주소를 삭제하면 204를 반환한다")
        void deleteAddress_existing_returns204() throws Exception {
            UUID addressId = UUID.randomUUID();

            mockMvc.perform(delete("/api/users/me/addresses/{addressId}", addressId)
                            .header("X-User-Id", USER_ID.toString()))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("존재하지 않는 주소 삭제 시 404를 반환한다")
        void deleteAddress_nonExisting_returns404() throws Exception {
            UUID addressId = UUID.randomUUID();
            willThrow(new AddressNotFoundException(addressId))
                    .given(addressService).deleteAddress(eq(USER_ID), eq(addressId));

            mockMvc.perform(delete("/api/users/me/addresses/{addressId}", addressId)
                            .header("X-User-Id", USER_ID.toString()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("ADDRESS_NOT_FOUND"));
        }

        @Test
        @DisplayName("기본 주소 삭제 시 다른 주소가 있으면 422를 반환한다")
        void deleteAddress_defaultWithOthers_returns422() throws Exception {
            UUID addressId = UUID.randomUUID();
            willThrow(new DefaultAddressCannotBeDeletedException())
                    .given(addressService).deleteAddress(eq(USER_ID), eq(addressId));

            mockMvc.perform(delete("/api/users/me/addresses/{addressId}", addressId)
                            .header("X-User-Id", USER_ID.toString()))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("DEFAULT_ADDRESS_CANNOT_BE_DELETED"));
        }
    }
}
