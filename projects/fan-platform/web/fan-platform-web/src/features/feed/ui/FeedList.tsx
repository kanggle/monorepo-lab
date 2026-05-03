import type { FeedItem } from '@/entities/post';
import { PostCard } from '@/features/post/ui/PostCard';
import { EmptyState } from '@/shared/ui/EmptyState';
import Link from 'next/link';

export function FeedList({ items }: { items: FeedItem[] }) {
  if (items.length === 0) {
    return (
      <EmptyState
        title="팔로우한 아티스트의 포스트가 없습니다"
        description="아티스트 디렉토리에서 좋아하는 아티스트를 찾아 팔로우해 보세요."
        action={
          <Link
            href="/artists"
            className="inline-flex rounded-md bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-700"
          >
            아티스트 둘러보기
          </Link>
        }
      />
    );
  }
  return (
    <ul className="flex flex-col gap-4">
      {items.map((item) => (
        <li key={item.postId}>
          <PostCard item={item} />
        </li>
      ))}
    </ul>
  );
}
