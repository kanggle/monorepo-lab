import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { AuthCardLayout } from '@/features/auth/ui/AuthCardLayout';

describe('AuthCardLayout', () => {
  it('children을 렌더링한다', () => {
    render(
      <AuthCardLayout>
        <p>로그인 폼</p>
      </AuthCardLayout>,
    );

    expect(screen.getByText('로그인 폼')).toBeInTheDocument();
  });

  it('main 요소를 렌더링한다', () => {
    render(
      <AuthCardLayout>
        <p>내용</p>
      </AuthCardLayout>,
    );

    expect(screen.getByRole('main')).toBeInTheDocument();
  });

  it('카드 컨테이너를 렌더링한다', () => {
    const { container } = render(
      <AuthCardLayout>
        <p>내용</p>
      </AuthCardLayout>,
    );

    const card = container.querySelector('.card');
    expect(card).toBeInTheDocument();
  });
});
