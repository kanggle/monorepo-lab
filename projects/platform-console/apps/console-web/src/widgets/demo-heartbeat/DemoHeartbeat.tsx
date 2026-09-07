'use client';

import { useEffect } from 'react';

/**
 * 60초마다 `POST /api/demo/heartbeat` 를 던지는 핑거. **인증된 셸에만** 마운트한다.
 *
 * 🔴🔴 마운트 지점이 이 컴포넌트의 절반이다. `(console)/layout.tsx` — 즉
 *    `isAuthenticated()` 가드 **뒤** — 에만 있고, 공개 둘러보기 `(demo)` 그룹에는 없다.
 *    익명 방문자가 탭을 열어 둔 것만으로 데모 EC2 가 계속 돌면 예산이 «구경하던 사람» 에게
 *    소진되고, 정작 로그인해서 기능을 쓰려는 사람이 왔을 때 인스턴스가 꺼져 있다.
 *    (그리고 둘러보기는 백엔드가 꺼져 있어도 완전히 동작한다 — 켜 둘 이유가 없다.)
 *
 * 🔴 그렇다고 마운트 지점만 믿지 않는다. 라우트 핸들러가 **서버에서 다시** 세션을 확인해
 *    익명 호출을 204 no-op 으로 흘린다. 이 컴포넌트는 «누가 부르는가» 를 정하지 못한다 —
 *    `curl` 은 컴포넌트를 거치지 않는다.
 *
 * 🔵 브라우저 → 같은 오리진(`/api/...`) 이므로 CSP `connect-src 'self'` 안이다. 컨트롤
 *    플레인 주소는 브라우저에 **안 나간다**(서버가 중계한다).
 *
 * 🔵 응답을 읽지 않고 실패를 삼킨다. 하트비트가 몇 번 빠져도 화면은 멀쩡히 돌아야 하고,
 *    콘솔 오류 한 줄이 «콘솔이 고장났다» 로 읽히는 것을 막는다.
 *
 * 🔵 60초인 이유: 런처 페이지가 30초 간격으로 같은 신호를 보낸다
 *    (`infra/demo/aws/site/index.html` § startHeartbeat). 콘솔은 그것보다 느려도 되지만
 *    유휴 판정보다는 촘촘해야 한다 — 그 사이 어딘가면 되고, 짧을수록 Lambda 호출만 는다.
 */
const HEARTBEAT_INTERVAL_MS = 60_000;

export function DemoHeartbeat() {
  useEffect(() => {
    const beat = () => {
      void fetch('/api/demo/heartbeat', {
        method: 'POST',
        cache: 'no-store',
        keepalive: true,
      }).catch(() => {
        /* 삼킨다 — 위 헤더 참조 */
      });
    };
    // 🔴 첫 박동을 즉시 보낸다. 60초를 기다리면, 로그인 직후 인스턴스가 유휴 판정 직전인
    //    경우에 «막 로그인한 사람 앞에서 백엔드가 꺼지는» 창이 열린다.
    beat();
    const id = setInterval(beat, HEARTBEAT_INTERVAL_MS);
    return () => clearInterval(id);
  }, []);

  return null;
}
