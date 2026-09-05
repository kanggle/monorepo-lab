#!/usr/bin/env bash
# =============================================================================
# check-host-drift.sh — 데모 호스트의 체크아웃이 **저장소 판본 그대로인가** (TASK-MONO-615 C3)
# =============================================================================
# `TASK-MONO-615` AC-3 의 C3 판정은 *"새 AMI 의 `demo.env` 가 저장소 판본이고, 호스트
# 로컬 수정이 **0건**"* 이다. 기동 창 #2 가 그것을 `git status --short` 로 쟀고, 1줄이
# 나왔다:
#
#     M infra/demo/demo-boot.sh        ← 내용 diff 는 0/0, 차이는 **실행 비트뿐**
#     mode change 100644 => 100755
#
# 🔴🔴 **술어가 mode change 를 content change 와 구별하지 못한다.** 그래서 두 가지가
#    동시에 참이었다: 판정이 묻는 것(«손으로 고친 값이 없는가»)은 충족되는데, 적힌
#    술어(`git status` 0줄)는 충족되지 않는다. 그 상태로 두면 둘 중 하나가 일어난다:
#      · 다음 사람이 그 한 줄을 **드리프트로 오독**한다, 또는
#      · 술어를 느슨하게 고쳐(`git status` 를 지워) **진짜 내용 변경까지 통과**시킨다.
#
# 🔵 615 § C3 이 방향을 이미 적었다: *"`git diff --numstat` 이 0 인가 + `--summary` 가
#    mode change 뿐인가이지, `git status` 를 지우는 것이 아니다."* 이 파일이 그것이다.
#
# 🔴🔴 **왜 산문이 아니라 스크립트인가** — `TASK-MONO-622` 가 방금 같은 교훈을 랜딩했다:
#    규칙이 주석에 적혀 있어도 **주석은 게이트가 아니다**(그 티켓에서 규칙은 위반한
#    메시지 여섯 줄 위에 있었고, 그래도 어겨졌다). 손으로 치는 `git status` 는 술어가
#    아니라 «기억» 이다.
#
# -----------------------------------------------------------------------------
# 무엇을 «드리프트» 로 세는가 — 그리고 무엇을 안 세는가
# -----------------------------------------------------------------------------
#   드리프트 O : 추적 파일의 **내용** 변경(numstat 이 0/0 이 아닌 것)
#   드리프트 O : **추적되지 않은 파일**(호스트에서 누가 새로 만든 것)
#   드리프트 X : **mode 변경만** 있는 파일 — AMI 굽기가 남기는 자국이다(기동 창 #1·#2
#                두 세대에서 같은 파일에 재현됐다). 🔴 그래도 **조용히 넘기지 않는다**:
#                이름을 찍고 «면제했다» 고 말한다. 조용한 면제는 다음 사람에게 «원래
#                그런 것» 으로 읽힌다.
#   드리프트 X : `.gitignore` 된 것 — 런타임 산출물이고 애초에 저장소 판본이 없다.
#
# 🔴 **부재 판정을 하지 않는다.** git 이 없거나 경로가 저장소가 아니면 rc=2(**판정 불가**)
#    다. «변경 0건» 과 «못 쟀다» 를 같은 초록으로 만들면 이 스크립트는 무의미해진다.
#
# -----------------------------------------------------------------------------
# 종료 코드 — 🔴 1과 2를 가른다
# -----------------------------------------------------------------------------
#   0  CLEAN   내용 변경 0 · 미추적 0  (mode 변경은 있을 수 있고, 있으면 이름을 찍는다)
#   1  DIRTY   내용 변경 또는 미추적 파일이 있다 — 호스트에서 누가 고쳤다
#   2  UNKNOWN 판정 불가(git 없음 · 저장소 아님 · git 명령 실패)
#
# 사용:
#   bash infra/demo/check-host-drift.sh [<repo-path>]   # 기본값 = 이 스크립트의 저장소
#   bash infra/demo/check-host-drift.sh --self-test     # 임시 저장소 5칸으로 술어를 검증
#
# 기동 창에서(SSM 읽기 전용):
#   bash /opt/demo/monorepo-lab/infra/demo/check-host-drift.sh; echo rc=$?
# =============================================================================
set -uo pipefail

die_unknown() { echo "[host-drift] ? 판정 불가 — $*" >&2; exit 2; }

