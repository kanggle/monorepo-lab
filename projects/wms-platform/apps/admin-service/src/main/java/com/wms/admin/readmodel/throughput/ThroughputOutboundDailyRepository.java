package com.wms.admin.readmodel.throughput;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ThroughputOutboundDailyRepository
        extends JpaRepository<ThroughputOutboundDailyEntity, ThroughputDailyId> {

    List<ThroughputOutboundDailyEntity> findByWarehouseIdAndDateBetweenOrderByDateAsc(
            UUID warehouseId, LocalDate from, LocalDate to);
}
