import { describe, it, expect } from 'vitest';
import { labelForUnknown } from '@/shared/lib/tolerant-label';

/**
 * TASK-PC-FE-234 — shared "tolerant unknown-enum label" helper. Pinned
 * here (this is the extraction of the duplicated ledger-ops / finance-ops
 * / finance-overview / erp-ops call-site helper) so a regression in the
 * shared implementation is caught at the source, in addition to every
 * call-site's own fallback-string assertion.
 */
describe('labelForUnknown', () => {
  const KNOWN = ['ACTIVE', 'RETIRED'] as const;

  it('renders a known value as-is', () => {
    expect(labelForUnknown('ACTIVE', KNOWN)).toBe('ACTIVE');
  });

  it('renders an unknown / future value with a generic "(unknown)" suffix', () => {
    expect(labelForUnknown('FUTURE_STATE', KNOWN)).toBe(
      'FUTURE_STATE (unknown)',
    );
  });

  it('renders null as the em-dash placeholder (nullable-aware)', () => {
    expect(labelForUnknown(null, KNOWN)).toBe('—');
  });

  it('renders undefined as the em-dash placeholder (nullable-aware)', () => {
    expect(labelForUnknown(undefined, KNOWN)).toBe('—');
  });

  it('renders an empty string as the em-dash placeholder', () => {
    expect(labelForUnknown('', KNOWN)).toBe('—');
  });
});
