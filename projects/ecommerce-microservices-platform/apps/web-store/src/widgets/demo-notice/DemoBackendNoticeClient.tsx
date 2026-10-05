'use client';

import { useEffect, useState } from 'react';

/**
 * 배너의 **방문 시점** 판정 (`TASK-MONO-654`).
 *
 * 🔴🔴 이 컴포넌트가 클라이언트인 이유는 «인터랙션» 이 아니라 **캐시**다. 판정이 서버
 * 컴포넌트에 있으면 프리렌더된 사본에 구워지고, 이 배포에서 그 사본은 **재생성되지
 * 않는다**(40시간 실측, 재생성 0회). 브라우저가 물어봐야 «지금» 의 답이 나온다.
 *
 * 🔴 `DEMO_API_BASE` 는 이 파일에 **없다.** 그 이름은 서버 전용 계약이고 클라이언트
 * 번들에 들어가면 안 된다(`ADR-MONO-068 § D1`). 그래서 상태 타입도 패키지에서
 * 임포트하지 않고 **여기서 좁게 다시 적는다** — 타입 임포트는 지워지지만, 그 모듈은
 * 모듈 스코프에서 해석기 팩토리를 부르므로 클라이언트 그래프에 끌려오면 안 된다.
 */
type BackendState = 'not-demo' | 'starting' | 'running' | 'unavailable';

/**
 * 🔴🔴 네 값이 **서로 다른 사실**이다. 뭉치면 `TASK-MONO-636`·`644` 가 두 번 고친
 * 그 결함이 세 번째로 생긴다.
 */
type Probe =
  /** 아직 안 물어봤다. 🔴 «꺼졌다» 가 **아니다** — 화면에 아무 말도 하지 않는다. */
  | { kind: 'probing' }
  /** 물어봤고 꺼져 있다. 배너를 낸다. */
  | { kind: 'unavailable' }
  /**
   * 🔴 물어봤고 **켜지는 중**이다 (`TASK-MONO-668`) — 인스턴스는 떴는데 고른 화면이 아직
   * 전부 준비되지 않았다. 「꺼져 있어」 배너와 **다른 문장**이다: 켜라고 하면 거짓이고(이미
   * 켜졌다), 아무 말도 안 하면 방문자가 «다 됐다» 고 믿는다(이 티켓의 결함).
   */
  | { kind: 'starting' }
  /** 물어봤고 켜져 있거나(running) 데모 배포가 아니다(not-demo). 배너 없음. */
  | { kind: 'quiet' }
  /**
   * 🔴 **못 물어봤다** — 우리 오리진의 라우트조차 응답하지 않았다.
   * 배너를 내지 **않는다**: 그 상태에서 *"데모 서버가 꺼져 있어"* 라고 말하는 것은
   * 관측하지 않은 원인을 지어내는 것이다. 🔵 이것은 「컨트롤 플레인 조회 실패」와
   * **다른 축**이다 — 그쪽은 서버가 판정해 `unavailable` 로 내려보내고(그때는 배너가
   * 옳다), 이쪽은 그 판정을 **받지도 못한** 경우다.
   */
  | { kind: 'unreachable' };

