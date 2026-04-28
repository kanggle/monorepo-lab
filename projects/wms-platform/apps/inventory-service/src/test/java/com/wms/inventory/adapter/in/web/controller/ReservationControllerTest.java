package com.wms.inventory.adapter.in.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.inventory.adapter.in.web.advice.GlobalExceptionHandler;
import com.wms.inventory.application.port.in.ConfirmReservationUseCase;
import com.wms.inventory.application.port.in.QueryReservationUseCase;
import com.wms.inventory.application.port.in.ReleaseReservationUseCase;
import com.wms.inventory.application.port.in.ReserveStockUseCase;
import com.wms.inventory.application.result.ReservationView;
import com.wms.inventory.config.SecurityConfig;
import com.wms.inventory.domain.exception.ReservationQuantityMismatchException;
import com.wms.inventory.domain.exception.StateTransitionInvalidException;
import com.wms.inventory.domain.model.ReservationStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ReservationController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class ReservationControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private ReserveStockUseCase reserveStock;
    @MockBean private ConfirmReservationUseCase confirmReservation;
    @MockBean private ReleaseReservationUseCase releaseReservation;
    @MockBean private QueryReservationUseCase queryReservation;

    private static final UUID RES_ID = UUID.randomUUID();
    private static final UUID PICKING_REQ = UUID.randomUUID();
    private static final UUID INV_ID = UUID.randomUUID();
    private static final UUID WAREHOUSE = UUID.randomUUID();

    @Test
    void createWithReserveRoleSucceeds() throws Exception {
        when(reserveStock.reserve(any())).thenReturn(sample(ReservationStatus.RESERVED));

        String body = objectMapper.writeValueAsString(Map.of(
                "pickingRequestId", PICKING_REQ,
                "warehouseId", WAREHOUSE,
                "lines", List.of(Map.of("inventoryId", INV_ID, "quantity", 5))));

        mockMvc.perform(post("/api/v1/inventory/reservations")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INVENTORY_RESERVE"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RESERVED"))
                .andExpect(jsonPath("$.id").value(RES_ID.toString()));
    }

    @Test
    void createWithReadRoleAloneIsForbidden() throws Exception {
        String body = "{\"pickingRequestId\":\"" + PICKING_REQ + "\","
                + "\"warehouseId\":\"" + WAREHOUSE + "\","
                + "\"lines\":[{\"inventoryId\":\"" + INV_ID + "\",\"quantity\":5}]}";

        mockMvc.perform(post("/api/v1/inventory/reservations")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INVENTORY_READ"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void createMissingLinesReturnsValidationError() throws Exception {
        String body = "{\"pickingRequestId\":\"" + PICKING_REQ + "\","
                + "\"warehouseId\":\"" + WAREHOUSE + "\","
                + "\"lines\":[]}";

        mockMvc.perform(post("/api/v1/inventory/reservations")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INVENTORY_RESERVE"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void confirmHappyPath() throws Exception {
        when(confirmReservation.confirm(any())).thenReturn(sample(ReservationStatus.CONFIRMED));

        String body = "{\"version\":0,\"lines\":[{\"reservationLineId\":\""
                + UUID.randomUUID() + "\",\"shippedQuantity\":5}]}";

        mockMvc.perform(post("/api/v1/inventory/reservations/{id}/confirm", RES_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INVENTORY_RESERVE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void confirmQuantityMismatchReturns422() throws Exception {
        when(confirmReservation.confirm(any())).thenThrow(
                new ReservationQuantityMismatchException(UUID.randomUUID(), 5, 3));

        String body = "{\"version\":0,\"lines\":[{\"reservationLineId\":\""
                + UUID.randomUUID() + "\",\"shippedQuantity\":3}]}";

        mockMvc.perform(post("/api/v1/inventory/reservations/{id}/confirm", RES_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INVENTORY_RESERVE"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("RESERVATION_QUANTITY_MISMATCH"));
    }

    @Test
    void confirmAlreadyTerminalReturns422() throws Exception {
        when(confirmReservation.confirm(any())).thenThrow(
                new StateTransitionInvalidException("already CONFIRMED"));

        String body = "{\"version\":1,\"lines\":[{\"reservationLineId\":\""
                + UUID.randomUUID() + "\",\"shippedQuantity\":5}]}";

        mockMvc.perform(post("/api/v1/inventory/reservations/{id}/confirm", RES_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INVENTORY_RESERVE"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("STATE_TRANSITION_INVALID"));
    }

    @Test
    void releaseExpiredReasonRejected() throws Exception {
        String body = "{\"reason\":\"EXPIRED\",\"version\":0}";

        mockMvc.perform(post("/api/v1/inventory/reservations/{id}/release", RES_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INVENTORY_ADMIN"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void releaseCancelledByAdminSucceeds() throws Exception {
        when(releaseReservation.release(any())).thenReturn(sample(ReservationStatus.RELEASED));

        String body = "{\"reason\":\"CANCELLED\",\"version\":0}";

        mockMvc.perform(post("/api/v1/inventory/reservations/{id}/release", RES_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INVENTORY_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RELEASED"));
    }

    @Test
    void getByIdReturnsETagAndBody() throws Exception {
        when(queryReservation.findById(RES_ID))
                .thenReturn(Optional.of(sample(ReservationStatus.RESERVED)));

        mockMvc.perform(get("/api/v1/inventory/reservations/{id}", RES_ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INVENTORY_READ"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(RES_ID.toString()));
    }

    @Test
    void getByIdUnknownReturns404() throws Exception {
        when(queryReservation.findById(any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/inventory/reservations/{id}", UUID.randomUUID())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INVENTORY_READ"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
    }

    private static ReservationView sample(ReservationStatus status) {
        ReservationView.Line line = new ReservationView.Line(
                UUID.randomUUID(), INV_ID, UUID.randomUUID(), UUID.randomUUID(), null, 5);
        Instant now = Instant.parse("2026-04-25T10:00:00Z");
        return new ReservationView(
                RES_ID, PICKING_REQ, WAREHOUSE, status,
                now.plusSeconds(86400),
                status == ReservationStatus.RELEASED
                        ? com.wms.inventory.domain.model.ReleasedReason.CANCELLED : null,
                status == ReservationStatus.CONFIRMED ? now : null,
                status == ReservationStatus.RELEASED ? now : null,
                List.of(line), 0L, now, now);
    }
}
