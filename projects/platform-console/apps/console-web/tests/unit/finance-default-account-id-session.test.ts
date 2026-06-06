import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * `shared/lib/finance-default-account-id.ts` — server-only helper for the
 * Option (a) activation chain (TASK-PC-FE-014 / `console-integration-contract.md
 * § 2.4.9.1 Implementation guidance — Option (a) activation`).
 *
 * Asserts the 5 normative null-paths + the happy-path trim:
 *   - registry response has finance.operatorContext.defaultAccountId set →
 *     returns the trimmed value;
 *   - registry response has finance.operatorContext omitted → null
 *     (Jackson `@JsonInclude(NON_NULL)` producer-side / unprovisioned operator);
 *   - registry response has finance.operatorContext present but defaultAccountId
 *     absent → null (future-extension carrier with other attributes only);
 *   - registry response has finance.operatorContext.defaultAccountId = "" → null
 *     (defensive — producer never emits this but consumer is tolerant);
 *   - registry response has finance.operatorContext.defaultAccountId = "   " → null
 *     (whitespace-only after trim);
 *   - registry response has finance.operatorContext.defaultAccountId = "  acc  " →
 *     "acc" (trim applied; non-empty after trim);
 *   - registry fetch throws (timeout / 401 / 503) → null (degraded; helper
 *     never throws so the dashboard proxy's own auth gates remain
 *     authoritative; finance card falls through to MISSING_PREREQUISITE).
 *
 * The helper is the sole legitimate consumer of the value on the
 * server-only boundary; the proxy route MUST only use this helper's
 * normalized return (never the raw registry shape).
 */

vi.mock('@/shared/api/registry-client', () => ({
  fetchRegistry: vi.fn(),
}));

import { fetchRegistry } from '@/shared/api/registry-client';
import { getFinanceDefaultAccountId } from '@/shared/lib/finance-default-account-id';

function registryWithFinanceItem(
  operatorContext: Record<string, unknown> | undefined,
) {
  return {
    products: [
      {
        productKey: 'iam' as const,
        displayName: 'IAM',
        available: true,
        tenants: ['wms'],
        baseRoute: '/iam',
      },
      {
        productKey: 'finance' as const,
        displayName: 'Finance',
        available: true,
        tenants: ['finance'],
        baseRoute: '/finance',
        operatorContext,
      },
    ],
  };
}

beforeEach(() => {
  vi.mocked(fetchRegistry).mockReset();
});

describe('getFinanceDefaultAccountId — happy path', () => {
  it('returns the value when finance.operatorContext.defaultAccountId is set', async () => {
    vi.mocked(fetchRegistry).mockResolvedValueOnce(
      registryWithFinanceItem({
        defaultAccountId: '01928c4a-7e9f-7c00-9a40-d2b1f5e8a000',
      }) as unknown as Awaited<ReturnType<typeof fetchRegistry>>,
    );
    const v = await getFinanceDefaultAccountId();
    expect(v).toBe('01928c4a-7e9f-7c00-9a40-d2b1f5e8a000');
  });

  it('trims surrounding whitespace and returns the trimmed value', async () => {
    vi.mocked(fetchRegistry).mockResolvedValueOnce(
      registryWithFinanceItem({
        defaultAccountId: '  acc-uuid-7  ',
      }) as unknown as Awaited<ReturnType<typeof fetchRegistry>>,
    );
    const v = await getFinanceDefaultAccountId();
    expect(v).toBe('acc-uuid-7');
  });
});

describe('getFinanceDefaultAccountId — null paths (header omitted at the proxy)', () => {
  it('returns null when finance.operatorContext is omitted entirely (unprovisioned operator)', async () => {
    vi.mocked(fetchRegistry).mockResolvedValueOnce(
      registryWithFinanceItem(undefined) as unknown as Awaited<
        ReturnType<typeof fetchRegistry>
      >,
    );
    const v = await getFinanceDefaultAccountId();
    expect(v).toBeNull();
  });

  it('returns null when operatorContext is present but defaultAccountId is absent', async () => {
    vi.mocked(fetchRegistry).mockResolvedValueOnce(
      registryWithFinanceItem({}) as unknown as Awaited<
        ReturnType<typeof fetchRegistry>
      >,
    );
    const v = await getFinanceDefaultAccountId();
    expect(v).toBeNull();
  });

  it('returns null when defaultAccountId is an empty string', async () => {
    vi.mocked(fetchRegistry).mockResolvedValueOnce(
      registryWithFinanceItem({
        defaultAccountId: '',
      }) as unknown as Awaited<ReturnType<typeof fetchRegistry>>,
    );
    const v = await getFinanceDefaultAccountId();
    expect(v).toBeNull();
  });

  it('returns null when defaultAccountId is whitespace only', async () => {
    vi.mocked(fetchRegistry).mockResolvedValueOnce(
      registryWithFinanceItem({
        defaultAccountId: '   ',
      }) as unknown as Awaited<ReturnType<typeof fetchRegistry>>,
    );
    const v = await getFinanceDefaultAccountId();
    expect(v).toBeNull();
  });

  it('returns null when there is no finance product item at all (defensive)', async () => {
    vi.mocked(fetchRegistry).mockResolvedValueOnce({
      products: [
        {
          productKey: 'iam' as const,
          displayName: 'IAM',
          available: true,
          tenants: ['wms'],
          baseRoute: '/iam',
        },
      ],
    } as unknown as Awaited<ReturnType<typeof fetchRegistry>>);
    const v = await getFinanceDefaultAccountId();
    expect(v).toBeNull();
  });

  it('returns null when fetchRegistry throws (degraded / 401 / 503 / timeout) — helper never throws', async () => {
    vi.mocked(fetchRegistry).mockRejectedValueOnce(
      new Error('registry unreachable'),
    );
    const v = await getFinanceDefaultAccountId();
    expect(v).toBeNull();
  });
});
