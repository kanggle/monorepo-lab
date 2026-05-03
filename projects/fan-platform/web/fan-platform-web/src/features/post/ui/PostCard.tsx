import Link from 'next/link';
import type { FeedItem } from '@/entities/post';

/** Single feed row. Locked tier renders a subscribe CTA without leaking content. */
export function PostCard({ item }: { item: FeedItem }) {
  const isArtist = item.postType === 'ARTIST_POST';
  return (
    <article
      data-testid="post-card"
      className="rounded-xl border border-ink-200 bg-white p-5 shadow-sm transition-shadow hover:shadow-md dark:bg-ink-900 dark:border-ink-800"
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
              {item.title}
            </h3>
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
        <Link
          className="ml-auto text-brand-600 hover:text-brand-700"
          href={`/posts/${item.postId}`}
        >
          자세히 보기 →
        </Link>
      </footer>
    </article>
  );
}
