'use client';

import { PageLayout } from '@/shared/ui';
import { TemplateForm } from '@/features/notification-management';

export default function NewTemplatePage() {
  return (
    <PageLayout
      title="알림 템플릿 등록"
      actions={[
        {
          label: '← 템플릿 목록',
          href: '/notifications/templates',
          variant: 'secondary' as const,
        },
      ]}
    >
      <TemplateForm />
    </PageLayout>
  );
}
