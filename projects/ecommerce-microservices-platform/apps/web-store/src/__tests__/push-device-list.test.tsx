import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { PushSubscriptionDevice } from '@repo/types';

vi.mock('@/features/notification/model/use-push-devices');

import { usePushDevices } from '@/features/notification/model/use-push-devices';
import { PushDeviceList } from '@/features/notification/ui/PushDeviceList';

const mockHook = vi.mocked(usePushDevices);

type HookResult = ReturnType<typeof usePushDevices>;

const CHROME_WIN =
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36';
const CHROME_ANDROID =
  'Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36';

function device(overrides: Partial<PushSubscriptionDevice>): PushSubscriptionDevice {
  return {
    id: 'sub-1',
    endpoint: 'https://fcm.example/AAA',
    userAgent: CHROME_WIN,
    createdAt: '2026-07-02T09:00:00Z',
    ...overrides,
  };
}

function stub(overrides: Partial<HookResult>): HookResult {
  return {
    devices: [],
    currentEndpoint: null,
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
    removeDevice: vi.fn().mockResolvedValue(undefined),
    removingEndpoint: null,
    ...overrides,
  } as HookResult;
}

describe('PushDeviceList (TASK-FE-085)', () => {
  beforeEach(() => vi.clearAllMocks());

  it('로딩 중이면 안내 문구를 표시한다', () => {
    mockHook.mockReturnValue(stub({ isLoading: true }));
    render(<PushDeviceList />);
    expect(screen.getByText('기기 목록을 불러오는 중…')).toBeInTheDocument();
  });

  it('기기가 없으면 빈 상태 안내를 표시한다', () => {
    mockHook.mockReturnValue(stub({ devices: [] }));
    render(<PushDeviceList />);
    expect(screen.getByText('이 계정으로 푸시를 받도록 등록된 기기가 없습니다.')).toBeInTheDocument();
  });

  it('기기 목록을 라벨과 함께 렌더링한다', () => {
    mockHook.mockReturnValue(
      stub({
        devices: [
          device({ id: 's1', endpoint: 'https://fcm.example/A', userAgent: CHROME_WIN }),
          device({ id: 's2', endpoint: 'https://fcm.example/B', userAgent: CHROME_ANDROID }),
        ],
      }),
    );
    render(<PushDeviceList />);
    expect(screen.getByText('Windows · Chrome')).toBeInTheDocument();
    expect(screen.getByText('Android · Chrome')).toBeInTheDocument();
    expect(screen.getAllByTestId('push-device-item')).toHaveLength(2);
  });

  it('현재 브라우저 endpoint 와 일치하는 기기에만 "이 기기" 배지를 표시한다', () => {
    mockHook.mockReturnValue(
      stub({
        currentEndpoint: 'https://fcm.example/B',
        devices: [
          device({ id: 's1', endpoint: 'https://fcm.example/A', userAgent: CHROME_WIN }),
          device({ id: 's2', endpoint: 'https://fcm.example/B', userAgent: CHROME_ANDROID }),
        ],
      }),
    );
    render(<PushDeviceList />);
    const badges = screen.getAllByTestId('current-device-badge');
    expect(badges).toHaveLength(1);
    expect(badges[0]).toHaveTextContent('이 기기');
  });

  it('해지 버튼 클릭 시 해당 endpoint 로 removeDevice 를 호출한다', async () => {
    const removeDevice = vi.fn().mockResolvedValue(undefined);
    mockHook.mockReturnValue(
      stub({
        removeDevice,
        devices: [device({ id: 's1', endpoint: 'https://fcm.example/A' })],
      }),
    );
    const user = userEvent.setup();
    render(<PushDeviceList />);
    await user.click(screen.getByRole('button', { name: '이 기기 푸시 해지' }));
    expect(removeDevice).toHaveBeenCalledWith('https://fcm.example/A');
  });

  it('해지 진행 중인 기기의 버튼은 비활성화된다', () => {
    mockHook.mockReturnValue(
      stub({
        removingEndpoint: 'https://fcm.example/A',
        devices: [device({ id: 's1', endpoint: 'https://fcm.example/A' })],
      }),
    );
    render(<PushDeviceList />);
    expect(screen.getByRole('button', { name: '이 기기 푸시 해지' })).toBeDisabled();
  });
});
