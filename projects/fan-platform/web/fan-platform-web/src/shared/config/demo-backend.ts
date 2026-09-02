// DEMO-RESOLVER-CONSUMER: fan-platform-web
//
// =============================================================================
// 데모 백엔드 주소의 런타임 해석 — **구현은 여기 없다**
// =============================================================================
// 구현은 `@demo/backend-resolver` 하나뿐이다 (`ADR-MONO-068 § D6 = B2`,
// `TASK-MONO-614`). 이 파일이 하는 일은 **이 앱의 세 값을 그 구현에 건네는 것**뿐이고,
// 그 셋이 정확히 두 사본이 갈리던 축이었다(2026-09-01 실측: 코드 71줄 중 다른 4줄이
// 전부 이 셋이었다).
//
// 🔴 여기에 로직을 되돌려 놓지 마라 — `scripts/check-demo-resolver-copies.sh` 가
//    **앱 안의 구현**을 RED 로 잡는다. 그 가드의 명제는 «사본이 같은가» 가 아니라
//    **«앱이 자기 구현을 갖지 않는가»** 다.
//
// 🔵 서버 전용이다 — 그리고 팬은 그 경계를 **이미 갖고 있었다**.
//    `TASK-MONO-565` 는 팬에 대해 *"프록시 층 자체가 없다(route.ts 2개) — 경계를 새로
//    만들어야 한다"* 고 적었다. 그 route.ts 계수는 맞지만 **거기서 끌어낸 추론은 틀렸다**:
//    팬의 게이트웨이 호출은 이미 서버 전용이다(`client.ts` 헤더 — 읽기는 RSC, 쓰기는
//    Server Action). 2026-08-26 산출물로 확인했다 — `client.ts` 계열의 특징 문자열
//    `Idempotency-Key` 가 `.next/server` 에 1건, `.next/static` 에 **0건**.
// =============================================================================

import { createDemoBackendResolver } from '@demo/backend-resolver';

export type { DemoBackend, DemoBackendState } from '@demo/backend-resolver';

const resolver = createDemoBackendResolver({
  // 🔴 데모 게이트웨이의 호스트명은 `<prefix>.<DEMO_DOMAIN>` 이고 팬의 접두사는
  //    `fan-platform` 이다(`projects/fan-platform/docker-compose.yml` 의 Traefik 라벨
  //    `Host(fan-platform.<DEMO_DOMAIN>)`).
  // 🔵 `web.fan-platform.<DEMO_DOMAIN>` 은 **화면**의 호스트다. 여기서 필요한 것은
  //    **게이트웨이**이므로 `web.` 접두사가 붙으면 안 된다.
  servicePrefix: 'fan-platform',
  // 🔴 순서를 바꾸지 마라. 그리고 이 사슬은 `shared/config/env.ts` 의 `gatewayInternalUrl`
  //    과 **같은 순서여야 한다** — 같은 사실이 두 곳에 있으면 한쪽만 고쳐진다.
  fallbackEnvNames: ['GATEWAY_URL_INTERNAL', 'NEXT_PUBLIC_GATEWAY_URL'],
  fallbackBaseUrl: 'http://fan-platform.local',
});

export const {
  resolveDemoBackend,
  resolveDemoBackendState,
  resolveUpstreamBaseUrl,
  __resetDemoBackendCache,
} = resolver;
