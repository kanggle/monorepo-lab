import { AcceptSellerInvitationForm } from '@/features/seller-invitation';
import { OIDC_ISSUER_URL } from '@/shared/auth/auth-callbacks';

interface Props {
  searchParams: Promise<{ token?: string }>;
}

/**
 * Seller-member invitation accept screen (TASK-FE-107). Protected by
 * `middleware.ts` (not in the public-path allow-list) — an unauthenticated
 * visit is bounced to `/login?from=<this path + ?token=>` and returns here
 * after sign-in (AC-4).
 *
 * Server Component so the IAM email-verification link (AC-2) can be built
 * from the server-only `OIDC_ISSUER_URL` env without exposing it to the
 * browser bundle or adding a new `NEXT_PUBLIC_*` var (AC-0 finding) — same
 * origin the storefront already uses for sign-in (`shared/auth/auth.ts`) and
 * RP-initiated logout (`shared/auth/federated-logout.ts`).
 */
export default async function AcceptSellerInvitationPage({ searchParams }: Props) {
  const { token } = await searchParams;
  const emailVerificationUrl = `${OIDC_ISSUER_URL}/email-verification`;

  return (
    <div className="container" style={{ paddingTop: 'var(--space-8)', paddingBottom: 'var(--space-16)', maxWidth: '480px' }}>
      <AcceptSellerInvitationForm
        initialToken={token?.trim()}
        emailVerificationUrl={emailVerificationUrl}
      />
    </div>
  );
}
