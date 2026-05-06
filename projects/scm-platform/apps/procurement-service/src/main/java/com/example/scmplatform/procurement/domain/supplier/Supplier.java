package com.example.scmplatform.procurement.domain.supplier;

import com.example.scmplatform.procurement.domain.error.SupplierInactiveException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Supplier master (v1 internal master, v2 will migrate to supplier-service).
 *
 * <p>Tenant-scoped — every read carries {@code tenantId}. Status transitions
 * (ACTIVE → INACTIVE / CONTRACT_EXPIRED) are recorded in audit_log per S7.
 */
@Entity
@Table(name = "suppliers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Supplier {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "name", length = 200, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private SupplierStatus status;

    @Column(name = "contract_started_at")
    private Instant contractStartedAt;

    @Column(name = "contract_expires_at")
    private Instant contractExpiresAt;

    @Column(name = "contact_info", columnDefinition = "jsonb")
    private String contactInfoJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public static Supplier create(String id, String tenantId, String name, SupplierStatus status) {
        Supplier s = new Supplier();
        s.id = id;
        s.tenantId = tenantId;
        s.name = name;
        s.status = status;
        Instant now = Instant.now();
        s.createdAt = now;
        s.updatedAt = now;
        return s;
    }

    public void ensureUsableForOrdering() {
        if (status != SupplierStatus.ACTIVE) {
            throw new SupplierInactiveException(
                    "Supplier " + id + " is not usable: status=" + status);
        }
    }
}
