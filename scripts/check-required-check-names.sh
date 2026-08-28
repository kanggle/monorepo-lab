#!/usr/bin/env bash
# =============================================================================
# required status check 의 **이름**이 세 곳에서 서로 어긋나지 않는가  (TASK-MONO-599)
# =============================================================================
# 무엇을 지키는가 — **한 문자열이 세 곳에 산다**:
#
#   ① GitHub branch protection 의 `required_status_checks.contexts`  (진실)
#   ② `.github/workflows/ci.yml` 의 job `name:`                        (그 문자열을 만드는 쪽)
#   ③ CLAUDE.md · platform/git-workflow-policy.md · .claude/commands/review-task.md
#                                                                      (그 문자열을 읽는 쪽)
#
# ①은 저장소 밖이라 CI 러너가 못 읽는다(아래 §못 무는 것). 그래서 ①을 **핀**
# (`scripts/required-check-names.txt`)으로 들여오고, 이 가드는 **핀 ↔ ② ↔ ③** 을 맞춘다.
#
# 🔴🔴 **왜 이게 위험한 축인가**: required context 는 **문자열 매칭**이다. `ci.yml` 의
#    `name:` 을 한 글자 다듬으면 등록된 문자열에 대응하는 체크런이 안 생기고 required 가
#    **영구 pending** → `main` 이 모든 PR 에서 BLOCKED. 그 리네임 PR 자신은 **초록으로
#    머지되고**, 다음 PR 부터 전부 막힌다 — 원인은 두 PR 전이다. 자기를 막지 못하는
#    변경이라 사람이 잡기 어렵다.
#
# 🔴 **못 무는 것 — 반대 방향** (TASK-MONO-599 AC-2 가 명시적으로 남긴 공백):
#    누가 **branch protection 쪽**을 바꾸면(컨텍스트 추가/제거/개명) 이 가드는 여전히
#    초록이다. 핀은 저장소 안의 사본이고, 사본은 원본이 바뀐 것을 모른다. protection API
#    는 admin 권한을 요구해서 워크플로의 `GITHUB_TOKEN` 으로 읽는다는 보장이 없고,
#    **못 도는 가드는 초록으로 썩는다** — 그래서 그 축을 이 가드에 넣지 않았다.
#    ⇒ 소유자가 required 집합을 바꾸면 **핀을 다시 만들어야 한다**(핀 파일 헤더 참조).
#
# 🔵 판정은 **등호**다, 부분문자열이 아니다. TASK-MONO-599 의 결함이 하루를 살아남은
#    이유가 정확히 그것이다 — `grep 'INDEX queue drift'` 는 맞고
#    `select(.name=="INDEX queue drift")` 는 틀렸다. ②는 `name:` 줄 **전체**와 등호로,
#    ③은 문서 안에서 **고정 문자열**로 본다(문서는 산문이라 등호를 걸 수 없다 — 그
#    대신 «괄호를 뺀 짧은 형태가 단독으로 있는가» 를 **따로** 잡는다, 아래 (4)).
#
# 사용법:
#   bash scripts/check-required-check-names.sh            # 저장소 판정
#   bash scripts/check-required-check-names.sh --self-test  # 가드가 무는지 증명
#
# 종료코드: 0 = OK · 1 = 드리프트 · 2 = 가드 자신이 못 돈다(핀/파일 부재)
# -----------------------------------------------------------------------------
set -uo pipefail

SELF="$(cd "$(dirname "$0")" && pwd)/$(basename "$0")"

ROOT="${CHECK_ROOT:-$(git rev-parse --show-toplevel 2>/dev/null)}"
if [ -z "$ROOT" ] || [ ! -d "$ROOT" ]; then
  echo "[required-names] ✖ 저장소 루트를 못 찾았습니다." >&2
  exit 2
fi

PIN="$ROOT/scripts/required-check-names.txt"
CI="$ROOT/.github/workflows/ci.yml"
DOCS=(
  "CLAUDE.md"
  "platform/git-workflow-policy.md"
  ".claude/commands/review-task.md"
)

