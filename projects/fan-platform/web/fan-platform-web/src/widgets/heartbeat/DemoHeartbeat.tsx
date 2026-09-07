'use client';

import { useEffect } from 'react';

/** 60초. 컨트롤 플레인의 유휴 임계값보다 충분히 짧다(`infra/demo/aws` 의 site 판은 30초). */
const INTERVAL_MS = 60_000;

/**
 * 로그인한 방문자의 세션 동안만 도는 heartbeat 핑.
 *
 * 🔴🔴 **인증된 셸에서만 마운트한다** (`widgets/header/Header.tsx` 의 `authed` 분기).
 *    익명 방문이 heartbeat 을 만들면 아무도 안 쓰는 데모 EC2 가 무기한 켜져 있게 된다 —
 *    이유 전체는 `app/api/demo/heartbeat/route.ts` 헤더에 있다. 이 컴포넌트를 셸의 공용
 *    자리(예: `(main)/layout.tsx`)로 올리지 마라. 그러면 공개 페이지에서도 돈다.
 *
 * 🔵 그래도 서버가 다시 판정한다(라우트의 세션 검사). 여기 조건은 «불필요한 요청을 안
 *    만든다» 는 것이고, «못 하게 막는다» 는 서버 쪽 일이다. 두 문장이 다른 일을 한다.
 *
 * 🔵 마운트 즉시 한 번 보내지 않고 60초 뒤부터 보낸다. 페이지를 스쳐 지나간 방문
 *    (열자마자 닫는)까지 인스턴스 수명을 늘릴 이유가 없다 — heartbeat 은 «머물러 있다» 의
 *    신호여야지 «들렀다» 의 신호가 아니다.
 *
 * 🔵 실패는 삼킨다. 컨트롤 플레인이 안 붙어도 화면은 아무 영향을 안 받는다.
 */
export function DemoHeartbeat() {
  useEffect(() => {
    const id = setInterval(() => {
      void fetch('/api/demo/heartbeat', { method: 'POST', keepalive: true }).catch(() => {});
    }, INTERVAL_MS);
    return () => clearInterval(id);
  }, []);

  return null;
}
