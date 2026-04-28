package com.wms.inventory.application.service;

import com.wms.inventory.application.port.out.ReservationRepository;
import com.wms.inventory.domain.exception.StateTransitionInvalidException;
import com.wms.inventory.domain.model.Reservation;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Releases {@code RESERVED} reservations whose {@code expiresAt} has passed.
 *
 * <p>The sweep itself is non-transactional. Each reservation release
 * delegates to {@link ReleaseReservationService#releaseExpired} which is
 * {@code @Transactional} — so each release opens / commits its own
 * transaction independently and a single failure does not abort the batch.
 *
 * <p>Disabled under {@code standalone} (no scheduler in that profile).
 * Disabled via {@code inventory.reservation.ttl-job.enabled=false} so
 * test harnesses can drive the job deterministically via {@link #runOnce()}.
 */
@Component
@Profile("!standalone")
public class ReservationExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(ReservationExpiryJob.class);
    private static final String SYSTEM_ACTOR = "system:reservation-ttl-job";

    private final ReservationRepository reservationRepository;
    private final ReleaseReservationService releaseService;
    private final Clock clock;
    private final int batchSize;
    private final boolean enabled;

    public ReservationExpiryJob(ReservationRepository reservationRepository,
                                ReleaseReservationService releaseService,
                                Clock clock,
                                @Value("${inventory.reservation.ttl-job.batch-size:200}") int batchSize,
                                @Value("${inventory.reservation.ttl-job.enabled:true}") boolean enabled) {
        this.reservationRepository = reservationRepository;
        this.releaseService = releaseService;
        this.clock = clock;
        this.batchSize = batchSize;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${inventory.reservation.ttl-job.interval-ms:60000}",
            initialDelayString = "${inventory.reservation.ttl-job.initial-delay-ms:60000}")
    public void runOnSchedule() {
        if (!enabled) {
            return;
        }
        runOnce();
    }

    /**
     * Single-pass sweep. Returns the number of reservations released. Tests
     * call this directly after fixing the {@link Clock}.
     */
    public int runOnce() {
        Instant asOf = clock.instant();
        List<Reservation> expired = reservationRepository.findExpired(asOf, batchSize);
        if (expired.isEmpty()) {
            return 0;
        }
        log.info("ReservationExpiryJob: {} expired RESERVED rows; releasing", expired.size());
        int released = 0;
        for (Reservation r : expired) {
            try {
                // Each call opens its own @Transactional boundary inside
                // ReleaseReservationService.release(...). One failure does
                // NOT abort the rest of the sweep.
                releaseService.releaseExpired(r.id(), SYSTEM_ACTOR);
                released++;
            } catch (StateTransitionInvalidException terminalRace) {
                log.debug("Reservation {} already terminal — skipping TTL release", r.id());
            } catch (OptimisticLockingFailureException versionRace) {
                log.debug("Reservation {} version race during TTL release — will retry next tick",
                        r.id());
            } catch (RuntimeException e) {
                log.warn("Reservation {} TTL release failed: {}", r.id(), e.getMessage());
            }
        }
        return released;
    }
}
