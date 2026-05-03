import { Suspense } from 'react';
import { getFanSession } from '@/shared/auth/session';
import { getFeed } from '@/features/feed';
import { FeedList } from '@/features/feed';
import { LoadingState } from '@/shared/ui/LoadingState';
import { ErrorState } from '@/shared/ui/ErrorState';
import { Pagination } from '@/shared/ui/Pagination';
import type { FeedPage } from '@/entities/post';

const PAGE_SIZE = 10;

async function FeedSection({ page }: { page: number }) {
  const session = await getFanSession();
  let feed: FeedPage | null = null;
  try {
    feed = await getFeed(session.accessToken, page, PAGE_SIZE);
  } catch {
    feed = null;
  }

  if (!feed) {
    return (
      <ErrorState
        title="피드를 불러올 수 없습니다"
        description="잠시 후 다시 시도해주세요. 백엔드 게이트웨이가 응답하지 않을 수 있습니다."
      />
    );
  }

  return (
    <>
      <FeedList items={feed.content} />
      <Pagination
        page={feed.page}
        totalPages={feed.totalPages}
        hrefFor={(p) => (p === 0 ? '/' : `/?page=${p}`)}
      />
    </>
  );
}

export default async function HomePage({
  searchParams,
}: {
  searchParams: Promise<{ page?: string }>;
}) {
  const params = await searchParams;
  const page = Number.parseInt(params.page ?? '0', 10) || 0;
  return (
    <section>
      <header className="mb-6">
        <h1 className="text-2xl font-bold text-ink-900">피드</h1>
        <p className="text-sm text-ink-600">팔로우한 아티스트의 최신 포스트입니다.</p>
      </header>
      <Suspense fallback={<LoadingState label="피드를 불러오는 중입니다..." />}>
        <FeedSection page={page} />
      </Suspense>
    </section>
  );
}
