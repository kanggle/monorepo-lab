'use client';

import { Suspense } from 'react';
import { PageLayout } from '@/shared/ui';
import { LoadingSpinner } from '@repo/ui';
import { UserList } from '@/features/user-management';

export default function UsersPage() {
  return (
    <PageLayout title="사용자 관리">
      <Suspense fallback={<LoadingSpinner />}>
        <UserList />
      </Suspense>
    </PageLayout>
  );
}
