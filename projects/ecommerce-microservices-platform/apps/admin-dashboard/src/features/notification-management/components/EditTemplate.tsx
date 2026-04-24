'use client';

import { PageLayout } from '@/shared/ui';
import { ErrorMessage } from '@repo/ui';
import { useTemplate } from '../hooks/use-template';
import { TemplateForm } from './TemplateForm';

interface Props {
  templateId: string;
}

export function EditTemplate({ templateId }: Props) {
  const { data: template, isLoading, isError, refetch } = useTemplate(templateId);

  if (isLoading || !template) {
    return <PageLayout.Skeleton />;
  }

  if (isError) {
    return (
      <ErrorMessage
        message="알림 템플릿 정보를 불러오는데 실패했습니다."
        onRetry={() => refetch()}
      />
    );
  }

  return (
    <PageLayout
      title="알림 템플릿 수정"
      actions={[
        {
          label: '← 템플릿 목록',
          href: '/notifications/templates',
          variant: 'secondary' as const,
        },
      ]}
    >
      <TemplateForm template={template} />
    </PageLayout>
  );
}
