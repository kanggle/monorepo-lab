import { Suspense } from 'react';
import Link from 'next/link';
import { getFanSession } from '@/shared/auth/session';
import { getMyPosts, MyPostList } from '@/features/post';
import { LoadingState } from '@/shared/ui/LoadingState';
import { EmptyState } from '@/shared/ui/EmptyState';
import { ErrorState } from '@/shared/ui/ErrorState';
import { Pagination } from '@/shared/ui/Pagination';
import type { MyPostsPage } from '@/entities/post';

export const metadata = { title: '내 글 · fan-platform' };

const PAGE_SIZE = 20;

/**
 * The fan's own posts (TASK-FAN-FE-016).
 *
 * <p>This route is the reason the ticket's Goal says "쓴 글을 다시 볼 수 있다" and not just
 * "글을 쓸 수 있다". The feed is follow-based, so a fan's own post never appears in their own
 * feed; without this page a published post is reachable only by an id nobody recorded.
 */
async function MyPosts({ page }: { page: number }) {
  const session = await getFanSession();
  let result: MyPostsPage | null = null;
  try {
    result = await getMyPosts(session.accessToken, page, PAGE_SIZE);
  } catch {
    result = null;
  }

  if (!result) {
    return <ErrorState title="내 글을 불러올 수 없습니다" />;
  }

  if (result.content.length === 0) {
    return (
      <EmptyState
        title="아직 작성한 글이 없습니다"
        description="첫 글을 남겨 팬들과 이야기를 나눠보세요."
        action={
          <Link
            href="/compose"
            className="rounded-md bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-700"
          >
            글쓰기
          </Link>
        }
      />
    );
  }

  return (
    <>
      <MyPostList posts={result.content} />
      <Pagination
        page={result.page}
        totalPages={result.totalPages}
        hrefFor={(p) => `/me/posts?page=${p}`}
      />
    </>
  );
}

export default async function MyPostsPageRoute({
  searchParams,
}: {
  searchParams: Promise<{ page?: string }>;
}) {
  const params = await searchParams;
  const page = Number.parseInt(params.page ?? '0', 10) || 0;
  return (
    <section>
      <header className="mb-6 flex items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-ink-900 dark:text-ink-100">내 글</h1>
          <p className="text-sm text-ink-600 dark:text-ink-400">
            내가 작성한 팬 게시물입니다.
          </p>
        </div>
        <Link
          href="/compose"
          className="rounded-md bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-700"
        >
          글쓰기
        </Link>
      </header>
      <Suspense fallback={<LoadingState label="내 글을 불러오는 중..." />}>
        <MyPosts page={page} />
      </Suspense>
    </section>
  );
}
