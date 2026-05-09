package com.wms.admin.api.settings;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.admin.api.dashboard.PageableSupport;
import com.wms.admin.api.dto.PageResponse;
import com.wms.admin.api.settings.dto.SettingResponse;
import com.wms.admin.api.settings.dto.UpsertSettingRequest;
import com.wms.admin.application.settings.SettingsService;
import com.wms.admin.application.settings.UpsertSettingCommand;
import com.wms.admin.domain.Setting;
import com.wms.admin.domain.SettingScope;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Settings REST surface — admin-service-api.md § 5. {@code GET / GET /{key} /
 * PUT /{key}}. v1 does not create new keys via API; PUT is upsert against an
 * existing seeded key.
 */
@RestController
@RequestMapping("/api/v1/admin/settings")
public class SettingsController {

    private static final String ACTOR_HEADER = "X-Actor-Id";
    private static final String DEFAULT_SORT = "key,asc";

    private final SettingsService settingsService;
    private final ObjectMapper objectMapper;

    public SettingsController(SettingsService settingsService, ObjectMapper objectMapper) {
        this.settingsService = settingsService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public PageResponse<SettingResponse> list(
            @RequestParam(required = false) String keyPrefix,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = DEFAULT_SORT) String sort) {
        SettingScope scopeEnum = parseScope(scope);
        Page<Setting> result = settingsService.search(keyPrefix, scopeEnum, warehouseId, PageableSupport.pageable(page, size, sort));
        return PageResponse.from(result, sort, s -> SettingResponse.from(s, objectMapper));
    }

    @GetMapping("/{key}")
    public ResponseEntity<SettingResponse> getByKey(@PathVariable String key,
                                                    @RequestParam(required = false) UUID warehouseId) {
        Setting setting = settingsService.findByKey(key, warehouseId);
        return ResponseEntity.ok().eTag(PageableSupport.etag(setting.version())).body(SettingResponse.from(setting, objectMapper));
    }

    @PutMapping("/{key}")
    public ResponseEntity<SettingResponse> upsert(@PathVariable String key,
                                                  @Valid @RequestBody UpsertSettingRequest request,
                                                  @RequestHeader(value = ACTOR_HEADER, required = false) String actorId) {
        Setting saved = settingsService.upsert(new UpsertSettingCommand(
                key, request.warehouseId(), request.valueJson(), actorId));
        return ResponseEntity.ok().eTag(PageableSupport.etag(saved.version())).body(SettingResponse.from(saved, objectMapper));
    }

    private static SettingScope parseScope(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return SettingScope.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("scope must be GLOBAL or WAREHOUSE");
        }
    }
}
