'use client';

import { useId, useMemo, useState } from 'react';
import { ApiError, messageForCode } from '@/shared/api/errors';
import { useAuditQuery } from './use-audit';
import {
  AUDIT_DEFAULT_PAGE_SIZE,
  isSecuritySource,
  type AuditPage,
  type AuditQueryParams,
  type AuditSource,
} from '../api/types';

/**
 * `AuditScreen` container logic (TASK-PC-FE-149 — behavior-preserving
 * container/presentational split). Owns the filter / source / pagination
 * state, the seeded SSR fallback, the `useAuditQuery` call and every
 * permission / degrade derivation. The screen stays read-only (no
 * mutation) — see `AuditScreen.tsx` for the full contract narrative.
 *
 * State ownership lives here (the parent): the table is a controlled
 * presentational component that only receives derived rows / pagination
 * + the page-change handlers.
 */

export interface FilterState {
  accountId: string;
  actionCode: string;
  from: string;
  to: string;
  source: '' | AuditSource;
  tenantId: string;
}

export const EMPTY_FILTERS: FilterState = {
  accountId: '',
  actionCode: '',
  from: '',
  to: '',
  source: '',
  tenantId: '',
};

export interface UseAuditScreenArgs {
  initial: AuditPage;
  securityEventReadGranted?: boolean;
}

export function useAuditScreen({
  initial,
  securityEventReadGranted,
}: UseAuditScreenArgs) {
  const accountFid = useId();
  const actionFid = useId();
  const fromFid = useId();
  const toFid = useId();
  const sourceFid = useId();
  const tenantFid = useId();

  const [filters, setFilters] = useState<FilterState>(EMPTY_FILTERS);
  const [query, setQuery] = useState<AuditQueryParams>({
    page: initial.page,
    size: initial.size,
  });
  const [rangeError, setRangeError] = useState<string | null>(null);

  const securityKnownDenied = securityEventReadGranted === false;

  const seeded =
    (query.page ?? 0) === initial.page &&
    !query.accountId &&
    !query.actionCode &&
    !query.from &&
    !query.to &&
    !query.source &&
    !query.tenantId;

  const audit = useAuditQuery(query, seeded ? initial : undefined);
  const data = audit.data;

  // 403 (permission / tenant-scope) and 422 surface as ApiError on the
  // client query — render inline, never crash, never a re-login loop.
  const apiError =
    audit.error instanceof ApiError ? (audit.error as ApiError) : null;
  const permissionDenied = apiError?.status === 403;
  const validationDenied = apiError?.status === 422;
  // A degrade signal: the client query failed with a non-ApiError (the
  // proxy mapped 503/timeout → 503 → ApiError 503; treat ≥500 as degrade).
  const degraded =
    audit.isError &&
    (!apiError || apiError.status >= 500) &&
    !permissionDenied &&
    !validationDenied;

  const permissionMessage = useMemo(() => {
    if (!apiError) return null;
    if (apiError.code === 'TENANT_SCOPE_DENIED') {
      return messageForCode('TENANT_SCOPE_DENIED');
    }
    if (apiError.status === 403) {
      // The intersection-permission rule: a security source without
      // security.event.read 403s — give the specific explanation.
      if (isSecuritySource(query.source)) {
        return messageForCode('SECURITY_EVENT_READ_REQUIRED');
      }
      return messageForCode('PERMISSION_DENIED', apiError.message);
    }
    if (apiError.status === 422) {
      return messageForCode(apiError.code, apiError.message);
    }
    return null;
  }, [apiError, query.source]);

  function submitFilters(e: React.FormEvent) {
    e.preventDefault();
    setRangeError(null);
    const from = filters.from.trim();
    const to = filters.to.trim();
    // Client guard — pre-empt the producer 422 (from > to). ISO-8601 from
    // <input type="datetime-local"> compares lexicographically.
    if (from && to && from > to) {
      setRangeError(messageForCode('AUDIT_RANGE_INVALID'));
      return;
    }
    const source =
      filters.source === '' ? undefined : (filters.source as AuditSource);
    setQuery({
      accountId: filters.accountId.trim() || undefined,
      actionCode: filters.actionCode.trim() || undefined,
      from: from || undefined,
      to: to || undefined,
      source,
      tenantId: filters.tenantId.trim() || undefined,
      page: 0,
      size: initial.size || AUDIT_DEFAULT_PAGE_SIZE,
    });
  }

  function resetFilters() {
    setFilters(EMPTY_FILTERS);
    setRangeError(null);
    setQuery({ page: 0, size: initial.size || AUDIT_DEFAULT_PAGE_SIZE });
  }

  const rows = data?.content ?? [];
  const totalPages = data ? Math.max(1, data.totalPages) : 1;

  const page = query.page ?? 0;
  const prevDisabled = page <= 0;
  const nextDisabled = !data || data.page + 1 >= data.totalPages;

  function goPrev() {
    setQuery((q) => ({ ...q, page: Math.max(0, (q.page ?? 0) - 1) }));
  }

  function goNext() {
    setQuery((q) => ({ ...q, page: (q.page ?? 0) + 1 }));
  }

  return {
    // field ids
    accountFid,
    actionFid,
    fromFid,
    toFid,
    sourceFid,
    tenantFid,
    // filter form state
    filters,
    setFilters,
    submitFilters,
    resetFilters,
    rangeError,
    // permission UX
    securityKnownDenied,
    // query result derivations
    data,
    rows,
    totalPages,
    permissionDenied,
    validationDenied,
    degraded,
    permissionMessage,
    // pagination
    prevDisabled,
    nextDisabled,
    goPrev,
    goNext,
  };
}
