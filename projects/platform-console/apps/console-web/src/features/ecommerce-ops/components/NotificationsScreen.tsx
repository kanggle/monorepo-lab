'use client';

import { useState } from 'react';
import Link from 'next/link';
import { Button } from '@/shared/ui/Button';
import { ApiError, messageForCode } from '@/shared/api/errors';
import { useNotificationTemplates } from '../hooks/use-ecommerce-notifications';
import {
  NOTIFICATION_DEFAULT_PAGE_SIZE,
  type NotificationTemplateList,
  type NotificationTemplateListParams,
} from '../api/notification-types';
import { NotificationsTable } from './NotificationsTable';

/**
 * ecommerce notification template operations list section (TASK-PC-FE-089 — ADR-031 Phase 5b).
 *
 * Server-rendered initial page is passed in; client re-query handles
 * pagination. Per-row action: 수정(edit). "템플릿 등록" links to /new.
 * No delete (producer defines none — § 2.4.10.4).
 *
 * Resilience (§ 2.5): 403 → inline; 503/timeout → this section degrades only.
 *
 * TASK-PC-FE-200: the list table + pagination are extracted into
 * {@link NotificationsTable} (presentational); this container keeps ALL state —
 * pagination query, seed fallback, and list-state branching.
 */

export interface NotificationsScreenProps {
  templates: NotificationTemplateList;
}

export function NotificationsScreen({ templates }: NotificationsScreenProps) {
  const [query, setQuery] = useState<NotificationTemplateListParams>({
    page: 0,
    size: templates.size || NOTIFICATION_DEFAULT_PAGE_SIZE,
  });

  const seeded = (query.page ?? 0) === 0;
  const listQ = useNotificationTemplates(query, seeded ? templates : undefined);
  // Only the seeded (page 0) query may fall back to the server-rendered `templates`
  // seed. For a paginated query, falling back to the seed would flash the first
  // page while the next page is still in flight — instead we render a loading
  // placeholder until the real result lands.
  const data = seeded ? listQ.data ?? templates : listQ.data;
  const loading = data === undefined;

  const apiError =
    listQ.error instanceof ApiError ? (listQ.error as ApiError) : null;
  const forbidden = apiError?.status === 403;
  const degraded =
    listQ.isError && (!apiError || apiError.status >= 500) && !forbidden;

  const rows = data?.content ?? [];
  const totalPages = data
    ? Math.max(1, Math.ceil(data.totalElements / (data.size || 20)))
    : 1;

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
        알림 템플릿 목록 · 생성 / 수정. 타입·채널은 생성 후 변경 불가.
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
      ) : loading ? (
        <p
          className="text-sm text-muted-foreground"
          data-testid="notification-loading"
        >
          조회 중…
        </p>
      ) : rows.length === 0 ? (
        <p
          className="text-sm text-muted-foreground"
          data-testid="notification-empty"
        >
          표시할 알림 템플릿이 없습니다.
        </p>
      ) : (
        <NotificationsTable
          rows={rows}
          pagination={{
            prevDisabled: (query.page ?? 0) <= 0,
            nextDisabled: (data?.page ?? 0) + 1 >= totalPages,
            pageInfo: `${(data?.page ?? 0) + 1} / ${totalPages} 페이지 · 총 ${data?.totalElements ?? 0}건`,
            onPrev: () =>
              setQuery((q) => ({
                ...q,
                page: Math.max(0, (q.page ?? 0) - 1),
              })),
            onNext: () =>
              setQuery((q) => ({ ...q, page: (q.page ?? 0) + 1 })),
          }}
        />
      )}
    </section>
  );
}
