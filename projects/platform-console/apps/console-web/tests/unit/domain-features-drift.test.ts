import { describe, it, expect } from 'vitest';
import { GROUPS, isParent } from '@/shared/ui/console-nav-config';
import { navLeaves } from '@/shared/guide/permission-map';
import { DOMAIN_FEATURES, type DomainFeatureKey } from '@/shared/guide/domain-features';

/**
 * TASK-PC-FE-321 — 도메인 요약(«도메인 한눈에») ↔ 사이드바 드리프트 가드.
 *
 * `permission-map-drift.test.ts`(TASK-PC-FE-298)와 같은 꼴 — 목록을 하드코딩하지 않고
 * `GROUPS`/`navLeaves()`(사이드바가 실제로 렌더하는 원장)를 직접 펴서 대조한다. 그래서
 * 메뉴가 사라지거나 옮겨지면, 또 사이드바에 새 도메인 드릴 부모가 생기면 이 파일이
 * 빨개진다(AC-4 bite, 티켓 AC-6).
 */

const navHrefs = navLeaves().map((l) => l.leaf.href);

function allItems() {
  return DOMAIN_FEATURES.flatMap((d) =>
    d.groups.flatMap((g) => g.items.map((item) => ({ domain: d.key, group: g.title, item }))),
  );
}

describe('domain-features ↔ nav (drift)', () => {
  it('every linked item (href) is a real sidebar leaf (AC-4a)', () => {
    const bad = allItems()
      .filter(({ item }) => item.href !== undefined && !navHrefs.includes(item.href))
      .map(({ domain, group, item }) => `${domain}/${group}: "${item.text}" → ${item.href}`);
    expect(bad, `hrefs that are not real nav leaves:\n${bad.join('\n')}`).toEqual([]);
  });

  it('every outsideConsole item has no href (AC-4b)', () => {
    const bad = allItems()
      .filter(({ item }) => item.outsideConsole === true && item.href !== undefined)
      .map(({ domain, group, item }) => `${domain}/${group}: "${item.text}"`);
    expect(bad, `outsideConsole items that still carry an href:\n${bad.join('\n')}`).toEqual([]);
  });

  it('every sidebar domain drill parent has a domain-features entry (AC-4c)', () => {
    // Derived from GROUPS itself — not hardcoded — so a new drill parent
    // (any group, not just 「도메인 운영」) trips this the moment it's added,
    // including a registry-gated one like fan (productKey) — gating is a
    // render-time concern (visibleGroups()), not a reason to skip it here.
    const parentKeys = GROUPS.flatMap((g) => g.items.filter(isParent).map((p) => p.key));
    const domainKeys = DOMAIN_FEATURES.map((d) => d.key);
    const missing = parentKeys.filter((k) => !domainKeys.includes(k as DomainFeatureKey));
    expect(
      missing,
      `sidebar drill parents with no domain-features entry: ${missing.join(', ')}`,
    ).toEqual([]);
    // and the reverse — every domain-features entry corresponds to a real parent
    // (catches a stale/renamed key in domain-features.ts).
    const orphanDomains = domainKeys.filter((k) => !parentKeys.includes(k));
    expect(
      orphanDomains,
      `domain-features entries with no matching sidebar drill parent: ${orphanDomains.join(', ')}`,
    ).toEqual([]);
  });

  it('non-vacuity: linked items ≥ number of domains (AC-4d)', () => {
    const linked = allItems().filter(({ item }) => item.href !== undefined);
    expect(linked.length).toBeGreaterThanOrEqual(DOMAIN_FEATURES.length);
  });

  it('every domain has 4-6 feature groups, each with at least one item (AC-1)', () => {
    for (const d of DOMAIN_FEATURES) {
      expect(d.groups.length, `${d.key}: group count`).toBeGreaterThanOrEqual(4);
      expect(d.groups.length, `${d.key}: group count`).toBeLessThanOrEqual(6);
      for (const g of d.groups) {
        expect(g.items.length, `${d.key}/${g.title}: item count`).toBeGreaterThanOrEqual(1);
      }
    }
    expect(DOMAIN_FEATURES.length).toBe(7);
    expect(DOMAIN_FEATURES.every((d) => d.oneLine.length > 0)).toBe(true);
  });
});
