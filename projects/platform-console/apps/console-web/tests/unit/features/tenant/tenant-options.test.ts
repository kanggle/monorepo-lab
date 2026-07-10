import { describe, it, expect } from 'vitest';
import { groupTenantsByCompany } from '@/features/tenant/lib/tenant-options';

/**
 * `groupTenantsByCompany` — the AWS-IdC account-picker grouping
 * (TASK-PC-FE-237 / ADR-047). The headline invariant: an ungrouped
 * (`org_node_id = null`, D7 lazy-migration) tenant must NEVER disappear.
 */

describe('groupTenantsByCompany', () => {
  it('an ungrouped tenant survives (headline)', () => {
    const { companies, ungrouped } = groupTenantsByCompany(
      ['t1', 't2', 'lonely'],
      [{ orgNodeId: 'c1', name: 'Acme', tenantIds: ['t1', 't2'] }],
    );
    expect(companies).toHaveLength(1);
    expect(companies[0].tenants).toEqual(['t1', 't2']);
    expect(ungrouped).toContain('lonely');
  });

  it('loses no tenant — union of all company tenants + ungrouped equals the input set', () => {
    const input = ['a', 'b', 'c', 'd', '*'];
    const { companies, ungrouped } = groupTenantsByCompany(input, [
      { orgNodeId: 'c1', name: 'One', tenantIds: ['a', 'b'] },
      { orgNodeId: 'c2', name: 'Two', tenantIds: ['c'] },
    ]);
    const union = new Set([...companies.flatMap((c) => c.tenants), ...ungrouped]);
    expect(union).toEqual(new Set(input));
    // 'd' and '*' are ungrouped, never lost.
    expect(ungrouped).toEqual(['*', 'd']);
  });

  it('assigns a tenant appearing in two groups to exactly ONE (first match)', () => {
    const { companies, ungrouped } = groupTenantsByCompany(
      ['shared'],
      [
        { orgNodeId: 'first', name: 'First', tenantIds: ['shared'] },
        { orgNodeId: 'second', name: 'Second', tenantIds: ['shared'] },
      ],
    );
    // Only the FIRST group claims it; the second has zero members → dropped.
    expect(companies).toHaveLength(1);
    expect(companies[0].orgNodeId).toBe('first');
    expect(companies[0].tenants).toEqual(['shared']);
    expect(ungrouped).toEqual([]);
    // Exactly once across the whole result.
    const all = [...companies.flatMap((c) => c.tenants), ...ungrouped];
    expect(all.filter((t) => t === 'shared')).toHaveLength(1);
  });

  it('with zero companies everything is ungrouped (sorted)', () => {
    const { companies, ungrouped } = groupTenantsByCompany(['z', 'a', 'm'], []);
    expect(companies).toEqual([]);
    expect(ungrouped).toEqual(['a', 'm', 'z']);
  });
});
