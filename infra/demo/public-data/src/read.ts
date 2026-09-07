// DEMO-PUBLIC-DATA: 판독자. **공개 화면이 데이터를 얻는 유일한 경로**다.
//
// =============================================================================
// 저장본을 읽는다 — 그리고 백엔드로는 **절대** 안 간다 (ADR-MONO-070 § D3)
// =============================================================================
// 요구사항이 명시적으로 금지한 구조가 있다:
//
//   *"먼저 백엔드를 호출했다가 실패하면 저장본을 보여주는 구조를 기본으로 만들지 않는다."*
//
// 그래서 이 모듈에는 **백엔드로 가는 코드가 아예 없다.** 폴백 사슬은 두 칸뿐이다:
//
//   ① Vercel 영속 저장본  (`DEMO_PUBLIC_DATA_BASE_URL` 이 있을 때)
//   ② 저장소에 커밋된 번들 시드
//
// 🔴 ② 는 «실패했을 때의 임시방편» 이 아니라 **선언된 바닥**이다. 이것이 있어야
//    *"백엔드 없이 새 production build 및 배포가 가능한지"* 가 참이 된다 — 외부 설정이
//    하나도 없는 상태(로컬·CI·최초 배포)에서도 화면이 실물로 선다.
// 🔴 그리고 화면은 어느 칸에서 왔는지를 **말해야** 한다(`source`). 말하지 않으면 번들 시드가
//    「지금 백엔드에서 뽑은 것」으로 읽힌다 — 없는 사실을 주장하는 화면이 된다.
//
// -----------------------------------------------------------------------------
// 🔴🔴 목록과 상세가 섞이지 않는다 — 그 성질이 여기서 만들어진다
// -----------------------------------------------------------------------------
// 판정은 **봉투 하나**를 단위로 한다. `readPublicData()` 는 봉투 전체를 돌려주고, 목록도
// 상세도 검색도 **그 하나의 객체**에서 파생된다. 화면이 "목록은 저장본, 상세는 백엔드"
// 같은 반쪽 구현을 하려 해도 이 모듈에는 그럴 인자가 없다.
//
// 🔵 캐시 키에 `dataVersion` 이 들어간다. 세대가 바뀌면 새 객체가 되고, 이미 렌더 중인
//    요청은 자기가 읽던 객체를 계속 본다(JS 객체 참조가 그것을 공짜로 준다).
//
// -----------------------------------------------------------------------------
// 🔵 서버 전용
// -----------------------------------------------------------------------------
// 브라우저에서 부르지 않는다. 데이터가 작으면 화면이 봉투를 클라이언트로 내려 보내
// 브라우저에서 검색·필터할 수 있지만, **읽는 것은 서버가** 한다(§ ADR D3.2).
// =============================================================================

import {
  PUBLIC_DATA_SCHEMA_VERSION,
  pointerPath,
  validateDatasetData,
  validateEnvelopeShape,
  validatePointerShape,
  type PublicDataEnvelope,
} from './contract.mjs';
import type { PublicDataByDataset, PublicDataset } from './datasets';

/**
 * 환경변수 사전.
 *
 * 🔵 `NodeJS.ProcessEnv` 를 쓰지 않는다 — 그러면 이 패키지가 `@types/node` 에 묶이고,
 *    판독자를 임포트하는 세 앱이 그 의존을 공유하게 된다. 필요한 것은 «문자열 사전» 하나뿐이다.
 */
export type EnvLike = Readonly<Record<string, string | undefined>>;

/** 저장본 왕복이 이보다 오래 걸리면 포기하고 번들 시드로 간다. 화면을 세우는 것이 우선이다. */
const FETCH_TIMEOUT_MS = 3_000;

/**
 * 캐시 TTL.
 *
 * 🔵 60초인 이유: 저장본은 **발행할 때만** 바뀌고 발행은 사람이나 시드가 일으키는 사건이다.
 *    따라서 TTL 은 "얼마나 자주 바뀌나" 가 아니라 **"바뀐 뒤 얼마나 빨리 따라가야 하나"** 로
 *    정한다. 1분이면 운영 중 변경 후 발행이 방문자에게 닿는 데 충분하고, 그보다 짧으면
 *    페이지 하나 렌더에 Blob 을 여러 번 때린다(무료 플랜의 대역폭 축이 실재한다).
 * 🔴 실패는 **더 짧게** 재시도한다 — 저장본이 잠깐 안 보이는 동안 번들 시드에 1분씩
 *    갇히면, 발행 직후의 방문자가 계속 옛 화면을 본다.
 */
