import Link from 'next/link';
import { ComposeForm } from '@/features/post';

export const metadata = { title: '글쓰기 · fan-platform' };

/**
 * Fan post composer (TASK-FAN-FE-016). Authentication is enforced by the `(main)` group's
 * middleware, the same as every other authed route — an anonymous visitor is redirected to
 * login rather than shown a form that cannot submit.
 */
export default function ComposePage() {
  return (
    <section>
      <header className="mb-6">
        <h1 className="text-2xl font-bold text-ink-900 dark:text-ink-100">글쓰기</h1>
        <p className="text-sm text-ink-600 dark:text-ink-400">
          팬 게시물을 작성합니다. 게시하면{' '}
          <Link href="/me/posts" className="text-brand-600 underline">
            내 글
          </Link>
          에서 다시 볼 수 있습니다.
        </p>
      </header>
      <ComposeForm />
    </section>
  );
}
