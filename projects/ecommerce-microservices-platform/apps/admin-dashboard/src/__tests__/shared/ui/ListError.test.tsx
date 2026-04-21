import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ListError } from '@/shared/ui/ListError';

describe('ListError', () => {
  it('에러 메시지를 표시한다', () => {
    render(<ListError message="데이터를 불러올 수 없습니다." onRetry={vi.fn()} />);
    expect(screen.getByText('데이터를 불러올 수 없습니다.')).toBeInTheDocument();
  });

  it('role="alert"을 가진다', () => {
    render(<ListError message="에러" onRetry={vi.fn()} />);
    expect(screen.getByRole('alert')).toBeInTheDocument();
  });

  it('다시 시도 버튼 클릭 시 onRetry를 호출한다', async () => {
    const user = userEvent.setup();
    const onRetry = vi.fn();
    render(<ListError message="에러" onRetry={onRetry} />);

    await user.click(screen.getByRole('button', { name: '다시 시도' }));

    expect(onRetry).toHaveBeenCalledTimes(1);
  });
});
