'use client';

import Link from 'next/link';
import { useId, useState } from 'react';
import { Button } from '@/shared/ui/Button';
import { StatusBadge } from '@/shared/ui/StatusBadge';
import { ApiError, messageForCode } from '@/shared/api/errors';
import {
  useSuggestions,
  useApproveSuggestion,
  useDismissSuggestion,
} from '../hooks/use-scm-replenishment';
import {
  REPL_DEFAULT_PAGE_SIZE,
  KNOWN_SUGGESTION_STATUSES,
  suggestionStatusTone,
  canApprove,
  canDismiss,
  type SuggestionPage,
  type Suggestion,
  type SuggestionQueryParams,
  type ApproveResult,
} from '../api/types';
import { ReplenishmentActionDialog } from './ReplenishmentActionDialog';

/**
 * scm replenishment-suggestions operator screen (TASK-PC-FE-077 — the human
 * operator gate of the ADR-MONO-027 wms→scm replenishment loop). The FIRST scm
 * operator-MUTATION surface, layered on the FE-008 scm read foundation.
 *
 * The section renders:
 *   - the suggestion list (status / skuCode filters + pagination) — each row
 *     shows the `triggerAvailableQty` that explains WHY it was suggested;
 *   - confirm-gated approve / dismiss actions with an optional note/reason in
 *     the request BODY (NO Idempotency-Key, NO X-Operator-Reason);
 *   - on approve success, the materialised DRAFT `poId` / `poStatus` + an
 *     explicit "PO created as DRAFT — submit it in Procurement" affordance
 *     linking to the FE-008 scm-ops PO surface (this screen NEVER submits).
 *
 * Idempotency (§ 2.4.6.1 / task Edge Case): re-approving (or approving a
 * `MATERIALIZED` suggestion) returns the existing `poId` — handled as success
 * (no duplicate-PO assumption, no error toast). A hard `409
 * SUGGESTION_ALREADY_MATERIALIZED` is a benign "already materialized" notice.
 * `SKU_SUPPLIER_UNMAPPED` (422) → inline; the suggestion stays `SUGGESTED`.
 * `INVALID_SUGGESTION_STATE` (422) → inline (the button is also state-disabled).
 *
 * Resilience (§ 2.5): 401 is handled by the server route (whole-session
 * re-login); 403/404/422/409 → inline actionable; 503/timeout → this section
 * degrades only (the console shell + the FE-008 scm read section stay intact).
 */

export interface ReplenishmentScreenProps {
  suggestions: SuggestionPage;
}

interface FilterState {
  status: string;
  skuCode: string;
}

const EMPTY_FILTERS: FilterState = { status: '', skuCode: '' };

type ActionKind = 'approve' | 'dismiss';

const ACTION_COPY: Record<
  ActionKind,
  { title: string; description: string; confirmLabel: string; noteLabel: string }
> = {
  approve: {
    title: '보충 발주를 승인할까요?',
    description:
      '공급사 매핑(sku_supplier_map)을 해석해 조달(Procurement)에 DRAFT 발주(PO)를 생성합니다. 생성된 PO 는 DRAFT 상태로만 만들어지며, 제출(SUBMIT)은 조달에서 별도로 진행합니다. 같은 추천을 다시 승인해도 기존 PO 가 반환됩니다(멱등).',
    confirmLabel: '승인 (DRAFT PO 생성)',
    noteLabel: '메모 (선택)',
  },
  dismiss: {
    title: '이 추천을 기각할까요?',
    description:
      '추천을 DISMISSED 상태로 변경하고 미해결 추천 가드를 해제합니다. 같은 추천을 다시 기각해도 변화가 없습니다(멱등).',
    confirmLabel: '기각',
    noteLabel: '사유 (선택)',
  },
};

