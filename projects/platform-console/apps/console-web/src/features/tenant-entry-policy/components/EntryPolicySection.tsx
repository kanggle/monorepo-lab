import { EntryPolicyPanel } from './EntryPolicyPanel';
import type { EntryPolicyState } from '../api/entry-policy-state';

/**
 * Server-renderable wrapper: picks what the entry-policy control shows for an
 * SSR {@link EntryPolicyState} (TASK-MONO-771 S5 — § 2.4.3.3 resilience):
 * `403` → inline «권한 없음» (no toggle — hidden, the producer stays the
 * authority) · `503`/timeout → a degraded note for THIS control only · otherwise
 * the {@link EntryPolicyPanel}. `noTenant` renders nothing (the page owns that gate).
 */
export function EntryPolicySection({
  tenantId,
  state,
}: {
  tenantId: string;
  state: EntryPolicyState;
}) {
  if (state.noTenant) return null;

  if (state.permissionError) {
    return (
      <div
        role="status"
        data-testid="entry-policy-permission-denied"
        className="mt-6 rounded-md border border-border bg-muted px-4 py-4 text-sm text-muted-foreground"
      >
        {state.permissionError.code === 'TENANT_SCOPE_DENIED'
          ? '이 테넌트의 운영자 진입 정책을 볼 권한이 없습니다 (자기 테넌트만 관리할 수 있습니다).'
          : '운영자 진입 2단계 인증 설정은 tenant.security.manage 권한이 필요합니다 (SUPER_ADMIN 또는 그 테넌트의 TENANT_ADMIN).'}
      </div>
    );
  }

  if (state.degraded || !state.policy) {
    return (
      <div
        role="status"
        data-testid="entry-policy-degraded"
        className="mt-6 rounded-md border border-border bg-muted px-4 py-4 text-sm text-muted-foreground"
      >
        운영자 진입 정책을 일시적으로 불러올 수 없습니다. 이 화면의 다른 기능은 계속 사용할 수 있습니다.
      </div>
    );
  }

  return (
    <EntryPolicyPanel
      tenantId={tenantId}
      initial={state.policy}
      selfHasSecondFactor={state.selfHasSecondFactor}
    />
  );
}
