package com.wms.admin.infra.persistence;

import com.wms.admin.application.port.SettingRepository;
import com.wms.admin.domain.Setting;
import com.wms.admin.domain.SettingScope;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
public class SettingRepositoryAdapter implements SettingRepository {

    private final AdminSettingJpaRepository repo;

    public SettingRepositoryAdapter(AdminSettingJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public Setting save(Setting setting) {
        return repo.save(AdminSettingJpaEntity.fromDomain(setting)).toDomain();
    }

    @Override
    public Optional<Setting> find(String key, UUID warehouseId) {
        return repo.findById(new AdminSettingId(key, warehouseId)).map(AdminSettingJpaEntity::toDomain);
    }

    @Override
    public Page<Setting> search(String keyPrefix, SettingScope scope, UUID warehouseId, Pageable pageable) {
        return repo.search(keyPrefix, scope, warehouseId, pageable).map(AdminSettingJpaEntity::toDomain);
    }
}
