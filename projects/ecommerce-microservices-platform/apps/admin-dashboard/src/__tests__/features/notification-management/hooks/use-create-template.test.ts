import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createElement } from 'react';
import { useCreateTemplate } from '@/features/notification-management/hooks/use-create-template';

const mockCreateTemplate = vi.fn().mockResolvedValue({ templateId: 'new-1' });

vi.mock('@/features/notification-management/api/notification-api', () => ({
  createTemplate: (...args: unknown[]) => mockCreateTemplate(...args),
}));

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: React.ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children);
}

describe('useCreateTemplate', () => {
  let alertSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    mockCreateTemplate.mockClear();
    alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {});
  });

  afterEach(() => {
    alertSpy.mockRestore();
  });

  it('템플릿 생성 mutation을 호출한다', async () => {
    const { result } = renderHook(() => useCreateTemplate(), {
      wrapper: createWrapper(),
    });

    const data = {
      type: 'ORDER_PLACED' as const,
      channel: 'EMAIL' as const,
      subject: '제목',
      body: '본문',
    };

    await result.current.mutateAsync(data);

    expect(mockCreateTemplate).toHaveBeenCalledWith(data, expect.anything());
  });

  it('TEMPLATE_ALREADY_EXISTS 에러 시 중복 메시지로 alert를 표시한다', async () => {
    mockCreateTemplate.mockRejectedValueOnce({
      code: 'TEMPLATE_ALREADY_EXISTS',
      message: 'already exists',
      timestamp: '2026-01-01T00:00:00Z',
    });

    const { result } = renderHook(() => useCreateTemplate(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({
      type: 'ORDER_PLACED',
      channel: 'EMAIL',
      subject: '제목',
      body: '본문',
    });

    await waitFor(() => {
      expect(result.current.isError).toBe(true);
    });

    expect(alertSpy).toHaveBeenCalledWith(
      '동일한 유형/채널 조합의 템플릿이 이미 존재합니다',
    );
  });

  it('일반 에러 시 기본 메시지로 alert를 표시한다', async () => {
    mockCreateTemplate.mockRejectedValueOnce(new Error('네트워크 오류'));

    const { result } = renderHook(() => useCreateTemplate(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({
      type: 'ORDER_PLACED',
      channel: 'EMAIL',
      subject: '제목',
      body: '본문',
    });

    await waitFor(() => {
      expect(result.current.isError).toBe(true);
    });

    expect(alertSpy).toHaveBeenCalledWith('네트워크 오류');
  });
});
