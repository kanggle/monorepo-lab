import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { QuantityControl } from '@/features/cart/ui/QuantityControl';

describe('QuantityControl', () => {
  it('현재 수량을 표시한다', () => {
    render(<QuantityControl quantity={3} onDecrease={vi.fn()} onIncrease={vi.fn()} />);

    expect(screen.getByText('3')).toBeInTheDocument();
  });

  it('감소 버튼 클릭 시 onDecrease를 호출한다', () => {
    const onDecrease = vi.fn();
    render(<QuantityControl quantity={2} onDecrease={onDecrease} onIncrease={vi.fn()} />);

    fireEvent.click(screen.getByRole('button', { name: '−' }));

    expect(onDecrease).toHaveBeenCalledTimes(1);
  });

  it('증가 버튼 클릭 시 onIncrease를 호출한다', () => {
    const onIncrease = vi.fn();
    render(<QuantityControl quantity={2} onDecrease={vi.fn()} onIncrease={onIncrease} />);

    fireEvent.click(screen.getByRole('button', { name: '+' }));

    expect(onIncrease).toHaveBeenCalledTimes(1);
  });

  it('수량이 1일 때 감소 버튼이 비활성화된다', () => {
    render(<QuantityControl quantity={1} onDecrease={vi.fn()} onIncrease={vi.fn()} />);

    expect(screen.getByRole('button', { name: '−' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '+' })).not.toBeDisabled();
  });

  it('수량이 1보다 클 때 감소 버튼이 활성화된다', () => {
    render(<QuantityControl quantity={5} onDecrease={vi.fn()} onIncrease={vi.fn()} />);

    expect(screen.getByRole('button', { name: '−' })).not.toBeDisabled();
  });
});
