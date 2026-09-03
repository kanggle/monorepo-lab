#!/usr/bin/env bash
# =============================================================================
# scripts/check-message-backticks.sh — 가드 메시지가 자기 예시를 «실행» 하지 않는가
# =============================================================================
# TASK-MONO-619.
#
# 무엇을 재는가
# -----------------------------------------------------------------------------
# 셸의 큰따옴표 문자열 안에서 백틱은 **명령 치환**이다. 그래서 이런 줄은
#
#     warn "억제 선언은 ... <bt>profiles:<bt> 로 가려진 서비스는 ..."      (<bt> = 백틱)
#
# ① 그 토큰을 **실행하고** ② 그 자리의 문구를 **출력에서 지운다.**
# 실측(TASK-MONO-617 bite):
#
#     check-suppressed-containers.sh: line 169: profiles:: command not found
#     [suppressed] 🔴 억제 선언은 렌더에서만 효력이 있습니다.  로 가려진 서비스는 ...
#     docker: 'docker rm' requires at least 1 argument      ← 🔴 진짜로 실행됐다
#
# 🔴 **초록 경로에서는 절대 안 보인다.** 통과 메시지에는 백틱이 없으므로, 가드를 초록으로만
#    돌려 본 사람은 이 결함을 평생 못 보고, 정작 **물어야 할 순간에 자기 처방을 지운 채** 문다.
# 🔴 `bash -n` 도 못 잡는다 — 문법적으로 완전히 정상이기 때문이다.
#
# 술어 — 인용 문맥을 본다 (이 요구는 실측에서 나왔다)
# -----------------------------------------------------------------------------
# 초판 탐지기는 «메시지 호출 줄의 이스케이프 안 된 백틱» 으로만 재서
# `scripts/check-required-check-names.sh` 의 `grep -qF ...` 를 **거짓 양성**으로 냈다 —
# 거기 백틱은 **홑따옴표 안**이라 실행되지 않는다. 멀쩡한 코드를 고발하는 가드는 꺼진다.
# ⇒ 문자를 훑으며 **큰따옴표/홑따옴표 상태**를 추적한다:
#     · 이스케이프(`\x`)는 통째로 건너뛴다
#     · 큰따옴표 밖의 `'` 만 홑따옴표 상태를 뒤집는다 (큰따옴표 안의 `'` 는 리터럴이다)
#     · 홑따옴표 **밖**의 이스케이프 안 된 백틱만 위반이다
#
# 🔴 한계(의도한 것): 여러 줄에 걸친 인용은 추적하지 않는다. 이 저장소의 메시지는 한 줄
#    단위이고(`\` 로 이어붙이되 각 줄이 자기 문자열을 닫는다), 그 이상을 재려면 셸 파서가
#    필요하다. 못 재는 것을 재는 척하지 않는다.
#
# 모집단
# -----------------------------------------------------------------------------
# `git ls-files` 의 `*.sh` 전부 + `.github/workflows/*.yml`(인라인 `run:` 도 셸이다).
# 🔴 **스캔한 파일이 0개면 FAIL** 이다 — glob 이 안 맞아 0개를 훑고 «위반 없음» 으로
#    통과하는 것이 이 부류 가드의 표준 고장이다.
# 🔵 **위반 수에는 하한을 두지 않는다.** 이 모집단은 **비어 가는 것이 목표**다 —
#    다 고치면 0이 되고, 그때 하한이 있으면 성공이 고장난다.
#
# 사용
# -----------------------------------------------------------------------------
#   bash scripts/check-message-backticks.sh              # 저장소 전수
#   bash scripts/check-message-backticks.sh <파일...>    # 지정 파일만
#   bash scripts/check-message-backticks.sh --self-test  # 실트리를 변형해 무는지 증명
# =============================================================================
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

SELFTEST=0
FILES=()
for a in "$@"; do
  case "$a" in
    --self-test) SELFTEST=1 ;;
    -*) echo "알 수 없는 옵션: $a" >&2; exit 2 ;;
    *) FILES+=("$a") ;;
  esac