export function DemoBackendNoticeClient() {
  const [probe, setProbe] = useState<Probe>({ kind: 'probing' });

  useEffect(() => {
    // 🔴 언마운트 뒤 setState 를 막는다 — 라우트 전환이 탐침보다 빠를 수 있다.
    let alive = true;

    (async () => {
      try {
        const res = await fetch('/api/demo/backend-state', { cache: 'no-store' });
        if (!res.ok) throw new Error(`status ${res.status}`);
        const body = (await res.json()) as { state?: BackendState };
        if (!alive) return;
        // 🔴 TASK-MONO-668 — 켜지는 중은 꺼짐과도 켜짐과도 다른 값이다.
        if (body.state === 'starting') return setProbe({ kind: 'starting' });
        setProbe(body.state === 'unavailable' ? { kind: 'unavailable' } : { kind: 'quiet' });
      } catch {
        // 🔴 삼키되 **거짓말하지 않는다.** 'unavailable' 로 떨어뜨리면 원인을 지어낸다.
        if (alive) setProbe({ kind: 'unreachable' });
      }
    })();

    return () => {
      alive = false;
    };
  }, []);

  // 🔴 TASK-MONO-668 — 켜지는 중.
  //    🔴 「샘플 데이터」 문장을 **안 넣는다** — 켜지는 중에 무엇이 그려지는지는 잰 적이 없고,
  //    `TASK-MONO-642` 의 규칙이 «배너는 화면이 그리는 것과 어긋나면 안 된다» 다.
  //    🔴 「서버를 켠 뒤」도 **안 넣는다** — 이미 켜졌다. 그 말은 방문자를 론처로 돌려보내
  //    중복 기동을 누르게 한다.
  //    🔴🔴 TASK-FE-104 — `starting` 은 «**선택된 묶음 전부**가 아직 ready 가 아니다» 다(`/status`
  //    `selection_ready === false`). 스토어 묶음은 이미 ready 일 수 있다: 20차 창에서 콘솔 애드온
  //    `console-scm` 하나를 내렸다 올리는 동안 스토어에 이 배너가 떴다. 그것은 결함이 아니라
  //    소유자 결정 ⓑ(2026-09-15, Lambda `_selection_ready` docstring)의 알려진 대가다 — 자기 묶음만
  //    보려면 해석기가 «내 묶음» 을 알아야 하고 그것은 `ADR-MONO-068` 재개봉이다. 소유자는
  //    2026-10-04 에 그 기준을 **유지**하고 문구만 바로잡기로 했다(FE-104 (나)).
  //    ⇒ 옛 첫 문장 «데모 서버가 켜지는 중입니다» 는 스토어 자신이 준비 중이라고 읽혀서, 판정이
  //    모르는 것을 말했다. 지금 문장은 판정이 아는 것(«선택한 화면 중 일부») 만 말한다.
  //    🔵 TASK-MONO-761 — console-web·fan-platform-web(배너 + 팬 로그인 문구)도 같은 첫 문장으로
  //    맞췄다. 세 앱이 같은 판정에 같은 문장을 말한다(`ADR-MONO-068 § D6` 의 취지).
  //    🔴 «장바구니» 를 **안 넣는다** — TASK-FE-102 로 장바구니는 서버 없이(브라우저 저장) 동작하고,
  //    TASK-FE-103 이 꺼짐 배너에서 같은 이유로 뺐다. 이 배너에만 남아 있었다.
  if (probe.kind === 'starting') {
    return (
      <div
        role="status"
        data-testid="demo-backend-starting"
        style={{
          background: '#e0f2fe',
          color: '#075985',
          padding: '10px 16px',
          fontSize: '0.9rem',
          textAlign: 'center',
          borderBottom: '1px solid #7dd3fc',
        }}
      >
        선택한 데모 화면 중 일부가 아직 켜지는 중입니다. 이 스토어는 이미 준비됐을 수 있지만,
        그 전에는 로그인·주문이 실패할 수 있습니다. 실패하면 몇 분 뒤 다시 시도해 주세요.
      </div>
    );
  }

  if (probe.kind !== 'unavailable') return null;

  return (
    <div
      role="status"
      data-testid="demo-backend-notice"
      style={{
        background: '#fef3c7',
        color: '#92400e',
        padding: '10px 16px',
        fontSize: '0.9rem',
        textAlign: 'center',
        borderBottom: '1px solid #fcd34d',
      }}
    >
      {/* 🔴 TASK-MONO-642 — 옛 문구는 «상품 데이터를 불러올 수 없습니다» 였다. 그런데 바로
          아래에 상품이 그려지고 있어서, 방문자에게는 «에러가 났는데 왜인지 뭔가는 보인다»
          로 읽혔다. 거짓은 아니었다(백엔드는 정말 꺼져 있다) — 뜻이 표현에 안 담겼다.
          🔴 «로그인과 로그인 후 기능이 잠겼다» 는 사실은 **남긴다.** 그것까지 지우면 방문자가 장바구니·
          로그인이 왜 안 되는지 모른다. 배너를 부드럽게 만드는 것이 목적이 아니다.
          🔵 「로그인 후 기능」이라는 말은 론처 카드의 「로그인 후」 칸과 **같은 용어**다(TASK-MONO-680 이 「실시간 기능」에서 바꿨다). 두 화면이
          다른 말을 하면 방문자가 어느 쪽을 믿을지 모른다.
          🔴🔴 TASK-MONO-654 — 이 문장은 **옮겨졌을 뿐 바뀌지 않았다.** 배너의 문장은
          방문자와의 계약이고, 이 티켓이 고치는 것은 «언제 판정하는가» 이지 «무엇을
          말하는가» 가 아니다.
          🔴 TASK-FE-103 — «장바구니·주문 같은 로그인 후 기능» 에서 장바구니를 뺐다. TASK-FE-102 로
          장바구니는 로그인 없이(서버도 없이 — 브라우저에만 저장) 쓸 수 있게 됐고, 배너가 «잠겼다» 고
          말하는 동안 실제로는 담기가 된다 — 642 가 고친 «배너가 화면과 어긋난다» 와 같은 결함이다. */}
      지금 보이는 상품은 샘플 데이터입니다. 데모 서버가 꺼져 있어 로그인할 수 없고, 주문 같은 로그인 후
      기능도 잠겨 있습니다. 장바구니는 로그인 없이 담아 둘 수 있습니다. 데모 시작 페이지에서 서버를 켠 뒤(약 10분)
      다시 열어 주세요.
    </div>
  );
}
