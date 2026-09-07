import Link from 'next/link';
import type { PublicPost } from '@demo/public-data';

/**
 * 공개 글 상세.
 *
 * 🔴🔴 잠긴 글에서 이 컴포넌트가 그리는 것은 **제목과 «멤버십 전용» 상태뿐**이다.
 *    `PublicPostCard` 와 같은 세 겹이 여기서도 그대로다(발행 계약 · 이 분기 · 서버
 *    컴포넌트). 상세 화면이 목록보다 «더 많이» 보여 주는 것이 자연스러워 보이지만,
 *    잠긴 글에서는 그 직관이 정확히 틀린 방향이다 — 상세에도 본문은 없다.
 *
 * 🔵 반응(좋아요) 바가 없다. 반응은 쓰기이고 로그인과 백엔드 인가를 요구한다. 로그인한
 *    방문자는 `/posts/[id]` 가 게이트웨이 판을 그리므로 거기서 예전 그대로 쓴다.
 */
export function PublicPostDetail({ post }: { post: PublicPost }) {
  const tierHref = post.visibility === 'PREMIUM' ? '/membership?tier=PREMIUM' : '/membership';

  return (
    <article
      data-testid="public-post-detail"
      data-locked={post.locked ? 'true' : 'false'}
      className="rounded-2xl border border-ink-200 bg-white p-8 shadow-sm"
    >
      <header className="mb-4 flex items-center gap-2">
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

      <h1 className="mb-3 text-2xl font-bold text-ink-900">{post.title}</h1>

      {post.locked ? (
        <div className="rounded-lg border border-dashed border-ink-200 bg-ink-50/60 p-8 text-center">
          <p className="text-base font-semibold text-ink-800">멤버십 전용</p>
          <p className="mt-2 text-sm text-ink-600">
            {post.visibility === 'PREMIUM'
              ? '이 포스트는 프리미엄 멤버십 전용입니다.'
              : '이 포스트는 멤버십 전용입니다.'}{' '}
            로그인하고 멤버십에 가입하면 읽을 수 있습니다.
          </p>
          <Link
            href={tierHref}
            className="mt-4 inline-block rounded-md bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-700"
          >
            멤버십 안내 보기
          </Link>
        </div>
      ) : (
        <p className="whitespace-pre-line text-base leading-relaxed text-ink-800">{post.body}</p>
      )}
    </article>
  );
}
