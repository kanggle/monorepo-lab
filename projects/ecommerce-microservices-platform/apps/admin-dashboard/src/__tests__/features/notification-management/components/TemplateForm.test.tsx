import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { TemplateForm } from '@/features/notification-management/components/TemplateForm';

const mockPush = vi.fn();
const mockBack = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush, back: mockBack }),
}));

const mockCreateTemplate = vi.fn().mockResolvedValue({ templateId: 'new-1' });
const mockUpdateTemplate = vi.fn().mockResolvedValue({ templateId: 't1' });

vi.mock(
  '@/features/notification-management/hooks/use-create-template',
  () => ({
    useCreateTemplate: () => ({
      mutateAsync: mockCreateTemplate,
      isPending: false,
    }),
  }),
);

vi.mock(
  '@/features/notification-management/hooks/use-update-template',
  () => ({
    useUpdateTemplate: () => ({
      mutateAsync: mockUpdateTemplate,
      isPending: false,
    }),
  }),
);

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
}

describe('TemplateForm', () => {
  beforeEach(() => {
    mockCreateTemplate.mockClear();
    mockUpdateTemplate.mockClear();
    mockPush.mockClear();
    mockBack.mockClear();
  });

  describe('등록 모드', () => {
    it('빈 폼을 렌더링한다', () => {
      render(<TemplateForm />, { wrapper: createWrapper() });

      expect(screen.getByLabelText('유형 *')).toHaveValue('ORDER_PLACED');
      expect(screen.getByLabelText('채널 *')).toHaveValue('EMAIL');
      expect(screen.getByLabelText('제목 *')).toHaveValue('');
      expect(screen.getByLabelText('본문 *')).toHaveValue('');
    });

    it('필수 필드가 비어있으면 등록 버튼이 비활성화된다', () => {
      render(<TemplateForm />, { wrapper: createWrapper() });

      const submitButton = screen.getByText('등록');
      expect(submitButton).toBeDisabled();
    });

    it('필수 필드 입력 후 등록 버튼이 활성화된다', async () => {
      render(<TemplateForm />, { wrapper: createWrapper() });

      await userEvent.type(screen.getByLabelText('제목 *'), '주문 확인');
      await userEvent.type(
        screen.getByLabelText('본문 *'),
        '{{userName}}님의 주문이 접수되었습니다.',
      );

      expect(screen.getByText('등록')).not.toBeDisabled();
    });

    it('폼 제출 시 createTemplate을 호출한다', async () => {
      render(<TemplateForm />, { wrapper: createWrapper() });

      await userEvent.type(screen.getByLabelText('제목 *'), '주문 확인');
      await userEvent.type(
        screen.getByLabelText('본문 *'),
        '주문이 접수되었습니다.',
      );
      await userEvent.click(screen.getByText('등록'));

      expect(mockCreateTemplate).toHaveBeenCalledWith({
        type: 'ORDER_PLACED',
        channel: 'EMAIL',
        subject: '주문 확인',
        body: '주문이 접수되었습니다.',
      });
    });

    it('유형과 채널을 선택할 수 있다', () => {
      render(<TemplateForm />, { wrapper: createWrapper() });

      expect(screen.getByLabelText('유형 *')).not.toBeDisabled();
      expect(screen.getByLabelText('채널 *')).not.toBeDisabled();
    });

    it('플레이스홀더 안내 텍스트가 표시된다', () => {
      render(<TemplateForm />, { wrapper: createWrapper() });

      expect(
        screen.getByText(/플레이스홀더를 사용할 수 있습니다/),
      ).toBeInTheDocument();
      expect(
        screen.getByText(/사용 가능한 변수/),
      ).toBeInTheDocument();
    });
  });

  describe('수정 모드', () => {
    const template = {
      templateId: 't1',
      type: 'ORDER_PLACED' as const,
      channel: 'EMAIL' as const,
      subject: '기존 제목',
      body: '기존 본문',
    };

    it('기존 데이터로 폼을 채운다', () => {
      render(<TemplateForm template={template} />, {
        wrapper: createWrapper(),
      });

      expect(screen.getByLabelText('제목 *')).toHaveValue('기존 제목');
      expect(screen.getByLabelText('본문 *')).toHaveValue('기존 본문');
    });

    it('수정 모드에서 유형과 채널이 비활성화된다', () => {
      render(<TemplateForm template={template} />, {
        wrapper: createWrapper(),
      });

      expect(screen.getByLabelText('유형 *')).toBeDisabled();
      expect(screen.getByLabelText('채널 *')).toBeDisabled();
    });

    it('수정 모드에서 수정 버튼이 표시된다', () => {
      render(<TemplateForm template={template} />, {
        wrapper: createWrapper(),
      });

      expect(screen.getByText('수정')).toBeInTheDocument();
    });

    it('취소 버튼 클릭 시 뒤로 이동한다', async () => {
      render(<TemplateForm template={template} />, {
        wrapper: createWrapper(),
      });

      await userEvent.click(screen.getByText('취소'));
      expect(mockBack).toHaveBeenCalledTimes(1);
    });

    it('폼 제출 시 updateTemplate을 호출한다', async () => {
      render(<TemplateForm template={template} />, {
        wrapper: createWrapper(),
      });

      await userEvent.clear(screen.getByLabelText('제목 *'));
      await userEvent.type(screen.getByLabelText('제목 *'), '수정된 제목');
      await userEvent.click(screen.getByText('수정'));

      expect(mockUpdateTemplate).toHaveBeenCalledWith({
        templateId: 't1',
        data: { subject: '수정된 제목', body: '기존 본문' },
      });
    });
  });
});
