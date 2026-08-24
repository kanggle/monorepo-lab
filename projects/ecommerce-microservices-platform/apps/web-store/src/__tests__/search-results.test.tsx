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

  it('검색 결과가 없으면 빈 상태 메시지를 표시한다', () => {
    render(<SearchResults items={[]} query="없는상품" />);

    expect(
      screen.getByText('"없는상품"에 대한 검색 결과가 없습니다.'),
    ).toBeInTheDocument();
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
