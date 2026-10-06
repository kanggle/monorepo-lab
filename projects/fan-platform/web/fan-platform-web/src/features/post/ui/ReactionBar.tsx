'use client';
import { useState, useTransition } from 'react';
import type { ReactionType } from '@/entities/post';
import { setReaction } from '@/features/post/api/reactions';

const REACTIONS: Array<{ type: ReactionType; emoji: string; label: string }> = [
  { type: 'LIKE', emoji: '👍', label: '좋아요' },
  { type: 'LOVE', emoji: '❤️', label: '사랑해요' },
  { type: 'FIRE', emoji: '🔥', label: '대박' },
  { type: 'SAD', emoji: '😢', label: '슬퍼요' },
];

export function ReactionBar({
  postId,
  totalReactions,
  initialReaction = null,
}: {
  postId: string;
  totalReactions: number;
  /**
   * The viewer's own reaction, as known by the server at render time
   * (TASK-FAN-FE-029). `null`/omitted means "not reacted" — the call site is
   * responsible for collapsing a missing `myReaction` field to `null` before
   * this prop is set (`post.myReaction ?? null`).
   */
  initialReaction?: ReactionType | null;
}) {
  const [active, setActive] = useState<ReactionType | null>(initialReaction);
  const [isPending, startTransition] = useTransition();

  const onClick = (type: ReactionType) => {
    setActive(type);
    startTransition(() => {
      // Server action — fire-and-forget; the action revalidates the post page
      // so the count updates on next render.
      void setReaction(postId, type);
    });
  };

  return (
    <div className="flex items-center gap-2">
      {REACTIONS.map((r) => (
        <button
          key={r.type}
          type="button"
          aria-label={r.label}
          aria-pressed={active === r.type}
          disabled={isPending}
          onClick={() => onClick(r.type)}
          className={[
            'rounded-full border border-ink-200 px-3 py-1.5 text-sm transition-colors',
            'hover:bg-brand-50 disabled:opacity-50',
            active === r.type ? 'border-brand-500 bg-brand-50 text-brand-700' : 'text-ink-600',
          ].join(' ')}
        >
          <span aria-hidden>{r.emoji}</span> <span>{r.label}</span>
        </button>
      ))}
      <span className="ml-auto text-xs text-ink-500">총 {totalReactions}개의 반응</span>
    </div>
  );
}
