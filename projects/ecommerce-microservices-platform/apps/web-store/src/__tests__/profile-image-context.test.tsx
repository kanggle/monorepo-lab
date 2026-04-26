import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ProfileImageProvider, useProfileImage } from '@/shared/context/ProfileImageContext';

function TestConsumer() {
  const { imageUrl, setImageUrl } = useProfileImage();
  return (
    <div>
      <span data-testid="image-url">{imageUrl || 'empty'}</span>
      <button onClick={() => setImageUrl('https://example.com/avatar.png')}>
        set-image
      </button>
      <button onClick={() => setImageUrl('')}>clear-image</button>
    </div>
  );
}

describe('ProfileImageContext', () => {
  it('초기 imageUrl은 빈 문자열이다', () => {
    render(
      <ProfileImageProvider>
        <TestConsumer />
      </ProfileImageProvider>,
    );

    expect(screen.getByTestId('image-url').textContent).toBe('empty');
  });

  it('setImageUrl로 이미지 URL을 설정할 수 있다', async () => {
    const user = userEvent.setup();

    render(
      <ProfileImageProvider>
        <TestConsumer />
      </ProfileImageProvider>,
    );

    await user.click(screen.getByText('set-image'));

    expect(screen.getByTestId('image-url').textContent).toBe('https://example.com/avatar.png');
  });

  it('setImageUrl로 이미지 URL을 빈 문자열로 초기화할 수 있다', async () => {
    const user = userEvent.setup();

    render(
      <ProfileImageProvider>
        <TestConsumer />
      </ProfileImageProvider>,
    );

    await user.click(screen.getByText('set-image'));
    expect(screen.getByTestId('image-url').textContent).toBe('https://example.com/avatar.png');

    await user.click(screen.getByText('clear-image'));
    expect(screen.getByTestId('image-url').textContent).toBe('empty');
  });

  it('여러 컨슈머가 동일한 상태를 공유한다', async () => {
    const user = userEvent.setup();

    function SecondConsumer() {
      const { imageUrl } = useProfileImage();
      return <span data-testid="second-url">{imageUrl || 'empty'}</span>;
    }

    render(
      <ProfileImageProvider>
        <TestConsumer />
        <SecondConsumer />
      </ProfileImageProvider>,
    );

    expect(screen.getByTestId('second-url').textContent).toBe('empty');

    await user.click(screen.getByText('set-image'));

    expect(screen.getByTestId('image-url').textContent).toBe('https://example.com/avatar.png');
    expect(screen.getByTestId('second-url').textContent).toBe('https://example.com/avatar.png');
  });

  it('Provider 없이 useProfileImage를 사용하면 기본값을 반환한다', () => {
    render(<TestConsumer />);

    expect(screen.getByTestId('image-url').textContent).toBe('empty');
  });
});
