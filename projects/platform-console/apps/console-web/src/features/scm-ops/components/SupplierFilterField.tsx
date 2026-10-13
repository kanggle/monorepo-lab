'use client';

import { useState } from 'react';
import { useSuppliers } from '../hooks/use-scm-ops';
import { SCM_SUPPLIER_MAX_PAGE_SIZE, type Supplier } from '@/shared/api/scm-suppliers-types';

/**
 * `<SupplierFilterField>` — the PO-list 공급사 filter (TASK-PC-FE-337), replacing
 * the raw `supplierId` text input with a one-page `<select>` of real suppliers
 * (mirrors the `SellerSelect` TASK-PC-FE-336 shape: one page of up to
 * {@link SCM_SUPPLIER_MAX_PAGE_SIZE} is enough — the supplier-search server
 * endpoint only supports an exact `code` match, so a free-text search box over
 * the gateway isn't an option here; `procurement-api.md` § GET suppliers).
 *
 * Label: «code · name · 상태» (status is shown, not hidden — the producer
 * stays the status authority; a non-ACTIVE supplier is still selectable,
 * task Edge Case "정지된 공급사").
 *
 * ── design choice: the "직접 입력" toggle (AC-4 / Related Edge Case) ──
 * `GET /po?supplierId=` matches the PO's STORED `supplierId` EXACTLY
 * (procurement-api.md § PurchaseOrderResponse supplier reference fields,
 * point 6). A PO drafted through `POST /po` stores a supplier **id**; a PO
 * materialised from a replenishment suggestion (`POST /po/from-suggestion`)
 * stores whatever demand-planning sent, which per `scm-procurement-events.md`
 * (ADR-MONO-050 D9) is the supplier **code** — NOT the id this `<select>`'s
 * options emit. A `<select>` cannot hold an arbitrary typed value, so there is
 * no way to reach a code-stored PO through it. Rather than silently failing to
 * find those POs, this field keeps a `직접 입력` toggle that swaps the
 * `<select>` for a plain text input on the SAME `data-testid` — the operator
 * who knows (from the {@link ScmPoTable} hint, AC-4) that a PO was suggestion-
 * sourced can still reach it by typing the supplier code directly. This is the
 * SIMPLEST shape that preserves both paths without inventing a combobox
 * (`OperatorPicker`-style escape hatches are overkill for a one-page list).
 *
 * Resilience (AC-3): a supplier-list fetch/parse failure (`useSuppliers().
 * isError`) falls back to the OLD free-text id input UNCONDITIONALLY (no
 * toggle needed — there is nothing to select from), on the SAME
 * `data-testid` — the caller never needs to branch on selector-vs-fallback;
 * it only ever reads `value`/`onChange`.
 *
 * Truncation notice (Edge Case "공급사가 한 페이지보다 많음"): when the
 * producer's `totalElements` exceeds what this one page loaded, a «처음 N개만
 * 표시» note renders under the select (the `SellerSelect` convention).
 */
export interface SupplierFilterFieldProps {
  id: string;
  value: string;
  onChange: (supplierId: string) => void;
  /** Base testid — also the testid of the rendered `<select>`/`<input>`
   *  itself (kept IDENTICAL across selector/manual/fallback mode so callers/
   *  tests never branch). */
  testid: string;
}

function supplierOptionLabel(s: Supplier): string {
  return `${s.code} · ${s.name} · ${s.status ?? '—'}`;
}

const inputCls =
  'mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary';

export function SupplierFilterField({
  id,
  value,
  onChange,
  testid,
}: SupplierFilterFieldProps) {
  const [manual, setManualState] = useState(false);
  // Switching mode clears the value: a code typed in manual mode is not an
  // option of the select, so it would stay applied while the select shows
  // «전체 공급사» — a filter the operator can no longer see.
  function setManual(next: boolean) {
    setManualState(next);
    if (value !== '') onChange('');
  }
  const suppliersQ = useSuppliers({ page: 0, size: SCM_SUPPLIER_MAX_PAGE_SIZE });
  const suppliers: Supplier[] = suppliersQ.data?.content ?? [];
  const total = suppliersQ.data?.totalElements ?? suppliers.length;
  const listFailed = suppliersQ.isError;
  const loading = suppliersQ.isLoading;
  const showManualInput = manual || listFailed;

  if (showManualInput) {
    return (
      <>
        <input
          id={id}
          type="text"
          value={value}
          onChange={(e) => onChange(e.target.value)}
          data-testid={testid}
          autoComplete="off"
          className={inputCls}
        />
        {listFailed ? (
          <p
            className="mt-1 text-xs text-muted-foreground"
            role="status"
            data-testid={`${testid}-fallback`}
          >
            공급사 목록을 불러오지 못해 공급사 ID를 직접 입력합니다.
          </p>
        ) : (
          <button
            type="button"
            onClick={() => setManual(false)}
            data-testid={`${testid}-mode-select`}
            className="mt-1 text-xs font-medium text-primary hover:underline"
          >
            목록에서 선택
          </button>
        )}
      </>
    );
  }

  return (
    <>
      <select
        id={id}
        value={value}
        disabled={loading}
        onChange={(e) => onChange(e.target.value)}
        data-testid={testid}
        className={inputCls}
      >
        <option value="">
          {loading ? '공급사 목록 불러오는 중…' : '전체 공급사'}
        </option>
        {suppliers.map((s) => (
          <option key={s.id} value={s.id}>
            {supplierOptionLabel(s)}
          </option>
        ))}
      </select>
      <button
        type="button"
        onClick={() => setManual(true)}
        data-testid={`${testid}-mode-manual`}
        className="mt-1 text-xs font-medium text-primary hover:underline"
      >
        직접 입력 (공급사 코드로 찾기)
      </button>
      {suppliersQ.isSuccess && total > suppliers.length && (
        <p
          className="mt-1 text-xs text-muted-foreground"
          data-testid={`${testid}-truncated`}
        >
          공급사가 많아 처음 {suppliers.length}개만 표시합니다.
        </p>
      )}
    </>
  );
}
