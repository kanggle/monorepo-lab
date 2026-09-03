#!/usr/bin/env bash
# =============================================================================
# infra/demo/check-suppressed-containers.sh — 억제된 서비스가 **실제로 안 돌고 있는가**
# =============================================================================
# TASK-MONO-617.
#
# 무엇을 재는가
# -----------------------------------------------------------------------------
# `ADR-MONO-067` 로 Vercel 에 옮겨간 표면은 데모 체인의 오버라이드가 `profiles:` 를
# 「추가」해서 **렌더에서 빠진다**(예: infra/demo/ecommerce-vercel.override.yml).
# 가드 (z19)는 그 **렌더**를 본다. 이 스크립트는 그 다음 축 — **런타임** — 을 본다.
#
# 왜 렌더로는 안 되는가 (대리지표 금지)
# -----------------------------------------------------------------------------
# 2026-09-02 `TASK-MONO-610` 기동 창 #1 에서 실측된 사슬:
#   1. `profiles:` 로 가려진 서비스는 compose 에게 **「고아」가 아니라 「비활성」**이다
#      ⇒ `docker compose down --remove-orphans` 가 그 컨테이너를 **안 지운다.**
#   2. `restart=unless-stopped` 때문에 EC2 부팅에서 **되살아난다.**
#   3. 되살아난 컨테이너는 **옛 도메인 라벨**을 들고 있어 새 도메인에서 404 를 낸다 —
#      그 404 는 「억제 완료」의 404 와 **모양이 같고 기전이 다르다.**
#   4. 그동안 (z19)는 **계속 초록**이다. 렌더에는 없기 때문이다.
# ⇒ 렌더·HTTP 상태코드·`docker compose config` 는 전부 이 상태를 통과시킨다.
#    어긋난 것은 **컨테이너의 존재**뿐이므로 그것을 직접 읽는다.
#
# 🔵 선례: `check-label-drift.sh`(TASK-MONO-553)가 같은 모양이다 — 「초록인데 실제로는
#    틀린 것」을 렌더/`ps` 요약으로는 못 보므로 `docker inspect` 출력을 직접 읽는다.
#    배선도 같다: demo-up.sh 가 `post_up_call` 로 돌리고, verify-demo-wrapper.sh 의
#    정적 칸이 「그 호출이 존재하는가」를 지킨다.
#
# 모집단을 **선언에서 유도한다** (서비스 이름 하드코딩 금지)
# -----------------------------------------------------------------------------
# 억제 대상을 목록으로 적으면 `ADR-MONO-067` 단계 3·4(console·fan)에서 같은 결함이
# **조용히** 재발한다. 그래서 체인에서 유도한다:
#
#   억제된 서비스 = 렌더(체인 − infra/demo/*.override.yml) − 렌더(체인)
#
# 🔵 이 유도의 전제 — *"데모 오버라이드는 서비스를 «추가» 하지 않는다"* — 는 가정이 아니라
#    실측이다(2026-09-03, 8개 프로젝트 전수: 추가 0건, ecommerce 만 33↔34 로 web-store).
#    그래서 차이의 **방향**이 모호하지 않다. 반대 방향이 생기면 (아래 자체검사가 말한다).
#
# 판정 = 컨테이너의 **존재**이지 실행이 아니다
# -----------------------------------------------------------------------------
# 🔴 `docker ps` 만 보면 놓친다 — 되살아나기를 기다리는 컨테이너는 `Exited` 로도 존재하고,
#    다음 부팅에서 `unless-stopped` 가 그것을 다시 세운다. `-a` 로 **존재**를 본다.
#    (`TASK-MONO-615` C1 이 `docker ps -a --filter name=web-store` = 0건으로 판정한 그 축)
#
# 🔴 공허 통과 방지 — **양성 대조군을 판정 안에 넣는다**
# -----------------------------------------------------------------------------
# "억제 대상 컨테이너 0건" 은 두 가지를 뜻할 수 있다: 정말 없거나, **docker 가 그 프로젝트를
# 못 보거나**(데몬 없음·권한 없음·다른 컨텍스트). 그래서 각 프로젝트마다 **그 프로젝트의
# 컨테이너가 ≥1 존재**하는지 먼저 본다. 0이면 그 프로젝트는 PASS 가 아니라 **판정 불가**다.
#
# 사용
# -----------------------------------------------------------------------------
#   bash infra/demo/check-suppressed-containers.sh ecommerce fan …   # 슬러그 목록
#   bash infra/demo/check-suppressed-containers.sh                    # 전 슬러그
# 종료코드: 0 = 위반 없음 · 1 = 억제 대상이 존재함 · 2 = 판정 불가(사유 출력)
#
# 🔵 bite: 억제 대상 서비스의 라벨을 단 컨테이너를 하나 만들면 rc=1 이어야 한다.
#   docker run -d --name bite-617 \
#     --label com.docker.compose.project=ecommerce \
#     --label com.docker.compose.service=web-store alpine sleep 300
# =============================================================================
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
# shellcheck source=/dev/null
. "$HERE/projects.sh"

