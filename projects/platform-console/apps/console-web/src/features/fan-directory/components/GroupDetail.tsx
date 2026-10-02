'use client';

import Link from 'next/link';
import { StatusBadge } from '@/shared/ui/StatusBadge';
import { DetailHeader } from '@/shared/ui/DetailHeader';
import { formatDateTime } from '@/shared/lib/datetime';
import { useArtistGroup, useChangeGroupAgency } from '../hooks/use-fan-directory';
import type { ArtistGroup } from '../api/fan-types';
import { AffiliationEditor } from './AffiliationEditor';
import { FanField } from './fan-ui';

/**
 * Artist group detail (TASK-MONO-751): fields · members (read) · affiliation change.
 * Roster edits (add/remove member) are not in this ticket's scope (AC: «소속 변경»).
 */
export function GroupDetail({ group: initial }: { group: ArtistGroup }) {
  const q = useArtistGroup(initial.id, initial);
  const group = q.data ?? initial;
  const changeAgency = useChangeGroupAgency(group.id);
  const archived = group.status === 'ARCHIVED';

  return (
    <section aria-labelledby="fan-group-detail-heading" className="space-y-8">
      <DetailHeader
        headingId="fan-group-detail-heading"
        title="아티스트 그룹 상세"
        backHref="/fan/groups"
        backTestId="fan-group-detail-back"
      />
      <dl data-testid="fan-group-detail" className="max-w-2xl">
        <FanField label="이름">
          <span data-testid="fan-group-detail-name">{group.name}</span>
        </FanField>
        <FanField label="상태">
          <StatusBadge tone={archived ? 'neutral' : 'success'}>{group.status}</StatusBadge>
        </FanField>
        <FanField label="소속사">
          <span data-testid="fan-group-detail-agency">{group.agency ?? '소속 없음'}</span>
        </FanField>
        <FanField label="데뷔일">{group.debutDate ?? '—'}</FanField>
        <FanField label="ID">
          <code className="text-xs">{group.id}</code>
        </FanField>
      </dl>

      <section className="max-w-2xl" aria-labelledby="fan-group-members-heading">
        <h2 id="fan-group-members-heading" className="mb-2 text-lg font-medium">
          멤버
        </h2>
        {group.members.length === 0 ? (
          <p className="text-sm text-muted-foreground" data-testid="fan-group-members-empty">
            멤버가 없습니다.
          </p>
        ) : (
          <table className="data-table" data-testid="fan-group-members">
            <caption className="sr-only">그룹 멤버</caption>
            <thead>
              <tr className="border-b border-border text-left">
                <th scope="col" className="p-2">아티스트</th>
                <th scope="col" className="p-2">역할</th>
                <th scope="col" className="p-2">합류</th>
              </tr>
            </thead>
            <tbody>
              {group.members.map((m) => (
                <tr key={`${m.artistId}-${m.joinedAt ?? ''}`} className="border-b border-border">
                  <td className="p-2">
                    <Link className="underline" href={`/fan/artists/${encodeURIComponent(m.artistId)}`}>
                      <code className="text-xs">{m.artistId}</code>
                    </Link>
                  </td>
                  <td className="p-2 text-xs">{m.role}</td>
                  <td className="p-2 text-sm text-muted-foreground">
                    {m.joinedAt ? formatDateTime(m.joinedAt) : '—'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      <AffiliationEditor
        key={group.agencyId ?? 'none'}
        testIdPrefix="fan-group"
        currentAgencyId={group.agencyId}
        currentAgencyName={group.agency}
        disabled={archived}
        pending={changeAgency.isPending}
        onSave={(agencyId, done) =>
          changeAgency.mutate(agencyId, { onSuccess: () => done(), onError: (err) => done(err) })
        }
      />
    </section>
  );
}
