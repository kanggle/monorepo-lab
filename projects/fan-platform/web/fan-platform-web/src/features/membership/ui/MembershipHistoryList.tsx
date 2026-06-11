import type { MembershipListItem } from '@/entities/membership';
import { historyStatus, HISTORY_LABEL, HISTORY_BADGE } from './historyStatus';

const TIER_LABEL: Record<MembershipListItem['tier'], string> = {
  MEMBERS_ONLY: '멤버스 전용',
  PREMIUM: '프리미엄',
};

function fmt(date: string): string {
  return new Date(date).toLocaleDateString('ko-KR');
}

/**
 * Read-only membership history. Presentational (no client directive — it renders
 * in the Server Component and takes no action); receives only plain data.
 */
export function MembershipHistoryList({
  memberships,
}: {
  memberships: MembershipListItem[];
}) {
  return (
    <ul className="flex flex-col gap-3" data-testid="membership-history">
      {memberships.map((m) => {
        const status = historyStatus(m);
        return (
          <li
            key={m.membershipId}
            data-testid="history-row"
            className="rounded-xl border border-ink-200 bg-white p-4"
          >
            <div className="flex items-center gap-2">
              <span className="text-base font-semibold text-ink-900">{TIER_LABEL[m.tier]}</span>
              <span
                className={[
                  'rounded-full px-2 py-0.5 text-xs font-medium',
                  HISTORY_BADGE[status],
                ].join(' ')}
              >
                {HISTORY_LABEL[status]}
              </span>
              <span className="ml-auto text-xs text-ink-400">{fmt(m.createdAt)} 가입</span>
            </div>
            <p className="mt-2 text-sm text-ink-600">
              {fmt(m.validFrom)} ~ {fmt(m.validTo)} · {m.planMonths}개월
            </p>
          </li>
        );
      })}
    </ul>
  );
}
