import { redirect } from 'next/navigation';

/**
 * `/` resolves to the authenticated catalog home, which lives in the
 * `(console)` route group (`(console)/console/page.tsx`). The `(console)`
 * layout enforces the GAP OIDC session guard. An unauthenticated visitor is
 * redirected from there to `/login`.
 */
export default function RootIndex() {
  redirect('/console');
}
