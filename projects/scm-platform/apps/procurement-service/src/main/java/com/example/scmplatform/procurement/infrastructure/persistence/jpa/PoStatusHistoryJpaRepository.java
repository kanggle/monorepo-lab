package com.example.scmplatform.procurement.infrastructure.persistence.jpa;

import com.example.scmplatform.procurement.domain.po.status.PoStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PoStatusHistoryJpaRepository extends JpaRepository<PoStatusHistory, Long> {
}
