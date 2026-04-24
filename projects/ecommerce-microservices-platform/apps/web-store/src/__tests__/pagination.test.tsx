import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Pagination } from '@/shared/ui/Pagination';

vi.mock('next/link', () => ({
  default: ({ href, children, ...props }: { href: string; children: React.ReactNode; [key: string]: unknown }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

describe('Pagination', () => {
  it('총 페이지가 1 이하이면 렌더링하지 않는다', () => {
    const { container } = render(
      <Pagination
        currentPage={0}
        totalElements={5}
        pageSize={20}
        baseHref="/products"
      />,
    );

    expect(container.firstChild).toBeNull();
  });

  it('페이지 번호를 표시한다', () => {
    render(
      <Pagination
        currentPage={0}
        totalElements={60}
        pageSize={20}
        baseHref="/products"
      />,
    );

    expect(screen.getByText('1')).toBeInTheDocument();
    expect(screen.getByText('2')).toBeInTheDocument();
    expect(screen.getByText('3')).toBeInTheDocument();
  });

  it('현재 페이지는 링크가 아닌 볼드 텍스트로 표시된다', () => {
    render(
      <Pagination
        currentPage={0}
        totalElements={60}
        pageSize={20}
        baseHref="/products"
      />,
    );

    const currentPage = screen.getByText('1');
    expect(currentPage.tagName).toBe('SPAN');
    expect(currentPage.style.fontWeight).toBe('bold');
  });

  it('다른 페이지는 링크로 표시된다', () => {
    render(
      <Pagination
        currentPage={0}
        totalElements={60}
        pageSize={20}
        baseHref="/products"
      />,
    );

    const page2 = screen.getByText('2');
    expect(page2.tagName).toBe('A');
    expect(page2).toHaveAttribute('href', '/products?page=1');
  });

  it('첫 페이지에서는 이전 버튼을 표시하지 않는다', () => {
    render(
      <Pagination
        currentPage={0}
        totalElements={60}
        pageSize={20}
        baseHref="/products"
      />,
    );

    expect(screen.queryByText('이전')).not.toBeInTheDocument();
  });

  it('중간 페이지에서는 이전/다음 버튼을 모두 표시한다', () => {
    render(
      <Pagination
        currentPage={1}
        totalElements={60}
        pageSize={20}
        baseHref="/products"
      />,
    );

    expect(screen.getByText('이전')).toBeInTheDocument();
    expect(screen.getByText('다음')).toBeInTheDocument();
  });

  it('마지막 페이지에서는 다음 버튼을 표시하지 않는다', () => {
    render(
      <Pagination
        currentPage={2}
        totalElements={60}
        pageSize={20}
        baseHref="/products"
      />,
    );

    expect(screen.getByText('이전')).toBeInTheDocument();
    expect(screen.queryByText('다음')).not.toBeInTheDocument();
  });

  it('검색 파라미터를 유지한다', () => {
    render(
      <Pagination
        currentPage={0}
        totalElements={60}
        pageSize={20}
        baseHref="/products"
        searchParams={{ q: 'test', sort: 'price_asc' }}
      />,
    );

    const page2Link = screen.getByText('2');
    const href = page2Link.getAttribute('href') ?? '';
    expect(href).toContain('q=test');
    expect(href).toContain('sort=price_asc');
    expect(href).toContain('page=1');
  });

  it('pagination 네비게이션 역할을 가진다', () => {
    render(
      <Pagination
        currentPage={0}
        totalElements={60}
        pageSize={20}
        baseHref="/products"
      />,
    );

    expect(screen.getByRole('navigation', { name: 'pagination' })).toBeInTheDocument();
  });

  it('10페이지 이상일 때 말줄임(...)이 표시된다', () => {
    render(
      <Pagination
        currentPage={0}
        totalElements={200}
        pageSize={20}
        baseHref="/products"
      />,
    );

    const ellipsis = screen.getByText('...');
    expect(ellipsis).toBeInTheDocument();
    expect(ellipsis.tagName).toBe('SPAN');
  });

  it('10페이지 이상일 때 첫/마지막/현재 페이지 ±2가 표시된다', () => {
    render(
      <Pagination
        currentPage={5}
        totalElements={400}
        pageSize={20}
        baseHref="/products"
      />,
    );

    // 첫 페이지
    expect(screen.getByText('1')).toBeInTheDocument();
    // 마지막 페이지
    expect(screen.getByText('20')).toBeInTheDocument();
    // 현재 페이지 ±2 (4, 5, 6, 7, 8)
    expect(screen.getByText('4')).toBeInTheDocument();
    expect(screen.getByText('5')).toBeInTheDocument();
    expect(screen.getByText('6')).toBeInTheDocument();
    expect(screen.getByText('7')).toBeInTheDocument();
    expect(screen.getByText('8')).toBeInTheDocument();
  });

  it('10페이지 미만일 때 말줄임 없이 전체 페이지가 표시된다', () => {
    render(
      <Pagination
        currentPage={0}
        totalElements={60}
        pageSize={20}
        baseHref="/products"
      />,
    );

    expect(screen.getByText('1')).toBeInTheDocument();
    expect(screen.getByText('2')).toBeInTheDocument();
    expect(screen.getByText('3')).toBeInTheDocument();
    expect(screen.queryByText('...')).not.toBeInTheDocument();
  });

  it('말줄임(...)은 클릭 불가능한 span으로 렌더링된다', () => {
    render(
      <Pagination
        currentPage={5}
        totalElements={400}
        pageSize={20}
        baseHref="/products"
      />,
    );

    const ellipses = screen.getAllByText('...');
    ellipses.forEach((el) => {
      expect(el.tagName).toBe('SPAN');
      expect(el.closest('a')).toBeNull();
    });
  });
});
