import Link from 'next/link';
import type { PublicArtist } from '@demo/public-data';
import { FEED_VISIBILITIES, isFeedFiltered, type FeedFilter, type FeedVisibility } from '../lib/select';

const VISIBILITY_LABEL: Record<FeedVisibility, string> = {
  PUBLIC: '공개',
  MEMBERS_ONLY: '멤버 전용',
  PREMIUM: '프리미엄',
};

const FIELD =
  'rounded-md border border-ink-200 bg-white px-3 py-2 text-sm focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-200 dark:bg-ink-900';

/**
 * 공개 피드 필터 — **서버 렌더 GET 폼**이다(TASK-FAN-FE-025).
 *
 * 🔴 `'use client'` 가 없고 상태도 없다. 제출은 브라우저의 평범한 GET 이동이고, 결과는
 *    서버가 저장본에서 다시 그린다. 클라이언트 컴포넌트로 만들면 세션·알림 모듈이 딸려
 *    와서 익명 방문의 «게이트웨이 호출 0» 불변식이 흔들릴 수 있다(`TASK-FAN-FE-024` 가 지킨 축).
 *
 * 🔵 폼에 `page` 가 없다 — 필터를 바꾸면 첫 페이지로 돌아간다.
 * 🔵 선택 상태는 쿼리스트링 원문이 아니라 **적용된** 필터(`resolveFeedFilter`)에서 온다.
 */
export function PublicFeedFilter({
  artists,
  filter,
}: {
  artists: readonly PublicArtist[];
  filter: FeedFilter;
}) {
  const sorted = artists.slice().sort((a, b) => a.stageName.localeCompare(b.stageName, 'ko'));

  return (
    <form
      action="/"
      method="get"
      autoComplete="off"
      role="search"
      aria-label="공개 피드 필터"
      className="mb-6 flex flex-wrap items-end gap-2"
      data-testid="public-feed-filter"
    >
      <label className="flex flex-col gap-1 text-xs text-ink-600">
        아티스트
        <select name="artist" defaultValue={filter.artistId ?? ''} className={FIELD}>
          <option value="">전체</option>
          {sorted.map((a) => (
            <option key={a.id} value={a.id}>
              {a.stageName}
            </option>
          ))}
        </select>
      </label>
      <label className="flex flex-col gap-1 text-xs text-ink-600">
        공개 범위
        <select name="visibility" defaultValue={filter.visibility ?? ''} className={FIELD}>
          <option value="">전체</option>
          {FEED_VISIBILITIES.map((v) => (
            <option key={v} value={v}>
              {VISIBILITY_LABEL[v]}
            </option>
          ))}
        </select>
      </label>
      <label className="flex min-w-0 flex-1 flex-col gap-1 text-xs text-ink-600 sm:max-w-xs">
        검색
        <input
          name="q"
          type="search"
          defaultValue={filter.q ?? ''}
          placeholder="제목 또는 아티스트 이름..."
          className={FIELD}
        />
      </label>
      <button
        type="submit"
        className="rounded-md bg-brand-600 px-3 py-2 text-sm font-medium text-white hover:bg-brand-700"
      >
        적용
      </button>
      {isFeedFiltered(filter) ? (
        <Link
          href="/"
          className="px-2 py-2 text-sm text-ink-600 underline hover:text-ink-900"
          data-testid="public-feed-filter-reset"
        >
          필터 해제
        </Link>
      ) : null}
    </form>
  );
}
