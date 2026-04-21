import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { DeleteConfirmation } from '@/features/user/ui/DeleteConfirmation';

describe('DeleteConfirmation', () => {
  it('삭제 확인 메시지를 표시한다', () => {
    render(<DeleteConfirmation isDeleting={false} onConfirm={vi.fn()} onCancel={vi.fn()} />);

    expect(screen.getByText('이 배송지를 삭제하시겠습니까?')).toBeInTheDocument();
  });

  it('삭제 버튼 클릭 시 onConfirm을 호출한다', async () => {
    const onConfirm = vi.fn();
    render(<DeleteConfirmation isDeleting={false} onConfirm={onConfirm} onCancel={vi.fn()} />);

    await userEvent.click(screen.getByText('삭제'));
    expect(onConfirm).toHaveBeenCalledTimes(1);
  });

  it('취소 버튼 클릭 시 onCancel을 호출한다', async () => {
    const onCancel = vi.fn();
    render(<DeleteConfirmation isDeleting={false} onConfirm={vi.fn()} onCancel={onCancel} />);

    await userEvent.click(screen.getByText('취소'));
    expect(onCancel).toHaveBeenCalledTimes(1);
  });

  it('삭제 중일 때 버튼이 비활성화된다', () => {
    render(<DeleteConfirmation isDeleting={true} onConfirm={vi.fn()} onCancel={vi.fn()} />);

    expect(screen.getByText('삭제 중...')).toBeDisabled();
  });
});
