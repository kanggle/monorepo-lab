import { SAMPLE_LABEL_SUFFIX } from './codes';

/**
 * R2ⓐ «(샘플)» labelling rule — the predicate, shared by the guard test and by
 * the domain fixture tickets (TASK-PC-FE-283…288).
 *
 * A fixture string is classified by the KEY it sits under:
 *
 *   - human-readable (names, titles, descriptions, memos, notes, warnings)
 *     → MUST end with {@link SAMPLE_LABEL_SUFFIX};
 *   - machine-read (ids, codes, enums, dates, amounts, routes, currency)
 *     → MUST NOT contain «(샘플)» — parsers and `StatusBadge` interpret them,
 *       and a suffix would break the screen (ADR-MONO-074 R2ⓐ);
 *   - any other string key → a violation of its own: the key must be
 *     classified before a fixture may use it. 🔴 An unknown key failing is the
 *     point — "unclassified passes" would let a new human-readable field ship
 *     without the suffix and nobody would notice.
 *
 * Strings inside arrays inherit the key of the array.
 */

const HUMAN_READABLE_KEYS = new Set([
  'displayName',
  'name',
  'title',
  'body',
  'description',
  'memo',
  'note',
  'warning',
  'label',
]);

const MACHINE_KEYS = new Set([
  'id',
  'productKey',
  'baseRoute',
  'tenants',
  'domain',
  'status',
  'reason',
  'type',
  'sourceType',
  'sourceDomain',
  'deepLink',
  'currency',
  'amount',
  'code',
  'degradedDomains',
]);

/** `*Id` (sourceId, accountId, nodeId, …) and `*At` (createdAt, asOf-like). */
function isMachineKey(key: string): boolean {
  return (
    MACHINE_KEYS.has(key) ||
    /Id$/.test(key) ||
    /At$/.test(key) ||
    key === 'asOf'
  );
}

export interface LabelViolation {
  path: string;
  value: string;
  problem: 'missing-suffix' | 'suffix-on-machine-value' | 'unclassified-key';
}

export function findLabelViolations(
  value: unknown,
  path = '$',
  key: string | null = null,
): LabelViolation[] {
  if (typeof value === 'string') {
    if (key === null) {
      return [{ path, value, problem: 'unclassified-key' }];
    }
    if (HUMAN_READABLE_KEYS.has(key)) {
      return value.endsWith(SAMPLE_LABEL_SUFFIX)
        ? []
        : [{ path, value, problem: 'missing-suffix' }];
    }
    if (isMachineKey(key)) {
      return value.includes('(샘플)')
        ? [{ path, value, problem: 'suffix-on-machine-value' }]
        : [];
    }
    return [{ path, value, problem: 'unclassified-key' }];
  }
  if (Array.isArray(value)) {
    return value.flatMap((item, i) => findLabelViolations(item, `${path}[${i}]`, key));
  }
  if (value !== null && typeof value === 'object') {
    return Object.entries(value as Record<string, unknown>).flatMap(([k, v]) =>
      findLabelViolations(v, `${path}.${k}`, k),
    );
  }
  return [];
}
