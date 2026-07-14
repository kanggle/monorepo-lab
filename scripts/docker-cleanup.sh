#!/usr/bin/env bash
# Docker 디스크 청소 — 안 쓰는 컨테이너/이미지/빌드캐시 회수 + 무제한 로그 감시.
#
# 이 호스트(Windows + Rancher Desktop WSL2)는 vhdx 가 한 번 커지면 prune 만으론
# C: 로 안 돌아온다(=프로젝트 메모리 env_rancher_desktop_vhdx_no_shrink). 이 스크립트는
# "VM 내부" 를 비워 vhdx 증식을 억제한다. 실제 C: 회수는 compact-rd-vhdx.ps1(관리자) 별건.
#
# 안전: 모두 "안 쓰는 것만" 지운다(실행 중 컨테이너·그 이미지·named 볼륨은 보호).
#   - container prune : 멈춘 컨테이너(Testcontainers 고아 등)
#   - image prune     : dangling(<none>) 이미지 — 재빌드로 태그 떨어진 옛 이미지
#   - builder prune   : 빌드 캐시 — **나이**로 자른다(1주 넘게 안 쓴 것만)
#   - 로그 감시       : 상한 없는 컨테이너를 보고만 한다(기본 비파괴; 지우려면 --logs)
#
# ─── 두 함정 (TASK-MONO-391 — 둘 다 실제로 물렸다) ─────────────────────────────
# 1) `docker system df` 의 RECLAIMABLE 열을 "회수 가능량" 으로 읽지 말 것.
#    **빌드캐시 행에서 그 열은 dangling 부분만 센다.** 2026-07-13 실측:
#      Build Cache  TOTAL 101  ACTIVE 0  SIZE 5.012GB  RECLAIMABLE 387.4MB
#    ACTIVE=0 이니 5.012GB 전부가 unused 인데 RECLAIMABLE 은 387MB 라고 말한다.
#    `-a` 없는 `builder prune` 은 그 387MB 만 지우고 끝난다 → 매주 성공(exit 0)하면서
#    4.6GB 를 남겼다. **이게 주간 청소가 초록인 채로 디스크를 못 지킨 이유다.**
#
#    ⚠️ 그리고 `--max-used-space`(크기 상한)로 고치려던 첫 시도는 **실측으로 반증됐다**:
#       캐시 502.6MB / 상한 300MB → **0B 축출**. `-a` 를 붙여도, `--reserved-space 0`
#       을 붙여도 0B(3회 시도 전부). 이유 = **buildkit GC 가 최근 사용된 레코드를
#       나이로 보호한다**(남은 레코드는 전부 `Last used: 6분 전`이었다).
#       ⇒ 이 호스트에서 **크기 상한은 캐시를 묶지 못한다.** 나이 필터만 문다:
#         `--filter until=1h`  (6분 된 캐시) → 0B      ← 음성 대조
#         `--filter until=1m`  (같은 레코드) → 502.6MB ← 양성 대조
#       그래서 아래는 `-a --filter until=${CACHE_MAX_AGE}` 를 쓴다. 주간 실행 + 1주
#       필터 = **오래된 캐시는 매주 사라지고 뜨거운 한 주치는 살아남는다.**
#
# 2) 컨테이너 JSON 로그는 `docker system df` 에 **아예 안 나온다**.
#    `/var/lib/docker/containers/*/*-json.log` — 2026-07-10 에 17.1GB, 07-13 에 2.9GB 가
#    이 사각지대에서 자랐다. 게다가 **`HostConfig.LogConfig` 는 컨테이너 생성 시점에
#    고정**되므로, compose 에 `logging:` 을 추가해도 **이미 있는 컨테이너엔 소급되지
#    않는다**(로테이션은 재생성 때만 붙는다: `up -d --force-recreate`).
#    ⇒ 아래 감시는 **크기가 아니라 "상한의 부재"** 로 판정한다. 크기로 판정하면 상한
#      없는 컨테이너가 작을 때는 안 보이고 터진 뒤에야 보인다(= 감시가 아니라 사후 통보).
#
# 3) 멈춘 컨테이너는 **매주 지워진다** — 그러니 데모 복구는 `docker start` 가 아니다.
#    아래 `container prune -f` 는 멈춘 컨테이너를 전부 지운다(데모 인프라 포함, 볼륨 보존).
#    ⚠️ "갓 멈춘 건 지키자" 며 `--filter until=24h` 를 거는 안은 **반증됐다**:
#       **`until` 은 정지 시각이 아니라 *생성* 시각 기준이다**(2026-07-14 실측 — 라벨로
#       격리한 probe: 생성 3분38초 전 / 정지 0초 전 → `until=2m` 에 **제거됨**).
#       ⇒ 13일 전 생성된 데모 컨테이너는 `until=24h` 로도 **그대로 지워지고**, 정작
#         보호되는 건 **갓 생성된 Testcontainers 고아**다. 보호가 정확히 뒤집혀 있다.
#       (`docker ps` 는 `until` 필터를 아예 안 받는다 — 미리보기가 안 되니 실측만이 답.)
#    ⇒ 필터를 걸지 않는다. 대신 **지우기 전에 소리내어 말한다**(아래 출력).
#      그리고 이건 양보가 아니다 — **재생성만이 로테이션을 붙인다**(함정 2). `docker start`
#      로 되살린 옛 컨테이너는 상한 없는 로그를 영원히 계속 쓴다. prune → `compose up -d`
#      경로가 함정 2 의 해법과 같은 방향이다.
#
# 사용:
#   ./scripts/docker-cleanup.sh            # 기본(컨테이너+dangling이미지+캐시나이컷+로그감시)
#   ./scripts/docker-cleanup.sh --images   # + 안 쓰는 "태그된" 이미지까지(-a, 더 공격적)
#   ./scripts/docker-cleanup.sh --logs     # + 상한 없는 컨테이너의 JSON 로그 truncate
#   ./scripts/docker-cleanup.sh --dry-run  # 아무것도 안 지우고 현황만 표시
set -euo pipefail

