import { NextResponse } from 'next/server';
import { getAccessToken, getActiveTenant } from '@/shared/lib/session';

export const runtime = 'nodejs';

/**
 * Non-sensitive session status for client components (header / switcher).
 * Returns ONLY booleans + the active tenant slug — never the token itself
 * (HttpOnly invariant; frontend-app.md § Authentication).
 */
export async function GET() {
  const authenticated = (await getAccessToken()) !== null;
  const activeTenant = await getActiveTenant();
  return NextResponse.json({ authenticated, activeTenant });
}
