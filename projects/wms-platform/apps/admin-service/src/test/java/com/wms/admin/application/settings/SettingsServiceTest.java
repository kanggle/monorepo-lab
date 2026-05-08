package com.wms.admin.application.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wms.admin.application.AdminEventEnvelopeBuilder;
import com.wms.admin.application.fakes.InMemorySettingRepository;
import com.wms.admin.application.fakes.RecordingOutboxPort;
import com.wms.admin.domain.Setting;
import com.wms.admin.domain.SettingScope;
import com.wms.admin.domain.error.SettingImmutableFieldException;
import com.wms.admin.domain.error.SettingNotFoundException;
import com.wms.admin.domain.error.SettingValidationErrorException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SettingsServiceTest {

    private InMemorySettingRepository repo;
    private RecordingOutboxPort outbox;
    private ObjectMapper mapper;
    private SettingsService service;

    @BeforeEach
    void setUp() throws Exception {
        repo = new InMemorySettingRepository();
        outbox = new RecordingOutboxPort();
        mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        Clock fixed = Clock.fixed(Instant.parse("2026-05-09T10:00:00Z"), ZoneOffset.UTC);
        service = new SettingsService(repo, outbox,
                new AdminEventEnvelopeBuilder(mapper), mapper, fixed);

        // Seed a global setting with an integer schema (1..168).
        repo.seed(new Setting(
                "inventory.reservation.ttl_hours", SettingScope.GLOBAL, null,
                "24",
                "{\"type\":\"integer\",\"minimum\":1,\"maximum\":168}",
                "TTL", 0L,
                Instant.parse("2026-05-09T09:00:00Z"), "system",
                Instant.parse("2026-05-09T09:00:00Z"), "system"));
    }

    @Test
    void upsert_validValue_savesAndEmitsChangedEvent() throws Exception {
        JsonNode value = mapper.readTree("36");
        Setting saved = service.upsert(new UpsertSettingCommand(
                "inventory.reservation.ttl_hours", null, value, "admin"));
        assertThat(saved.valueJson()).isEqualTo("36");
        assertThat(outbox.eventTypes()).containsExactly("admin.settings.changed");
    }

    @Test
    void upsert_unknownKey_raisesSettingNotFound() throws Exception {
        JsonNode value = mapper.readTree("36");
        assertThatThrownBy(() -> service.upsert(new UpsertSettingCommand(
                "missing.key", null, value, "admin")))
                .isInstanceOf(SettingNotFoundException.class);
    }

    @Test
    void upsert_outOfRangeValue_raisesValidationError() throws Exception {
        // schema says minimum=1, maximum=168 — 200 fails.
        JsonNode value = mapper.readTree("200");
        assertThatThrownBy(() -> service.upsert(new UpsertSettingCommand(
                "inventory.reservation.ttl_hours", null, value, "admin")))
                .isInstanceOf(SettingValidationErrorException.class);
    }

    @Test
    void upsert_wrongType_raisesValidationError() throws Exception {
        // schema requires integer; pass a string.
        JsonNode value = mapper.readTree("\"thirty-six\"");
        assertThatThrownBy(() -> service.upsert(new UpsertSettingCommand(
                "inventory.reservation.ttl_hours", null, value, "admin")))
                .isInstanceOf(SettingValidationErrorException.class);
    }

    @Test
    void upsert_warehouseScopedKey_withMatchingWarehouseId_succeeds() throws Exception {
        // Seed a WAREHOUSE-scoped setting; upsert with the matching warehouse_id should succeed.
        java.util.UUID wh = java.util.UUID.randomUUID();
        repo.seed(new Setting(
                "outbound.priority", SettingScope.WAREHOUSE, wh,
                "5", "{\"type\":\"integer\",\"minimum\":1,\"maximum\":10}", null, 0L,
                Instant.parse("2026-05-09T09:00:00Z"), "system",
                Instant.parse("2026-05-09T09:00:00Z"), "system"));
        JsonNode value = mapper.readTree("7");
        Setting saved = service.upsert(new UpsertSettingCommand(
                "outbound.priority", wh, value, "admin"));
        assertThat(saved.valueJson()).isEqualTo("7");
        assertThat(saved.warehouseId()).isEqualTo(wh);
    }

    @Test
    void upsert_warehouseScopeMismatch_raisesNotFound() throws Exception {
        // Caller looks up with warehouseId=null but the persisted row is WAREHOUSE-scoped — no row
        // matches the (key, warehouseId) tuple, so SettingNotFoundException surfaces (404 path
        // per admin-service-api.md § 5.3, before the immutable-field check can fire).
        java.util.UUID wh = java.util.UUID.randomUUID();
        repo.seed(new Setting(
                "outbound.priority", SettingScope.WAREHOUSE, wh,
                "5", "{\"type\":\"integer\",\"minimum\":1,\"maximum\":10}", null, 0L,
                Instant.parse("2026-05-09T09:00:00Z"), "system",
                Instant.parse("2026-05-09T09:00:00Z"), "system"));
        JsonNode value = mapper.readTree("7");
        assertThatThrownBy(() -> service.upsert(new UpsertSettingCommand(
                "outbound.priority", null, value, "admin")))
                .isInstanceOf(SettingNotFoundException.class);
    }

    @Test
    void upsert_sameValue_skipsEventEmission() throws Exception {
        JsonNode value = mapper.readTree("24"); // same as seeded value
        service.upsert(new UpsertSettingCommand(
                "inventory.reservation.ttl_hours", null, value, "admin"));
        assertThat(outbox.eventTypes()).isEmpty();
    }
}