drift_check() {
  local repo="$1" numstat summary untracked content_files mode_files rc
  command -v git >/dev/null 2>&1 || { echo "UNKNOWN git 없음"; return 2; }
  [ -d "$repo" ] || { echo "UNKNOWN 경로 없음: $repo"; return 2; }
  git -C "$repo" rev-parse --is-inside-work-tree >/dev/null 2>&1 \
    || { echo "UNKNOWN 저장소가 아님: $repo"; return 2; }

  # 🔴 numstat 은 **내용** 만 센다 — mode 만 바뀐 파일은 0<TAB>0<TAB>path 로 나온다.
  numstat="$(git -C "$repo" diff --numstat 2>/dev/null)" || { echo "UNKNOWN diff 실패"; return 2; }
  summary="$(git -C "$repo" diff --summary 2>/dev/null)" || { echo "UNKNOWN summary 실패"; return 2; }
  untracked="$(git -C "$repo" ls-files --others --exclude-standard 2>/dev/null)" \
    || { echo "UNKNOWN ls-files 실패"; return 2; }

  # 내용이 바뀐 파일 = numstat 의 앞 두 칸이 모두 0 이 **아닌** 줄.
  # 🔵 바이너리는 `-`<TAB>`-` 로 나온다 — 0 이 아니므로 내용 변경으로 센다(맞는 방향이다).
  content_files="$(printf '%s\n' "$numstat" | awk -F'\t' 'NF>=3 && !($1=="0" && $2=="0") {print $3}')"
  mode_files="$(printf '%s\n' "$summary" | sed -n 's/^ mode change [0-9]* => [0-9]* //p')"

  local nc nu nm
  nc=$(printf '%s' "$content_files" | grep -c . || true)
  nu=$(printf '%s' "$untracked"     | grep -c . || true)
  nm=$(printf '%s' "$mode_files"    | grep -c . || true)

  if [ "$nc" -gt 0 ] || [ "$nu" -gt 0 ]; then
    echo "DIRTY content=$nc untracked=$nu mode=$nm"
    [ "$nc" -gt 0 ] && printf '%s\n' "$content_files" | sed 's/^/  content: /'
    [ "$nu" -gt 0 ] && printf '%s\n' "$untracked"     | sed 's/^/  untracked: /'
    return 1
  fi
  echo "CLEAN content=0 untracked=0 mode=$nm"
  [ "$nm" -gt 0 ] && printf '%s\n' "$mode_files" | sed 's/^/  mode-only(면제): /'
  return 0
}

