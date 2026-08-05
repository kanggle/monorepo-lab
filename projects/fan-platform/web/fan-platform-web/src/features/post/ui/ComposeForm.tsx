'use client';
import { useState, useTransition } from 'react';
import { useRouter } from 'next/navigation';
import { Button } from '@/shared/ui/Button';
import { publishFanPost } from '@/features/post/api/actions';
import { FAN_POST_BODY_MAX, FAN_POST_TITLE_MAX } from '@/features/post/lib/post-limits';

/**
 * Fan post composer (TASK-FAN-FE-016).
 *
 * <p>There is deliberately no visibility control — see `publishFanPost`. Every post this
 * screen creates is PUBLIC.
 *
 * <p>On success it navigates to the new post's detail page rather than clearing the form.
 * That is the point of the ticket as much as the writing is: the feed is follow-based, so a
 * fan's own post is invisible in their own feed, and a composer that just says "saved" would
 * leave the post apparently nowhere.
 */
export function ComposeForm() {
  const router = useRouter();
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [pending, startTransition] = useTransition();

  const bodyTooLong = body.trim().length > FAN_POST_BODY_MAX;
  const canSubmit = body.trim().length > 0 && !bodyTooLong && !pending;

  function submit() {
    setError(null);
    startTransition(async () => {
      const result = await publishFanPost(title, body);
      if (!result.ok) {
        setError(result.message);
        return;
      }
      router.push(`/posts/${result.postId}`);
    });
  }

  return (
    <form
      data-testid="compose-form"
      onSubmit={(e) => {
        e.preventDefault();
        if (canSubmit) submit();
      }}
      className="flex flex-col gap-4 rounded-xl border border-ink-200 bg-white p-6 dark:bg-ink-900 dark:border-ink-800"
    >
      <label className="flex flex-col gap-1">
        <span className="text-sm font-medium text-ink-700 dark:text-ink-200">
          제목 <span className="font-normal text-ink-500">(선택)</span>
        </span>
        <input
          data-testid="compose-title"
          type="text"
          value={title}
          maxLength={FAN_POST_TITLE_MAX}
          onChange={(e) => setTitle(e.target.value)}
          placeholder="제목을 입력하세요"
          className="rounded-md border border-ink-200 px-3 py-2 text-sm text-ink-900 dark:bg-ink-800 dark:text-ink-100 dark:border-ink-700"
        />
      </label>

      <label className="flex flex-col gap-1">
        <span className="text-sm font-medium text-ink-700 dark:text-ink-200">내용</span>
        <textarea
          data-testid="compose-body"
          value={body}
          rows={10}
          onChange={(e) => setBody(e.target.value)}
          placeholder="어떤 이야기를 나누고 싶으신가요?"
          className="rounded-md border border-ink-200 px-3 py-2 text-sm text-ink-900 dark:bg-ink-800 dark:text-ink-100 dark:border-ink-700"
        />
        <span
          className={[
            'self-end text-xs',
            bodyTooLong ? 'text-red-600' : 'text-ink-500',
          ].join(' ')}
        >
          {body.trim().length.toLocaleString()} / {FAN_POST_BODY_MAX.toLocaleString()}
        </span>
      </label>

      <p className="text-xs text-ink-500">
        작성한 글은 전체 공개됩니다.
      </p>

      {error ? (
        <p data-testid="compose-error" className="text-sm text-red-600">
          {error}
        </p>
      ) : null}

      <Button type="submit" disabled={!canSubmit} className="self-start">
        {pending ? '게시하는 중…' : '게시하기'}
      </Button>
    </form>
  );
}
