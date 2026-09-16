/**
 * The notification bell's inbox items resolve to a REAL approval, and the
 * notification's `type` agrees with that approval's actual `status`
 * (TASK-PC-FE-285 coordinator CORRECTION, 2026-09-17 UTC).
 *
 * The bell (`NotificationBell.tsx`) is mounted on EVERY screen a sample
 * visitor sees — a dangling `sourceId` in `SAMPLE_NOTIFICATION_INBOX` is a
 * 404 one click away from anywhere in the console, and a `type` that
 * disagrees with the linked approval's `status` is the exact "two screens
 * tell a different story about the same record" defect class
 * TASK-PC-FE-284's settlement CORRECTION already named for money — here it
 * would be an "APPROVAL_APPROVED" notification linking to a still-SUBMITTED
 * request (or similar).
 *
 * Everything below reads through `sampleResponse` (never the raw
 * `SAMPLE_NOTIFICATION_INBOX` / `ERP_APPROVAL_REQUESTS` constants) — the
 * inbox fixture and the erp_approval fixture are two independent modules;
 * this test is the only thing that proves they still agree.
 */
import { describe, it, expect } from 'vitest';
import { sampleResponse } from '@/shared/sample/router';

const TYPE_TO_STATUS: Record<string, string> = {
  APPROVAL_SUBMITTED: 'SUBMITTED',
  APPROVAL_APPROVED: 'APPROVED',
  APPROVAL_REJECTED: 'REJECTED',
  APPROVAL_WITHDRAWN: 'WITHDRAWN',
};

interface InboxItem {
  sourceType?: string;
  sourceId?: string;
  type: string;
}

async function fetchInboxItems(): Promise<InboxItem[]> {
  const res = sampleResponse({
    core: 'console-bff',
    surface: 'notifications-inbox',
    method: 'GET',
    path: '/',
  });
  expect(res.status).toBe(200);
  const body = (await res.json()) as { items: InboxItem[] };
  return body.items;
}

describe('notification bell inbox → erp approval (world consistency)', () => {
  it('🔵 non-vacuity — the inbox has APPROVAL-sourced items to walk', async () => {
    const items = await fetchInboxItems();
    const approvalItems = items.filter((i) => i.sourceType === 'APPROVAL');
    expect(approvalItems.length).toBeGreaterThan(0);
  });

  it('every APPROVAL-sourced notification\'s sourceId resolves to a REAL approval request', async () => {
    const items = await fetchInboxItems();
    for (const item of items) {
      if (item.sourceType !== 'APPROVAL' || !item.sourceId) continue;
      const detailRes = sampleResponse({
        core: 'flat',
        surface: 'erp_approval',
        method: 'GET',
        path: `/api/erp/approval/requests/${encodeURIComponent(item.sourceId)}`,
      });
      expect(detailRes.status, `sourceId=${item.sourceId}`).toBe(200);
    }
  });

  it('every APPROVAL-sourced notification\'s type agrees with the linked approval\'s ACTUAL status', async () => {
    const items = await fetchInboxItems();
    let checked = 0;
    for (const item of items) {
      if (item.sourceType !== 'APPROVAL' || !item.sourceId) continue;
      const expectedStatus = TYPE_TO_STATUS[item.type];
      expect(expectedStatus, `unknown notification type ${item.type}`).toBeTruthy();
      const detailRes = sampleResponse({
        core: 'flat',
        surface: 'erp_approval',
        method: 'GET',
        path: `/api/erp/approval/requests/${encodeURIComponent(item.sourceId)}`,
      });
      const body = (await detailRes.json()) as { data: { status: string } };
      expect(body.data.status, `sourceId=${item.sourceId} type=${item.type}`).toBe(
        expectedStatus,
      );
      checked += 1;
    }
    expect(checked).toBeGreaterThan(0);
  });

  it('the bell\'s deep-link route (`/erp/approval?request=<sourceId>`) resolves through the SAME approval-requests detail lookup the route itself uses', async () => {
    // Mirrors `NotificationBell.tsx`'s fallback: no `deepLink` on any erp item
    // here, so every APPROVAL-sourced row falls through to
    // `/erp/approval?request=<sourceId>` — the exact path `ErpApprovalScreen`
    // reads via `sp.request`. Proving the detail lookup 200s (above) IS
    // proving that route resolves; this cell only pins the "no deepLink"
    // precondition so the fallback branch is the one actually exercised.
    const items = await fetchInboxItems();
    const approvalItems = items.filter((i) => i.sourceType === 'APPROVAL');
    for (const item of approvalItems as Array<InboxItem & { deepLink?: string }>) {
      expect(item.deepLink, item.sourceId).toBeUndefined();
    }
  });
});
