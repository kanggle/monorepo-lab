import { NextResponse } from 'next/server';
import { getNotification } from '@/features/notifications/api/notification-api';
import { mapErpError, newRequestId } from '../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin erp notification DETAIL proxy (read — GET only). Forwards to the
 * UNCHANGED producer `GET /api/erp/notifications/{id}` via `getNotification()`
 * (domain-facing IAM token server-side). The row must belong to the caller;
 * a `404 NOTIFICATION_NOT_FOUND` (unknown id, or owned by another recipient)
 * passes through `mapErpError` inline-actionably (no crash — avoids existence
 * leak by treating a foreign row as non-existent).
 *
 * NO POST / PUT / PATCH / DELETE — single notification is GET-only.
 */
export async function GET(
  _req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const requestId = newRequestId();
  const { id } = await params;
  try {
    const result = await getNotification(id);
    return NextResponse.json({ data: result });
  } catch (err) {
    return mapErpError(err, requestId);
  }
}
