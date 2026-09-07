// DEMO-PUBLIC-DATA: 발행 절차. **정상본을 절대 덮어쓰지 않는 것**이 이 파일의 유일한 일이다.
//
// =============================================================================
// 절차 (ADR-MONO-070 § D4)
// =============================================================================
//   1. 봉투를 조립하고 **먼저 검증한다** (`validateEnvelope`)
//   2. 불변 세대 파일을 올린다        `public-data/<ds>/v/<dataVersion>.json`
//   3. **올린 것을 다시 읽어** 검증한다 (부분 업로드·잘림·인코딩 사고)
//   4. 지금 포인터를 읽고 **동시 발행**을 판정한다
//   5. 포인터 한 줄만 교체한다
//   6. 구세대를 정리한다 (실패해도 발행은 성공이다 — 정리는 발행의 일부가 아니다)
//
// 🔴 1~4 중 어디서 멈춰도 **포인터는 안 움직인다** ⇒ 갱신 실패·부분 수집·오류 응답이
//    정상본을 못 덮는다. 서버가 꺼져 있으면 애초에 1 에서 멈춘다.
// 🔴 3 을 «올렸으니 됐다» 로 생략하지 마라. 이 저장소가 이름 붙인 함정이다 — **게이트
//    순서가 게이트의 대상을 바꾼다**: 산출물을 만드는 명령이 게이트 안에 있으면 그 뒤로 한
//    번 더 돌려야 «올라간 것» 을 잰 것이 된다. 1 은 «내가 만든 객체» 를 쟀고, 3 이 «저장소에
//    있는 바이트» 를 잰다.
//
// -----------------------------------------------------------------------------
// 🔴🔴 동시 발행 — 오래된 작업이 새 세대를 덮지 않게
// -----------------------------------------------------------------------------
// Blob 에는 compare-and-swap 이 없다. 그래서 **비교를 값의 모양에 넣는다**: 세대 식별자가
// `YYYYMMDDTHHMMSSZ-<hex>` 라 **사전순 = 시간순**이고, 포인터 교체 직전에 지금 포인터를
// 다시 읽어 `mine <= current` 면 **포기한다.**
//
// 🔴 이것은 락이 아니다. 두 작업이 5 를 동시에 지나면 나중에 쓴 쪽이 이긴다 — 그때 이기는
//    쪽이 «더 새로운 세대» 인 것은 보장되지 않는다(경합 창은 4→5 사이다). 이 창을 닫으려면
//    저장소에 CAS 가 있어야 하고 Blob 에는 없다. **그래서 창의 크기와 남는 위험을 여기 적어
//    둔다**: 창은 put 한 번(수십 ms)이고, 발행자는 사람이 부르거나 시드 뒤 한 번 도는
//    것이라 동시 실행 자체가 드물다. 조용히 «안전하다» 고 적는 것보다 이쪽이 정직하다.
// 🔵 그리고 5 이후 **다시 읽어 확인한다**(§ verifyPointer). 졌으면 그 사실을 **말한다** —
//    「발행했다」고 보고한 뒤 실제로는 남의 세대가 서빙되는 상태가 가장 나쁘다.
// =============================================================================

import { randomBytes, createHash } from 'node:crypto';
import {
  PUBLIC_DATA_SCHEMA_VERSION,
  makeDataVersion,
  pointerPath,
  versionPath,
  imagePath,
  validateEnvelope,
  validatePointerShape,
} from './contract.mjs';

/** 남길 구세대 수. 🔵 정리는 저장량을 위한 것이지 보안 조치가 아니다(§ 공개 철회). */
export const RETAIN_VERSIONS = 5;

/** @param {Date} now @returns {string} */
export function newDataVersion(now = new Date()) {
  return makeDataVersion(now, randomBytes(4).toString('hex'));
}

/**
 * 봉투를 만든다.
 * @param {{dataset: string, source: string, origin: string, data: any,
 *          coverage: Record<string, number>, collectionStatus: Record<string, string>,
 *          dataVersion?: string, now?: Date}} args
 */
