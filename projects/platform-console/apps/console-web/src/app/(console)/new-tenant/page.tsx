import Link from 'next/link';
import {
  CreateOrganizationForm,
  ReceivedInvitations,
  getReceivedInvitationsState,
} from '@/features/onboarding';
import { TenantCreateScreen, type TenantOrgNodeOption } from '@/features/tenants';
import { listOrgNodes } from '@/features/org-hierarchy';
import { getSelfOrNull } from '@/shared/api/iam-operators-read';
import { hasPermission } from '@/shared/guide/permission-map';
import { getServerEnv } from '@/shared/config/env';
import { buildLoginRedirectFor } from '@/shared/lib/login-redirect';
import { getAccessToken, isNonOperatorSession, isSampleVisitor } from '@/shared/lib/session';

export const dynamic = 'force-dynamic';

/**
 * The one «테넌트 생성» screen (TASK-MONO-773 S6 — console-integration-contract § 2.6.4; ADR-MONO-080 D9 = T1).
 * «조직 만들기» (`/onboarding`) and «테넌트 등록» (the `/tenants` form) became this page. One screen, three modes,
 * picked by WHO came in:
 *
 * | shell state                                      | mode                                   |
 * |--------------------------------------------------|----------------------------------------|
 * | non-operator (guard row 3)                       | self — first company + the empty state |
 * | operator without a `'*'` `tenant.manage` grant   | self — second company (ADR-082 D6 b)   |
 * | `'*'` operator holding `tenant.manage`           | SUPER_ADMIN — tenant + first admin     |
 *
 * The self modes post to the onboarding BFF with the IAM access cookie (the operator token is not used — IAM
 * resolves the creator by `sub`); the SUPER_ADMIN mode posts to `/api/tenants` with `firstAdmin`.
 *
 * 🔴 The SUPER_ADMIN predicate reads `GET /api/admin/me` (roles include the caller's `'*'` role rows whatever
 *    tenant is active — `admin-api.md` § 행동 테넌트): `homeTenantId === '*'` ∧ the roles hold `tenant.manage`.
 *    A company-scoped `SUPER_ADMIN` role row (home ≠ `'*'`) is a company operator here. `/me` unreadable (e.g. no
 *    active tenant) → the self form: never the more privileged form on a guess, and the producer answers a `'*'`
 *    caller there with `409 OPERATOR_ALREADY_PROVISIONED` (its copy names the platform administrator).
 *
 * Sample visitor (guard row 1) → a static notice on this same address (§ 2.6.4 «Sample mode», TASK-PC-FE-376):
 * the sample shell cannot create a tenant (the self modes need the IAM access cookie, the SUPER_ADMIN mode the
 * operator token — a sample visitor has neither), so it says so and offers 로그인 back to here. It used to
 * `redirect('/')`, which — with the sidebar item still visible — read as a menu that silently did nothing.
 * No network call, no form mounted.
 */
