import type { ReactNode } from 'react';
import { redirect } from 'next/navigation';
import { getOperatorToken, hasPreOperatorSession } from '@/shared/lib/session';

export const dynamic = 'force-dynamic';

/**
 * Pre-operator onboarding shell (ADR-MONO-044 §3.4 / TASK-PC-FE-182).
 *
 * The ONLY route group admitted to the "logged in but not yet an operator"
 * intermediate state. Server-side guard (three-way, no client token juggling):
 *   - operator token present → already an operator → `/` (the console shell).
 *   - no pre-operator session (no IAM access token) → anonymous → `/login`.
 *   - else (access present, operator absent) → render the onboarding surface.
 *
 * Deliberately minimal chrome — no tenant switcher / sidebar (there is no
 * tenant or operator context yet); just a centered card, mirroring `/login`.
 */
export default async function OnboardingLayout({
  children,
}: {
  children: ReactNode;
}) {
  if ((await getOperatorToken()) !== null) redirect('/');
  if (!(await hasPreOperatorSession())) redirect('/login');

  return (
    <main className="flex min-h-screen items-center justify-center bg-muted px-4 py-10">
      <div className="w-full max-w-md">{children}</div>
    </main>
  );
}
