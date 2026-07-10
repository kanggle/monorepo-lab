import type { OrgNode, Ceiling } from '../api/types';

/**
 * Pure, React-free org-tree helpers (TASK-PC-FE-237 / ADR-047). Fully unit-
 * testable: assembles the flat `GET /api/admin/org-nodes` array into a tree,
 * and evaluates the ceiling (entitlement CAP) semantics the UI must never get
 * wrong. NO imports from React / Next / the api layer.
 */

export interface OrgNodeTreeItem extends OrgNode {
  children: OrgNodeTreeItem[];
}

/** Stable sibling order: by `name`, then `orgNodeId` (names are NOT unique). */
function compareNodes(a: OrgNode, b: OrgNode): number {
  if (a.name < b.name) return -1;
  if (a.name > b.name) return 1;
  if (a.orgNodeId < b.orgNodeId) return -1;
  if (a.orgNodeId > b.orgNodeId) return 1;
  return 0;
}

/**
 * Assemble the flat node array into a forest.
 *
 * Roots = nodes whose `parentId` is null OR whose parent is ABSENT from the
 * list — an `ORG_ADMIN` sees only its subtree, so its top node's parent is not
 * in the list; that node must still render as a root, never vanish.
 *
 * Robustness: siblings are sorted for stable rendering; a corrupt cyclic input
 * neither infinite-loops (a `visited` set guards descent) nor drops nodes (any
 * node stranded inside a cycle is promoted to a root so it still renders).
 */
export function buildTree(nodes: OrgNode[]): OrgNodeTreeItem[] {
  const byId = new Map<string, OrgNode>();
  for (const n of nodes) byId.set(n.orgNodeId, n);

  const childrenOf = new Map<string, OrgNode[]>();
  const explicitRoots: OrgNode[] = [];
  for (const n of nodes) {
    const pid = n.parentId;
    if (pid !== null && byId.has(pid)) {
      const arr = childrenOf.get(pid);
      if (arr) arr.push(n);
      else childrenOf.set(pid, [n]);
    } else {
      explicitRoots.push(n);
    }
  }

  const visited = new Set<string>();
  function build(n: OrgNode): OrgNodeTreeItem {
    visited.add(n.orgNodeId);
    const kids = (childrenOf.get(n.orgNodeId) ?? [])
      .filter((c) => !visited.has(c.orgNodeId)) // cycle guard — never re-descend
      .sort(compareNodes);
    return { ...n, children: kids.map(build) };
  }

  const roots = explicitRoots.slice().sort(compareNodes).map(build);

  // Promote any node stranded inside a cycle (never reached from a real root)
  // to a root so it is not silently dropped from the rendered forest.
  const stranded = nodes.filter((n) => !visited.has(n.orgNodeId)).sort(compareNodes);
  for (const n of stranded) {
    if (!visited.has(n.orgNodeId)) roots.push(build(n));
  }
  return roots;
}

/**
 * The set of every descendant of `id` (children, grandchildren, …), EXCLUDING
 * `id` itself. Used client-side to forbid re-parenting a node under its own
 * descendant (the server also 422s with `ORG_NODE_CYCLE`). Cycle-safe.
 */
export function descendantIds(nodes: OrgNode[], id: string): Set<string> {
  const childrenOf = new Map<string, string[]>();
  for (const n of nodes) {
    if (n.parentId !== null) {
      const arr = childrenOf.get(n.parentId);
      if (arr) arr.push(n.orgNodeId);
      else childrenOf.set(n.parentId, [n.orgNodeId]);
    }
  }
  const result = new Set<string>();
  const stack = [...(childrenOf.get(id) ?? [])];
  while (stack.length > 0) {
    const cur = stack.pop() as string;
    if (result.has(cur)) continue;
    result.add(cur);
    for (const c of childrenOf.get(cur) ?? []) {
      if (!result.has(c)) stack.push(c);
    }
  }
  // Guard against a self-loop / cycle re-adding the node itself.
  result.delete(id);
  return result;
}

/**
 * Is `child` a subset (⊆) of `parent` as an entitlement CAP?
 *   - `UNBOUNDED ⊆ UNBOUNDED` → true
 *   - anything `⊆ UNBOUNDED`  → true  (an unbounded parent caps nothing)
 *   - `UNBOUNDED ⊆ BOUNDED()` → FALSE (unbounded is WIDER than any bounded cap)
 *   - `BOUNDED(a) ⊆ BOUNDED(b)` iff every a ∈ b (so `BOUNDED([]) ⊆ BOUNDED(x)`)
 */
export function isCeilingSubset(child: Ceiling, parent: Ceiling): boolean {
  if (parent.mode === 'UNBOUNDED') return true;
  if (child.mode === 'UNBOUNDED') return false;
  const parentSet = new Set(parent.domains);
  return child.domains.every((d) => parentSet.has(d));
}

/**
 * `true` when the ceiling permits NOTHING — a `BOUNDED` cap with zero domains.
 * The OPPOSITE of `UNBOUNDED` (which permits everything, including
 * not-yet-known domains). These two states must never be conflated in the UI.
 */
export function permitsNothing(c: Ceiling): boolean {
  return c.mode === 'BOUNDED' && c.domains.length === 0;
}

/** Intersection of two ceilings (identity = UNBOUNDED). */
function intersectCeiling(a: Ceiling, b: Ceiling): Ceiling {
  if (a.mode === 'UNBOUNDED') return b;
  if (b.mode === 'UNBOUNDED') return a;
  const bSet = new Set(b.domains);
  return { mode: 'BOUNDED', domains: a.domains.filter((d) => bSet.has(d)) };
}

/**
 * The EFFECTIVE ceiling of node `id` — the intersection of every ceiling on
 * the root→node chain (a node can never be wider than any ancestor). The
 * intersection identity is `UNBOUNDED`, so an all-unbounded chain (or an `id`
 * absent from the list) resolves to `UNBOUNDED`. Cycle-safe.
 */
export function effectiveCeilingOf(nodes: OrgNode[], id: string): Ceiling {
  const byId = new Map<string, OrgNode>();
  for (const n of nodes) byId.set(n.orgNodeId, n);

  const chain: OrgNode[] = [];
  const seen = new Set<string>();
  let cur: OrgNode | undefined = byId.get(id);
  while (cur && !seen.has(cur.orgNodeId)) {
    seen.add(cur.orgNodeId);
    chain.push(cur);
    cur = cur.parentId !== null ? byId.get(cur.parentId) : undefined;
  }

  let eff: Ceiling = { mode: 'UNBOUNDED' };
  for (const n of chain) eff = intersectCeiling(eff, n.ceiling);
  return eff;
}