# 임계는 전부 env 로 덮을 수 있다 — 그래야 **경고 분기를 실제로 물려서** 시험할 수 있다.
# (평시엔 임계 미달이라 경고 코드가 한 번도 안 돌고, 그러면 "경고가 비-0 으로 죽지 않는다"
#  는 성질이 검증되지 않은 채 남는다. 안 돌아본 분기는 안 되는 분기다.)
CACHE_MAX_AGE="${CACHE_MAX_AGE:-168h}"    # 빌드캐시: 이 기간 넘게 안 쓴 것만 제거(=1주)
WARN_CACHE_GB="${WARN_CACHE_GB:-5}"       # 나이로 자른 뒤에도 이만큼 크면 경고(지우지는 않음)
WARN_TOTAL_MB="${WARN_TOTAL_MB:-1024}"    # 로그 총량 경고 임계
WARN_SINGLE_MB="${WARN_SINGLE_MB:-500}"   # 개별 로그 경고 임계

AGGRESSIVE=0; DRY=0; LOGS=0
for a in "$@"; do
  case "$a" in
    --images) AGGRESSIVE=1 ;;
    --logs) LOGS=1 ;;
    --dry-run) DRY=1 ;;
    -h|--help) grep '^#' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
  esac
done

# 도커가 죽어 있으면 여기서 명확히 죽는다. 예약작업 RED 는 정당하다(청소를 못 했으니).
# 다만 "스크립트 버그" 로 오진하지 않도록 원인을 말해준다.
if ! docker version >/dev/null 2>&1; then
  echo "!! 도커 데몬에 연결할 수 없습니다 — 청소를 수행하지 못했습니다." >&2
  echo "   (Rancher Desktop 미기동 / WSL 백엔드 다운. 스크립트 결함이 아닙니다.)" >&2
  echo "   복구: rdctl shutdown → wsl --shutdown → Rancher Desktop 재기동(비관리자)." >&2
  exit 1
fi

# ── 로그 감시 헬퍼 ───────────────────────────────────────────────────────────
# 상한이 **없는** 컨테이너 이름 목록. LogConfig 는 호스트에서 docker inspect 로 읽을 수
# 있으므로 wsl 을 쓰지 않는다(Git Bash → wsl 인용 함정 회피).
unbounded_containers() {
  docker ps -a --format '{{.Names}}' 2>/dev/null | while IFS= read -r n; do
    [ -n "$n" ] || continue
    ms="$(docker inspect "$n" --format '{{index .HostConfig.LogConfig.Config "max-size"}}' 2>/dev/null || true)"
    case "$ms" in
      ''|'<no value>') printf '%s\n' "$n" ;;
    esac
  done
}

