import { SAMPLE_AS_OF } from '../codes';

/**
 * Dashboard fixtures (R3ⓐ — the first screens made `ready`): the console-bff
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
    { domain: 'iam', status: 'ok', data: { totalElements: 128 } },
    {
      domain: 'wms',
      status: 'ok',
      data: { inventorySnapshot: { totalStockUnits: 48210, alertCount: 3 } },
    },
    {
      domain: 'scm',
      status: 'ok',
      data: {
        meta: { warning: 'Not for procurement decisions (S5) (샘플)' },
        nodes: [
          { nodeId: 'sample-node-01', name: '평택 물류센터 (샘플)' },
          { nodeId: 'sample-node-02', name: '이천 물류센터 (샘플)' },
          { nodeId: 'sample-node-03', name: '부산 항만창고 (샘플)' },
        ],
      },
    },
    {
      domain: 'finance',
      status: 'ok',
      data: {
        balance: { amount: '1250000000', currency: 'KRW' },
        accountId: 'sample-account-0001',
      },
    },
    { domain: 'erp', status: 'ok', data: { meta: { totalElements: 12 } } },
    { domain: 'ecommerce', status: 'ok', data: { totalElements: 342 } },
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
