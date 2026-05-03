/**
 * next-auth v5 catch-all route handler — handles `/api/auth/signin`,
 * `/api/auth/callback/<provider>`, `/api/auth/signout`, `/api/auth/session`,
 * etc.
 */
import { handlers } from '@/shared/auth/auth';

export const { GET, POST } = handlers;

// Force dynamic — auth flows must not be cached.
export const dynamic = 'force-dynamic';
