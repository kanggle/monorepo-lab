package com.wms.outbound.adapter.out.persistence.repository;

import com.wms.outbound.adapter.out.persistence.entity.MasterZoneSnapshot;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MasterZoneSnapshotRepository extends JpaRepository<MasterZoneSnapshot, UUID> {
}
