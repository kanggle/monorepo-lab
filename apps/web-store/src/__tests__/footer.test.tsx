import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Footer } from '@/widgets/footer';

vi.mock('next/link', () => ({
  default: ({ href, children, ...props }: { href: string; children: React.ReactNode; [key: string]: unknown }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

describe('Footer', () => {
  it('브랜드명을 표시한다', () => {
    render(<Footer />);

    expect(screen.getByText('WebStore')).toBeInTheDocument();
  });

  it('설명 문구를 표시한다', () => {
    render(<Footer />);

    expect(screen.getByText(/합리적인 가격, 빠른 배송/)).toBeInTheDocument();
  });

  it('전체상품 링크를 표시한다', () => {
    render(<Footer />);

    const link = screen.getByText('전체상품');
    expect(link).toHaveAttribute('href', '/products');
  });

  it('마이페이지 링크를 표시한다', () => {
    render(<Footer />);

    const link = screen.getByText('마이페이지');
    expect(link).toHaveAttribute('href', '/my/profile');
  });

  it('주문조회 링크를 표시한다', () => {
    render(<Footer />);

    const link = screen.getByText('주문조회');
    expect(link).toHaveAttribute('href', '/orders');
  });

  it('배송지관리 링크를 표시한다', () => {
    render(<Footer />);

    const link = screen.getByText('배송지관리');
    expect(link).toHaveAttribute('href', '/my/addresses');
  });

  it('쇼핑 섹션 제목을 표시한다', () => {
    render(<Footer />);

    expect(screen.getByText('쇼핑')).toBeInTheDocument();
  });

  it('고객지원 섹션 제목을 표시한다', () => {
    render(<Footer />);

    expect(screen.getByText('고객지원')).toBeInTheDocument();
  });

  it('저작권 텍스트를 표시한다', () => {
    render(<Footer />);

    const year = new Date().getFullYear();
    expect(screen.getByText(new RegExp(`${year} WebStore`))).toBeInTheDocument();
  });
});
