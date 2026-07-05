'use client';

import Link from 'next/link';
import { Button } from '@/shared/ui/Button';
import { formatDateTime } from '@/shared/lib/datetime';
import {
  TEMPLATE_TYPE_LABELS,
  type NotificationTemplateList,
  type TemplateType,
} from '../api/notification-types';

interface NotificationsTableProps {
  rows: NotificationTemplateList['content'];
  pagination: {
    prevDisabled: boolean;
    nextDisabled: boolean;
    pageInfo: string;
    onPrev: () => void;
    onNext: () => void;
  };
}

function formatType(type: string): string {
  return TEMPLATE_TYPE_LABELS[type as TemplateType] ?? type;
}

/**
 * Notification template list table + pagination (TASK-PC-FE-200 — extracted
 * from {@link NotificationsScreen}, presentational only). Per-row action:
 * 수정(edit). No delete (producer defines none — § 2.4.10.4). Query/pagination
 * state stays owned by `NotificationsScreen`; all `data-testid`s are unchanged.
 */
export function NotificationsTable({
  rows,
  pagination,
}: NotificationsTableProps) {
  return (
    <>
      <table
        className="mb-3 data-table"
        data-testid="notification-table"
      >
        <caption className="sr-only">알림 템플릿 목록</caption>
        <thead>
          <tr className="border-b border-border text-left">
            <th scope="col" className="p-2">
              유형
            </th>
            <th scope="col" className="p-2">
              채널
            </th>
            <th scope="col" className="p-2">
              제목
            </th>
            <th scope="col" className="p-2">
              등록일
            </th>
            <th scope="col" className="p-2">
              수정일
            </th>
            <th scope="col" className="p-2">
              작업
            </th>
          </tr>
        </thead>
        <tbody>
          {rows.map((t, i) => (
            <tr
              key={t.templateId}
              data-testid={`notification-row-${i}`}
              className="border-b border-border"
            >
              <td className="p-2">{formatType(t.type)}</td>
              <td className="p-2">{t.channel}</td>
              <td className="p-2">{t.subject}</td>
              <td className="p-2">{formatDateTime(t.createdAt)}</td>
              <td className="p-2">{formatDateTime(t.updatedAt)}</td>
              <td className="p-2">
                <Link
                  href={`/ecommerce/notifications/templates/${t.templateId}/edit`}
                >
                  <Button
                    variant="secondary"
                    size="sm"
                    data-testid={`notification-edit-${i}`}
                  >
                    수정
                  </Button>
                </Link>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <nav
        className="flex items-center justify-between"
        aria-label="알림 템플릿 페이지 이동"
      >
        <Button
          variant="secondary"
          disabled={pagination.prevDisabled}
          onClick={pagination.onPrev}
          data-testid="notification-prev"
        >
          이전
        </Button>
        <span
          className="text-sm text-muted-foreground"
          data-testid="notification-pageinfo"
        >
          {pagination.pageInfo}
        </span>
        <Button
          variant="secondary"
          disabled={pagination.nextDisabled}
          onClick={pagination.onNext}
          data-testid="notification-next"
        >
          다음
        </Button>
      </nav>
    </>
  );
}