say()  { echo "[suppressed] $*"; }
warn() { echo "[suppressed] $*" >&2; }

# 🔵 `--derive` — 모집단 유도만 하고 `<slug><TAB><service>` 를 찍은 뒤 끝낸다.
#    데몬이 없어도 돈다(`docker compose config` 는 클라이언트 측이다). 정적 가드 (z27)이
#    이것을 부른다 — 유도 로직을 가드에 **복제하지 않기 위해서**다. 한 사실이 두 곳에
#    있으면 한쪽만 고쳐진다.
DERIVE=0
SLUGS=()
for a in "$@"; do
  case "$a" in
    --derive) DERIVE=1 ;;
    -*) warn "알 수 없는 옵션: $a"; exit 2 ;;
    *) SLUGS+=("$a") ;;
  esac
done
if [ "${#SLUGS[@]}" -eq 0 ]; then
  mapfile -t SLUGS < <(printf '%s\n' "${!COMPOSE[@]}" | sort)
fi

# --- 0. docker 가 판정 가능한 상태인가 -------------------------------------
if ! command -v docker >/dev/null 2>&1; then
  warn "⏭ docker 명령이 없습니다 — 판정 불가(이 호스트는 데모 호스트가 아닙니다)"
  exit 2
fi
# 🔴 데몬은 **판정**에만 필요하다. 유도는 클라이언트 측이므로 --derive 는 통과시킨다 —
#    여기서 같이 막으면 CI(데몬은 있으나 데모 스택이 없는 곳)에서 (z27)이 공허해진다.
if [ "$DERIVE" = 0 ] && ! docker info >/dev/null 2>&1; then
  warn "⏭ docker 데몬에 붙지 못했습니다 — 판정 불가"
  warn "   🔴 이것을 «억제됨» 으로 읽지 마세요. 0건과 «못 봤다» 는 다릅니다."
  exit 2
fi

render() {  # $@ = -f 인자들 → 서비스 이름 정렬 목록
  (cd "$ROOT" && docker compose --env-file "$HERE/demo.env" "$@" config --services 2>/dev/null) | sort
}

rc=0
checked=0; skipped=0; suppressed_total=0
declare -a VIOLATIONS=()

