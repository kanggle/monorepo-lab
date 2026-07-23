import { describe, it, expect } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { WmsGuideScreen } from '@/features/wms-guide';
import {
  STOCK_BUCKETS,
  RESERVATION_STAGES,
  INVENTORY_EVENTS,
  LOW_STOCK_MECHANISMS,
  ORDER_STATES,
  TMS_STATES,
  WMS_GLOSSARY,
  WMS_RECIPES,
  WMS_ROLES,
} from '@/features/wms-guide/data';
import { runAxe } from '../a11y/axe-helper';

/**
 * WMS 가이드 화면 (TASK-PC-FE-183) — 순수 정적 참조 화면. 재고(수량 버킷·예약
 * 흐름·저재고 이중 메커니즘·재고 변동 이벤트)와 출고(주문 상태머신·TMS 통보·
 * 도메인 롤)의 설명이 렌더되는지 구조 단위로 단언한다. IamGuideScreen.test 와
 * 동일 정책 — 설명 텍스트가 아니라 testid/구조를 단언한다.
 */
describe('WmsGuideScreen', () => {
  it('renders the two top-level sections (재고 · 출고)', () => {
    render(<WmsGuideScreen />);
    expect(screen.getByTestId('wms-guide')).toBeInTheDocument();
    expect(screen.getByTestId('wms-guide-inventory')).toBeInTheDocument();
    expect(screen.getByTestId('wms-guide-outbound')).toBeInTheDocument();
  });

  it('mounts the 읽기경로 배너 (GuideReadingPath) at the top of the guide (TASK-PC-FE-257)', () => {
    render(<WmsGuideScreen />);
    const banner = screen.getByTestId('wms-guide-reading-path');
    expect(banner).toBeInTheDocument();
    // Points at sections that actually exist on this guide (재고 · 출고).
    expect(within(banner).getByText('재고')).toBeInTheDocument();
    expect(within(banner).getByText('출고')).toBeInTheDocument();
  });

  it('cross-links the WMS domain-role note to the IAM guide (TASK-PC-FE-257)', () => {
    render(<WmsGuideScreen />);
    const link = screen.getByTestId('wms-guide-xlink-iam');
    expect(link.tagName).toBe('A');
    expect(link).toHaveAttribute('href', '/iam/guide');
  });

  it('renders the in-page TOC with a link per existing section id (TASK-PC-FE-255)', () => {
    render(<WmsGuideScreen />);
    const toc = screen.getByTestId('guide-toc');
    for (const id of [
      'wms-guide-inventory',
      'wms-guide-outbound',
      'wms-guide-roles',
    ]) {
      const link = within(toc).getByTestId(`guide-toc-${id}`);
      expect(link.getAttribute('href')).toBe(`#${id}`);
      // The linked section id must actually exist on the page — no drift.
      expect(document.getElementById(id)).not.toBeNull();
    }
  });

  it('renders the order-state flow diagram above the order-state table (TASK-PC-FE-255)', () => {
    render(<WmsGuideScreen />);
    const flow = screen.getByTestId('state-flow');
    for (const s of ORDER_STATES) {
      expect(within(flow).getByTestId(`state-flow-${s.name}`)).toBeInTheDocument();
    }
    // The table remains — the diagram is additive, not a replacement.
    expect(screen.getByTestId('wms-guide-order-states')).toBeInTheDocument();
  });

  it('renders every stock bucket with its field + pickability', () => {
    render(<WmsGuideScreen />);
    const table = screen.getByTestId('wms-guide-buckets');
    for (const b of STOCK_BUCKETS) {
      const row = screen.getByTestId(`wms-guide-bucket-${b.key}`);
      expect(within(row).getByText(b.label)).toBeInTheDocument();
      expect(within(row).getByText(b.field)).toBeInTheDocument();
    }
    // The four canonical buckets, in the derived-sum order.
    expect(STOCK_BUCKETS.map((b) => b.key)).toEqual([
      'available',
      'reserved',
      'damaged',
      'onHand',
    ]);
    // Only 가용 is pickable.
    expect(within(table).getByText('가능')).toBeInTheDocument();
  });

  it('renders the reservation flow (가용 ↔ 예약) stages', () => {
    render(<WmsGuideScreen />);
    RESERVATION_STAGES.forEach((_, i) => {
      expect(
        screen.getByTestId(`wms-guide-reservation-${i}`),
      ).toBeInTheDocument();
    });
  });

  it('renders BOTH low-stock mechanisms (table badge vs alert) — the divergence', () => {
    render(<WmsGuideScreen />);
    LOW_STOCK_MECHANISMS.forEach((m, i) => {
      const card = screen.getByTestId(`wms-guide-lowstock-${i}`);
      expect(within(card).getByText(m.where)).toBeInTheDocument();
      expect(within(card).getByText(m.threshold)).toBeInTheDocument();
    });
    expect(LOW_STOCK_MECHANISMS).toHaveLength(2);
  });

  it('renders every inventory-change event', () => {
    render(<WmsGuideScreen />);
    for (const e of INVENTORY_EVENTS) {
      const row = screen.getByTestId(`wms-guide-invevent-${e.event}`);
      expect(within(row).getByText(e.event)).toBeInTheDocument();
    }
  });

  it('renders the full 8-state order machine with terminal flags', () => {
    render(<WmsGuideScreen />);
    for (const s of ORDER_STATES) {
      const row = screen.getByTestId(`wms-guide-order-${s.name}`);
      expect(within(row).getByText(s.name)).toBeInTheDocument();
      expect(within(row).getByText(s.label)).toBeInTheDocument();
    }
    // The three terminal states are exactly SHIPPED / CANCELLED / BACKORDERED.
    expect(
      ORDER_STATES.filter((s) => s.terminal).map((s) => s.name),
    ).toEqual(['SHIPPED', 'CANCELLED', 'BACKORDERED']);
  });

  it('renders the TMS notification states', () => {
    render(<WmsGuideScreen />);
    for (const t of TMS_STATES) {
      const row = screen.getByTestId(`wms-guide-tms-${t.name}`);
      expect(within(row).getByText(t.name)).toBeInTheDocument();
    }
    expect(TMS_STATES.map((t) => t.name)).toEqual([
      'PENDING',
      'NOTIFIED',
      'NOTIFY_FAILED',
    ]);
  });

  it('mounts the 작업 레시피 (GuideRecipe) with numbered steps that reference real states (TASK-PC-FE-256)', () => {
    render(<WmsGuideScreen />);
    expect(screen.getByTestId('wms-guide-recipes')).toBeInTheDocument();
    // 2~3 recipes, each an actual GuideRecipe card with numbered steps.
    expect(WMS_RECIPES.length).toBeGreaterThanOrEqual(2);
    expect(WMS_RECIPES.length).toBeLessThanOrEqual(3);
    WMS_RECIPES.forEach((recipe, i) => {
      const card = screen.getByTestId(`wms-guide-recipe-${i}`);
      expect(within(card).getByText(recipe.title)).toBeInTheDocument();
      // A numbered <ol> with one <li> per step (not just "a component exists").
      expect(card.querySelector('ol')).not.toBeNull();
      recipe.steps.forEach((_, s) => {
        expect(
          within(card).getByTestId(`wms-guide-recipe-${i}-step-${s}`),
        ).toBeInTheDocument();
      });
    });
  });

  it('mounts the 용어집 (Glossary) — each term defined actually renders in this guide (TASK-PC-FE-256)', () => {
    render(<WmsGuideScreen />);
    const glossary = screen.getByTestId('wms-guide-glossary-table');
    expect(glossary).toBeInTheDocument();
    for (const entry of WMS_GLOSSARY) {
      const row = within(glossary).getByTestId(
        `wms-guide-glossary-table-${entry.key}`,
      );
      // Definition visible in the row (keyboard/mobile reachable, not hover-only).
      expect(within(row).getByText(entry.meaning)).toBeInTheDocument();
      expect(row.querySelector('dfn')).not.toBeNull();
    }
  });

  it('renders the WMS domain roles (separate axis from IAM roles)', () => {
    render(<WmsGuideScreen />);
    const table = screen.getByTestId('wms-guide-roles');
    for (const r of WMS_ROLES) {
      expect(within(table).getByText(r.role)).toBeInTheDocument();
    }
  });

  it('is a pure static screen — no data-fetch, no permission gate (always renders)', () => {
    // No providers, no query client, no router — it must render standalone.
    const { container } = render(<WmsGuideScreen />);
    expect(container.querySelector('[data-testid="wms-guide"]')).not.toBeNull();
  });

  it('is WCAG AA axe-clean', async () => {
    const { container } = render(<WmsGuideScreen />);
    const violations = await runAxe(container);
    expect(violations).toEqual([]);
  });
});
