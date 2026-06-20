package com.example.settlement.infrastructure.persistence;

import com.example.settlement.domain.payout.SellerPayout;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Spring Data repository for the {@link SellerPayout} aggregate. */
public interface SellerPayoutJpaRepository extends JpaRepository<SellerPayout, String> {

    List<SellerPayout> findByPeriodId(String periodId);
}
