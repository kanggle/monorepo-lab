'use client';

import { useRouter } from 'next/navigation';
import { DataTable, ListError } from '@/shared/ui';
import type { ColumnDef } from '@/shared/ui';
import { useTemplates } from '../hooks/use-templates';
import type {
  NotificationTemplateSummary,
  NotificationTemplateType,
  NotificationChannel,
} from '@repo/types';

const TYPE_LABELS: Record<NotificationTemplateType, string> = {
  ORDER_PLACED: '주문 완료',
  PAYMENT_COMPLETED: '결제 완료',
  SHIPPING_STATUS_CHANGED: '배송 상태 변경',
  WELCOME: '회원 가입',
};

const CHANNEL_LABELS: Record<NotificationChannel, string> = {
  EMAIL: '이메일',
  SMS: 'SMS',
  PUSH: '푸시',
};

const columns: ColumnDef<NotificationTemplateSummary>[] = [
  {
    key: 'type',
    header: '유형',
    sortable: true,
    render: (item: NotificationTemplateSummary) =>
      TYPE_LABELS[item.type] ?? item.type,
  },
  {
    key: 'channel',
    header: '채널',
    sortable: true,
    render: (item: NotificationTemplateSummary) =>
      CHANNEL_LABELS[item.channel] ?? item.channel,
  },
  { key: 'subject', header: '제목', sortable: true },
  {
    key: 'createdAt',
    header: '생성일',
    sortable: true,
    render: (item: NotificationTemplateSummary) =>
      new Date(item.createdAt).toLocaleDateString('ko-KR'),
  },
];

export function TemplateList() {
  const router = useRouter();
  const { data, isLoading, isError, refetch, pagination } = useTemplates();

  if (isError) {
    return (
      <ListError
        message="알림 템플릿 목록을 불러오는데 실패했습니다."
        onRetry={() => refetch()}
      />
    );
  }

  return (
    <DataTable<NotificationTemplateSummary>
      columns={columns}
      data={data?.content ?? []}
      pagination={pagination}
      isLoading={isLoading}
      emptyMessage="등록된 알림 템플릿이 없습니다."
      onRowClick={(item) =>
        router.push(`/notifications/templates/${item.templateId}/edit`)
      }
      rowKey={(item) => item.templateId}
    />
  );
}
