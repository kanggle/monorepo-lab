package com.wms.outbound.adapter.out.persistence.repository;

import com.wms.outbound.adapter.out.persistence.entity.MasterLocationSnapshot;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MasterLocationSnapshotRepository extends JpaRepository<MasterLocationSnapshot, UUID> {
}
