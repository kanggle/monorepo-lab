// DEMO-RESOLVER: @demo/backend-resolver   (ADR-MONO-068 § D6 = B2 — 이것이 유일한 구현이다)
//
// =============================================================================
// 데모 백엔드 주소를 **런타임에** 얻는다 (ADR-MONO-067 D2)
// =============================================================================
// 왜 런타임인가: 데모 호스트의 공인 IP 는 **부팅마다 바뀐다**(하루에 세 번 바뀌는 것을
// 관측했다). 그런데 Next 는 `NEXT_PUBLIC_*` 을 **빌드 타임에 인라인**하므로, 주소를 env 로
// 받으면 그 값은 **구워진 순간부터 썩는다.**
//
// ⇒ 서버가 요청 시점에 컨트롤 플레인 `/status` 에 물어서 조립한다.
//    `{state, ip, ...}` → `DEMO_DOMAIN = <ip-대시>.sslip.io` → `http://<prefix>.<DEMO_DOMAIN>`
//
// -----------------------------------------------------------------------------
// 🔴 왜 이 파일이 여기 있는가 — 자리 선택의 근거는 README.md 에 있다
// -----------------------------------------------------------------------------
// 요약만: `DEMO_API_BASE` 라는 **계약**을 정하는 것이 `infra/demo/aws/site/build.sh` 이고,
// 그 계약의 다른 클라이언트(론처 `index.html`)도 `infra/demo/` 안에 산다. 계약과 그
// 클라이언트들이 한 디렉터리에 있는 편이, 공유 코드라는 이유만으로 새 최상위 디렉터리를
// 만드는 것보다 낫다. 기각한 후보 셋과 각각의 이유는 README.md § 자리.
//
// -----------------------------------------------------------------------------
// 🔴 이 모듈이 **하지 않는** 것 세 가지 — 각각 실패 모드가 있다
// -----------------------------------------------------------------------------
//  1. `DEMO_API_BASE` 가 없으면 **아무것도 하지 않는다**(null 반환). 로컬 개발과 CI 에는
//     컨트롤 플레인이 없다 — 여기서 죽으면 이 모듈이 **로컬을 깬다**. 호출자는 null 을
//     받으면 기존 env 사슬로 간다.
//  2. `state` 가 `running` 이 아니면 **주소를 만들지 않는다**. 조용히 옛 IP 로 붙는 것이
//     가장 나쁘다 — 그 IP 는 이미 **남의 인스턴스**일 수 있다(AWS 가 회수해 재할당한다).
//  3. `/status` 가 실패하면(5xx·타임아웃·비-JSON) **기존 동작으로 떨어진다**. 판정 불가를
//     "꺼짐" 으로도 "켜짐" 으로도 번역하지 않는다.
//
// -----------------------------------------------------------------------------
// 🔵 서버 전용이다
// -----------------------------------------------------------------------------
// 이 모듈은 브라우저에서 부르면 안 된다. 브라우저는 상대경로(`/api/bff` 등)만 알고(D1),
// 백엔드 오리진을 **모르는 것이 요구사항**이다. 클라이언트 번들에 들어가지 않도록
// 클라이언트 컴포넌트에서 임포트하지 마라.
//
// -----------------------------------------------------------------------------
// 🔴 왜 팩토리인가 — 모듈 스코프 싱글턴이 아니다
// -----------------------------------------------------------------------------
// 캐시는 **인스턴스마다** 따로다. 한 프로세스가 두 접두사(예: 포워더 + 스토어)를 동시에
// 해석할 수 있고, 그때 캐시를 공유하면 한쪽의 `baseUrl` 이 다른 쪽에 새어 나간다.
// 🔵 대신 소비자는 자기 모듈 스코프에서 팩토리를 **한 번** 부르고 그 결과를 재수출한다 —
//    그래야 옛 판(모듈 스코프 캐시)과 캐시 수명이 같다.
// =============================================================================

/** `/status` 가 돌려주는 것 중 이 모듈이 쓰는 필드. 나머지는 무시한다. */
interface DemoStatus {
  state?: unknown;
  ip?: unknown;
}

export interface DemoBackend {
  /** 예: `http://ecommerce.13-125-1-2.sslip.io` */
  baseUrl: string;
  /** 예: `13-125-1-2.sslip.io` — 다른 서비스 호스트를 조립할 때 쓴다. */
  demoDomain: string;
}