# =============================================================================
# --self-test — 이 가드가 **무는지** 증명한다
# =============================================================================
# 🔴 가드를 «초록이더라» 로 넘기지 않는다. 각 칸은 **주입이 실제로 적용됐는지 먼저
#    단언**하고 그 다음에 판정을 읽는다 — 안 그러면 «주입이 실패해서 통과» 를
#    «가드가 안 문다» 로 읽거나 그 반대가 된다.
if [ "${1:-}" = "--self-test" ]; then
  pass=0; total=0
  st_mk() {                                  # 저장소의 5개 파일만 임시 트리로 복사
    local t; t="$(mktemp -d)"
    mkdir -p "$t/scripts" "$t/.github/workflows" "$t/platform" "$t/.claude/commands"
    cp "$PIN" "$t/scripts/required-check-names.txt"
    cp "$CI"  "$t/.github/workflows/ci.yml"
    for d in "${DOCS[@]}"; do mkdir -p "$t/$(dirname "$d")"; cp "$ROOT/$d" "$t/$d"; done
    printf '%s' "$t"
  }
  st_cell() {                                # $1=label $2=expected_rc $3=tree
    local label="$1" want="$2" t="$3" got
    CHECK_ROOT="$t" bash "$SELF" >/dev/null 2>&1; got=$?
    total=$((total+1))
    if [ "$got" -eq "$want" ]; then pass=$((pass+1)); printf '  ✅ %-52s rc=%s\n' "$label" "$got"
    else printf '  ❌ %-52s rc=%s (기대 %s)\n' "$label" "$got" "$want"; fi
    rm -rf "$t"
  }

  echo "[required-names] --self-test"

  # (a) 양성 대조군 — 손대지 않은 사본은 통과해야 한다. 이게 빨가면 아래 칸들의
  #     «RED» 는 주입 때문인지 픽스처 때문인지 구별되지 않는다.
  st_cell "(a) 무손상 사본 → 통과" 0 "$(st_mk)"

  # (b) 🔴 진짜 재발 경로 — ci.yml 의 job 이름을 한 글자 다듬는다
  t="$(st_mk)"
  sed -i 's/^    name: INDEX queue drift (INDEX\.md tables vs queue directories)$/    name: INDEX queue drift (INDEX.md tables vs queue dirs)/' "$t/.github/workflows/ci.yml"
  grep -qF 'queue dirs)' "$t/.github/workflows/ci.yml" || { echo "  ⚠ (b) 주입 실패 — 칸을 신뢰할 수 없음"; exit 3; }
  st_cell "(b) ci.yml job 이름 리네임 → RED" 1 "$t"

  # (c) 문서 한 곳에서 이름이 사라진다
  t="$(st_mk)"
  before=$(grep -cF 'Task ID collision (duplicate IDs in active queues)' "$t/CLAUDE.md")
  sed -i 's/Task ID collision (duplicate IDs in active queues)/Task ID collision/g' "$t/CLAUDE.md"
  after=$(grep -cF 'Task ID collision (duplicate IDs in active queues)' "$t/CLAUDE.md")
  [ "$before" -gt 0 ] && [ "$after" -eq 0 ] || { echo "  ⚠ (c) 주입 실패 ($before→$after)"; exit 3; }
  st_cell "(c) 문서에서 전체 이름 소실 → RED" 1 "$t"

  # (d) 핀이 조용히 줄어든다
  t="$(st_mk)"
  grep -v 'Task ID collision' "$t/scripts/required-check-names.txt" > "$t/p.tmp" && mv "$t/p.tmp" "$t/scripts/required-check-names.txt"
  [ "$(grep -vc '^#' "$t/scripts/required-check-names.txt")" -eq 3 ] || { echo "  ⚠ (d) 주입 실패"; exit 3; }
  st_cell "(d) 핀 4→3 (FLOOR) → RED" 1 "$t"

  # (e) 🔴🔴 TASK-MONO-599 의 결함 그 자체 — 전체 이름은 그대로 두고
  #     **짧은 형태를 코드 스팬으로 하나 더** 넣는다. (2)만 있으면 이건 통과해 버린다.
  t="$(st_mk)"
  printf '\n요약: `INDEX queue drift` 하나면 충분하다.\n' >> "$t/platform/git-workflow-policy.md"
  grep -qF '`INDEX queue drift`' "$t/platform/git-workflow-policy.md" || { echo "  ⚠ (e) 주입 실패"; exit 3; }
  grep -qF 'INDEX queue drift (INDEX.md tables vs queue directories)' "$t/platform/git-workflow-policy.md" || { echo "  ⚠ (e) 전체 이름이 사라짐 — 칸이 (c)와 구별 안 됨"; exit 3; }
  st_cell "(e) 짧은 형태가 코드 스팬으로 남음 → RED" 1 "$t"

  # (f) 🔴 인코딩 대조군 — `§` 가 든 이름이 실제로 **매치되고 있는지** 직접 확인한다.
  #     (a)가 초록인 것만으로는 부족하다: 만약 그 이름을 아무도 못 찾는 게 아니라
  #     **핀에서 빈 줄로 읽혔다면** 역시 초록이 된다.
  t="$(st_mk)"; total=$((total+1))
  n_sec="$(grep -v '^#' "$t/scripts/required-check-names.txt" | grep 'Walkthrough')"
  if [ -n "$n_sec" ] && grep -qF -- "$n_sec" "$t/CLAUDE.md" && grep -qF -- "$n_sec" "$t/.github/workflows/ci.yml"; then
    pass=$((pass+1)); printf '  ✅ %-52s «%s»\n' "(f) § 이름이 실제로 매치된다" "${n_sec:0:34}…"
  else
    printf '  ❌ %-52s (핀에서 읽힌 값: «%s»)\n' "(f) § 이름이 실제로 매치된다" "$n_sec"
  fi
  rm -rf "$t"

  # (g) 🔵 CRLF 대조군 — 핀이 CRLF 로 체크아웃돼도 **통과해야** 한다. 이 칸이 빨가면
  #     가드가 Windows 에서만 빨개지고, 원인이 내용이 아니라 체크아웃 바이트라
  #     진단이 오래 걸린다. (bite 가 아니라 «깨지지 않음» 을 지키는 칸이다.)
  t="$(st_mk)"
  sed -i 's/$/\r/' "$t/scripts/required-check-names.txt"
  grep -qU $'\r' "$t/scripts/required-check-names.txt" 2>/dev/null || \
    od -c "$t/scripts/required-check-names.txt" | grep -q '\\r' || { echo "  ⚠ (g) 주입 실패 — CR 이 안 들어감"; exit 3; }
  st_cell "(g) 핀이 CRLF 여도 통과" 0 "$t"

  echo "[required-names] --self-test $pass/$total"
  [ "$pass" -eq "$total" ] && exit 0 || exit 1
