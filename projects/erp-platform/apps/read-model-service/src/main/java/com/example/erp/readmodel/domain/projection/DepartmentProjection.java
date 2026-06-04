package com.example.erp.readmodel.domain.projection;

import com.example.erp.readmodel.domain.common.MasterStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Projection of a {@code department} master (read-only, E5). Maintained by the
 * {@code erp.masterdata.department.changed.v1} consumer as a single-row upsert
 * keyed by {@code id} (= aggregateId). {@code parentId} drives the read-time
 * ancestry walk that resolves the employee org-view department path.
 *
 * <p>Pure Java — no framework annotations (Hexagonal domain). Mutation is
 * limited to {@link #applyUpsert} (CREATED/UPDATED/PARENT_MOVED) and
 * {@link #applyRetire} (RETIRED → logical retire, row retained).
 */
public class DepartmentProjection {

    private final String id;
    private String code;
    private String name;
    private String parentId;
    private MasterStatus status;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private Instant lastEventAt;
    private String lastEventId;

    public DepartmentProjection(String id, String code, String name, String parentId,
                                MasterStatus status, LocalDate effectiveFrom,
                                LocalDate effectiveTo, Instant lastEventAt,
                                String lastEventId) {
        this.id = Objects.requireNonNull(id, "id");
        this.code = code;
        this.name = name;
        this.parentId = parentId;
        this.status = status == null ? MasterStatus.ACTIVE : status;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.lastEventAt = lastEventAt;
        this.lastEventId = lastEventId;
    }

    /** Factory for a CREATED/UPDATED/PARENT_MOVED upsert from the event {@code after}. */
    public static DepartmentProjection of(String id, String code, String name, String parentId,
                                          MasterStatus status, LocalDate effectiveFrom,
                                          LocalDate effectiveTo, Instant lastEventAt,
                                          String lastEventId) {
        return new DepartmentProjection(id, code, name, parentId, status,
                effectiveFrom, effectiveTo, lastEventAt, lastEventId);
    }

    /**
     * Applies a CREATED/UPDATED/PARENT_MOVED upsert: latest-wins on all business
     * fields (including {@code parentId} for PARENT_MOVED). Idempotent — applying
     * the same {@code after} twice yields the same state.
     */
    public void applyUpsert(String code, String name, String parentId, MasterStatus status,
                            LocalDate effectiveFrom, LocalDate effectiveTo,
                            Instant lastEventAt, String lastEventId) {
        this.code = code;
        this.name = name;
        this.parentId = parentId;
        this.status = status == null ? MasterStatus.ACTIVE : status;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.lastEventAt = lastEventAt;
        this.lastEventId = lastEventId;
    }

    /**
     * Applies a RETIRED change: logical retire only — the row is retained with
     * {@code status = RETIRED} and {@code effectiveTo} set (E2; never a delete).
     */
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
    public String parentId() { return parentId; }
    public MasterStatus status() { return status; }
    public LocalDate effectiveFrom() { return effectiveFrom; }
    public LocalDate effectiveTo() { return effectiveTo; }
    public Instant lastEventAt() { return lastEventAt; }
    public String lastEventId() { return lastEventId; }
}
