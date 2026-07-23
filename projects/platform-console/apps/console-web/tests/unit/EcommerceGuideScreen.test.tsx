import { describe, it, expect } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { EcommerceGuideScreen } from '@/features/ecommerce-guide';
import {
  DOMAIN_SERVICES,
  ORDER_STATES,
  PAYMENT_STATES,
  SHIPPING_STATES,
  PRODUCT_STATES,
  PRODUCT_CONCEPTS,
  PROMOTION_STATES,
  DISCOUNT_TYPES,
  SELLER_STATES,
  USER_STATES,
  TEMPLATE_TYPES,
  NOTIFICATION_CHANNELS,
  ECOMMERCE_GLOSSARY,
  ECOMMERCE_RECIPES,
} from '@/features/ecommerce-guide/data';
import { runAxe } from '../a11y/axe-helper';

/**
 * E-Commerce 가이드 화면 (TASK-PC-FE-184) — 순수 정적 참조 화면. 도메인 서비스
 * 구성과 7개 운영 화면(상품·주문·배송·프로모션·사용자·셀러·알림)이 보여주는
 * 상태값·상태머신이 렌더되는지 구조 단위로 단언한다. IamGuideScreen /
 * WmsGuideScreen.test 와 동일 정책 — 설명 텍스트가 아니라 testid/구조를 단언한다.
 */
