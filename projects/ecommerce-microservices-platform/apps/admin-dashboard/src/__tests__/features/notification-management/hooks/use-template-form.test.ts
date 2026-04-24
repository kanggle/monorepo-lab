import { renderHook, act, waitFor } from '@testing-library/react';
import { useTemplateForm } from '@/features/notification-management/hooks/use-template-form';

const mockPush = vi.fn();
const mockCreateMutate = vi.fn().mockResolvedValue({ templateId: 'new-1' });
const mockUpdateMutate = vi.fn().mockResolvedValue({ templateId: 't1' });

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush }),
}));

vi.mock(
  '@/features/notification-management/hooks/use-create-template',
  () => ({
    useCreateTemplate: () => ({ mutateAsync: mockCreateMutate }),
  }),
);

vi.mock(
  '@/features/notification-management/hooks/use-update-template',
  () => ({
    useUpdateTemplate: () => ({ mutateAsync: mockUpdateMutate }),
  }),
);

function createEvent() {
  return { preventDefault: vi.fn() } as unknown as React.FormEvent;
}

describe('useTemplateForm', () => {
  beforeEach(() => {
    mockPush.mockClear();
    mockCreateMutate.mockClear();
    mockUpdateMutate.mockClear();
  });

  describe('등록 모드', () => {
    it('기본값으로 초기화된다', () => {
      const { result } = renderHook(() => useTemplateForm());

      expect(result.current.type).toBe('ORDER_PLACED');
      expect(result.current.channel).toBe('EMAIL');
      expect(result.current.subject).toBe('');
      expect(result.current.body).toBe('');
      expect(result.current.isEdit).toBe(false);
      expect(result.current.isValid).toBe(false);
    });

    it('subject와 body를 모두 입력해야 isValid가 true가 된다', () => {
      const { result } = renderHook(() => useTemplateForm());

      act(() => {
        result.current.setSubject('제목');
      });
      expect(result.current.isValid).toBe(false);

      act(() => {
        result.current.setBody('본문');
      });
      expect(result.current.isValid).toBe(true);
    });

    it('공백만 있는 입력은 유효하지 않다', () => {
      const { result } = renderHook(() => useTemplateForm());

      act(() => {
        result.current.setSubject('   ');
        result.current.setBody('   ');
      });
      expect(result.current.isValid).toBe(false);
    });

    it('handleSubmit 호출 시 createTemplate을 실행하고 목록으로 이동한다', async () => {
      const { result } = renderHook(() => useTemplateForm());

      act(() => {
        result.current.setSubject('  제목  ');
        result.current.setBody('  본문  ');
      });

      await act(async () => {
        await result.current.handleSubmit(createEvent());
      });

      expect(mockCreateMutate).toHaveBeenCalledWith({
        type: 'ORDER_PLACED',
        channel: 'EMAIL',
        subject: '제목',
        body: '본문',
      });
      expect(mockPush).toHaveBeenCalledWith('/notifications/templates');
    });

    it('유효하지 않은 상태에서는 mutation을 호출하지 않는다', async () => {
      const { result } = renderHook(() => useTemplateForm());

      await act(async () => {
        await result.current.handleSubmit(createEvent());
      });

      expect(mockCreateMutate).not.toHaveBeenCalled();
    });

    it('mutation 실패 시 error 상태를 설정한다', async () => {
      mockCreateMutate.mockRejectedValueOnce(new Error('생성 실패'));

      const { result } = renderHook(() => useTemplateForm());

      act(() => {
        result.current.setSubject('제목');
        result.current.setBody('본문');
      });

      await act(async () => {
        await result.current.handleSubmit(createEvent());
      });

      await waitFor(() => {
        expect(result.current.error).toBe('생성 실패');
      });
    });
  });

  describe('수정 모드', () => {
    const template = {
      templateId: 't1',
      type: 'SHIPPING_STATUS_CHANGED' as const,
      channel: 'SMS' as const,
      subject: '기존 제목',
      body: '기존 본문',
    };

    it('기존 데이터로 초기화된다', () => {
      const { result } = renderHook(() => useTemplateForm(template));

      expect(result.current.type).toBe('SHIPPING_STATUS_CHANGED');
      expect(result.current.channel).toBe('SMS');
      expect(result.current.subject).toBe('기존 제목');
      expect(result.current.body).toBe('기존 본문');
      expect(result.current.isEdit).toBe(true);
      expect(result.current.isValid).toBe(true);
    });

    it('handleSubmit 호출 시 updateTemplate을 실행한다', async () => {
      const { result } = renderHook(() => useTemplateForm(template));

      act(() => {
        result.current.setSubject('새 제목');
      });

      await act(async () => {
        await result.current.handleSubmit(createEvent());
      });

      expect(mockUpdateMutate).toHaveBeenCalledWith({
        templateId: 't1',
        data: { subject: '새 제목', body: '기존 본문' },
      });
      expect(mockPush).toHaveBeenCalledWith('/notifications/templates');
    });
  });
});
