package com.example.scmplatform.inventoryvisibility.adapter.outbound.persistence.adapter;

import com.example.scmplatform.inventoryvisibility.adapter.outbound.persistence.jpa.InventoryNodeJpaEntity;
import com.example.scmplatform.inventoryvisibility.adapter.outbound.persistence.jpa.InventoryNodeJpaRepository;
import com.example.scmplatform.inventoryvisibility.domain.node.InventoryNode;
import com.example.scmplatform.inventoryvisibility.domain.node.NodeId;
import com.example.scmplatform.inventoryvisibility.domain.node.NodeStatus;
import com.example.scmplatform.inventoryvisibility.domain.node.NodeType;
import com.example.scmplatform.inventoryvisibility.domain.node.repository.InventoryNodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class InventoryNodeRepositoryImpl implements InventoryNodeRepository {

    private final InventoryNodeJpaRepository jpaRepository;

    @Override
    public Optional<InventoryNode> findById(NodeId id) {
        return jpaRepository.findById(id.toString()).map(this::toDomain);
    }

    @Override
    public Optional<InventoryNode> findByTenantIdAndExternalId(String tenantId, String externalId) {
        return jpaRepository.findByTenantIdAndNodeExternalId(tenantId, externalId)
                .map(this::toDomain);
    }

    @Override
    public List<InventoryNode> findAllByTenantId(String tenantId) {
        return jpaRepository.findAllByTenantId(tenantId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public InventoryNode save(InventoryNode node) {
        InventoryNodeJpaEntity entity = toEntity(node);
        return toDomain(jpaRepository.save(entity));
    }

    private InventoryNode toDomain(InventoryNodeJpaEntity e) {
        return new InventoryNode(
                NodeId.of(UUID.fromString(e.getId())),
                e.getTenantId(),
                e.getNodeExternalId(),
                NodeType.valueOf(e.getNodeType().name()),
                e.getName(),
                NodeStatus.valueOf(e.getStatus().name()),
                e.getContactInfo(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }

    private InventoryNodeJpaEntity toEntity(InventoryNode n) {
        InventoryNodeJpaEntity e = new InventoryNodeJpaEntity();
        e.setId(n.getId().toString());
        e.setTenantId(n.getTenantId());
        e.setNodeExternalId(n.getNodeExternalId());
        e.setNodeType(InventoryNodeJpaEntity.NodeTypeJpa.valueOf(n.getNodeType().name()));
        e.setName(n.getName());
        e.setStatus(InventoryNodeJpaEntity.NodeStatusJpa.valueOf(n.getStatus().name()));
        e.setContactInfo(n.getContactInfo());
        e.setCreatedAt(n.getCreatedAt());
        e.setUpdatedAt(n.getUpdatedAt());
        return e;
    }
}
