// DEMO-RESOLVER-CONSUMER: web-store
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
// 🔵 왜 이 파일을 남겨 두는가(소비자가 패키지를 직접 import 하지 않고): 소비자 셋이 이미
//    이 경로를 쓰고, 설정값 셋이 한 자리에 모여 있는 편이 세 소비자에 흩어지는 것보다
//    낫다. 그리고 캐시는 **인스턴스마다** 이므로 팩토리를 앱마다 **한 번만** 부르는 자리가
//    필요하다 — 소비자들이 각자 부르면 캐시가 셋이 되어 컨트롤 API 를 세 배로 때린다.
// =============================================================================

import { createDemoBackendResolver } from '@demo/backend-resolver';

export type { DemoBackend, DemoBackendState } from '@demo/backend-resolver';

const resolver = createDemoBackendResolver({
  // 🔴 데모 게이트웨이의 호스트명은 `<prefix>.<DEMO_DOMAIN>` 이고 ecommerce 의 접두사는
  //    `ecommerce` 다(`infra/demo` 의 Traefik 라벨). 틀리면 DNS 는 풀리고 TCP 도 붙는데
  //    **Traefik 이 라우터를 못 찾아 404** 를 낸다 — 진단이 가장 오래 걸리는 종류다.
  servicePrefix: 'ecommerce',
  // 🔴 순서를 바꾸지 마라. 데모 호스트의 컨테이너 판(`gateway-service:8080`)이
  //    `API_URL_INTERNAL` 로, 로컬 개발(`ecommerce.local`)이 `NEXT_PUBLIC_API_URL` 로 산다.
  fallbackEnvNames: ['API_URL_INTERNAL', 'NEXT_PUBLIC_API_URL'],
  // CI 가 이것으로 산다.
  fallbackBaseUrl: 'http://localhost:8080',
});

export const {
  resolveDemoBackend,
  resolveDemoBackendState,
  resolveUpstreamBaseUrl,
  __resetDemoBackendCache,
} = resolver;