const CACHE_TTL_MS = 60_000;
const CACHE_TTL_ON_FALLBACK_MS = 10_000;

/** 화면이 방문자에게 말해야 하는 것 — 어디서 왔고, 무엇을 얼마나 담고 있는가. */
export interface PublicDataResult<TData> {
  data: TData;
  /** 봉투 그대로. 화면은 `source`·`generatedAt`·`coverage` 를 여기서 읽는다. */
  envelope: PublicDataEnvelope<TData>;
  /**
   * 저장본을 **시도했는데 못 읽어서** 번들로 떨어졌는가.
   *
   * 🔴 `envelope.source === 'bundled'` 와 **다른 사실**이다. 저장소가 번들을 발행본으로
   *    올린 경우에도 source 는 bundled 이지만 그때 degraded 는 거짓이다. 화면의 문구가
   *    갈리는 축은 이쪽이다("아직 발행 전" vs "저장본을 못 읽는 중").
   */
  degraded: boolean;
  /** degraded 일 때 왜. 로그·진단용이며 화면에 그대로 내보내지 않는다. */
  degradedReason: string | null;
}

interface CacheEntry {
  at: number;
  ttl: number;
  result: PublicDataResult<unknown>;
}

const cache = new Map<PublicDataset, CacheEntry>();

/** 테스트 전용 — 칸 사이에 캐시가 새지 않게 한다. */
export function __resetPublicDataCache(): void {
  cache.clear();
}

/**
 * 저장본의 베이스 URL.
 *
 * 🔴 `NEXT_PUBLIC_` 접두사를 **쓰지 않는다.** Next 는 그 접두사를 빌드 타임에 인라인하고,
 *    그러면 저장소 주소가 «구워진 순간부터» 고정된다. 서버에서 읽으면 재배포 없이 바꿀 수
 *    있고, 브라우저는 이 값을 알 필요가 없다(브라우저는 서버가 렌더한 결과만 본다).
 */
