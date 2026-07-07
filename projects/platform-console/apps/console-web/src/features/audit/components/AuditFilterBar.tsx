'use client';

import type { Dispatch, FormEvent, SetStateAction } from 'react';
import { Button } from '@/shared/ui/Button';
import { showPickerOnClick } from '@/shared/lib/show-picker';
import { messageForCode } from '@/shared/api/errors';
import {
  AUDIT_SOURCES,
  isSecuritySource,
  type AuditSource,
} from '../api/types';
import type { FilterState } from './use-audit-screen';

/**
 * `AuditScreen` filter bar (TASK-PC-FE-210 split): the 계정 ID / 액션 코드 /
 * 소스 / 시작·종료 시각 / (SUPER_ADMIN) 테넌트 filter form. Presentational — the
 * filter state, the field ids, the submit/reset handlers and the known-denied
 * capability hint all live in `useAuditScreen` and arrive via props.
 *
 * Intersection-permission UX preserved verbatim: when `securityKnownDenied`,
 * the security source options (`login_history` / `suspicious`) are PRE-DISABLED
 * with a " — 권한 없음" suffix + the `SECURITY_EVENT_READ_REQUIRED` hint; when
 * unknown they stay enabled (a server 403 is handled defensively upstream).
 * Every `data-testid` / aria / class / option order / copy is byte-identical.
 */

const SOURCE_LABEL: Record<AuditSource, string> = {
  admin: '관리자 작업 (admin)',
  login_history: '로그인 이력 (login_history)',
  suspicious: '의심 활동 (suspicious)',
};

export interface AuditFilterBarProps {
  accountFid: string;
  actionFid: string;
  fromFid: string;
  toFid: string;
  sourceFid: string;
  tenantFid: string;
  filters: FilterState;
  setFilters: Dispatch<SetStateAction<FilterState>>;
  submitFilters: (e: FormEvent) => void;
  resetFilters: () => void;
  securityKnownDenied: boolean;
  superAdminTenants: string[];
}

export function AuditFilterBar({
  accountFid,
  actionFid,
  fromFid,
  toFid,
  sourceFid,
  tenantFid,
  filters,
  setFilters,
  submitFilters,
  resetFilters,
  securityKnownDenied,
  superAdminTenants,
}: AuditFilterBarProps) {
  return (
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
          onClick={showPickerOnClick}
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
          onClick={showPickerOnClick}
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
  );
}