/**
 * 화면이 *"데모 백엔드가 꺼져 있다"* 를 **표현**할 수 있게 하는 판정.
 *
 * 🔴 `resolveDemoBackend()` 의 `null` 을 그대로 "꺼짐" 으로 읽으면 안 된다 — 로컬 개발과
 * CI 도 `null` 이고, 거기서 "데모가 꺼졌습니다" 배너를 띄우면 **거짓말**이다. 가르는 것은
 * **컨트롤 플레인이 설정돼 있는가**(= 이 배포가 데모인가)이다.
 *
 * `ADR-MONO-067` § Consequences 가 이것을 **새 요구**라고 적었다: *"데모가 꺼져 있어도
 * 화면 자체는 뜬다(백엔드 없는 상태를 앱이 표현해야 한다)."* 예전에는 데모 호스트에서
 * 같이 죽었으므로 표현할 필요가 없었다.
 */
export type DemoBackendState = 'not-demo' | 'running' | 'unavailable';

/**
 * 앱마다 다른 **세 가지**. 이 셋이 정확히 `ADR-MONO-068 § D5.1` 의 정규화가 지우던 축이고,
 * 2026-09-01 실측으로 두 사본의 **코드 71줄 중 다른 4줄 전부**였다.
 * 🔴 넷째 축이 생기면 그것은 «설정» 이 아니라 **동작 분기**다 — 그때는 이 인터페이스를
 *    늘리기 전에 `ADR-MONO-068` 을 다시 열어라.
 */
export interface DemoBackendResolverConfig {
  /**
   * 데모 게이트웨이의 서비스 접두사 — `<prefix>.<DEMO_DOMAIN>`.
   *
   * 🔴 이 문자열이 틀리면 DNS 는 풀리고 TCP 도 붙는데 **Traefik 이 라우터를 못 찾아 404**
   * 를 낸다 — 진단이 가장 오래 걸리는 종류다(`TASK-MONO-389` 가 점/대시 표기로 같은 것을
   * 밟았다). 값의 권위는 그 프로젝트의 compose Traefik 라벨이다.
   */
  servicePrefix: string;
  /**
   * 해석이 안 됐을 때 순서대로 볼 env 이름들.
   *
   * 🔴 순서를 바꾸지 마라. 이 사슬이 로컬 개발과 CI 와 데모 호스트의 컨테이너 판을 동시에
   * 지탱한다. 데모 해석은 이 사슬의 **맨 앞에 한 칸**을 더할 뿐이다.
   */
  fallbackEnvNames: readonly string[];
  /** 사슬이 전부 비었을 때의 값. CI 가 이것으로 산다. */
  fallbackBaseUrl: string;
}

export interface DemoBackendResolver {
  resolveDemoBackend(): Promise<DemoBackend | null>;
  resolveDemoBackendState(): Promise<DemoBackendState>;
  resolveUpstreamBaseUrl(): Promise<string>;
  /** 테스트 전용 — 칸 사이에 캐시가 새지 않게 한다. */
  __resetDemoBackendCache(): void;
}

/** 컨트롤 플레인 왕복이 이보다 오래 걸리면 포기하고 기존 동작으로 간다. */
const STATUS_TIMEOUT_MS = 2_000;

/**
 * 캐시 TTL.
 *
 * 🔵 왜 15초인가: 데모 IP 는 **부팅 시점에만** 바뀐다(실행 중에는 고정). 그러니 TTL 은
 * "얼마나 자주 바뀌나" 가 아니라 **"바뀐 뒤 얼마나 빨리 따라가야 하나"** 로 정한다.
 * 부팅은 약 11분이 걸리고 그동안 화면은 어차피 못 뜬다 ⇒ 15초면 사용자가 체감하기 전에
 * 따라간다. 반대로 이보다 짧으면 페이지 하나 렌더에 컨트롤 API 를 여러 번 때린다.
 */
const CACHE_TTL_MS = 15_000;

function controlPlaneBase(): string | null {
  const raw = process.env.DEMO_API_BASE;
  if (typeof raw !== 'string') return null;
  // 🔴🔴 **공백을 먼저 턴다** (TASK-MONO-635). 이전 판은 `!raw` 로만 걸러서 `'   '` 를
  //    **참으로 통과시켰고**, 그 값이 그대로 `${base}/heartbeat` 에 붙어 `   /heartbeat`
  //    라는 쓰레기 URL 이 됐다. 빈 문자열은 막으면서 공백 문자열은 못 막는 것은
  //    «없음» 의 정의가 반쪽이라는 뜻이다.
  // 🔵 이 결함을 잡은 것은 콘솔 라우트가 갖고 있던 더 꼼꼼한 사본이다 — 그 사본을 여기로
  //    접으면서 그쪽의 `.trim()` 을 함께 가져왔다. 통합이 성질을 **잃지 않게** 하는 것이
  //    통합의 조건이고, 그것을 지킨 자리가 여기다(테스트가 그 한 칸을 지키고 있었다).
  const trimmed = raw.trim().replace(/\/+$/, '');
  return trimmed.length > 0 ? trimmed : null;
}

