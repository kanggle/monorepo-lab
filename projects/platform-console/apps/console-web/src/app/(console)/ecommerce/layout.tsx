import type { ReactNode } from 'react';
import { DomainTenantGate } from '@/widgets/domain-tenant-gate';

/**
 * TASK-PC-FE-292 — this section opens only once a tenant is assumed; until then
 * it shows «테넌트를 먼저 선택하세요» instead of a gateway 401 read as «세션 만료».
 * See {@link DomainTenantGate}.
 *
 * TASK-MONO-718 (owner decision ⓑ) — `productKey` additionally asks whether the
 * assumed tenant is one the ecommerce product actually serves. It is passed HERE
 * and nowhere else: the 2026-09-22 window measured ecommerce as the only section
 * whose data lives outside `demo-corp` (wms · scm · erp · finance all rendered
 * under it), and the ticket's § 제외 forbids widening that to «a shared problem»
 * on evidence nobody has.
 */
export default function EcommerceSectionLayout({ children }: { children: ReactNode }) {
  return (
    <DomainTenantGate section="E-Commerce" productKey="ecommerce">
      {children}
    </DomainTenantGate>
  );
}
