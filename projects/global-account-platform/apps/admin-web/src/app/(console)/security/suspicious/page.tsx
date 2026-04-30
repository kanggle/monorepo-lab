import { AuditTable } from '@/features/audit/components/AuditTable';

export const metadata = { title: '의심 이벤트 — Admin Console' };

export default function SuspiciousPage() {
  return (
    <section className="flex flex-col gap-4">
      <h1 className="text-xl font-semibold">의심 이벤트</h1>
      <AuditTable defaultSource="suspicious" />
    </section>
  );
}
