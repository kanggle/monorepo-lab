import { NextResponse } from 'next/server';
import { buildGapEndSessionUrl } from '@/shared/auth/federated-logout';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

/**
 * Returns the GAP RP-initiated-logout (`end_session`) URL for the current
 * session, or `{ url: null }` when there is no `id_token_hint`.
 *
 * The client logout (`auth-context` `logout()`) fetches this BEFORE calling
 * `signOut()` — i.e. while the id_token cookie still exists — then clears the
 * NextAuth session and hard-navigates to the returned URL so the GAP (SAS) IdP
 * terminates its own session (no silent re-auth on next login). A literal
 * segment under `/api/auth/`, so it takes precedence over the NextAuth
 * `[...nextauth]` catch-all. See TASK-PC-FE-033 / BE-328 for the rationale.
 */
export async function GET() {
  const url = await buildGapEndSessionUrl();
  return NextResponse.json({ url });
}
