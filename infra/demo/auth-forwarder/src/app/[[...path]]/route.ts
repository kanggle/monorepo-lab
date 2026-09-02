// DEMO-RESOLVER-CONSUMER: auth-forwarder
//
// =============================================================================
// `auth.hubwang.com` 의 catch-all 포워더 (ADR-MONO-069 § C2 / TASK-MONO-610)
// =============================================================================
// 브라우저 ──HTTPS──> Vercel(이 함수) ──HTTP──> http://iam.<그날 IP>.sslip.io
//
// 🔵 Vercel 이 TLS 를 끝내므로 브라우저는 평문을 **한 번도 보지 않는다**. 그래서
//    인증서도 DNS 쓰기도 필요 없다(`ADR-MONO-069` C2 가 C1 과 갈리는 지점).
//
// -----------------------------------------------------------------------------
// 🔴🔴 이 파일의 두 결정 — 둘 다 `TASK-MONO-610 AC-0` 의 **실측**에서 나왔다
// -----------------------------------------------------------------------------
//  ① **업스트림에 보내는 `Host` 는 `iam.<DEMO_DOMAIN>` 이다** — 공개 이름이 아니다.
//     데모 스택 앞단은 Traefik 이고 라우터를 **`Host` 로** 고른다. `auth.hubwang.com` 을
//     그대로 넘기면 DNS 는 풀리고 TCP 도 붙는데 **Traefik 이 라우터를 못 찾아 404** 를
//     낸다 — 이 저장소에서 진단이 가장 오래 걸리는 종류다(`TASK-MONO-389`).
//     🔵 `fetch()` 는 URL 에서 `Host` 를 스스로 만든다 ⇒ 들어온 `host` 헤더를 **복사하지
//     않는 것**이 곧 ①의 구현이다(아래 HOP_BY_HOP).
//
//  ② **공개 이름은 `X-Forwarded-*` 로 넘긴다.** AC-0 축 2 실측 (2026-09-01):
//
//        X-Forwarded-Proto 없음            → Location: http://auth.hubwang.com/login
//        X-Forwarded-Proto: https          → Location: https://auth.hubwang.com/login   ✅
//
//     🔵 **음성 대조군이 반대로 움직였다** — 빼면 http 로 되돌아간다. 그래서 그 헤더가
//     원인이라고 말할 수 있다. IdP 는 `SERVER_FORWARD_HEADERS_STRATEGY=FRAMEWORK` 로
//     그것을 존중하고, 그 env 가 라우터와 **한 쌍**임을 `verify-demo-wrapper.sh` (l) 이 지킨다.
//     🔴 즉 이 파일의 정확성은 **그 가드가 지키는 조건 위에 서 있다.** 그 env 가 빠지면
//     여기 아무 변화가 없어도 브라우저가 평문으로 튄다.
//
// -----------------------------------------------------------------------------
// 🔴 이 파일이 **하지 않는** 것
// -----------------------------------------------------------------------------
//  - **리다이렉트를 따라가지 않는다** (`redirect: 'manual'`). 302 는 브라우저의 것이다.
//    따라가면 `Location` 이 사라지고 OIDC 흐름이 이 함수 안에서 끝나 버린다.
//  - **데모가 꺼졌을 때 업스트림을 지어내지 않는다.** 해석기의 폴백 사슬
//    (`resolveUpstreamBaseUrl()`)을 **부르지 않는 이유가 그것이다** — 앱에게 «기존 env 로
//    가라» 는 옳지만, IdP 의 현관에게 그것은 «없는 호스트로 15초 동안 DNS 를 시도하라» 다.
//    ⇒ 정의된 화면을 낸다(`ADR-MONO-069` V7: *"502 가 아니라 정의된 화면"*).
//  - **본문을 해석하지 않는다.** 그대로 흘려보낸다.
// =============================================================================

import { createDemoBackendResolver } from '@demo/backend-resolver';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

