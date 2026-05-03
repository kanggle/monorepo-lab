import { getFanSession } from '@/shared/auth/session';

export default async function MyPage() {
  const session = await getFanSession();
  return (
    <section>
      <header className="mb-6">
        <h1 className="text-2xl font-bold text-ink-900">내 정보</h1>
        <p className="text-sm text-ink-600">GAP 토큰에 포함된 클레임을 확인합니다.</p>
      </header>
      <dl className="grid gap-3 rounded-xl border border-ink-200 bg-white p-6 text-sm">
        <div className="flex items-center gap-2">
          <dt className="w-24 font-medium text-ink-500">tenant_id</dt>
          <dd className="rounded bg-brand-50 px-2 py-0.5 font-mono text-brand-700">
            {session.tenantId ?? '—'}
          </dd>
        </div>
        <div className="flex items-center gap-2">
          <dt className="w-24 font-medium text-ink-500">account_id</dt>
          <dd className="font-mono text-ink-700">{session.accountId ?? '—'}</dd>
        </div>
        <div className="flex items-start gap-2">
          <dt className="w-24 font-medium text-ink-500">roles</dt>
          <dd className="flex flex-wrap gap-1">
            {(session.roles ?? []).length === 0 ? (
              <span className="text-ink-500">—</span>
            ) : (
              session.roles.map((r) => (
                <span
                  key={r}
                  className="rounded-full bg-ink-100 px-2 py-0.5 text-xs text-ink-700"
                >
                  {r}
                </span>
              ))
            )}
          </dd>
        </div>
      </dl>
    </section>
  );
}
