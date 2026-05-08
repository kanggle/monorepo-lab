package com.wms.admin.application.settings;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.wms.admin.application.AdminEventEnvelopeBuilder;
import com.wms.admin.application.port.OutboxPort;
import com.wms.admin.application.port.SettingRepository;
import com.wms.admin.domain.Setting;
import com.wms.admin.domain.SettingScope;
import com.wms.admin.domain.error.SettingImmutableFieldException;
import com.wms.admin.domain.error.SettingNotFoundException;
import com.wms.admin.domain.error.SettingValidationErrorException;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Settings upsert + JSON Schema validation. v1 forbids creating new keys via
 * API — only the seed migration registers keys with their {@code schemaJson}.
 * {@link SettingNotFoundException} on absent key.
 */
@Service
public class SettingsService {

    private static final String AGGREGATE_TYPE = "setting";

    private final SettingRepository settingRepository;
    private final OutboxPort outboxPort;
    private final AdminEventEnvelopeBuilder envelopeBuilder;
    private final ObjectMapper objectMapper;
    private final JsonSchemaFactory schemaFactory;
    private final Clock clock;

    public SettingsService(SettingRepository settingRepository,
                           OutboxPort outboxPort,
                           AdminEventEnvelopeBuilder envelopeBuilder,
                           ObjectMapper objectMapper,
                           Clock clock) {
        this.settingRepository = settingRepository;
        this.outboxPort = outboxPort;
        this.envelopeBuilder = envelopeBuilder;
        this.objectMapper = objectMapper;
        this.schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
        this.clock = clock;
    }

    @PreAuthorize("hasAnyRole('WMS_ADMIN','WMS_SUPERADMIN')")
    @Transactional
    public Setting upsert(UpsertSettingCommand cmd) {
        Setting existing = settingRepository.find(cmd.key(), cmd.warehouseId())
                .orElseThrow(() -> new SettingNotFoundException(cmd.key()));

        // Scope/warehouse_id are immutable: caller must match what was persisted.
        if (existing.scope() == SettingScope.WAREHOUSE && cmd.warehouseId() == null) {
            throw new SettingImmutableFieldException("warehouseId");
        }
        if (existing.scope() == SettingScope.GLOBAL && cmd.warehouseId() != null) {
            throw new SettingImmutableFieldException("warehouseId");
        }

        validateValueAgainstSchema(cmd.valueJson(), existing.schemaJson(), cmd.key());

        String newValueJson = serialise(cmd.valueJson());
        String previousValueJson = existing.valueJson();

        Instant now = clock.instant();
        Setting updated = existing.withValue(newValueJson, now, cmd.actorId());
        Setting saved = settingRepository.save(updated);

        // Only emit when value changed (no-op upserts are skipped)
        if (!equalsIgnoringWhitespace(previousValueJson, newValueJson)) {
            appendChangedEvent(saved, previousValueJson, cmd.actorId(), now);
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public Setting findByKey(String key, UUID warehouseId) {
        return settingRepository.find(key, warehouseId)
                .orElseThrow(() -> new SettingNotFoundException(key));
    }

    @Transactional(readOnly = true)
    public Page<Setting> search(String keyPrefix, SettingScope scope, UUID warehouseId, Pageable pageable) {
        return settingRepository.search(keyPrefix, scope, warehouseId, pageable);
    }

    private void validateValueAgainstSchema(JsonNode valueJson, String schemaJsonRaw, String key) {
        try {
            JsonNode schemaNode = objectMapper.readTree(schemaJsonRaw);
            JsonSchema schema = schemaFactory.getSchema(schemaNode);
            Set<ValidationMessage> errors = schema.validate(valueJson);
            if (!errors.isEmpty()) {
                String detail = "value for " + key + " failed schema validation: "
                        + errors.stream().map(ValidationMessage::getMessage).collect(Collectors.joining("; "));
                throw new SettingValidationErrorException(detail);
            }
        } catch (SettingValidationErrorException e) {
            throw e;
        } catch (Exception e) {
            throw new SettingValidationErrorException("schema validation failed for " + key + ": " + e.getMessage());
        }
    }

    private String serialise(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise setting value", e);
        }
    }

    private boolean equalsIgnoringWhitespace(String a, String b) {
        try {
            JsonNode aNode = objectMapper.readTree(a);
            JsonNode bNode = objectMapper.readTree(b);
            return aNode.equals(bNode);
        } catch (Exception e) {
            return a.equals(b);
        }
    }

    private void appendChangedEvent(Setting setting, String previousValueJson,
                                    String actorId, Instant occurredAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("key", setting.key());
        payload.put("scope", setting.scope().name());
        payload.put("warehouseId", setting.warehouseId() != null ? setting.warehouseId().toString() : null);
        payload.put("valueJson", parseOrRaw(setting.valueJson()));
        payload.put("previousValueJson", parseOrRaw(previousValueJson));
        payload.put("version", setting.version());
        String envelope = envelopeBuilder.build(
                "admin.settings.changed", AGGREGATE_TYPE, setting.key(),
                actorId, occurredAt, payload);
        outboxPort.append(AGGREGATE_TYPE, setting.key(), "admin.settings.changed",
                envelope, setting.key());
    }

    private Object parseOrRaw(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return json;
        }
    }
}
