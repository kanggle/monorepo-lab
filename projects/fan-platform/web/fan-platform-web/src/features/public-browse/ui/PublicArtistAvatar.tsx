'use client';

import { useState } from 'react';
import type { PublicArtist } from '@demo/public-data';

/**
 * 공개 아티스트의 얼굴 자리 — 사진이 있으면 사진, 없으면 이니셜.
 *
 * =============================================================================
 * 🔴🔴 왜 이 컴포넌트가 생겼나 (TASK-MONO-641)
 * =============================================================================
 * `TASK-MONO-638` 이 여섯 아티스트의 `profileImageUrl` 을 채웠는데 **그 값을 읽는 코드가
 * 하나도 없었다.** 저장소 전체에서 그 이름은 시험 픽스처 한 줄에만 나왔고, 카드와 프로필은
 * 이니셜 두 글자를 그라디언트 위에 그리고 있었다. 배포된 화면에서 실측했다:
 *
 *     fan.hubwang.com/artists   아티스트 6명 ✅   images.unsplash.com 0건 🔴
 *
 * ⇒ 필드를 채우는 것과 그 필드가 **보이는 것**은 다른 축이다. 앞엣것만 하면 시험도 가드도
 *   초록인 채로 화면은 그대로다 — 이 저장소가 «아무도 렌더 안 하는 데이터» 라고 이름 붙인
 *   함정이고, 638 의 AC 가 «채운다» 로만 쓰여 있어서 그 체크가 닫혔다.
 *
 * =============================================================================
 * 🔴 이니셜 경로를 지우지 않는다
 * =============================================================================
 * `profileImageUrl === null` 은 **638 이전 여섯 명 전원의 상태**였고, 지금도 저장본이
 * 비거나 수집이 실패하면 그 값이 온다. 그 경로를 지우면 그날 얼굴 자리가 통째로 깨진다.
 * 그래서 이 컴포넌트의 기본값은 **이니셜**이고, 사진은 «있을 때만» 그린다.
 *
 * 🔴 그리고 사진이 **404 여도** 이니셜로 되돌아간다. 그러려면 `onError` 가 필요하고,
 *    그것이 이 파일이 `'use client'` 인 유일한 이유다. 부모 둘은 서버 컴포넌트로 남는다.
 *
 * 🔵 `next/image` 를 안 쓴다 — 이 앱은 `images: { unoptimized: true }` 라 최적화를 안 타고
 *    (그래서 이미 112% 초과인 Vercel 변환 축을 **안 민다**, TASK-MONO-587), 실제로 이 앱
 *    어디에도 `next/image` 를 안 쓴다. 여기서만 도입하면 그 설정과 결합이 생긴다.
 * 🔵 폭·높이는 URL 이 이미 들고 있다(`w=400&h=400`, TASK-MONO-638) — 원본을 받지 않는다.
 */
export function PublicArtistAvatar({
  artist,
  className,
  textClassName,
}: {
  artist: PublicArtist;
  /** 자리의 크기·모양. 사진과 이니셜 블록이 **같은 값**을 받는다(교체돼도 레이아웃이 안 흔들린다). */
  className: string;
  textClassName: string;
}) {
  const [failed, setFailed] = useState(false);
  const src = artist.profileImageUrl;
  const initials = artist.stageName.slice(0, 2).toUpperCase();

  if (!src || failed) {
    return (
      <div
        data-testid="public-artist-avatar-fallback"
        className={`${className} flex items-center justify-center bg-gradient-to-br from-brand-100 to-accent-100 ${textClassName} font-bold text-brand-700`}
      >
        {initials}
      </div>
    );
  }

  return (
    // eslint-disable-next-line @next/next/no-img-element -- 위 § 참조: 이 앱은 next/image 를 쓰지 않는다
    <img
      data-testid="public-artist-avatar"
      src={src}
      alt={`${artist.stageName} 프로필 사진`}
      loading="lazy"
      className={`${className} object-cover`}
      onError={() => setFailed(true)}
    />
  );
}