export function buildEnvelope(args) {
  const now = args.now ?? new Date();
  return {
    schemaVersion: PUBLIC_DATA_SCHEMA_VERSION,
    dataset: args.dataset,
    dataVersion: args.dataVersion ?? newDataVersion(now),
    generatedAt: now.toISOString(),
    source: args.source,
    origin: args.origin,
    coverage: args.coverage,
    collectionStatus: args.collectionStatus,
    data: args.data,
  };
}

/**
 * 지금 포인터를 읽는다. 없으면 null(= 최초 발행). 깨져 있으면 **예외**.
 *
 * 🔴 깨진 포인터를 null 로 읽으면 «최초 발행» 으로 오인해 그대로 덮어쓴다. 「없다」와
 *    「못 읽는다」는 다른 사실이고, 후자에서 할 일은 사람을 부르는 것이다.
 *
 * @param {import('./store.mjs').PublicDataStore} store
 * @param {string} dataset
 */
export async function readCurrentPointer(store, dataset) {
  const raw = await store.getText(pointerPath(dataset));
  if (raw === null) return null;
  /** @type {unknown} */
  let parsed;
  try {
    parsed = JSON.parse(raw);
  } catch (err) {
    throw new Error(
      `[publish] 현재 포인터(${pointerPath(dataset)})가 JSON 이 아닙니다 — 발행을 중단합니다.\n` +
        `→ 「없다」가 아니라 「깨졌다」입니다. 덮어쓰면 정상본을 잃습니다. 사람이 봐야 합니다.\n` +
        `  ${err instanceof Error ? err.message : String(err)}`,
    );
  }
  const v = validatePointerShape(parsed, { dataset });
  if (!v.ok) {
    throw new Error(`[publish] 현재 포인터가 계약을 어깁니다: ${v.reason} — 발행을 중단합니다.`);
  }
  return v.pointer;
}

/**
 * 발행. 위 1~6 을 순서대로 한다.
 *
 * @param {object} args
 * @param {import('./store.mjs').PublicDataStore} args.store
 * @param {any} args.envelope
 * @param {boolean} [args.force]  🔴 «세대를 되돌린다» 는 명시적 의사표시. 기본은 거부.
 * @param {(msg: string) => void} [args.log]
 */
