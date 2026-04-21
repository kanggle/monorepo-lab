'use client';

import { Suspense } from 'react';
import { useSearchParams } from 'next/navigation';
import { NotificationList, NotificationDetail } from '@/features/notification';

function NotificationsContent() {
  const searchParams = useSearchParams();
  const notificationId = searchParams.get('id');

  if (notificationId) {
    return <NotificationDetail notificationId={notificationId} />;
  }

  return <NotificationList />;
}

export default function NotificationsPage() {
  return (
    <Suspense fallback={null}>
      <NotificationsContent />
    </Suspense>
  );
}
