import { render, screen } from '@testing-library/react';
import { Sidebar } from '@/shared/ui';

let mockPathname = '/dashboard';

vi.mock('next/navigation', () => ({
  usePathname: () => mockPathname,
  useRouter: () => ({ push: vi.fn() }),
}));

vi.mock('@/shared/hooks', () => ({
  useAuth: () => ({ user: { email: 'admin@test.com' }, logout: vi.fn() }),
}));

describe('Sidebar', () => {
  it('네비게이션 항목을 표시한다', () => {
    render(<Sidebar />);

    expect(screen.getByText('대시보드')).toBeInTheDocument();
    expect(screen.getByText('상품 관리')).toBeInTheDocument();
    expect(screen.getByText('주문 관리')).toBeInTheDocument();
    expect(screen.getByText('사용자 관리')).toBeInTheDocument();
  });

  it('Admin 타이틀을 표시한다', () => {
    render(<Sidebar />);
    expect(screen.getByText('Admin')).toBeInTheDocument();
  });

  it('활성 메뉴에 활성 스타일을 적용한다', () => {
    mockPathname = '/products';
    render(<Sidebar />);

    const productLink = screen.getByText('상품 관리');
    expect(productLink).toHaveStyle({ color: '#fff' });
  });

  it('비활성 메뉴에 비활성 스타일을 적용한다', () => {
    mockPathname = '/products';
    render(<Sidebar />);

    const dashboardLink = screen.getByText('대시보드');
    expect(dashboardLink).toHaveStyle({ color: '#888' });
  });

  it('네비게이션 링크가 올바른 href를 가진다', () => {
    render(<Sidebar />);

    expect(screen.getByText('대시보드').closest('a')).toHaveAttribute('href', '/dashboard');
    expect(screen.getByText('상품 관리').closest('a')).toHaveAttribute('href', '/products');
    expect(screen.getByText('주문 관리').closest('a')).toHaveAttribute('href', '/orders');
    expect(screen.getByText('사용자 관리').closest('a')).toHaveAttribute('href', '/users');
  });
});
