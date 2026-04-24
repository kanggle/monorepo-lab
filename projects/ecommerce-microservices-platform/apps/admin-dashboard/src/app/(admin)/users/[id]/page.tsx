'use client';

import { use } from 'react';
import { UserDetail } from '@/features/user-management';

interface Props {
  params: Promise<{ id: string }>;
}

export default function UserDetailPage({ params }: Props) {
  const { id } = use(params);
  return <UserDetail userId={id} />;
}
