package com.wms.admin.readmodel.throughput;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Composite key for the two throughput daily counter tables. */
public class ThroughputDailyId implements Serializable {

    private LocalDate date;
    private UUID warehouseId;

    public ThroughputDailyId() {
    }

    public ThroughputDailyId(LocalDate date, UUID warehouseId) {
        this.date = date;
        this.warehouseId = warehouseId;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public UUID getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(UUID warehouseId) {
        this.warehouseId = warehouseId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ThroughputDailyId that)) return false;
        return Objects.equals(date, that.date) && Objects.equals(warehouseId, that.warehouseId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(date, warehouseId);
    }
}