# VM 안의 컨테이너 로그 총량(MB). `docker system df` 가 이 값을 보고하지 않으므로
# 이 줄이 유일한 가시성이다. Rancher/WSL 이 아니면 조용히 건너뛴다.
log_total_mb() {
  command -v wsl >/dev/null 2>&1 || { echo ""; return 0; }
  MSYS_NO_PATHCONV=1 wsl -d rancher-desktop sh -s 2>/dev/null <<'EOSH' || echo ""
du -sm /var/lib/docker/containers 2>/dev/null | cut -f1
EOSH
}

# 개별 로그 상위 5개 ("MB  컨테이너ID")
log_top() {
  command -v wsl >/dev/null 2>&1 || return 0
  MSYS_NO_PATHCONV=1 wsl -d rancher-desktop sh -s 2>/dev/null <<'EOSH' || true
du -m /var/lib/docker/containers/*/*-json.log 2>/dev/null | sort -rn | head -5 | while read -r mb path; do
  dir=${path%/*}
  echo "$mb ${dir##*/}"
done
EOSH
}

report_logs() {
  echo "=== 컨테이너 JSON 로그 감시 (docker system df 가 못 보는 영역) ==="
  total="$(log_total_mb | tr -d '[:space:]')"
  if [ -n "$total" ]; then
    echo "로그 총량: ${total}MB  (/var/lib/docker/containers)"
  else
    echo "로그 총량: (측정 불가 — Rancher/WSL 환경이 아님)"
    total=0
  fi

  unbounded="$(unbounded_containers || true)"
  ucount=0
  [ -n "$unbounded" ] && ucount="$(printf '%s\n' "$unbounded" | grep -c . || true)"
  echo "--- 상한 없는 컨테이너: ${ucount}개 (LogConfig 에 max-size 없음 = 무제한 증가 가능) ---"
  if [ "$ucount" -eq 0 ]; then
    echo "  없음 (전부 로테이션 적용됨)"
  else
    # 전수 나열은 노이즈다(58개면 아무도 안 읽는다 = 꺼진 가드와 같다). 상위 10개만.
    printf '%s\n' "$unbounded" | head -10 | sed 's/^/  [!] /'
    if [ "$ucount" -gt 10 ]; then
      echo "  ... 외 $((ucount - 10))개 (전체 목록: docker ps -a 와 대조)"
    fi
    echo ""
    echo "  ^ 이 컨테이너들은 compose 에 logging: 이 있어도 **소급되지 않는다**"
    echo "    (LogConfig 는 생성 시점 고정). 로테이션을 붙이려면 재생성해야 한다:"
    echo "      docker compose -p <project> -f <files> up -d --force-recreate <svc>"
  fi

  top="$(log_top || true)"
  if [ -n "$top" ]; then
    echo "--- 큰 로그 상위 5 ---"
    printf '%s\n' "$top" | while read -r mb id; do
      [ -n "${id:-}" ] || continue
      name="$(docker inspect "$id" --format '{{.Name}}' 2>/dev/null | sed 's|^/||' || true)"
      flag=""
      if [ "${mb:-0}" -ge "$WARN_SINGLE_MB" ] 2>/dev/null; then flag=" [!]"; fi
      printf '  %6sMB  %s%s\n' "$mb" "${name:-$id}" "$flag"
    done
  fi

  # 경고는 **출력**이지 종료코드가 아니다. 예약작업이 매주 RED 면 사람이 무시하고,
  # 무시되는 가드는 꺼진 가드다(TASK-MONO-360).
  if [ "${total:-0}" -ge "$WARN_TOTAL_MB" ] 2>/dev/null; then
    echo ""
    echo "[!] 로그 총량이 ${WARN_TOTAL_MB}MB 를 넘었습니다. 위 '상한 없는 컨테이너' 를 재생성하거나,"
    echo "    즉시 회수가 필요하면 --logs 로 truncate 하세요(관측성 손실 — 원인 조사 후에)."
  fi
  echo ""
}

echo "=== 청소 전 ==="
docker system df
echo ""
report_logs

if [ "$DRY" = "1" ]; then
  echo "[dry-run] 아무것도 지우지 않았습니다."
  echo "  주의: 위 RECLAIMABLE 열은 **빌드캐시 행에서 dangling 만** 셉니다 —"
  echo "        실제 회수 가능량은 그보다 큽니다(${CACHE_MAX_AGE} 넘게 안 쓴 캐시가 제거 대상)."
  exit 0
