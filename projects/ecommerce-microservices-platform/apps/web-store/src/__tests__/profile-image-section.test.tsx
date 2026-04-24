import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ProfileImageSection } from '@/features/user/ui/ProfileImageSection';

describe('ProfileImageSection', () => {
  const defaultProps = {
    profileImageUrl: '',
    profileName: '홍길동',
    onImageChange: vi.fn(),
  };

  it('프로필 이미지 URL이 없으면 이름 첫 글자를 표시한다', () => {
    render(<ProfileImageSection {...defaultProps} />);

    expect(screen.getByText('홍')).toBeInTheDocument();
  });

  it('프로필 이미지 URL이 있으면 이미지를 표시한다', () => {
    render(<ProfileImageSection {...defaultProps} profileImageUrl="/img/profile.jpg" />);

    const img = screen.getByAltText('프로필');
    expect(img).toBeInTheDocument();
    expect(img).toHaveAttribute('src', '/img/profile.jpg');
  });

  it('선택 버튼을 표시한다', () => {
    render(<ProfileImageSection {...defaultProps} />);

    expect(screen.getByText('선택')).toBeInTheDocument();
  });

  it('이미지가 있으면 삭제 버튼을 표시한다', () => {
    render(<ProfileImageSection {...defaultProps} profileImageUrl="/img/profile.jpg" />);

    expect(screen.getByText('삭제')).toBeInTheDocument();
  });

  it('이미지가 없으면 삭제 버튼을 표시하지 않는다', () => {
    render(<ProfileImageSection {...defaultProps} />);

    expect(screen.queryByText('삭제')).not.toBeInTheDocument();
  });

  it('삭제 버튼 클릭 시 빈 문자열로 onImageChange를 호출한다', async () => {
    const onImageChange = vi.fn();
    const user = userEvent.setup();
    render(
      <ProfileImageSection
        {...defaultProps}
        profileImageUrl="/img/profile.jpg"
        onImageChange={onImageChange}
      />,
    );

    await user.click(screen.getByText('삭제'));

    expect(onImageChange).toHaveBeenCalledWith('');
  });

  it('선택 버튼 클릭 시 파일 입력을 트리거한다', async () => {
    const user = userEvent.setup();
    render(<ProfileImageSection {...defaultProps} />);

    const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement;
    expect(fileInput).toBeTruthy();
    expect(fileInput.style.display).toBe('none');

    const clickSpy = vi.spyOn(fileInput, 'click');
    await user.click(screen.getByText('선택'));

    expect(clickSpy).toHaveBeenCalled();
  });
});
