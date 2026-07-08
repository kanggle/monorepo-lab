import { describe, it, expect } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { ErpGuideScreen } from '@/features/erp-guide';
import {
  DOMAIN_SERVICES,
  CONSOLE_SCREENS,
  MASTER_STATUSES,
  EMPLOYMENT_STATUSES,
  APPROVAL_STATUSES,
  DELEGATION_SCOPES,
} from '@/features/erp-guide/data';
import { runAxe } from '../a11y/axe-helper';

/**
 * ERP 가이드 화면 (TASK-PC-FE-232) — 순수 정적 참조 화면. erp-platform
 * 도메인 서비스 구성과 6개 콘솔 화면(개요·가이드·마스터·통합 조회·결재함·
 * 위임)이 보여주는 값·개념(마스터/직원 상태·결재 상태머신·위임 스코프·
 * asOf·read-model·알림)이 렌더되는지 구조 단위로 단언한다. FinanceGuideScreen
 * / ScmGuideScreen.test 와 동일 정책 — 설명 텍스트가 아니라 testid/구조를
 * 단언한다.
 */
describe('ErpGuideScreen', () => {
  it('renders every top-level section', () => {
    render(<ErpGuideScreen />);
    expect(screen.getByTestId('erp-guide')).toBeInTheDocument();
    for (const id of [
      'erp-guide-services',
      'erp-guide-screens',
      'erp-guide-master-states',
      'erp-guide-employment-states',
      'erp-guide-approval-states',
      'erp-guide-delegation-scopes',
      'erp-guide-concepts',
    ]) {
      expect(screen.getByTestId(id)).toBeInTheDocument();
    }
  });

  it('renders all 4 domain service rows', () => {
    render(<ErpGuideScreen />);
    for (const s of DOMAIN_SERVICES) {
      const row = screen.getByTestId(`erp-guide-service-${s.key}`);
      expect(within(row).getByText(s.name)).toBeInTheDocument();
    }
    expect(DOMAIN_SERVICES.map((s) => s.key)).toEqual([
      'masterdata-service',
      'approval-service',
      'read-model-service',
      'notification-service',
    ]);
  });

  it('renders every console screen row (개요·가이드·마스터·통합 조회·결재함·위임)', () => {
    render(<ErpGuideScreen />);
    for (const s of CONSOLE_SCREENS) {
      const row = screen.getByTestId(`erp-guide-screen-${s.key}`);
      expect(within(row).getByText(s.label)).toBeInTheDocument();
    }
    expect(CONSOLE_SCREENS.map((s) => s.key)).toEqual([
      'overview',
      'guide',
      'masters',
      'orgview',
      'approval',
      'delegation',
    ]);
  });

  it('renders the 2 master statuses with attention distinction', () => {
    render(<ErpGuideScreen />);
    for (const s of MASTER_STATUSES) {
      const row = screen.getByTestId(`erp-guide-master-state-${s.name}`);
      expect(within(row).getByText(s.name)).toBeInTheDocument();
    }
    expect(MASTER_STATUSES.map((s) => s.name)).toEqual(['ACTIVE', 'RETIRED']);
    expect(
      MASTER_STATUSES.filter((s) => s.attention).map((s) => s.name),
    ).toEqual(['RETIRED']);
  });

  it('renders the 3 employment statuses', () => {
    render(<ErpGuideScreen />);
    for (const s of EMPLOYMENT_STATUSES) {
      expect(
        screen.getByTestId(`erp-guide-employment-state-${s.name}`),
      ).toBeInTheDocument();
    }
    expect(EMPLOYMENT_STATUSES.map((s) => s.name)).toEqual([
      'EMPLOYED',
      'ON_LEAVE',
      'SEPARATED',
    ]);
  });

  it('renders the 6 approval states with terminal distinction', () => {
    render(<ErpGuideScreen />);
    for (const s of APPROVAL_STATUSES) {
      const row = screen.getByTestId(`erp-guide-approval-state-${s.name}`);
      expect(within(row).getByText(s.name)).toBeInTheDocument();
    }
    expect(APPROVAL_STATUSES.map((s) => s.name)).toEqual([
      'DRAFT',
      'SUBMITTED',
      'IN_REVIEW',
      'APPROVED',
      'REJECTED',
      'WITHDRAWN',
    ]);
    expect(
      APPROVAL_STATUSES.filter((s) => s.terminal).map((s) => s.name),
    ).toEqual(['APPROVED', 'REJECTED', 'WITHDRAWN']);
  });

  it('renders the 2 delegation scopes', () => {
    render(<ErpGuideScreen />);
    for (const s of DELEGATION_SCOPES) {
      expect(
        screen.getByTestId(`erp-guide-delegation-scope-${s.name}`),
      ).toBeInTheDocument();
    }
    expect(DELEGATION_SCOPES.map((s) => s.name)).toEqual([
      'GLOBAL',
      'REQUEST',
    ]);
  });

  it('is a pure static screen — no data-fetch, no permission gate (always renders)', () => {
    // No providers, no query client, no router — it must render standalone.
    const { container } = render(<ErpGuideScreen />);
    expect(
      container.querySelector('[data-testid="erp-guide"]'),
    ).not.toBeNull();
  });

  it('is WCAG AA axe-clean', async () => {
    const { container } = render(<ErpGuideScreen />);
    const violations = await runAxe(container);
    expect(violations).toEqual([]);
  });
});
