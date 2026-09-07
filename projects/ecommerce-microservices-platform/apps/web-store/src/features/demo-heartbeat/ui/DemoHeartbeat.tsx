'use client';

import { useEffect } from 'react';

/** 데모 컨트롤 플레인의 idle 타이머보다 충분히 짧게. 분 단위 타이머에 60초면 여유가 있다. */
const DEFAULT_INTERVAL_MS = 60_000;

interface DemoHeartbeatProps {
  intervalMs?: number;
}

/**
 * 로그인 영역에 머무는 동안 데모 EC2 를 깨어 있게 유지한다.
 *
 * 🔴🔴 **공개 화면에는 절대 마운트하지 않는다.** 홈·상품 목록·상세는 공개 저장본만 읽으므로
 *    EC2 를 쓰지 않는다 — 거기서 하트비트를 보내면 아무도 안 쓰는 인스턴스를 켜 두는 것이고,
 *    그 비용은 월 예산 가드를 태워 정작 백엔드가 필요한 사람의 `/start` 를 429 로 만든다.
 *    그래서 마운트 지점은 **인증 가드를 이미 통과한 자리**뿐이다(`/my` 레이아웃 · `/cart` ·
 *    `/checkout`). 라우트 핸들러도 세션이 없으면 아무 데도 안 보내므로 게이트가 둘이다.
 *
 * 🔵 응답을 안 본다(항상 204). 실패도 삼킨다 — 화면의 어떤 것도 이 비콘에 걸리면 안 된다.
 */
export function DemoHeartbeat({ intervalMs = DEFAULT_INTERVAL_MS }: DemoHeartbeatProps) {
  useEffect(() => {
    function beat() {
      void fetch('/api/demo/heartbeat', { method: 'POST', keepalive: true }).catch(() => {});
    }
    // 마운트 즉시 한 번 — 첫 틱까지 60초를 기다리면 그 사이에 idle 판정이 지나갈 수 있다.
    beat();
    const timer = setInterval(beat, intervalMs);
    return () => clearInterval(timer);
  }, [intervalMs]);

  return null;
}
