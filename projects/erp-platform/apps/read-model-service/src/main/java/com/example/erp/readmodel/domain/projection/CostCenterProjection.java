package com.example.erp.readmodel.domain.projection;

import com.example.erp.readmodel.domain.common.MasterStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Projection of a {@code costcenter} master (read-only, E5). Maintained by the
 * {@code erp.masterdata.costcenter.changed.v1} consumer as a single-row upsert
 * keyed by {@code id} (= aggregateId). Pure Java — no framework annotations.
 */
public class CostCenterProjection {

    private final String id;
    private String code;
    private String name;
    private String departmentId;
    private MasterStatus status;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private Instant lastEventAt;
    private String lastEventId;

    public CostCenterProjection(String id, String code, String name, String departmentId,
                                MasterStatus status, LocalDate effectiveFrom,
                                LocalDate effectiveTo, Instant lastEventAt,
                                String lastEventId) {
        this.id = Objects.requireNonNull(id, "id");
        this.code = code;
        this.name = name;
        this.departmentId = departmentId;
        this.status = status == null ? MasterStatus.ACTIVE : status;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.lastEventAt = lastEventAt;
        this.lastEventId = lastEventId;
    }

    public static CostCenterProjection of(String id, String code, String name, String departmentId,
                                          MasterStatus status, LocalDate effectiveFrom,
                                          LocalDate effectiveTo, Instant lastEventAt,
                                          String lastEventId) {
        return new CostCenterProjection(id, code, name, departmentId, status,
                effectiveFrom, effectiveTo, lastEventAt, lastEventId);
    }

    public void applyUpsert(String code, String name, String departmentId, MasterStatus status,
                            LocalDate effectiveFrom, LocalDate effectiveTo,
                            Instant lastEventAt, String lastEventId) {
        this.code = code;
        this.name = name;
        this.departmentId = departmentId;
        this.status = status == null ? MasterStatus.ACTIVE : status;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.lastEventAt = lastEventAt;
        this.lastEventId = lastEventId;
    }

    public void applyRetire(LocalDate effectiveTo, Instant lastEventAt, String lastEventId) {
        this.status = MasterStatus.RETIRED;
        if (effectiveTo != null) {
            this.effectiveTo = effectiveTo;
        }
        this.lastEventAt = lastEventAt;
        this.lastEventId = lastEventId;
    }

    public String id() { return id; }
    public String code() { return code; }
    public String name() { return name; }
    public String departmentId() { return departmentId; }
    public MasterStatus status() { return status; }
    public LocalDate effectiveFrom() { return effectiveFrom; }
    public LocalDate effectiveTo() { return effectiveTo; }
    public Instant lastEventAt() { return lastEventAt; }
    public String lastEventId() { return lastEventId; }
}
