#!/usr/bin/env bash
# =============================================================================
# package.json 의 scripts 가 가리키는 로컬 파일이 **추적되는가** — TASK-MONO-640
# =============================================================================
# 🔴🔴 이 가드가 생긴 이유는 «파일이 없어서» 가 아니다. 파일은 **있었다.**
#
#   `infra/demo/public-data/bin/` 의 생성기와 발행자는 작성자의 트리에 멀쩡히 있었고,
#   그 트리에서는 `npm run build-snapshots` 가 잘 돌았다. 그런데 루트 `.gitignore` 의
#   `bin/` 패턴(Gradle 산출물용, 경로 제한 없음)이 그 디렉터리를 삼켜서 **저장소에는 한
#   번도 들어가지 않았다.** 그동안 README 는 그 스크립트를 정본 사용법으로 적었고,
#   *"snapshots/ 는 손으로 쓰지 않는다"* 라고 금지까지 해 뒀다.
#
# 🔴 그래서 술어를 «파일이 존재하는가» 로 짜면 **이 결함을 못 잡는다** — 작성자의 트리에서도
#    CI 에서도 그 검사는 초록이다(CI 는 체크아웃한 트리만 보므로 애초에 파일이 없어 다른
#    이유로 빨개지겠지만, 로컬에서는 영원히 초록이다). 물어야 하는 것은 **«추적되는가»** 다.
#    ⇒ `git ls-files --error-unmatch` 로 묻는다.
#
# 무엇을 세는가
# -----------------------------------------------------------------------------
# 추적되는 `package.json` 전부의 `scripts` 값에서 **로컬 스크립트처럼 보이는 토큰**을 뽑는다
# (`.mjs`·`.cjs`·`.js`·`.ts`·`.sh` 로 끝나고, 옵션(`-`)도 URL 도 아닌 것). 그 토큰을 그
# `package.json` 의 디렉터리 기준으로 해석해 **추적 여부**를 묻는다.
#
# 🔵 의도한 한계: `npx`·`node -e`·셸 변수로 조립되는 경로는 안 본다. 그것까지 재려면 셸을
#    해석해야 한다. **못 재는 것을 재는 척하지 않는다** — 대신 아래 «모집단» 을 출력한다.
#
# 모집단
# -----------------------------------------------------------------------------
# 🔴 스캔한 `package.json` 이 0개거나 추출된 대상이 0건이면 **FAIL** 이다. 0개를 훑고
#    «위반 없음» 으로 통과하는 것이 이 부류 가드의 표준 고장이다.
# 🔵 위반 수에는 하한을 두지 않는다 — 이 모집단은 **비어 있는 것이 정상**이고, 하한을 두면
#    다 고쳤을 때 성공이 고장난다.
#
# 사용
# -----------------------------------------------------------------------------
#   bash scripts/check-package-script-targets.sh              # 저장소 전수
#   bash scripts/check-package-script-targets.sh --self-test  # 실트리를 변형해 무는지 증명
#
# 🔴 이 검사는 **인덱스**(`git ls-files`)를 읽는다. 스테이지 전에 돌리면 방금 만든 파일을
#    «추적 안 됨» 으로 본다 — 그것은 오검출이 아니라 **사실**이지만, 커밋 직전에 돌리는 것이
#    의미가 있다(이 저장소는 스테이지 전 가드로 세 번 헛짚은 적이 있다).
# =============================================================================
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

SELFTEST=0
for a in "$@"; do
  case "$a" in
    --self-test) SELFTEST=1 ;;
    *) echo "알 수 없는 옵션: $a" >&2; exit 2 ;;
  esac
done