# ---------------------------------------------------------------------------
# self-test — 🔴 임시 저장소를 **실제로 만들어** 술어를 건다. 픽스처 문자열이 아니다.
#   손으로 지어낸 diff 문자열은 git 의 실제 출력보다 관대해서 초록이 공허해진다.
# ---------------------------------------------------------------------------
self_test() {
  local tmp fails=0
  command -v git >/dev/null 2>&1 || die_unknown "self-test 에 git 이 필요합니다"
  tmp="$(mktemp -d)" || die_unknown "mktemp 실패"
  # shellcheck disable=SC2064
  trap "rm -rf '$tmp'" EXIT

  mk() {   # mk <name> → $tmp/<name> 에 커밋 1개짜리 저장소를 만든다
    local d="$tmp/$1"
    mkdir -p "$d"
    git -C "$d" init -q 2>/dev/null
    git -C "$d" config user.email z@z; git -C "$d" config user.name z
    # 🔴 mode 칸이 **파일시스템에 의존하지 않게** 한다. Windows/msys 에서는
    #    core.filemode 가 기본 false 라 `chmod +x` 가 아무 diff 도 안 만들고, 그러면
    #    「mode 변경만 → CLEAN」 칸이 **주입 없이 통과**한다(= 공허한 초록).
    #    🔵 실측(2026-09-05, 이 호스트): filemode=true + chmod 조차 diff 0 이었다.
    #    ⇒ 워크트리 대신 **인덱스**를 바꾼다(`update-index --chmod`) — 리눅스·Windows 동일.
    git -C "$d" config core.filemode true
    printf 'hello\n' > "$d/a.sh"; printf 'x\n' > "$d/b.txt"
    printf 'ignored\n' > "$d/.gitignore"; printf 'runtime\n' > "$d/ignored"
    git -C "$d" add -A >/dev/null 2>&1
    git -C "$d" commit -qm init >/dev/null 2>&1
    printf '%s' "$d"
  }

  cell() {  # cell <이름> <경로> <기대rc> <기대 첫 낱말>
    local name="$1" d="$2" want_rc="$3" want_tag="$4" out rc tag
    out="$(drift_check "$d")"; rc=$?
    tag="$(printf '%s' "$out" | head -1 | awk '{print $1}')"
    if [ "$rc" != "$want_rc" ] || [ "$tag" != "$want_tag" ]; then
      echo "  ✖ [$name] rc=$rc tag=$tag (기대 rc=$want_rc tag=$want_tag)" >&2
      printf '%s\n' "$out" | sed 's/^/      /' >&2
      fails=$((fails+1))
    else
      echo "  ✓ [$name] rc=$rc $tag"
    fi
  }

  # (1) 대조군 — 손 안 댄 체크아웃
  d="$(mk clean)"; cell "대조군(손 안 댄 체크아웃)" "$d" 0 CLEAN

  # (2) 🔴 이 스크립트가 존재하는 이유 — mode 만 바뀐 파일은 CLEAN 이어야 한다
  # 🔵 방향은 실제 호스트(100644 => 100755)와 반대(100755 => 100644)지만, 술어는
  #    «mode change 뿐인가» 만 보므로 방향에 무관하다.
  d="$(mk modeonly)"; git -C "$d" update-index --chmod=+x a.sh
  git -C "$d" diff --summary | grep -q 'mode change' \
    || { echo "  ✖ [주입확인] mode change 가 안 생겼다 — 주입 수단이 깨졌다" >&2; fails=$((fails+1)); }
  cell "mode 변경만 → CLEAN(면제)" "$d" 0 CLEAN
  drift_check "$d" | grep -q 'mode-only(면제): a.sh' \
    || { echo "  ✖ [면제 표기] 면제한 파일 이름을 안 찍는다 — 조용한 면제는 금지" >&2; fails=$((fails+1)); }

  # (3) 내용 변경 → DIRTY. 🔴 (2)와 (3)을 가르는 것이 이 술어의 전부다
  d="$(mk content)"; printf 'tampered\n' >> "$d/a.sh"
  git -C "$d" diff --numstat | grep -qv '^0	0	' \
    || { echo "  ✖ [주입확인] 내용 변경이 numstat 에 안 잡힌다" >&2; fails=$((fails+1)); }
  cell "내용 변경 → DIRTY" "$d" 1 DIRTY

  # (4) 🔴 내용 + mode 동시 — mode 면제가 내용 변경을 **가리면 안 된다**
  # 🔴🔴 순서가 중요하다 — `update-index --chmod` 은 **워크트리 내용도 인덱스에 올린다.**
  #    내용을 먼저 바꾸고 chmod 하면 내용 절반이 스테이지돼 사라지고, 이 칸이 (2)와
  #    같아진다(실측 2026-09-05: 그렇게 짰다가 CLEAN 이 나왔다). ⇒ **chmod 먼저, 내용 나중.**
  d="$(mk both)"; git -C "$d" update-index --chmod=+x a.sh; printf 'tampered\n' >> "$d/a.sh"
  # 🔴 복합 주입은 **절반마다** 단언한다. 한쪽만 확인하면 나머지가 조용히 빠진다.
  git -C "$d" diff --summary | grep -q 'mode change' \
    || { echo "  ✖ [주입확인] (4) 의 mode 절반이 안 들어갔다 — 이 칸은 (3)의 중복이 된다" >&2; fails=$((fails+1)); }
  [ -n "$(git -C "$d" diff --numstat)" ] \
    || { echo "  ✖ [주입확인] (4) 의 content 절반이 안 들어갔다 — 이 칸은 (2)의 중복이 된다" >&2; fails=$((fails+1)); }
  cell "내용+mode 동시 → DIRTY" "$d" 1 DIRTY

  # (5) 미추적 파일 → DIRTY. 🔵 .gitignore 된 것은 세지 않는다(런타임 산출물)
  d="$(mk untracked)"; printf 'new\n' > "$d/c.txt"
  cell "미추적 파일 → DIRTY" "$d" 1 DIRTY
  d="$(mk ignoredonly)"; printf 'more\n' >> "$d/ignored"
  cell "gitignore 된 변경 → CLEAN" "$d" 0 CLEAN

  # (6) 🔴 부재 판정 금지 — 저장소가 아니면 초록이 아니라 «판정 불가» 다
  mkdir -p "$tmp/notrepo"; cell "저장소 아님 → UNKNOWN" "$tmp/notrepo" 2 UNKNOWN

  if [ "$fails" -gt 0 ]; then
    echo "[host-drift] ✖ self-test 실패 ${fails}건" >&2; return 1
  fi
  echo "[host-drift] ✔ self-test 7칸 통과 — mode 면제가 내용 변경을 가리지 않고, 부재는 초록이 아니다"
  return 0
}

case "${1:-}" in
  --self-test) self_test; exit $? ;;
esac

REPO="${1:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
out="$(drift_check "$REPO")"; rc=$?
printf '%s\n' "$out" | sed 's/^/[host-drift] /'
case "$rc" in
  0) echo "[host-drift] ✔ 호스트 체크아웃은 저장소 판본이다 (TASK-MONO-615 C3)" ;;
  1) echo "[host-drift] ✖ 호스트 로컬 수정이 있습니다 — 위 목록을 보세요" >&2 ;;
  2) echo "[host-drift] ? 판정 불가 — 「변경 0건」으로 읽지 마세요" >&2 ;;
esac
exit "$rc"
