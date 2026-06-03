'use client';

import { useId, useMemo, useState } from 'react';
import { Button } from '@/shared/ui/Button';
import { ApiError, messageForCode } from '@/shared/api/errors';
import { useAuditQuery } from '../hooks/use-audit';
import {
  AUDIT_SOURCES,
  AUDIT_DEFAULT_PAGE_SIZE,
  isSecuritySource,
  type AuditPage,
  type AuditQueryParams,
  type AuditSource,
} from '../api/types';
import { AuditRowCells, auditRowKey } from './AuditRowCells';

/**
 * GAP unified audit + security read surface (TASK-PC-FE-003 — Phase 2
 * slice 2). READ-ONLY: there is NO mutation here — no reason capture, no
 * Idempotency-Key, no destructive/confirm dialog (those are FE-002
 * concerns; carrying them over would be a defect).
 *
 * Server-rendered initial page is passed in; client re-query handles
 * filter / source / pagination changes (one call per distinct query — the
 * producer meta-audits every query, so there is no auto-refetch loop).
 *
 * Intersection-permission UX (console-integration-contract § 2.4.2):
 *   - `source=login_history|suspicious` additionally requires
 *     `security.event.read`. When the operator's capability is *known*
 *     client-side (`securityEventReadGranted === false`) the security
 *     source options are PRE-DISABLED with an explanation (never error on
 *     click). When it is *unknown* (the default — claims are not currently
 *     exposed client-side; the operator token is HttpOnly) the options stay
 *     enabled and a server `403 PERMISSION_DENIED` is ALWAYS handled
 *     defensively inline (no crash). The producer is the final authority.
 *
 * Tenant-scope UX: a non-SUPER_ADMIN operator gets NO free-text tenant
 * override (only the standard tenant selector in the console shell). A
 * SUPER_ADMIN may pass an explicit cross-tenant `tenantId` via the
 * (optional) selector; a foreign tenant for a non-super operator →
 * `403 TENANT_SCOPE_DENIED` → inline, no crash.
 *
 * Resilience (§ 2.5): 401 handled by the server route (re-login); 403/422
 * → inline actionable; 503/timeout → this section degrades only (the
 * console shell stays intact).
 */

export interface AuditScreenProps {
  initial: AuditPage;
  /**
   * Operator capability hint, when derivable client-side. `false` ⇒
   * pre-disable the security source options with an explanation; `true`
   * ⇒ enabled; `undefined` (default) ⇒ unknown → enabled + always
   * defensively handle the server 403 (claims are not currently exposed
   * client-side — the operator token is HttpOnly).
   */
  securityEventReadGranted?: boolean;
  /** SUPER_ADMIN cross-tenant selector options (omit / empty ⇒ the
   *  no-free-text-override standard behaviour for non-super operators). */
  superAdminTenants?: string[];
}

const SOURCE_LABEL: Record<AuditSource, string> = {
  admin: '관리자 작업 (admin)',
  login_history: '로그인 이력 (login_history)',
  suspicious: '의심 활동 (suspicious)',
};

interface FilterState {
  accountId: string;
  actionCode: string;
  from: string;
  to: string;
  source: '' | AuditSource;
  tenantId: string;
}

const EMPTY_FILTERS: FilterState = {
  accountId: '',
  actionCode: '',
  from: '',
  to: '',
  source: '',
  tenantId: '',
};

