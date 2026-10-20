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
 * Gates, in order: no active tenant → tenant gate · otherwise the control
 * (its own 403 / 503 render inline — the page never crashes). There is no
 * `*` branch: the active tenant is always a registry tenant — the switcher
 * never offers `*` (TASK-PC-FE-353 AC-0 ①), so such a branch could not run.
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
