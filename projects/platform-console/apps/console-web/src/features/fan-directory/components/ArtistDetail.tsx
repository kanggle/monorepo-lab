'use client';

import { useId, useState } from 'react';
import { Button } from '@/shared/ui/Button';
import { StatusBadge } from '@/shared/ui/StatusBadge';
import { ConfirmDialog } from '@/shared/ui/ConfirmDialog';
import { DetailHeader } from '@/shared/ui/DetailHeader';
import {
  useArtist,
  useChangeArtistAgency,
  useChangeArtistStatus,
  useUpdateArtist,
} from '../hooks/use-fan-directory';
import { artistStatusTone, fanErrorMessage } from '../lib/fan-messages';
import type { Artist } from '../api/fan-types';
import { AffiliationEditor } from './AffiliationEditor';
import { FanError, FanField, inputCls, labelCls } from './fan-ui';

/**
 * Artist detail (TASK-MONO-751): profile edit · affiliation · publish / archive.
 * Directory fields only — the operator never authors as the artist (ADR-MONO-059).
 */
export function ArtistDetail({ artist: initial }: { artist: Artist }) {
  const q = useArtist(initial.id, initial);
  const artist = q.data ?? initial;
  const archived = artist.status === 'ARCHIVED';
  const changeAgency = useChangeArtistAgency(artist.id);

  return (
    <section aria-labelledby="fan-artist-detail-heading" className="space-y-8">
      <DetailHeader
        headingId="fan-artist-detail-heading"
        title="아티스트 상세"
        backHref="/fan/artists"
        backTestId="fan-artist-detail-back"
      />
      <dl data-testid="fan-artist-detail" className="max-w-2xl">
        <FanField label="활동명">
          <span data-testid="fan-artist-detail-stage">{artist.stageName}</span>
        </FanField>
        <FanField label="상태">
          <StatusBadge tone={artistStatusTone(artist.status)}>
            <span data-testid="fan-artist-detail-status">{artist.status}</span>
          </StatusBadge>
        </FanField>
        <FanField label="유형">{artist.artistType}</FanField>
        <FanField label="소속사">
          <span data-testid="fan-artist-detail-agency">{artist.agency ?? '소속 없음'}</span>
        </FanField>
        <FanField label="계정 ID">
          <code className="text-xs">{artist.accountId ?? '—'}</code>
        </FanField>
        <FanField label="ID">
          <code className="text-xs">{artist.id}</code>
        </FanField>
      </dl>

      <StatusSection artist={artist} />
      <AffiliationEditor
        key={artist.agencyId ?? 'none'}
        testIdPrefix="fan-artist"
        currentAgencyId={artist.agencyId}
        currentAgencyName={artist.agency}
        disabled={archived}
        pending={changeAgency.isPending}
        onSave={(agencyId, done) =>
          changeAgency.mutate(agencyId, { onSuccess: () => done(), onError: (err) => done(err) })
        }
      />
      {!archived && <ProfileSection artist={artist} />}
    </section>
  );
}

