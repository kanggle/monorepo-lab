import Link from 'next/link';
import type { Post } from '@/entities/post';

const STATUS_LABEL: Record<string, string> = {
  PUBLISHED: '공개',
  DRAFT: '임시저장',
  HIDDEN: '숨김',
};

/**
 * The author's own posts (TASK-FAN-FE-016).
 *
 * <p>Distinct from `PostCard`, which renders a feed row for someone else's post and has to
 * cope with a locked tier. Nothing here is ever locked — the reader is the author — so this
 * shows the fields an author actually needs: status, and a link back to the post.
 */
export function MyPostList({ posts }: { posts: Post[] }) {
  return (
    <ul data-testid="my-post-list" className="flex flex-col gap-3">
      {posts.map((post) => (
        <li key={post.postId}>
          <Link
            href={`/posts/${post.postId}`}
            data-testid="my-post-item"
            className="block rounded-xl border border-ink-200 bg-white p-5 transition-shadow hover:shadow-md dark:bg-ink-900 dark:border-ink-800"
          >
            <div className="mb-2 flex items-center gap-2">
              <span className="rounded-full bg-ink-100 px-2 py-0.5 text-xs font-medium text-ink-600">
                FAN
              </span>
              {post.status !== 'PUBLISHED' ? (
                <span className="rounded-full bg-accent-100 px-2 py-0.5 text-xs font-medium text-accent-700">
                  {STATUS_LABEL[post.status] ?? post.status}
                </span>
              ) : null}
              <time
                className="ml-auto text-xs text-ink-400"
                dateTime={post.publishedAt ?? post.createdAt}
              >
                {new Date(post.publishedAt ?? post.createdAt).toLocaleDateString('ko-KR')}
              </time>
            </div>
            {post.title ? (
              <h3 className="mb-1 text-lg font-semibold text-ink-900 dark:text-ink-100">
                {post.title}
              </h3>
            ) : null}
            <p className="line-clamp-3 whitespace-pre-wrap text-sm text-ink-700 dark:text-ink-300">
              {post.body}
            </p>
            <div className="mt-3 flex gap-4 text-xs text-ink-500">
              <span>댓글 {post.commentCount}</span>
              <span>반응 {post.reactionCount}</span>
            </div>
          </Link>
        </li>
      ))}
    </ul>
  );
}
