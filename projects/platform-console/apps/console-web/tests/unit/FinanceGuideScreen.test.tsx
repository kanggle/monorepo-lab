import { describe, it, expect } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { FinanceGuideScreen } from '@/features/finance-guide';
import {
  DOMAIN_SERVICES,
  CONSOLE_SCREENS,
  ACCOUNT_STATES,
  KYC_LEVELS,
} from '@/features/finance-guide/data';
import { runAxe } from '../a11y/axe-helper';

/**
 * Finance 가이드 화면 (TASK-PC-FE-229) — 순수 정적 참조 화면. finance-platform
 * 도메인 서비스 구성과 4개 콘솔 화면(개요·가이드·계좌·원장)이 보여주는 값·
 * 개념(계좌 상태·KYC·F5·복식부기·대사·FX)이 렌더되는지 구조 단위로 단언한다.
 * ScmGuideScreen.test 와 동일 정책 — 설명 텍스트가 아니라 testid/구조를
 * 단언한다.
 */
describe('FinanceGuideScreen', () => {
  it('renders every top-level section', () => {
    render(<FinanceGuideScreen />);
    expect(screen.getByTestId('finance-guide')).toBeInTheDocument();
    for (const id of [
      'finance-guide-services',
      'finance-guide-screens',
      'finance-guide-account-states',
      'finance-guide-kyc',
      'finance-guide-concepts',
    ]) {
      expect(screen.getByTestId(id)).toBeInTheDocument();
    }
  });

  it('renders both domain service rows (account-service, ledger-service)', () => {
    render(<FinanceGuideScreen />);
    for (const s of DOMAIN_SERVICES) {
      const row = screen.getByTestId(`finance-guide-service-${s.key}`);
      expect(within(row).getByText(s.name)).toBeInTheDocument();
    }
    expect(DOMAIN_SERVICES.map((s) => s.key)).toEqual([
      'account-service',
      'ledger-service',
    ]);
  });

  it('renders every console screen row (개요·가이드·계좌·원장)', () => {
    render(<FinanceGuideScreen />);
    for (const s of CONSOLE_SCREENS) {
      const row = screen.getByTestId(`finance-guide-screen-${s.key}`);
      expect(within(row).getByText(s.label)).toBeInTheDocument();
    }
    expect(CONSOLE_SCREENS.map((s) => s.key)).toEqual([
      'overview',
      'guide',
      'accounts',
      'ledger',
    ]);
  });

  it('renders the 5 regulated account states with attention distinction', () => {
    render(<FinanceGuideScreen />);
    for (const s of ACCOUNT_STATES) {
      const row = screen.getByTestId(`finance-guide-account-state-${s.name}`);
      expect(within(row).getByText(s.name)).toBeInTheDocument();
    }
    expect(ACCOUNT_STATES.map((s) => s.name)).toEqual([
      'PENDING_KYC',
      'ACTIVE',
      'RESTRICTED',
      'FROZEN',
      'CLOSED',
    ]);
    // FROZEN/RESTRICTED/PENDING_KYC need operator attention; ACTIVE/CLOSED do not.
    expect(
      ACCOUNT_STATES.filter((s) => s.attention).map((s) => s.name),
    ).toEqual(['PENDING_KYC', 'RESTRICTED', 'FROZEN']);
  });

  it('renders the 3 KYC levels', () => {
    render(<FinanceGuideScreen />);
    for (const k of KYC_LEVELS) {
      expect(screen.getByTestId(`finance-guide-kyc-${k.name}`)).toBeInTheDocument();
    }
    expect(KYC_LEVELS.map((k) => k.name)).toEqual(['NONE', 'BASIC', 'FULL']);
  });

  it('is a pure static screen — no data-fetch, no permission gate (always renders)', () => {
    // No providers, no query client, no router — it must render standalone.
    const { container } = render(<FinanceGuideScreen />);
    expect(
      container.querySelector('[data-testid="finance-guide"]'),
    ).not.toBeNull();
  });

  it('is WCAG AA axe-clean', async () => {
    const { container } = render(<FinanceGuideScreen />);
    const violations = await runAxe(container);
    expect(violations).toEqual([]);
  });
});
