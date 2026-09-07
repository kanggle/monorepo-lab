import type { PublicPost } from '@demo/public-data';
import { PublicPostCard } from './PublicPostCard';

/** 공개 피드 목록. 0건 처리는 호출자가 `PublicEmptyState` 로 한다(0의 뜻이 둘이라서). */
export function PublicFeedList({ posts }: { posts: PublicPost[] }) {
  return (
    <ul className="flex flex-col gap-4" data-testid="public-feed">
      {posts.map((post) => (
        <li key={post.id}>
          <PublicPostCard post={post} />
        </li>
      ))}
    </ul>
  );
}
