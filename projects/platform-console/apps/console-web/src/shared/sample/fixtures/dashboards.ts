import { SAMPLE_AS_OF } from '../codes';
import { IAM_FIXTURE_HANDLERS } from './iam';
import { ERP_FIXTURE_HANDLERS } from './erp';
import { ECOMMERCE_FIXTURE_HANDLERS } from './ecommerce';
import { LEDGER_FIXTURE_HANDLERS } from './ledger';
import { FINANCE_FIXTURE_HANDLERS } from './finance';
import { WMS_FIXTURE_HANDLERS } from './wms';
import { SCM_FIXTURE_HANDLERS } from './scm';

/**
 * The sample operator overview (`/` → `/dashboards/overview`, the FIRST screen
 * an anonymous visitor lands on).
 *
 * TASK-PC-FE-285 (coordinator review) — an overview card's number is NOT typed
 * here. Each card's `data` is the answer the SAME domain fixture gives to the
 * SAME request the overview composition leg makes, so the landing card and the
 * list screen it summarises cannot disagree. Before 285 the cards said
 * IAM 128 · ERP 12 · E-Commerce 342 while the lists held 5 · 3 · 3.
 *
 * TASK-PC-FE-295 — `data` is the PRODUCER'S OWN response body, because that is
 * what the real leg carries (`console-composition.ts` loads it verbatim). A
 * sample body in a shape the card schemas happen to read, but the wire never
 * sends, is how three cards were blank for logged-in operators while the sample
 * world looked fine.
 *
 * TASK-PC-FE-364 — the six legs became business figures (locked accounts ·
 * unshipped orders · draft POs · open reconciliation discrepancies · my
 * approval inbox · orders to ship). The paths below are the leg paths of
 * `operatorOverviewLegs` with the deployment base stripped (each domain's
 * fixture is keyed by the path its own console client sends). A fixture that
 * stops answering — or answers 0 — throws at module load: loud, never a silent
 * fallback, and never a sample overview that shows «nothing to do».
 */
function legBody(
  handler: ((path: string) => unknown) | undefined,
  path: string,
): Record<string, unknown> {
  const body = handler?.(path);
  if (!body || typeof body !== 'object') {
    throw new Error(`sample overview: no body for ${path}`);
  }
  return body as Record<string, unknown>;
}

/** Not vacuous: a 0 would render as «nothing waiting» on the landing screen. */
function requirePositive(n: unknown, what: string): void {
  if (typeof n !== 'number' || n <= 0) {
    throw new Error(`sample overview: ${what} must be a positive count (got ${String(n)})`);
  }
}

/** `GET /api/admin/accounts?status=LOCKED…` — `{ content, totalElements, … }`. */
const IAM_LOCKED_BODY = (() => {
  const path = '/api/admin/accounts?status=LOCKED&page=0&size=1';
  const body = legBody(IAM_FIXTURE_HANDLERS['iam:accounts'], path);
  requirePositive(body.totalElements, `iam ${path} totalElements`);
  return body;
})();

/** `GET /api/v1/admin/dashboard/orders?status=RECEIVED…` — the read-model page. */
const WMS_UNSHIPPED_BODY = (() => {
  const path = '/api/v1/admin/dashboard/orders?status=RECEIVED&page=0&size=1';
  const body = legBody(WMS_FIXTURE_HANDLERS['wms:wms'], path);
  requirePositive(
    (body.page as { totalElements?: unknown } | undefined)?.totalElements,
    `wms ${path} page.totalElements`,
  );
  return body;
})();

/** `GET /api/v1/procurement/po?status=DRAFT…` — `{ data: <page>, meta }`. */
const SCM_DRAFT_PO_BODY = (() => {
  const path = '/api/v1/procurement/po?status=DRAFT&page=0&size=1';
  const body = legBody(SCM_FIXTURE_HANDLERS['flat:scm'], path);
  requirePositive(
    (body.data as { totalElements?: unknown } | undefined)?.totalElements,
    `scm ${path} data.totalElements`,
  );
  return body;
})();

/**
 * `GET /api/finance/ledger/reconciliation/discrepancies?status=OPEN…` —
 * `{ data: [ Discrepancy ], meta: { …, totalElements } }`. The card reads only
 * `meta.totalElements` (F5: the rows' minor-units strings are never touched).
 */
const FINANCE_OPEN_DISCREPANCIES_BODY = (() => {
  const path = '/api/finance/ledger/reconciliation/discrepancies?status=OPEN&page=0&size=1';
  const body = legBody(LEDGER_FIXTURE_HANDLERS['flat:ledger'], path);
  requirePositive(
    (body.meta as { totalElements?: unknown } | undefined)?.totalElements,
    `finance ${path} meta.totalElements`,
  );
  return body;
})();

