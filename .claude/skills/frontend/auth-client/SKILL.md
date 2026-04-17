---
name: auth-client
description: Frontend auth: HttpOnly cookies, refresh proxy, server boundary
category: frontend
---

# Skill: Auth Client

Patterns for handling JWT tokens, refresh, and server/client auth boundary in Next.js apps.

Prerequisite: read `specs/contracts/http/auth-api.md` and `backend/jwt-auth/SKILL.md` before using this skill. For backend-side patterns see `backend/jwt-auth/SKILL.md` and `backend/redis-session/SKILL.md`.

---

## Token Storage Decision

| Storage | Pros | Cons | When |
|---|---|---|---|
| HttpOnly cookie | XSS-safe, sent automatically | CSRF concern, larger requests | **Default** |
| In-memory (Zustand/Context) | XSS-safe-ish, no CSRF | Lost on refresh, multi-tab issue | SPA without SSR |
| localStorage | Survives reload, simple | XSS-vulnerable | Forbidden for access tokens |
| sessionStorage | Tab-scoped | XSS-vulnerable | Forbidden for access tokens |

**Use HttpOnly cookies for both access and refresh tokens.** Never store JWTs in localStorage.

---

## Cookie Configuration

```ts
// shared/api/auth/set-cookies.ts
'use server';
import { cookies } from 'next/headers';

export async function setAuthCookies(accessToken: string, refreshToken: string) {
  const cookieStore = await cookies();
  cookieStore.set('access_token', accessToken, {
    httpOnly: true,
    secure: true,
    sameSite: 'lax',
    path: '/',
    maxAge: 60 * 30, // 30 min
  });
  cookieStore.set('refresh_token', refreshToken, {
    httpOnly: true,
    secure: true,
    sameSite: 'strict',
    path: '/api/auth',
    maxAge: 60 * 60 * 24 * 14, // 14 days
  });
}
```

Required flags:
- `httpOnly: true` — prevent JS access
- `secure: true` — HTTPS only (skip in local dev only)
- `sameSite: 'lax'` for access, `'strict'` for refresh
- `path: '/api/auth'` for refresh — limit exposure

---

## Refresh Flow

Server-side refresh proxy to avoid exposing refresh tokens to the client:

```ts
// app/api/auth/refresh/route.ts
export async function POST() {
  const cookieStore = await cookies();
  const refresh = cookieStore.get('refresh_token')?.value;
  if (!refresh) return new Response(null, { status: 401 });

  const res = await fetch(`${process.env.AUTH_API}/auth/refresh`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken: refresh }),
  });
  if (!res.ok) {
    cookieStore.delete('access_token');
    cookieStore.delete('refresh_token');
    return new Response(null, { status: 401 });
  }
  const { accessToken, refreshToken } = await res.json();
  await setAuthCookies(accessToken, refreshToken);
  return new Response(null, { status: 204 });
}
```

Client retry on 401:

```ts
// shared/api/client.ts
async function request(input: RequestInfo, init?: RequestInit) {
  let res = await fetch(input, { credentials: 'include', ...init });
  if (res.status === 401) {
    const refreshed = await fetch('/api/auth/refresh', { method: 'POST', credentials: 'include' });
    if (refreshed.ok) {
      res = await fetch(input, { credentials: 'include', ...init });
    }
  }
  return res;
}
```

Use a single in-flight refresh promise to prevent thundering herd.

---

## Server vs Client Boundary

| Action | Where | Why |
|---|---|---|
| Read user from token | Server component | Cookie access only on server |
| Show "Login" / "Logout" toggle | Server component | Avoid hydration mismatch |
| Form login submission | Server action or API route | Set HttpOnly cookies |
| Conditional UI based on role | Server component (preferred) | No client-side leak |
| Client-side redirect on 401 | Client component | After refresh fails |

```tsx
// app/(authed)/layout.tsx
import { redirect } from 'next/navigation';
import { getCurrentUser } from '@/shared/auth/server';

export default async function AuthedLayout({ children }: { children: React.ReactNode }) {
  const user = await getCurrentUser();
  if (!user) redirect('/login');
  return <>{children}</>;
}
```

---

## Logout

Always clear cookies on the server and revoke the refresh token on the backend:

```ts
'use server';
export async function logoutAction() {
  const cookieStore = await cookies();
  const refresh = cookieStore.get('refresh_token')?.value;
  if (refresh) await fetch(`${process.env.AUTH_API}/auth/logout`, { method: 'POST', body: JSON.stringify({ refreshToken: refresh }) });
  cookieStore.delete('access_token');
  cookieStore.delete('refresh_token');
  redirect('/');
}
```

---

## Common Pitfalls

| Pitfall | Fix |
|---|---|
| Storing JWT in localStorage | Use HttpOnly cookie |
| Returning refresh token to client JSON | Handle only on server |
| Missing `credentials: 'include'` on fetch | Cookies not sent |
| `sameSite: 'none'` without explicit need | Use `'lax'` |
| Multiple concurrent refresh calls | Single-flight promise |
| Reading cookies from client component | Move to server component |

---

## Verification Checklist

- [ ] Tokens stored in HttpOnly cookies only
- [ ] Refresh handled by server route, not client
- [ ] Single-flight refresh on 401
- [ ] Auth-protected routes guarded in server layout
- [ ] Logout revokes refresh token on backend
- [ ] No JWT or refresh token visible in browser DevTools Application tab JS-accessible storage
