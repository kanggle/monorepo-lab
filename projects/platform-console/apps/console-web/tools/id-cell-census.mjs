// id-cell-census.mjs — TASK-PC-FE-277
//
// 「참조 id 를 보이는 텍스트 자리에 그대로 찍는 칸」을 센다.
//
// ## 🔴🔴 왜 이 파일이 저장소에 있나 — 산문으로 적힌 술어는 다시 셀 수 없다
//
// `TASK-PC-FE-276` 이 다른 도메인을 세어 **48곳**이라고 넘겼고, `TASK-PC-FE-277` 의 AC-0 이
// *"위 48 을 그대로 믿지 마라. 같은 술어로 다시 세고, 그 사이 늘었는지 적어라"* 라고 했다.
// 그런데 기록된 술어는 산문이었다:
//
//     "`{x.<something>Id}` 를 **보이는 텍스트 자리**에 렌더하는 줄
//      제외: key= · data-testid= · value= · 그 밖의 prop 전달"
//
// 🔴 「보이는 텍스트 자리」를 **무엇으로 판정했는지가 없다.** 그래서 다시 셀 수가 없고,
//    새 수와 48 은 «비교 가능한 값» 이 아니다 — 서로 다른 추출기의 출력이다.
//    (이 저장소가 이름 붙인 축: 두 추출값의 비교는 추출기를 잰다.)
//
// ⇒ 그래서 술어를 **여기 박아 둔다.** 다음 사람은 이 파일을 돌려서 같은 수를 얻고,
//   늘었는지 줄었는지를 **말할 수 있다.**
//
// ## 🔴 이 술어가 한 번 틀렸다 — 그 기록
//
// 첫 판은 `=` 바로 뒤의 `{` 만 제외했다(JSX 속성값). 그랬더니 **111** 이 나왔고, 기안의
// 48 과 두 배 넘게 벌어졌다. `OrgAdminPanel.tsx` 를 눈으로 열어 보고 원인을 알았다:
//
//     data-testid={`org-admin-row-${a.operatorId}`}     ← 보이는 텍스트가 아니다
//
// **템플릿 리터럴 안의 `${...}`** 는 앞 글자가 `$` 라 그 제외를 빠져나갔다. `$` 도 빼자
// **66** 이 됐고 도메인 분포가 기안의 모양과 같아졌다.
//
// ## 🔴 남은 한계 (안 적으면 다음 사람이 이 수를 성질로 읽는다)
//
//   · **줄 단위다.** 여러 줄에 걸친 JSX 는 못 본다 ⇒ 이 수는 **하한**이다.
//   · **자기 식별자와 참조를 안 가른다.** `OrdersTable` 의 `{o.orderId}` 는 그 행 자신의
//     id 이고 정상이다. 가르는 것은 사람의 일이고, 그것이 277 의 본체다.
//   · **런타임 값이 UUID 인지 못 판정한다.** 콘솔 zod 는 전부 `z.string()` 이라 선언으로는
//     못 읽는다 — 시드/마이그레이션에서 실제 값 모양을 읽어야 한다.
//
// 사용: node tools/id-cell-census.mjs [루트]     (기본 src/features)
import { readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";

const ROOT = process.argv[2] || "src/features";
const files = [];
(function walk(d) {
  for (const e of readdirSync(d)) {
    const p = join(d, e);
    if (statSync(p).isDirectory()) walk(p);
    else if (p.endsWith(".tsx")) files.push(p.split("\\").join("/"));
  }
})(ROOT);

// `{ident.somethingId}` 또는 `{ident.somethingId ?? '...'}`
// 🔴 앞 글자를 함께 잡는다 — 그 한 글자가 「텍스트 자리인가」를 가른다.
const RE = /(.)\{\s*([A-Za-z_$][\w$]*)\.([A-Za-z_$][\w$]*Id)\s*(\?\?[^}]*)?\}/g;

const byDomain = new Map();
const rows = [];
for (const f of files) {
  // 🔵 erp-ops 는 TASK-PC-FE-276 이 이미 고쳤다(8곳 + data-master-ref 마커 + 회귀 가드).
  if (f.includes("/erp-ops/")) continue;
  const src = readFileSync(f, "utf8");
  src.split(/\r?\n/).forEach((line, i) => {
    for (const m of line.matchAll(RE)) {
      // `=` 바로 뒤 = JSX 속성값. `$` 바로 뒤 = 템플릿 리터럴 보간(대개 data-testid/key).
      if (m[1] === "=" || m[1] === "$") continue;
      const parts = f.split("/");
      const dom = parts[parts.indexOf("features") + 1] || "(?)";
      byDomain.set(dom, (byDomain.get(dom) || 0) + 1);
      rows.push([dom, `${f}:${i + 1}`, `${m[2]}.${m[3]}`].join("\t"));
    }
  });
}

// 🔴 빈 모집단은 «없다» 가 아니라 «추출기가 깨졌다» 이다. 0 을 초록으로 읽지 않게 한다.
if (files.length === 0) {
  console.error(`[census] ${ROOT} 아래에서 .tsx 를 한 개도 못 찾았습니다 — 추출기가 깨졌습니다.`);
  process.exit(2);
}

console.log(`파일 ${files.length} · 매치 ${rows.length}`);
console.log(
  [...byDomain.entries()]
    .sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]))
    .map(([d, n]) => `${d} ${n}`)
    .join(" · "),
);
console.log("---");
console.log(rows.sort().join("\n"));
