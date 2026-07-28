package com.example.scmplatform.inventoryvisibility.adapter.outbound.persistence.adapter;

import com.example.scmplatform.inventoryvisibility.adapter.outbound.persistence.jpa.InboundExpectationJpaEntity;
import com.example.scmplatform.inventoryvisibility.adapter.outbound.persistence.jpa.InboundExpectationJpaRepository;
import com.example.scmplatform.inventoryvisibility.domain.expectation.ExpectationStatus;
import com.example.scmplatform.inventoryvisibility.domain.expectation.InboundExpectation;
import com.example.scmplatform.inventoryvisibility.domain.expectation.repository.InboundExpectationRepository;
import com.example.scmplatform.inventoryvisibility.domain.node.NodeId;
import com.example.scmplatform.inventoryvisibility.domain.snapshot.Quantity;
import com.example.scmplatform.inventoryvisibility.domain.snapshot.Sku;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class InboundExpectationRepositoryImpl implements InboundExpectationRepository {

    private final InboundExpectationJpaRepository jpaRepository;

    @Override
    public boolean exists(String tenantId, String sourcePoNumber, Sku sku, NodeId nodeId) {
        return jpaRepository.existsByTenantIdAndSourcePoNumberAndSkuAndNodeId(
                tenantId, sourcePoNumber, sku.value(), nodeId.toString());
    }

    @Override
    public List<InboundExpectation> findOpenByNodeAndSku(NodeId nodeId, Sku sku, String tenantId) {
        return jpaRepository.findByNodeIdAndSkuAndTenantIdAndStatus(
                        nodeId.toString(), sku.value(), tenantId,
                        InboundExpectationJpaEntity.ExpectationStatusJpa.OPEN)
                .stream().map(this::toDomain).toList();
    }

    @Override
    public InboundExpectation save(InboundExpectation expectation) {
        return toDomain(jpaRepository.save(toEntity(expectation)));
    }

    private InboundExpectation toDomain(InboundExpectationJpaEntity e) {
        return InboundExpectation.reconstitute(
                e.getId(),
                e.getTenantId(),
                NodeId.of(ReadModelIds.requireUuid(e.getNodeId(), "inbound_expectations.node_id")),
                Sku.of(e.getSku()),
                Quantity.of(e.getExpectedQuantity()),
                e.getSourcePoId(),
                e.getSourcePoNumber(),
                e.getExpectedAt(),
                ExpectationStatus.valueOf(e.getStatus().name()),
                e.getCreatedAt(),
                e.getUpdatedAt(),
                e.getSatisfiedAt());
    }

    private InboundExpectationJpaEntity toEntity(InboundExpectation d) {
        InboundExpectationJpaEntity e = new InboundExpectationJpaEntity();
        e.setId(d.getId());
        e.setTenantId(d.getTenantId());
        e.setNodeId(d.getNodeId().toString());
        e.setSku(d.getSku().value());
        e.setExpectedQuantity(d.getExpectedQuantity().value());
        e.setSourcePoId(d.getSourcePoId());
        e.setSourcePoNumber(d.getSourcePoNumber());
        e.setExpectedAt(d.getExpectedAt());
        e.setStatus(InboundExpectationJpaEntity.ExpectationStatusJpa.valueOf(d.getStatus().name()));
        e.setCreatedAt(d.getCreatedAt());
        e.setUpdatedAt(d.getUpdatedAt());
        e.setSatisfiedAt(d.getSatisfiedAt());
        return e;
    }
}