/**
 * `GET /api/erp/approval/inbox?page=0&size=1` — `{ data, meta: { …,
 * actorEmployeeId } }`. 🔴 The sample visitor IS linked to an employee
 * (`actorEmployeeId` present); a body without it would render «직원 연결 없음»
 * on the landing screen, so that is asserted too.
 */
const ERP_INBOX_BODY = (() => {
  const path = '/api/erp/approval/inbox?page=0&size=1';
  const body = legBody(ERP_FIXTURE_HANDLERS['flat:erp_approval'], path);
  const meta = body.meta as { totalElements?: unknown; actorEmployeeId?: unknown } | undefined;
  requirePositive(meta?.totalElements, `erp ${path} meta.totalElements`);
  if (typeof meta?.actorEmployeeId !== 'string' || meta.actorEmployeeId.length === 0) {
    throw new Error(`sample overview: erp ${path} must carry meta.actorEmployeeId`);
  }
  return body;
})();

/** `GET /api/admin/orders?status=CONFIRMED…` — `{ content, page, size, totalElements }`. */
const ECOMMERCE_TO_SHIP_BODY = (() => {
  const path = '/api/admin/orders?status=CONFIRMED&page=0&size=1';
  const body = legBody(ECOMMERCE_FIXTURE_HANDLERS['ecommerce:ecommerce_order'], path);
  requirePositive(body.totalElements, `ecommerce ${path} totalElements`);
  return body;
})();

/**
 * TASK-PC-FE-368 — each card's headline metric (ADR-MONO-084 D7) is the answer the
 * SAME domain fixture gives to the SAME `…/metrics?period=week` request the
 * composition's headline leg makes — so the landing card and the domain's own
 * «실적» strip cannot disagree. Not found ⇒ throws at module load (loud, never a
 * silent «실적을 불러올 수 없음» on the first screen a visitor sees).
 *
 * 🔴 This file may import nothing outside `shared/sample/`
 * (`tests/unit/sample-router-isolation.test.ts`), so the key selection of
 * `shared/lib/operator-overview-headline.ts` (`HEADLINE_SPECS`) is restated here.
 * `tests/unit/features/operator-overview/headline-metric.test.tsx` pins the two
 * to each other: it reduces the same fixture answers with the production
 * function and requires equality.
 */
interface SampleMetric {
  key: string;
  unit: string;
  currency: string | null;
  current: string;
  previous: string;
}

function sampleHeadline(
  domain: string,
  matches: (key: string) => boolean,
  handler: ((path: string) => unknown) | undefined,
  path: string,
) {
  let body = handler?.(path) as { data?: unknown; metrics?: SampleMetric[] } | undefined;
  // procurement wraps most reads in `{ data, meta }`; the bare form has `metrics`.
  if (body && !('metrics' in body) && body.data) body = body.data as typeof body;
  const metrics = (body?.metrics ?? []).filter((m) => matches(m.key));
  if (metrics.length === 0) {
    throw new Error(`sample overview: no headline metric for ${domain} from ${path}`);
  }
  return {
    status: 'ok',
    period: 'week',
    metrics: metrics.map((m) => ({
      key: m.key,
      unit: m.unit,
      currency: m.currency,
      current: m.current,
      previous: m.previous,
    })),
  } as const;
}

const SAMPLE_HEADLINES = {
  iam: sampleHeadline('iam', (k) => k === 'loginFailure', IAM_FIXTURE_HANDLERS['iam:iam_metrics'], '/api/admin/metrics?period=week&tenantId=sample'),
  wms: sampleHeadline('wms', (k) => k === 'qtyShipped', WMS_FIXTURE_HANDLERS['wms:wms'], '/api/v1/admin/dashboard/metrics?period=week'),
  scm: sampleHeadline('scm', (k) => k.startsWith('poAmount:'), SCM_FIXTURE_HANDLERS['flat:scm'], '/api/v1/procurement/po/metrics?period=week'),
  finance: sampleHeadline('finance', (k) => k.startsWith('transferAmount:'), FINANCE_FIXTURE_HANDLERS['flat:finance'], '/api/finance/accounts/metrics?period=week'),
  erp: sampleHeadline('erp', (k) => k === 'approved', ERP_FIXTURE_HANDLERS['flat:erp_approval'], '/api/erp/approval/metrics?period=week'),
  ecommerce: sampleHeadline('ecommerce', (k) => k === 'revenue', ECOMMERCE_FIXTURE_HANDLERS['ecommerce:ecommerce_order'], '/api/admin/orders/metrics?period=week'),
};

/**
 * Dashboard fixtures (R3ⓐ — the first screens made `ready`): the composed
 * operator overview (§ 2.4.9.1), domain health (§ 2.4.9.2) and the
 * notification aggregator inbox (ADR-MONO-043 §4).
 *
 * Hand-authored synthetic data (ADR-MONO-074 A4). Each shape is parsed by the
 * SAME zod schema the real screen uses (`tests/unit/sample-fixtures-schema.test.ts`)
 * — a fixture that broke the schema would render as a section degrade and be
 * indistinguishable from "broken" (task Failure Scenario).
 *
 * R2ⓐ: human-readable strings end with «(샘플)»; ids, codes, amounts, counts,
 * dates and status enums do not (`tests/unit/sample-label-rule.test.ts`).
 */

