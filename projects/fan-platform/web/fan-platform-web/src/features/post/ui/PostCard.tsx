import Link from 'next/link';
import type { FeedItem } from '@/entities/post';
import { PostImage } from '@/shared/ui/PostImage';
import { CARD_LINK_CLASS } from '@/shared/ui/cardLink';

/**
 * 사진 목록. 🔴 `?? []` — 사진 필드가 생기기 전의 백엔드 응답에는 키가 없다(`FeedItem.mediaRefs` 참조).
 * 🔴 **잠기지 않은 분기에서만** 부른다. 서버가 잠긴 항목을 이미 비워 보내지만, 화면이 한 겹 더 선다.
 */
function photosOf(item: FeedItem): string[] {
  return item.mediaRefs ?? [];
}

/**
 * Single feed row. Locked tier renders a subscribe CTA without leaking content.
 *
 * 🔵 **카드 어디를 눌러도 상세로 간다** (TASK-FAN-FE-022) — 상세 링크 하나를 카드 전체로 늘린다
 * (`shared/ui/cardLink.ts`). 제목이 보이면 제목이 그 링크이고, 🔴 제목이 없으면(잠긴 항목은 서버가
 * `title: null` 로 보낸다) 화면에 안 보이는 이름을 단 링크를 둔다 — 이름 없는 링크는 스크린리더가
 * «링크» 라고만 읽는다.
 */
export function PostCard({ item }: { item: FeedItem }) {
  const isArtist = item.postType === 'ARTIST_POST';
  const detailHref = `/posts/${item.postId}`;
  const titleIsLink = !item.locked && Boolean(item.title);
  return (
    <article
      data-testid="post-card"
      className="relative rounded-xl border border-ink-200 bg-white p-5 shadow-sm transition-shadow hover:shadow-md dark:bg-ink-900 dark:border-ink-800"
    >
      <header className="mb-2 flex items-center gap-2">
        <span
          className={[
            'rounded-full px-2 py-0.5 text-xs font-medium',
            isArtist
              ? 'bg-brand-100 text-brand-700'
              : 'bg-ink-100 text-ink-600',
          ].join(' ')}
        >
          {isArtist ? 'ARTIST' : 'FAN'}
        </span>
        {item.visibility !== 'PUBLIC' ? (
          <span className="rounded-full bg-accent-100 px-2 py-0.5 text-xs font-medium text-accent-700">
            {item.visibility === 'MEMBERS_ONLY' ? '멤버 전용' : 'PREMIUM'}
          </span>
        ) : null}
        <time
          className="ml-auto text-xs text-ink-400"
          dateTime={item.publishedAt}
        >
          {new Date(item.publishedAt).toLocaleDateString('ko-KR')}
        </time>
      </header>

      {titleIsLink ? null : (
        <Link href={detailHref} data-card-link="" className={CARD_LINK_CLASS}>
          <span className="sr-only">{item.locked ? '멤버십이 필요한 포스트' : '포스트 보기'}</span>
        </Link>
      )}

      {item.locked ? (
        <div className="rounded-lg bg-ink-50 p-4 text-center dark:bg-ink-800">
          <p className="text-sm font-medium text-ink-800 dark:text-ink-100">
            멤버십이 필요한 포스트입니다
          </p>
          <p className="mt-1 text-xs text-ink-600 dark:text-ink-400">
            가입하면 이 포스트를 읽을 수 있습니다.
          </p>
        </div>
      ) : (
        <>
          {item.title ? (
            <h3 className="mb-1 text-lg font-semibold text-ink-900 dark:text-ink-100">
              <Link href={detailHref} data-card-link="" className={CARD_LINK_CLASS}>
                {item.title}
              </Link>
            </h3>
          ) : null}
          {photosOf(item).length > 0 ? (
            // 🔵 카드는 첫 장만 — 공개 피드(TASK-MONO-678)와 같은 규칙이다.
            <PostImage
              src={photosOf(item)[0]}
              alt={item.title ?? '포스트 사진'}
              frameClassName="mb-3 aspect-video rounded-lg bg-ink-100 dark:bg-ink-800"
              badge={photosOf(item).length > 1 ? `+${photosOf(item).length - 1}` : undefined}
            />
          ) : null}
          {item.bodyPreview ? (
            <p className="line-clamp-3 text-sm text-ink-600 dark:text-ink-300">
              {item.bodyPreview}
            </p>
          ) : null}
        </>
      )}

      <footer className="mt-4 flex items-center gap-4 text-xs text-ink-500">
        <span>댓글 {item.commentCount}</span>
        <span>반응 {item.reactionCount}</span>
      </footer>
    </article>
  );
}
