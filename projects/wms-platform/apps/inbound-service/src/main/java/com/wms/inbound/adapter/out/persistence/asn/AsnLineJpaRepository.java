package com.wms.inbound.adapter.out.persistence.asn;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AsnLineJpaRepository extends JpaRepository<AsnLineJpaEntity, UUID> {

    List<AsnLineJpaEntity> findByAsnIdOrderByLineNoAsc(UUID asnId);

    void deleteByAsnId(UUID asnId);
}
