import { randomUUID } from 'node:crypto';

import { test, expect } from '@playwright/test';
import { Kafka } from 'kafkajs';

/**
 * TASK-SCM-INT-002 leg 2 — replenishment-loop federation live proof (ADR-MONO-027 §5).
 *
 * The DETERMINISTIC, PR-gated authority is leg 1 (scm `tests/e2e`
 * ReplenishmentLoopE2ETest — Testcontainers, real Kafka + real demand-planning +
 * real procurement, the exact wms envelope via KafkaTestProducer). This leg is
 * the NIGHTLY federation live demonstration: it runs the REAL scm
 * demand-planning consumer + procurement inside the cross-product federation
 * stack and drives the loop with a PRODUCTION IAM operator token.
 *
 * Shape (ADR-022 Option-B live-proof shape, adapted):
 *
 *   operator seeds reorder policy + supplier mapping (REST, production token)
 *     → a canonical `inventory.low-stock-detected` envelope is published to the
 *       REAL broker (the exact shape wms emits — wms inventory-events.md §7;
 *       producer-envelope fidelity is guarded by leg-1's KafkaTestProducer)
 *       → the REAL demand-planning consumer raises a SUGGESTED suggestion
 *         → operator approves (production `console_operator_token`, tenant_id='*')
 *           → procurement DRAFT PO (origin=DEMAND_PLANNING, sourceSuggestionId)
 *
 * ZERO wms change (ADR-027 §D1/§5): wms-inventory-service is not booted. The
 * only path that triggers wms low-stock detection is a role-gated REST mutation
 * (ROLE_INVENTORY_WRITE) which the production IAM token model never mints, so a
 * committed nightly spec injects the canonical envelope rather than relaxing wms
 * authz. The loop's wms→scm contract fidelity is asserted by leg 1; this leg
 * asserts the scm side + the production-token operator approve end-to-end.
 *
 * Auth: demand-planning + procurement have no fine-grained role gate (just an
 * authenticated, tenant `scm`/`*`/entitled token). The seeded SUPER_ADMIN's
 * `console_operator_token` (tenant_id='*') is accepted by both producers
 * (login.ts: the wildcard token is accepted by all producers). The approve
 * propagates that bearer to procurement (intra-scm trust, ADR-027 D5), so the
 * DRAFT PO is created under the same tenant the GET reads it under.
 */
