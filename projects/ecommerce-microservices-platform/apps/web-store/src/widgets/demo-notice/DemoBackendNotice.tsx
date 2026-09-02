// DEMO-RESOLVER-CONSUMER: web-store   (ADR-MONO-068 § D6 = B2 — 구현은 @demo/backend-resolver 하나뿐이다)
//
// 🔵 이 파일은 해석기를 **소비**할 뿐 자기가 주소를 만들지 않는다. 그래도 마커를 단다:
//    가드의 내용 탐지는 `DEMO_API_BASE` 라는 문자열을 보고, 아래 주석이 그 이름을 설명하기
//    때문에 걸린다. **마커를 안 달고 문구를 바꿔 피하는 쪽이 나쁘다** — 그러면 가드가
//    아니라 가드의 눈을 피하는 습관이 생긴다. 같은 앱이므로 승격 트리거의 개수는 그대로 1 이다.
import { resolveDemoBackendState } from '@/shared/config/demo-backend';

/**
 * 데모 백엔드가 꺼져 있을 때 **그렇다고 말한다** (TASK-MONO-580 / ADR-MONO-067 AC-4).
 *
 * 🔴 왜 필요한가 — 이 화면이 Vercel 로 옮겨 가면서 **새로 생긴 상태**다. 예전에는 스토어가
 * 데모 호스트의 컨테이너였으므로 백엔드가 꺼지면 화면도 같이 사라졌다. 이제 화면은 Vercel
 * 에서 **멀쩡히 뜨고** 데이터만 없다 ⇒ 아무 말도 안 하면 방문자는 **빈 목록을 "고장"** 으로
 * 읽는다. `ADR-MONO-067` § Consequences 가 이것을 "새 요구" 라고 적어 뒀다.
 *
 * 🔵 서버 컴포넌트다 — 판정이 `DEMO_API_BASE`(비공개 env)에 달려 있고, 그 이름이 클라이언트
 * 번들에 들어가면 안 된다(D1).
 *
 * 🔴 로컬 개발과 CI 에서는 **아무것도 렌더하지 않는다**(`not-demo`). 거기서 "데모가 꺼졌다"
 * 고 말하는 것은 거짓이다 — 애초에 데모가 아니다.
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
      데모 서버가 꺼져 있어 상품 데이터를 불러올 수 없습니다. 데모 시작 페이지에서
      서버를 켠 뒤(약 10분) 다시 열어 주세요.
    </div>
  );
}
