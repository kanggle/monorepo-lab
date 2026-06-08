package com.wms.outbound.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.wms.outbound.application.result.PickingRequestResult;
import com.wms.outbound.application.service.fakes.FakePickingPersistencePort;
import com.wms.outbound.domain.model.PickingRequest;
import com.wms.outbound.domain.model.PickingRequestLine;
import com.wms.outbound.domain.model.PickingRequestStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PickingQueryService} (TASK-BE-343).
 *
 * <p>Verifies that {@code findByOrderId} maps lines from the domain aggregate
 * correctly — including {@code locationId} and {@code qtyToPick} — and that
 * lot-tracked lines carry a non-null {@code lotId} while non-lot lines carry
 * {@code null}.
 */
class PickingQueryServiceTest {

    private static final Instant T0 = Instant.parse("2026-04-29T10:00:00Z");

    private FakePickingPersistencePort fakePicking;
    private PickingQueryService service;

    @BeforeEach
    void setUp() {
        fakePicking = new FakePickingPersistencePort();
        service = new PickingQueryService(fakePicking);
    }

    @Test
    @DisplayName("findByOrderId maps all line fields including locationId + qtyToPick")
    void findByOrderId_mapsLinesFromDomainAggregate() {
        UUID orderId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        UUID lineId = UUID.randomUUID();
        UUID orderLineId = UUID.randomUUID();
        UUID skuId = UUID.randomUUID();

        PickingRequestLine line = new PickingRequestLine(
                lineId, UUID.randomUUID(), orderLineId, skuId, null, locationId, 42);
        PickingRequest pr = pickingRequest(orderId, List.of(line));
        fakePicking.save(pr);

        Optional<PickingRequestResult> result = service.findByOrderId(orderId);

        assertThat(result).isPresent();
        PickingRequestResult r = result.get();
        assertThat(r.orderId()).isEqualTo(orderId);
        assertThat(r.lines()).hasSize(1);
        assertThat(r.lines().get(0).pickingRequestLineId()).isEqualTo(lineId);
        assertThat(r.lines().get(0).orderLineId()).isEqualTo(orderLineId);
        assertThat(r.lines().get(0).skuId()).isEqualTo(skuId);
        assertThat(r.lines().get(0).lotId()).isNull();
        assertThat(r.lines().get(0).locationId()).isEqualTo(locationId);
        assertThat(r.lines().get(0).qtyToPick()).isEqualTo(42);
    }

    @Test
    @DisplayName("findByOrderId — lot-tracked line carries non-null lotId")
    void findByOrderId_lotTrackedLine_lotIdMapped() {
        UUID orderId = UUID.randomUUID();
        UUID lotId = UUID.randomUUID();

        PickingRequestLine line = new PickingRequestLine(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), lotId, UUID.randomUUID(), 5);
        fakePicking.save(pickingRequest(orderId, List.of(line)));

        PickingRequestResult r = service.findByOrderId(orderId).orElseThrow();

        assertThat(r.lines().get(0).lotId()).isEqualTo(lotId);
    }

    @Test
    @DisplayName("findByOrderId — no picking request → empty Optional")
    void findByOrderId_noPicking_returnsEmpty() {
        Optional<PickingRequestResult> result = service.findByOrderId(UUID.randomUUID());
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findById maps lines the same way findByOrderId does")
    void findById_mapsLines() {
        UUID pickingId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();

        PickingRequestLine line = new PickingRequestLine(
                UUID.randomUUID(), pickingId, UUID.randomUUID(),
                UUID.randomUUID(), null, locationId, 7);
        PickingRequest pr = pickingRequestWithId(pickingId, UUID.randomUUID(), List.of(line));
        fakePicking.save(pr);

        PickingRequestResult r = service.findById(pickingId).orElseThrow();

        assertThat(r.pickingRequestId()).isEqualTo(pickingId);
        assertThat(r.lines()).hasSize(1);
        assertThat(r.lines().get(0).locationId()).isEqualTo(locationId);
        assertThat(r.lines().get(0).qtyToPick()).isEqualTo(7);
    }

    // ------------------------------------------------------------------
    //  helpers
    // ------------------------------------------------------------------

    private static PickingRequest pickingRequest(UUID orderId, List<PickingRequestLine> lines) {
        return pickingRequestWithId(UUID.randomUUID(), orderId, lines);
    }

    private static PickingRequest pickingRequestWithId(UUID id, UUID orderId,
                                                        List<PickingRequestLine> lines) {
        return new PickingRequest(
                id, orderId, UUID.randomUUID(), UUID.randomUUID(),
                PickingRequestStatus.SUBMITTED, 0L, T0, T0, lines);
    }
}
