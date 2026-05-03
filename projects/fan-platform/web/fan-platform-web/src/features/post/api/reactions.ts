'use server';
import { revalidatePath } from 'next/cache';
import { gatewayFetch } from '@/shared/api/client';
import { getFanSession } from '@/shared/auth/session';
import type { ReactionType } from '@/entities/post';

/**
 * Add or update the actor's reaction. Idempotent upsert per
 * `(post_id, reactor_account_id)` — see community-api.md § Reactions.
 */
export async function setReaction(postId: string, reactionType: ReactionType): Promise<void> {
  const session = await getFanSession();
  await gatewayFetch(`/api/v1/community/posts/${encodeURIComponent(postId)}/reactions`, {
    accessToken: session.accessToken,
    method: 'PUT',
    body: { reactionType },
  });
  revalidatePath(`/posts/${postId}`);
}

export async function removeReaction(postId: string): Promise<void> {
  const session = await getFanSession();
  await gatewayFetch(`/api/v1/community/posts/${encodeURIComponent(postId)}/reactions`, {
    accessToken: session.accessToken,
    method: 'DELETE',
  });
  revalidatePath(`/posts/${postId}`);
}