/**
 * 🔵 `servicePrefix: 'iam'` — 데모의 IdP 호스트는 `iam.<DEMO_DOMAIN>` 이다
 * (`infra/demo/iam-traefik.override.yml` 의 Traefik 라벨이 권위).
 *
 * 🔴 `fallbackEnvNames` / `fallbackBaseUrl` 은 `resolveUpstreamBaseUrl()` **전용**이고
 * 이 앱은 그 함수를 **부르지 않는다**(위 § 하지 않는 것). 값을 비워 두지 않고 데모 스택과
 * 같은 이름으로 맞춰 둔 이유는, 나중에 누군가 그 함수를 부르게 되더라도 **엉뚱한 곳이
 * 아니라 데모가 실제로 쓰는 이름**으로 떨어지게 하기 위해서다.
 */
const resolver = createDemoBackendResolver({
  servicePrefix: 'iam',
  fallbackEnvNames: ['IAM_ORIGIN_INTERNAL'],
  fallbackBaseUrl: 'http://iam.local',
});

/**
 * 업스트림으로 **복사하지 않는** 요청 헤더.
 *
 * - `host` — ① 의 구현. 이것을 지워야 `fetch()` 가 업스트림 URL 로 `Host` 를 만든다.
 * - `accept-encoding` — 클라이언트의 값을 그대로 넘기지 않는다.
 *   🔴🔴 **그렇다고 업스트림이 identity 를 받는 것은 아니다** (2026-09-02 실측):
 *   undici(`fetch`)가 **자기 값을 스스로 붙인다**. 즉 이 줄은 «압축이 없다» 를 만들지
 *   못한다 — 그 사실이 아래 `content-encoding` 처리의 이유다.
 * - 나머지는 홉 단위 헤더(RFC 9110 § 7.6.1)라 프록시를 건너면 안 된다.
 */
const HOP_BY_HOP_REQUEST = new Set([
  'connection',
  'keep-alive',
  'proxy-authenticate',
  'proxy-authorization',
  'te',
  'trailer',
  'transfer-encoding',
  'upgrade',
  'host',
  'content-length',
  'accept-encoding',
]);

/**
 * 업스트림 응답에서 **다시 만들거나 버리는** 헤더.
 *
 * 🔴 `set-cookie` 가 여기 있는 이유가 중요하다 — `Headers.forEach` 는 여러 개의
 * `Set-Cookie` 를 **하나의 문자열로 합친다**. 합쳐진 쿠키는 브라우저가 하나로 읽어
 * **세션이 조용히 사라진다**. 그래서 `getSetCookie()` 로 따로 꺼내 `append` 한다.
 *
 * 🔴🔴 `content-encoding` 과 `content-length` 도 **반드시** 버려야 한다 (2026-09-02 실측):
 * undici 는 자기가 붙인 `Accept-Encoding` 때문에 본문을 **자동으로 해제하면서도
 * 헤더는 남긴다.**
 *
 *     upstream: content-encoding: gzip · content-length: 35 · body(gzip 15바이트 원문)
 *     fetch() : content-encoding: **gzip 그대로** · content-length: **35 그대로**
 *               · body = "HELLO-GZIP-BODY" (**이미 해제됨**)
 *
 * ⇒ 그대로 통과시키면 브라우저가 **평문을 gzip 으로 읽으려다 실패**하고, 길이도 어긋난다.
 * 증상은 «프록시가 죽었다» 가 아니라 **«어떤 페이지만 깨진다»** 라 원인을 못 찾는다.
 */
const RESPONSE_DROP = new Set([
  'set-cookie',
  'connection',
  'keep-alive',
  'transfer-encoding',
  'content-length',
  'content-encoding',
]);

/** 본문을 가질 수 없는 메서드. */
const BODYLESS = new Set(['GET', 'HEAD']);

