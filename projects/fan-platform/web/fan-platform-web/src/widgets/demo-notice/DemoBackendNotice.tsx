// DEMO-RESOLVER-CONSUMER: fan-platform-web   (ADR-MONO-068 § D6 = B2 — 구현은 @demo/backend-resolver 하나뿐이다)
//
// 🔵 이 파일은 해석기를 **소비**할 뿐 자기가 주소를 만들지 않는다. 그래도 마커를 단다 —
//    web-store 의 같은 위젯이 적어 둔 이유 그대로다: 가드의 내용 탐지가 이 근처의 낱말에
//    걸리는데 **마커를 안 달고 문구를 바꿔 피하는 쪽이 나쁘다**. 그러면 가드가 아니라
//    가드의 눈을 피하는 습관이 생긴다.
//
// 🔵 승격 트리거는 건드리지 않는다 — `check-demo-resolver-copies.sh` 는 **파일이 아니라
//    앱**을 세고(`PROMOTE_AT_APPS=3`), fan-platform-web 은 `shared/config/demo-backend.ts`
//    로 **이미 2번째 앱**이다. 그리고 동일성 비교 대상은 `IMPL_RE`
//    (`export (async )?function resolveDemoBackend`) 에 걸리는 파일뿐이라 이 위젯은
//    **비교 집합 밖**이다 ⇒ 아래 문구가 web-store 판과 달라도 드리프트가 아니다.
import { resolveDemoBackendState } from '@/shared/config/demo-backend';

/**
 * 데모 백엔드가 꺼져 있을 때 **그렇다고 말한다** (`TASK-MONO-586` AC-3 / `ADR-MONO-067` 단계 4).
 *
 * 🔴 왜 필요한가 — 팬이 Vercel 로 옮겨 가면서 **새로 생긴 상태**다. 예전에는 화면이 데모
 * 호스트의 컨테이너였으므로 백엔드가 꺼지면 화면도 같이 사라졌다. 이제 화면은 Vercel 에서
 * **멀쩡히 뜨고** 데이터만 없다 ⇒ 아무 말도 안 하면 방문자는 **빈 피드를 "고장"** 으로
 * 읽는다.
 *
 * 🔵 서버 컴포넌트다 — 판정이 `DEMO_API_BASE`(비공개 env)에 달려 있고, 그 이름이 클라이언트
 * 번들에 들어가면 안 된다. 팬은 그 경계를 이미 갖고 있다(`demo-backend.ts` 헤더).
 *
 * 🔴 로컬 개발과 CI 에서는 **아무것도 렌더하지 않는다**(`not-demo`). 거기서 "데모가 꺼졌다"
 * 고 말하는 것은 거짓이다 — 애초에 데모가 아니다. 판정을 가르는 것은 백엔드의 상태가 아니라
 * **컨트롤 플레인이 설정돼 있는가**(= 이 배포가 데모인가)이다.
 *
 * 🔴 **`/login` 은 이 위젯이 덮지 않는다 — 알고 비워 둔 자리다.** 데모가 꺼져 있으면 IdP 도
 * 같이 꺼져 있으므로 로그인도 실패하는데, 이 위젯은 `(main)` 셸에만 붙는다. AC-3 이 말하는
 * 것은 *"화면은 뜨고 데이터만 없다"* 이고 로그인 실패는 **다른 증상**이라 다른 처방이
 * 필요하다(그쪽은 `TASK-MONO-574` 의 왕복 측정이 먼저다). 여기서 조용히 같이 처리한 척하면
 * 그 구멍이 안 보이게 된다.
 */
export async function DemoBackendNotice() {
  const state = await resolveDemoBackendState();
  if (state !== 'unavailable') return null;

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
      데모 서버가 꺼져 있어 피드와 아티스트 데이터를 불러올 수 없습니다. 데모 시작
      페이지에서 서버를 켠 뒤(약 10분) 다시 열어 주세요.
    </div>
  );
}
