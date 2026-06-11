package com.example.scmplatform.inventoryvisibility.adapter.outbound.persistence.adapter;

import com.example.scmplatform.inventoryvisibility.adapter.outbound.persistence.jpa.InventorySnapshotJpaEntity;
import com.example.scmplatform.inventoryvisibility.adapter.outbound.persistence.jpa.InventorySnapshotJpaRepository;
import com.example.scmplatform.inventoryvisibility.domain.node.NodeId;
import com.example.scmplatform.inventoryvisibility.domain.snapshot.InventorySnapshot;
import com.example.scmplatform.inventoryvisibility.domain.snapshot.Quantity;
import com.example.scmplatform.inventoryvisibility.domain.snapshot.Sku;
import com.example.scmplatform.inventoryvisibility.domain.snapshot.SnapshotId;
import com.example.scmplatform.inventoryvisibility.domain.snapshot.repository.InventorySnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class InventorySnapshotRepositoryImpl implements InventorySnapshotRepository {

    private final InventorySnapshotJpaRepository jpaRepository;

    @Override
    public Optional<InventorySnapshot> findById(SnapshotId id) {
        return jpaRepository.findById(id.toString()).map(this::toDomain);
    }

    @Override
    public Optional<InventorySnapshot> findByNodeIdAndSku(NodeId nodeId, Sku sku, String tenantId) {
        return jpaRepository.findByNodeIdAndSkuAndTenantId(nodeId.toString(), sku.value(), tenantId)
                .map(this::toDomain);
    }

    @Override
    public List<InventorySnapshot> findByNodeId(NodeId nodeId, String tenantId) {
        return jpaRepository.findByNodeIdAndTenantId(nodeId.toString(), tenantId)
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<InventorySnapshot> findBySku(Sku sku, String tenantId) {
        return jpaRepository.findBySkuAndTenantId(sku.value(), tenantId)
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<InventorySnapshot> findAll(String tenantId, int page, int size) {
        return jpaRepository.findAllByTenantId(tenantId, PageRequest.of(page, size))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<InventorySnapshot> findAllAcrossTenants() {
        return jpaRepository.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public long countAll(String tenantId) {
        return jpaRepository.countByTenantId(tenantId);
    }

    @Override
    public InventorySnapshot save(InventorySnapshot snapshot) {
        return toDomain(jpaRepository.save(toEntity(snapshot)));
    }

    private InventorySnapshot toDomain(InventorySnapshotJpaEntity e) {
        return new InventorySnapshot(
                SnapshotId.of(ReadModelIds.requireUuid(e.getId(), "inventory_snapshots.id")),
                NodeId.of(ReadModelIds.requireUuid(e.getNodeId(), "inventory_snapshots.node_id")),
                Sku.of(e.getSku()),
                e.getTenantId(),
                Quantity.of(e.getQuantity()),
                ReadModelIds.requireUuid(e.getLastEventId(), "inventory_snapshots.last_event_id"),
                e.getLastEventAt(),
                e.getVersion(),
                e.getUpdatedAt()
        );
    }

    private InventorySnapshotJpaEntity toEntity(InventorySnapshot s) {
        InventorySnapshotJpaEntity e = new InventorySnapshotJpaEntity();
        e.setId(s.getId().toString());
        e.setNodeId(s.getNodeId().toString());
        e.setSku(s.getSku().value());
        e.setTenantId(s.getTenantId());
        e.setQuantity(s.getQuantity().value());
        e.setLastEventId(s.getLastEventId().toString());
        e.setLastEventAt(s.getLastEventAt());
        e.setVersion(s.getVersion());
        e.setUpdatedAt(s.getUpdatedAt());
        return e;
    }
}
