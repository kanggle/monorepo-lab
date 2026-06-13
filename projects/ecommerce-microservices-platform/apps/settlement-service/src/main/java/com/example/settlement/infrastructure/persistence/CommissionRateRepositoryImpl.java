package com.example.settlement.infrastructure.persistence;

import com.example.settlement.domain.repository.CommissionRateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CommissionRateRepositoryImpl implements CommissionRateRepository {

    private final CommissionRateJpaRepository jpaRepository;

    @Override
    public Optional<Integer> findRateBps(String tenantId, String sellerId) {
        return jpaRepository.findByTenantIdAndSellerId(tenantId, sellerId)
                .map(CommissionRateJpaEntity::getRateBps);
    }

    @Override
    public void upsert(String tenantId, String sellerId, int rateBps) {
        jpaRepository.findByTenantIdAndSellerId(tenantId, sellerId)
                .ifPresentOrElse(
                        existing -> existing.updateRate(rateBps),
                        () -> jpaRepository.save(CommissionRateJpaEntity.of(tenantId, sellerId, rateBps)));
    }
}
