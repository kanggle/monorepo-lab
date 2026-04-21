import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { PageLayout } from '@/shared/ui/PageLayout';

vi.mock('next/link', () => ({
  default: ({ children, href, ...props }: { children: React.ReactNode; href: string }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

describe('PageLayout', () => {
  it('제목을 표시한다', () => {
    render(<PageLayout title="상품 관리">내용</PageLayout>);
    expect(screen.getByText('상품 관리')).toBeInTheDocument();
  });

  it('children을 렌더링한다', () => {
    render(<PageLayout title="제목">테스트 내용</PageLayout>);
    expect(screen.getByText('테스트 내용')).toBeInTheDocument();
  });

  it('링크 액션을 렌더링한다', () => {
    render(
      <PageLayout
        title="상품 관리"
        actions={[{ label: '등록', href: '/products/new' }]}
      >
        내용
      </PageLayout>,
    );
    const link = screen.getByText('등록');
    expect(link).toBeInTheDocument();
    expect(link.closest('a')).toHaveAttribute('href', '/products/new');
  });

  it('버튼 액션 클릭 시 onClick을 호출한다', async () => {
    const onClick = vi.fn();
    render(
      <PageLayout
        title="상품 관리"
        actions={[{ label: '삭제', variant: 'danger', onClick }]}
      >
        내용
      </PageLayout>,
    );

    await userEvent.click(screen.getByText('삭제'));
    expect(onClick).toHaveBeenCalledTimes(1);
  });

  it('스켈레톤을 렌더링한다', () => {
    render(<PageLayout.Skeleton />);
    expect(document.querySelector('div')).toBeInTheDocument();
  });
});