function demoOffResponse(state: 'not-demo' | 'unavailable'): Response {
  const title =
    state === 'not-demo'
      ? '이 배포에는 데모 컨트롤 플레인이 설정되어 있지 않습니다'
      : '데모 백엔드가 지금 꺼져 있습니다';
  const detail =
    state === 'not-demo'
      ? 'DEMO_API_BASE 가 없습니다. 이 배포는 데모 IdP 를 가리킬 수 없습니다.'
      : '데모 인스턴스가 실행 중이 아니거나 주소를 확정할 수 없습니다. 데모를 켠 뒤 다시 시도하세요.';

  // 🔵 502 가 아니라 **정의된 화면**이다 (ADR-MONO-069 V7). 503 + Retry-After 는
  //    "지금은 없지만 영구적이지 않다" 를 기계에게도 말해 준다.
  return new Response(
    `<!doctype html><meta charset="utf-8"><title>${title}</title>` +
      `<div style="font:16px/1.6 system-ui,sans-serif;max-width:34rem;margin:12vh auto;padding:0 1rem">` +
      `<h1 style="font-size:1.25rem">${title}</h1><p>${detail}</p></div>`,
    {
      status: 503,
      headers: {
        'content-type': 'text/html; charset=utf-8',
        'cache-control': 'no-store',
        'retry-after': '60',
      },
    },
  );
}

async function forward(req: Request, path: string[]): Promise<Response> {
  const demo = await resolver.resolveDemoBackend();
  if (!demo) return demoOffResponse(await resolver.resolveDemoBackendState() === 'not-demo' ? 'not-demo' : 'unavailable');

  const incoming = new URL(req.url);
  const target = new URL(`${demo.baseUrl}/${path.join('/')}`);
  target.search = incoming.search;

  const headers = new Headers();
  req.headers.forEach((value, key) => {
    if (!HOP_BY_HOP_REQUEST.has(key.toLowerCase())) headers.set(key, value);
  });

  // ② 공개 이름을 **여기서만** 말한다. 🔵 상수로 박지 않고 요청이 들고 온 이름을 쓰는
  //    이유: preview 배포에서도 자기 이름으로 동작해야 하기 때문이다. 프로덕션에서는
  //    이 값이 `auth.hubwang.com` 이다.
  //
  // 🔴🔴 **`new URL(req.url).host` 를 쓰면 안 된다** (2026-09-02, 로컬 하네스가 잡았다).
  //    그것은 들어온 `Host` 헤더가 아니라 **런타임이 만든 요청 URL 의 host** 다 —
  //    실측: `Host: 127.0.0.1:3003` 으로 불렀는데 `new URL(req.url).host` 는
  //    `localhost:3003` 이었다. Vercel 에서는 커스텀 도메인이 아니라 **배포 URL** 이 될 수
  //    있고, 그러면 IdP 가 `https://<deployment>.vercel.app/login` 을 광고한다 —
  //    `auth.hubwang.com` 을 만들려던 이 프로젝트의 **존재 이유가 무너지는** 자리다.
  //    ⇒ 헤더를 **직접** 읽는다. 없을 때만(HTTP/1.0 등) URL 로 떨어진다.
  const publicHost = req.headers.get('host') ?? incoming.host;
  headers.set('x-forwarded-proto', 'https');
  headers.set('x-forwarded-host', publicHost);
  headers.set('x-forwarded-port', '443');

  const body = BODYLESS.has(req.method) ? undefined : await req.arrayBuffer();

  const upstream = await fetch(target, {
    method: req.method,
    headers,
    body,
    redirect: 'manual',
    cache: 'no-store',
  });

  const out = new Headers();
  upstream.headers.forEach((value, key) => {
    if (!RESPONSE_DROP.has(key.toLowerCase())) out.set(key, value);
  });
  for (const cookie of upstream.headers.getSetCookie()) out.append('set-cookie', cookie);

  return new Response(upstream.body, {
    status: upstream.status,
    statusText: upstream.statusText,
    headers: out,
  });
}

type Ctx = { params: Promise<{ path?: string[] }> };

async function handler(req: Request, ctx: Ctx): Promise<Response> {
  const { path } = await ctx.params;
  return forward(req, path ?? []);
}

export const GET = handler;
export const HEAD = handler;
export const POST = handler;
export const PUT = handler;
export const PATCH = handler;
export const DELETE = handler;
export const OPTIONS = handler;
