import Link from 'next/link';
import {
  getUsersSectionState,
  UsersScreen,
} from '@/features/ecommerce-ops';
import { resolveEcommerceEligibility } from '../products/_eligibility';

export const dynamic = 'force-dynamic';

/**
 * ecommerce user list route (TASK-PC-FE-084 — the users facet;
 * ADR-MONO-031 Phase 2b). An in-console nav destination under the `/ecommerce`
 * section.
 *
 * Server component. ecommerce is reached server-side with the HttpOnly
 * **domain-facing IAM OIDC access token** (NOT the IAM exchanged operator
 * token — § 2.4.10 per-domain credential divergence; the ecommerce gateway
 * requires the IAM OIDC token with `account_type=OPERATOR`).
 *
 * Eligibility (§ 2.2 / § 2.4.10): reuses `resolveEcommerceEligibility()` from
 * the products dir (import-only, no modification). Waterfall mirrors the
 * orders page: registryDegraded → notEligible → forbidden → degraded → happy.
 *
 * READ-ONLY: no mutation surface.
 */
export default async function EcommerceUsersPage() {
  const { eligible, registryDegraded } = await resolveEcommerceEligibility();

  const heading = (
    <h1 id="ecommerce-users-heading" className="mb-6 text-2xl font-semibold">
      E-Commerce 사용자
    </h1>
  );

  if (registryDegraded) {
    return (
      <section aria-labelledby="ecommerce-users-heading">
        {heading}
        <div
          role="status"
          data-testid="user-degraded"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          ecommerce 사용자 정보를 일시적으로 불러올 수 없습니다. 콘솔의 다른
          기능은 계속 사용할 수 있습니다. 잠시 후 다시 시도하세요.
        </div>
      </section>
    );
  }

  const state = await getUsersSectionState(eligible);

  if (state.notEligible) {
    return (
      <section aria-labelledby="ecommerce-users-heading">
        {heading}
        <div
          role="status"
          data-testid="user-not-eligible"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          <p className="mb-2 font-medium text-foreground">
            ecommerce 사용자 화면에 대한 접근 권한이 없습니다.
          </p>
          <p>
            현재 로그인한 운영자에게 ecommerce 테넌트 스코프가 부여되어 있지
            않습니다. 접근이 필요하면 운영자 관리자에게 문의하세요.
          </p>
          <Link
            href="/console"
            className="mt-4 inline-block text-sm underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          >
            카탈로그로 이동
          </Link>
        </div>
      </section>
    );
  }

  if (state.forbidden) {
    return (
      <section aria-labelledby="ecommerce-users-heading">
        {heading}
        <div
          role="status"
          data-testid="user-forbidden"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          이 화면을 조회할 권한이 없습니다. (운영자 역할 확인이 필요합니다.)
        </div>
      </section>
    );
  }

  if (state.degraded || !state.users) {
    return (
      <section aria-labelledby="ecommerce-users-heading">
        {heading}
        <div
          role="status"
          data-testid="user-degraded"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          ecommerce 사용자 정보를 일시적으로 불러올 수 없습니다. 콘솔의 다른
          기능은 계속 사용할 수 있습니다. 잠시 후 다시 시도하세요.
        </div>
      </section>
    );
  }

  return <UsersScreen users={state.users} />;
}
