// DEMO-PUBLIC-DATA-CONSUMER: web-store
//
// =============================================================================
// 공개 카탈로그의 **선언된 1차 출처** (ADR-MONO-070 § D3)
// =============================================================================
// 홈 / 목록 / 상세 / 검색은 게이트웨이가 아니라 **공개 저장본**을 읽는다. 그래서 이 앱의
// 공개 화면은 로그인도, 백엔드도, 데모 EC2 도 없이 실물로 선다.
//
// 🔴🔴 **이것은 TASK-FE-061 이 지운 «조용한 목 폴백» 이 아니다.** 그때 지워진 것은
//    *백엔드 호출이 실패하면 몰래 가짜 상품으로 갈아끼우는* 코드였고, 그것이 나빴던 이유는
//    두 가지였다: (a) 화면이 실패를 **숨겼고**, (b) 가짜 id(`mock-1`)가 UUID 가 아니라
//    위시리스트·장바구니·주문 같은 쓰기 경로를 다운스트림에서 터뜨렸다.
//    여기는 그 둘 다 성립하지 않는다 —
//      (a) 출처를 **화면이 말한다**(`DataProvenanceNotice`, `envelope.source`/`degraded`).
//          폴백 사슬이 아니라 **선언된 1차 출처**다. 백엔드로 가는 코드가 아예 없다.
//      (b) 저장본의 id 는 발행자가 백엔드에서 뽑은 **실제 UUID** 다(번들 시드도 동일 형식).
//    ⇒ 지워진 것을 되살린 것으로 읽지 마라. 축이 다르다.
//
// 🔵 **서버 전용이다.** 판독자는 `fetch` + 파일 시드로 서버에서만 돈다(`@demo/public-data`
//    의 read.ts § 서버 전용). 클라이언트 컴포넌트에서 임포트하지 말 것 — 브라우저는 서버가
//    렌더한 결과만 본다. (`import 'server-only'` 를 달지 않는 것은 이 앱의 다른 서버측
//    데이터 모듈(`entities/product/api/*`)과 같은 관례를 따르기 위해서다.)
// =============================================================================

import {
  bundledEnvelope,
  createPublicDataReader,
  type PublicDataResult,
  type PublicDataSource,
  type StorePublicData,
} from '@demo/public-data';
import bundledJson from '@demo/public-data/snapshots/store.json';

// 🔴 판독자는 **앱마다 한 번만** 만든다. 캐시가 인스턴스마다이므로 호출부가 각자 만들면
//    한 요청이 저장본을 여러 번 읽는다(무료 플랜의 대역폭 축이 실재한다 — read.ts § TTL).
const { readPublicData } = createPublicDataReader('store', bundledEnvelope('store', bundledJson));

/** 화면이 방문자에게 말해야 하는 것 — 어디서 왔고, 언제 만들어졌는가. */
export interface StoreSnapshotProvenance {
  source: PublicDataSource;
  /** ISO-8601 UTC 순간. 🔴 KST 로 바꾸지 마라 — 문구가 "UTC 기준" 이라고 말한다. */
  generatedAt: string;
  /**
   * 저장본을 **시도했는데 못 읽어서** 번들 시드로 떨어졌는가.
   * 🔴 `source === 'bundled'` 와 다른 사실이다(read.ts § degraded). 문구가 갈리는 축은 이쪽.
   */
  degraded: boolean;
}

/**
 * 저장본 봉투 전체를 읽는다.
 *
 * 🔵 요청당 한 번만 도는 것은 판독자 자신의 60초 TTL 캐시가 보장한다 — 여기에 React
 *    `cache()` 를 한 겹 더 두지 않는 이유다(캐시가 둘이면 «어느 세대를 보는가» 가 두 곳에서
 *    정해진다).
 */
export async function readStoreSnapshot(): Promise<PublicDataResult<StorePublicData>> {
  return readPublicData();
}

export async function readStoreProvenance(): Promise<StoreSnapshotProvenance> {
  const { envelope, degraded } = await readStoreSnapshot();
  return { source: envelope.source, generatedAt: envelope.generatedAt, degraded };
}

/**
 * `generatedAt` 을 UTC 그대로 사람이 읽는 모양으로 자른다 — "2026-09-07 00:00".
 *
 * 🔴 `shared/lib/datetime.ts` 의 `formatDateTime` 을 쓰지 않는다. 그쪽은 `Asia/Seoul` 을
 *    **핀**하는 것이 존재 이유인데(PROJECT.md § Frontend Conventions), 여기 문구는
 *    "UTC 기준" 이라고 말한다 ⇒ KST 로 찍으면 **문구가 거짓**이 된다. 그리고 이 함수는
 *    `toLocale*` 을 아예 안 부르므로 그 규약이 막으려는 SSR↔hydration 불일치도 없다
 *    (ISO 문자열을 자르는 순수 문자열 연산이다).
 */
export function formatSnapshotInstantUtc(generatedAt: string): string {
  const m = /^(\d{4}-\d{2}-\d{2})T(\d{2}:\d{2})/.exec(generatedAt);
  // 🔵 모양이 다르면 **원문 그대로** 돌려준다. 못 읽는 값을 그럴듯하게 꾸미면 그 화면은
  //    "언제 만들어졌나" 를 틀리게 주장한다.
  return m ? `${m[1]} ${m[2]}` : generatedAt;
}
