import { describe, it, expect } from 'vitest';
import {
  buildTree,
  descendantIds,
  isCeilingSubset,
  permitsNothing,
  effectiveCeilingOf,
} from '@/features/org-hierarchy/lib/tree';
import type { OrgNode, Ceiling } from '@/features/org-hierarchy/api/types';

/**
 * Pure org-tree / ceiling semantics (TASK-PC-FE-237 / ADR-047). These are the
 * correctness core of the feature — getting the ceiling matrix or the
 * subtree-root assembly wrong inverts the deny-only meaning or drops a node.
 */

function node(
  id: string,
  parentId: string | null,
  name = id,
  ceiling: Ceiling = { mode: 'UNBOUNDED' },
  depth = 1,
): OrgNode {
  return {
    orgNodeId: id,
    parentId,
    name,
    depth,
    ceiling,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  };
}

const B = (...domains: string[]): Ceiling => ({ mode: 'BOUNDED', domains });
const U: Ceiling = { mode: 'UNBOUNDED' };

describe('buildTree', () => {
  it('assembles a nested tree from a flat list', () => {
    const nodes = [
      node('a', null, 'A'),
      node('b', 'a', 'B'),
      node('c', 'a', 'C'),
      node('d', 'b', 'D'),
    ];
    const tree = buildTree(nodes);
    expect(tree).toHaveLength(1);
    expect(tree[0].orgNodeId).toBe('a');
    // siblings sorted by name: B before C
    expect(tree[0].children.map((c) => c.orgNodeId)).toEqual(['b', 'c']);
    expect(tree[0].children[0].children.map((c) => c.orgNodeId)).toEqual(['d']);
  });

  it('treats a node whose parent is ABSENT as a root (ORG_ADMIN subtree case)', () => {
    // An ORG_ADMIN sees only its subtree — the top node points at a parent not
    // in the list. It must still render as a root, never vanish.
    const nodes = [
      node('sub', 'not-in-list', 'Sub'),
      node('leaf', 'sub', 'Leaf'),
    ];
    const tree = buildTree(nodes);
    expect(tree.map((r) => r.orgNodeId)).toEqual(['sub']);
    expect(tree[0].children.map((c) => c.orgNodeId)).toEqual(['leaf']);
  });

  it('drops no node', () => {
    const nodes = [
      node('a', null),
      node('b', 'a'),
      node('orphan', 'ghost'),
    ];
    const seen = new Set<string>();
    const walk = (items: ReturnType<typeof buildTree>) => {
      for (const it of items) {
        seen.add(it.orgNodeId);
        walk(it.children);
      }
    };
    walk(buildTree(nodes));
    expect(seen).toEqual(new Set(['a', 'b', 'orphan']));
  });

  it('terminates (no infinite loop) on cyclic input and keeps every node', () => {
    // A ↔ B mutual-parent corruption.
    const nodes = [node('a', 'b', 'A'), node('b', 'a', 'B')];
    const tree = buildTree(nodes);
    const seen = new Set<string>();
    const walk = (items: ReturnType<typeof buildTree>) => {
      for (const it of items) {
        seen.add(it.orgNodeId);
        walk(it.children);
      }
    };
    walk(tree);
    expect(seen).toEqual(new Set(['a', 'b']));
  });
});

describe('isCeilingSubset', () => {
  it('covers the full matrix', () => {
    expect(isCeilingSubset(U, U)).toBe(true);
    expect(isCeilingSubset(B('wms'), U)).toBe(true);
    // UNBOUNDED is WIDER than any bounded cap — not a subset.
    expect(isCeilingSubset(U, B('wms', 'scm'))).toBe(false);
    expect(isCeilingSubset(B('wms'), B('wms', 'scm'))).toBe(true);
    expect(isCeilingSubset(B('wms', 'erp'), B('wms'))).toBe(false);
    // BOUNDED([]) ⊆ BOUNDED(x) — the empty cap is a subset of everything.
    expect(isCeilingSubset(B(), B('wms'))).toBe(true);
  });
});

describe('permitsNothing', () => {
  it('is true for BOUNDED([]) and false for UNBOUNDED (they are NOT equal)', () => {
    expect(permitsNothing(B())).toBe(true);
    expect(permitsNothing(U)).toBe(false);
    // The two opposite states must never behave the same.
    expect(permitsNothing(B())).not.toBe(permitsNothing(U));
  });
});

describe('effectiveCeilingOf', () => {
  it('intersects down the root→node chain', () => {
    const nodes = [
      node('root', null, 'root', U),
      node('mid', 'root', 'mid', B('wms', 'scm')),
      node('leaf', 'mid', 'leaf', B('wms')),
    ];
    const eff = effectiveCeilingOf(nodes, 'leaf');
    expect(eff.mode).toBe('BOUNDED');
    expect(eff.mode === 'BOUNDED' && eff.domains).toEqual(['wms']);
  });

  it('returns UNBOUNDED for an all-unbounded chain', () => {
    const nodes = [
      node('root', null, 'root', U),
      node('mid', 'root', 'mid', U),
    ];
    expect(effectiveCeilingOf(nodes, 'mid')).toEqual({ mode: 'UNBOUNDED' });
  });
});

describe('descendantIds', () => {
  it('excludes the node itself', () => {
    const nodes = [
      node('a', null),
      node('b', 'a'),
      node('c', 'b'),
      node('d', null),
    ];
    const ds = descendantIds(nodes, 'a');
    expect(ds).toEqual(new Set(['b', 'c']));
    expect(ds.has('a')).toBe(false);
    expect(ds.has('d')).toBe(false);
  });
});
