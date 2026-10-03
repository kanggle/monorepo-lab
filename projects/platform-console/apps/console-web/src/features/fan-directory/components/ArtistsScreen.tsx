'use client';

import { useId, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { Button } from '@/shared/ui/Button';
import { StatusBadge } from '@/shared/ui/StatusBadge';
import { ApiError } from '@/shared/api/errors';
import { useArtists } from '../hooks/use-fan-directory';
import { artistStatusTone } from '../lib/fan-messages';
import type { Artist, Paged } from '../api/fan-types';
import { FanNote, inputCls, labelCls } from './fan-ui';

/**
 * Fan artists list (TASK-MONO-751). 🔴 The producer's directory search returns PUBLISHED
 * artists only — a DRAFT (every freshly registered artist) and an ARCHIVED one are reached
 * by id. The screen says so and offers «ID로 열기» rather than letting a new artist look lost.
 */
export function ArtistsScreen({ artists }: { artists: Paged<Artist> }) {
  const router = useRouter();
  const searchId = useId();
  const openId = useId();
  const [page, setPage] = useState(0);
  const [draftQ, setDraftQ] = useState('');
  const [q, setQ] = useState('');
  const [openValue, setOpenValue] = useState('');
  const query = useArtists({ page, q }, page === 0 && q === '' ? artists : undefined);
  const data = page === 0 && q === '' ? query.data ?? artists : query.data;
  const apiError = query.error instanceof ApiError ? query.error : null;
  const forbidden = apiError?.status === 403;
  const degraded = query.isError && !forbidden;
  const rows = data?.content ?? [];
  const totalPages = Math.max(1, data?.totalPages ?? 1);

  return (
    <section aria-labelledby="fan-artists-heading">
      <div className="mb-2 flex items-center justify-between">
        <h1 id="fan-artists-heading" className="text-2xl font-semibold">
          아티스트
        </h1>
        <Link href="/fan/artists/new" data-testid="fan-artist-new-link">
          <Button>아티스트 등록</Button>
        </Link>
      </div>
      <p className="mb-4 text-sm text-muted-foreground">
        목록은 <strong>공개(PUBLISHED)</strong> 아티스트만 보여 줍니다(팬 디렉터리 검색의 규칙).
        새로 등록한 아티스트는 초안(DRAFT)이라 여기 없고, 등록 직후 상세 화면으로 이동합니다 —
        초안·보관 아티스트는 아래 «ID로 열기» 로 엽니다.
      </p>

      <div className="mb-6 flex flex-wrap items-end gap-6">
        <form
          className="flex items-end gap-2"
          onSubmit={(e) => {
            e.preventDefault();
            setPage(0);
            setQ(draftQ.trim());
          }}
        >
          <div>
            <label htmlFor={searchId} className={labelCls}>
              활동명 검색
            </label>
            <input
              id={searchId}
              value={draftQ}
              onChange={(e) => setDraftQ(e.target.value)}
              className={inputCls}
              data-testid="fan-artist-search"
            />
          </div>
          <Button type="submit" variant="secondary" data-testid="fan-artist-search-submit">
            검색
          </Button>
        </form>
        <form
          className="flex items-end gap-2"
          onSubmit={(e) => {
            e.preventDefault();
            const v = openValue.trim();
            if (v) router.push(`/fan/artists/${encodeURIComponent(v)}`);
          }}
        >
          <div>
            <label htmlFor={openId} className={labelCls}>
              ID로 열기
            </label>
            <input
              id={openId}
              value={openValue}
              onChange={(e) => setOpenValue(e.target.value)}
              placeholder="아티스트 ID"
              className={inputCls}
              data-testid="fan-artist-open-id"
            />
          </div>
          <Button type="submit" variant="secondary" data-testid="fan-artist-open-submit">
            열기
          </Button>
        </form>
      </div>

      {forbidden ? (
        <FanNote testId="fan-artist-forbidden">
          이 화면은 fan-platform 테넌트로 전환한 플랫폼 운영자만 쓸 수 있습니다.
        </FanNote>
      ) : degraded ? (
        <FanNote testId="fan-artist-degraded">
          아티스트 정보를 일시적으로 불러올 수 없습니다. 콘솔의 다른 기능은 계속 사용할 수 있습니다.
        </FanNote>
      ) : data === undefined ? (
        <p className="text-sm text-muted-foreground" data-testid="fan-artist-loading">
          조회 중…
        </p>
      ) : rows.length === 0 ? (
        <p className="text-sm text-muted-foreground" data-testid="fan-artist-empty">
          표시할 공개 아티스트가 없습니다.
        </p>
      ) : (
        <>
          <table className="mb-3 data-table" data-testid="fan-artist-table">
            <caption className="sr-only">아티스트 목록</caption>
            <thead>
              <tr className="border-b border-border text-left">
                <th scope="col" className="p-2">활동명</th>
                <th scope="col" className="p-2">유형</th>
                <th scope="col" className="p-2">상태</th>
                <th scope="col" className="p-2">소속사</th>
                <th scope="col" className="p-2">작업</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((a, i) => (
                <tr key={a.id} data-testid={`fan-artist-row-${i}`} className="border-b border-border">
                  <td className="p-2">{a.stageName}</td>
                  <td className="p-2 text-xs">{a.artistType}</td>
                  <td className="p-2">
                    <StatusBadge tone={artistStatusTone(a.status)}>{a.status}</StatusBadge>
                  </td>
                  <td className="p-2">{a.agency ?? '—'}</td>
                  <td className="p-2">
                    <Link href={`/fan/artists/${encodeURIComponent(a.id)}`}>
                      <Button variant="secondary" size="sm" data-testid={`fan-artist-detail-${i}`}>
                        상세
                      </Button>
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <nav className="flex items-center justify-between" aria-label="아티스트 페이지 이동">
            <Button
              variant="secondary"
              disabled={page <= 0}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              data-testid="fan-artist-prev"
            >
              이전
            </Button>
            <span className="text-sm text-muted-foreground" data-testid="fan-artist-pageinfo">
              {(data.page ?? 0) + 1} / {totalPages} 페이지 · 총 {data.totalElements}건
            </span>
            <Button
              variant="secondary"
              disabled={(data.page ?? 0) + 1 >= totalPages}
              onClick={() => setPage((p) => p + 1)}
              data-testid="fan-artist-next"
            >
              다음
            </Button>
          </nav>
        </>
      )}
    </section>
  );
}
