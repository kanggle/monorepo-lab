import { describe, expect, it } from 'vitest';
import * as errors from '@/shared/api/errors';
import { DomainUnavailableError } from '@/shared/api/errors';

/**
 * TASK-PC-FE-386 — the 20 per-domain `*UnavailableError` classes share one
 * body via `DomainUnavailableError`. These cells pin what callers observe so
 * the move stays behaviour-neutral: own class + `Error` instanceof, the
 * literal `name`, the three fields, and the historical own-property order.
 */
const NAMES = [
  'AccountsUnavailableError',
  'AuditUnavailableError',
  'OperatorsUnavailableError',
  'RbacUnavailableError',
  'WmsUnavailableError',
  'WmsOutboundUnavailableError',
  'ScmUnavailableError',
  'ScmReplenishmentUnavailableError',
  'FinanceUnavailableError',
  'LedgerUnavailableError',
  'ErpUnavailableError',
  'EcommerceUnavailableError',
  'FanUnavailableError',
  'OnboardingUnavailableError',
  'SubscriptionsUnavailableError',
  'PartnershipsUnavailableError',
  'TenantsUnavailableError',
  'OrgNodesUnavailableError',
  'GroupsUnavailableError',
  'DomainPermissionSetsUnavailableError',
] as const;

type Ctor = new (
  reason: 'timeout' | 'circuit_open' | 'downstream',
  code: string,
  message: string,
) => Error & { reason: string; code: string };

describe('DomainUnavailableError subclasses (TASK-PC-FE-386)', () => {
  it.each(NAMES)('%s keeps its observable shape', (name) => {
    const Cls = (errors as unknown as Record<string, Ctor>)[name];
    expect(Cls).toBeTypeOf('function');

    const err = new Cls('circuit_open', 'CIRCUIT_OPEN', 'section down');

    expect(err).toBeInstanceOf(Cls);
    expect(err).toBeInstanceOf(Error);
    expect(err).toBeInstanceOf(DomainUnavailableError);
    expect(err.name).toBe(name);
    expect(err.reason).toBe('circuit_open');
    expect(err.code).toBe('CIRCUIT_OPEN');
    expect(err.message).toBe('section down');
    // Historical own-property order, measured on the pre-386 classes: ES2022
    // field definitions put `reason`, `code` first; `name` is added after.
    expect(Object.keys(err)).toEqual(['reason', 'code', 'name']);
  });

  it('one domain error is not an instance of another domain error', () => {
    const wms = new errors.WmsUnavailableError('timeout', 'X', 'm');
    expect(wms).not.toBeInstanceOf(errors.ScmUnavailableError);
    expect(wms).not.toBeInstanceOf(errors.AccountsUnavailableError);
  });

  it('covers every class extending DomainUnavailableError (no silent 21st)', () => {
    const extending = Object.entries(errors)
      .filter(
        ([, v]) =>
          typeof v === 'function' &&
          v !== DomainUnavailableError &&
          (v as { prototype?: unknown }).prototype instanceof DomainUnavailableError,
      )
      .map(([k]) => k)
      .sort();
    expect(extending).toEqual([...NAMES].sort());
  });
});
