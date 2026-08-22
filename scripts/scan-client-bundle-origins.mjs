// =============================================================================
// TASK-MONO-565 / ADR-MONO-067 AC-0 ① — 클라이언트 번들의 절대 오리진 스캐너
// =============================================================================
// 사용: node scan-bundle.mjs <app-dir> <label>
//
// 🔴 대상은 `.next/static/**/*.js` 뿐이다. `.next/server/**` 은 세지 않는다 —
//    서버는 평문 HTTP 를 불러도 되고 (B) 는 바로 그것을 전제로 한다. 섞어 세면 숫자가
//    부풀고 **틀린 결론**이 나온다. `.map` 도 세지 않는다(브라우저가 실행하는 건 `.js`).
//
// 🔴 1판은 술어가 헐거워 `http://n` · `https://a` 같은 **미니파이 조각**을 오리진으로
//    셌다. 호스트에 점이 있거나 정확히 `localhost` 인 것만 오리진으로 인정한다.
//
// 🔴 그리고 **한 버킷으로 뭉치지 않는다.** 세 갈래로 갈라 전부 출력한다 —
//    분류하지 못한 몫이 조용히 사라지면 판정이 거짓 초록이 된다(오늘 이미 세 번 데였다):
//      backend  : 데모 백엔드로 갈 수 있는 것 (`*.sslip.io` · `*.local` · `localhost[:port]`)
//                 ← D1 이 금지하는 것
//      thirdParty: 외부 서비스 (결제 SDK, 이미지 CDN 등) ← D1 의 대상이 아니다
//      benign   : 문서/스펙 URL
// =============================================================================
import { readdirSync, statSync, readFileSync } from 'node:fs';
import { join } from 'node:path';

const appDir = process.argv[2];
const label = process.argv[3] ?? appDir;
if (!appDir) { console.error('usage: node scan-bundle.mjs <app-dir> <label>'); process.exit(2); }

const staticDir = join(appDir, '.next', 'static');

function walk(dir) {
  let out = [];
  let ents;
  try { ents = readdirSync(dir); } catch { return out; }
  for (const e of ents) {
    const p = join(dir, e);
    let st;
    try { st = statSync(p); } catch { continue; }
    if (st.isDirectory()) out = out.concat(walk(p));
    else if (e.endsWith('.js')) out.push(p);
  }
  return out;
}

const files = walk(staticDir);
if (files.length === 0) {
  console.log(JSON.stringify({ label, verdict: 'UNDECIDABLE',
    reason: '.next/static 에 .js 가 0건 — 빌드가 없거나 경로가 다릅니다' }, null, 2));
  process.exit(3);
}

const ORIGIN_RE = /https?:\/\/[a-z0-9.\-]+(?::\d+)?/gi;

// 오리진으로 **인정할 자격**: 점이 있는 호스트, 또는 정확히 localhost.
function isRealOrigin(o) {
  const host = o.replace(/^https?:\/\//i, '').split(':')[0];
  return host === 'localhost' || (host.includes('.') && !host.endsWith('.'));
}

const BENIGN = [/w3\.org/i, /schema\.org/i, /nextjs\.org/i, /react(js)?\.(dev|org)/i, /github\.com/i, /errors\.authjs\.dev/i];
const BACKENDISH = [/\.sslip\.io/i, /\.local(:|$)/i, /^https?:\/\/localhost/i, /ac0-probe\.invalid/i];

const buckets = { backend: new Map(), thirdParty: new Map(), benign: new Map() };
const rejected = new Map();   // 오리진으로 인정 못 한 조각 — 세어서 함께 낸다

for (const f of files) {
  let s;
  try { s = readFileSync(f, 'utf8'); } catch { continue; }
  const seen = new Set();
  for (const m of s.matchAll(ORIGIN_RE)) seen.add(m[0]);
  for (const o of seen) {
    if (!isRealOrigin(o)) { rejected.set(o, (rejected.get(o) ?? 0) + 1); continue; }
    const b = BACKENDISH.some((r) => r.test(o)) ? 'backend'
            : BENIGN.some((r) => r.test(o)) ? 'benign'
            : 'thirdParty';
    buckets[b].set(o, (buckets[b].get(o) ?? 0) + 1);
  }
}

const dump = (m) => [...m.entries()].sort((a, b) => b[1] - a[1]).map(([o, n]) => ({ origin: o, files: n }));

console.log(JSON.stringify({
  label,
  verdict: 'MEASURED',
  jsFiles: files.length,
  backendCount: buckets.backend.size,
  backend: dump(buckets.backend),
  thirdPartyCount: buckets.thirdParty.size,
  thirdParty: dump(buckets.thirdParty),
  benignCount: buckets.benign.size,
  benign: dump(buckets.benign),
  rejectedFragmentCount: rejected.size,          // 분류 못 한 몫도 판정 옆에 둔다
  rejectedFragments: dump(rejected).slice(0, 10),
}, null, 2));
