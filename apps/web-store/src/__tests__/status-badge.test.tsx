import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { StatusBadge } from '@/shared/ui/StatusBadge';

const LABELS: Record<string, string> = {
  pending: '대기',
  confirmed: '확인',
  shipped: '배송중',
  delivered: '배송완료',
  cancelled: '취소',
};

const COLORS: Record<string, string> = {
  pending: '#f59e0b',
  confirmed: '#3b82f6',
  shipped: '#8b5cf6',
  delivered: '#22c55e',
  cancelled: '#ef4444',
};

describe('StatusBadge', () => {
  it('주어진 status에 해당하는 라벨을 표시한다', () => {
    render(<StatusBadge status="pending" labels={LABELS} colors={COLORS} />);

    expect(screen.getByText('대기')).toBeInTheDocument();
  });

  it('confirmed 상태의 라벨과 색상을 올바르게 표시한다', () => {
    render(<StatusBadge status="confirmed" labels={LABELS} colors={COLORS} />);

    const badge = screen.getByText('확인');
    expect(badge).toBeInTheDocument();
    expect(badge).toHaveStyle({ backgroundColor: '#3b82f6' });
  });

  it('shipped 상태의 라벨과 색상을 올바르게 표시한다', () => {
    render(<StatusBadge status="shipped" labels={LABELS} colors={COLORS} />);

    const badge = screen.getByText('배송중');
    expect(badge).toBeInTheDocument();
    expect(badge).toHaveStyle({ backgroundColor: '#8b5cf6' });
  });

  it('delivered 상태의 라벨과 색상을 올바르게 표시한다', () => {
    render(<StatusBadge status="delivered" labels={LABELS} colors={COLORS} />);

    const badge = screen.getByText('배송완료');
    expect(badge).toHaveStyle({ backgroundColor: '#22c55e' });
  });

  it('cancelled 상태의 라벨과 색상을 올바르게 표시한다', () => {
    render(<StatusBadge status="cancelled" labels={LABELS} colors={COLORS} />);

    const badge = screen.getByText('취소');
    expect(badge).toHaveStyle({ backgroundColor: '#ef4444' });
  });

  it('정의되지 않은 status를 전달하면 라벨이 undefined로 표시된다', () => {
    render(<StatusBadge status="unknown" labels={LABELS} colors={COLORS} />);

    const badge = screen.getByText((_, element) => element?.tagName === 'SPAN');
    expect(badge).toBeInTheDocument();
  });

  it('inline-block으로 렌더링된다', () => {
    render(<StatusBadge status="pending" labels={LABELS} colors={COLORS} />);

    const badge = screen.getByText('대기');
    expect(badge).toHaveStyle({ display: 'inline-block' });
  });

  it('흰색 텍스트 색상이 적용된다', () => {
    render(<StatusBadge status="pending" labels={LABELS} colors={COLORS} />);

    const badge = screen.getByText('대기');
    expect(badge).toHaveStyle({ color: 'var(--color-white)' });
  });
});