describe('EcommerceGuideScreen', () => {
  it('renders every top-level section', () => {
    render(<EcommerceGuideScreen />);
    expect(screen.getByTestId('ecommerce-guide')).toBeInTheDocument();
    for (const id of [
      'ecommerce-guide-services',
      'ecommerce-guide-order',
      'ecommerce-guide-payment',
      'ecommerce-guide-shipping',
      'ecommerce-guide-product',
      'ecommerce-guide-promotion',
      'ecommerce-guide-seller',
      'ecommerce-guide-user',
      'ecommerce-guide-notification',
    ]) {
      expect(screen.getByTestId(id)).toBeInTheDocument();
    }
  });

  it('mounts the 읽기경로 배너 (GuideReadingPath) and cross-links WMS fulfillment to the WMS guide (TASK-PC-FE-257)', () => {
    render(<EcommerceGuideScreen />);
    const banner = screen.getByTestId('ecommerce-guide-reading-path');
    expect(banner).toBeInTheDocument();
    // Points at sections that actually exist on this guide (주문 · 배송).
    expect(within(banner).getByText('주문')).toBeInTheDocument();
    expect(within(banner).getByText('배송')).toBeInTheDocument();
    // The WMS-routed shipping note cross-links to the WMS guide.
    const xlink = screen.getByTestId('ecommerce-guide-xlink-wms');
    expect(xlink.tagName).toBe('A');
    expect(xlink).toHaveAttribute('href', '/wms/guide');
  });

  it('renders every domain service row', () => {
    render(<EcommerceGuideScreen />);
    for (const s of DOMAIN_SERVICES) {
      const row = screen.getByTestId(`ecommerce-guide-service-${s.key}`);
      expect(within(row).getByText(s.name)).toBeInTheDocument();
    }
  });

  it('renders the 6 console order states with operator-actionable distinction', () => {
    render(<EcommerceGuideScreen />);
    for (const s of ORDER_STATES) {
      const row = screen.getByTestId(`ecommerce-guide-order-${s.name}`);
      expect(within(row).getByText(s.name)).toBeInTheDocument();
      expect(within(row).getByText(s.label)).toBeInTheDocument();
    }
    // The console order enum is exactly these six (BACKORDERED is backend-only).
    expect(ORDER_STATES.map((s) => s.name)).toEqual([
      'PENDING',
      'CONFIRMED',
      'SHIPPED',
      'DELIVERED',
      'CANCELLED',
      'STUCK_RECOVERY_FAILED',
    ]);
    // Only 대기/확정 are operator-actionable; SHIPPED/DELIVERED are event-driven.
    expect(
      ORDER_STATES.filter((s) => s.operatorActionable).map((s) => s.name),
    ).toEqual(['PENDING', 'CONFIRMED']);
  });

  it('renders the 6 payment states', () => {
    render(<EcommerceGuideScreen />);
    for (const p of PAYMENT_STATES) {
      const row = screen.getByTestId(`ecommerce-guide-payment-${p.name}`);
      expect(within(row).getByText(p.name)).toBeInTheDocument();
    }
    expect(PAYMENT_STATES.map((p) => p.name)).toEqual([
      'PENDING',
      'COMPLETED',
      'FAILED',
      'PARTIALLY_REFUNDED',
      'REFUNDED',
      'VOIDED',
    ]);
  });

  it('renders the strictly-linear 4-state shipping machine', () => {
    render(<EcommerceGuideScreen />);
    for (const s of SHIPPING_STATES) {
      const row = screen.getByTestId(`ecommerce-guide-shipping-${s.name}`);
      expect(within(row).getByText(s.name)).toBeInTheDocument();
    }
    expect(SHIPPING_STATES.map((s) => s.name)).toEqual([
      'PREPARING',
      'SHIPPED',
      'IN_TRANSIT',
      'DELIVERED',
    ]);
    // DELIVERED is the sole terminal.
    expect(SHIPPING_STATES.filter((s) => s.terminal).map((s) => s.name)).toEqual(
      ['DELIVERED'],
    );
  });

  it('renders product states + concepts', () => {
    render(<EcommerceGuideScreen />);
    for (const p of PRODUCT_STATES) {
      expect(
        screen.getByTestId(`ecommerce-guide-product-${p.name}`),
      ).toBeInTheDocument();
    }
    expect(PRODUCT_STATES.map((p) => p.name)).toEqual([
      'ON_SALE',
      'SOLD_OUT',
      'HIDDEN',
    ]);
    for (const c of PRODUCT_CONCEPTS) {
      expect(
        screen.getByTestId(`ecommerce-guide-product-concept-${c.key}`),
      ).toBeInTheDocument();
    }
  });

  it('renders promotion states + both discount types', () => {
    render(<EcommerceGuideScreen />);
    for (const p of PROMOTION_STATES) {
      expect(
        screen.getByTestId(`ecommerce-guide-promotion-${p.name}`),
      ).toBeInTheDocument();
    }
    expect(PROMOTION_STATES.map((p) => p.name)).toEqual([
      'SCHEDULED',
      'ACTIVE',
      'ENDED',
    ]);
    for (const d of DISCOUNT_TYPES) {
      expect(
        screen.getByTestId(`ecommerce-guide-discount-${d.name}`),
      ).toBeInTheDocument();
    }
    expect(DISCOUNT_TYPES.map((d) => d.name)).toEqual(['FIXED', 'PERCENTAGE']);
  });

  it('renders the 4-state seller lifecycle', () => {
    render(<EcommerceGuideScreen />);
    for (const s of SELLER_STATES) {
      const row = screen.getByTestId(`ecommerce-guide-seller-${s.name}`);
      expect(within(row).getByText(s.name)).toBeInTheDocument();
    }
    expect(SELLER_STATES.map((s) => s.name)).toEqual([
      'PENDING_PROVISIONING',
      'ACTIVE',
      'SUSPENDED',
      'CLOSED',
    ]);
  });

  it('renders the 3 read-only user states', () => {
    render(<EcommerceGuideScreen />);
    for (const u of USER_STATES) {
      expect(
        screen.getByTestId(`ecommerce-guide-user-${u.name}`),
      ).toBeInTheDocument();
    }
    expect(USER_STATES.map((u) => u.name)).toEqual([
      'ACTIVE',
      'SUSPENDED',
      'WITHDRAWN',
    ]);
  });

  it('renders every notification template type + channel', () => {
    render(<EcommerceGuideScreen />);
    for (const t of TEMPLATE_TYPES) {
      const row = screen.getByTestId(`ecommerce-guide-template-${t.name}`);
      expect(within(row).getByText(t.name)).toBeInTheDocument();
    }
    expect(TEMPLATE_TYPES.map((t) => t.name)).toEqual([
      'ORDER_PLACED',
      'PAYMENT_COMPLETED',
      'SHIPPING_STATUS_CHANGED',
      'WELCOME',
    ]);
    for (const c of NOTIFICATION_CHANNELS) {
      expect(
        screen.getByTestId(`ecommerce-guide-channel-${c.name}`),
      ).toBeInTheDocument();
    }
    expect(NOTIFICATION_CHANNELS.map((c) => c.name)).toEqual([
      'EMAIL',
      'SMS',
      'PUSH',
    ]);
  });

  it('mounts 작업 레시피 (GuideRecipe) with numbered steps (TASK-PC-FE-256)', () => {
    render(<EcommerceGuideScreen />);
    expect(screen.getByTestId('ecommerce-guide-recipes')).toBeInTheDocument();
    expect(ECOMMERCE_RECIPES.length).toBeGreaterThanOrEqual(2);
    expect(ECOMMERCE_RECIPES.length).toBeLessThanOrEqual(3);
    ECOMMERCE_RECIPES.forEach((recipe, i) => {
      const card = screen.getByTestId(`ecommerce-guide-recipe-${i}`);
      expect(within(card).getByText(recipe.title)).toBeInTheDocument();
      expect(card.querySelector('ol')).not.toBeNull();
      recipe.steps.forEach((_, s) => {
        expect(
          within(card).getByTestId(`ecommerce-guide-recipe-${i}-step-${s}`),
        ).toBeInTheDocument();
      });
    });
  });

  it('mounts 용어집 (Glossary) with always-visible definitions (TASK-PC-FE-256)', () => {
    render(<EcommerceGuideScreen />);
    const glossary = screen.getByTestId('ecommerce-guide-glossary-table');
    expect(glossary).toBeInTheDocument();
    for (const entry of ECOMMERCE_GLOSSARY) {
      const row = within(glossary).getByTestId(
        `ecommerce-guide-glossary-table-${entry.key}`,
      );
      expect(within(row).getByText(entry.meaning)).toBeInTheDocument();
    }
  });

  it('renders the ecommerce domain-role note (single ECOMMERCE_OPERATOR axis)', () => {
    render(<EcommerceGuideScreen />);
    expect(screen.getByTestId('ecommerce-guide-roles')).toBeInTheDocument();
  });

  it('is a pure static screen — no data-fetch, no permission gate (always renders)', () => {
    // No providers, no query client, no router — it must render standalone.
    const { container } = render(<EcommerceGuideScreen />);
    expect(
      container.querySelector('[data-testid="ecommerce-guide"]'),
    ).not.toBeNull();
  });

  it('is WCAG AA axe-clean', async () => {
    const { container } = render(<EcommerceGuideScreen />);
    const violations = await runAxe(container);
    expect(violations).toEqual([]);
  });
});
