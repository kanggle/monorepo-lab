package com.example.promotion.infrastructure.persistence.repository;

import com.example.promotion.infrastructure.persistence.entity.PromotionJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface PromotionJpaRepository extends JpaRepository<PromotionJpaEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PromotionJpaEntity p WHERE p.promotionId = :promotionId")
    Optional<PromotionJpaEntity> findByIdForUpdate(String promotionId);

    @Query("SELECT p FROM PromotionJpaEntity p WHERE p.startDate <= :now AND p.endDate >= :now")
    Page<PromotionJpaEntity> findActive(Instant now, Pageable pageable);

    @Query("SELECT p FROM PromotionJpaEntity p WHERE p.startDate > :now")
    Page<PromotionJpaEntity> findScheduled(Instant now, Pageable pageable);

    @Query("SELECT p FROM PromotionJpaEntity p WHERE p.endDate < :now")
    Page<PromotionJpaEntity> findEnded(Instant now, Pageable pageable);
}
