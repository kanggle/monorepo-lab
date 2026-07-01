package com.wms.outbound.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Guards the contract-defined error code for {@link OrderNoDuplicateException}
 * (TASK-MONO-321). The {@code GlobalExceptionHandler} emits {@code errorCode()}
 * as the API envelope's {@code code}; the outbound service + {@code
 * platform/error-handling.md} contracts require the granular {@code
 * ORDER_NO_DUPLICATE}, not the generic {@code CONFLICT}.
 */
class OrderNoDuplicateExceptionTest {

    @Test
    void errorCodeIsGranularContractCode() {
        OrderNoDuplicateException e = new OrderNoDuplicateException("ORD-20260701-1");

        assertThat(e.errorCode()).isEqualTo("ORDER_NO_DUPLICATE");
        assertThat(e.getOrderNo()).isEqualTo("ORD-20260701-1");
    }
}
