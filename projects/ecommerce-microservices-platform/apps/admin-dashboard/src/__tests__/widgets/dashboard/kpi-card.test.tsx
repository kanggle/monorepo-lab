import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { KpiCard } from '@/widgets/dashboard/KpiCard';

describe('KpiCard', () => {
  it('제목과 값을 렌더링한다', () => {
    render(<KpiCard title="오늘 주문" value="12건" subValue="120,000원" />);
    expect(screen.getByText('오늘 주문')).toBeInTheDocument();
    expect(screen.getByText('12건')).toBeInTheDocument();
    expect(screen.getByText('120,000원')).toBeInTheDocument();
  });

  it('isLoading 시 로딩 스켈레톤을 표시한다', () => {
    render(<KpiCard title="제목" value="X" isLoading />);
    expect(screen.getByLabelText('로딩 중')).toBeInTheDocument();
    expect(screen.queryByText('X')).not.toBeInTheDocument();
  });

  it('error 시 메시지와 재시도 버튼을 표시한다', async () => {
    const onRetry = vi.fn();
    render(<KpiCard title="제목" value="X" error="실패" onRetry={onRetry} />);
    expect(screen.getByText('실패')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: '다시 시도' }));
    expect(onRetry).toHaveBeenCalledOnce();
  });
});
