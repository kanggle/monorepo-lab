// DEMO-RESOLVER-CONSUMER: console-web
//
// =============================================================================
// 데모 백엔드 주소의 런타임 해석 — **구현은 여기 없다** (TASK-MONO-585 / ADR-MONO-067 D2)
// =============================================================================
// 구현은 `@demo/backend-resolver` 하나뿐이다 (`ADR-MONO-068 § D6 = B2`,
// `TASK-MONO-614`). 이 파일이 하는 일은 두 가지다:
//   ① 이 앱의 세 값을 그 구현에 건네고,
//   ② 콘솔에만 있는 축 하나를 더한다 — **백엔드가 여섯 곳**이라는 것.
//
// 🔴 여기에 해석 로직을 되돌려 놓지 마라 — `scripts/check-demo-resolver-copies.sh` 가
//    **앱 안의 구현**을 RED 로 잡는다. 그 가드의 명제는 «사본이 같은가» 가 아니라
//    **«앱이 자기 구현을 갖지 않는가»** 다. 지문 중 하나가 «컨트롤 플레인 베이스 주소를
//    환경변수로 직접 읽는 행위» 이므로, 그 값을 이 파일에서 읽어도 RED 다.
//
// 🔵 그리고 그 지문은 **산문에도 걸린다**: 이 주석의 초판이 그 env 이름을
//    `process.env.` 접두사까지 붙여 그대로 적었다가 가드를 발화시켰다(2026-09-05).
//    가드가 옳게 문 것이 아니라 내가 지문을 문서에 적은 것이다 — 그래서 이름을 풀어 썼다.
//
// -----------------------------------------------------------------------------
// 🔴🔴 콘솔은 형제 둘과 다르다 — 업스트림이 **하나가 아니라 여섯**이다
// -----------------------------------------------------------------------------
// web-store 와 fan 은 게이트웨이가 하나라 `resolveUpstreamBaseUrl()` 한 줄로 끝난다.
// 콘솔은 `iam`·`wms`·`scm`·`finance`·`erp`·`ecommerce` 여섯 접두사를 부르고, 그 주소가
// `env.ts` 에 **12개의 서로 다른 URL** 로 흩어져 있다(호스트 7종 · 경로 포함 12건).
//
// 🔴 그렇다고 해석기 인스턴스를 여섯 개 만들지 않는다. `resolveDemoBackend()` 는
//    `demoDomain` 을 **함께** 돌려주고(그 필드의 JSDoc 이 *"다른 서비스 호스트를 조립할
//    때 쓴다"* 라고 적어 둔 그대로다), 인스턴스마다 캐시가 따로이므로 여섯 개를 만들면
//    한 요청에 컨트롤 플레인 `/status` 를 최대 6회 때린다. 인스턴스는 **하나**고,
//    나머지 다섯 호스트는 그 `demoDomain` 에서 조립한다.
//
// -----------------------------------------------------------------------------
// 🔴🔴 매핑의 출처는 하드코딩한 표가 아니라 **기존 env 값 자신**이다
// -----------------------------------------------------------------------------
// 「접두사 → 데모 호스트」 표를 새로 쓰면 같은 사실이 두 곳에 생기고 한쪽만 고쳐진다
// (그리고 여섯 중 하나를 빠뜨리면 **그 화면만** 죽어 원인이 안 보인다 — 이 티켓의
// Edge Case 가 그것이다). 그래서 표를 안 쓴다:
//
//   `.local` 은 우연이 아니라 **`${DEMO_DOMAIN:-local}` 의 기본값**이다.
//
// 실측(2026-09-05) — 각 프로젝트 compose 의 Traefik 라우터 규칙:
//   iam-traefik.override.yml   Host(`iam.${DEMO_DOMAIN:-local}`)
//   wms/scm/erp/finance/…      Host(`{wms,scm,erp,finance}.${DEMO_DOMAIN:-local}`)
//   ecommerce                  Host(`ecommerce.${DEMO_DOMAIN:-local}`)
//   platform-console           Host(`console.${DEMO_DOMAIN:-local}`)
//
// ⇒ 데모에서의 주소는 **설정된 값의 호스트 꼬리 `.local` 을 `.<demoDomain>` 으로 바꾼
//   것**과 정확히 같다. 접두사도, 경로도, 스킴도 값 자신이 들고 있다. 새 백엔드가 하나
//   더 늘어도 이 파일은 **안 바뀐다**.
//
// 🔴 그래서 이 파일이 지키는 불변식은 «여섯을 다 적었는가» 가 아니라
//    **«`.local` 을 그대로 부르는 자리가 없는가»** 이고, 그것은 세는 것이 가능하다 —
//    `scripts/check-console-backend-urls.sh` 가 그 축을 문다.
//
// -----------------------------------------------------------------------------
// 🔵 `OIDC_ISSUER_URL` 은 **일부러 이 축에 안 태운다**
// -----------------------------------------------------------------------------
// 발급자는 데모 IP 에서 파생되지 않는다 — `ADR-MONO-069` 가 `C2` 로 **고정된 이름**
// (`https://auth.hubwang.com`)을 지정했고, 그것이 그 결정의 요지다. 그리고 발급자
// 문자열은 토큰의 `iss` 클레임과 **문자 비교**되므로 조용히 고쳐 쓰면 안 된다.
// 🔵 실무적으로도 무해한 no-op 이 아니다: 아래 `demoizeUrl` 은 `.local` 에만 반응하는데
//    Vercel 의 발급자는 `.local` 이 아니고, 데모 컨테이너의 발급자는 이미 `DEMO_DOMAIN`
//    형태로 주입된다(`demo.env`). 즉 «태워도 안 바뀐다» 가 아니라 **태우면 안 되는 값**이다.
// =============================================================================

