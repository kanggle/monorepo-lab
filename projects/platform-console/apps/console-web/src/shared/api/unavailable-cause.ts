import { ApiError } from './errors';

/**
 * Why a whole-fan-out BFF call failed — TASK-MONO-711 ②.
 *
 * ## 왜 이것이 필요한가
 *
 * `getOperatorOverviewState()` / `getDomainHealthState()` 는 `400 NO_ACTIVE_TENANT`
 * 와 `401` 만 갈라내고 **나머지 전부**를 하나의 `bffUnavailable: true` 로 떨궜다.
 * 거기 떨어지는 것: 502 · 500 · 타임아웃 · DNS 실패 · `ApiError` 가 아닌 예외 전부.
 *
 * 🔴🔴 그래서 «기록된 영구 한계» 와 «진짜 장애» 가 **같은 값**으로 나온다. 콘솔이
 * Vercel 로 옮겨간 뒤(`ADR-MONO-067` 단계 3) `console-bff` 는 공개 호스트명이 없어
 * (`TASK-MONO-362`) 이 경로가 **상시** 실패한다 — 즉 이 신호는 늘 켜져 있고,
 * **상시 켜진 신호는 꺼진 것과 같다.** 언젠가 BFF 가 정말로 죽어도 아무도 구별하지
 * 못한다.
 *
 * ⇒ 실패를 **분류해서 들고 다닌다.** 화면 문구를 바꾸는 일이 아니다(그건 별건이다) —
 * 로그와 촬영 매니페스트가 «무엇이 실패했는지» 를 말할 수 있게 하는 것이 목적이다.
 *
 * 🔴 **방문자에게 내부 상태코드를 보이지 마라.** 이 값은 서버 로그·진단용이다.
 */
export type UnavailableCause =
  /** 응답은 왔는데 상태코드가 나쁘다 (`status` · `code` 를 함께 싣는다). */
  | { kind: 'status'; status: number; code: string }
  /** 요청이 응답에 닿지 못했다 — DNS·연결거부·TLS·CORS 등 `fetch` 자체가 던진 경우. */
  | { kind: 'transport'; name: string }
  /** 중단됐다 — `AbortError` / `TimeoutError`. */
  | { kind: 'timeout'; name: string }
  /** 위 어느 것으로도 못 가른 것. 🔵 «모른다» 를 «없다» 로 적지 않기 위해 남긴다. */
  | { kind: 'unknown'; name: string };

/**
 * Classifies a caught error into an {@link UnavailableCause}.
 *
 * 🔴 순서가 의미를 갖는다: `ApiError` 가 먼저다. 그것은 **응답을 받은** 경우이고,
 * 나머지는 응답을 못 받은 경우다. 뒤집으면 `ApiError` 가 `unknown` 으로 샌다.
 */
export function classifyUnavailable(err: unknown): UnavailableCause {
  if (err instanceof ApiError) {
    return { kind: 'status', status: err.status, code: err.code };
  }
  // 🔵 `AbortError` / `TimeoutError` 는 DOMException 이거나 Error 이고 환경마다 다르다 —
  //    생성자가 아니라 **이름**으로 가른다.
  const name =
    typeof err === 'object' && err !== null && typeof (err as { name?: unknown }).name === 'string'
      ? (err as { name: string }).name
      : typeof err;
  if (name === 'AbortError' || name === 'TimeoutError') return { kind: 'timeout', name };
  // 🔴 `fetch` 가 네트워크 층에서 실패하면 `TypeError` 를 던진다(undici/브라우저 공통).
  //    이것이 «BFF 에 닿지도 못했다» 이고, 지금 콘솔이 상시 머무는 상태다.
  if (err instanceof TypeError) return { kind: 'transport', name };
  return { kind: 'unknown', name };
}

/** 로그 한 줄에 실을 짧은 표현. 🔵 값이 아니라 **모양**만 싣는다. */
export function formatUnavailableCause(c: UnavailableCause): string {
  switch (c.kind) {
    case 'status':
      return `status:${c.status}/${c.code}`;
    case 'transport':
    case 'timeout':
    case 'unknown':
      return `${c.kind}:${c.name}`;
  }
}