export function AuditScreen({
  initial,
  securityEventReadGranted,
  superAdminTenants = [],
}: AuditScreenProps) {
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

  return (
    <section aria-labelledby="audit-heading">
      <h1 id="audit-heading" className="mb-2 text-2xl font-semibold">
        감사 · 보안 조회
      </h1>
      <p className="mb-6 text-sm text-muted-foreground">
        관리자 작업 · 로그인 이력 · 의심 활동 통합 조회 (읽기 전용). 이
        조회는 감사 기록에 남습니다.
      </p>

      <form
        onSubmit={submitFilters}
        className="mb-6 grid gap-3 sm:grid-cols-2 lg:grid-cols-3"
        role="search"
        aria-label="감사 로그 필터"
      >
        <div>
          <label
            htmlFor={accountFid}
            className="block text-sm font-medium text-foreground"
          >
            계정 ID
          </label>
          <input
            id={accountFid}
            type="text"
            value={filters.accountId}
            onChange={(e) =>
              setFilters((f) => ({ ...f, accountId: e.target.value }))
            }
            data-testid="audit-filter-accountId"
            className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          />
        </div>

        <div>
          <label
            htmlFor={actionFid}
            className="block text-sm font-medium text-foreground"
          >
            액션 코드
          </label>
          <input
            id={actionFid}
            type="text"
            value={filters.actionCode}
            onChange={(e) =>
              setFilters((f) => ({ ...f, actionCode: e.target.value }))
            }
            placeholder="예: ACCOUNT_LOCK"
            data-testid="audit-filter-actionCode"
            className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          />
        </div>

        <div>
          <label
            htmlFor={sourceFid}
            className="block text-sm font-medium text-foreground"
          >
            소스
          </label>
          <select
            id={sourceFid}
            value={filters.source}
            onChange={(e) =>
              setFilters((f) => ({
                ...f,
                source: e.target.value as FilterState['source'],
              }))
            }
            data-testid="audit-filter-source"
            className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          >
            <option value="">전체 (admin)</option>
            {AUDIT_SOURCES.map((s) => {
              const securityLocked =
                securityKnownDenied && isSecuritySource(s);
              return (
                <option
                  key={s}
                  value={s}
                  disabled={securityLocked}
                  data-testid={`audit-source-option-${s}`}
                >
                  {SOURCE_LABEL[s]}
                  {securityLocked ? ' — 권한 없음' : ''}
                </option>
              );
            })}
          </select>
          {securityKnownDenied && (
            <p
              className="mt-1 text-xs text-muted-foreground"
              data-testid="audit-security-locked-hint"
            >
              {messageForCode('SECURITY_EVENT_READ_REQUIRED')}
            </p>
          )}
        </div>

        <div>
          <label
            htmlFor={fromFid}
            className="block text-sm font-medium text-foreground"
          >
            시작 시각
          </label>
          <input
            id={fromFid}
            type="datetime-local"
            value={filters.from}
            onChange={(e) =>
              setFilters((f) => ({ ...f, from: e.target.value }))
            }
            data-testid="audit-filter-from"
            className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          />
        </div>

        <div>
          <label
            htmlFor={toFid}
            className="block text-sm font-medium text-foreground"
          >
            종료 시각
          </label>
          <input
            id={toFid}
            type="datetime-local"
            value={filters.to}
            onChange={(e) =>
              setFilters((f) => ({ ...f, to: e.target.value }))
            }
            data-testid="audit-filter-to"
            className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          />
        </div>

        {superAdminTenants.length > 0 && (
          <div>
            <label
              htmlFor={tenantFid}
              className="block text-sm font-medium text-foreground"
            >
              테넌트 (SUPER_ADMIN 교차 조회)
            </label>
            <select
              id={tenantFid}
              value={filters.tenantId}
              onChange={(e) =>
                setFilters((f) => ({ ...f, tenantId: e.target.value }))
              }
              data-testid="audit-filter-tenantId"
              className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
            >
              <option value="">활성 테넌트</option>
              {superAdminTenants.map((t) => (
                <option key={t} value={t}>
                  {t}
                </option>
              ))}
            </select>
          </div>
        )}

        <div className="flex items-end gap-3">
          <Button type="submit" data-testid="audit-filter-submit">
            조회
          </Button>
          <Button
            type="button"
            variant="secondary"
            onClick={resetFilters}
            data-testid="audit-filter-reset"
          >
            초기화
          </Button>
        </div>
      </form>

      {rangeError && (
        <p
          role="alert"
          data-testid="audit-range-error"
          className="mb-6 rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive"
        >
          {rangeError}
        </p>
      )}

      {(permissionDenied || validationDenied) && permissionMessage && (
        <div
          role="status"
          data-testid="audit-permission-denied"
          className="mb-6 rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          {permissionMessage}
        </div>
      )}

      {degraded && (
        <div
          role="status"
          data-testid="audit-degraded"
          className="mb-6 rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          감사 서비스를 일시적으로 불러올 수 없습니다. 콘솔의 다른 기능은
          계속 사용할 수 있습니다. 잠시 후 다시 시도하세요.
        </div>
      )}

      {!permissionDenied && !validationDenied && !degraded && (
        <>
          {rows.length === 0 ? (
            <p
              className="text-sm text-muted-foreground"
              data-testid="audit-empty"
            >
              표시할 감사 기록이 없습니다. (조회 결과 없음 또는 조회 권한
              없음)
            </p>
          ) : (
            <>
              <table
                className="data-table"
                data-testid="audit-table"
              >
                <caption className="sr-only">감사 로그 통합 조회 결과</caption>
                <thead>
                  <tr className="border-b border-border text-left">
                    <th scope="col" className="p-2">
                      소스
                    </th>
                    <th scope="col" className="p-2">
                      액션 / 이벤트
                    </th>
                    <th scope="col" className="p-2">
                      행위자 / 계정
                    </th>
                    <th scope="col" className="p-2">
                      대상 / 위치
                    </th>
                    <th scope="col" className="p-2">
                      결과
                    </th>
                    <th scope="col" className="p-2">
                      발생 시각 (UTC)
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((row, i) => {
                    const key = auditRowKey(row, i);
                    return (
                      <tr
                        key={key}
                        data-testid={`audit-row-${i}`}
                        data-source={(row as { source: string }).source}
                        className="border-b border-border"
                      >
                        <AuditRowCells row={row} />
                      </tr>
                    );
                  })}
                </tbody>
              </table>

              <nav
                className="mt-4 flex items-center justify-between"
                aria-label="페이지 이동"
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
                  data-testid="audit-prev"
                >
                  이전
                </Button>
                <span
                  className="text-sm text-muted-foreground"
                  data-testid="audit-pageinfo"
                >
                  {data
                    ? `${data.page + 1} / ${totalPages} 페이지 · 총 ${data.totalElements}건`
                    : '—'}
                </span>
                <Button
                  variant="secondary"
                  disabled={!data || data.page + 1 >= data.totalPages}
                  onClick={() =>
                    setQuery((q) => ({ ...q, page: (q.page ?? 0) + 1 }))
                  }
                  data-testid="audit-next"
                >
                  다음
                </Button>
              </nav>
            </>
          )}
        </>
      )}
    </section>
  );
}