test.describe('scm replenishment loop — federation live (ADR-MONO-027 leg 2)', () => {
  // Nightly-only, not PR-gated; tolerate the documented federation flake classes
  // (Docker Hub image pull, consumer warm-up) with bounded retries.
  test.describe.configure({ retries: 2 });
  test.setTimeout(150_000);

  const SCM_DP_BASE =
    process.env.E2E_SCM_DP_BASE_URL ?? 'http://localhost:18100';
  const SCM_BASE = process.env.E2E_SCM_BASE_URL ?? 'http://localhost:18092';
  const REDPANDA_BROKER =
    process.env.E2E_REDPANDA_BROKER ?? 'localhost:19092';
  const ALERT_TOPIC = 'wms.inventory.alert.v1';
  const OPERATOR_COOKIE = 'console_operator_token';

  test('real broker wms alert -> demand-planning suggestion -> approve -> procurement DRAFT PO', async ({
    page,
  }) => {
    // ---- production operator token (SUPER_ADMIN, tenant_id='*') ----
    const cookies = await page.context().cookies();
    const token = cookies.find((c) => c.name === OPERATOR_COOKIE)?.value;
    expect(token, `${OPERATOR_COOKIE} cookie present`).toBeTruthy();
    const authed = {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    };

    // Unique SKU per run → run isolation (the suggestion + PO are identifiable
    // regardless of the static `scm` suggestion tenant; fresh DB per nightly run).
    const sku = `SKU-FED-REPLEN-${randomUUID().slice(0, 8)}`;
    const warehouseId = randomUUID();
    const supplierId = randomUUID();

    // ---- 1) operator seeds supplier mapping + reorder policy (REST PUT) ----
    const mapResp = await page.request.put(
      `${SCM_DP_BASE}/api/demand-planning/sku-supplier-map/${sku}`,
      {
        headers: authed,
        data: {
          supplierId,
          defaultOrderQty: 100,
          leadTimeDays: 7,
          currency: 'KRW',
        },
      },
    );
    expect(
      mapResp.status(),
      `PUT sku-supplier-map -> 200 (body: ${await mapResp.text()})`,
    ).toBe(200);

    const policyResp = await page.request.put(
      `${SCM_DP_BASE}/api/demand-planning/policies/${sku}`,
      {
        headers: authed,
        data: { reorderPoint: 10, safetyStock: 5, reorderQty: 50 },
      },
    );
    expect(
      policyResp.status(),
      `PUT policy -> 200 (body: ${await policyResp.text()})`,
    ).toBe(200);

    // ---- 2) publish the canonical wms low-stock alert to the REAL broker ----
    // Envelope mirrors KafkaTestProducer.publishLowStockAlert (leg 1) byte-for-
    // byte, which itself mirrors wms inventory-events.md §7. availableQty(3) <
    // reorderPoint(10) crosses the scm reorder policy.
    const kafka = new Kafka({
      clientId: 'fed-e2e-wms-alert-producer',
      brokers: [REDPANDA_BROKER],
      retry: { retries: 8 },
    });
    const producer = kafka.producer();
    await producer.connect();
    try {
      const envelope = {
        eventId: randomUUID(),
        eventType: 'inventory.low-stock-detected',
        eventVersion: 1,
        occurredAt: new Date().toISOString(),
        producer: 'inventory-service',
        aggregateType: 'inventory',
        aggregateId: warehouseId,
        payload: {
          skuId: randomUUID(),
          skuCode: sku,
          locationId: warehouseId,
          locationCode: 'WH-FED-E2E',
          availableQty: 3,
          threshold: 8,
          triggeringEventType: 'inventory.adjusted',
        },
      };
      await producer.send({
        topic: ALERT_TOPIC,
        messages: [{ key: warehouseId, value: JSON.stringify(envelope) }],
      });
    } finally {
      await producer.disconnect();
    }

    // ---- 3) the REAL demand-planning consumer raises a SUGGESTED suggestion ----
    let suggestionId: string | undefined;
    await expect
      .poll(
        async () => {
          const resp = await page.request.get(
            `${SCM_DP_BASE}/api/demand-planning/suggestions?skuCode=${sku}&status=SUGGESTED`,
            { headers: authed },
          );
          if (resp.status() !== 200) return null;
          const body = await resp.json();
          const found = (body.data ?? []).find(
            (s: { skuCode?: string; status?: string; id?: string }) =>
              s.skuCode === sku && s.status === 'SUGGESTED',
          );
          suggestionId = found?.id;
          return suggestionId ?? null;
        },
        {
          message: 'demand-planning raises a SUGGESTED suggestion for the SKU',
          timeout: 60_000,
          intervals: [1_000],
        },
      )
      .toBeTruthy();

    // ---- 4) operator approves -> intra-scm DRAFT PO materialization ----
    const approveResp = await page.request.post(
      `${SCM_DP_BASE}/api/demand-planning/suggestions/${suggestionId}/approve`,
      { headers: authed, data: {} },
    );
    expect(
      approveResp.status(),
      `approve -> 200 (body: ${await approveResp.text()})`,
    ).toBe(200);
    const approveData = (await approveResp.json()).data;
    expect(approveData.status).toBe('MATERIALIZED');
    expect(approveData.poStatus).toBe('DRAFT');
    const poId: string = approveData.poId;
    expect(poId, 'approve returns a linked poId').toBeTruthy();

    // ---- 5) the procurement DRAFT PO carries the provenance — never SUBMITted --
    const poResp = await page.request.get(
      `${SCM_BASE}/api/procurement/po/${poId}`,
      { headers: authed },
    );
    expect(
      poResp.status(),
      `GET procurement PO -> 200 (body: ${await poResp.text()})`,
    ).toBe(200);
    const po = (await poResp.json()).data;
    expect(po.status, 'PO is DRAFT only — never auto-SUBMITted').toBe('DRAFT');
    expect(po.origin).toBe('DEMAND_PLANNING');
    expect(po.sourceSuggestionId).toBe(suggestionId);
  });
});
