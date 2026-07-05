'use client';

import { useState } from 'react';
import { ApiError, messageForCode } from '@/shared/api/errors';
import {
  useSuggestions,
  useApproveSuggestion,
  useDismissSuggestion,
} from '../hooks/use-scm-replenishment';
import {
  REPL_DEFAULT_PAGE_SIZE,
  type SuggestionPage,
  type Suggestion,
  type SuggestionQueryParams,
  type ApproveResult,
} from '../api/types';
import { ReplenishmentActionDialog } from './ReplenishmentActionDialog';
import { ApprovedDraftBanner } from './ApprovedDraftBanner';
import { ReplenishmentFilters } from './ReplenishmentFilters';
import { ReplenishmentTable } from './ReplenishmentTable';

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
      <ReplenishmentFilters
        filters={filters}
        onChange={setFilters}
        onSubmit={submitFilters}
      />

      {/* ── DRAFT PO success affordance (approve) ───────────────────────── */}
      <ApprovedDraftBanner
        approved={approved}
        onDismiss={() => setApproved(null)}
      />

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
        <ReplenishmentTable
          rows={rows}
          queryPage={query.page ?? 0}
          dataPage={data.page}
          totalPages={totalPages}
          totalElements={data.totalElements}
          onPrev={() =>
            setQuery((q) => ({ ...q, page: Math.max(0, (q.page ?? 0) - 1) }))
          }
          onNext={() => setQuery((q) => ({ ...q, page: (q.page ?? 0) + 1 }))}
          onAction={openAction}
          actionPending={actionPending}
        />
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
