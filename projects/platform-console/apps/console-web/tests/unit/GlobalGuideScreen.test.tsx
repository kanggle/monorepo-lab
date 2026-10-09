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
import { navLeaves, RBAC_ROLES, RBAC_ROLE_NATURE } from '@/shared/guide/permission-map';
import { DOMAIN_FEATURES } from '@/shared/guide/domain-features';
import { SCREEN_COVERAGE } from '@/shared/sample/coverage';
import { runAxe } from '../a11y/axe-helper';
import { citedPath, citedPathExists } from '../helpers/cited-path';

/**
 * TASK-PC-FE-298 — 전역 가이드(/guide). 7개 탭 · 백엔드 호출 0 · 모든 인프라 사실에 인용 +
 * 인용된 경로 실재 · 전체 메뉴 표가 사이드바 전부를 덮음.
 *
 * TASK-PC-FE-321 — 맨 앞에 새 탭 「도메인 한눈에」가 추가되어 8개 탭이 됐다(AC-2).
 */
const EMAIL = 'visitor-guide@example.test';

afterEach(() => {
  vi.restoreAllMocks();
  window.location.hash = '';
});

describe('GlobalGuideScreen', () => {
  it('has the 8 tabs the ticket lists, 「도메인 한눈에」 first (TASK-PC-FE-321 AC-2)', () => {
    render(<GlobalGuideScreen demoLoginEmail={EMAIL} />);
    const tabs = within(screen.getByTestId('global-guide-tabs-list')).getAllByRole('tab');
    expect(tabs.map((t) => t.textContent)).toEqual([
      '도메인 한눈에',
      '시스템 아키텍처',
      '도메인별 서비스 구성',
      '서버 구성',
      '전체 메뉴 소개',
      '권한 및 테스트 계정',
      '대표 업무 흐름',
      '서버 기동·종료 방식',
    ]);
    expect(GLOBAL_GUIDE_TABS).toHaveLength(8);
    for (const t of GLOBAL_GUIDE_TABS) {
      expect(screen.getByTestId(`global-guide-tabs-panel-${t.id}`)).toHaveAttribute('role', 'tabpanel');
    }
  });

  it('「도메인 한눈에」 renders the 7 domains in data order, each with its items (TASK-PC-FE-321 AC-2)', () => {
    render(<GlobalGuideScreen demoLoginEmail={EMAIL} />);
    expect(DOMAIN_FEATURES).toHaveLength(7);
    const panel = screen.getByTestId('global-guide-tabs-panel-global-guide-domains');
    // headings appear in data order
    const headings = within(panel)
      .getAllByRole('heading', { level: 3 })
      .map((h) => h.textContent);
    expect(headings).toEqual(DOMAIN_FEATURES.map((d) => d.label));
    for (const d of DOMAIN_FEATURES) {
      const summary = within(panel).getByTestId(`global-guide-domain-${d.key}-summary`);
      expect(within(summary).getByText(d.oneLine)).toBeInTheDocument();
      // at least the first item of the first group renders
      const firstItem = d.groups[0].items[0];
      expect(within(summary).getByText(firstItem.text)).toBeInTheDocument();
    }
  });

  it('marks the fan directory «플랫폼 운영자 전용», never «콘솔 밖» — the screens exist, only gated (TASK-PC-FE-321)', () => {
    render(<GlobalGuideScreen demoLoginEmail={EMAIL} />);
    const fan = screen.getByTestId('global-guide-domain-fan-summary');
    const directory = within(fan).getByTestId('global-guide-domain-fan-summary-group-0');
    expect(directory).toHaveTextContent('아티스트 디렉터리');
    expect(within(directory).getAllByText('플랫폼 운영자 전용').length).toBeGreaterThan(0);
    expect(within(directory).queryByText('콘솔 밖')).not.toBeInTheDocument();
    // and no dead link for customer operators / visitors
    expect(within(directory).queryAllByRole('link')).toHaveLength(0);
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

  it('shows no source citations — «출처는 생략» (TASK-PC-FE-322 AC-2)', () => {
    render(<GlobalGuideScreen demoLoginEmail={EMAIL} />);
    const guide = screen.getByTestId('global-guide');
    expect(within(guide).queryByText(/^출처$/)).not.toBeInTheDocument();
    expect(within(guide).queryByRole('columnheader', { name: '출처' })).not.toBeInTheDocument();
    expect(within(guide).queryByRole('columnheader', { name: '근거' })).not.toBeInTheDocument();
    // a cited path from data.ts must not leak into the rendered page
    expect(guide.textContent).not.toContain('infra/demo/README.md');
  });

  it('the rewritten tabs carry no file paths, line numbers or ticket ids (TASK-PC-FE-322 AC-3)', () => {
    render(<GlobalGuideScreen demoLoginEmail={EMAIL} />);
    // 「도메인 한눈에」 is TASK-PC-FE-321's own data — out of this ticket's scope.
    const rewritten = GLOBAL_GUIDE_TABS.map((t) => t.id).filter((id) => id !== 'global-guide-domains');
    expect(rewritten).toHaveLength(7);
    const leaks: string[] = [];
    for (const id of rewritten) {
      const text = screen.getByTestId(`global-guide-tabs-panel-${id}`).textContent ?? '';
      for (const re of [/TASK-[A-Z]/, /ADR-[A-Z]/, /\.(md|sql|tsx?|ya?ml|sh|py)\b/, /:\d+(-\d+)?\b/]) {
        const m = text.match(re);
        if (m) leaks.push(`${id}: ${m[0]}`);
      }
    }
    expect(leaks).toEqual([]);
  });

  it('the service table lists each project exactly as its apps/ directory', () => {
    for (const g of DOMAIN_SERVICE_GROUPS) {
      for (const app of g.apps) {
        expect(citedPathExists(`projects/${g.project}/apps/${app}`), `${g.project}/${app}`).toBe(true);
      }
    }
  });

  it('the 전체 메뉴 소개 table keeps its «뎁스» column — only the domain guides drop it (TASK-PC-FE-330)', () => {
    render(<GlobalGuideScreen demoLoginEmail={EMAIL} />);
    const headers = within(screen.getByTestId('global-guide-map'))
      .getAllByRole('columnheader', { hidden: true })
      .map((h) => h.textContent);
    expect(headers).toContain('뎁스');
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

  it('every role column header names the role AND what kind of person holds it (TASK-PC-FE-327)', () => {
    render(<GlobalGuideScreen demoLoginEmail={EMAIL} />);
    for (const r of RBAC_ROLES) {
      const th = screen.getByTestId(`global-guide-rbac-role-${r}`);
      expect(th).toHaveTextContent(r);
      expect(RBAC_ROLE_NATURE[r].trim()).not.toBe('');
      expect(th).toHaveTextContent(RBAC_ROLE_NATURE[r]);
    }
    // The owner's own wording for the platform roles (rbac.md «CS L1 · CS L2 · 보안팀»).
    expect(screen.getByTestId('global-guide-rbac-role-SUPER_ADMIN')).toHaveTextContent('플랫폼 전체 관리자');
    expect(screen.getByTestId('global-guide-rbac-role-SUPPORT_READONLY')).toHaveTextContent('CS 1선');
    expect(screen.getByTestId('global-guide-rbac-role-SUPPORT_LOCK')).toHaveTextContent('CS 2선');
    expect(screen.getByTestId('global-guide-rbac-role-SECURITY_ANALYST')).toHaveTextContent('보안팀');
  });

  it('keyboard: ArrowRight / End / Home move the selected tab (roving tabindex)', () => {
    render(<GlobalGuideScreen demoLoginEmail={EMAIL} />);
    const tabs = within(screen.getByTestId('global-guide-tabs-list')).getAllByRole('tab');
    const last = tabs.length - 1; // 8 tabs since TASK-PC-FE-321 (도메인 한눈에 prepended)
    fireEvent.keyDown(tabs[0], { key: 'ArrowRight' });
    expect(tabs[1]).toHaveAttribute('aria-selected', 'true');
    expect(tabs[1]).toHaveAttribute('tabIndex', '0');
    expect(tabs[0]).toHaveAttribute('tabIndex', '-1');
    expect(screen.getByTestId('global-guide-tabs-panel-global-guide-architecture')).not.toHaveAttribute('hidden');
    expect(screen.getByTestId('global-guide-tabs-panel-global-guide-domains')).toHaveAttribute('hidden');
    fireEvent.keyDown(tabs[1], { key: 'End' });
    expect(tabs[last]).toHaveAttribute('aria-selected', 'true');
    fireEvent.keyDown(tabs[last], { key: 'ArrowRight' });
    expect(tabs[0]).toHaveAttribute('aria-selected', 'true'); // wraps
    fireEvent.keyDown(tabs[0], { key: 'ArrowLeft' });
    expect(tabs[last]).toHaveAttribute('aria-selected', 'true'); // wraps back
    fireEvent.keyDown(tabs[last], { key: 'Home' });
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