export async function publishEnvelope(args) {
  const { store, envelope } = args;
  const log = args.log ?? (() => {});
  const dataset = envelope.dataset;

  // ── 1. 조립본 검증 --------------------------------------------------------
  const pre = validateEnvelope(envelope, { dataset });
  if (!pre.ok) {
    throw new Error(
      `[publish] 봉투가 계약을 어깁니다: ${pre.reason}\n` +
        `→ **포인터는 움직이지 않았습니다.** 마지막 정상본이 그대로 서빙됩니다.`,
    );
  }

  const vPath = versionPath(dataset, envelope.dataVersion);
  const body = JSON.stringify(envelope);

  // ── 2. 불변 세대 업로드 ---------------------------------------------------
  log(`[publish] ② 세대 업로드 — ${vPath} (${body.length} bytes)`);
  const put = await store.put(vPath, body, 'application/json; charset=utf-8');

  // ── 3. 올린 것을 **다시 읽어** 검증 ---------------------------------------
  //    🔴 여기가 «게이트 순서» 규율의 자리다. 위 put 은 산출물을 만드는 명령이고, 그것을
  //       만든 뒤 한 번 더 재야 «저장소에 있는 바이트» 를 잰 것이 된다.
  const readBack = await store.getText(vPath);
  if (readBack === null) {
    throw new Error(`[publish] 방금 올린 세대를 다시 읽지 못했습니다: ${vPath} — 포인터를 안 옮깁니다.`);
  }
  /** @type {unknown} */
  let parsedBack;
  try {
    parsedBack = JSON.parse(readBack);
  } catch (err) {
    throw new Error(
      `[publish] 올라간 세대가 JSON 으로 파싱되지 않습니다(잘림/인코딩): ${vPath}\n` +
        `  ${err instanceof Error ? err.message : String(err)}`,
    );
  }
  const post = validateEnvelope(parsedBack, { dataset, dataVersion: envelope.dataVersion });
  if (!post.ok) {
    throw new Error(`[publish] 올라간 세대가 계약을 어깁니다: ${post.reason} — 포인터를 안 옮깁니다.`);
  }

  // ── 4. 동시 발행 판정 -----------------------------------------------------
  const current = await readCurrentPointer(store, dataset);
  if (current !== null && current.dataVersion >= envelope.dataVersion && args.force !== true) {
    // 🔴 «같음» 도 거부한다. 같은 세대를 두 번 발행하는 것은 재시도이거나 시계 사고이고,
    //    둘 다 조용히 지나가면 안 된다.
    throw new Error(
      `[publish] 더 새로운(또는 같은) 세대가 이미 발행돼 있습니다 — 포인터를 안 옮깁니다.\n` +
        `  현재: ${current.dataVersion}\n  내 것: ${envelope.dataVersion}\n` +
        `→ 오래된 작업이 새 세대를 덮는 것을 막았습니다. 정말 되돌리려면 --force 를 쓰세요.`,
    );
  }

  // ── 5. 포인터 교체 --------------------------------------------------------
  /** @type {any} */
  const pointer = {
    schemaVersion: PUBLIC_DATA_SCHEMA_VERSION,
    dataset,
    dataVersion: envelope.dataVersion,
    url: put.url || store.publicUrl(vPath),
    publishedAt: new Date().toISOString(),
    source: envelope.source,
  };
  log(`[publish] ⑤ 포인터 교체 — ${pointerPath(dataset)} → ${envelope.dataVersion}`);
  await store.put(pointerPath(dataset), JSON.stringify(pointer), 'application/json; charset=utf-8');

  // 🔵 교체도 다시 읽어 확인한다. 「발행했다」고 보고한 뒤 실제로는 남의 세대가 서빙되는
  //    상태가 가장 나쁘다 — 그 상태는 로그만 보면 성공이다.
  const after = await readCurrentPointer(store, dataset);
  if (after === null || after.dataVersion !== envelope.dataVersion) {
    throw new Error(
      `[publish] 포인터 교체가 반영되지 않았습니다(동시 발행에 졌을 수 있습니다).\n` +
        `  기대: ${envelope.dataVersion}\n  실제: ${after === null ? '(없음)' : after.dataVersion}`,
    );
  }

  // ── 6. 구세대 정리 --------------------------------------------------------
  //    🔴 실패해도 발행은 성공이다. 정리는 저장량 관리이지 발행의 일부가 아니고, 여기서
  //       죽으면 «발행됐는데 실패로 보고» 하게 된다.
  let removed = 0;
  try {
    removed = await pruneOldVersions(store, dataset, envelope.dataVersion, log);
  } catch (err) {
    log(`[publish] ⚠ 구세대 정리 실패(발행 자체는 성공): ${err instanceof Error ? err.message : String(err)}`);
  }

  return { dataVersion: envelope.dataVersion, url: pointer.url, removed };
}

/**
 * 구세대 정리 — 현재 세대 포함 최근 `RETAIN_VERSIONS` 개만 남긴다.
 *
 * 🔴🔴 **이 함수는 「공개 철회」의 수단이 아니다.** 요구사항이 그 둘을 함께 적어 두었지만
 *    실제로는 다른 일이다:
 *      · 정리   = 저장량 관리. 구세대 **파일**을 지운다.
 *      · 공개철회 = 그 데이터가 **새 세대에 없게** 만드는 것. 재발행이 그 수단이다.
 *    구세대 URL 은 세대 식별자를 알아야 열리고 화면은 포인터만 따라가므로, 재발행 즉시
 *    방문자 경로에서는 사라진다. 그러나 **URL 을 아는 사람에게는 정리 전까지 남는다** —
 *    그래서 철회가 급하면 `--retain 0` 로 즉시 정리하거나 그 파일을 직접 지워야 한다.
 *    🔵 이미지는 내용 주소라 세대에 안 묶이고, 여기서 **안 지운다**(다른 세대가 참조할 수
 *       있다). 이미지 철회는 별도 작업이며 이 함수의 축이 아니다.
 *
 * @param {import('./store.mjs').PublicDataStore} store
 * @param {string} dataset
 * @param {string} keepAtLeast  현재 세대. 🔴 무슨 일이 있어도 이건 안 지운다.
 * @param {(msg: string) => void} log
 * @param {number} [retain]
 */
