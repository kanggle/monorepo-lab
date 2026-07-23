import { describe, it, expect } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import {
  Glossary,
  GuideReadingPath,
  GuideRecipe,
  GuideToc,
  StateFlow,
  Term,
  type GlossaryEntry,
  type GuideRecipeData,
} from '@/shared/ui/guide-primitives';
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

describe('GuideReadingPath (TASK-PC-FE-257)', () => {
  it('renders the "처음이면 여기부터" banner with its title and guidance body', () => {
    render(
      <GuideReadingPath>처음이라면 재고와 출고부터 읽으세요.</GuideReadingPath>,
    );
    const banner = screen.getByTestId('guide-reading-path');
    // A plain container (NOT a nested landmark — see GuideReadingPath doc).
    expect(banner.tagName).toBe('DIV');
    expect(within(banner).getByText('처음이면 여기부터')).toBeInTheDocument();
    expect(
      within(banner).getByText('처음이라면 재고와 출고부터 읽으세요.'),
    ).toBeInTheDocument();
  });

  it('accepts a per-guide testid and an overridable title', () => {
    render(
      <GuideReadingPath testid="wms-guide-reading-path" title="이 화면 읽는 법">
        본문
      </GuideReadingPath>,
    );
    const banner = screen.getByTestId('wms-guide-reading-path');
    expect(within(banner).getByText('이 화면 읽는 법')).toBeInTheDocument();
  });

  it('is WCAG AA axe-clean', async () => {
    const { container } = render(
      <GuideReadingPath>안내 문구</GuideReadingPath>,
    );
    const violations = await runAxe(container);
    expect(violations).toEqual([]);
  });
});

describe('GuideRecipe (TASK-PC-FE-256)', () => {
  const recipe: GuideRecipeData = {
    title: '환불 요청이 들어왔을 때',
    intro: '결제 전용 화면이 없어 환불은 주문 취소의 보상으로 처리됩니다.',
    steps: ['주문을 연다', '취소하면 환불 보상이 걸린다', '결제 상태로 관측된다'],
  };

  it('renders the title, intro and one numbered <li> per step in a semantic <ol>', () => {
    render(<GuideRecipe recipe={recipe} testid="test-recipe" />);
    const card = screen.getByTestId('test-recipe');
    expect(within(card).getByText(recipe.title)).toBeInTheDocument();
    expect(within(card).getByText(recipe.intro!)).toBeInTheDocument();
    // Semantic ordered list.
    const ol = card.querySelector('ol');
    expect(ol).not.toBeNull();
    recipe.steps.forEach((step, i) => {
      const li = screen.getByTestId(`test-recipe-step-${i}`);
      expect(li.tagName).toBe('LI');
      expect(within(li).getByText(step)).toBeInTheDocument();
      // The circular marker carries the 1-based step number.
      expect(within(li).getByText(String(i + 1))).toBeInTheDocument();
    });
  });

  it('omits the intro paragraph when none is given', () => {
    const noIntro: GuideRecipeData = { title: 'x', steps: ['a', 'b'] };
    render(<GuideRecipe recipe={noIntro} testid="no-intro" />);
    expect(screen.getByTestId('no-intro-step-0')).toBeInTheDocument();
    expect(screen.getByTestId('no-intro-step-1')).toBeInTheDocument();
  });

  it('is WCAG AA axe-clean', async () => {
    const { container } = render(
      <GuideRecipe recipe={recipe} testid="test-recipe" />,
    );
    const violations = await runAxe(container);
    expect(violations).toEqual([]);
  });
});

describe('Term (TASK-PC-FE-256)', () => {
  it('wraps children in an <abbr> exposing the expansion via title (SR-friendly)', () => {
    render(<Term title="Purchase Order">PO</Term>);
    const abbr = screen.getByText('PO');
    expect(abbr.tagName).toBe('ABBR');
    expect(abbr.getAttribute('title')).toBe('Purchase Order');
  });
});

describe('Glossary (TASK-PC-FE-256)', () => {
  const entries: GlossaryEntry[] = [
    {
      key: 'PO',
      term: '발주 (PO)',
      full: 'Purchase Order',
      meaning: '공급사에 물품을 주문하는 구매 문서.',
    },
    {
      key: 'S5',
      term: 'S5 경고',
      meaning: '발주 결정의 근거로 쓰지 말라는 계약상 경고.',
    },
  ];

  it('renders one row per entry with the term (as <dfn>) and its always-visible meaning', () => {
    render(<Glossary entries={entries} testid="test-glossary" />);
    expect(screen.getByTestId('test-glossary')).toBeInTheDocument();
    for (const e of entries) {
      const row = screen.getByTestId(`test-glossary-${e.key}`);
      expect(within(row).getByText(e.term)).toBeInTheDocument();
      // The definition is present in the row text (not hover-only).
      expect(within(row).getByText(e.meaning)).toBeInTheDocument();
      // Term cell uses a <dfn> semantic element.
      expect(row.querySelector('dfn')).not.toBeNull();
    }
  });

  it('wraps an abbreviation term in <abbr title> only when a full expansion is given', () => {
    render(<Glossary entries={entries} testid="test-glossary" />);
    const poRow = screen.getByTestId('test-glossary-PO');
    const s5Row = screen.getByTestId('test-glossary-S5');
    expect(poRow.querySelector('abbr')?.getAttribute('title')).toBe(
      'Purchase Order',
    );
    expect(s5Row.querySelector('abbr')).toBeNull();
  });

  it('is WCAG AA axe-clean', async () => {
    const { container } = render(
      <Glossary entries={entries} testid="test-glossary" />,
    );
    const violations = await runAxe(container);
    expect(violations).toEqual([]);
  });
});
