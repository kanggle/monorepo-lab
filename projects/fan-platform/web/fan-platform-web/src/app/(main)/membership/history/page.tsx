import Link from 'next/link';
import { getFanSession } from '@/shared/auth/session';
import { getMemberships, MembershipHistoryList } from '@/features/membership';
import { EmptyState } from '@/shared/ui/EmptyState';

/**
 * Membership history — every membership the caller has held (subscribe / renew /
 * expire / cancel), newest window first. Read-only Server Component: the access
 * token stays on the server; the list rows are presentational (no action).
 */
export default async function MembershipHistoryPage() {
  const session = await getFanSession();
  const memberships = await getMemberships(session.accessToken);

  return (
    <section className="flex flex-col gap-6">
      <header className="flex flex-col gap-1">
        <Link href="/membership" className="text-sm font-medium text-brand-600 hover:text-brand-700">
          ← 멤버십
        </Link>
        <h1 className="text-2xl font-bold text-ink-900">멤버십 이력</h1>
        <p className="text-sm text-ink-600">지금까지의 구독·갱신·만료·해지 내역입니다.</p>
      </header>

      {memberships.length === 0 ? (
        <EmptyState
          title="멤버십 이력이 없습니다"
          description="아직 구독한 멤버십이 없어요."
          action={
            <Link href="/membership" className="text-sm font-medium text-brand-600 hover:text-brand-700">
              멤버십 둘러보기
            </Link>
          }
        />
      ) : (
        <MembershipHistoryList memberships={memberships} />
      )}
    </section>
  );
}
