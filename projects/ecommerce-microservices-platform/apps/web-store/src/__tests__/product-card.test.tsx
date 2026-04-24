import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ProductCard } from '@/entities/product/ui/ProductCard';
import type { ProductSummary } from '@repo/types';

vi.mock('next/link', () => ({
  default: ({ href, children, ...props }: { href: string; children: React.ReactNode; [key: string]: unknown }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

vi.mock('next/image', () => ({
  default: ({ src, alt, ...props }: { src: string; alt: string; [key: string]: unknown }) => (
    // eslint-disable-next-line @next/next/no-img-element
    <img src={src} alt={alt} {...props} />
  ),
}));

const baseProduct: ProductSummary = {
  id: 'prod-1',
  name: '테스트 상품',
  status: 'ON_SALE',
  price: 29900,
  thumbnailUrl: '/images/test.jpg',
  categoryId: 'cat-1',
};

describe('ProductCard', () => {
  it('상품 이름을 표시한다', () => {
    render(<ProductCard product={baseProduct} />);

    expect(screen.getByText('테스트 상품')).toBeInTheDocument();
  });

  it('상품 가격을 원화 형식으로 표시한다', () => {
    render(<ProductCard product={baseProduct} />);

    // 가격 숫자와 단위 "원"이 별도 span으로 렌더링되므로 textContent 전체로 검색한다
    expect(
      screen.getByText((_, el) => el?.textContent === '29,900원'),
    ).toBeInTheDocument();
  });

  it('상품 상세 페이지로의 링크를 포함한다', () => {
    render(<ProductCard product={baseProduct} />);

    const link = screen.getByRole('link');
    expect(link).toHaveAttribute('href', '/products/prod-1');
  });

  it('썸네일 이미지를 표시한다', () => {
    render(<ProductCard product={baseProduct} />);

    const img = screen.getByAltText('테스트 상품');
    expect(img).toBeInTheDocument();
    expect(img).toHaveAttribute('src', '/images/test.jpg');
  });

  it('판매 중 상품은 품절 뱃지를 표시하지 않는다', () => {
    render(<ProductCard product={baseProduct} />);

    expect(screen.queryByText('품절')).not.toBeInTheDocument();
  });

  it('품절 상품은 품절 뱃지를 표시한다', () => {
    const soldOutProduct: ProductSummary = {
      ...baseProduct,
      status: 'SOLD_OUT',
    };
    render(<ProductCard product={soldOutProduct} />);

    expect(screen.getByText('품절')).toBeInTheDocument();
  });

  it('품절 상품은 투명도가 낮아진다', () => {
    const soldOutProduct: ProductSummary = {
      ...baseProduct,
      status: 'SOLD_OUT',
    };
    render(<ProductCard product={soldOutProduct} />);

    const link = screen.getByRole('link');
    expect(link.style.opacity).toBe('0.6');
  });

  it('썸네일 URL이 없으면 이미지를 표시하지 않는다', () => {
    const noImageProduct: ProductSummary = {
      ...baseProduct,
      thumbnailUrl: '',
    };
    render(<ProductCard product={noImageProduct} />);

    expect(screen.queryByRole('img')).not.toBeInTheDocument();
  });
});
