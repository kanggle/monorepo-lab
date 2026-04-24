import { render, screen } from '@testing-library/react';
import { EditTemplate } from '@/features/notification-management/components/EditTemplate';

const mockRefetch = vi.fn();
let mockUseTemplate: {
  data: unknown;
  isLoading: boolean;
  isError: boolean;
  refetch: () => void;
};

vi.mock('@/features/notification-management/hooks/use-template', () => ({
  useTemplate: () => mockUseTemplate,
}));

vi.mock('@/shared/ui', () => ({
  PageLayout: Object.assign(
    ({ title, children }: { title: string; children: React.ReactNode }) => (
      <div data-testid="page-layout" data-title={title}>
        {children}
      </div>
    ),
    { Skeleton: () => <div data-testid="skeleton" /> },
  ),
}));

vi.mock('@repo/ui', () => ({
  ErrorMessage: ({ message, onRetry }: { message: string; onRetry: () => void }) => (
    <div data-testid="error-message">
      <span>{message}</span>
      <button onClick={onRetry}>다시 시도</button>
    </div>
  ),
}));

vi.mock('@/features/notification-management/components/TemplateForm', () => ({
  TemplateForm: ({ template }: { template: unknown }) => (
    <div data-testid="template-form" data-template={JSON.stringify(template)} />
  ),
}));

describe('EditTemplate', () => {
  beforeEach(() => {
    mockRefetch.mockClear();
  });

  it('로딩 중이면 Skeleton을 표시한다', () => {
    mockUseTemplate = {
      data: undefined,
      isLoading: true,
      isError: false,
      refetch: mockRefetch,
    };

    render(<EditTemplate templateId="t1" />);

    expect(screen.getByTestId('skeleton')).toBeInTheDocument();
  });

  it('data가 없으면 Skeleton을 표시한다', () => {
    mockUseTemplate = {
      data: undefined,
      isLoading: false,
      isError: false,
      refetch: mockRefetch,
    };

    render(<EditTemplate templateId="t1" />);

    expect(screen.getByTestId('skeleton')).toBeInTheDocument();
  });

  it('데이터 로드 성공 시 PageLayout과 TemplateForm을 렌더링한다', () => {
    mockUseTemplate = {
      data: {
        templateId: 't1',
        type: 'ORDER_PLACED',
        channel: 'EMAIL',
        subject: '제목',
        body: '본문',
      },
      isLoading: false,
      isError: false,
      refetch: mockRefetch,
    };

    render(<EditTemplate templateId="t1" />);

    expect(screen.getByTestId('page-layout')).toHaveAttribute(
      'data-title',
      '알림 템플릿 수정',
    );
    expect(screen.getByTestId('template-form')).toBeInTheDocument();
  });
});
