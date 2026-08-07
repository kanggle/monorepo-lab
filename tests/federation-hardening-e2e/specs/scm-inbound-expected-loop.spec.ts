import { randomUUID } from 'node:crypto';

import { test, expect, type BrowserContext } from '@playwright/test';
import { Kafka } from 'kafkajs';

import { switchTenant } from '../fixtures/console-helpers';
import { loginAsMultiOperator } from '../fixtures/login';

/**
 * TASK-SCM-INT-004 leg 2 — inbound-expected loop federation live proof (ADR-MONO-050 §3/§5).
 *
 * The DETERMINISTIC, PR-gated authority is the two legs:
 *   - scm producer: scm `tests/e2e` InboundExpectedLoopE2ETest (real alert →
 *     demand-planning → approve → procurement PO → CONFIRMED → asserts the
 *     byte-exact scm.procurement.inbound-expected.v1 envelope with CODE ids +
 *     decimal-string qty);
 *   - wms consumer: wms inbound-service ScmInboundExpectedConsumerIT (injects the
 *     byte-exact envelope onto a real broker → asserts Asn(CREATED, SCM_PROCUREMENT)
 *     against real Postgres, incl. dedup / fail-closed / 3PL / cancel).
 *
 * This leg is the NIGHTLY federation live demonstration: it runs the REAL wms
 * inbound-service consumer inside the cross-product federation stack.
 *
 * Shape (mirrors the ADR-027 replenishment leg's inject pattern):
 *   seed shared CODES into inbound-service read-model (workflow psql, seed-wms-inbound.sql)
 *     → publish the canonical `scm.procurement.inbound-expected.v1` envelope to the
 *       REAL broker (the exact shape scm procurement emits on CONFIRMED —
 *       producer fidelity is guarded by the scm-side PR-gated leg)
 *       → the REAL wms inbound-service consumer creates an Asn(CREATED,
 *         source=SCM_PROCUREMENT) at the resolved warehouse
 *           → assert the ASN appears via the inbound AsnController list endpoint.
 *
 * WHY INJECT (not drive scm to CONFIRMED): driving the scm PO to CONFIRMED in the
 * federation stack requires the supplier-adapter mock (SUBMIT → supplier call),
 * which the base stack does not run — exactly why the replenishment leg injects
 * the canonical wms alert rather than role-gating a real wms mutation. The scm
 * producer half is proven by the PR-gated scm leg; this proves the wms half live.
 *
 * AUTH — why an assume-tenant token, NOT the suite SUPER_ADMIN wildcard (TASK-MONO-432):
 * the AsnController GET is @PreAuthorize hasRole('INBOUND_READ'), behind the wms
 * TenantClaimValidator. wms is the ONE platform that deliberately REJECTS the
 * SUPER_ADMIN tenant_id='*' wildcard (ADR-MONO-019/048 § D5; WmsTenantGatePolicyTest
 * asserts the refusal — do not widen it). So the suite-wide SUPER_ADMIN storageState
 * token (tenant_id='*', no entitled_domains) 403s at the tenant gate before any role
 * check — which is exactly what the first workflow_dispatch of this wired leg surfaced.
 *
 * The token wms ACCEPTS is an assume-tenant token for a wms-ENTITLED tenant: its
 * signed entitled_domains carries `wms` (TenantClaimValidator entitlement-trust dual-
 * accept), and OperatorRoleDerivation grants that same wms entitlement the
 * INBOUND_READ operator role. So this spec overrides the suite storageState, logs in
 * fresh as the multi-assignment operator, and drives the real /api/tenant assume-tenant
 * exchange into `acme-corp` ([finance,wms]) — the production read path a real wms
 * operator uses. Mirrors subscription-plane-separation.spec.ts. The deterministic
 * wms-side IT is unaffected (it asserts at the DB layer, bypassing the read authz).
 */

// Override the suite-wide SUPER_ADMIN storageState — the operator logs in fresh and
// assumes a wms-entitled tenant (see the AUTH note above).
test.use({ storageState: { cookies: [], origins: [] } });

