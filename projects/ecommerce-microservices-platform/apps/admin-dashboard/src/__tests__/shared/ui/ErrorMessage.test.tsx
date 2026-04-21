import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ErrorMessage } from '@repo/ui';

describe('ErrorMessage', () => {
  it('에러 메시지를 표시한다', () => {
    render(<ErrorMessage message="오류가 발생했습니다." />);
    expect(screen.getByRole('alert')).toBeInTheDocument();
    expect(screen.getByText('오류가 발생했습니다.')).toBeInTheDocument();
  });

  it('재시도 버튼이 없으면 표시하지 않는다', () => {
    render(<ErrorMessage message="오류" />);
    expect(screen.queryByText('다시 시도')).not.toBeInTheDocument();
  });

  it('재시도 콜백이 있으면 버튼을 표시하고 클릭 시 호출한다', async () => {
    const onRetry = vi.fn();
    render(<ErrorMessage message="오류" onRetry={onRetry} />);

    const button = screen.getByText('다시 시도');
    await userEvent.click(button);

    expect(onRetry).toHaveBeenCalledTimes(1);
  });
});
