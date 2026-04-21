import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createElement } from 'react';
import { useUpdateTemplate } from '@/features/notification-management/hooks/use-update-template';

const mockUpdateTemplate = vi.fn().mockResolvedValue({ templateId: 't1' });

vi.mock('@/features/notification-management/api/notification-api', () => ({
  updateTemplate: (...args: unknown[]) => mockUpdateTemplate(...args),
}));

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: React.ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children);
}

describe('useUpdateTemplate', () => {
  let alertSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    mockUpdateTemplate.mockClear();
    alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {});
  });

  afterEach(() => {
    alertSpy.mockRestore();
  });

  it('템플릿 수정 mutation을 templateId와 데이터로 호출한다', async () => {
    const { result } = renderHook(() => useUpdateTemplate(), {
      wrapper: createWrapper(),
    });

    await result.current.mutateAsync({
      templateId: 't1',
      data: { subject: '수정된 제목', body: '수정된 본문' },
    });

    expect(mockUpdateTemplate).toHaveBeenCalledWith('t1', {
      subject: '수정된 제목',
      body: '수정된 본문',
    });
  });

  it('실패 시 기본 메시지로 alert를 표시한다', async () => {
    mockUpdateTemplate.mockRejectedValueOnce(null);

    const { result } = renderHook(() => useUpdateTemplate(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({
      templateId: 't1',
      data: { subject: '제목', body: '본문' },
    });

    await waitFor(() => {
      expect(result.current.isError).toBe(true);
    });

    expect(alertSpy).toHaveBeenCalledWith('알림 템플릿 수정에 실패했습니다.');
  });
});
