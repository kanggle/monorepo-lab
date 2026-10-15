import { NextResponse } from 'next/server';

export const runtime = 'nodejs';

/**
 * TASK-MONO-773 S6 — PHASE 1 scaffold only (so the red tests compile). Pre-S6 there is no such route — Next
 * answered `404`; this reproduces that. PHASE 2 implements the contract's BFF.
 */
export async function POST(_req: Request): Promise<NextResponse> {
  return NextResponse.json({ code: 'NOT_FOUND', message: 'not found' }, { status: 404 });
}
