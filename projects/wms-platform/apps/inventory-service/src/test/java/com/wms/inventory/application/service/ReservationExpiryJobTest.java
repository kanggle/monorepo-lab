package com.wms.inventory.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wms.inventory.application.port.out.ReservationRepository;
import com.wms.inventory.domain.exception.StateTransitionInvalidException;
import com.wms.inventory.domain.model.Reservation;
import com.wms.inventory.domain.model.ReservationLine;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReservationExpiryJobTest {

    private static final Instant NOW = Instant.parse("2026-04-25T15:00:00Z");

    private ReservationRepository reservationRepo;
    private ReleaseReservationService releaseService;
    private ReservationExpiryJob job;

    @BeforeEach
    void setUp() {
        reservationRepo = mock(ReservationRepository.class);
        releaseService = mock(ReleaseReservationService.class);
        job = new ReservationExpiryJob(reservationRepo, releaseService,
                Clock.fixed(NOW, ZoneOffset.UTC), 200, true);
    }

    @Test
    void noExpiredRowsReturnsZero() {
        when(reservationRepo.findExpired(any(), anyInt())).thenReturn(List.of());
        int released = job.runOnce();
        assertThat(released).isZero();
    }

    @Test
    void releasesEachExpiredRowInIndependentCalls() {
        Reservation r1 = sample();
        Reservation r2 = sample();
        when(reservationRepo.findExpired(any(), anyInt())).thenReturn(List.of(r1, r2));

        int released = job.runOnce();

        assertThat(released).isEqualTo(2);
        verify(releaseService).releaseExpired(r1.id(), "system:reservation-ttl-job");
        verify(releaseService).releaseExpired(r2.id(), "system:reservation-ttl-job");
    }

    @Test
    void perReservationFailureDoesNotAbortBatch() {
        Reservation r1 = sample();
        Reservation r2 = sample();
        Reservation r3 = sample();
        when(reservationRepo.findExpired(any(), anyInt()))
                .thenReturn(List.of(r1, r2, r3));
        // Stub the generic case FIRST, then the specific override — Mockito
        // resolves to the most-recently-configured matching stub.
        doReturn(null).when(releaseService).releaseExpired(any(), anyString());
        doThrow(new StateTransitionInvalidException("already terminal"))
                .when(releaseService).releaseExpired(r2.id(), "system:reservation-ttl-job");

        int released = job.runOnce();
        assertThat(released).isEqualTo(2); // r1 and r3 succeeded; r2 skipped
        verify(releaseService, atLeast(1)).releaseExpired(r1.id(), "system:reservation-ttl-job");
        verify(releaseService, times(1)).releaseExpired(r3.id(), "system:reservation-ttl-job");
    }

    @Test
    void disabledViaPropertySkipsScheduledRun() {
        ReservationExpiryJob disabled = new ReservationExpiryJob(reservationRepo, releaseService,
                Clock.fixed(NOW, ZoneOffset.UTC), 200, false);
        disabled.runOnSchedule();
        verify(reservationRepo, times(0)).findExpired(any(), anyInt());
    }

    private static Reservation sample() {
        UUID id = UUID.randomUUID();
        ReservationLine line = new ReservationLine(
                UUID.randomUUID(), id, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), null, 5);
        return Reservation.create(id, UUID.randomUUID(), UUID.randomUUID(),
                List.of(line), NOW.plusSeconds(60), NOW.minusSeconds(120), "seed");
    }
}
