import { SAMPLE_TENANT_ID } from '../codes';

/**
 * Sample IAM product/tenant registry (`GET /api/admin/console/registry`) —
 * hand-authored synthetic data (ADR-MONO-074 A4: there is no extraction path
 * from any backend).
 *
 * AC-12 (implementer's choice, recorded in TASK-PC-FE-282): every product is
 * `available` and lists the single sample tenant, so every screen's registry
 * eligibility pre-flight passes — a sample visitor never sees a
 * "not permitted" screen. Permission-denied states are not reproduced in
 * sample mode.
 *
 * R2ⓐ: `displayName` is read by people → «(샘플)». `productKey`, `baseRoute`
 * and tenant ids are parsed → no suffix.
 *
 * TASK-PC-FE-286 (Edge Case 1) — `finance.operatorContext.defaultAccountId`
 * (`OperatorContextSchema`, `shared/api/operator-context-types.ts`) is what
 * `getFinanceDefaultAccountId()` reads to seed the `/finance` overview's
 * account leg and the operator-overview finance card (`fixtures/dashboards.ts`).
 * Before this field existed, every sample visitor's `/finance` overview
 * silently degraded to `defaultAccountMissing: true` ("not set up yet") — a
 * normal state, but one where a sample world resolving zero default accounts
 * is as broken for AC-7 as one resolving the wrong one.
 *
 * 🔴 The literal `'sample-account-0001'` below is deliberately NOT imported
 * from `./finance.ts` (`SAMPLE_FINANCE_DEFAULT_ACCOUNT_ID`) — a
 * `registry.ts → finance.ts` edge creates a real circular-init crash:
 * `finance.ts` imports `../router`, which imports `./fixtures` (this
 * directory's barrel), which imports BOTH `./registry` and `./dashboards`;
 * `dashboards.ts` also eagerly (at module-load time, not inside a deferred
 * closure) reads `FINANCE_FIXTURE_HANDLERS` off `./finance.ts` to derive its
 * card. Whichever test file happens to import `registry.ts` before
 * `dashboards.ts` gets has landed on a live cycle mid-init, and
 * `FINANCE_FIXTURE_HANDLERS` reads back `undefined` (measured: this exact
 * crash in `tests/unit/sample-fixtures-schema.test.ts`, which imports
 * `registry.ts` on its own import line before `dashboards.ts`'s). The SAME
 * "duplicate the literal, cross-check with a router-level test" shape
 * TASK-PC-FE-285's CORRECTION already used for the notification bell's
 * `sourceId` values (never an import edge into the id's owning fixture) —
 * `tests/unit/sample-fixtures-schema-finance-ledger.test.ts`'s "Edge Case 1"
 * describe block asserts this id resolves to a REAL account in `finance.ts`.
 */
export const SAMPLE_REGISTRY = {
  products: [
    {
      productKey: 'iam',
      displayName: 'IAM (샘플)',
      available: true,
      tenants: [SAMPLE_TENANT_ID],
      baseRoute: '/iam',
    },
    {
      productKey: 'wms',
      displayName: 'WMS (샘플)',
      available: true,
      tenants: [SAMPLE_TENANT_ID],
      baseRoute: '/wms',
    },
    {
      productKey: 'scm',
      displayName: 'SCM (샘플)',
      available: true,
      tenants: [SAMPLE_TENANT_ID],
      baseRoute: '/scm',
    },
    {
      productKey: 'erp',
      displayName: 'ERP (샘플)',
      available: true,
      tenants: [SAMPLE_TENANT_ID],
      baseRoute: '/erp',
    },
    {
      productKey: 'finance',
      displayName: 'Finance (샘플)',
      available: true,
      tenants: [SAMPLE_TENANT_ID],
      baseRoute: '/finance',
      // MUST stay byte-identical to `SAMPLE_FINANCE_DEFAULT_ACCOUNT_ID` in
      // `./finance.ts` (see the module-cycle note above for why this is a
      // literal, not an import).
      operatorContext: { defaultAccountId: 'sample-account-0001' },
    },
    {
      productKey: 'ecommerce',
      displayName: 'E-Commerce (샘플)',
      available: true,
      tenants: [SAMPLE_TENANT_ID],
      baseRoute: '/ecommerce',
    },
  ],
} as const;
