import { expect } from 'vitest';
import { screen, within } from '@testing-library/react';
import {
  DOMAIN_GUIDE_TAB_KEYS,
  DOMAIN_GUIDE_TAB_LABELS,
  type DomainGuideTabKey,
} from '@/shared/guide/DomainGuideTabs';

/**
 * TASK-PC-FE-298 — 도메인 가이드 6개의 공통 단언: 탭(PC-FE-329 이후 7개)이 정해진 이름·순서로 있고,
 * 기존 섹션(id)이 **기대한 탭 패널 안으로 옮겨졌는지**. 이것이 PC-FE-255 의 페이지 내
 * 목차(GuideToc) 단언을 대체한다 — 목차는 탭 목록이 대신한다.
 */
export function expectDomainGuideTabs(
  prefix: string,
  sectionsByTab: Partial<Record<DomainGuideTabKey, string[]>>,
) {
  const tablist = screen.getByTestId(`${prefix}-tabs-list`);
  const tabs = within(tablist).getAllByRole('tab');
  expect(tabs.map((t) => t.textContent)).toEqual(
    DOMAIN_GUIDE_TAB_KEYS.map((k) => DOMAIN_GUIDE_TAB_LABELS[k]),
  );
  // exactly one selected (the first) on first render
  expect(tabs.filter((t) => t.getAttribute('aria-selected') === 'true')).toHaveLength(1);
  expect(tabs[0]).toHaveAttribute('aria-selected', 'true');

  for (const key of DOMAIN_GUIDE_TAB_KEYS) {
    const panel = screen.getByTestId(`${prefix}-tabs-panel-${prefix}-tab-${key}`);
    expect(panel).toHaveAttribute('role', 'tabpanel');
    for (const id of sectionsByTab[key] ?? []) {
      const el = document.getElementById(id);
      expect(el, `section #${id} exists`).not.toBeNull();
      expect(panel.contains(el), `section #${id} lives in the ${key} tab`).toBe(true);
    }
  }
  // the generated tab is populated from the permission map — TASK-PC-FE-329: one 「메뉴 안내」
  // card per menu now carries what the old «메뉴별 사용 절차» tab added (여는 곳 · 필요한 권한).
  const cards = screen.getByTestId(`${prefix}-menu-cards`);
  expect(cards.children.length).toBeGreaterThan(0);
  for (const card of Array.from(cards.children)) {
    expect(card).toHaveTextContent('여는 곳');
    expect(card).toHaveTextContent('필요한 권한');
  }
  expect(screen.queryByTestId(`${prefix}-menu-procedures`)).toBeNull();
  const map = screen.getByTestId(`${prefix}-map`);
  // TASK-PC-FE-330 — 「메뉴별 권한」 has no «뎁스» column in a domain guide.
  // `hidden: true` — the table sits in the 「권한 안내」 panel, which is not the selected tab.
  const headers = within(map).getAllByRole('columnheader', { hidden: true }).map((h) => h.textContent);
  expect(headers).toContain('메뉴명');
  expect(headers).not.toContain('뎁스');
}
