import { describe, it, expect } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { GuideToc, StateFlow } from '@/shared/ui/guide-primitives';
import { runAxe } from '../a11y/axe-helper';

/**
 * 공용 도메인 가이드 원자 GuideToc · StateFlow (TASK-PC-FE-255) — 순수 정적
 * presentation. 각 GuideScreen 이 넘긴 `items`/`states` 를 그대로 렌더하는지
 * 구조 단위로 단언한다(하드코딩된 목차/흐름이 아니라는 것은 개별 GuideScreen
 * 테스트에서 화면 고유 section id 로 재확인).
 */
describe('GuideToc', () => {
  const items = [
    { id: 'sec-a', label: '섹션 A' },
    { id: 'sec-b', label: '섹션 B' },
    { id: 'sec-c', label: '섹션 C' },
  ];

  it('renders one anchor link per item, each pointing at #id', () => {
    render(<GuideToc items={items} />);
    const nav = screen.getByTestId('guide-toc');
    expect(nav).toBeInTheDocument();
    for (const item of items) {
      const link = screen.getByTestId(`guide-toc-${item.id}`);
      expect(within(nav).getByText(item.label)).toBeInTheDocument();
      expect(link.tagName).toBe('A');
      expect(link.getAttribute('href')).toBe(`#${item.id}`);
    }
  });

  it('exposes a keyboard-navigable nav landmark with an aria-label', () => {
    render(<GuideToc items={items} />);
    const nav = screen.getByRole('navigation', { name: '목차' });
    expect(nav).toBeInTheDocument();
  });

  it('is WCAG AA axe-clean', async () => {
    const { container } = render(<GuideToc items={items} />);
    const violations = await runAxe(container);
    expect(violations).toEqual([]);
  });
});

describe('StateFlow', () => {
  const states = [
    { label: '준비중', name: 'PENDING' },
    { label: '발송', name: 'DISPATCHED' },
    { label: '배송중', name: 'IN_TRANSIT' },
    { label: '배송완료', name: 'DELIVERED', terminal: true },
  ];

  it('renders one chip per state, in array order', () => {
    render(<StateFlow states={states} />);
    const flow = screen.getByTestId('state-flow');
    for (const s of states) {
      const chip = screen.getByTestId(`state-flow-${s.name}`);
      expect(within(chip).getByText(s.label)).toBeInTheDocument();
    }
    // Order matches the source array (flex layout, DOM order = visual order).
    const chipNodes = states.map((s) =>
      within(flow).getByTestId(`state-flow-${s.name}`),
    );
    const positions = chipNodes.map(
      (node) => Array.from(flow.querySelectorAll('[data-testid]')).indexOf(node),
    );
    expect(positions).toEqual([...positions].sort((a, b) => a - b));
  });

  it('marks the terminal state distinctly from in-progress states', () => {
    render(<StateFlow states={states} />);
    const terminalChip = screen.getByTestId('state-flow-DELIVERED');
    const inProgressChip = screen.getByTestId('state-flow-PENDING');
    expect(terminalChip.className).not.toEqual(inProgressChip.className);
    // Consistent with TerminalCell's filled/foreground semantic for 종료.
    expect(terminalChip.className).toContain('bg-foreground');
  });

  it('does not delete/replace information — it is a decorative supplement (aria-hidden)', () => {
    render(<StateFlow states={states} />);
    const flow = screen.getByTestId('state-flow');
    expect(flow.getAttribute('aria-hidden')).toBe('true');
  });
});
