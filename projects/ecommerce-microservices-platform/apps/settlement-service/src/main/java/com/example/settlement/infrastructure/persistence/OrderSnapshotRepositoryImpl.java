package com.example.settlement.infrastructure.persistence;

import com.example.settlement.domain.model.OrderSnapshot;
import com.example.settlement.domain.model.OrderSnapshotLine;
import com.example.settlement.domain.repository.OrderSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class OrderSnapshotRepositoryImpl implements OrderSnapshotRepository {

    private final OrderSnapshotJpaRepository jpaRepository;

    @Override
    public void upsert(OrderSnapshot snapshot) {
        List<OrderSnapshotLineJpaEntity> lineEntities = snapshot.lines().stream()
                .map(l -> OrderSnapshotLineJpaEntity.of(l.sellerId(), l.grossMinor()))
                .toList();

        OrderSnapshotJpaEntity entity = jpaRepository.findById(snapshot.orderId())
                .orElseGet(() -> OrderSnapshotJpaEntity.of(snapshot.orderId(), snapshot.tenantId()));
        entity.setTenantId(snapshot.tenantId());
        entity.replaceLines(lineEntities);
        jpaRepository.save(entity);
    }

    @Override
    public Optional<OrderSnapshot> findByOrderId(String orderId) {
        return jpaRepository.findById(orderId).map(this::toDomain);
    }

    private OrderSnapshot toDomain(OrderSnapshotJpaEntity e) {
        List<OrderSnapshotLine> lines = e.getLines().stream()
                .map(l -> new OrderSnapshotLine(l.getSellerId(), l.getGrossMinor()))
                .toList();
        return new OrderSnapshot(e.getOrderId(), e.getTenantId(), lines);
    }
}