/**
 * 부팅 경로와 **같은 규칙**으로 도메인을 만든다: 점을 대시로 바꾸고 `sslip.io` 를 붙인다.
 *
 * 🔴 `infra/demo/demo-boot.sh` 와 론처 `index.html` 이 같은 파생을 한다. 세 곳이 어긋나면
 * DNS 는 풀리는데 Traefik 이 404 를 낸다 — 그래서 `verify-demo-wrapper.sh` (t) 가 페이지와
 * 부팅을 대조한다. 이 파일은 그 가드의 모집단이 아니므로, 규칙을 바꿀 일이 생기면
 * **세 곳을 함께** 고쳐야 한다.
 */
function demoDomainFromIp(ip: string): string {
  return `${ip.replace(/\./g, '-')}.sslip.io`;
}

function isPlausibleIpv4(value: unknown): value is string {
  return typeof value === 'string' && /^\d{1,3}(\.\d{1,3}){3}$/.test(value);
}

async function fetchStatus(base: string): Promise<DemoStatus | null> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), STATUS_TIMEOUT_MS);
  try {
    const res = await fetch(`${base}/status`, {
      cache: 'no-store',
      signal: controller.signal,
    });
    if (!res.ok) return null;
    return (await res.json()) as DemoStatus;
  } catch {
    // 타임아웃 · 네트워크 실패 · 비-JSON 전부 여기로 온다. 판정 불가다.
    return null;
  } finally {
    clearTimeout(timer);
  }
}

/**
 * 한 앱의 해석기를 만든다.
 *
 * 🔴 반환하는 함수들은 **클로저**다(`this` 를 안 쓴다) — 소비자가
 * `export const { resolveDemoBackend } = createDemoBackendResolver(...)` 처럼 **떼어내
 * 재수출**하기 때문이다. 메서드로 두면 그 자리에서 `this` 가 사라져 조용히 깨진다.
 */
export function createDemoBackendResolver(
  config: DemoBackendResolverConfig,
): DemoBackendResolver {
  let cache: { at: number; value: DemoBackend | null } | null = null;

  const resolveDemoBackend = async (): Promise<DemoBackend | null> => {
    const base = controlPlaneBase();
    if (!base) return null;

    const now = Date.now();
    if (cache && now - cache.at < CACHE_TTL_MS) return cache.value;

    const status = await fetchStatus(base);
    let value: DemoBackend | null = null;

    // 🔴 `state` 와 `ip` 를 **둘 다** 요구한다. `state=running` 인데 `ip` 가 없는 반쪽
    //    응답으로 주소를 만들면, 만들어진 주소가 무엇을 가리키는지 아무도 모른다.
    if (status && status.state === 'running' && isPlausibleIpv4(status.ip)) {
      const demoDomain = demoDomainFromIp(status.ip);
      value = {
        baseUrl: `http://${config.servicePrefix}.${demoDomain}`,
        demoDomain,
      };
    }

    cache = { at: now, value };
    return value;
  };

  const resolveDemoBackendState = async (): Promise<DemoBackendState> => {
    if (!controlPlaneBase()) return 'not-demo';
    return (await resolveDemoBackend()) ? 'running' : 'unavailable';
  };

  const resolveUpstreamBaseUrl = async (): Promise<string> => {
    const demo = await resolveDemoBackend();
    if (demo) return demo.baseUrl;
    // 🔴 `??` 와 **같은 의미**여야 한다 — 빈 문자열은 nullish 가 아니므로 사슬을 멈춘다.
    //    `||` 로 바꾸면 `FOO=''` 인 배포에서 폴백이 한 칸 더 흐른다.
    for (const name of config.fallbackEnvNames) {
      const value = process.env[name];
      if (value !== undefined && value !== null) return value;
    }
    return config.fallbackBaseUrl;
  };

  const __resetDemoBackendCache = (): void => {
    cache = null;
  };

  return {
    resolveDemoBackend,
    resolveDemoBackendState,
    resolveUpstreamBaseUrl,
    __resetDemoBackendCache,
  };
}

