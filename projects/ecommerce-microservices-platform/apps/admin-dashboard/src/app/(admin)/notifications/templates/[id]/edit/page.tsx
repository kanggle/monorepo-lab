'use client';

import { use } from 'react';
import { EditTemplate } from '@/features/notification-management';

interface Props {
  params: Promise<{ id: string }>;
}

export default function EditTemplatePage({ params }: Props) {
  const { id } = use(params);
  return <EditTemplate templateId={id} />;
}
