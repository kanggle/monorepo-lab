import { redirect } from 'next/navigation';

export const dynamic = 'force-dynamic';

/**
 * TASK-MONO-773 S6 — PHASE 1 scaffold only (so the red tests compile). It reproduces the pre-S6 behaviour:
 * tenant creation lived at `/onboarding`. PHASE 2 replaces this with the one «테넌트 생성» screen.
 */
export default async function NewTenantPage(): Promise<never> {
  redirect('/onboarding');
}