export const SAMPLE_OPERATOR_OVERVIEW = {
  asOf: SAMPLE_AS_OF,
  cards: [
    { domain: 'iam', status: 'ok', data: IAM_LOCKED_BODY, headline: SAMPLE_HEADLINES.iam },
    { domain: 'wms', status: 'ok', data: WMS_UNSHIPPED_BODY, headline: SAMPLE_HEADLINES.wms },
    { domain: 'scm', status: 'ok', data: SCM_DRAFT_PO_BODY, headline: SAMPLE_HEADLINES.scm },
    { domain: 'finance', status: 'ok', data: FINANCE_OPEN_DISCREPANCIES_BODY, headline: SAMPLE_HEADLINES.finance },
    { domain: 'erp', status: 'ok', data: ERP_INBOX_BODY, headline: SAMPLE_HEADLINES.erp },
    { domain: 'ecommerce', status: 'ok', data: ECOMMERCE_TO_SHIP_BODY, headline: SAMPLE_HEADLINES.ecommerce },
  ],
} as const;

export const SAMPLE_DOMAIN_HEALTH = {
  asOf: SAMPLE_AS_OF,
  cards: [
    { domain: 'iam', status: 'ok', data: { status: 'UP' } },
    { domain: 'wms', status: 'ok', data: { status: 'UP' } },
    { domain: 'scm', status: 'ok', data: { status: 'UP' } },
    { domain: 'finance', status: 'ok', data: { status: 'UP' } },
    { domain: 'erp', status: 'ok', data: { status: 'UP' } },
    { domain: 'ecommerce', status: 'ok', data: { status: 'UP' } },
  ],
} as const;

/**
 * CORRECTION (TASK-PC-FE-285 coordinator review, 2026-09-17 UTC) — every
 * `sourceId` below MUST be a real id in the erp approval fixture world
 * (`shared/sample/fixtures/erp.ts`'s `ERP_APPROVAL_REQUESTS`), and each
 * notification's `type` MUST match that approval's actual `status` — the
 * bell is mounted on every screen a sample visitor sees, so a dangling
 * `sourceId` is a 404 one click away from anywhere in the console, and a
 * `type`/`status` mismatch is the exact "two screens disagree" defect class
 * TASK-PC-FE-284's settlement CORRECTION already named. Before this fix the
 * three ids (`sample-approval-1001/1000/0998`) existed ONLY in this file —
 * `tests/unit/notification-inbox-approval-links.test.ts` walks the inbox →
 * approval via `sampleResponse` (never the raw constants) and proves it.
 */
export const SAMPLE_NOTIFICATION_INBOX = {
  asOf: SAMPLE_AS_OF,
  items: [
    {
      // appr-sample-0001 — status SUBMITTED, submitterId emp-sample-0002 (이민준).
      id: 'sample-notification-0001',
      sourceDomain: 'erp',
      type: 'APPROVAL_SUBMITTED',
      title: '영업1팀 예산 증액 요청 (샘플)',
      body: '이민준 님이 영업1팀 예산 증액 요청을 상신했습니다 (샘플)',
      sourceType: 'APPROVAL',
      sourceId: 'appr-sample-0001',
      read: false,
      createdAt: '2026-09-15T09:30:00Z',
    },
    {
      // appr-sample-0004 — status APPROVED, title '이민준 부서 이동 승인'.
      id: 'sample-notification-0002',
      sourceDomain: 'erp',
      type: 'APPROVAL_APPROVED',
      title: '이민준 부서 이동 승인 완료 (샘플)',
      body: '이민준 부서 이동 요청이 최종 승인되었습니다 (샘플)',
      sourceType: 'APPROVAL',
      sourceId: 'appr-sample-0004',
      read: false,
      createdAt: '2026-09-14T23:10:00Z',
    },
    {
      // appr-sample-0005 — status REJECTED, reason '예산 부족으로 반려'.
      id: 'sample-notification-0003',
      sourceDomain: 'erp',
      type: 'APPROVAL_REJECTED',
      title: '영업2팀 폐지 결재 반려 (샘플)',
      body: '예산 부족으로 반려되었습니다 (샘플)',
      sourceType: 'APPROVAL',
      sourceId: 'appr-sample-0005',
      read: true,
      createdAt: '2026-09-12T02:00:00Z',
      readAt: '2026-09-12T05:00:00Z',
    },
  ],
  meta: { page: 0, size: 20, totalElements: 3 },
  degradedDomains: [],
} as const;
