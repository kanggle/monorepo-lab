#!/usr/bin/env bash
# =============================================================================
# infra/demo/seed/seed.sh — 도메인 데이터 시드 드라이버 (TASK-MONO-506)
# =============================================================================
# demo-up.sh 가 스택을 띄운 뒤 마지막에 호출한다. 인자는 **기동된 도메인 목록**이며
# (demo-up.sh 가 resolve_deps 로 정렬한 그 목록), 각 도메인에 대응하는
# `seed-<domain>.sh` 가 있으면 실행한다.
#
# 설계 규칙
# -----------------------------------------------------------------------------
# · 시드는 **데모 기동을 막지 않는다.** 개별 도메인 시드가 실패해도 다른 도메인을
#   계속 시드하고, 마지막에 요약과 함께 비-0 으로 끝난다. 데모는 이미 떠 있고,
#   시드 실패는 "화면이 빈다" 이지 "데모가 죽었다" 가 아니다.
# · 그렇다고 **조용히 성공한 척하지도 않는다** — 실패한 도메인 이름을 마지막에
#   다시 나열한다. 이 저장소가 반복해서 당한 실패 모드가 "초록인데 화면은 빈" 것이다.
# · `DEMO_SEED=0` 으로 통째로 끌 수 있다(AMI 재굽기·디버깅용).
#
# 단독 실행:
#   DEMO_DOMAIN=local bash infra/demo/seed/seed.sh ecommerce fan
# =============================================================================
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [ "${DEMO_SEED:-1}" = "0" ]; then
  echo "[seed] DEMO_SEED=0 — 도메인 데이터 시드를 건너뜁니다"
  exit 0
fi

# demo.env 를 아직 안 읽었다면(단독 실행) 여기서 읽는다. demo-up.sh 경유면 이미 export 돼 있다.
if [ -z "${DEMO_DOMAIN:-}" ] && [ -f "$HERE/../demo.env" ]; then
  set -a; . "$HERE/../demo.env"; set +a
fi
export DEMO_DOMAIN="${DEMO_DOMAIN:-local}"

DOMAINS=("$@")
if [ "${#DOMAINS[@]}" -eq 0 ]; then
  echo "usage: seed.sh <domain...>" >&2; exit 2
fi

echo "[seed] 대상 도메인: ${DOMAINS[*]}  (DEMO_DOMAIN=$DEMO_DOMAIN)"

failed=()
skipped=()
for d in "${DOMAINS[@]}"; do
  script="$HERE/seed-$d.sh"
  if [ ! -f "$script" ]; then
    # 시드 스크립트가 없는 도메인은 "시드할 것이 없다" 이다(iam 은 Flyway 가 전부 심는다,
    # console 은 자기 데이터가 없다). 그래도 **목록에 남긴다** — 침묵은 커버리지 착시다.
    skipped+=("$d"); continue
  fi
  echo "[seed] --- $d ---"
  bash "$script" || failed+=("$d")
done

[ "${#skipped[@]}" -eq 0 ] || echo "[seed] 시드 스크립트 없음(시드 대상 아님): ${skipped[*]}"

if [ "${#failed[@]}" -gt 0 ]; then
  echo "[seed] ✗ 실패한 도메인: ${failed[*]} — 해당 화면은 비어 있을 수 있습니다" >&2
  exit 1
fi
echo "[seed] 모든 도메인 시드 완료"
