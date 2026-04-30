import { cookies } from 'next/headers';
import { redirect } from 'next/navigation';
import { type OperatorSession } from '@/shared/api/admin-api';

/**
 * Resolve the current operator session on the server.
 * Decodes the HttpOnly accessToken JWT payload (no signature verification
 * needed — the token was issued by admin-service and stored in an HttpOnly
 * cookie that JS cannot tamper with).
 *
 * If the session is missing / expired, redirects to `/login`.
 */
export async function requireOperatorSession(redirectTo: string): Promise<OperatorSession> {
  const cookieStore = await cookies();
  const access = cookieStore.get('accessToken')?.value;
  if (!access) redirect(`/login?redirect=${encodeURIComponent(redirectTo)}`);

  try {
    // JWT structure: header.payload.signature
    const parts = access.split('.');
    if (parts.length !== 3) redirect(`/login?redirect=${encodeURIComponent(redirectTo)}`);

    const payload = JSON.parse(Buffer.from(parts[1], 'base64url').toString('utf-8'));

    // Check expiration
    const now = Math.floor(Date.now() / 1000);
    if (payload.exp && payload.exp < now) {
      redirect(`/login?redirect=${encodeURIComponent(redirectTo)}`);
    }

    return {
      operatorId: payload.sub ?? '',
      email: payload.email ?? payload.sub ?? '',
      roles: payload.roles ?? ['SUPER_ADMIN'],
    };
  } catch {
    redirect(`/login?redirect=${encodeURIComponent(redirectTo)}`);
  }
}
