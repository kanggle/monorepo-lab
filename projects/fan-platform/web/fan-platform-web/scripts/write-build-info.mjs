// =============================================================================
// write-build-info.mjs — 서빙 중인 판이 **어느 커밋인지** 말하게 한다
//                        (TASK-MONO-563 AC-3)
// =============================================================================
// `next build` 직전에 `public/build-info.json` 을 만든다. Next 는 `public/` 을 그대로
// 정적 자산으로 올리므로 결과는 `/build-info.json` 에서 읽힌다.
//
// 🔴 **왜 필요한가 — URL 200 은 판정이 아니다.** 배포가 실패해도 사이트는 마지막 성공 판을
//    계속 서빙한다. `TASK-MONO-562` 가 론처에서 정확히 그것을 겪었고(560·561 을 머지했는데
//    방문자는 그 링크가 없는 페이지를 200 으로 받았다), `TASK-MONO-563` 은 fan 에서
//    **성공한 배포가 하나도 없는데** 아무 계기판도 그것을 말하지 않는 상태를 만났다.
//
// 🔵 **론처와 달리 fan 은 내용 md5 축을 쓸 수 없다.** 이 앱은 라우트 대부분이 동적(`ƒ`)이라
//    같은 커밋이라도 응답 바이트가 매 요청 다를 수 있다. 그래서 판정 축은 **커밋 하나**다.
//    (`infra/demo/aws/site` 는 정적 문서라 md5 가 성립한다 — 같은 축을 복사하면 틀린다.)
//
// 🔴 **타임스탬프를 넣지 않는다.** 커밋이 말하지 않는 것을 말해 주지 않으면서 빌드마다
//    값이 달라져 diff 를 더럽히고, 이 저장소는 KST 호스트/UTC CI 의 날짜 축에서 이미
//    값을 치렀다. 판정에 필요한 것은 **어느 커밋인가** 뿐이다.
//
// 🔴 **이 스크립트는 빌드를 깨뜨려서는 안 된다.** Docker 이미지 빌드도 같은 `build` 스크립트를
//    타는데 그 컨텍스트에는 `.git` 도 `VERCEL_*` 도 없다. 무엇을 못 읽든 파일은 반드시 쓰고,
//    모르는 값은 `unknown` 으로 적는다 — **없는 파일과 모른다고 적힌 파일은 다르다.**
//    판정자는 `unknown` 을 신선으로 읽지 않고 **판정 불가**로 낸다.
// =============================================================================
import { mkdirSync, writeFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const APP = dirname(dirname(fileURLToPath(import.meta.url)));

function git(...args) {
  try {
    return execFileSync('git', ['-C', APP, ...args], {
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'ignore'],
    }).trim();
  } catch {
    return '';
  }
}

// Vercel 이 주는 값이 1순위다 — 빌드가 실제로 어느 커밋에서 트리거됐는지는 그쪽이 안다.
// 로컬/Docker 는 git 으로 떨어지고, 둘 다 없으면 `unknown`.
const commit = process.env.VERCEL_GIT_COMMIT_SHA || git('rev-parse', 'HEAD') || 'unknown';
const ref =
  process.env.VERCEL_GIT_COMMIT_REF || git('rev-parse', '--abbrev-ref', 'HEAD') || 'unknown';

const out = join(APP, 'public', 'build-info.json');
mkdirSync(dirname(out), { recursive: true });
writeFileSync(out, JSON.stringify({ commit, ref }) + '\n', 'utf8');

console.log(`[build-info] ${out} <- commit=${commit} ref=${ref}`);
