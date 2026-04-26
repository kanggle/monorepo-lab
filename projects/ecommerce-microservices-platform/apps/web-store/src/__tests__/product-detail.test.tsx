import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ProductDetail } from '@/features/product/ui/ProductDetail';
import type { ProductDetail as ProductDetailType } from '@repo/types';

vi.mock('next/image', () => ({
  default: ({ src, alt, ...props }: { src: string; alt: string; [key: string]: unknown }) => (
    // eslint-disable-next-line @next/next/no-img-element
    <img src={src} alt={alt} {...props} />
  ),
}));

vi.mock('@/entities/product', () => ({
  ProductImage: ({ images, alt }: { images: string[]; alt: string }) => (
    <div data-testid="product-image" data-images={images.join(',')} data-alt={alt} />
  ),
}));

const baseProduct: ProductDetailType = {
  id: 'prod-1',
  name: '테스트 상품',
  description: '상품 설명입니다.',
  status: 'ON_SALE',
  price: 29900,
  categoryId: 'cat-1',
  images: [{ imageId: 'img-1', url: '/img/1.jpg', sortOrder: 0, isPrimary: true }],
  variants: [],
};

describe('ProductDetail', () => {
  it('상품 이름을 표시한다', () => {
    render(<ProductDetail product={baseProduct} />);

    expect(screen.getByText('테스트 상품')).toBeInTheDocument();
  });

  it('상품 가격을 원화 형식으로 표시한다', () => {
    render(<ProductDetail product={baseProduct} />);

    expect(screen.getByText('29,900원')).toBeInTheDocument();
  });

  it('상품 설명을 표시한다', () => {
    render(<ProductDetail product={baseProduct} />);

    expect(screen.getByText('상품 설명입니다.')).toBeInTheDocument();
  });

  it('ProductImage 컴포넌트에 이미지 목록을 전달한다', () => {
    render(<ProductDetail product={baseProduct} />);

    const imageEl = screen.getByTestId('product-image');
    expect(imageEl).toHaveAttribute('data-images', '/img/1.jpg');
    expect(imageEl).toHaveAttribute('data-alt', '테스트 상품');
  });

  it('이미지가 없으면 기본 이미지 경로를 사용한다', () => {
    const noImageProduct = { ...baseProduct, images: [] };
    render(<ProductDetail product={noImageProduct} />);

    const imageEl = screen.getByTestId('product-image');
    expect(imageEl).toHaveAttribute('data-images', '/images/products/prod-1.jpg');
  });

  it('옵션이 있으면 옵션 섹션을 표시한다', () => {
    const productWithVariants: ProductDetailType = {
      ...baseProduct,
      variants: [
        { id: 'v1', optionName: '빨강', stock: 10, additionalPrice: 1000 },
        { id: 'v2', optionName: '파랑', stock: 0, additionalPrice: 0 },
      ],
    };
    render(<ProductDetail product={productWithVariants} />);

    expect(screen.getByText('옵션')).toBeInTheDocument();
    expect(screen.getByText('빨강')).toBeInTheDocument();
    expect(screen.getByText('파랑')).toBeInTheDocument();
  });

  it('추가 가격이 있는 옵션은 추가 가격을 표시한다', () => {
    const productWithVariants: ProductDetailType = {
      ...baseProduct,
      variants: [
        { id: 'v1', optionName: '빨강', stock: 10, additionalPrice: 1000 },
      ],
    };
    render(<ProductDetail product={productWithVariants} />);

    expect(screen.getByText('+1,000원')).toBeInTheDocument();
  });

  it('재고가 0인 옵션은 품절을 표시한다', () => {
    const productWithVariants: ProductDetailType = {
      ...baseProduct,
      variants: [
        { id: 'v1', optionName: '빨강', stock: 0, additionalPrice: 0 },
      ],
    };
    render(<ProductDetail product={productWithVariants} />);

    expect(screen.getByText('품절')).toBeInTheDocument();
  });

  it('재고가 있는 옵션은 재고 수를 표시한다', () => {
    const productWithVariants: ProductDetailType = {
      ...baseProduct,
      variants: [
        { id: 'v1', optionName: '빨강', stock: 5, additionalPrice: 0 },
      ],
    };
    render(<ProductDetail product={productWithVariants} />);

    expect(screen.getByText('재고 5')).toBeInTheDocument();
  });

  it('옵션이 없으면 옵션 섹션을 표시하지 않는다', () => {
    render(<ProductDetail product={baseProduct} />);

    expect(screen.queryByText('옵션')).not.toBeInTheDocument();
  });
});
