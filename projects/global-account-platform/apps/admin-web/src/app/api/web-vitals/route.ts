import { NextResponse } from 'next/server';
import { logger, newRequestId } from '@/shared/lib/logger';

export async function POST(req: Request) {
  const requestId = newRequestId();
  try {
    const payload = await req.json();
    logger.info('web_vital', { requestId, ...payload });
  } catch {
    logger.warn('web_vital_parse_failed', { requestId });
  }
  return new NextResponse(null, { status: 204 });
}