import { createDemoBackendResolver } from '@demo/backend-resolver';

export type { DemoBackend, DemoBackendState } from '@demo/backend-resolver';

const resolver = createDemoBackendResolver({
  // 🔴 데모 게이트웨이의 호스트명은 `<prefix>.<DEMO_DOMAIN>` 이고 콘솔의 접두사는
  //    `console` 이다(`projects/platform-console/docker-compose.yml` 의 Traefik 라벨
  //    `Host(console.${DEMO_DOMAIN:-local})`).
  // 🔵 콘솔에서 이 `baseUrl` 을 실제로 쓰는 곳은 없다 — 콘솔은 자기 자신을 부르지
  //    않는다(그 몫은 `self-origin.ts`). 필요한 것은 같은 왕복이 돌려주는
  //    `demoDomain` 이고, `servicePrefix` 는 그 왕복의 필수 입력이다.
  servicePrefix: 'console',
  // 🔴 순서를 바꾸지 마라. 이 사슬은 `self-origin.ts` 의 순서와 **같아야 한다** —
  //    같은 사실이 두 곳에 있으면 한쪽만 고쳐진다.
  fallbackEnvNames: ['CONSOLE_PUBLIC_ORIGIN', 'NEXT_PUBLIC_APP_URL'],
  fallbackBaseUrl: 'http://console.local',
});

export const {
  resolveDemoBackend,
  resolveDemoBackendState,
  resolveUpstreamBaseUrl,
  __resetDemoBackendCache,
} = resolver;

/**
 * 호스트 꼬리가 `.local` 인 URL 을 `.<demoDomain>` 으로 바꾼다. 그 밖은 **그대로 둔다**.
 *
 * 🔴 문자열 치환이다. `new URL(...).toString()` 을 쓰면 안 된다 — 그것은
 * `http://iam.local` 을 `http://iam.local/` 로 **정규화**하고, 그러면 호출부의
 * `` `${base}/api/admin/…` `` 가 `//api/admin/…` 이 된다(Traefik 404, 진단이 가장 오래
 * 걸리는 종류다). 여기서 바꾸는 것은 호스트 한 조각뿐이고 나머지 바이트는 안 건드린다.
 *
 * 🔵 `.local` 이 아닌 값(Vercel 의 `https://…`, 컨테이너 DNS 인 `http://console-bff:8080`)
 * 은 그대로 통과한다 — 「데모가 아닌 배포에서는 아무 일도 안 일어난다」가 요구사항이다.
 */
export function demoizeUrl(url: string, demoDomain: string): string {
  // `^스킴://` 로 시작해서 **첫 `/?#` 이전**(= 호스트 구간)이 `.local` 로 끝나는 경우만.
  // 뒤에 오는 것은 문자열 끝이거나 포트(`:`)·경로(`/`)·쿼리(`?`)·프래그먼트(`#`) 다.
  return url.replace(
    /^(https?:\/\/)([^/?#]*\.)local(?=$|[:/?#])/i,
    (_m, scheme: string, prefixDot: string) =>
      `${scheme}${prefixDot}${demoDomain}`,
  );
}

/**
 * 설정된 백엔드 URL 하나를 **런타임에** 실제로 부를 주소로 바꾼다.
 *
 * - `DEMO_API_BASE` 부재(로컬·CI·컨테이너 데모) → **아무것도 안 한다**. 설정값 그대로.
 * - `state != running` → 주소를 안 만든다 ⇒ 설정값 그대로(그리고 그 값은 Vercel 에서
 *   닿지 않는다 — 그 상태를 화면이 말하는 것이 `DemoBackendNotice` 의 몫이다).
 * - `/status` 실패 → 판정 불가 ⇒ 설정값 그대로. 「꺼짐」으로도 「켜짐」으로도 번역하지 않는다.
 *
 * 🔴 조용히 옛 IP 로 붙는 것이 가장 나쁘다는 판단은 해석기 쪽에 있다(그 파일의 헤더 ②).
 * 여기서 다시 정하지 않는다.
 */
export async function resolveBackendUrl(configured: string): Promise<string> {
  const demo = await resolveDemoBackend();
  return demo ? demoizeUrl(configured, demo.demoDomain) : configured;
}
