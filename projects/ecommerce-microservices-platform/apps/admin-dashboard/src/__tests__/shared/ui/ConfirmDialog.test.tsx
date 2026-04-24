import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ConfirmDialog } from '@/shared/ui/ConfirmDialog';

describe('ConfirmDialog', () => {
  const defaultProps = {
    open: true,
    title: '삭제 확인',
    message: '정말 삭제하시겠습니까?',
    onConfirm: vi.fn(),
    onCancel: vi.fn(),
  };

  it('open이 false이면 렌더링하지 않는다', () => {
    render(<ConfirmDialog {...defaultProps} open={false} />);
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('open이 true이면 제목과 메시지를 표시한다', () => {
    render(<ConfirmDialog {...defaultProps} />);
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(screen.getByText('삭제 확인')).toBeInTheDocument();
    expect(screen.getByText('정말 삭제하시겠습니까?')).toBeInTheDocument();
  });

  it('확인 버튼 클릭 시 onConfirm을 호출한다', async () => {
    const onConfirm = vi.fn();
    render(<ConfirmDialog {...defaultProps} onConfirm={onConfirm} />);

    await userEvent.click(screen.getByText('확인'));
    expect(onConfirm).toHaveBeenCalledTimes(1);
  });

  it('취소 버튼 클릭 시 onCancel을 호출한다', async () => {
    const onCancel = vi.fn();
    render(<ConfirmDialog {...defaultProps} onCancel={onCancel} />);

    await userEvent.click(screen.getByText('취소'));
    expect(onCancel).toHaveBeenCalledTimes(1);
  });

  it('커스텀 버튼 라벨을 표시한다', () => {
    render(
      <ConfirmDialog
        {...defaultProps}
        confirmLabel="삭제"
        cancelLabel="돌아가기"
      />,
    );
    expect(screen.getByText('삭제')).toBeInTheDocument();
    expect(screen.getByText('돌아가기')).toBeInTheDocument();
  });
});
