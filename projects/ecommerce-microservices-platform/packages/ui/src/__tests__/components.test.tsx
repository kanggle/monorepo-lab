import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { LoadingSpinner } from '../LoadingSpinner';
import { ErrorMessage } from '../ErrorMessage';
import { EmptyState } from '../EmptyState';

describe('LoadingSpinner', () => {
  it('로딩 상태를 표시한다', () => {
    render(<LoadingSpinner />);

    expect(screen.getByRole('status')).toBeInTheDocument();
    expect(screen.getByText('로딩 중...')).toBeInTheDocument();
  });
});

describe('ErrorMessage', () => {
  it('에러 메시지를 표시한다', () => {
    render(<ErrorMessage message="오류가 발생했습니다." />);

    expect(screen.getByRole('alert')).toBeInTheDocument();
    expect(screen.getByText('오류가 발생했습니다.')).toBeInTheDocument();
  });

  it('onRetry가 없으면 재시도 버튼을 표시하지 않는다', () => {
    render(<ErrorMessage message="오류" />);

    expect(screen.queryByText('다시 시도')).not.toBeInTheDocument();
  });

  it('onRetry가 있으면 재시도 버튼을 표시한다', async () => {
    const onRetry = vi.fn();
    render(<ErrorMessage message="오류" onRetry={onRetry} />);

    const button = screen.getByText('다시 시도');
    expect(button).toBeInTheDocument();

    await userEvent.click(button);
    expect(onRetry).toHaveBeenCalledTimes(1);
  });

  it('color prop이 있으면 메시지에 색상을 적용한다', () => {
    render(<ErrorMessage message="빨간 에러" color="red" />);

    const msg = screen.getByText('빨간 에러');
    expect(msg).toHaveStyle({ color: 'rgb(255, 0, 0)' });
  });

  it('color prop이 없으면 별도 색상을 적용하지 않는다', () => {
    render(<ErrorMessage message="기본 에러" />);

    const msg = screen.getByText('기본 에러');
    expect(msg.style.color).toBe('');
  });
});

describe('EmptyState', () => {
  it('빈 상태 메시지를 표시한다', () => {
    render(<EmptyState message="데이터가 없습니다." />);

    expect(screen.getByText('데이터가 없습니다.')).toBeInTheDocument();
  });
});
