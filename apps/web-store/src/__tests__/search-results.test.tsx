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
});
