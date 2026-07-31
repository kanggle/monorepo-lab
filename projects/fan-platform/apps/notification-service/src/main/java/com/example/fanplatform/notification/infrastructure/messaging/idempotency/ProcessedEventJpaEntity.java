package com.example.fanplatform.notification.infrastructure.messaging.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Consumer idempotency dedupe row (architecture.md § Idempotency,
 * ADR-MONO-058 § D7 {@code EventDedupePort} adoption). Keyed on the envelope
 * {@code eventId}; a duplicate eventId is skipped without creating a second
 * notification.
 *
 * <p>Column shape: event_id / event_type / processed_at. Declared service-locally,
 * which is now the only option: TASK-MONO-406 deleted the library's own
 * {@code ProcessedEventJpaEntity} / {@code ProcessedEventJpaRepository} (and the
 * {@code OutboxAutoConfiguration} / {@code OutboxJpaConfig} that registered them
 * app-wide) — a shared-library entity for a per-service dedupe table violated
 * ADR-MONO-004 + {@code platform/shared-library-policy.md} and collided with
 * service-local beans of the same name. {@code libs:java-messaging} no longer ships
 * any {@code @Entity}.
 *
 * <p>The mapped class is still declared even though rows are now written via a
 * native {@code INSERT … ON CONFLICT DO NOTHING} ({@link ProcessedEventJpaRepository
 * #insertIfAbsent}, not {@code save(...)}) — {@link ProcessedEventJpaRepository}'s
 * {@code JpaRepository<ProcessedEventJpaEntity, String>} generic parameter still
 * needs a mapped entity, and {@code JpaConfig}'s {@code @EntityScan} targets this
 * package.
 */
@Entity
@Table(name = "processed_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessedEventJpaEntity {

    @Id
    @Column(name = "event_id", length = 64, nullable = false)
    private String eventId;

    @Column(name = "event_type", length = 100, nullable = false)
    private String eventType;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;
}