function StatusSection({ artist }: { artist: Artist }) {
  const [target, setTarget] = useState<'PUBLISHED' | 'ARCHIVED' | null>(null);
  const [error, setError] = useState<string | null>(null);
  const change = useChangeArtistStatus(artist.id);
  const canPublish = artist.status === 'DRAFT';
  const canArchive = artist.status === 'DRAFT' || artist.status === 'PUBLISHED';
  if (!canPublish && !canArchive) return null;

  return (
    <section className="max-w-2xl space-y-3" aria-labelledby="fan-artist-status-heading">
      <h2 id="fan-artist-status-heading" className="text-lg font-medium">
        상태
      </h2>
      <div className="flex gap-3">
        {canPublish && (
          <Button onClick={() => setTarget('PUBLISHED')} data-testid="fan-artist-publish">
            공개
          </Button>
        )}
        {canArchive && (
          <Button variant="secondary" onClick={() => setTarget('ARCHIVED')} data-testid="fan-artist-archive">
            보관
          </Button>
        )}
      </div>
      <ConfirmDialog
        open={target !== null}
        title={target === 'PUBLISHED' ? '아티스트를 공개할까요?' : '아티스트를 보관할까요?'}
        description={
          target === 'PUBLISHED'
            ? `"${artist.stageName}" 이(가) 팬 디렉터리에 보이게 됩니다.`
            : `"${artist.stageName}" 을(를) 보관합니다. 되돌릴 수 없습니다.`
        }
        confirmLabel={target === 'PUBLISHED' ? '공개' : '보관'}
        destructive={target === 'ARCHIVED'}
        pending={change.isPending}
        errorMessage={error}
        dialogTestId="fan-confirm-dialog"
        cancelTestId="fan-confirm-cancel"
        confirmTestId="fan-confirm-confirm"
        errorTestId="fan-confirm-error"
        onConfirm={() => {
          if (!target) return;
          change.mutate(
            { status: target },
            {
              onSuccess: () => setTarget(null),
              onError: (err) => setError(fanErrorMessage(err, '상태를 바꾸지 못했습니다.')),
            },
          );
        }}
        onCancel={() => {
          setTarget(null);
          setError(null);
        }}
      />
    </section>
  );
}

function ProfileSection({ artist }: { artist: Artist }) {
  const ids = { stage: useId(), real: useId(), debut: useId(), bio: useId() };
  const [stageName, setStageName] = useState(artist.stageName);
  const [realName, setRealName] = useState(artist.realName ?? '');
  const [debutDate, setDebutDate] = useState(artist.debutDate ?? '');
  const [bio, setBio] = useState(artist.bio ?? '');
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const update = useUpdateArtist(artist.id);
  const valid = stageName.trim() !== '' && stageName.trim().length <= 120;

  return (
    <section className="max-w-2xl space-y-3" aria-labelledby="fan-artist-profile-heading">
      <h2 id="fan-artist-profile-heading" className="text-lg font-medium">
        프로필 수정
      </h2>
      <p className="text-sm text-muted-foreground">
        비운 칸은 «바꾸지 않음» 으로 보냅니다(PATCH). 계정 ID 는 바꿀 수 없습니다.
      </p>
      <div>
        <label htmlFor={ids.stage} className={labelCls}>
          활동명
        </label>
        <input id={ids.stage} value={stageName} onChange={(e) => setStageName(e.target.value)} maxLength={120} className={inputCls} data-testid="fan-artist-edit-stage" />
      </div>
      <div>
        <label htmlFor={ids.real} className={labelCls}>
          본명
        </label>
        <input id={ids.real} value={realName} onChange={(e) => setRealName(e.target.value)} maxLength={120} className={inputCls} data-testid="fan-artist-edit-real" />
      </div>
      <div>
        <label htmlFor={ids.debut} className={labelCls}>
          데뷔일
        </label>
        <input id={ids.debut} type="date" value={debutDate} onChange={(e) => setDebutDate(e.target.value)} className={inputCls} data-testid="fan-artist-edit-debut" />
      </div>
      <div>
        <label htmlFor={ids.bio} className={labelCls}>
          소개
        </label>
        <textarea id={ids.bio} value={bio} onChange={(e) => setBio(e.target.value)} maxLength={4000} rows={4} className={inputCls} data-testid="fan-artist-edit-bio" />
      </div>
      <Button
        disabled={!valid || update.isPending}
        onClick={() => {
          setError(null);
          setSaved(false);
          update.mutate(
            {
              stageName: stageName.trim(),
              realName: realName.trim() || undefined,
              debutDate: debutDate || undefined,
              bio: bio.trim() || undefined,
            },
            {
              onSuccess: () => setSaved(true),
              onError: (err) => setError(fanErrorMessage(err, '프로필을 저장하지 못했습니다.')),
            },
          );
        }}
        data-testid="fan-artist-edit-submit"
      >
        저장
      </Button>
      {saved && (
        <p className="text-sm text-muted-foreground" data-testid="fan-artist-edit-saved">
          저장했습니다.
        </p>
      )}
      <FanError testId="fan-artist-edit-error" message={error} />
    </section>
  );
}