fi

echo "=== 멈춘 컨테이너 정리 ==="
# 지우기 전에 **무엇을 지우는지 말한다.** 이 스크립트는 매주 무인으로 도는데, 여기서
# 침묵하면 데모 복구 경로가 조용히 사라진다(= 함정 3 이 실제로 물렸던 방식).
stopped="$(docker ps -a --filter status=exited -q | grep -c . || true)"
if [ "${stopped:-0}" -gt 0 ]; then
  echo "  멈춘 컨테이너 ${stopped}개를 삭제합니다 (named 볼륨은 보존 → 데이터 손실 없음)."
  echo "  ⚠️ 데모의 멈춘 컨테이너도 포함됩니다. 복구는 'docker start' 가 아니라 **재생성**입니다:"
  echo "       ./scripts/fed-e2e-up.sh            # 또는 docker compose -p <proj> -f <files> up -d"
  echo "     재생성이라야 로그 로테이션이 붙습니다(LogConfig 는 생성 시점 고정 — 함정 2)."
fi
docker container prune -f
echo "=== dangling 이미지 정리 ==="
docker image prune -f

echo "=== 빌드 캐시: ${CACHE_MAX_AGE} 넘게 안 쓴 것 제거 ==="
# -a 는 dangling 뿐 아니라 unused 전체를 대상에 넣고, until 필터가 "최근 것은 남긴다".
# 크기 상한(--max-used-space)이 아니라 **나이**로 자르는 이유는 § 함정 1 참조 — 크기
# 상한은 이 호스트에서 실제로 0B 를 축출한다(buildkit 이 최근 사용 레코드를 보호).
docker builder prune -f -a --filter "until=${CACHE_MAX_AGE}"

# 나이로 자른 뒤에도 여전히 크면 그건 "한 주치 빌드가 실제로 크다" 는 뜻이다.
# 지우지 않고 **말한다**(경고는 출력이지 종료코드가 아니다).
cache_mb="$(docker system df --format '{{.Type}}\t{{.Size}}' 2>/dev/null \
  | awk -F'\t' '/Build Cache/{print $2}' | sed 's/[^0-9.]//g' | cut -d. -f1 || true)"
cache_unit="$(docker system df --format '{{.Type}}\t{{.Size}}' 2>/dev/null \
  | awk -F'\t' '/Build Cache/{print $2}' | sed 's/[0-9.]//g' || true)"
if [ "${cache_unit:-}" = "GB" ] && [ "${cache_mb:-0}" -ge "$WARN_CACHE_GB" ] 2>/dev/null; then
  echo "[!] 빌드 캐시가 ${cache_mb}GB 입니다 (${CACHE_MAX_AGE} 이내인데도)."
  echo "    한 주치 빌드가 그만큼 크다는 뜻입니다. 즉시 비우려면: docker builder prune -af"
fi

if [ "$AGGRESSIVE" = "1" ]; then
  echo "=== 안 쓰는 태그 이미지까지 정리 (-a) ==="
  docker image prune -af
fi

if [ "$LOGS" = "1" ]; then
  echo "=== 상한 없는 컨테이너의 JSON 로그 truncate (--logs 명시) ==="
  # 명시 요청일 때만. 로그 폭증은 **증상**이고 진짜 원인은 그 로그 안에 있다.
  targets="$(unbounded_containers || true)"
  if [ -z "$targets" ]; then
    echo "  대상 없음."
  else
    n=0
    while IFS= read -r name; do
      [ -n "$name" ] || continue
      lp="$(docker inspect "$name" --format '{{.LogPath}}' 2>/dev/null || true)"
      [ -n "$lp" ] || continue
      MSYS_NO_PATHCONV=1 wsl -d rancher-desktop sh -s "$lp" 2>/dev/null <<'EOSH' || true
[ -f "$1" ] && truncate -s 0 "$1"
EOSH
      n=$((n + 1))
      echo "  truncated: $name"
    done <<EOF
$targets
EOF
    echo "  총 ${n}건."
  fi
fi

echo ""
echo "=== 청소 후 ==="
docker system df
echo ""
report_logs
echo ">>> VM 내부를 비웠습니다. C: 실제 회수는 관리자에서 compact-rd-vhdx.ps1 필요"
echo "    (이유: WSL2 vhdx 는 prune 후에도 호스트에서 안 줄어듦)."
