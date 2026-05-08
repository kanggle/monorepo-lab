package com.wms.admin.application.fakes;

import com.wms.admin.application.port.SettingRepository;
import com.wms.admin.domain.Setting;
import com.wms.admin.domain.SettingScope;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

public class InMemorySettingRepository implements SettingRepository {

    private record Key(String key, UUID warehouseId) {}

    private final Map<Key, Setting> store = new LinkedHashMap<>();

    public void seed(Setting s) {
        store.put(new Key(s.key(), s.warehouseId()), s);
    }

    @Override
    public Setting save(Setting setting) {
        Key k = new Key(setting.key(), setting.warehouseId());
        Setting prior = store.get(k);
        long nextVersion = prior == null ? setting.version() : setting.version() + 1;
        Setting stored = new Setting(setting.key(), setting.scope(), setting.warehouseId(),
                setting.valueJson(), setting.schemaJson(), setting.description(),
                nextVersion,
                setting.createdAt(), setting.createdBy(), setting.updatedAt(), setting.updatedBy());
        store.put(k, stored);
        return stored;
    }

    @Override
    public Optional<Setting> find(String key, UUID warehouseId) {
        return Optional.ofNullable(store.get(new Key(key, warehouseId)));
    }

    @Override
    public Page<Setting> search(String keyPrefix, SettingScope scope, UUID warehouseId, Pageable pageable) {
        List<Setting> all = store.values().stream()
                .filter(s -> keyPrefix == null || s.key().startsWith(keyPrefix))
                .filter(s -> scope == null || s.scope() == scope)
                .filter(s -> warehouseId == null || Objects.equals(s.warehouseId(), warehouseId))
                .toList();
        return new PageImpl<>(all, pageable, all.size());
    }
}
