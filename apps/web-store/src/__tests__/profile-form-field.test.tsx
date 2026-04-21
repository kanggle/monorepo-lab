import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ProfileFormField } from '@/features/user/ui/ProfileFormField';

describe('ProfileFormField', () => {
  const defaultProps = {
    id: 'nickname',
    label: '닉네임',
    type: 'text' as const,
    value: '',
    onChange: vi.fn(),
  };

  it('라벨과 입력 필드를 표시한다', () => {
    render(<ProfileFormField {...defaultProps} />);

    expect(screen.getByLabelText('닉네임')).toBeInTheDocument();
  });

  it('값을 표시한다', () => {
    render(<ProfileFormField {...defaultProps} value="테스트닉네임" />);

    expect(screen.getByLabelText('닉네임')).toHaveValue('테스트닉네임');
  });

  it('입력 시 onChange를 호출한다', async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    render(<ProfileFormField {...defaultProps} onChange={onChange} />);

    await user.type(screen.getByLabelText('닉네임'), 'a');

    expect(onChange).toHaveBeenCalledWith('a');
  });

  it('placeholder를 표시한다', () => {
    render(<ProfileFormField {...defaultProps} placeholder="닉네임을 입력하세요" />);

    expect(screen.getByPlaceholderText('닉네임을 입력하세요')).toBeInTheDocument();
  });

  it('에러 메시지를 표시한다', () => {
    render(<ProfileFormField {...defaultProps} error="필수 입력값입니다." />);

    expect(screen.getByRole('alert')).toHaveTextContent('필수 입력값입니다.');
  });

  it('에러가 없으면 에러 메시지를 표시하지 않는다', () => {
    render(<ProfileFormField {...defaultProps} />);

    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('type이 tel이면 tel 입력 필드를 렌더링한다', () => {
    render(<ProfileFormField {...defaultProps} type="tel" />);

    expect(screen.getByLabelText('닉네임')).toHaveAttribute('type', 'tel');
  });
});
