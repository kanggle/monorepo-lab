package com.wms.outbound.adapter.out.persistence.repository;

import com.wms.outbound.adapter.out.persistence.entity.MasterPartnerSnapshot;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MasterPartnerSnapshotRepository extends JpaRepository<MasterPartnerSnapshot, UUID> {
}