test.describe('scm inbound-expected loop — federation live (ADR-MONO-050 leg 2)', () => {
  // Nightly-only, not PR-gated; tolerate the documented federation flake classes
  // (Docker Hub image pull, consumer warm-up) with bounded retries.
  test.describe.configure({ retries: 2 });
  test.setTimeout(150_000);

  const WMS_INBOUND_BASE =
    process.env.E2E_WMS_INBOUND_BASE_URL ?? 'http://localhost:18101';
  const REDPANDA_BROKER =
    process.env.E2E_REDPANDA_BROKER ?? 'localhost:19092';
  const INBOUND_EXPECTED_TOPIC = 'scm.procurement.inbound-expected.v1';

  // The wms-ENTITLED tenant whose assume-tenant token carries INBOUND_READ
  // (entitled_domains ∋ wms → OperatorRoleDerivation grants WMS_OPERATOR + INBOUND_*).
  const WMS_ENTITLED_TENANT = 'acme-corp';
  const ASSUMED_COOKIE = 'console_assumed_token';

  // Shared CODES — MUST match fixtures/seed-wms-inbound.sql.
  const WAREHOUSE_CODE = 'WH-FED-IE';
  const SUPPLIER_CODE = 'SUP-FED-IE';
  const SKU_CODE = 'SKU-FED-IE';

  async function readCookie(
    ctx: BrowserContext,
    name: string,
  ): Promise<string | undefined> {
    const all = await ctx.cookies();
    return all.find((c) => c.name === name)?.value;
  }

  test('real broker scm inbound-expected -> wms inbound-service creates the Asn expectation', async ({
    browser,
  }) => {
    const ctx = await browser.newContext({
      storageState: { cookies: [], origins: [] },
    });
    try {
      // ---- assume-tenant token for a wms-entitled tenant (carries INBOUND_READ) ----
      // Production-identical: fresh OIDC login → real /api/tenant assume-tenant
      // exchange → the re-scoped domain-facing token wms accepts (entitlement-trust)
      // and authorizes (INBOUND_READ). NOT the suite SUPER_ADMIN wildcard (403 on wms).
      await loginAsMultiOperator(ctx);
      await switchTenant(ctx, WMS_ENTITLED_TENANT);
      const token = await readCookie(ctx, ASSUMED_COOKIE);
      expect(
        token,
        `assume-tenant token for ${WMS_ENTITLED_TENANT} (entitled_domains ∋ wms → INBOUND_READ)`,
      ).toBeTruthy();
      const authed = { Authorization: `Bearer ${token}` };

      // Unique poNumber per run → run isolation (fresh DB per nightly run).
      const poNumber = `SCM-PO-FED-${randomUUID().slice(0, 8).toUpperCase()}`;
      const poId = randomUUID();

      // ---- publish the canonical scm inbound-expected envelope to the REAL broker ----
      // Byte-exact OutboxProcurementEventPublisher shape: 7-field envelope, payload
      // identifiers are CODES, expectedQty a plain DECIMAL STRING (ADR-050 D9).
      const kafka = new Kafka({
        clientId: 'fed-e2e-scm-inbound-expected-producer',
        brokers: [REDPANDA_BROKER],
        retry: { retries: 8 },
      });
      const producer = kafka.producer();
      await producer.connect();
      try {
        const envelope = {
          eventId: randomUUID(),
          eventType: 'scm.procurement.inbound-expected',
          source: 'scm-platform-procurement-service',
          occurredAt: new Date().toISOString(),
          schemaVersion: 1,
          partitionKey: poId,
          payload: {
            poId,
            poNumber,
            supplierId: SUPPLIER_CODE,
            destinationWarehouseId: WAREHOUSE_CODE,
            destinationNodeType: 'WMS_WAREHOUSE',
            expectedArrivalDate: '2026-07-24',
            currency: 'KRW',
            lines: [{ skuCode: SKU_CODE, expectedQty: '100', uom: 'EA' }],
          },
        };
        await producer.send({
          topic: INBOUND_EXPECTED_TOPIC,
          messages: [{ key: poId, value: JSON.stringify(envelope) }],
        });
      } finally {
        await producer.disconnect();
      }

      // ---- the REAL wms inbound-service creates the Asn expectation ----
      // Poll the inbound AsnController list endpoint until a SCM_PROCUREMENT ASN
      // appears in CREATED. The list summary does not carry poNumber, so match on
      // source+status; the nightly DB is fresh per run and this spec is the only
      // injector of an scm inbound-expected event, so a CREATED SCM_PROCUREMENT ASN
      // is unambiguously the one this test drove (poId/poNumber logged for triage).
      //
      // Envelope: `specs/contracts/http/inbound-service-api.md` §Pagination —
      // `{ content, page:{number,size,totalElements,totalPages}, sort }`.
      // It is NOT `{ items, ... }`: TASK-BE-568 (ADR-MONO-058 D3) replaced the
      // hand-rolled `PagedResponse(items, page, size, total)` with the shared
      // carrier, and this reader kept asking for `items`. `body.items` then read
      // `undefined` on every poll, so the predicate was false for the full 60s —
      // and the failure message still said "wms inbound-service creates an Asn",
      // i.e. a READER bug wearing the producer's name. It reddened the nightly
      // for 7 consecutive days (TASK-MONO-516) while wms was, in fact, creating
      // the ASN on every single attempt.
      //
      // Hence `lastDiagnostic`: every non-match now records WHY, and the timeout
      // reports it. A wrong status, a renamed envelope and a genuinely absent row
      // are three different failures — they must not share one sentence.
      let lastDiagnostic = 'no request completed';
      const asnPath = `${WMS_INBOUND_BASE}/api/v1/inbound/asns?status=CREATED&size=100`;
      try {
        await expect
          .poll(
            async () => {
              const resp = await ctx.request.get(asnPath, { headers: authed });
              if (resp.status() !== 200) {
                // An auth/routing failure is "could not ask", never "not there".
                lastDiagnostic =
                  `HTTP ${resp.status()} from the ASN list — ` +
                  `body=${(await resp.text()).slice(0, 200)}`;
                return false;
              }
              const body = await resp.json();
              const rows = (body as { content?: unknown }).content;
              if (!Array.isArray(rows)) {
                // Contract drift: 200 OK, but not the envelope we contracted for.
                lastDiagnostic =
                  `200 OK but no \`content\` array (§Pagination) — ` +
                  `envelope keys=[${Object.keys(body ?? {}).join(', ')}]`;
                return false;
              }
              const match = (rows as { source?: string; status?: string }[]).some(
                (a) => a.source === 'SCM_PROCUREMENT' && a.status === 'CREATED',
              );
              if (!match) {
                lastDiagnostic =
                  `200 OK, content=${rows.length} row(s), none with ` +
                  `source=SCM_PROCUREMENT status=CREATED — sources=[${
                    (rows as { source?: string }[])
                      .map((a) => a.source ?? '?')
                      .join(', ') || 'none'
                  }]`;
              }
              return match;
            },
            {
              message: `wms inbound-service creates an Asn(CREATED, SCM_PROCUREMENT) — poNumber=${poNumber} poId=${poId}`,
              timeout: 60_000,
              intervals: [1_000],
            },
          )
          .toBe(true);
      } catch (err) {
        throw new Error(
          `wms inbound-service did not surface an Asn(CREATED, SCM_PROCUREMENT) ` +
            `— poNumber=${poNumber} poId=${poId}\n` +
            `last observed state: ${lastDiagnostic}\n` +
            `(if this reports a renamed envelope or a non-200, the wms consumer ` +
            `may well be healthy and THIS reader is the defect — check the ` +
            `inbound-service log for \`scm_inbound_expected_created\` before ` +
            `blaming the consumer)\n${(err as Error).message}`,
        );
      }
    } finally {
      await ctx.close();
    }
  });
});
