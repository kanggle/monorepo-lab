import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ProductList } from '@/features/product/ui/ProductList';
import type { ProductSummary } from '@repo/types';

vi.mock('next/link', () => ({
  default: ({ href, children, ...props }: { href: string; children: React.ReactNode; [key: string]: unknown }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

const products: ProductSummary[] = [
  {
    id: 'p1',
    name: '상품 A',
    status: 'ON_SALE',
    price: 10000,
    thumbnailUrl: '/a.jpg',
    categoryId: 'c1',
  },
  {
    id: 'p2',
    name: '상품 B',
    status: 'ON_SALE',
    price: 20000,
    thumbnailUrl: '/b.jpg',
    categoryId: 'c1',
  },
];

describe('ProductList', () => {
  it('상품 목록을 렌더링한다', () => {
    render(<ProductList products={products} />);

    expect(screen.getByText('상품 A')).toBeInTheDocument();
    expect(screen.getByText('상품 B')).toBeInTheDocument();
  });

  it('상품이 없으면 빈 상태 메시지를 표시한다', () => {
    render(<ProductList products={[]} />);

    expect(screen.getByText('등록된 상품이 없습니다.')).toBeInTheDocument();
  });

  it('상품 개수만큼 카드를 렌더링한다', () => {
    render(<ProductList products={products} />);

    const links = screen.getAllByRole('link');
    expect(links).toHaveLength(2);
  });

  it('정상 응답에 포함된 상품 링크는 mock-* 형태의 id를 갖지 않는다 (TASK-FE-061)', () => {
    render(<ProductList products={products} />);

    const links = screen.getAllByRole('link');
    for (const link of links) {
      const href = link.getAttribute('href') ?? '';
      expect(href).not.toMatch(/\/products\/mock-/);
    }
  });
});