for slug in "${SLUGS[@]}"; do
  [ -n "${COMPOSE[$slug]+x}" ] || { warn "알 수 없는 도메인: $slug"; rc=2; continue; }

  # --- 1. 모집단: 체인에서 유도한다 -----------------------------------------
  full=(); bare=(); has_override=0
  for f in ${COMPOSE[$slug]}; do
    full+=(-f "$ROOT/$f")
    case "$f" in
      infra/demo/*.override.yml) has_override=1 ;;
      *) bare+=(-f "$ROOT/$f") ;;
    esac
  done
  [ "$has_override" = 1 ] || continue      # 억제 선언이 있을 수 없다

  with="$(render "${full[@]}")"
  without="$(render "${bare[@]}")"

  n_with=$(printf '%s\n' "$with" | grep -c . || true)
  n_without=$(printf '%s\n' "$without" | grep -c . || true)

  # 🔴 바닥 — 렌더가 깨지면 목록이 통째로 비고, 그 0행은 «억제됨» 과 구별되지 않는다.
  if [ "$n_with" -lt 1 ] || [ "$n_without" -lt 1 ]; then
    warn "[$slug] ⏭ 렌더가 비었습니다 (체인=$n_with, 오버라이드뺀판=$n_without) — 판정 불가"
    warn "        🔴 «억제됨» 이 아니라 «\`docker compose config\` 가 실패했다» 입니다."
    rc=2; skipped=$((skipped+1)); continue
  fi

  mapfile -t SUPP < <(comm -13 <(printf '%s\n' "$with") <(printf '%s\n' "$without") | grep . || true)
  mapfile -t ADDED < <(comm -23 <(printf '%s\n' "$with") <(printf '%s\n' "$without") | grep . || true)

  # 🔵 유도의 전제 자체검사 — 오버라이드가 서비스를 «추가» 하면 차이의 방향이 모호해진다.
  if [ "${#ADDED[@]}" -gt 0 ]; then
    warn "[$slug] ⚠ 데모 오버라이드가 서비스를 **추가**합니다: ${ADDED[*]}"
    warn "        이 스크립트의 모집단 유도는 «오버라이드는 서비스를 추가하지 않는다»"
    warn "        (2026-09-03 8개 전수 실측)를 전제로 합니다. 전제가 바뀌었으니 유도를"
    warn "        다시 설계하세요 — 지금 판정은 그대로 진행하되 이 줄을 무시하지 마세요."
  fi

  [ "${#SUPP[@]}" -gt 0 ] || continue      # 이 프로젝트는 억제하는 것이 없다
  suppressed_total=$((suppressed_total + ${#SUPP[@]}))

  if [ "$DERIVE" = 1 ]; then
    for svc in "${SUPP[@]}"; do printf '%s\t%s\n' "$slug" "$svc"; done
    continue
  fi

  # --- 2. 양성 대조군: 이 프로젝트가 docker 에 보이는가 ----------------------
  live_n=$(docker ps -a --filter "label=com.docker.compose.project=$slug" -q 2>/dev/null | grep -c . || true)
  if [ "$live_n" -lt 1 ]; then
    warn "[$slug] ⏭ 이 프로젝트의 컨테이너가 **0개** 입니다 — 억제 판정 불가"
    warn "        억제 대상(${SUPP[*]})이 «없다» 와 «docker 가 이 프로젝트를 못 본다» 를"
    warn "        구별할 수 없습니다. 스택이 안 떴거나 프로젝트 이름이 다릅니다."
    rc=2; skipped=$((skipped+1)); continue
  fi

  # --- 3. 판정: 억제 대상의 **존재** ---------------------------------------
  for svc in "${SUPP[@]}"; do
    checked=$((checked+1))
    found="$(docker ps -a \
      --filter "label=com.docker.compose.project=$slug" \
      --filter "label=com.docker.compose.service=$svc" \
      --format '{{.Names}}|{{.State}}|{{.CreatedAt}}' 2>/dev/null)"
    if [ -n "$found" ]; then
      VIOLATIONS+=("$slug/$svc")
      warn "[$slug] ✖ 억제된 서비스 '$svc' 의 컨테이너가 **존재합니다** (대조군: 이 프로젝트 컨테이너 ${live_n}개)"
      while IFS='|' read -r nm st cr; do
        [ -n "$nm" ] && warn "        · $nm  state=$st  created=$cr"
      done <<< "$found"
      rc=1
    fi
  done
done

# --- 4. 보고 ---------------------------------------------------------------
if [ "${#VIOLATIONS[@]}" -gt 0 ]; then
  warn ""
  warn "🔴 억제 선언은 렌더에서만 효력이 있습니다. \`profiles:\` 로 가려진 서비스는 compose 에게"
  warn "   «고아» 가 아니라 «비활성» 이라 \`down --remove-orphans\` 가 지우지 않고,"
  warn "   \`restart=unless-stopped\` 가 다음 부팅에서 되살립니다 (TASK-MONO-610 창 #1 실측)."
  warn "🔴 그 컨테이너는 **옛 도메인 라벨**을 들고 있어 새 도메인에서 404 를 냅니다 —"
  warn "   그 404 는 «억제 완료» 의 404 와 모양이 같고 기전이 다릅니다."
  warn "→ 고치는 법: 해당 컨테이너를 명시적으로 제거하거나(\`docker rm -f\`), 억제가 들어간"
  warn "   AMI 로 재굽기하세요. 억제 선언(infra/demo/*.override.yml)을 지우는 것은 답이 아닙니다."
  exit 1
fi

if [ "$DERIVE" = 1 ]; then
  [ "$rc" = 2 ] && exit 2
  exit 0
fi

if [ "$rc" = 2 ]; then
  say "⏭ 판정 불가 — 위 사유 참조 (건너뛴 프로젝트 ${skipped}개)"
  exit 2
fi

say "OK — 억제 대상 ${suppressed_total}개(검사 ${checked}건) 모두 컨테이너가 존재하지 않습니다."
say "     (모집단은 체인에서 유도했습니다. 서비스 이름을 하드코딩하지 않습니다.)"
exit 0