fi
# 핀이 이보다 적어지면 «조용히 줄었다» 이므로 실패한다. 늘어나는 것은 허용(소유자가
# required 를 추가할 수 있다) — 줄어드는 쪽만 이 가드가 감지할 수 있는 사고다.
FLOOR=4

fail=0
note() { printf '  %s\n' "$1"; }

# --- (0) 가드 자신이 돌 수 있는가 -------------------------------------------
for f in "$PIN" "$CI"; do
  [ -f "$f" ] || { echo "[required-names] ✖ 없음: ${f#$ROOT/}" >&2; exit 2; }
done
for d in "${DOCS[@]}"; do
  [ -f "$ROOT/$d" ] || { echo "[required-names] ✖ 없음: $d" >&2; exit 2; }
done

# 핀 읽기 — 주석/빈 줄 제외
# 🔵 `\r` 을 벗긴다 — 이 호스트는 Windows 고, 핀이 CRLF 로 체크아웃되면 이름 끝의 `\r`
#    때문에 넷 다 «없음» 이 되어 **Windows 에서만 빨간 가드**가 된다. `.gitattributes` 가
#    blob 을 LF 로 고정하지만 이중 방어다(self-test 칸 (g)).
names=()
while IFS= read -r line; do
  line="${line%$'\r'}"
  case "$line" in ''|'#'*) continue ;; esac
  names+=("$line")
done < "$PIN"

if [ "${#names[@]}" -lt "$FLOOR" ]; then
  echo "[required-names] ✖ 핀이 ${#names[@]}개입니다 — 하한 $FLOOR." >&2
  echo "  핀이 조용히 줄면 이 가드가 지키는 이름도 같이 사라집니다." >&2
  exit 1
fi

# --- (1)(2) 핀의 각 이름이 ci.yml 과 문서에 그대로 있는가 --------------------
for n in "${names[@]}"; do
  # ② ci.yml — `name: <핀>` 이 줄 전체와 일치하거나, `name:` 없는 job 이라
  #    job id 가 그대로 context 인 경우(`  <핀>:`). 후자는 근사이므로 아래에 적는다.
  if grep -qxF "    name: $n" "$CI" || grep -qxF "  name: $n" "$CI" || grep -qxF "  $n:" "$CI"; then
    :
  else
    note "✖ ci.yml 에 없음: «$n»"
    note "  → job 이름을 바꿨다면 **branch protection 을 먼저 갱신**해야 합니다."
    note "     지금 이대로 머지하면 이 PR 은 초록이고 **다음 PR 부터 전부 BLOCKED** 됩니다."
    fail=1
  fi

  # ③ 문서 — 고정 문자열로 존재해야 한다
  for d in "${DOCS[@]}"; do
    grep -qF -- "$n" "$ROOT/$d" || { note "✖ $d 에 없음: «$n»"; fail=1; }
  done
done

# --- (3) 🔴 짧은 형태가 단독으로 남아 있지 않은가 ----------------------------
# TASK-MONO-599 가 고친 결함 자체의 재발 가드다. 「괄호를 뺀 이름」을 백틱으로 감싼 채
# 문서에 두면, 그것으로 protection 을 재등록했을 때 **영구 pending** 이 된다.
for n in "${names[@]}"; do
  short="${n%% (*}"
  [ "$short" = "$n" ] && continue          # 괄호가 없는 이름(`changes`)은 해당 없음
  for d in "${DOCS[@]}"; do
    if grep -qF -- "\`$short\`" "$ROOT/$d"; then
      note "✖ $d 에 **짧은 형태**가 코드 스팬으로 있습니다: \`$short\`"
      note "  → 괄호는 생략 가능한 장식이 아니라 **다른 문자열**입니다."
      note "     전체 이름: «$n»"
      fail=1
    fi
  done
done

if [ "$fail" -ne 0 ]; then
  echo "[required-names] ✖ required check 이름이 어긋났습니다." >&2
  exit 1
fi

echo "[required-names] ok — 핀 ${#names[@]}개가 ci.yml 과 문서 ${#DOCS[@]}곳에서 일치."
echo "  (🔴 branch protection 쪽 변경은 이 가드가 **못 봅니다** — 헤더 §못 무는 것 참조.)"
exit 0
