import { notFound } from 'next/navigation';
import { Suspense } from 'react';
import Link from 'next/link';
import { getFanSession } from '@/shared/auth/session';
import { getPost, ReactionBar } from '@/features/post';
import { ApiError } from '@/shared/api/errors';
import { LoadingState } from '@/shared/ui/LoadingState';
import { ErrorState } from '@/shared/ui/ErrorState';

async function PostDetail({ id }: { id: string }) {
  const session = await getFanSession();
  try {
    const post = await getPost(session.accessToken, id);
    return (
      <article className="rounded-2xl border border-ink-200 bg-white p-8 shadow-sm">
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
            {post.publishedAt
              ? new Date(post.publishedAt).toLocaleString('ko-KR')
              : '발행 전'}
          </span>
        </header>
        {post.title ? (
          <h1 className="mb-3 text-2xl font-bold text-ink-900">{post.title}</h1>
        ) : null}
        <p className="whitespace-pre-line text-base leading-relaxed text-ink-800">
          {post.body}
        </p>
        <footer className="mt-8 border-t border-ink-200 pt-4">
          <ReactionBar postId={post.postId} totalReactions={post.reactionCount} />
        </footer>
      </article>
    );
  } catch (err) {
    if (err instanceof ApiError) {
      if (err.status === 404) notFound();
      if (err.code === 'MEMBERSHIP_REQUIRED') {
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
    }
    throw err;
  }
}

export default async function PostDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  return (
    <Suspense fallback={<LoadingState label="포스트를 불러오는 중..." />}>
      <PostDetail id={id} />
    </Suspense>
  );
}
