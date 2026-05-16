import { NextResponse } from 'next/server';
import { exportAccount } from '@/features/accounts/api/accounts-api';
import { mapError, newRequestId } from '../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin export proxy (GDPR portability). The producer mandates
 * `X-Operator-Reason` even though this is a GET (the access is meta-audited
 * by the producer). The unmasked PII payload is streamed back as a download
 * via a JSON attachment; the client never buffers PII into React state —
 * the browser saves the blob (console-integration-contract § 2.4.1 export).
 */
export async function GET(
  req: Request,
  { params }: { params: Promise<{ accountId: string }> },
) {
  const requestId = newRequestId();
  const { accountId } = await params;
  const reason = new URL(req.url).searchParams.get('reason') ?? '';
  try {
    const data = await exportAccount(accountId, reason);
    return new NextResponse(JSON.stringify(data, null, 2), {
      status: 200,
      headers: {
        'Content-Type': 'application/json',
        'Content-Disposition': `attachment; filename="account-${encodeURIComponent(
          accountId,
        )}-export.json"`,
        'Cache-Control': 'no-store',
      },
    });
  } catch (err) {
    return mapError(err, requestId);
  }
}
