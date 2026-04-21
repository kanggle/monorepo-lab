import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import GlobalError from '@/app/error';

describe('GlobalError (web-store error boundary)', () => {
  it('에러 메시지를 표시한다', () => {
    const error = Object.assign(new Error('테스트 에러'), { digest: 'abc' });
    const reset = vi.fn();

    render(<GlobalError error={error} reset={reset} />);

    expect(screen.getByRole('alert')).toBeInTheDocument();
    expect(screen.getByText('문제가 발생했습니다')).toBeInTheDocument();
    expect(screen.getByText('테스트 에러')).toBeInTheDocument();
  });

  it('에러 메시지가 없을 때 기본 메시지를 표시한다', () => {
    const error = Object.assign(new Error(''), { digest: undefined });
    const reset = vi.fn();

    render(<GlobalError error={error} reset={reset} />);

    expect(screen.getByText('알 수 없는 오류가 발생했습니다.')).toBeInTheDocument();
  });

  it('다시 시도 버튼 클릭 시 reset을 호출한다', async () => {
    const user = userEvent.setup();
    const error = Object.assign(new Error('에러'), { digest: undefined });
    const reset = vi.fn();

    render(<GlobalError error={error} reset={reset} />);

    await user.click(screen.getByRole('button', { name: '다시 시도' }));

    expect(reset).toHaveBeenCalledTimes(1);
  });
});
