import { render, screen } from '@testing-library/react';
import { StatusBadge } from '@/shared/ui/StatusBadge';

describe('StatusBadge', () => {
  it('알려진 상태에 대해 한국어 라벨을 표시한다', () => {
    render(<StatusBadge status="ON_SALE" />);
    expect(screen.getByText('판매중')).toBeInTheDocument();
  });

  it('CANCELLED 상태를 올바르게 표시한다', () => {
    render(<StatusBadge status="CANCELLED" />);
    expect(screen.getByText('취소')).toBeInTheDocument();
  });

  it('알 수 없는 상태는 원본 문자열을 표시한다', () => {
    render(<StatusBadge status="UNKNOWN_STATUS" />);
    expect(screen.getByText('UNKNOWN_STATUS')).toBeInTheDocument();
  });
});
