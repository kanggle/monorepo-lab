package com.example.scmplatform.demandplanning.adapter.outbound.persistence.adapter;

import com.example.scmplatform.demandplanning.application.port.outbound.OpsAlertPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Best-effort ops alert via structured log emission.
 * In production, swap for a PagerDuty / Slack / alertmanager adapter.
 * Failure to alert is logged but never propagates (best-effort per architecture.md).
 */
@Slf4j
@Component
public class LoggingOpsAlertAdapter implements OpsAlertPort {

    @Override
    public void alertUnmappedSku(String skuCode, String eventId, String sourceTopic) {
        try {
            log.error("[OPS-ALERT] Unmapped SKU on demand-planning consumer — event routed to DLT. " +
                    "skuCode={} eventId={} sourceTopic={} " +
                    "action=add_sku_supplier_mapping",
                    skuCode, eventId, sourceTopic);
        } catch (Exception e) {
            log.warn("OpsAlert: failed to emit alert for unmapped SKU skuCode={}: {}", skuCode, e.getMessage());
        }
    }
}
