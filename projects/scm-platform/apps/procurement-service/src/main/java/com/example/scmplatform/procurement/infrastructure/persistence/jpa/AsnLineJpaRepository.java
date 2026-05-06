package com.example.scmplatform.procurement.infrastructure.persistence.jpa;

import com.example.scmplatform.procurement.domain.asn.AsnLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AsnLineJpaRepository extends JpaRepository<AsnLine, String> {

    List<AsnLine> findByAsnId(String asnId);
}
