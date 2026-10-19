import { NextResponse } from 'next/server';
import { listEmployeePicker } from '@/features/erp-ops/api/erp-api';
import {
  buildListParams,
  mapErpError,
  newRequestId,
} from '../../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin erp employee «선택용» picker read proxy (GET only — TASK-ERP-BE-045,
 * masterdata-api.md § `GET /api/erp/masterdata/employees/picker`). Mirrors the
 * employees LIST proxy: IAM OIDC domain-facing token attached server-side, the
 * producer error mapped through `mapErpError` (a `403` stays `403` — the pickers
 * turn it into the «권한 없음» id-input fallback). Only `active` / `page` / `size`
 * are forwarded; the picker takes no `asOf` and no per-master filters.
 *
 * Route precedence: this static `picker` segment wins over the sibling `[id]`
 * dynamic segment in the App Router, so `/employees/picker` never reaches the
 * employee detail handler.
 */
export async function GET(req: Request) {
  const requestId = newRequestId();
  try {
    const { active, page, size } = buildListParams(req);
    const result = await listEmployeePicker({ active, page, size });
    return NextResponse.json(result);
  } catch (err) {
    return mapErpError(err, requestId);
  }
}
