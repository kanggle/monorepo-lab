'use client';

import { useState } from 'react';
import Link from 'next/link';
import { Button } from '@/shared/ui/Button';
import { ApiError, messageForCode } from '@/shared/api/errors';
import { useNotificationTemplates } from '../hooks/use-ecommerce-notifications';
import {
  NOTIFICATION_DEFAULT_PAGE_SIZE,
  TEMPLATE_TYPE_LABELS,
  type NotificationTemplateList,
  type NotificationTemplateListParams,
  type TemplateType,
} from '../api/notification-types';

/**
 * ecommerce notification template operations list section (TASK-PC-FE-089 — ADR-031 Phase 5b).
 *
 * Server-rendered initial page is passed in; client re-query handles
 * pagination. Per-row action: 수정(edit). "템플릿 등록" links to /new.
 * No delete (producer defines none — § 2.4.10.4).
 *
 * Resilience (§ 2.5): 403 → inline; 503/timeout → this section degrades only.
 */

export interface NotificationsScreenProps {
  templates: NotificationTemplateList;
}

function formatType(type: string): string {
  return TEMPLATE_TYPE_LABELS[type as TemplateType] ?? type;
}

export function NotificationsScreen({ templates }: NotificationsScreenProps) {
  const [query, setQuery] = useState<NotificationTemplateListParams>({
    page: 0,
    size: templates.size || NOTIFICATION_DEFAULT_PAGE_SIZE,
  });

  const seeded = (query.page ?? 0) === 0;
  const listQ = useNotificationTemplates(query, seeded ? templates : undefined);
  const data = listQ.data ?? templates;

  const apiError =
    listQ.error instanceof ApiError ? (listQ.error as ApiError) : null;
  const forbidden = apiError?.status === 403;
  const degraded =
    listQ.isError && (!apiError || apiError.status >= 500) && !forbidden;

  const rows = data.content;
  const totalPages = Math.max(
    1,
    Math.ceil(data.totalElements / (data.size || 20)),
  );

  return (
    <section aria-labelledby="ecommerce-notifications-heading">
      <div className="mb-2 flex items-center justify-between">
        <h1
          id="ecommerce-notifications-heading"
          className="text-2xl font-semibold"
        >
          E-Commerce 알림 템플릿
        </h1>
        <Link
          href="/ecommerce/notifications/templates/new"
          data-testid="notification-new-link"
        >
          <Button>템플릿 등록</Button>
        </Link>
      </div>
      <p className="mb-6 text-sm text-muted-foreground">
        알림 템플릿 목록 · 생성 / 수정. 타입·채널은 생성 후 변경 불가. ecommerce
        알림 템플릿을 콘솔에서 운영합니다.
      </p>

      {forbidden ? (
        <div
          role="status"
          data-testid="notification-forbidden"
          className="rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          {messageForCode('FORBIDDEN')}
        </div>
      ) : degraded ? (
        <div
          role="status"
          data-testid="notification-degraded"
          className="rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          ecommerce 알림 템플릿 정보를 일시적으로 불러올 수 없습니다. 콘솔의
          다른 기능은 계속 사용할 수 있습니다.
        </div>
      ) : rows.length === 0 ? (
        <p
          className="text-sm text-muted-foreground"
          data-testid="notification-empty"
        >
          표시할 알림 템플릿이 없습니다.
        </p>
      ) : (
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
                  <td className="p-2">{t.createdAt}</td>
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
              disabled={(query.page ?? 0) <= 0}
              onClick={() =>
                setQuery((q) => ({
                  ...q,
                  page: Math.max(0, (q.page ?? 0) - 1),
                }))
              }
              data-testid="notification-prev"
            >
              이전
            </Button>
            <span
              className="text-sm text-muted-foreground"
              data-testid="notification-pageinfo"
            >
              {`${data.page + 1} / ${totalPages} 페이지 · 총 ${data.totalElements}건`}
            </span>
            <Button
              variant="secondary"
              disabled={data.page + 1 >= totalPages}
              onClick={() =>
                setQuery((q) => ({ ...q, page: (q.page ?? 0) + 1 }))
              }
              data-testid="notification-next"
            >
              다음
            </Button>
          </nav>
        </>
      )}
    </section>
  );
}