export default async function NewTenantPage() {
  if (await isSampleVisitor()) {
    return (
      <NewTenantFrame intro="회사(테넌트)를 만들고 그 관리자가 되는 화면입니다.">
        <div
          role="status"
          data-testid="new-tenant-sample-notice"
          className="rounded-lg border border-border bg-muted/40 p-6 text-sm text-foreground"
        >
          <p className="font-medium">샘플 콘솔에서는 테넌트를 만들 수 없습니다.</p>
          <p className="mt-2 text-muted-foreground">
            로그인하면 이 화면에서 새 테넌트를 만들고, 받은 초대를 확인할 수 있습니다.
          </p>
          <div className="mt-4 flex flex-wrap gap-3">
            <Link
              href={buildLoginRedirectFor('/new-tenant')}
              prefetch={false}
              data-testid="new-tenant-sample-login"
              className="rounded-md bg-primary px-4 py-2 font-medium text-primary-foreground hover:bg-primary/90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            >
              로그인
            </Link>
            <Link
              href="/dashboards/overview"
              data-testid="new-tenant-sample-overview"
              className="rounded-md border border-border px-4 py-2 font-medium hover:bg-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            >
              개요로 돌아가기
            </Link>
          </div>
        </div>
      </NewTenantFrame>
    );
  }

  const emailVerificationUrl = `${getServerEnv().OIDC_ISSUER_URL}/email-verification`;

  if (await isNonOperatorSession()) {
    const accessToken = (await getAccessToken()) ?? '';
    const received = await getReceivedInvitationsState(accessToken);
    return (
      <NewTenantFrame
        intro="로그인은 됐지만 아직 운영자로 소속된 회사가 없습니다. 회사에 이미 관리자가 있다면 초대를 받아 들어가고, 없다면 새 테넌트를 만들어 그 관리자가 되세요."
      >
        <ReceivedInvitations state={received} emailVerificationUrl={emailVerificationUrl} />
        <p
          data-testid="new-tenant-ask-admin"
          className="rounded-md border border-border bg-muted/40 px-4 py-3 text-sm text-muted-foreground"
        >
          회사에 관리자가 있다면 그 관리자에게 초대를 요청하세요. 초대 메일의 링크로 수락하면 그 회사의 운영자가
          됩니다.
        </p>
        <div className="rounded-lg border border-border bg-background p-6">
          <h2 className="mb-4 text-base font-semibold text-foreground">새 테넌트 만들기</h2>
          <CreateOrganizationForm mode="first" emailVerificationUrl={emailVerificationUrl} />
        </div>
      </NewTenantFrame>
    );
  }

  const me = await getSelfOrNull();
  const platformTenantManager =
    me !== null && me.homeTenantId === '*' && hasPermission(me.roles, 'tenant.manage');

  if (platformTenantManager) {
    return (
      <NewTenantFrame intro="플랫폼 관리자는 테넌트를 만들고 첫 관리자를 이메일로 초대합니다. 첫 관리자가 초대를 수락하면 테넌트가 활성화됩니다.">
        <TenantCreateScreen orgNodeOptions={await orgNodeOptionsOrNull()} />
      </NewTenantFrame>
    );
  }

  return (
    <NewTenantFrame intro="새 회사(테넌트)를 하나 더 만들고 그 관리자가 됩니다. 지금 소속된 회사는 그대로입니다.">
      {me === null && (
        <p data-testid="new-tenant-platform-hint" className="text-xs text-muted-foreground">
          플랫폼 관리자라면 상단 스위처에서 테넌트를 고른 뒤 이 화면을 다시 열면 첫 관리자 초대 양식이 열립니다.
        </p>
      )}
      <div className="rounded-lg border border-border bg-background p-6">
        <CreateOrganizationForm mode="additional" emailVerificationUrl={emailVerificationUrl} />
      </div>
    </NewTenantFrame>
  );
}

/** Heading + intro shared by every mode (the mode-specific blocks stay in its `children`). */
function NewTenantFrame({ intro, children }: { intro: string; children: React.ReactNode }) {
  return (
    <section aria-labelledby="new-tenant-heading" className="max-w-2xl space-y-6">
      <div>
        <h1 id="new-tenant-heading" data-testid="new-tenant-heading" className="text-2xl font-semibold">
          테넌트 생성
        </h1>
        <p className="mt-2 text-sm text-muted-foreground">{intro}</p>
      </div>
      {children}
    </section>
  );
}

/**
 * «소속 노드 (선택)» choices for the SUPER_ADMIN form (TASK-PC-FE-312 — moved here with the form). Fail-soft:
 * any failure → `null`; the form still creates unplaced tenants.
 */
async function orgNodeOptionsOrNull(): Promise<TenantOrgNodeOption[] | null> {
  try {
    const { items } = await listOrgNodes();
    return items.map((n) => ({ orgNodeId: n.orgNodeId, name: n.name }));
  } catch {
    return null;
  }
}
