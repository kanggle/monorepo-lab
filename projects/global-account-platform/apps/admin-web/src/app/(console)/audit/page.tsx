import { AuditTabs } from '@/features/audit/components/AuditTabs';

export const metadata = { title: '감사 로그 — Admin Console' };

export default function AuditPage() {
  return (
    <section className="flex flex-col gap-4">
      <h1 className="text-xl font-semibold">감사 로그</h1>
      <AuditTabs />
    </section>
  );
}
