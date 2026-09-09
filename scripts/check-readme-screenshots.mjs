#!/usr/bin/env node
// -----------------------------------------------------------------------------
// README 스크린샷 배선이 어긋나지 않게 한다 (TASK-MONO-650 AC-3)
// -----------------------------------------------------------------------------
// **양방향**으로 묻는다:
//   참조 → 파일   README 가 가리키는 이미지가 실재하는가
//   파일 → 참조   `docs/screenshots/` 에 있는데 아무도 안 가리키는 고아가 있는가
//
// 🔴🔴 **술어는 마크다운 «과» HTML 둘 다여야 한다.** 이 저장소는 두 문법을 섞어 쓴다 —
//    ecommerce README 는 스크린샷을 `<img src=...>` 로 걸고 `![...]()` 는 배지뿐이다.
//    한쪽만 물면 **이미 있는 것을 «없다» 고 보고한다**: 2026-09-09 에 `![` 로만 세서
//    「스크린샷 0장」이라 보고했고 실제로는 7장이 있었다. 그래서 그 좁힘이 다시 일어나면
//    빨개지도록 `--self-test` 에 **HTML 전용 픽스처**를 상주시켜 뒀다(칸 4).
//
// 🔴 **비-공허성** — 스크린샷이 0장인 서비스가 아직 여럿이므로 «참조 0건» 은 정상이다.
//    그러면 두 방향이 «둘 다 비어서» 합의하고 가드가 아무것도 안 보고 초록이 된다.
//    그래서 하한을 둔다. 🔴🔴 하한의 대상은 «스크린샷을 가진 서비스 수» 가 **아니라**
//    «참조와 파일이 둘 다 있는 쌍의 수» 다 — 전자는 줄어드는 모집단이라, 서비스가 하나
//    빠지는 정상적인 변경이 하한을 깨서 «성공을 고장으로» 만든다.
//
// 🔵 **못 잡는 것을 적어 둔다.** 이 가드는 «가리키는 파일이 있는가» 만 묻고
//    **«그 그림이 볼 만한가»** 는 못 묻는다. 빈 표·권한거부 화면도 파일로는 정상이다.
//    그 축은 사람 눈이고 `TASK-MONO-648` 이 전수로 했다(19장 중 6장만 쓸 만했다).
//    그리고 «경로가 프로젝트 상대인가» 도 여기서는 통과한다 — 루트 기준 경로는 모노레포
//    에서 실재하므로, 그 결함은 `TEMPLATE.md` 로 추출된 뒤에만 드러난다.
//
// 사용:  node scripts/check-readme-screenshots.mjs [--root <dir>] [--self-test]
// -----------------------------------------------------------------------------
import { existsSync, readdirSync, readFileSync, mkdirSync, writeFileSync, rmSync } from 'node:fs';
import { join, dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { tmpdir } from 'node:os';

const HERE = dirname(fileURLToPath(import.meta.url));
const REPO = resolve(HERE, '..');
const FLOOR = 8; // 참조·파일이 둘 다 있는 쌍의 하한 (실제 저장소에만 적용)
const IMG_EXT = /\.(png|jpe?g|webp|gif|svg)$/i;

/** 한 저장소 트리를 훑어 { pairs, problems, scanned } 를 낸다. */
export function inspect(root) {
  const projects = join(root, 'projects');
  const problems = [];
  let pairs = 0;
  let scanned = 0;
  if (!existsSync(projects)) return { pairs, problems, scanned };

  for (const proj of readdirSync(projects, { withFileTypes: true })
    .filter((e) => e.isDirectory())
    .map((e) => e.name)
    .sort()) {
    const readme = join(projects, proj, 'README.md');
    if (!existsSync(readme)) continue;
    scanned++;
    const src = readFileSync(readme, 'utf8');

    // 🔴 두 문법을 다 뽑는다. 외부 URL(배지 등)은 대상이 아니다.
    const refs = new Set();
    for (const m of src.matchAll(/!\[[^\]]*\]\(([^)\s]+)/g)) refs.add(m[1]);
    for (const m of src.matchAll(/<img[^>]*\ssrc=["']([^"']+)["']/gi)) refs.add(m[1]);
    const local = [...refs].filter((r) => !/^https?:/i.test(r) && IMG_EXT.test(r));

    for (const rel of local) {
      if (existsSync(join(projects, proj, rel))) pairs++;
      else problems.push(`${proj}: README 가 가리키는 파일이 없다 — ${rel}`);
    }

    const shotDir = join(projects, proj, 'docs', 'screenshots');
    if (existsSync(shotDir)) {
      for (const f of readdirSync(shotDir)) {
        if (!IMG_EXT.test(f)) continue;
        const rel = `docs/screenshots/${f}`;
        if (!local.includes(rel)) {
          problems.push(`${proj}: 아무 README 도 안 가리키는 고아 이미지 — ${rel}`);
        }
      }
    }
  }
  return { pairs, problems, scanned };
}

// -----------------------------------------------------------------------------
// 자기 시험 — 가드가 **무는지**를 실행으로 보인다
// -----------------------------------------------------------------------------
function fixture(dir, { readme, files }) {
  const p = join(dir, 'projects', 'demo-svc');
  mkdirSync(join(p, 'docs', 'screenshots'), { recursive: true });
  writeFileSync(join(p, 'README.md'), readme, 'utf8');
  for (const f of files) writeFileSync(join(p, 'docs', 'screenshots', f), 'x', 'utf8');
  return dir;
}

