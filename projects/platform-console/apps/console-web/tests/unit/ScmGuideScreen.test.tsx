import { describe, it, expect } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { ScmGuideScreen } from '@/features/scm-guide';
import {
  DOMAIN_SERVICES,
  PO_STATES,
  STALENESS_STATES,
  SUGGESTION_STATES,
  POLICY_FIELDS,
  SUPPLIER_FIELDS,
} from '@/features/scm-guide/data';
import { runAxe } from '../a11y/axe-helper';

/**
 * SCM 가이드 화면 (TASK-PC-FE-188) — 순수 정적 참조 화면. scm-platform 도메인
 * 서비스 구성과 3개 운영 화면(개요=발주+재고 가시성 · 보충 · 설정)이 보여주는
 * 값·상태머신이 렌더되는지 구조 단위로 단언한다. Iam/Wms/EcommerceGuideScreen.test
 * 와 동일 정책 — 설명 텍스트가 아니라 testid/구조를 단언한다.
 */
describe('ScmGuideScreen', () => {
  it('renders every top-level section', () => {
    render(<ScmGuideScreen />);
    expect(screen.getByTestId('scm-guide')).toBeInTheDocument();
    for (const id of [
      'scm-guide-services',
      'scm-guide-procurement',
      'scm-guide-visibility',
      'scm-guide-replenishment',
      'scm-guide-config',
    ]) {
      expect(screen.getByTestId(id)).toBeInTheDocument();
    }
  });

  it('renders every domain service row', () => {
    render(<ScmGuideScreen />);
    for (const s of DOMAIN_SERVICES) {
      const row = screen.getByTestId(`scm-guide-service-${s.key}`);
      expect(within(row).getByText(s.name)).toBeInTheDocument();
    }
    expect(DOMAIN_SERVICES.map((s) => s.key)).toEqual([
      'gateway',
      'procurement',
      'inventory-visibility',
      'demand-planning',
    ]);
  });

  it('renders the 9 PO lifecycle states with terminal distinction', () => {
    render(<ScmGuideScreen />);
    for (const s of PO_STATES) {
      const row = screen.getByTestId(`scm-guide-po-${s.name}`);
      expect(within(row).getByText(s.name)).toBeInTheDocument();
      expect(within(row).getByText(s.label)).toBeInTheDocument();
    }
    expect(PO_STATES.map((s) => s.name)).toEqual([
      'DRAFT',
      'SUBMITTED',
      'ACKNOWLEDGED',
      'CONFIRMED',
      'PARTIALLY_RECEIVED',
      'RECEIVED',
      'SETTLED',
      'CLOSED',
      'CANCELED',
    ]);
    // CLOSED + CANCELED are the terminal states.
    expect(PO_STATES.filter((s) => s.terminal).map((s) => s.name)).toEqual([
      'CLOSED',
      'CANCELED',
    ]);
  });

  it('renders the 3 node staleness states', () => {
    render(<ScmGuideScreen />);
    for (const s of STALENESS_STATES) {
      const row = screen.getByTestId(`scm-guide-staleness-${s.name}`);
      expect(within(row).getByText(s.name)).toBeInTheDocument();
    }
    expect(STALENESS_STATES.map((s) => s.name)).toEqual([
      'FRESH',
      'STALE',
      'UNREACHABLE',
    ]);
  });

  it('renders the 4 replenishment suggestion states with operator-actionable distinction', () => {
    render(<ScmGuideScreen />);
    for (const s of SUGGESTION_STATES) {
      const row = screen.getByTestId(`scm-guide-suggestion-${s.name}`);
      expect(within(row).getByText(s.name)).toBeInTheDocument();
    }
    expect(SUGGESTION_STATES.map((s) => s.name)).toEqual([
      'SUGGESTED',
      'APPROVED',
      'MATERIALIZED',
      'DISMISSED',
    ]);
    // Only SUGGESTED/APPROVED are operator-actionable (approve/dismiss).
    expect(
      SUGGESTION_STATES.filter((s) => s.operatorActionable).map((s) => s.name),
    ).toEqual(['SUGGESTED', 'APPROVED']);
  });

  it('renders every config field (reorder policy + supplier map)', () => {
    render(<ScmGuideScreen />);
    for (const f of POLICY_FIELDS) {
      expect(
        screen.getByTestId(`scm-guide-policy-fields-${f.key}`),
      ).toBeInTheDocument();
    }
    expect(POLICY_FIELDS.map((f) => f.field)).toEqual([
      'reorderPoint',
      'safetyStock',
      'reorderQty',
    ]);
    for (const f of SUPPLIER_FIELDS) {
      expect(
        screen.getByTestId(`scm-guide-supplier-fields-${f.key}`),
      ).toBeInTheDocument();
    }
    expect(SUPPLIER_FIELDS.map((f) => f.field)).toEqual([
      'supplierId',
      'defaultOrderQty',
      'leadTimeDays',
      'currency',
    ]);
  });

  it('renders the scm domain-role note (single SCM_OPERATOR + single-tenant axis)', () => {
    render(<ScmGuideScreen />);
    expect(screen.getByTestId('scm-guide-roles')).toBeInTheDocument();
  });

  it('is a pure static screen — no data-fetch, no permission gate (always renders)', () => {
    // No providers, no query client, no router — it must render standalone.
    const { container } = render(<ScmGuideScreen />);
    expect(
      container.querySelector('[data-testid="scm-guide"]'),
    ).not.toBeNull();
  });

  it('is WCAG AA axe-clean', async () => {
    const { container } = render(<ScmGuideScreen />);
    const violations = await runAxe(container);
    expect(violations).toEqual([]);
  });
});
