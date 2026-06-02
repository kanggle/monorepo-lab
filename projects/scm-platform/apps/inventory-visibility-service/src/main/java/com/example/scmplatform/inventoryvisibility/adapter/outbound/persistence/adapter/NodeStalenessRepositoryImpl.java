package com.example.scmplatform.inventoryvisibility.adapter.outbound.persistence.adapter;

import com.example.scmplatform.inventoryvisibility.adapter.outbound.persistence.jpa.NodeStalenessJpaEntity;
import com.example.scmplatform.inventoryvisibility.adapter.outbound.persistence.jpa.NodeStalenessJpaRepository;
import com.example.scmplatform.inventoryvisibility.domain.node.NodeId;
import com.example.scmplatform.inventoryvisibility.domain.staleness.NodeStaleness;
import com.example.scmplatform.inventoryvisibility.domain.staleness.StalenessStatus;
import com.example.scmplatform.inventoryvisibility.domain.staleness.repository.NodeStalenessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class NodeStalenessRepositoryImpl implements NodeStalenessRepository {

    private final NodeStalenessJpaRepository jpaRepository;

    @Override
    public Optional<NodeStaleness> findByNodeId(NodeId nodeId) {
        return jpaRepository.findById(nodeId.toString()).map(this::toDomain);
    }

    @Override
    public List<NodeStaleness> findAllByTenantId(String tenantId) {
        return jpaRepository.findAllByTenantId(tenantId).stream()
                .map(this::toDomain).toList();
    }

    @Override
    public NodeStaleness save(NodeStaleness staleness) {
        return toDomain(jpaRepository.save(toEntity(staleness)));
    }

    private NodeStaleness toDomain(NodeStalenessJpaEntity e) {
        UUID lastEventId = e.getLastEventId() != null
                ? ReadModelIds.requireUuid(e.getLastEventId(), "node_staleness.last_event_id")
                : null;
        return new NodeStaleness(
                NodeId.of(ReadModelIds.requireUuid(e.getNodeId(), "node_staleness.node_id")),
                e.getTenantId(),
                e.getLastEventAt(),
                lastEventId,
                StalenessStatus.valueOf(e.getStalenessStatus().name()),
                e.getLastCheckedAt()
        );
    }

    private NodeStalenessJpaEntity toEntity(NodeStaleness ns) {
        NodeStalenessJpaEntity e = new NodeStalenessJpaEntity();
        e.setNodeId(ns.getNodeId().toString());
        e.setTenantId(ns.getTenantId());
        e.setLastEventAt(ns.getLastEventAt());
        e.setLastEventId(ns.getLastEventId() != null ? ns.getLastEventId().toString() : null);
        e.setStalenessStatus(NodeStalenessJpaEntity.StalenessStatusJpa.valueOf(
                ns.getStalenessStatus().name()));
        e.setLastCheckedAt(ns.getLastCheckedAt());
        return e;
    }
}
