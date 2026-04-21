import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { StarRating } from '@/features/review/ui/StarRating';

describe('StarRating', () => {
  it('별점 수만큼 5개의 별 버튼을 렌더링한다', () => {
    render(<StarRating rating={3} />);

    for (let i = 1; i <= 5; i++) {
      expect(screen.getByRole('button', { name: `${i}점` })).toBeInTheDocument();
    }
  });

  it('onChange가 없으면 비활성 모드로 radiogroup 역할을 사용하지 않는다', () => {
    render(<StarRating rating={3} />);

    expect(screen.queryByRole('radiogroup')).not.toBeInTheDocument();
    expect(screen.getByLabelText('별점 3점')).toBeInTheDocument();
    for (let i = 1; i <= 5; i++) {
      expect(screen.getByRole('button', { name: `${i}점` })).toBeDisabled();
    }
  });

  it('onChange가 있으면 radiogroup 역할을 갖고 각 별이 radio가 된다', () => {
    render(<StarRating rating={3} onChange={vi.fn()} />);

    expect(screen.getByRole('radiogroup', { name: '별점 선택' })).toBeInTheDocument();
    expect(screen.getAllByRole('radio')).toHaveLength(5);
  });

  it('현재 rating과 일치하는 별이 aria-checked=true이다', () => {
    render(<StarRating rating={4} onChange={vi.fn()} />);

    const radios = screen.getAllByRole('radio');
    expect(radios[3]).toHaveAttribute('aria-checked', 'true');
    expect(radios[0]).toHaveAttribute('aria-checked', 'false');
  });

  it('별 클릭 시 onChange가 해당 점수로 호출된다', () => {
    const onChange = vi.fn();
    render(<StarRating rating={2} onChange={onChange} />);

    fireEvent.click(screen.getByRole('radio', { name: '5점' }));

    expect(onChange).toHaveBeenCalledWith(5);
  });
});
