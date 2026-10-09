import Link from 'next/link';
import { getActiveTenant } from '@/shared/lib/session';
import { EntryPolicySection, getEntryPolicyState } from '@/features/tenant-entry-policy';
import { NoTenantNotice } from '@/widgets/no-tenant-notice';

export const dynamic = 'force-dynamic';

/**
 * «보안 설정» (TASK-MONO-771 S5 — console-integration-contract § 2.4.3.3, owner
 * decision OD-1): a TENANT_ADMIN turns «이 테넌트에 운영자로 들어올 때 2단계 인증
 * 필수» on or off for the ACTIVE tenant (path `tenantId` = the active tenant).
 * The producer (`tenant.security.manage` + path scope) is the authority; the
 * nav hides this entry for roles whose seed matrix lacks the key.
 *
 * Gates, in order: no active tenant → tenant gate · platform scope `*` → a
 * pointer to 테넌트 상세 (`'*'` cannot hold a policy) · otherwise the control
 * (its own 403 / 503 render inline — the page never crashes).
 */
export default async function SecuritySettingsPage() {
  const heading = (
    <h1 id="security-settings-heading" className="mb-6 text-2xl font-semibold">
      보안 설정
    </h1>
  );
  const tenant = await getActiveTenant();

  if (!tenant) {
    return (
      <section aria-labelledby="security-settings-heading">
        {heading}
        {await NoTenantNotice({
          testId: 'security-settings-no-tenant',
          description: (
            <>
              보안 설정은 활성 테넌트에 적용됩니다. 상단의 테넌트 스위처에서 관리할
              테넌트를 선택한 뒤 다시 시도하세요.
            </>
          ),
        })}
      </section>
    );
  }

  if (tenant === '*') {
    return (
      <section aria-labelledby="security-settings-heading">
        {heading}
        <div
          role="status"
          data-testid="security-settings-platform-scope"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          플랫폼 스코프(*)에는 진입 정책이 없습니다 — 플랫폼 운영자의 2단계 인증은 역할이 요구합니다.
          특정 테넌트의 정책은{' '}
          <Link href="/tenants" className="underline">
            테넌트
          </Link>{' '}
          상세에서 바꾸거나, 테넌트 스위처로 그 테넌트를 선택하세요.
        </div>
      </section>
    );
  }

  const state = await getEntryPolicyState(tenant);
  if (state.noTenant) {
    return (
      <section aria-labelledby="security-settings-heading">
        {heading}
        {await NoTenantNotice({
          testId: 'security-settings-no-tenant',
          description: <>테넌트를 먼저 선택해주세요.</>,
        })}
      </section>
    );
  }

  return (
    <section aria-labelledby="security-settings-heading" data-testid="security-settings">
      {heading}
      <p className="text-sm text-muted-foreground" data-testid="security-settings-tenant">
        대상 테넌트: <span className="font-mono">{tenant}</span>
      </p>
      <EntryPolicySection tenantId={tenant} state={state} />
    </section>
  );
}