done

# ---------------------------------------------------------------------------
# scan <파일...> — 위반을 `파일:줄:내용` 으로 찍고, 위반 수를 종료코드가 아니라
# 마지막 줄(`COUNT=<n>`)로 알린다.
# ---------------------------------------------------------------------------
scan() {
  awk '
    BEGIN { sq = sprintf("%c", 39); bt = sprintf("%c", 96); dq = sprintf("%c", 34); n = 0 }
    {
      line = $0
      if (line ~ /^[ \t]*#/) next
      ismsg = (line ~ /(fail|warn|say|ok|note|echo|printf)[ \t]+"/)
      if (!ismsg && line ~ ("^[ \t]*\\$" sq "\\\\n" sq dq)) ismsg = 1
      if (!ismsg) next

      insq = 0; indq = 0; i = 1; len = length(line)
      while (i <= len) {
        c = substr(line, i, 1)
        if (c == "\\") { i += 2; continue }
        if (c == dq && !insq) { indq = !indq; i++; continue }
        if (c == sq && !indq) { insq = !insq; i++; continue }
        if (c == bt && !insq) { printf "%s:%d:%s\n", FILENAME, FNR, line; n++; break }
        i++
      }
    }
    END { printf "COUNT=%d\n", n }
  ' "$@"
}

# ---------------------------------------------------------------------------
# --self-test — 실트리를 **변형해서** 무는 것을 증명한다 (합성 픽스처 아님)
# ---------------------------------------------------------------------------
if [ "$SELFTEST" = 1 ]; then
  src="infra/demo/verify-demo-wrapper.sh"
  [ -f "$src" ] || { echo "self-test: 기준 파일이 없습니다: $src" >&2; exit 2; }
  tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT

  # 🔴🔴 상수부터 단언한다. 초판은 `printf '%c' 96` 을 썼는데 **bash 의 `%c` 는 인자
  #     문자열의 첫 글자를 찍으므로 `9` 가 나온다**(awk 의 `sprintf("%c",96)` 과 다르다).
  #     그리고 주입과 그 주입의 단언이 **같은 틀린 상수**를 써서 단언이 통과했다 —
  #     그래서 bite 가 「안 물었다」로만 보이고 원인은 안 보였다. 상수를 안 재면
  #     주입 단언은 자기 자신을 확인할 뿐이다.
  BT="$(printf '\140')"
  [ "$BT" = "$(awk 'BEGIN{printf "%c", 96}')" ] || {
    echo "self-test (0) 상수 실패: BT 가 백틱이 아닙니다 (얻은 값: '$BT')" >&2; exit 1; }

  probe() { scan "$1" | tail -1 | sed 's/COUNT=//'; }

  # (1) 대조군 — 손대지 않은 실파일
  cp "$src" "$tmp/a.sh"
  c1="$(probe "$tmp/a.sh")"
  [ "$c1" = "0" ] || { echo "self-test (1) 대조군 실패: 손대지 않은 $src 에서 위반 $c1 건" >&2; exit 1; }

  # (2) 주입 — 메시지 줄에 이스케이프 안 된 백틱. 🔴 주입을 먼저 단언한다.
  cp "$src" "$tmp/b.sh"
  printf '%s\n' "warn \"주입: ${BT}docker rm -f${BT} 를 쓰십시오\"" >> "$tmp/b.sh"
  grep -qF "${BT}docker rm -f${BT}" "$tmp/b.sh" || { echo "self-test (2) 주입 실패" >&2; exit 1; }
  c2="$(probe "$tmp/b.sh")"
  [ "$c2" = "1" ] || { echo "self-test (2) bite 실패: 주입했는데 위반 $c2 건 (기대 1)" >&2; exit 1; }

  # (3) 대조군 — **홑따옴표 안**의 백틱은 실행되지 않는다 ⇒ 물면 안 된다
  cp "$src" "$tmp/c.sh"
  printf '%s\n' "grep -qF '${BT}INDEX queue drift${BT}' file || echo \"없습니다\"" >> "$tmp/c.sh"
  grep -qF "${BT}INDEX queue drift${BT}" "$tmp/c.sh" || { echo "self-test (3) 주입 실패" >&2; exit 1; }
  c3="$(probe "$tmp/c.sh")"
  [ "$c3" = "0" ] || { echo "self-test (3) 거짓 양성: 홑따옴표 안의 백틱을 위반 $c3 건으로 셌습니다" >&2; exit 1; }

  # (4) 대조군 — **주석 안**의 백틱 ⇒ 물면 안 된다 (이 파일 헤더가 그렇게 쓴다)
  cp "$src" "$tmp/d.sh"
  printf '%s\n' "# 주석: warn \"${BT}docker rm -f${BT}\" 는 예시입니다" >> "$tmp/d.sh"
  c4="$(probe "$tmp/d.sh")"
  [ "$c4" = "0" ] || { echo "self-test (4) 거짓 양성: 주석 안의 백틱을 위반 $c4 건으로 셌습니다" >&2; exit 1; }

  echo "self-test OK — 대조군 0 / 주입 1 / 홑따옴표 0 / 주석 0 (실트리 변형)"
  exit 0
fi

# ---------------------------------------------------------------------------
# 본 실행
# ---------------------------------------------------------------------------
if [ "${#FILES[@]}" -eq 0 ]; then
  mapfile -t FILES < <(git ls-files '*.sh' '.github/workflows/*.yml' 2>/dev/null)
fi

# 🔴 모집단 하한 — 0파일을 훑고 «위반 없음» 으로 통과하지 않는다.
if [ "${#FILES[@]}" -lt 1 ]; then
  echo "check-message-backticks: FAIL — 스캔한 파일이 0개입니다." >&2
  echo "  → «위반이 없다» 가 아니라 **아무것도 안 봤다** 입니다 (git ls-files 가 비었거나 저장소 밖)." >&2
  exit 1
fi

out="$(scan "${FILES[@]}")"
count="$(printf '%s\n' "$out" | tail -1 | sed 's/COUNT=//')"

if [ "${count:-0}" != "0" ]; then
  echo "check-message-backticks: FAIL — 메시지 문자열 안의 이스케이프 안 된 백틱 ${count}건" >&2
  printf '%s\n' "$out" | sed '$d' | sed 's/^/  /' >&2
  echo >&2
  echo "  🔴 셸의 큰따옴표 안에서 백틱은 **명령 치환**입니다. 두 가지가 일어납니다:" >&2
  echo "     ① 그 자리의 문구가 출력에서 **사라집니다** (처방의 핵심이 빕니다)" >&2
  echo "     ② 그 토큰이 PATH 에 있으면 **실제로 실행됩니다** — TASK-MONO-617 에서" >&2
  echo "        docker rm 이 그렇게 실행됐습니다(인자가 없어 실패했을 뿐입니다)." >&2
  echo "  → 고치는 법: 백틱 앞에 백슬래시를 붙이십시오. **문구는 바꾸지 마십시오** —" >&2
  echo "     백틱은 이 저장소가 코드 조각을 감싸는 관례이고, 지우면 처방이 읽기 어려워집니다." >&2
  echo "  → 확인: 고친 뒤 그 메시지를 **실제로 출력시켜** 보십시오. bash -n 통과는 증거가" >&2
  echo "     아닙니다 — 이 결함은 문법적으로 정상입니다." >&2
  exit 1
fi

echo "check-message-backticks: OK — ${#FILES[@]}개 파일, 메시지 안 이스케이프 안 된 백틱 0건."
echo "  (위반 수에는 하한을 두지 않습니다 — 이 모집단은 비어 가는 것이 목표입니다.)"
exit 0
