/**
 * Shared, domain-agnostic notification consumer / delivery library — the <b>D4</b>
 * deliverable of {@code docs/adr/ADR-MONO-043-notification-architecture-unification.md}
 * (ACCEPTED).
 *
 * <p>It lifts the machinery the four per-domain notification-services (erp,
 * ecommerce, wms, fan) re-derive — the canonical inbox item shape, the
 * envelope-validated + deduped consumer leg, the Category-C delivery engine
 * (state machine + jittered backoff + per-row dispatch), and the external-channel
 * adapter SPI — into one configurable, composition-over-inheritance module
 * (ADR-MONO-038 posture).
 *
 * <h2>Ownership split (what the lib owns vs the service owns)</h2>
 * <ul>
 *   <li><b>Lib owns</b>: the {@link com.example.platform.notification.view.NotificationView}
 *       contract shape; the {@link com.example.platform.notification.channel channel SPI};
 *       the {@link com.example.platform.notification.delivery delivery engine} control flow,
 *       state model, ports, and backoff arithmetic;
 *       {@link com.example.platform.notification.consumer.NotificationConsumerSupport}.</li>
 *   <li><b>Service owns</b>: the channel adapters (vendor wiring, credentials), the recipient
 *       resolution, the topics, the error envelope + metrics, the {@code @Scheduled} retry
 *       trigger, the {@code FOR UPDATE SKIP LOCKED} query, the per-row
 *       {@code @Transactional(REQUIRES_NEW)} bean, and the outbox re-emission.</li>
 * </ul>
 *
 * <p>HARDSTOP-03 — this package is project-agnostic: no domain event types, no
 * recipient-resolution logic, no service names, no channel credentials. The root
 * package is {@code com.example.platform.notification} (deliberately NOT
 * {@code com.example.notification}, which collides with the ecommerce service).
 */
package com.example.platform.notification;