export async function pruneOldVersions(store, dataset, keepAtLeast, log, retain = RETAIN_VERSIONS) {
  const prefix = `public-data/${dataset}/v/`;
  const all = (await store.listPaths(prefix)).filter((p) => p.endsWith('.json'));
  // 사전순 = 시간순이므로 내림차순 정렬이 곧 최신순이다(§ makeDataVersion).
  const sorted = all.slice().sort().reverse();
  const keep = new Set(sorted.slice(0, Math.max(1, retain)));
  keep.add(versionPath(dataset, keepAtLeast)); // 🔴 현재 세대는 무조건 남는다
  let removed = 0;
  for (const p of sorted) {
    if (keep.has(p)) continue;
    await store.remove(p);
    removed += 1;
  }
  if (removed > 0) log(`[publish] ⑥ 구세대 ${removed}개 정리 (남긴 수 ${keep.size})`);
  return removed;
}

/**
 * 이미지 하나를 **내용 주소**로 복사한다.
 *
 * 🔴 「접근 제한 이미지나 재배포 권리가 없는 외부 이미지를 무분별하게 복사하지 않는다」 —
 *    그래서 `allowedOrigins` 를 **반드시** 받고, 목록 밖 주소는 복사하지 않고 `null` 을
 *    돌려준다(= 그 화면은 이미지 없이 뜬다). 기본값을 두지 않는 이유가 그것이다: 기본값이
 *    있으면 새 출처가 조용히 통과한다.
 * 🔵 같은 바이트는 같은 경로가 되므로 재발행이 저장량을 늘리지 않는다.
 *
 * @param {object} args
 * @param {import('./store.mjs').PublicDataStore} args.store
 * @param {string} args.url
 * @param {string[]} args.allowedOrigins
 * @param {Map<string, string>} args.cache  원본 URL → 복사된 URL (한 실행 안의 중복 제거)
 * @param {(msg: string) => void} [args.log]
 * @returns {Promise<string | null>}
 */
export async function copyImage(args) {
  const { store, url, allowedOrigins, cache } = args;
  const log = args.log ?? (() => {});
  const hit = cache.get(url);
  if (hit !== undefined) return hit;

  /** @type {URL} */
  let parsed;
  try {
    parsed = new URL(url);
  } catch {
    return null;
  }
  if (!allowedOrigins.includes(parsed.origin)) {
    log(`[publish] ⏭ 이미지 건너뜀(허용 출처 아님): ${parsed.origin}`);
    return null;
  }

  const res = await fetch(url);
  if (!res.ok) {
    log(`[publish] ⏭ 이미지 건너뜀(HTTP ${res.status}): ${url}`);
    return null;
  }
  const contentType = res.headers.get('content-type') ?? 'application/octet-stream';
  // 🔴 이미지가 아닌 것을 이미지로 재배포하지 않는다. 백엔드가 에러 HTML 을 200 으로
  //    주는 일은 흔하고, 그것이 그대로 공개 저장소에 앉으면 아무도 모른다.
  if (!contentType.startsWith('image/')) {
    log(`[publish] ⏭ 이미지 건너뜀(content-type=${contentType}): ${url}`);
    return null;
  }
  const bytes = new Uint8Array(await res.arrayBuffer());
  const sha = createHash('sha256').update(bytes).digest('hex');
  const ext = (contentType.split('/')[1] ?? 'bin').split(';')[0];
  const path = imagePath(sha, ext);

  const existing = await store.getText(path).catch(() => null);
  const target = existing !== null ? store.publicUrl(path) : (await store.put(path, bytes, contentType)).url;
  cache.set(url, target);
  return target;
}
