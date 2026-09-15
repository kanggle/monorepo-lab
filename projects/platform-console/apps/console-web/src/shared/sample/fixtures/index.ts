import { SAMPLE_REGISTRY } from './registry';
import {
  SAMPLE_OPERATOR_OVERVIEW,
  SAMPLE_DOMAIN_HEALTH,
  SAMPLE_NOTIFICATION_INBOX,
} from './dashboards';

/**
 * Fixture answer per `ready` surface, keyed `<core>:<surface>`
 * (see `coverage.ts`). The handler receives the request path (query stripped)
 * and returns the body, or `undefined` when the path has no sample — the
 * router then answers `SAMPLE_NOT_READY` (never a silent 200 with nothing).
 */
export type FixtureHandler = (path: string) => unknown | undefined;

export const SAMPLE_FIXTURES: Readonly<Record<string, FixtureHandler>> = {
  'registry:registry': () => SAMPLE_REGISTRY,
  'console-bff:operator-overview': () => SAMPLE_OPERATOR_OVERVIEW,
  'console-bff:domain-health': () => SAMPLE_DOMAIN_HEALTH,
  'console-bff:notifications-inbox': () => SAMPLE_NOTIFICATION_INBOX,
};

/**
 * Every fixture document, for the R2ⓐ labelling guard
 * (`tests/unit/sample-label-rule.test.ts`). A new fixture that is not listed
 * here escapes that guard — the ledger test cross-checks that every
 * `SAMPLE_FIXTURES` key's document appears in this list.
 */
export const SAMPLE_FIXTURE_DOCUMENTS: Readonly<Record<string, unknown>> = {
  'registry:registry': SAMPLE_REGISTRY,
  'console-bff:operator-overview': SAMPLE_OPERATOR_OVERVIEW,
  'console-bff:domain-health': SAMPLE_DOMAIN_HEALTH,
  'console-bff:notifications-inbox': SAMPLE_NOTIFICATION_INBOX,
};
