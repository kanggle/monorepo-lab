'use client';

import { useState } from 'react';
import { z } from 'zod';
import { Button } from '@/shared/ui/Button';
import { apiClient } from '@/shared/api/client';
import { ApiError } from '@/shared/api/errors';
import {
  AccountPageSchema,
  AccountSummarySchema,
} from '@/shared/api/iam-accounts-types';

/**
 * `<AccountEmailLookup>` — TASK-PC-FE-334, an ASSIST for the audit filter and
 * artist-registration screens' existing 계정 ID input (that input is NOT
 * removed — this is a secondary path for operators who know the email, not
 * the id). Queries the same-origin `GET /api/accounts?email=` proxy (never
 * IAM directly, architecture.md § Forbidden Dependencies) — an exact-match
 * lookup scoped to the active tenant unless the caller overrides it.
 *
 * `AccountSummarySchema` (shared/api/iam-accounts-types.ts) intentionally
 * carries no `tenantId` field (its only other two consumers — the accounts
 * screen's own list and the composed overview — never need one). The task's
 * SUPER_ADMIN-`*` edge case ("같은 이메일 계정 여러 행 → 행마다 테넌트를 같이
 * 보여주고 고르게") asks this ONE caller to show a tenant per row when the
 * producer sends one, so `LookupRowSchema` extends the shared row with an
 * OPTIONAL `tenantId` here, locally — the shared contract surface is left
 * untouched (no shared/contract change), and a row that doesn't carry the
 * field still parses (and renders) unchanged.
 */
const LookupRowSchema = AccountSummarySchema.extend({
  tenantId: z.string().optional(),
});
const LookupPageSchema = AccountPageSchema.extend({
  content: z.array(LookupRowSchema),
});
export type AccountLookupRow = z.infer<typeof LookupRowSchema>;

export interface AccountEmailLookupProps {
  /** `<label htmlFor>` 대상 입력 id. */
  id: string;
  /** 테스트 훅의 베이스 id — 하위 요소에 접미사가 붙는다. */
  testid: string;
  onPick: (accountId: string, account?: AccountLookupRow) => void;
  label?: string;
}

export function AccountEmailLookup({
  id,
  testid,
  onPick,
  label = '이메일로 계정 찾기',
}: AccountEmailLookupProps) {
  const [email, setEmail] = useState('');
  const [rows, setRows] = useState<AccountLookupRow[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function search() {
    const trimmed = email.trim();
    if (!trimmed) return;
    setLoading(true);
    setError(null);
    setRows(null);
    try {
      const raw = await apiClient.get<unknown>(
        `/api/accounts?email=${encodeURIComponent(trimmed)}`,
      );
      const page = LookupPageSchema.parse(raw);
      setRows(page.content);
    } catch (err) {
      if (err instanceof ApiError && err.status === 403) {
        setError(
          '이 테넌트의 계정을 조회할 권한이 없습니다 — 계정 ID 를 직접 입력하세요',
        );
      } else {
        setError('계정을 조회하지 못했습니다. 잠시 후 다시 시도하세요.');
      }
    } finally {
      setLoading(false);
    }
  }

  return (
    <div
      className="space-y-2 rounded-md border border-dashed border-border bg-muted/30 p-3"
      data-testid={`${testid}-section`}
    >
      <label htmlFor={id} className="block text-sm font-medium text-foreground">
        {label}
      </label>
      <div className="flex gap-2">
        <input
          id={id}
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          placeholder="name@example.com"
          data-testid={`${testid}-email-input`}
          className="flex-1 rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        />
        <Button
          type="button"
          variant="secondary"
          onClick={() => void search()}
          disabled={loading || email.trim() === ''}
          data-testid={`${testid}-search`}
        >
          {loading ? '찾는 중…' : '찾기'}
        </Button>
      </div>

      {error && (
        <p role="alert" data-testid={`${testid}-error`} className="text-sm text-destructive">
          {error}
        </p>
      )}

      {rows !== null && !error && (
        rows.length === 0 ? (
          <p data-testid={`${testid}-empty`} className="text-sm text-muted-foreground">
            현재 테넌트에 이 이메일의 계정이 없습니다
          </p>
        ) : (
          <ul
            data-testid={`${testid}-results`}
            className="divide-y divide-border rounded-md border border-border"
          >
            {rows.map((r) => (
              <li key={r.id}>
                <button
                  type="button"
                  onClick={() => onPick(r.id, r)}
                  data-testid={`${testid}-result-${r.id}`}
                  className="flex w-full items-center justify-between gap-3 px-3 py-2 text-left text-sm hover:bg-accent"
                >
                  <span className="min-w-0 truncate">
                    {r.email}
                    {r.tenantId ? ` · ${r.tenantId}` : ''}
                  </span>
                  <span className="shrink-0 text-xs text-muted-foreground">{r.status}</span>
                </button>
              </li>
            ))}
          </ul>
        )
      )}
    </div>
  );
}
