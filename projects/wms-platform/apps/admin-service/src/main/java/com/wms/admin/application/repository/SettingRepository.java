package com.wms.admin.application.repository;

import com.wms.admin.domain.Setting;
import com.wms.admin.domain.SettingScope;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SettingRepository {

    Setting save(Setting setting);

    Optional<Setting> find(String key, UUID warehouseId);

    Page<Setting> search(String keyPrefix, SettingScope scope, UUID warehouseId, Pageable pageable);
}
