import type { ReactNode } from 'react';
import { DomainTenantGate } from '@/widgets/domain-tenant-gate';

/**
 * TASK-PC-FE-292 — this section opens only once a tenant is assumed; until then
 * it shows «테넌트를 먼저 선택하세요» instead of a gateway 401 read as «세션 만료».
 * See {@link DomainTenantGate}.
 */
export default function LedgerSectionLayout({ children }: { children: ReactNode }) {
  return <DomainTenantGate section="원장">{children}</DomainTenantGate>;
}
