import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { UsePushSubscriptionResult } from '@/features/notification/model/use-push-subscription';

vi.mock('@/features/notification/model/use-push-subscription');

import { usePushSubscription } from '@/features/notification/model/use-push-subscription';
import { PushOptIn } from '@/features/notification/ui/PushOptIn';

const mockHook = vi.mocked(usePushSubscription);

function stub(overrides: Partial<UsePushSubscriptionResult>): UsePushSubscriptionResult {
  return {
    supported: true,
    permission: 'default',
    subscribed: false,
    isBusy: false,
    error: null,
    subscribe: vi.fn(),
    unsubscribe: vi.fn(),
    ...overrides,
  };
}

describe('PushOptIn', () => {
  beforeEach(() => vi.clearAllMocks());

  it('미지원 브라우저에서는 안내만 표시하고 버튼이 없다', () => {
    mockHook.mockReturnValue(stub({ supported: false, permission: 'unsupported' }));
    render(<PushOptIn />);
    expect(screen.getByText('이 브라우저는 푸시 알림을 지원하지 않습니다.')).toBeInTheDocument();
    expect(screen.queryByTestId('push-optin-button')).not.toBeInTheDocument();
  });

  it('권한 차단(denied) 시 설정 안내를 표시하고 버튼이 없다', () => {
    mockHook.mockReturnValue(stub({ permission: 'denied' }));
    render(<PushOptIn />);
    expect(screen.getByText(/브라우저 설정에서 이 사이트의 알림을 허용/)).toBeInTheDocument();
    expect(screen.queryByTestId('push-optin-button')).not.toBeInTheDocument();
  });

  it('미구독 상태에서 버튼 클릭 시 subscribe 를 호출한다', async () => {
    const subscribe = vi.fn();
    mockHook.mockReturnValue(stub({ subscribed: false, subscribe }));
    const user = userEvent.setup();
    render(<PushOptIn />);

    const button = screen.getByTestId('push-optin-button');
    expect(button).toHaveTextContent('이 브라우저에서 푸시 받기');
    await user.click(button);
    expect(subscribe).toHaveBeenCalledTimes(1);
  });

  it('구독 상태에서 버튼 클릭 시 unsubscribe 를 호출한다', async () => {
    const unsubscribe = vi.fn();
    mockHook.mockReturnValue(stub({ subscribed: true, permission: 'granted', unsubscribe }));
    const user = userEvent.setup();
    render(<PushOptIn />);

    const button = screen.getByTestId('push-optin-button');
    expect(button).toHaveTextContent('이 브라우저 구독 해지');
    await user.click(button);
    expect(unsubscribe).toHaveBeenCalledTimes(1);
  });

  it('처리 중이면 버튼이 비활성화된다', () => {
    mockHook.mockReturnValue(stub({ isBusy: true }));
    render(<PushOptIn />);
    expect(screen.getByTestId('push-optin-button')).toBeDisabled();
  });

  it('"이 브라우저에서 푸시 받기" 버튼 글자색이 좌측 메뉴 선택 색(--color-on-primary)과 동일하다 [FE-086]', () => {
    mockHook.mockReturnValue(stub({ subscribed: false }));
    render(<PushOptIn />);
    const button = screen.getByTestId('push-optin-button');
    expect(button).toHaveTextContent('이 브라우저에서 푸시 받기');
    expect(button.style.color).toBe('var(--color-on-primary)');
  });

  it('제목/설명 문구 없이 버튼만 표시한다 [FE-087]', () => {
    mockHook.mockReturnValue(stub({ subscribed: false }));
    render(<PushOptIn />);
    expect(screen.getByTestId('push-optin-button')).toBeInTheDocument();
    // The descriptive note is removed — only the button remains.
    expect(
      screen.queryByText('허용하면 주문·배송 알림을 이 브라우저에서 실시간으로 받습니다.'),
    ).not.toBeInTheDocument();
  });

  it('구독 상태에서도 안내 문구 없이 해지 버튼만 표시한다 [FE-087]', () => {
    mockHook.mockReturnValue(stub({ subscribed: true, permission: 'granted' }));
    render(<PushOptIn />);
    expect(screen.getByTestId('push-optin-button')).toHaveTextContent('이 브라우저 구독 해지');
    expect(screen.queryByText('이 브라우저에서 푸시 알림을 받고 있습니다.')).not.toBeInTheDocument();
  });

  it('에러가 있으면 에러 메시지를 표시한다', () => {
    mockHook.mockReturnValue(stub({ error: '푸시 알림 구독에 실패했습니다.' }));
    render(<PushOptIn />);
    expect(screen.getByTestId('push-optin-error')).toHaveTextContent('푸시 알림 구독에 실패했습니다.');
  });
});
