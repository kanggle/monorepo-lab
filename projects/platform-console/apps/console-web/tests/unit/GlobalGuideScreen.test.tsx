import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, screen, within, fireEvent } from '@testing-library/react';
import { GlobalGuideScreen, GLOBAL_GUIDE_TABS } from '@/features/global-guide';
import {
  ARCHITECTURE_FACTS,
  BUSINESS_FLOWS,
  DOMAIN_SERVICE_GROUPS,
  SERVER_LAYERS,
  SHUTDOWN_RULES,
  STARTUP_STEPS,
} from '@/features/global-guide/data';
import { navLeaves } from '@/shared/guide/permission-map';
import { SCREEN_COVERAGE } from '@/shared/sample/coverage';
import { runAxe } from '../a11y/axe-helper';
import { citedPath, citedPathExists } from '../helpers/cited-path';

/**
 * TASK-PC-FE-298 — 전역 가이드(/guide). 7개 탭 · 백엔드 호출 0 · 모든 인프라 사실에 인용 +
 * 인용된 경로 실재 · 전체 메뉴 표가 사이드바 전부를 덮음.
 */
const EMAIL = 'visitor-guide@example.test';

afterEach(() => {
  vi.restoreAllMocks();
  window.location.hash = '';
});

describe('GlobalGuideScreen', () => {
  it('has the 7 tabs the ticket lists, in order (AC-1)', () => {
    render(<GlobalGuideScreen demoLoginEmail={EMAIL} />);
    const tabs = within(screen.getByTestId('global-guide-tabs-list')).getAllByRole('tab');
    expect(tabs.map((t) => t.textContent)).toEqual([
      '시스템 아키텍처',
      '도메인별 서비스 구성',
      '서버 구성',
      '전체 메뉴 소개',
      '권한 및 테스트 계정',
      '대표 업무 흐름',
      '서버 기동·종료 방식',
    ]);
    expect(GLOBAL_GUIDE_TABS).toHaveLength(7);
    for (const t of GLOBAL_GUIDE_TABS) {
      expect(screen.getByTestId(`global-guide-tabs-panel-${t.id}`)).toHaveAttribute('role', 'tabpanel');
    }
  });

  it('renders with NO backend call — a static page for logged-out sample visitors (AC-1)', () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch');
    render(<GlobalGuideScreen demoLoginEmail={EMAIL} />);
    expect(fetchSpy).not.toHaveBeenCalled();
    expect(SCREEN_COVERAGE['/guide']).toBe('static');
  });

  it('every infrastructure fact cites a repo file, and every cited path exists (AC-6)', () => {
    const cited = [
      ...ARCHITECTURE_FACTS,
      ...SERVER_LAYERS.flatMap((l) => l.facts),
      ...STARTUP_STEPS,
      ...SHUTDOWN_RULES,
      ...BUSINESS_FLOWS,
      ...DOMAIN_SERVICE_GROUPS,
    ];
    expect(cited.length).toBeGreaterThan(20);
    const bad: string[] = [];
    for (const item of cited) {
      const label = 'title' in item ? item.title : item.project;
      if (item.sources.length === 0) bad.push(`${label}: no source`);
      for (const s of item.sources) {
        if (!citedPathExists(s)) bad.push(`${label}: ${citedPath(s)}`);
      }
    }
    expect(bad, 'cited paths that do not exist').toEqual([]);
  });

  it('the service table lists each project exactly as its apps/ directory', () => {
    for (const g of DOMAIN_SERVICE_GROUPS) {
      for (const app of g.apps) {
        expect(citedPathExists(`projects/${g.project}/apps/${app}`), `${g.project}/${app}`).toBe(true);
      }
    }
  });

  it('the 전체 메뉴 소개 table covers every sidebar leaf', () => {
    render(<GlobalGuideScreen demoLoginEmail={EMAIL} />);
    const table = screen.getByTestId('global-guide-map');
    for (const { leaf } of navLeaves()) {
      expect(within(table).getByTestId(`global-guide-map-row-${leaf.href}`)).toBeInTheDocument();
    }
  });

  it('shows the test account from props, the role matrix, and the recorded mismatches', () => {
    render(<GlobalGuideScreen demoLoginEmail={EMAIL} />);
    expect(screen.getByTestId('global-guide-test-account')).toHaveTextContent(EMAIL);
    expect(screen.getByTestId('global-guide-rbac-partnership.manage')).toBeInTheDocument();
    const mismatches = screen.getByTestId('global-guide-mismatches');
    expect(mismatches).toHaveTextContent('/permissions');
    expect(mismatches).toHaveTextContent('/permission-sets');
  });

  it('keyboard: ArrowRight / End / Home move the selected tab (roving tabindex)', () => {
    render(<GlobalGuideScreen demoLoginEmail={EMAIL} />);
    const tabs = within(screen.getByTestId('global-guide-tabs-list')).getAllByRole('tab');
    fireEvent.keyDown(tabs[0], { key: 'ArrowRight' });
    expect(tabs[1]).toHaveAttribute('aria-selected', 'true');
    expect(tabs[1]).toHaveAttribute('tabIndex', '0');
    expect(tabs[0]).toHaveAttribute('tabIndex', '-1');
    expect(screen.getByTestId('global-guide-tabs-panel-global-guide-services')).not.toHaveAttribute('hidden');
    expect(screen.getByTestId('global-guide-tabs-panel-global-guide-architecture')).toHaveAttribute('hidden');
    fireEvent.keyDown(tabs[1], { key: 'End' });
    expect(tabs[6]).toHaveAttribute('aria-selected', 'true');
    fireEvent.keyDown(tabs[6], { key: 'ArrowRight' });
    expect(tabs[0]).toHaveAttribute('aria-selected', 'true'); // wraps
    fireEvent.keyDown(tabs[0], { key: 'ArrowLeft' });
    expect(tabs[6]).toHaveAttribute('aria-selected', 'true'); // wraps back
    fireEvent.keyDown(tabs[6], { key: 'Home' });
    expect(tabs[0]).toHaveAttribute('aria-selected', 'true');
  });

  it('a URL hash naming a tab opens that tab (shareable deep link)', () => {
    window.location.hash = '#global-guide-lifecycle';
    render(<GlobalGuideScreen demoLoginEmail={EMAIL} />);
    expect(screen.getByTestId('global-guide-tabs-tab-global-guide-lifecycle')).toHaveAttribute(
      'aria-selected',
      'true',
    );
  });

  it('is WCAG AA axe-clean', async () => {
    const { container } = render(<GlobalGuideScreen demoLoginEmail={EMAIL} />);
    expect(await runAxe(container)).toEqual([]);
  });
});
