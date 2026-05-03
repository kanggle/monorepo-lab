/**
 * Post types — shape from `specs/contracts/http/community-api.md`.
 */

export type PostType = 'ARTIST_POST' | 'FAN_POST';
export type PostVisibility = 'PUBLIC' | 'MEMBERS_ONLY' | 'PREMIUM';
export type PostStatus = 'DRAFT' | 'PUBLISHED' | 'HIDDEN' | 'DELETED';

export interface Post {
  postId: string;
  tenantId: string;
  postType: PostType;
  visibility: PostVisibility;
  status: PostStatus;
  authorAccountId: string;
  title: string | null;
  body: string;
  commentCount: number;
  reactionCount: number;
  publishedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface FeedItem {
  postId: string;
  postType: PostType;
  visibility: PostVisibility;
  authorAccountId: string;
  title: string | null;
  bodyPreview: string | null;
  commentCount: number;
  reactionCount: number;
  publishedAt: string;
  /**
   * When `true` the visibility tier blocks the actor — `title` and
   * `bodyPreview` are null and the UI should render a Subscribe CTA.
   */
  locked: boolean;
}

export interface FeedPage {
  content: FeedItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

export type ReactionType = 'LIKE' | 'LOVE' | 'FIRE' | 'SAD';

export interface Comment {
  commentId: string;
  postId: string;
  tenantId: string;
  authorAccountId: string;
  body: string;
  createdAt: string;
}
