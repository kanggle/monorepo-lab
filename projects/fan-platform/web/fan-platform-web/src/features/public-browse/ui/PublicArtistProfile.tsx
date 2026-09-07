import type { PublicArtist } from '@demo/public-data';

/**
 * 공개 아티스트 프로필.
 *
 * 🔴 `realName` 을 그리는 자리가 **없다.** `@/features/artist` 의 `ArtistProfile` 에는
 *    있지만(그쪽은 로그인 뒤 게이트웨이 응답을 그린다) 공개 저장본에는 본명이 실리지
 *    않는다 — 활동명은 공개고 본명은 개인정보다.
 * 🔴 팔로우 버튼도 없다. 팔로우는 `accountId` 를 요구하고 그 필드는 공개 계약에 없다.
 *    로그인한 방문자에게는 `/artists/[id]` 페이지가 별도의 회원 패널로 그것을 붙인다.
 */
export function PublicArtistProfile({ artist }: { artist: PublicArtist }) {
  return (
    <article
      data-testid="public-artist-profile"
      className="rounded-2xl border border-ink-200 bg-white p-8 shadow-sm"
    >
      <div className="flex items-start gap-6">
        <div className="flex h-32 w-32 items-center justify-center rounded-2xl bg-gradient-to-br from-brand-100 to-accent-100 text-4xl font-bold text-brand-700">
          {artist.stageName.slice(0, 2).toUpperCase()}
        </div>
        <div className="flex-1">
          <h1 className="text-3xl font-bold text-ink-900">{artist.stageName}</h1>
          <p className="mt-2 text-sm text-ink-600">
            {artist.artistType === 'SOLO' ? '솔로' : '그룹 멤버'}
            {artist.agency ? ` · ${artist.agency}` : ''}
            {artist.debutDate ? ` · 데뷔 ${artist.debutDate}` : ''}
          </p>
        </div>
      </div>
      {artist.bio ? (
        <p className="mt-6 whitespace-pre-line text-sm leading-relaxed text-ink-700">
          {artist.bio}
        </p>
      ) : null}
    </article>
  );
}