# ---------------------------------------------------------------------------
# extract — 추적되는 package.json 에서 `<package.json>\t<script 이름>\t<대상 경로>` 를 뽑는다
# ---------------------------------------------------------------------------
# 🔴 파일 목록을 **stdin 으로** 넘긴다. 임시 파일 경로로 넘기면 이 호스트(msys 셸 +
#    Windows 네이티브 node)에서 `/tmp/x` 가 `C:\tmp\x` 로 해석돼 ENOENT 로 죽는다.
extract() {
  git ls-files '*package.json' | grep -v node_modules | node -e '
    const fs = require("fs"), path = require("path");
    let buf = "";
    process.stdin.on("data", (c) => (buf += c)).on("end", () => {
      const files = buf.split("\n").filter(Boolean);
      const EXT = /\.(mjs|cjs|js|ts|sh)$/;
      let n = 0;
      for (const f of files) {
        let pkg;
        try { pkg = JSON.parse(fs.readFileSync(f, "utf8")); } catch { continue; }
        const dir = path.posix.dirname(f);
        for (const [name, cmd] of Object.entries(pkg.scripts || {})) {
          for (const tok of String(cmd).split(/\s+/)) {
            if (tok.startsWith("-") || tok.includes("://")) continue;
            if (!EXT.test(tok)) continue;
            const rel = path.posix.join(dir, tok.replace(/^\.\//, ""));
            console.log([f, name, rel].join("\t"));
            n++;
          }
        }
      }
      console.error("SCANNED=" + files.length + " TARGETS=" + n);
    });
  '
}

scan() {  # → 위반을 찍고, 마지막 줄에 COUNT=<n>
  local rows npkg ntgt viol=0
  rows="$(extract 2>"$ROOT/.pkgscan.err")"
  npkg="$(sed -n 's/.*SCANNED=\([0-9]*\).*/\1/p' "$ROOT/.pkgscan.err")"
  ntgt="$(sed -n 's/.*TARGETS=\([0-9]*\)/\1/p' "$ROOT/.pkgscan.err")"
  rm -f "$ROOT/.pkgscan.err"
  # 🔴 모집단부터 센다. 0 이면 아래 루프는 아무것도 시험하지 않으면서 초록이다.
  if [ -z "${npkg:-}" ] || [ "$npkg" = "0" ]; then
    echo "POP_FAIL=package.json 을 0개 훑었습니다"; echo "COUNT=-1"; return
  fi
  if [ -z "${ntgt:-}" ] || [ "$ntgt" = "0" ]; then
    echo "POP_FAIL=로컬 스크립트 대상을 0건 뽑았습니다(추출식이 깨졌습니까?)"; echo "COUNT=-1"; return
  fi
  while IFS=$'\t' read -r pj name target; do
    [ -n "${target:-}" ] || continue
    if [ ! -e "$target" ]; then
      echo "$pj: '$name' 이 없는 파일을 가리킵니다: $target"
      viol=$((viol + 1))
    elif ! git ls-files --error-unmatch -- "$target" >/dev/null 2>&1; then
      echo "$pj: '$name' 이 **추적되지 않는** 파일을 가리킵니다: $target"
      echo "    → 파일은 있는데 저장소에는 없습니다. 새로 클론한 사람에게는 이 스크립트가 없습니다."
      echo "    → .gitignore 가 삼켰는지 보세요: git check-ignore -v $target"
      viol=$((viol + 1))
    fi
  done <<< "$rows"
  echo "PKG=$npkg TARGETS=$ntgt"
  echo "COUNT=$viol"
}

# ---------------------------------------------------------------------------
# --self-test — 실트리를 **변형해서** 무는 것을 증명한다 (합성 픽스처가 아니다)
# ---------------------------------------------------------------------------
if [ "$SELFTEST" = 1 ]; then
  probe() { scan | tail -1 | sed 's/COUNT=//'; }

  # (1) 대조군 — 손대지 않은 실트리
  c1="$(probe)"
  [ "$c1" = "0" ] || { echo "self-test (1) 대조군 실패: 손대지 않은 트리에서 위반 $c1 건" >&2; scan >&2; exit 1; }

  tmp_pkg="$ROOT/.selftest-pkg"
  cleanup() { rm -rf "$tmp_pkg"; }
  trap cleanup EXIT
  mkdir -p "$tmp_pkg"

  # (2) 없는 파일을 가리키는 스크립트 → 물어야 한다
  printf '%s\n' '{"name":"selftest-missing","scripts":{"x":"node bin/nope.mjs"}}' > "$tmp_pkg/package.json"
  git add -N "$tmp_pkg/package.json" >/dev/null 2>&1
  c2="$(probe)"
  [ "$c2" = "1" ] || { echo "self-test (2) bite 실패: 없는 파일을 가리켰는데 위반 $c2 건 (기대 1)" >&2; exit 1; }

  # (3) 🔴 **있는데 추적 안 되는** 파일 → 물어야 한다. 이 축이 이 가드의 존재 이유다.
  #     (2)만 있으면 «존재하는가» 가드와 구별되지 않는다.
  printf '%s\n' '{"name":"selftest-untracked","scripts":{"x":"node bin/local-only.mjs"}}' > "$tmp_pkg/package.json"
  mkdir -p "$tmp_pkg/bin"
  printf '%s\n' 'console.log("local only");' > "$tmp_pkg/bin/local-only.mjs"
  [ -e "$tmp_pkg/bin/local-only.mjs" ] || { echo "self-test (3) 주입 실패: 파일이 안 만들어졌습니다" >&2; exit 1; }
  git ls-files --error-unmatch -- "$tmp_pkg/bin/local-only.mjs" >/dev/null 2>&1 \
    && { echo "self-test (3) 주입 실패: 그 파일이 이미 추적됩니다 — 이 칸은 다른 것을 재게 됩니다" >&2; exit 1; }
  c3="$(probe)"
  [ "$c3" = "1" ] || { echo "self-test (3) bite 실패: 있는데 추적 안 되는 파일을 가리켰는데 위반 $c3 건 (기대 1)" >&2; exit 1; }

  # (4) 대조군 — 추적되는 파일을 가리키면 물면 **안 된다**
  # 🔴 경로는 **그 package.json 의 디렉터리 기준**으로 풀린다. 이 자리표시 패키지는
  #    `.selftest-pkg/` 에 있으므로 `../` 로 올라가야 저장소의 실제 파일을 가리킨다.
  #    (초판은 루트 기준으로 적었다가 «없는 파일» 로 물렸다 — 가드가 옳았고 픽스처가 틀렸다.)
  printf '%s\n' '{"name":"selftest-ok","scripts":{"x":"bash ../scripts/check-package-script-targets.sh"}}' > "$tmp_pkg/package.json"
  c4="$(probe)"
  [ "$c4" = "0" ] || { echo "self-test (4) 대조군 실패: 추적되는 파일을 가리켰는데 위반 $c4 건 (기대 0)" >&2; scan >&2; exit 1; }

  git rm -q --cached -- "$tmp_pkg/package.json" >/dev/null 2>&1 || true
  cleanup; trap - EXIT
  echo "check-package-script-targets --self-test: OK — 대조군 2칸 · bite 2칸(없는 파일 · 추적 안 되는 파일)"
  exit 0
fi

out="$(scan)"
count="$(printf '%s\n' "$out" | tail -1 | sed 's/COUNT=//')"
pop="$(printf '%s\n' "$out" | grep '^PKG=' || true)"

if printf '%s\n' "$out" | grep -q '^POP_FAIL='; then
  printf '%s\n' "$out" | grep '^POP_FAIL=' | sed 's/^POP_FAIL=/check-package-script-targets: FAIL — /' >&2
  echo "  → 0건을 훑고 «위반 없음» 으로 통과하는 것이 이 부류 가드의 표준 고장입니다." >&2
  exit 1
fi

if [ "$count" != "0" ]; then
  echo "check-package-script-targets: FAIL — package.json 의 scripts 가 가리키는 파일 $count 건이 저장소에 없습니다." >&2
  printf '%s\n' "$out" | grep -v '^COUNT=' | grep -v '^PKG=' >&2
  exit 1
fi

echo "check-package-script-targets: OK — $pop · 저장소에 없는 대상 0건."
echo "  (위반 수에는 하한을 두지 않습니다 — 이 모집단은 비어 있는 것이 정상입니다.)"