// =============================================================================
// 컨트롤 플레인 — `DEMO_API_BASE` 를 읽는 **유일한 자리** (TASK-MONO-635)
// =============================================================================
// 🔴🔴 왜 이것이 여기 있는가 — 세 앱이 각자 읽으려다 가드에 물렸다.
//
// 세 프런트엔드가 «로그인한 세션이 데모를 살려 둔다» 는 heartbeat 중계를 갖게 됐고
// (ADR-MONO-071 § D8), 셋 다 `process.env.DEMO_API_BASE` 를 직접 읽었다. 그러자
// `scripts/check-demo-resolver-copies.sh` 가 셋을 전부 물었다 — 그 가드의 구현 지문 ①이
// 정확히 그 리터럴이기 때문이다(`ADR-MONO-068 § D6 = B2`).
//
// 🔵 **가드가 옳다.** 그 라우트들이 주소를 «해석» 하지는 않지만, `DEMO_API_BASE` 는
//    이 패키지가 소유하는 **계약**이고 그것을 세 곳에서 읽으면 계약이 네 집을 갖는다.
//    형태가 바뀌는 날(예: 끝 슬래시 정책, 다른 이름) 한 곳만 고쳐진다.
//
// 🔴 그래서 **지문을 문법으로 피하지 않았다.** 상수로 감싸 `process.env[NAME]` 로 쓰면
//    가드는 통과하지만 중복은 그대로 남는다 — 그것은 고침이 아니라 회피다.
//    (그 회피를 실제로 시도한 판이 있었고, 그 파일 자신이 "옳은 모양은 이것이 아니다"
//     라고 적어 두었다. 그 부채를 여기서 갚는다.)
// =============================================================================

/**
 * 컨트롤 플레인의 베이스 URL. 설정이 없으면 `null` — **결함이 아니다**(로컬·CI·비데모 배포).
 *
 * 🔵 계약을 정하는 것은 `infra/demo/aws/site/build.sh` 이고, 읽는 것은 이 함수 하나다.
 */
export function controlPlaneBaseUrl(): string | null {
  return controlPlaneBase();
}

/** `sendDemoHeartbeat()` 의 결과. 🔴 세 값이 **서로 다른 사실**이라 뭉치지 않는다. */
export type DemoHeartbeatOutcome =
  /** 보냈다(2xx). */
  | 'sent'
  /** 이 배포는 데모가 아니다 — `DEMO_API_BASE` 가 없다. 살려 둘 인스턴스 자체가 없다. */
  | 'not-demo'
  /** 데모인데 못 보냈다(타임아웃·네트워크·5xx). 화면은 안 죽는다. */
  | 'failed';

/** 컨트롤 플레인 왕복이 이보다 오래 걸리면 포기한다. 하트비트 실패로 화면이 죽으면 안 된다. */
const HEARTBEAT_TIMEOUT_MS = 2_000;

/**
 * 데모 EC2 의 유휴 타이머를 미룬다.
 *
 * 🔴🔴 **호출자가 «로그인했는가» 를 먼저 판정해야 한다.** 이 함수는 그것을 모른다 —
 *    알면 안 된다(앱마다 세션 기전이 다르다: next-auth 둘, 자체 쿠키 하나). 익명 방문자의
 *    열린 탭이 EC2 예산을 태우는 것을 막는 것은 **호출부의 책임**이고, 세 앱의 라우트
 *    핸들러가 서버에서 그 판정을 한다.
 * 🔵 그 분리가 곧 «공개 둘러보기는 heartbeat 를 안 보낸다»(ADR-MONO-071 § D8)의 구현이다.
 */
export async function sendDemoHeartbeat(): Promise<DemoHeartbeatOutcome> {
  const base = controlPlaneBase();
  if (base === null) return 'not-demo';

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), HEARTBEAT_TIMEOUT_MS);
  try {
    const res = await fetch(`${base}/heartbeat`, {
      method: 'POST',
      cache: 'no-store',
      signal: controller.signal,
    });
    return res.ok ? 'sent' : 'failed';
  } catch {
    // 🔴 삼키되 **거짓말하지 않는다** — 'sent' 로 답하면 호출부가 «살아 있다» 로 읽는다.
    return 'failed';
  } finally {
    clearTimeout(timer);
  }
}
