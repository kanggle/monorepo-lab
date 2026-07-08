import { redirect } from 'next/navigation';
import { ApiError } from '@/shared/api/errors';
import { listSettings } from './wms-settings-api';
import { getProjectionStatus } from './wms-refs-api';
import type { CellStatus } from './overview-state';
import type { ProjectionStatus, Setting } from './types';

/**
 * Server-side wms **운영설정**(operations settings) section state for the
 * dedicated `(console)/wms/operations` route (TASK-PC-FE-224 — surfaces the
 * previously-uncoded `GET /settings` read (§ 5.1) alongside the already-
 * exported-but-zero-consumer `getProjectionStatus()` (§ 6.2, already living
 * in `wms-refs-api.ts`)).
 *
 * ── TWO INDEPENDENT CELLS (task § Scope item 3 / Failure Scenarios) ──
 * Unlike every other single-read wms section state (`master-state.ts` /
 * `inbound-state.ts` / `inventory-state.ts` / …), this section fans OUT
 * settings + projection-status IN PARALLEL and resolves EACH into its own
 * {@link CellStatus} — mirrors `overview-state.ts`'s `cell()` helper (the
 * fan-out-of-independent-cells pattern, reused here rather than re-exported,
 * same as every other feature-local helper in this module). One leg
 * degrading (403/503/timeout) must NEVER blank the other section (task
 * Failure Scenario: "settings/projection 한쪽 실패를 전체 degrade로 오처리
 * → 정상 섹션까지 숨김" — a state test asserts each cell resolves
 * independently). Only a `401` (IAM OIDC session expired) on EITHER leg
 * re-throws to the top-level catch → a WHOLE-SESSION `redirect('/login')`
 * (no partial authed state — same invariant as every other wms section).
 *
 * Eligibility gate (§ 2.4.5 tenant-model divergence): identical to every
 * other wms section state — the `(console)/wms/operations` PAGE resolves
 * the operator's wms eligibility from the data-driven registry
 * (`productKey=wms`) and passes it in here. Not eligible ⇒ block with NO
 * wms call ever fabricated (no cross-tenant call, no fan-out run).
 */
export interface WmsOperationsSectionState {
  /** True when the operator is not wms-eligible — no fan-out was run. */
  notEligible: boolean;
  /** `GET /settings` content (§ 5.1), or `null` when the cell did not
   *  resolve `ok`. The screen filters this down to the KNOWN operational
   *  keys it renders (task Edge Case: a producer-absent key ⇒ its row is
   *  simply omitted, never forced blank). */
  settings: Setting[] | null;
  settingsStatus: CellStatus;
  /** `GET /operations/projection-status` (§ 6.2), or `null` when the cell
   *  did not resolve `ok`. */
  projection: ProjectionStatus | null;
  projectionStatus: CellStatus;
}

const EMPTY: WmsOperationsSectionState = {
  notEligible: false,
  settings: null,
  settingsStatus: 'degraded',
  projection: null,
  projectionStatus: 'degraded',
};

interface Cell<T> {
  value: T | null;
  status: CellStatus;
}

/**
 * Resolve a single fan-out leg into a cell — mirrors `overview-state.ts`'s
 * `cell()`: success → `ok`; `403` → `forbidden`; `503`/timeout/network
 * (`WmsUnavailableError`, not an `ApiError`) / any other error → `degraded`.
 * A `401` is RE-THROWN so the caller performs a whole-session
 * `redirect('/login')` (never a per-cell degrade).
 */
async function cell<T>(p: Promise<T>): Promise<Cell<T>> {
  try {
    return { value: await p, status: 'ok' };
  } catch (err) {
    if (err instanceof ApiError && err.status === 401) {
      throw err; // whole-session re-login — propagate, do not degrade.
    }
    if (err instanceof ApiError && err.status === 403) {
      return { value: null, status: 'forbidden' };
    }
    return { value: null, status: 'degraded' };
  }
}

/**
 * @param eligible whether the operator is wms-eligible, resolved by the
 *   page from the data-driven registry. `false` ⇒ block (no wms call).
 */
export async function getWmsOperationsState(
  eligible: boolean,
): Promise<WmsOperationsSectionState> {
  if (!eligible) {
    // Not wms-eligible — never fabricate a cross-tenant call.
    return { ...EMPTY, notEligible: true };
  }

  try {
    const [settingsCell, projectionCell] = await Promise.all([
      cell(listSettings({ page: 0, size: 20 })),
      cell(getProjectionStatus()),
    ]);

    return {
      notEligible: false,
      settings: settingsCell.value?.data.content ?? null,
      settingsStatus: settingsCell.status,
      projection: projectionCell.value?.data ?? null,
      projectionStatus: projectionCell.status,
    };
  } catch (err) {
    // Only a `401` re-thrown by a cell reaches here → whole-session re-login.
    if (err instanceof ApiError && err.status === 401) {
      redirect('/login');
    }
    throw err;
  }
}
