package com.wms.inbound.adapter.out.persistence.webhook;

import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ErpWebhookInboxJpaRepository extends JpaRepository<ErpWebhookInboxJpaEntity, String> {

    List<ErpWebhookInboxJpaEntity> findAllByStatusOrderByReceivedAtAsc(String status, Limit limit);
}
