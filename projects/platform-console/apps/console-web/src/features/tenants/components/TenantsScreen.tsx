'use client';

import { useState } from 'react';
import Link from 'next/link';
import { ApiError } from '@/shared/api/errors';
import { useTenantsList } from '../hooks/use-tenants';
import type { TenantPage, TenantStatus, TenantType } from '../api/types';
import { TenantsTable } from './TenantsTable';

/**
 * IAM tenant-management list surface (TASK-PC-FE-226 — the isolation-boundary
 * CRUD screen; SUPER_ADMIN only). Server-rendered initial page is passed in;
 * client re-query handles the status/tenantType filter + pagination + post-
 * mutation invalidation (mirrors `OperatorsScreen`).
 *
 * TASK-MONO-773 S6 — the create form is no longer here: «조직 만들기» (`/onboarding`) and «테넌트 등록»
 * (this screen's form) became ONE «테넌트 생성» screen `/new-tenant` (console-integration-contract § 2.6.4;
 * the SUPER_ADMIN form lives in `TenantCreateScreen`). This screen keeps the list and links there.
 *
 * This screen is functionally UNRELATED to `features/tenant`
 * (`TenantSwitcher` — the operator's own active-tenant session switcher);
 * this is the tenant-resource CRUD surface reached from 조직 설정 ▸ 테넌트.
 */
export interface TenantsScreenProps {
  initial: TenantPage;
}

export function TenantsScreen({ initial }: TenantsScreenProps) {
  const [statusFilter, setStatusFilter] = useState<'' | TenantStatus>('');
  const [tenantTypeFilter, setTenantTypeFilter] = useState<'' | TenantType>('');
  const [query, setQuery] = useState<{
    status?: TenantStatus;
    tenantType?: TenantType;
    page: number;
    size: number;
  }>({ page: initial.page, size: initial.size });

  const seeded =
    query.page === initial.page &&
    query.status === undefined &&
    query.tenantType === undefined;
  const list = useTenantsList(query, seeded ? initial : undefined);
  const page = list.data;

  const listApiError = list.error instanceof ApiError ? (list.error as ApiError) : null;
  const isListError = list.isError;

  function submitFilter(e: React.FormEvent) {
    e.preventDefault();
    setQuery({
      status: statusFilter === '' ? undefined : statusFilter,
      tenantType: tenantTypeFilter === '' ? undefined : tenantTypeFilter,
      page: 0,
      size: initial.size,
    });
  }

  const rows = page?.items ?? [];

  return (
    <section aria-labelledby="tenants-heading">
      <h1 id="tenants-heading" className="mb-2 text-2xl font-semibold">
        테넌트 관리
      </h1>
      <p className="mb-6 text-sm text-muted-foreground">
        테넌트(격리 경계) 조회·수정. 세션의 활성 테넌트를 전환하는 화면이
        아닙니다 — 상단의 테넌트 스위처와는 별개입니다. 모든 변경 작업은
        사유를 요구하며 감사 기록에 남습니다. (SUPER_ADMIN 전용)
      </p>

      <p className="mb-6 text-sm">
        새 테넌트는{' '}
        <Link
          href="/new-tenant"
          data-testid="tenants-create-link"
          className="font-medium text-foreground underline underline-offset-2"
        >
          테넌트 생성
        </Link>{' '}
        화면에서 첫 관리자와 함께 만듭니다.
      </p>

      <TenantsTable
        statusFilter={statusFilter}
        onStatusFilterChange={setStatusFilter}
        tenantTypeFilter={tenantTypeFilter}
        onTenantTypeFilterChange={setTenantTypeFilter}
        onSubmitFilter={submitFilter}
        isListError={isListError}
        rows={rows}
        page={page}
        currentPage={query.page}
        onPrevPage={() => setQuery((q) => ({ ...q, page: Math.max(0, q.page - 1) }))}
        onNextPage={() => setQuery((q) => ({ ...q, page: q.page + 1 }))}
      />
      {listApiError && (
        <p className="sr-only" data-testid="tenants-list-error-code">
          {listApiError.code}
        </p>
      )}
    </section>
  );
}
