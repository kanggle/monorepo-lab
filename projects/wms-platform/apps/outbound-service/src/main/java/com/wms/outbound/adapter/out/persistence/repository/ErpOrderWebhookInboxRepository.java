package com.wms.outbound.adapter.out.persistence.repository;

import com.wms.outbound.adapter.out.persistence.entity.ErpOrderWebhookInbox;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ErpOrderWebhookInboxRepository extends JpaRepository<ErpOrderWebhookInbox, UUID> {

    List<ErpOrderWebhookInbox> findAllByStatusOrderByReceivedAtAsc(String status, Limit limit);
}
