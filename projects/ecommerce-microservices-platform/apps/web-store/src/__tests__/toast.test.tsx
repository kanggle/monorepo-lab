import { describe, it, expect, vi } from 'vitest';
import { render, screen, act } from '@testing-library/react';
import { Toast } from '@/shared/ui/Toast';

describe('Toast', () => {
  it('성공 타입일 때 role="status"로 렌더링한다', () => {
    render(<Toast message="저장 완료" type="success" onClose={vi.fn()} />);

    expect(screen.getByRole('status')).toBeInTheDocument();
    expect(screen.getByText('저장 완료')).toBeInTheDocument();
  });

  it('에러 타입일 때 role="alert"로 렌더링한다', () => {
    render(<Toast message="오류 발생" type="error" onClose={vi.fn()} />);

    expect(screen.getByRole('alert')).toBeInTheDocument();
    expect(screen.getByText('오류 발생')).toBeInTheDocument();
  });

  it('메시지 텍스트를 올바르게 표시한다', () => {
    render(<Toast message="주문이 완료되었습니다" type="success" onClose={vi.fn()} />);

    expect(screen.getByText('주문이 완료되었습니다')).toBeInTheDocument();
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

  it('커스텀 duration을 지정하면 해당 시간 후 onClose를 호출한다', () => {
    vi.useFakeTimers();
    const onClose = vi.fn();

    render(<Toast message="테스트" type="error" onClose={onClose} duration={5000} />);

    act(() => {
      vi.advanceTimersByTime(4999);
    });
    expect(onClose).not.toHaveBeenCalled();

    act(() => {
      vi.advanceTimersByTime(1);
    });
    expect(onClose).toHaveBeenCalledTimes(1);

    vi.useRealTimers();
  });

  it('언마운트 시 타이머가 정리된다', () => {
    vi.useFakeTimers();
    const onClose = vi.fn();

    const { unmount } = render(<Toast message="테스트" type="success" onClose={onClose} />);

    unmount();

    act(() => {
      vi.advanceTimersByTime(3000);
    });
    expect(onClose).not.toHaveBeenCalled();

    vi.useRealTimers();
  });

  it('성공 타입일 때 녹색 계열 배경색이 적용된다', () => {
    render(<Toast message="성공" type="success" onClose={vi.fn()} />);

    const toast = screen.getByRole('status');
    expect(toast).toHaveStyle({ backgroundColor: '#f0fdf4' });
  });

  it('에러 타입일 때 빨간색 계열 배경색이 적용된다', () => {
    render(<Toast message="실패" type="error" onClose={vi.fn()} />);

    const toast = screen.getByRole('alert');
    expect(toast).toHaveStyle({ backgroundColor: '#fef2f2' });
  });
});
