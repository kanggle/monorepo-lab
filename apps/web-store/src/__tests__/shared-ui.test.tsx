import { describe, it, expect, vi } from 'vitest';
import { render, screen, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { LoadingSpinner, ErrorMessage, EmptyState } from '@repo/ui';
import { Toast } from '@/shared/ui/Toast';

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
});

describe('EmptyState', () => {
  it('빈 상태 메시지를 표시한다', () => {
    render(<EmptyState message="데이터가 없습니다." />);

    expect(screen.getByText('데이터가 없습니다.')).toBeInTheDocument();
  });
});

describe('Toast', () => {
  it('성공 토스트를 role="status"로 렌더링한다', () => {
    render(<Toast message="성공!" type="success" onClose={vi.fn()} />);

    expect(screen.getByRole('status')).toBeInTheDocument();
    expect(screen.getByText('성공!')).toBeInTheDocument();
  });

  it('에러 토스트를 role="alert"로 렌더링한다', () => {
    render(<Toast message="실패!" type="error" onClose={vi.fn()} />);

    expect(screen.getByRole('alert')).toBeInTheDocument();
    expect(screen.getByText('실패!')).toBeInTheDocument();
  });

  it('지정된 시간 후 onClose를 호출한다', () => {
    vi.useFakeTimers();
    const onClose = vi.fn();

    render(<Toast message="테스트" type="success" onClose={onClose} duration={5000} />);

    expect(onClose).not.toHaveBeenCalled();

    act(() => {
      vi.advanceTimersByTime(5000);
    });

    expect(onClose).toHaveBeenCalledTimes(1);

    vi.useRealTimers();
  });

  it('기본 duration(3000ms) 후 onClose를 호출한다', () => {
    vi.useFakeTimers();
    const onClose = vi.fn();

    render(<Toast message="테스트" type="success" onClose={onClose} />);

    act(() => {
      vi.advanceTimersByTime(2999);
    });
    expect(onClose).not.toHaveBeenCalled();

    act(() => {
      vi.advanceTimersByTime(1);
    });
    expect(onClose).toHaveBeenCalledTimes(1);

    vi.useRealTimers();
  });
});
