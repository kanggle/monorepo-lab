package com.wms.admin.readmodel.master;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SkuRefRepository extends JpaRepository<SkuRefEntity, UUID> {

    Page<SkuRefEntity> findAllBy(Pageable pageable);
}