export function ReplenishmentScreen({ suggestions }: ReplenishmentScreenProps) {
  const statusFid = useId();
  const skuFid = useId();

  // ── suggestion list (filters + pagination) ──────────────────────────
  const [filters, setFilters] = useState<FilterState>(EMPTY_FILTERS);
  const [query, setQuery] = useState<SuggestionQueryParams>({
    page: 0,
    size: suggestions.size || REPL_DEFAULT_PAGE_SIZE,
  });

  const seeded =
    (query.page ?? 0) === 0 && !query.status && !query.skuCode;

  const listQ = useSuggestions(query, seeded ? suggestions : undefined);
  const data = listQ.data ?? suggestions;

  const listApiError =
    listQ.error instanceof ApiError ? (listQ.error as ApiError) : null;
  const listForbidden = listApiError?.status === 403;
  const listRateLimited = listApiError?.code === 'RATE_LIMIT_EXCEEDED';
  const listDegraded = listQ.isError && !listForbidden && !listRateLimited;

  function submitFilters(e: React.FormEvent) {
    e.preventDefault();
    setQuery({
      status: filters.status.trim() || undefined,
      skuCode: filters.skuCode.trim() || undefined,
      page: 0,
      size: suggestions.size || REPL_DEFAULT_PAGE_SIZE,
    });
  }

  const rows = data.content;
  const totalPages = Math.max(1, data.totalPages ?? 1);

  // ── action dialog (approve / dismiss) ───────────────────────────────
  const approve = useApproveSuggestion();
  const dismiss = useDismissSuggestion();
  const [action, setAction] = useState<{
    kind: ActionKind;
    suggestion: Suggestion;
  } | null>(null);
  const [note, setNote] = useState('');
  const [actionError, setActionError] = useState<string | null>(null);

  // The materialised DRAFT PO surfaced after a successful approve (incl. the
  // idempotent re-approve path returning the existing poId).
  const [approved, setApproved] = useState<ApproveResult | null>(null);

  const activeMutation = action?.kind === 'approve' ? approve : dismiss;
  const actionPending = approve.isPending || dismiss.isPending;

  function openAction(kind: ActionKind, suggestion: Suggestion) {
    setActionError(null);
    setNote('');
    setAction({ kind, suggestion });
  }

  function confirmAction() {
    if (!action) return;
    const { kind, suggestion } = action;
    if (kind === 'approve') {
      approve.mutate(
        { id: suggestion.id, note },
        {
          onSuccess: (result) => {
            setAction(null);
            setActionError(null);
            // Surface the materialised DRAFT poId/poStatus (also the idempotent
            // re-approve path — the existing poId, no duplicate-PO assumption).
            setApproved(result);
          },
          onError: (e) => handleActionError(e),
        },
      );
    } else {
      dismiss.mutate(
        { id: suggestion.id, reason: note },
        {
          onSuccess: () => {
            setAction(null);
            setActionError(null);
          },
          onError: (e) => handleActionError(e),
        },
      );
    }
  }

  function handleActionError(e: unknown) {
    const code = e instanceof ApiError ? e.code : 'SERVICE_UNAVAILABLE';
    setActionError(messageForCode(code, '작업을 처리하지 못했습니다.'));
  }

  return (
    <section aria-labelledby="repl-heading">
      <h1 id="repl-heading" className="mb-2 text-2xl font-semibold">
        SCM 보충 운영 (재고보충 추천)
      </h1>
      <p className="mb-6 text-sm text-muted-foreground">
        wms 저재고 알림이 만든 보충 추천을 검토하고 승인(→ DRAFT 발주 생성)
        또는 기각합니다. 승인 시 생성되는 발주는 <strong>DRAFT</strong> 상태로만
        만들어지며, 제출(SUBMIT)은 조달(Procurement)에서 별도로 진행합니다.
      </p>

      {/* ── filters ────────────────────────────────────────────────────── */}
      <form
        onSubmit={submitFilters}
        className="mb-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-4"
        role="search"
        aria-label="보충 추천 필터"
      >
        <div>
          <label
            htmlFor={statusFid}
            className="block text-sm font-medium text-foreground"
          >
            상태
          </label>
          <select
            id={statusFid}
            value={filters.status}
            onChange={(e) =>
              setFilters((f) => ({ ...f, status: e.target.value }))
            }
            data-testid="repl-filter-status"
            className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          >
            <option value="">전체</option>
            {KNOWN_SUGGESTION_STATUSES.map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label
            htmlFor={skuFid}
            className="block text-sm font-medium text-foreground"
          >
            SKU 코드
          </label>
          <input
            id={skuFid}
            type="text"
            value={filters.skuCode}
            onChange={(e) =>
              setFilters((f) => ({ ...f, skuCode: e.target.value }))
            }
            data-testid="repl-filter-sku"
            className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          />
        </div>
        <div className="flex items-end">
          <Button type="submit" data-testid="repl-filter-submit">
            조회
          </Button>
        </div>
      </form>

      {/* ── DRAFT PO success affordance (approve) ───────────────────────── */}
      {approved && (
        <div
          role="status"
          data-testid="repl-approved-draft"
          className="mb-6 rounded-md border-2 border-emerald-500 bg-emerald-50 px-4 py-3 text-sm text-emerald-900 dark:border-emerald-600 dark:bg-emerald-950/50 dark:text-emerald-100"
        >
          <p className="font-medium">
            보충 추천이 승인되어 <strong>DRAFT</strong> 발주가 생성되었습니다.
          </p>
          <p className="mt-1">
            PO:{' '}
            <span data-testid="repl-approved-poid" className="font-mono">
              {approved.poId ?? '—'}
            </span>{' '}
            · 상태:{' '}
            <span data-testid="repl-approved-postatus" className="font-medium">
              {approved.poStatus ?? '—'}
            </span>
          </p>
          <p className="mt-2">
            이 발주는 <strong>DRAFT</strong> 상태입니다 — 제출(SUBMIT)은 이
            화면이 아니라 <strong>조달(Procurement)</strong> 에서 별도로
            진행해야 합니다.{' '}
            <Link
              href="/scm"
              data-testid="repl-procurement-link"
              className="underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
            >
              조달(발주) 화면으로 이동
            </Link>
          </p>
          <Button
            variant="ghost"
            size="sm"
            onClick={() => setApproved(null)}
            data-testid="repl-approved-dismiss"
            className="mt-2"
          >
            닫기
          </Button>
        </div>
      )}

      {/* ── list / states ───────────────────────────────────────────────── */}
      {listForbidden ? (
        <div
          role="status"
          data-testid="repl-forbidden"
          className="rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          {messageForCode('TENANT_FORBIDDEN')}
        </div>
      ) : listRateLimited ? (
        <div
          role="status"
          data-testid="repl-ratelimited"
          className="rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          {messageForCode('RATE_LIMIT_EXCEEDED')}
        </div>
      ) : listDegraded ? (
        <div
          role="status"
          data-testid="repl-degraded"
          className="rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          scm 보충 추천 정보를 일시적으로 불러올 수 없습니다. 콘솔의 다른
          기능은 계속 사용할 수 있습니다.
        </div>
      ) : rows.length === 0 ? (
        <p className="text-sm text-muted-foreground" data-testid="repl-empty">
          표시할 보충 추천이 없습니다.
        </p>
      ) : (
        <>
          <table className="mb-3 data-table" data-testid="repl-table">
            <caption className="sr-only">보충 추천 목록</caption>
            <thead>
              <tr className="border-b border-border text-left">
                <th scope="col" className="p-2">
                  SKU
                </th>
                <th scope="col" className="p-2">
                  창고
                </th>
                <th scope="col" className="p-2">
                  공급사
                </th>
                <th scope="col" className="p-2">
                  추천 수량
                </th>
                <th scope="col" className="p-2">
                  트리거 가용재고
                </th>
                <th scope="col" className="p-2">
                  상태
                </th>
                <th scope="col" className="p-2">
                  PO
                </th>
                <th scope="col" className="p-2">
                  작업
                </th>
              </tr>
            </thead>
            <tbody>
              {rows.map((s, i) => {
                const approveOk = canApprove(s.status);
                const dismissOk = canDismiss(s.status);
                return (
                  <tr
                    key={s.id}
                    data-testid={`repl-row-${i}`}
                    className="border-b border-border"
                  >
                    <td className="p-2">{s.skuCode ?? '—'}</td>
                    <td className="p-2">{s.warehouseId ?? '—'}</td>
                    <td className="p-2">{s.supplierId ?? '—'}</td>
                    <td className="p-2">{s.suggestedQty ?? '—'}</td>
                    <td
                      className="p-2"
                      data-testid={`repl-row-trigger-${i}`}
                    >
                      {s.triggerAvailableQty ?? '—'}
                    </td>
                    <td className="p-2" data-testid={`repl-row-status-${i}`}>
                      <StatusBadge tone={suggestionStatusTone(s.status)}>
                        {s.status ?? '—'}
                      </StatusBadge>
                    </td>
                    <td className="p-2">{s.materializedPoId ?? '—'}</td>
                    <td className="p-2">
                      <div className="flex gap-2">
                        <Button
                          size="sm"
                          onClick={() => openAction('approve', s)}
                          disabled={!approveOk || actionPending}
                          data-testid={`repl-approve-${i}`}
                        >
                          승인
                        </Button>
                        <Button
                          variant="secondary"
                          size="sm"
                          onClick={() => openAction('dismiss', s)}
                          disabled={!dismissOk || actionPending}
                          data-testid={`repl-dismiss-${i}`}
                        >
                          기각
                        </Button>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
          <nav
            className="mb-8 flex items-center justify-between"
            aria-label="보충 추천 페이지 이동"
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
              data-testid="repl-prev"
            >
              이전
            </Button>
            <span
              className="text-sm text-muted-foreground"
              data-testid="repl-pageinfo"
            >
              {`${data.page + 1} / ${totalPages} 페이지 · 총 ${data.totalElements}건`}
            </span>
            <Button
              variant="secondary"
              disabled={data.page + 1 >= totalPages}
              onClick={() =>
                setQuery((q) => ({ ...q, page: (q.page ?? 0) + 1 }))
              }
              data-testid="repl-next"
            >
              다음
            </Button>
          </nav>
        </>
      )}

      <ReplenishmentActionDialog
        open={action !== null}
        title={action ? ACTION_COPY[action.kind].title : ''}
        description={action ? ACTION_COPY[action.kind].description : ''}
        confirmLabel={action ? ACTION_COPY[action.kind].confirmLabel : ''}
        noteLabel={action ? ACTION_COPY[action.kind].noteLabel : ''}
        noteValue={note}
        onNoteChange={setNote}
        pending={activeMutation.isPending}
        errorMessage={actionError}
        onConfirm={confirmAction}
        onCancel={() => {
          setAction(null);
          setActionError(null);
          setNote('');
        }}
      />
    </section>
  );
}
