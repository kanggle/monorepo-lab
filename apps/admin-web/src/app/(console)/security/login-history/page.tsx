import { AuditTable } from '@/features/audit/components/AuditTable';

export const metadata = { title: '로그인 이력 — Admin Console' };

export default function LoginHistoryPage() {
  return (
    <section className="flex flex-col gap-4">
      <h1 className="text-xl font-semibold">로그인 이력</h1>
      <p className="text-sm text-muted-foreground">
        소스를 login_history로 필터링하여 조회하세요.
      </p>
      <AuditTable defaultSource="login_history" />
    </section>
  );
}
