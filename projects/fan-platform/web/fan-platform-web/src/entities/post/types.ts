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
  /**
   * 화면이 그대로 그리는 https 절대주소(TASK-MONO-679 ⓐ). 사진이 없으면 `[]`.
   *
   * 🔴 optional 인 이유: 웹은 Vercel 로 **먼저** 배포되고 데모 백엔드는 AMI 재굽기 뒤에야 이 필드를
   *    싣는다. 그 사이의 응답에는 키가 **없다** — 읽는 쪽은 `?? []` 로 받는다.
   */
  mediaRefs?: string[];
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
  /**
   * `locked` 면 서버가 `[]` 로 비워 보낸다(제목·미리보기와 같은 게이트). optional 인 이유는 `Post.mediaRefs` 참조.
   */
  mediaRefs?: string[];
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

/**
 * A page of the caller's own posts (`GET /api/community/posts/mine`).
 *
 * Items are full `Post` objects, not `FeedItem`s: the author is always entitled to their own
 * post, so there is no `locked`/`bodyPreview` redaction to represent here.
 */
export interface MyPostsPage {
  content: Post[];
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
