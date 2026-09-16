import { SAMPLE_REGISTRY } from './registry';
import {
  SAMPLE_OPERATOR_OVERVIEW,
  SAMPLE_DOMAIN_HEALTH,
  SAMPLE_NOTIFICATION_INBOX,
} from './dashboards';

import { IAM_FIXTURE_HANDLERS, IAM_FIXTURE_DOCUMENTS } from './iam';
import { ECOMMERCE_FIXTURE_HANDLERS, ECOMMERCE_FIXTURE_DOCUMENTS } from './ecommerce';
import type { FixtureNotFound } from '../router';

/**
 * Fixture answer per `ready` surface, keyed `<core>:<surface>`
 * (see `coverage.ts`). The handler receives the request path — TASK-PC-FE-283
 * widened this from "query stripped" to the FULL path (query string
 * included), so a domain fixture can apply the screen's own filter/search/
 * page params over its rows (ADR-MONO-074 / TASK-PC-FE-283 AC-4; a deviation
 * from TASK-PC-FE-282's router recorded in that task's Implementation notes).
 * The TASK-PC-FE-282 dashboard/registry handlers below ignore the argument
 * entirely, so this is backward-compatible with them.
 *
 * Returns `undefined` when the path has no sample — the router then answers
 * `SAMPLE_NOT_READY` (never a silent 200 with nothing) — or a
 * {@link FixtureNotFound} (`shared/sample/router.ts`) when the path names a
 * real detail id that does not exist among the fixture's rows (TASK-PC-FE-283
 * AC-3 — the router then answers the SAME 404 shape the real backend would).
 */
export type FixtureHandler = (path: string) => unknown | FixtureNotFound | undefined;

export const SAMPLE_FIXTURES: Readonly<Record<string, FixtureHandler>> = {
  'registry:registry': () => SAMPLE_REGISTRY,
  'console-bff:operator-overview': () => SAMPLE_OPERATOR_OVERVIEW,
  'console-bff:domain-health': () => SAMPLE_DOMAIN_HEALTH,
  'console-bff:notifications-inbox': () => SAMPLE_NOTIFICATION_INBOX,
  ...IAM_FIXTURE_HANDLERS,
  ...ECOMMERCE_FIXTURE_HANDLERS,
};

/**
 * Every fixture document, for the R2ⓐ labelling guard
 * (`tests/unit/sample-label-rule.test.ts`). A new fixture that is not listed
 * here escapes that guard — the ledger test cross-checks that every
 * `SAMPLE_FIXTURES` key's document appears in this list.
 *
 * TASK-PC-FE-283 — for a multi-branch IAM handler (list / detail / sub-
 * resource), the document is the AGGREGATE of every underlying seed array —
 * not just the one branch a default path would hit — so the label guard scans
 * every string that surface could ever answer with (see `iam.ts` § document
 * exports).
 */
export const SAMPLE_FIXTURE_DOCUMENTS: Readonly<Record<string, unknown>> = {
  'registry:registry': SAMPLE_REGISTRY,
  'console-bff:operator-overview': SAMPLE_OPERATOR_OVERVIEW,
  'console-bff:domain-health': SAMPLE_DOMAIN_HEALTH,
  'console-bff:notifications-inbox': SAMPLE_NOTIFICATION_INBOX,
  ...IAM_FIXTURE_DOCUMENTS,
  ...ECOMMERCE_FIXTURE_DOCUMENTS,
};
