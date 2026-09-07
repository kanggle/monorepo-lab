import Link from 'next/link';
import type { PublicPost } from '@demo/public-data';

/**
 * 공개 피드의 글 한 줄.
 *
 * ─────────────────────────────────────────────────────────────────────────
 * 🔴🔴 잠긴 글의 본문은 **어디에도 없다** — 그리고 그것을 «안 그린다» 로 얻지 않는다
 * ─────────────────────────────────────────────────────────────────────────
 * 세 겹이 같은 방향으로 걸려 있다:
 *
 *   ① 발행 계약  — `locked === true` 면 `body` 는 `null`, `imageUrls` 는 `[]` 다
 *                  (`@demo/public-data` 의 `validateDatasetData` 가 봉투를 **거부**한다).
 *                  `bodyPreview` 라는 필드는 공개 계약에 **존재하지 않는다.**
 *   ② 이 컴포넌트 — 잠긴 분기에서 `post.body` 를 참조하는 식이 하나도 없다.
 *   ③ 서버 컴포넌트 — `'use client'` 가 없으므로 `post` 객체가 RSC 페이로드로 직렬화되지
 *                  않는다. 클라이언트 컴포넌트에 `post` 를 통째로 넘기면 ①②가 다 참이어도
 *                  **봉투의 필드가 페이로드에 실린다** — 그 함정을 여기서 닫는다.
 *
 * ⇒ 「본문을 조심해서 안 그린다」가 아니라 **그릴 본문이 없다.** 그래서 다음 사람이 이
 *   카드에 필드를 하나 더 붙여도 잠긴 글에서 새 나갈 것이 없다.
 * ─────────────────────────────────────────────────────────────────────────
 */
export function PublicPostCard({ post }: { post: PublicPost }) {
  return (
    <article
      data-testid="public-post-card"
      data-locked={post.locked ? 'true' : 'false'}
      className="rounded-xl border border-ink-200 bg-white p-5 shadow-sm transition-shadow hover:shadow-md dark:bg-ink-900 dark:border-ink-800"
    >
      <header className="mb-2 flex items-center gap-2">
        <Link
          href={`/artists/${post.artistId}`}
          className="rounded-full bg-brand-100 px-2 py-0.5 text-xs font-medium text-brand-700 hover:bg-brand-200"
        >
          {post.artistStageName}
        </Link>
        {post.locked ? (
          <span className="rounded-full bg-accent-100 px-2 py-0.5 text-xs font-medium text-accent-700">
            {post.visibility === 'PREMIUM' ? 'PREMIUM' : '멤버 전용'}
          </span>
        ) : null}
        <time className="ml-auto text-xs text-ink-400" dateTime={post.publishedAt}>
          {post.publishedAt.slice(0, 10)}
        </time>
      </header>

      <h3 className="mb-1 text-lg font-semibold text-ink-900 dark:text-ink-100">
        {post.title}
      </h3>

      {post.locked ? (
        // 🔴 티저. 제목은 이미 위에 있고, 여기서 말하는 것은 «왜 안 보이는가» 와
        //    «무엇을 하면 되는가» 뿐이다.
        <div className="rounded-lg bg-ink-50 p-4 text-center dark:bg-ink-800">
          <p className="text-sm font-medium text-ink-800 dark:text-ink-100">멤버십 전용</p>
          <p className="mt-1 text-xs text-ink-600 dark:text-ink-400">
            멤버십에 가입하면 이 포스트를 읽을 수 있습니다.
          </p>
          <Link
            href="/membership"
            className="mt-3 inline-block rounded-md bg-brand-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-brand-700"
          >
            멤버십 안내 보기
          </Link>
        </div>
      ) : (
        <p className="line-clamp-3 whitespace-pre-line text-sm text-ink-600 dark:text-ink-300">
          {post.body}
        </p>
      )}

      <footer className="mt-4 flex items-center text-xs text-ink-500">
        <Link className="ml-auto text-brand-600 hover:text-brand-700" href={`/posts/${post.id}`}>
          자세히 보기 →
        </Link>
      </footer>
    </article>
  );
}
