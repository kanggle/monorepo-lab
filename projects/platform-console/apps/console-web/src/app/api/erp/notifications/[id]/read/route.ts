import { NextResponse } from 'next/server';
import { markNotificationRead } from '@/features/notifications/api/notification-api';
import { mapErpError, newRequestId } from '../../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin erp notification MARK-READ proxy (write — POST only). Forwards
 * to the UNCHANGED producer `POST /api/erp/notifications/{id}/read` via
 * `markNotificationRead()` (domain-facing IAM token server-side).
 *
 * The operation is **naturally idempotent** — re-marking an already-read
 * notification returns 200 with the same `readAt` (the original is preserved,
 * not advanced). No body is required or expected; no `Idempotency-Key` is set
 * (the transactional idempotency-key mechanism does not apply to
 * state-converging assignments). No `X-Operator-Reason` (notification-service
 * has no reason slot).
 *
 * `404 NOTIFICATION_NOT_FOUND` passes through `mapErpError` inline-actionably
 * (unknown id or owned by another recipient — treated as non-existent).
 *
 * NO GET / PUT / PATCH / DELETE — mark-read is POST-only.
 */
export async function POST(
  _req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const requestId = newRequestId();
  const { id } = await params;
  try {
    const result = await markNotificationRead(id);
    return NextResponse.json({ data: result });
  } catch (err) {
    return mapErpError(err, requestId);
  }
}
