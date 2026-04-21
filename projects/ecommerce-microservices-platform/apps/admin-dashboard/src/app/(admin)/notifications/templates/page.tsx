'use client';

import { Suspense } from 'react';
import { PageLayout } from '@/shared/ui';
import { LoadingSpinner } from '@repo/ui';
import { TemplateList } from '@/features/notification-management';

export default function TemplatesPage() {
  return (
    <PageLayout
      title="알림 템플릿 관리"
      actions={[{ label: '템플릿 등록', href: '/notifications/templates/new' }]}
    >
      <Suspense fallback={<LoadingSpinner />}>
        <TemplateList />
      </Suspense>
    </PageLayout>
  );
}
