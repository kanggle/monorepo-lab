import type { ReactNode } from 'react';
import Link from 'next/link';
import { getFanSession } from '@/shared/auth/session';
import { getPost } from '@/features/post/api/getPost';
import { ReactionBar } from './ReactionBar';
import { ApiError } from '@/shared/api/errors';
import { ErrorState } from '@/shared/ui/ErrorState';

/**
 * `/posts/[id]` 의 **회원 판** — 게이트웨이가 그리는 상세. 예전 페이지의 본문 그대로다.
 *
 * ─────────────────────────────────────────────────────────────────────────
 * 🔴 컴포넌트가 아니라 **함수**인 이유 — "못 그렸다" 를 호출자가 알아야 한다
 * ─────────────────────────────────────────────────────────────────────────
 * `/posts/[id]` 는 이제 두 출처를 갖는다: 로그인한 방문자에게는 게이트웨이(멤버 전용
 * 본문·반응까지), 그 외에는 공개 저장본. 게이트웨이가 답을 못 주면 **저장본으로 내려가야**
 * 하는데, async 서버 컴포넌트로 만들면 `null` 을 돌려줘도 페이지는 그 자리에 빈 칸을 그릴
 * 뿐 폴백을 못 한다. 그래서 페이지가 `await` 해서 «엘리먼트 or null» 을 직접 받는다.
 *
 * 반환값의 뜻:
 *   · ReactNode — 이걸 그려라(성공 또는 «멤버십이 필요하다» 라는 **확정된 답**).
 *   · null      — 판정 못 했다. 호출자가 공개 저장본으로 내려가라.
 *
 * 🔴 404 도 `null` 이다. 게이트웨이에 없다고 저장본에도 없다는 뜻은 아니다 — 데모 백엔드가
 *    꺼져 있거나 다른 세대를 들고 있을 수 있다. «없다» 의 최종 판정은 두 출처를 다 본
 *    호출자가 한다.
 *
 * 🔴 인가는 하나도 안 바뀐다. 본문을 줄지 말지는 여전히 community-service 가 정하고,
 *    `MEMBERSHIP_REQUIRED` 는 그 서비스가 내린 판정을 그대로 그리는 것이다.
 * ─────────────────────────────────────────────────────────────────────────
 */
export async function memberPostDetail(id: string): Promise<ReactNode | null> {
  const session = await getFanSession();
  if (!session.accessToken) return null;

  try {
    const post = await getPost(session.accessToken, id);
    return (
      <article
        data-testid="member-post-detail"
        className="rounded-2xl border border-ink-200 bg-white p-8 shadow-sm"
      >
        <header className="mb-4 flex items-center gap-2">
          <span
            className={[
              'rounded-full px-2 py-0.5 text-xs font-medium',
              post.postType === 'ARTIST_POST'
                ? 'bg-brand-100 text-brand-700'
                : 'bg-ink-100 text-ink-600',
            ].join(' ')}
          >
            {post.postType === 'ARTIST_POST' ? 'ARTIST' : 'FAN'}
          </span>
          <span className="text-xs text-ink-400">
            {post.publishedAt ? new Date(post.publishedAt).toLocaleString('ko-KR') : '발행 전'}
          </span>
        </header>
        {post.title ? (
          <h1 className="mb-3 text-2xl font-bold text-ink-900">{post.title}</h1>
        ) : null}
        <p className="whitespace-pre-line text-base leading-relaxed text-ink-800">{post.body}</p>
        <footer className="mt-8 border-t border-ink-200 pt-4">
          <ReactionBar postId={post.postId} totalReactions={post.reactionCount} />
        </footer>
      </article>
    );
  } catch (err) {
    if (err instanceof ApiError && err.code === 'MEMBERSHIP_REQUIRED') {
      const requiredTier = err.details?.requiredTier;
      const href =
        requiredTier === 'PREMIUM' || requiredTier === 'MEMBERS_ONLY'
          ? `/membership?tier=${requiredTier}`
          : '/membership';
      return (
        <ErrorState
          title="멤버십이 필요합니다"
          description={
            requiredTier === 'PREMIUM'
              ? '이 포스트는 프리미엄 멤버십 전용입니다.'
              : '이 포스트는 멤버십 전용입니다.'
          }
          action={
            <Link
              href={href}
              className="rounded-md bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-700"
            >
              멤버십 구독하기
            </Link>
          }
        />
      );
    }
    // 404 를 포함한 나머지 전부 — 판정 못 했다. 호출자가 저장본을 본다(§ 위).
    return null;
  }
}