function baseUrl(env: EnvLike): string | null {
  const raw = env.DEMO_PUBLIC_DATA_BASE_URL;
  if (typeof raw !== 'string' || raw.trim() === '') return null;
  const trimmed = raw.trim().replace(/\/+$/, '');
  // 🔴 모양을 믿지 않는다. 오타(`http://`, 따옴표 포함)는 브라우저에서만 깨지고, 그 실패는
  //    "저장소가 죽었다" 처럼 보인다. 여기서 걸러 번들 시드로 정직하게 떨어진다.
  if (!/^https:\/\/[^\s"']+$/.test(trimmed)) return null;
  return trimmed;
}

async function fetchJson(url: string, signal: AbortSignal): Promise<unknown> {
  const res = await fetch(url, {
    signal,
    // 🔴 Next 의 fetch 캐시를 쓰지 않는다 — 이 모듈이 자기 캐시를 갖고 있고(세대 키),
    //    두 캐시가 겹치면 «어느 세대를 보고 있는가» 가 두 곳에서 정해진다.
    cache: 'no-store',
    headers: { accept: 'application/json' },
  });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return (await res.json()) as unknown;
}

/**
 * 판독자를 만든다.
 *
 * @param dataset  어느 화면의 데이터인가.
 * @param bundled  저장소에 커밋된 시드 봉투. **필수다** — 없으면 이 모듈은 «데이터 없음» 을
 *                 표현할 방법이 없고, 그때 화면은 백엔드로 가고 싶어진다. 그것이 이 ADR 이
 *                 금지한 구조다.
 */
export function createPublicDataReader<K extends keyof PublicDataByDataset & PublicDataset>(
  dataset: K,
  bundled: PublicDataEnvelope<PublicDataByDataset[K]>,
  deps?: { env?: EnvLike; now?: () => number },
) {
  // 🔴 `process` 를 직접 참조하지 않는다 — 이 패키지는 `@types/node` 에 안 묶인다(§ EnvLike).
  //    globalThis 경유는 그 타입 의존 없이 같은 값을 준다. 없으면 빈 사전 = 번들 시드.
  const env: EnvLike =
    deps?.env ?? (globalThis as { process?: { env?: EnvLike } }).process?.env ?? {};
  const now = deps?.now ?? (() => Date.now());

  function bundledResult(reason: string | null): PublicDataResult<PublicDataByDataset[K]> {
    return {
      data: bundled.data,
      envelope: bundled,
      degraded: reason !== null,
      degradedReason: reason,
    };
  }

  async function load(): Promise<PublicDataResult<PublicDataByDataset[K]>> {
    const base = baseUrl(env);
    // 🔵 설정이 없는 것은 **결함이 아니다.** 로컬 개발·CI·최초 배포가 그 상태이고, 거기서
    //    "저장본을 못 읽습니다" 배너를 띄우면 거짓말이다(§ backend-resolver 의 not-demo 와
    //    같은 축). 그래서 degraded 가 아니다.
    if (base === null) return bundledResult(null);

    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), FETCH_TIMEOUT_MS);
    try {
      const rawPointer = await fetchJson(`${base}/${pointerPath(dataset)}`, controller.signal);
      const p = validatePointerShape(rawPointer, { dataset });
      if (!p.ok) return bundledResult(`포인터가 유효하지 않습니다: ${p.reason}`);

      const rawEnvelope = await fetchJson(p.pointer.url, controller.signal);
      const shape = validateEnvelopeShape(rawEnvelope, { dataset, dataVersion: p.pointer.dataVersion });
      if (!shape.ok) return bundledResult(`봉투가 유효하지 않습니다: ${shape.reason}`);

      const envelope = rawEnvelope as PublicDataEnvelope<PublicDataByDataset[K]>;
      const content = validateDatasetData(dataset, envelope.data);
      if (!content.ok) return bundledResult(`내용물이 유효하지 않습니다: ${content.reason}`);

      return { data: envelope.data, envelope, degraded: false, degradedReason: null };
    } catch (err) {
      // 🔴 판정 불가를 "데이터 없음" 으로 번역하지 않는다. 번들 시드로 가되 **degraded 로
      //    표시**해서, 화면이 「샘플을 보고 있다」를 말할 수 있게 한다.
      const msg = err instanceof Error ? err.message : String(err);
      return bundledResult(`저장본을 읽지 못했습니다: ${msg}`);
    } finally {
      clearTimeout(timer);
    }
  }

  /**
   * 봉투를 읽는다. 요청 하나가 **한 번만** 부르고, 목록·상세·검색을 그 결과에서 파생한다.
   *
   * 🔴 인자가 없다. 테넌트도, 페이지도, 검색어도 받지 않는다 — 그런 인자가 있으면 그것이
   *    저장본의 «질의 표면» 이 되고, 질의 표면은 지켜야 할 경계가 된다(§ datasets.ts).
   */
  async function readPublicData(): Promise<PublicDataResult<PublicDataByDataset[K]>> {
    const hit = cache.get(dataset);
    if (hit && now() - hit.at < hit.ttl) {
      return hit.result as PublicDataResult<PublicDataByDataset[K]>;
    }
    const result = await load();
    cache.set(dataset, {
      at: now(),
      ttl: result.degraded ? CACHE_TTL_ON_FALLBACK_MS : CACHE_TTL_MS,
      result: result as PublicDataResult<unknown>,
    });
    return result;
  }

  return { readPublicData, __resetPublicDataCache };
}

/**
 * 번들 시드 봉투를 조립한다 — JSON 파일 하나를 봉투로 감싸는 자리.
 *
 * 🔴 시드 JSON 이 계약을 어기면 **여기서 죽는다.** 조용히 빈 데이터로 가면 그 화면은
 *    "아직 데이터가 없습니다" 를 그리고, 그건 «시드가 깨졌다» 와 구별되지 않는다.
 *    빌드가 죽는 편이 낫다 — CI 가 잡는다.
 */
export function bundledEnvelope<K extends keyof PublicDataByDataset & PublicDataset>(
  dataset: K,
  raw: unknown,
): PublicDataEnvelope<PublicDataByDataset[K]> {
  const shape = validateEnvelopeShape(raw, { dataset });
  if (!shape.ok) {
    throw new Error(
      `[public-data] 번들 시드('${dataset}')가 봉투 계약을 어깁니다: ${shape.reason}\n` +
        `→ 시드는 infra/demo/public-data/snapshots/${dataset}.json 입니다. ` +
        `schemaVersion 은 ${PUBLIC_DATA_SCHEMA_VERSION} 이어야 합니다.`,
    );
  }
  const envelope = raw as PublicDataEnvelope<PublicDataByDataset[K]>;
  const content = validateDatasetData(dataset, envelope.data);
  if (!content.ok) {
    throw new Error(`[public-data] 번들 시드('${dataset}')의 내용물이 계약을 어깁니다: ${content.reason}`);
  }
  return envelope;
}
