import { NextResponse } from 'next/server';
import { listNotifications } from '@/features/notifications/api/notification-api';
import type { NotificationInboxQueryParams } from '@/features/notifications/api/notification-types';
import { mapErpError, newRequestId } from '../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin erp notification INBOX proxy (read — GET only). Forwards to the
 * UNCHANGED producer `GET /api/erp/notifications` via `listNotifications()`
 * (domain-facing IAM token server-side). Returns the caller's recipient-scoped
 * notification inbox with optional `unread`/`page`/`size` filtering.
 *
 * `unread` is forwarded verbatim ONLY when present in the query string (omitting
 * is the producer default "all" — not the same as `unread=false`). `page` and
 * `size` are forwarded when present. No other query params are forwarded.
 *
 * NO POST / PUT / PATCH / DELETE — inbox is GET-only.
 */
export async function GET(req: Request) {
  const requestId = newRequestId();
  const sp = new URL(req.url).searchParams;
  const params: NotificationInboxQueryParams = {};
  // `unread` — only set when explicitly present (boolean string: 'true'/'false').
  if (sp.has('unread')) params.unread = sp.get('unread') === 'true';
  if (sp.has('page')) params.page = Number(sp.get('page'));
  if (sp.has('size')) params.size = Number(sp.get('size'));
  try {
    const result = await listNotifications(params);
    return NextResponse.json(result);
  } catch (err) {
    return mapErpError(err, requestId);
  }
}