function selfTest() {
  const base = join(tmpdir(), `rms-selftest-${process.pid}`);
  const cells = [];
  const cell = (name, fn) => {
    const dir = join(base, name);
    mkdirSync(dir, { recursive: true });
    try {
      fn(dir);
      cells.push([name, true, '']);
    } catch (e) {
      cells.push([name, false, String(e.message || e)]);
    }
  };
  const eq = (got, want, what) => {
    if (got !== want) throw new Error(`${what}: ${got} (기대 ${want})`);
  };

  // (1) 대조군 — 둘이 맞으면 조용하다
  cell('1-clean', (d) => {
    fixture(d, { readme: '<img src="docs/screenshots/a.jpg">', files: ['a.jpg'] });
    const r = inspect(d);
    eq(r.problems.length, 0, '문제 수');
    eq(r.pairs, 1, '쌍 수');
  });

  // (2) 참조는 있고 파일이 없다 → 문다
  cell('2-missing-file', (d) => {
    fixture(d, { readme: '<img src="docs/screenshots/a.jpg">', files: [] });
    const r = inspect(d);
    eq(r.problems.length, 1, '문제 수');
    if (!r.problems[0].includes('파일이 없다')) throw new Error(`사유가 틀림: ${r.problems[0]}`);
  });

  // (3) 파일은 있고 참조가 없다 → 문다 (반대 방향)
  cell('3-orphan', (d) => {
    fixture(d, { readme: '# 아무 이미지도 안 건다', files: ['a.jpg'] });
    const r = inspect(d);
    eq(r.problems.length, 1, '문제 수');
    if (!r.problems[0].includes('고아')) throw new Error(`사유가 틀림: ${r.problems[0]}`);
  });

  // 🔴🔴 (4) **HTML 전용 README 를 세는가** — 술어를 마크다운으로 좁히면 여기서 죽는다.
  //     2026-09-09 에 내가 실제로 저지른 좁힘이고, 그때 「스크린샷 0장」이라 오보했다.
  cell('4-html-only-counts', (d) => {
    fixture(d, {
      readme: '![CI](https://img.shields.io/badge/x-y-z)\n<img src="docs/screenshots/a.jpg" width="900">',
      files: ['a.jpg'],
    });
    const r = inspect(d);
    eq(r.pairs, 1, 'HTML 전용 README 의 쌍 수');
    eq(r.problems.length, 0, '문제 수');
  });

  // (5) 마크다운 전용도 세는가 (반대 방향의 같은 함정)
  cell('5-markdown-only-counts', (d) => {
    fixture(d, { readme: '![shot](docs/screenshots/a.jpg)', files: ['a.jpg'] });
    eq(inspect(d).pairs, 1, '마크다운 전용 README 의 쌍 수');
  });

  // (6) 🔵 대조군 — **외부 URL(배지)은 대상이 아니다.** 이것이 걸리면 모든 README 가 빨개진다.
  cell('6-badges-are-not-shots', (d) => {
    fixture(d, { readme: '![CI](https://img.shields.io/badge/a.svg)', files: [] });
    const r = inspect(d);
    eq(r.problems.length, 0, '문제 수');
    eq(r.pairs, 0, '쌍 수');
  });

  // (7) 🔵 대조군 — **스크린샷이 0장인 서비스는 정상이다.** 이것이 걸리면 wms·scm·
  //     platform-console 이 이유 없이 빨개진다.
  cell('7-no-screenshots-is-fine', (d) => {
    fixture(d, { readme: '# 스크린샷 없음', files: [] });
    eq(inspect(d).problems.length, 0, '문제 수');
  });

  rmSync(base, { recursive: true, force: true });
  const failed = cells.filter(([, ok]) => !ok);
  for (const [n, ok, why] of cells) console.log(`  ${ok ? '✅' : '🔴'} (${n})${why ? ' — ' + why : ''}`);
  console.log(`self-test: ${cells.length - failed.length}/${cells.length} 통과`);
  return failed.length === 0 ? 0 : 1;
}

// -----------------------------------------------------------------------------
const argv = process.argv.slice(2);
if (argv.includes('--self-test')) process.exit(selfTest());

const rootArg = argv.indexOf('--root');
const root = rootArg >= 0 ? resolve(argv[rootArg + 1]) : REPO;
const { pairs, problems, scanned } = inspect(root);

console.log(`check-readme-screenshots: README ${scanned}개 · 참조↔파일 쌍 ${pairs}개`);

if (rootArg < 0 && pairs < FLOOR) {
  console.error(
    `\n🔴 비-공허성 하한 미달: 쌍이 ${pairs}개인데 하한은 ${FLOOR}개다.\n` +
      `   이 가드가 «아무것도 안 보고» 초록이 되는 것을 막는 장치다.\n` +
      `   🔴 술어를 좁혔는지 먼저 의심하라 — 마크다운 «과» HTML 을 둘 다 물어야 한다.\n` +
      `   배선을 정말 줄였다면 이 상수를 그 사실과 함께 낮춰라.`,
  );
  process.exit(1);
}

if (problems.length) {
  console.error(`\n🔴 어긋남 ${problems.length}건:`);
  for (const p of problems) console.error(`   ${p}`);
  console.error(
    `\n   고치는 법: 참조를 지웠으면 파일도 지우고, 파일을 옮겼으면 README 도 고쳐라.\n` +
      `   🔴 경로는 **프로젝트 상대**여야 한다 — 저장소 루트 기준으로 쓰면 모노레포에서는\n` +
      `      초록이고 TEMPLATE.md 로 추출된 리포에서만 깨져, CI 가 절대 못 잡는다.`,
  );
  process.exit(1);
}

console.log('  참조와 파일이 양방향으로 합의한다. 고아 0건.');
