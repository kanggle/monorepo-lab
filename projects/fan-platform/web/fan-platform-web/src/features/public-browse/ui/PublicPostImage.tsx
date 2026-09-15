'use client';

import { useState } from 'react';

/**
 * 공개 글의 사진 한 장 — 로드에 실패하면 **자리째** 사라진다 (TASK-MONO-678).
 *
 * =============================================================================
 * 🔴🔴 왜 `post` 가 아니라 `src` · `alt` 만 받는가
 * =============================================================================
 * 이 파일이 `'use client'` 인 이유는 `onError` 하나다. 그리고 클라이언트 컴포넌트에 넘긴
 * props 는 **RSC 페이로드로 직렬화된다.** `post` 를 통째로 넘기면 화면에 안 그리는 필드까지
 * 페이로드에 실린다 — `PublicPostCard` 머리말 ③이 닫아 둔 함정이다. 그래서 부모(서버
 * 컴포넌트)가 **잠기지 않은 글에서만** 이 컴포넌트를 만들고, 넘기는 것은 그 한 장의 주소와
 * 설명뿐이다. 잠긴 글을 판정할 수단이 이 파일에 **없는 것**이 요점이다.
 *
 * =============================================================================
 * 🔴 실패하면 틀(frame)까지 지운다
 * =============================================================================
 * `<img>` 만 숨기면 16:9 회색 상자가 남고, 방문자에게는 «깨진 사진» 으로 읽힌다. 사진은
 * 글의 부속이지 본체가 아니다 — 없으면 예전 카드 그대로 보이는 편이 참이다.
 *
 * 🔵 `next/image` 를 안 쓴다 — `PublicArtistAvatar` 와 같은 이유(`images.unoptimized: true`,
 *    `TASK-MONO-587` 의 변환 축을 안 민다).
 */
export function PublicPostImage({
  src,
  alt,
  frameClassName,
  badge,
}: {
  src: string;
  alt: string;
  /** 틀의 크기·모양. 사진은 그 안을 `object-cover` 로 채운다. */
  frameClassName: string;
  /** 틀 오른쪽 아래에 얹는 짧은 표식(예: 남은 장수). */
  badge?: string;
}) {
  const [failed, setFailed] = useState(false);
  if (failed) return null;

  return (
    <div data-testid="public-post-image-frame" className={`relative overflow-hidden ${frameClassName}`}>
      {/* eslint-disable-next-line @next/next/no-img-element -- 위 § 참조: 이 앱은 next/image 를 쓰지 않는다 */}
      <img
        data-testid="public-post-image"
        src={src}
        alt={alt}
        loading="lazy"
        decoding="async"
        className="h-full w-full object-cover"
        onError={() => setFailed(true)}
      />
      {badge ? (
        <span className="absolute bottom-2 right-2 rounded-full bg-ink-900/70 px-2 py-0.5 text-xs font-medium text-white">
          {badge}
        </span>
      ) : null}
    </div>
  );
}
