import type { ReactNode } from 'react';
import { DomainTenantGate } from '@/widgets/domain-tenant-gate';

/**
 * TASK-MONO-751 (ADR-MONO-079 D4-A) — the fan-directory section opens only once a tenant is
 * assumed AND that tenant is one the `fan` product serves (`fan-platform`). This is the
 * ticket's «전환 뒤에 묻는다» rule (Failure Scenario 1): before the switch the domain-facing
 * token is the base login token (`tenant_id=iam`), which artist-service refuses
 * `403 TENANT_FORBIDDEN`; with another tenant assumed (e.g. `demo-corp`) it is refused the
 * same way. `productKey="fan"` makes the gate say «switch to fan-platform» instead of letting
 * the page make that call.
 */
export default function FanSectionLayout({ children }: { children: ReactNode }) {
  return (
    <DomainTenantGate section="팬 디렉터리" productKey="fan">
      {children}
    </DomainTenantGate>
  );
}
