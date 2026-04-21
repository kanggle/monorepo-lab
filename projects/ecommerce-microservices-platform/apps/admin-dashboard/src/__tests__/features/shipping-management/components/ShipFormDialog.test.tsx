import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ShipFormDialog } from '@/features/shipping-management/components/ShipFormDialog';

describe('ShipFormDialog', () => {
  it('open이 false이면 렌더링하지 않는다', () => {
    render(
      <ShipFormDialog open={false} isPending={false} onConfirm={vi.fn()} onCancel={vi.fn()} />,
    );

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('open이 true이면 폼을 표시한다', () => {
    render(
      <ShipFormDialog open={true} isPending={false} onConfirm={vi.fn()} onCancel={vi.fn()} />,
    );

    expect(screen.getByRole('dialog', { name: '발송 처리' })).toBeInTheDocument();
    expect(screen.getByLabelText('택배사')).toBeInTheDocument();
    expect(screen.getByLabelText('운송장 번호')).toBeInTheDocument();
  });

  it('택배사와 운송장 번호를 입력하지 않으면 발송 처리 버튼이 비활성이다', () => {
    render(
      <ShipFormDialog open={true} isPending={false} onConfirm={vi.fn()} onCancel={vi.fn()} />,
    );

    const submitButton = screen.getByRole('button', { name: '발송 처리' });
    expect(submitButton).toBeDisabled();
  });

  it('택배사와 운송장 번호를 입력하면 발송 처리 버튼이 활성화된다', async () => {
    const user = userEvent.setup();
    render(
      <ShipFormDialog open={true} isPending={false} onConfirm={vi.fn()} onCancel={vi.fn()} />,
    );

    await user.type(screen.getByLabelText('택배사'), 'CJ대한통운');
    await user.type(screen.getByLabelText('운송장 번호'), '1234567890');

    const submitButton = screen.getByRole('button', { name: '발송 처리' });
    expect(submitButton).not.toBeDisabled();
  });

  it('발송 처리 버튼 클릭 시 onConfirm을 호출한다', async () => {
    const user = userEvent.setup();
    const onConfirm = vi.fn();
    render(
      <ShipFormDialog open={true} isPending={false} onConfirm={onConfirm} onCancel={vi.fn()} />,
    );

    await user.type(screen.getByLabelText('택배사'), 'CJ대한통운');
    await user.type(screen.getByLabelText('운송장 번호'), '1234567890');
    await user.click(screen.getByRole('button', { name: '발송 처리' }));

    expect(onConfirm).toHaveBeenCalledWith('1234567890', 'CJ대한통운');
  });

  it('취소 버튼 클릭 시 onCancel을 호출한다', async () => {
    const user = userEvent.setup();
    const onCancel = vi.fn();
    render(
      <ShipFormDialog open={true} isPending={false} onConfirm={vi.fn()} onCancel={onCancel} />,
    );

    await user.click(screen.getByRole('button', { name: '취소' }));

    expect(onCancel).toHaveBeenCalled();
  });

  it('isPending이 true이면 발송 처리 버튼에 처리 중 텍스트가 표시된다', async () => {
    const user = userEvent.setup();
    render(
      <ShipFormDialog open={true} isPending={true} onConfirm={vi.fn()} onCancel={vi.fn()} />,
    );

    await user.type(screen.getByLabelText('택배사'), 'CJ대한통운');
    await user.type(screen.getByLabelText('운송장 번호'), '1234567890');

    expect(screen.getByRole('button', { name: '처리 중...' })).toBeDisabled();
  });
});
