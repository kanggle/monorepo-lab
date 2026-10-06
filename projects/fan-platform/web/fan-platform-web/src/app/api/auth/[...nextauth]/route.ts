/**
 * next-auth v5 catch-all route handler — handles `/api/auth/signin`,
 * `/api/auth/callback/<provider>`, `/api/auth/signout`, etc.
 *
 * `GET /api/auth/session` is wrapped (TASK-FAN-FE-027): the session read is the
 * ONE place the silent refresh runs (its Set-Cookie reaches the browser), and a
 * concurrent-refresh LOSER must not write its cookie over the winner's — see
 * `shared/auth/session-route.ts`.
 */
import { handlers } from '@/shared/auth/auth';
import { withRefreshRaceRetry } from '@/shared/auth/session-route';

export const GET = withRefreshRaceRetry({ handler: handlers.GET });
export const POST = handlers.POST;

// Force dynamic — auth flows must not be cached.
export const dynamic = 'force-dynamic';
