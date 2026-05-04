package com.example.web.exception;

import com.example.web.dto.ErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CommonGlobalExceptionHandler 단위 테스트")
class CommonGlobalExceptionHandlerTest {

    private final CommonGlobalExceptionHandler handler = new CommonGlobalExceptionHandler() {};

    @Mock
    private MethodArgumentNotValidException mockValidationEx;
    @Mock
    private BindingResult mockBindingResult;
    @Mock
    private MissingRequestHeaderException mockMissingHeaderEx;

    @Test
    @DisplayName("MethodArgumentNotValidException — 400, 첫 번째 필드 오류 메시지 반환")
    void handleValidation_returns400WithFieldError() {
        FieldError fieldError = new FieldError("request", "email", "must not be blank");
        when(mockValidationEx.getBindingResult()).thenReturn(mockBindingResult);
        when(mockBindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

        ResponseEntity<ErrorResponse> response = handler.handleValidation(mockValidationEx);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().message()).isEqualTo("email: must not be blank");
    }

    @Test
    @DisplayName("MethodArgumentNotValidException — 필드 오류 없을 시 기본 메시지 반환")
    void handleValidation_noFieldErrors_returnsDefaultMessage() {
        when(mockValidationEx.getBindingResult()).thenReturn(mockBindingResult);
        when(mockBindingResult.getFieldErrors()).thenReturn(List.of());

        ResponseEntity<ErrorResponse> response = handler.handleValidation(mockValidationEx);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("Validation failed");
    }

    @Test
    @DisplayName("HttpMessageNotReadableException — 400, VALIDATION_ERROR, 고정 메시지")
    void handleMalformedRequest_returns400() {
        HttpMessageNotReadableException e = new HttpMessageNotReadableException("malformed JSON");

        ResponseEntity<ErrorResponse> response = handler.handleMalformedRequest(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().message()).isEqualTo("Malformed request body");
    }

    @Test
    @DisplayName("MissingRequestHeaderException — 400, VALIDATION_ERROR, 헤더명 포함")
    void handleMissingHeader_returns400WithHeaderName() {
        when(mockMissingHeaderEx.getHeaderName()).thenReturn("X-Account-Id");

        ResponseEntity<ErrorResponse> response = handler.handleMissingHeader(mockMissingHeaderEx);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().message()).isEqualTo("Missing required header: X-Account-Id");
    }

    @Test
    @DisplayName("MissingServletRequestParameterException — 400, VALIDATION_ERROR, 파라미터명 포함")
    void handleMissingParam_returns400WithParamName() {
        MissingServletRequestParameterException e =
                new MissingServletRequestParameterException("page", "Integer");

        ResponseEntity<ErrorResponse> response = handler.handleMissingParam(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().message()).isEqualTo("Missing required parameter: page");
    }

    @Test
    @DisplayName("IllegalArgumentException — 400, VALIDATION_ERROR, 예외 메시지 포함")
    void handleIllegalArgument_returns400WithMessage() {
        IllegalArgumentException e = new IllegalArgumentException("invalid status value");

        ResponseEntity<ErrorResponse> response = handler.handleIllegalArgument(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().message()).isEqualTo("invalid status value");
    }

    @Test
    @DisplayName("ObjectOptimisticLockingFailureException — 409, CONFLICT")
    void handleOptimisticLock_returns409() {
        ObjectOptimisticLockingFailureException e =
                new ObjectOptimisticLockingFailureException("Account", "acc-1");

        ResponseEntity<ErrorResponse> response = handler.handleOptimisticLock(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo("CONFLICT");
        assertThat(response.getBody().message()).isEqualTo("Concurrent modification detected. Please retry.");
    }

    @Test
    @DisplayName("Exception — 500, INTERNAL_ERROR")
    void handleGeneral_returns500() {
        Exception e = new RuntimeException("unexpected failure");

        ResponseEntity<ErrorResponse> response = handler.handleGeneral(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().message()).isEqualTo("An unexpected error occurred");
    }
}
