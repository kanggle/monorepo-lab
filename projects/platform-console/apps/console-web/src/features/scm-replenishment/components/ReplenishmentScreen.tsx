'use client';

import { useState } from 'react';
import { ApiError } from '@/shared/api/errors';
import { useSuggestions } from '../hooks/use-scm-replenishment';
import { useReplenishmentActions } from '../hooks/use-replenishment-actions';
import {
  REPL_DEFAULT_PAGE_SIZE,
  type SuggestionPage,
  type SuggestionQueryParams,
} from '../api/types';
import { ReplenishmentActionDialog } from './ReplenishmentActionDialog';
import { ApprovedDraftBanner } from './ApprovedDraftBanner';
import { ReplenishmentFilters } from './ReplenishmentFilters';
import { ReplenishmentListStates } from './ReplenishmentListStates';
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
 *
 * The approve/dismiss action-model lives in {@link useReplenishmentActions};
 * the filter bar / suggestion table / DRAFT-PO banner / list-state banners in
 * their own leaves (TASK-PC-FE-190 / -215 split). This orchestrator keeps the
 * list query wiring only.
 */

export interface ReplenishmentScreenProps {
  suggestions: SuggestionPage;
}

interface FilterState {
  status: string;
  skuCode: string;
}

const EMPTY_FILTERS: FilterState = { status: '', skuCode: '' };

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
  const showTable =
    !listForbidden && !listRateLimited && !listDegraded && rows.length > 0;

  // ── action dialog (approve / dismiss) ───────────────────────────────
  const {
    action,
    copy,
    note,
    setNote,
    actionError,
    actionPending,
    activePending,
    approved,
    dismissApproved,
    openAction,
    confirmAction,
    cancelAction,
  } = useReplenishmentActions();

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
      <ApprovedDraftBanner approved={approved} onDismiss={dismissApproved} />

      {/* ── list / states ───────────────────────────────────────────────── */}
      {showTable ? (
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
      ) : (
        <ReplenishmentListStates
          forbidden={listForbidden}
          rateLimited={listRateLimited}
          degraded={listDegraded}
        />
      )}

      <ReplenishmentActionDialog
        open={action !== null}
        title={copy?.title ?? ''}
        description={copy?.description ?? ''}
        confirmLabel={copy?.confirmLabel ?? ''}
        noteLabel={copy?.noteLabel ?? ''}
        noteValue={note}
        onNoteChange={setNote}
        pending={activePending}
        errorMessage={actionError}
        onConfirm={confirmAction}
        onCancel={cancelAction}
      />
    </section>
  );
}
