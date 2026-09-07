// DEMO-PUBLIC-DATA: 저장소 어댑터. Vercel Blob 과 로컬 디렉터리를 **같은 인터페이스**로 만든다.
//
// =============================================================================
// 왜 어댑터인가 — 「검증 가능한가」가 설계를 정했다
// =============================================================================
// 이 저장소에는 Vercel 토큰·CLI 가 없다(`TASK-MONO-575` 가 실측으로 적어 뒀다). 발행 경로를
// Blob 에만 묶으면 **이 세션에서도, CI 에서도, 다음 사람의 노트북에서도 한 번도 돌려볼 수
// 없는 코드**가 된다 — 그리고 안 돌려본 코드는 처음 돌릴 때 틀린다.
//
// ⇒ 같은 발행 절차(불변 세대 → 검증 → 포인터 교체 → 구세대 정리)를 **로컬 디렉터리에도**
//   적용할 수 있게 만든다. 그러면:
//     · 절차 자체(동시 발행·부분 수집·정상본 보존)를 **실제로 실행해서** 시험할 수 있고,
//     · Blob 어댑터가 하는 일은 `put`/`list`/`del` 세 호출로 줄어든다.
//
// 🔴 이것은 「가짜로 통과시키는 스텁」이 아니다. 두 어댑터는 **같은 발행자 코드**를 받고,
//    로컬 어댑터가 통과시키는 것은 절차이지 Blob 이 아니다. Blob 쪽 미검증분은 발행자가
//    보고할 때 그렇게 적힌다.
// 🔴 로컬 어댑터의 `publicUrl` 은 `file:` 이 아니라 **선언된 베이스 URL** 을 쓴다. 포인터의
//    `url` 은 https 절대주소여야 하고(계약), 그 계약을 로컬에서만 느슨하게 하면 로컬에서
//    통과한 봉투가 실제로는 판독 불가일 수 있다 — 실물보다 관대한 스텁이 되는 그 함정이다.
// =============================================================================

import { mkdir, readFile, writeFile, rm, readdir } from 'node:fs/promises';
import { dirname, join } from 'node:path';

/**
 * @typedef {object} PublicDataStore
 * @property {(path: string, body: string | Uint8Array, contentType: string) => Promise<{url: string}>} put
 * @property {(path: string) => Promise<string | null>} getText   없으면 null (404 를 예외로 만들지 않는다)
 * @property {(prefix: string) => Promise<string[]>} listPaths
 * @property {(path: string) => Promise<void>} remove
 * @property {(path: string) => string} publicUrl
 * @property {string} kind
 */

/**
 * 로컬 디렉터리 어댑터.
 * @param {{dir: string, baseUrl: string}} opts
 * @returns {PublicDataStore}
 */
export function createLocalStore(opts) {
  const base = opts.baseUrl.replace(/\/+$/, '');
  const full = (/** @type {string} */ p) => join(opts.dir, p);
  return {
    kind: `local(${opts.dir})`,
    publicUrl: (p) => `${base}/${p}`,
    async put(path, body, _contentType) {
      const target = full(path);
      await mkdir(dirname(target), { recursive: true });
      await writeFile(target, body);
      return { url: `${base}/${path}` };
    },
    async getText(path) {
      try {
        return await readFile(full(path), 'utf8');
      } catch (err) {
        // 🔴 «없다» 와 «못 읽는다» 를 가른다. 후자를 없는 것으로 읽으면, 읽기 권한 사고가
        //    「아직 발행 안 됨」으로 보이고 발행자가 **정상본을 덮어쓴다**.
        if (/** @type {NodeJS.ErrnoException} */ (err).code === 'ENOENT') return null;
        throw err;
      }
    },
    async listPaths(prefix) {
      /** @type {string[]} */
      const out = [];
      const walk = async (/** @type {string} */ rel) => {
        let entries;
        try {
          entries = await readdir(full(rel), { withFileTypes: true });
        } catch {
          return;
        }
        for (const e of entries) {
          const child = rel === '' ? e.name : `${rel}/${e.name}`;
          if (e.isDirectory()) await walk(child);
          else if (child.startsWith(prefix)) out.push(child);
        }
      };
      await walk('');
      return out.sort();
    },
    async remove(path) {
      await rm(full(path), { force: true });
    },
  };
}

/**
 * Vercel Blob 어댑터.
 *
 * 🔴 `@vercel/blob` 을 **동적으로** 임포트한다. 판독자 쪽 코드는 이 파일을 안 부르고, 이
 *    파일을 부르는 것은 CLI 뿐이다 — 정적 임포트로 두면 세 Next 앱의 서버 번들이 그 패키지를
 *    해석하려 들고(없으면 빌드가 죽고), 「판독자는 의존성이 없다」는 성질이 사라진다.
 * 🔴 토큰은 **환경변수에서만** 온다. argv 로 받으면 `ps` 에 남는다.
 *
 * @param {{token: string, baseUrl: string}} opts
 * @returns {Promise<PublicDataStore>}
 */
export async function createBlobStore(opts) {
  /** @type {any} */
  let blob;
  try {
    blob = await import('@vercel/blob');
  } catch (err) {
    throw new Error(
      '[public-data] @vercel/blob 을 불러오지 못했습니다. 이 패키지는 **발행자 전용 의존**입니다.\n' +
        `  설치: pnpm --dir infra/demo/public-data add -D @vercel/blob\n` +
        `  원인: ${err instanceof Error ? err.message : String(err)}`,
    );
  }
  const base = opts.baseUrl.replace(/\/+$/, '');
  return {
    kind: 'vercel-blob',
    publicUrl: (p) => `${base}/${p}`,
    async put(path, body, contentType) {
      const res = await blob.put(path, body, {
        access: 'public',
        token: opts.token,
        contentType,
        // 🔴 접미사를 붙이지 않는다 — 경로가 **내용 주소**이거나(이미지) **세대 식별자**여야
        //    하고(봉투), 무작위 접미사가 붙으면 둘 다 성립하지 않는다.
        addRandomSuffix: false,
        allowOverwrite: true,
      });
      return { url: res.url };
    },
    async getText(path) {
      const res = await fetch(`${base}/${path}`, { cache: 'no-store' });
      if (res.status === 404) return null;
      if (!res.ok) throw new Error(`GET ${path} → HTTP ${res.status}`);
      return await res.text();
    },
    async listPaths(prefix) {
      /** @type {string[]} */
      const out = [];
      let cursor;
      do {
        const page = await blob.list({ prefix, cursor, token: opts.token, limit: 1000 });
        for (const b of page.blobs) out.push(b.pathname);
        cursor = page.hasMore ? page.cursor : undefined;
      } while (cursor);
      return out.sort();
    },
    async remove(path) {
      await blob.del(`${base}/${path}`, { token: opts.token });
    },
  };
}
