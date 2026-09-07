import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { SearchResults } from '@/features/search/ui/SearchResults';
import type { SearchProductItem } from '@repo/types';

vi.mock('next/link', () => ({
  default: ({ href, children, ...props }: { href: string; children: React.ReactNode; [key: string]: unknown }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

const items: SearchProductItem[] = [
  {
    productId: 'p1',
    name: '검색 상품 A',
    price: 15000,
    status: 'ON_SALE',
    thumbnailUrl: '/a.jpg',
    categoryId: 'c1',
    score: 1.5,
  },
  {
    productId: 'p2',
    name: '검색 상품 B',
    price: 25000,
    status: 'SOLD_OUT',
    thumbnailUrl: '/b.jpg',
    categoryId: 'c1',
    score: 1.2,
  },
];

describe('SearchResults', () => {
  it('검색 결과를 렌더링한다', () => {
    render(<SearchResults items={items} query="테스트" />);

    expect(screen.getByText('검색 상품 A')).toBeInTheDocument();
    expect(screen.getByText('검색 상품 B')).toBeInTheDocument();
  });

  /**
   * 🔴🔴 이 칸의 명제가 **갈라졌다** (TASK-MONO-635 / ADR-MONO-070 § D3.2).
   *
   * 예전에는 0건이 한 문장이었다: *"…에 대한 검색 결과가 없습니다."*
   * 카탈로그가 저장본에서 오게 되면서 그 한 문장이 **두 사실을 뭉개게** 됐다:
   *
   *   · 저장본에 상품은 있는데 조건에 안 맞았다   → 검색 결과 없음
   *   · 저장본 자체가 비었다(아직 발행 전)        → 저장본이 비어 있음
   *
   * 뭉치면 화면이 «전체 카탈로그가 비었다» 는 **없는 주장**을 한다 — 저장본은 카탈로그의
   * *범위* 일 뿐이고, 그 범위 밖을 «세상에 없음» 으로 번역하는 것이 금지된 것이다.
   *
   * 🔴 그래서 단언을 **지우지 않고 둘로 늘렸다.** 하나만 남기면 두 문구가 같아져도(=뭉개져도)
   *    이 스위트는 초록이다.
   */
  it('조건에 안 맞는 0건 → «검색 결과 없음» (저장본 범위를 함께 말한다)', () => {
    render(<SearchResults items={[]} query="없는상품" corpusSize={8} />);

    expect(screen.getByText('검색 결과 없음')).toBeInTheDocument();
    // 🔵 검색어와 «저장본 안에서» 라는 범위 한정이 둘 다 있어야 한다.
    expect(screen.getByTestId('snapshot-empty-state')).toHaveTextContent('없는상품');
    expect(screen.getByTestId('snapshot-empty-state')).toHaveTextContent('저장본');
    // 🔴 «저장본이 비어 있음» 으로 새면 안 된다(뒤집힌 칸).
    expect(screen.queryByText('저장본이 비어 있음')).not.toBeInTheDocument();
  });

  it('저장본이 비었을 때 → «저장본이 비어 있음» + 전체 카탈로그를 단정하지 않는다', () => {
    render(<SearchResults items={[]} query="없는상품" corpusSize={0} />);

    expect(screen.getByText('저장본이 비어 있음')).toBeInTheDocument();
    // 🔴🔴 이 문장이 이 컴포넌트의 존재 이유다 — 없는 주장을 하지 않는다는 선언.
    expect(screen.getByTestId('snapshot-empty-state')).toHaveTextContent(
      '전체 카탈로그가 비어 있다는 뜻은 아닙니다',
    );
    expect(screen.queryByText('검색 결과 없음')).not.toBeInTheDocument();
  });

  it('모집단을 «모를» 때는 0 으로 단정하지 않는다', () => {
    // 🔴 `undefined` 는 "0" 이 아니라 "모른다" 다. 모르면서 «저장본이 비었다» 고 단정하는
    //    것이 더 큰 거짓이므로 «검색 결과 없음» 쪽으로 붙는다.
    render(<SearchResults items={[]} query="없는상품" />);

    expect(screen.getByText('검색 결과 없음')).toBeInTheDocument();
    expect(screen.getByTestId('snapshot-empty-state')).toHaveAttribute('data-corpus-size', 'unknown');
  });

  it('품절 상품에 품절 뱃지를 표시한다', () => {
    render(<SearchResults items={items} query="테스트" />);

    expect(screen.getByText('품절')).toBeInTheDocument();
  });

  it('검색 결과 개수만큼 카드를 렌더링한다', () => {
    render(<SearchResults items={items} query="테스트" />);

    const links = screen.getAllByRole('link');
    expect(links).toHaveLength(2);
  });

  // 전체 상품 목록에는 하트가 붙고 검색 결과에만 없던 것이 TASK-FE-099 의 결함이다.
  // 술어는 "액션이 카드마다 붙는가" — 위시리스트라는 구체 구현이 아니라 배선을 잡는다.
  it('renderAction 을 받으면 카드마다 액션을 붙인다', () => {
    render(
      <SearchResults
        items={items}
        query="테스트"
        renderAction={(product) => (
          <button type="button" data-testid="card-action" data-product-id={product.id} />
        )}
      />,
    );

    const actions = screen.getAllByTestId('card-action');
    expect(actions).toHaveLength(2);
    // 매핑도 함께 고정한다: 액션은 검색 응답의 productId 로 만들어진 상품을 받는다
    expect(actions.map((el) => el.getAttribute('data-product-id'))).toEqual(['p1', 'p2']);
  });

  it('renderAction 이 없으면 액션 없이 렌더링한다', () => {
    render(<SearchResults items={items} query="테스트" />);

    expect(screen.queryByTestId('card-action')).not.toBeInTheDocument();
  });
});
