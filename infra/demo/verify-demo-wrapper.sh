#!/usr/bin/env bash
# =============================================================================
# infra/demo/verify-demo-wrapper.sh — 통합 데모 래퍼 회귀 방어 (TASK-MONO-341/344/346)
# =============================================================================
# 래퍼(demo-up.sh)의 정당성이 의존하는 불변식을 검증한다. 하나라도 무너지면
# 데모가 부팅되지 않거나 일부 도메인이 "소리없이" 사라진다.
#
#   (a) 모든 compose 조합이 렌더된다              docker compose config -q
#   (b) container_name 이 전역에서 유일하다        (docker 는 중복 container_name 거부)
#   (c) host ports 가 전역에서 충돌하지 않는다
#   (d) 커버리지 드리프트 — 디스크의 모든 projects/*/docker-compose.yml 이 맵에 있다
#   (e) **앱 서비스 ≥1** — 각 프로젝트가 build: 를 가진 서비스를 최소 1개 기여한다
#       (TASK-MONO-342: iam/wms 는 base 만 주면 DB 만 뜨고 앱이 0개였다. iam 은
#        OIDC IdP 라 그 경우 전 도메인의 토큰 검증이 무너진다.)
#   (g) **미설정 compose 변수 0건** — 렌더가 "variable is not set" 경고를 내면 FAIL
#       (TASK-MONO-346: 미설정 변수는 error 가 아니라 warning 이라 (a)가 통과시킨다.
#        ecommerce 의 bare ${VAR} 14개는 gitignored .env 에서 오므로 fresh clone
#        (데모 AMI · CI 러너)에서 전부 빈 문자열이 됐고, 그 중 9개가
#        POSTGRES_PASSWORD 라 postgres 가 초기화를 거부해 DB 9개 + 앱 12개가 죽었다.)
#   (h) **참조 이미지가 레지스트리에 실재한다** — compose 가 가리키는 image: 가 사라지면
#       기동이 즉사한다. (TASK-MONO-353: bitnami/kafka:3.7 이 Docker Hub 에서 삭제되어
#        scm/erp/fan 의 compose 가 전부 깨졌다. 우리 커밋과 무관하게 외부에서 깨지므로
#        어떤 diff-기반 검사로도 안 잡히고, 그 3개 compose 를 실행하는 CI 잡이 하나도
#        없어(E2E 는 Testcontainers 기반) 오래 방치됐다.)
#   (f) --live: 서로 다른 프로젝트의 같은 서비스 키(redis)가 별도 -p 로 공존 healthy
#
# jq 는 쓰지 않는다(러너 외 환경 호환) — `docker compose config` YAML 을 grep/awk 로 판다.
#
# 사용법:
#   bash infra/demo/verify-demo-wrapper.sh                     # 정적 (a)~(e),(g),(h)
#   bash infra/demo/verify-demo-wrapper.sh --live              # + (f) 실기동 증명
#   bash infra/demo/verify-demo-wrapper.sh --require-coverage  # + (h) 무신호 실행을 FAIL
#
# --require-coverage (TASK-MONO-359): 가드 (h) 가 **한 건도 확인하지 못한 실행**을
# 합격으로 처리하지 않는다. 레지스트리 레이트리밋에 걸리면 (h) 는 전 이미지를 skip 하고
# 그대로 통과하는데(아래 설계 근거 참조), 그건 PR 에서는 옳지만 **스케줄 실행에서는
# "무신호"를 "이상 없음"으로 보고하는 것**이다. 시계가 달린 잡(nightly)만 이 플래그를
# 켜서 커버리지 0 을 FAIL 로 만든다. PR 잡은 켜지 않는다 — 외부 레이트리밋이 머지를
# 막아서는 안 된다.
# =============================================================================
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
# shellcheck source=infra/demo/projects.sh
source "$HERE/projects.sh"
# shellcheck source=infra/demo/demo.env
set -a; source "$HERE/demo.env"; set +a

LIVE=0
REQUIRE_COVERAGE=0
for arg in "$@"; do
  case "$arg" in
    --live)             LIVE=1 ;;
    --require-coverage) REQUIRE_COVERAGE=1 ;;
    *) echo "알 수 없는 플래그: $arg (사용법은 파일 상단 참조)" >&2; exit 2 ;;
  esac
done

fail() { echo "  FAIL: $*" >&2; exit 1; }
ok()   { echo "  ok: $*"; }

render() { # $1=slug ('traefik' 특수) → 렌더된 YAML 을 stdout 으로
  if [ "$1" = "traefik" ]; then
    docker compose -p verify-traefik -f "$ROOT/$TRAEFIK_COMPOSE" config 2>/dev/null
  else
    local ARGS; mapfile -t ARGS < <(compose_args "$1")
    docker compose -p "verify-$1" "${ARGS[@]}" config 2>/dev/null
  fi
}

# ---------------------------------------------------------------------------
echo "[verify] (a) compose 렌더 — traefik + ${#COMPOSE[@]} projects"
# ---------------------------------------------------------------------------
docker compose -p verify-traefik -f "$ROOT/$TRAEFIK_COMPOSE" config -q 2>/dev/null \
  || fail "traefik compose 렌더 실패: $TRAEFIK_COMPOSE"
ok "traefik"
for p in "${!COMPOSE[@]}"; do
  mapfile -t ARGS < <(compose_args "$p")
  docker compose -p "verify-$p" "${ARGS[@]}" config -q 2>/dev/null \
    || fail "$p compose 렌더 실패: ${COMPOSE[$p]}"
  ok "$p"
done

# ---------------------------------------------------------------------------
echo "[verify] (b) container_name 전역 유일성"
# ---------------------------------------------------------------------------
names_file="$(mktemp)"; ports_file="$(mktemp)"
trap 'rm -f "$names_file" "$ports_file"' EXIT
{
  render traefik
  for p in "${!COMPOSE[@]}"; do render "$p"; done
} | awk '/^[[:space:]]*container_name:[[:space:]]*/ { print $2 }' | sort > "$names_file"

dupe_names="$(uniq -d < "$names_file")"
[ -z "$dupe_names" ] || fail "중복 container_name (docker 가 거부함):"$'\n'"$dupe_names"
ok "$(wc -l < "$names_file" | tr -d ' ') 개 container_name 전부 유일"

# ---------------------------------------------------------------------------
echo "[verify] (c) host port 전역 무충돌"
# ---------------------------------------------------------------------------
{
  render traefik
  for p in "${!COMPOSE[@]}"; do render "$p"; done
} | awk '/^[[:space:]]*published:[[:space:]]*/ { gsub(/"/,"",$2); if ($2 != "") print $2 }' \
  | sort > "$ports_file"

dupe_ports="$(uniq -d < "$ports_file")"
[ -z "$dupe_ports" ] || fail "중복 host port:"$'\n'"$dupe_ports"
ok "published host ports: $(tr '\n' ' ' < "$ports_file")— 충돌 없음"

# ---------------------------------------------------------------------------
echo "[verify] (d) 커버리지 드리프트 — 모든 projects/*/docker-compose.yml 이 맵에 등록"
# ---------------------------------------------------------------------------
missing=""
for f in "$ROOT"/projects/*/docker-compose.yml; do
  [ -e "$f" ] || continue
  rel="${f#"$ROOT"/}"
  found=0
  for p in "${!COMPOSE[@]}"; do
    case " $(compose_files "$p" | tr '\n' ' ') " in *" $rel "*) found=1; break;; esac
  done
  [ "$found" -eq 1 ] || missing="$missing$rel"$'\n'
done
[ -z "$missing" ] || fail "래퍼 맵(infra/demo/projects.sh)에 미등록된 프로젝트 compose:"$'\n'"$missing"\
  $'\n'"→ 데모에서 조용히 누락됩니다. COMPOSE + FULL/DOWN_ORDER 를 갱신하세요."
ok "${#COMPOSE[@]} 개 프로젝트 전부 맵에 등록됨"

for p in "${!COMPOSE[@]}"; do
  while read -r f; do
    [ -e "$ROOT/$f" ] || fail "맵의 $p 가 가리키는 파일 없음: $f"
  done < <(compose_files "$p")
done
ok "맵의 모든 경로가 실재"

# ---------------------------------------------------------------------------
echo "[verify] (e) 앱 서비스 ≥1 — 각 프로젝트가 build: 서비스를 기여하는가"
# ---------------------------------------------------------------------------
# 근거(MONO-342): iam / wms 의 base compose 는 인프라 전용이다. base 만 기동하면
# DB 만 뜨고 앱이 0개가 된다. iam 은 OIDC IdP 이므로 데모 전체가 무너진다.
# `build:` 를 가진 서비스 = 이 저장소가 소스에서 굽는 서비스 = 애플리케이션.
appless=""
for p in "${!COMPOSE[@]}"; do
  n="$(render "$p" | awk '/^[[:space:]]{4}build:[[:space:]]*$/ { c++ } END { print c+0 }')"
  if [ "$n" -lt 1 ]; then
    appless="$appless  $p → build: 서비스 0개 (${COMPOSE[$p]})"$'\n'
  else
    ok "$p — build: 서비스 ${n}개"
  fi
done
[ -z "$appless" ] || fail "앱 서비스를 하나도 기여하지 않는 프로젝트:"$'\n'"$appless"\
  $'\n'"→ 데모에서 DB 만 뜨고 앱이 안 뜹니다. 풀스택 compose(docker-compose.e2e.yml)를"\
  $'\n'"   projects.sh 의 COMPOSE[<slug>] 에 함께 등록하세요 (TASK-MONO-342)."

# ---------------------------------------------------------------------------
echo "[verify] (g) 미설정 compose 변수 0건 (demo.env 로 전부 채워지는가)"
# ---------------------------------------------------------------------------
# 근거(MONO-346): compose 는 미설정 변수를 error 가 아니라 warning 으로 처리하고
# 빈 문자열로 보간한다. 비밀번호 자리에서 이는 "조용한 기동 실패"가 된다 —
# postgres:16-alpine 은 빈 POSTGRES_PASSWORD 로 초기화를 거부한다.
# 가드 (a)는 stderr 를 버리므로 이를 볼 수 없다. 여기서 경고를 에러로 승격한다.
#
# CI 의 fresh checkout 에는 gitignored `.env` 가 없으므로, 이 가드는 CI 에서
# 권위를 갖는다(실 `.env` 를 가진 개발자 로컬은 결손을 가릴 수 있다).
unset_vars() { # $1=slug|'traefik' → 미설정 변수명을 한 줄에 하나씩
  local out
  if [ "$1" = "traefik" ]; then
    out="$(docker compose -p verify-traefik -f "$ROOT/$TRAEFIK_COMPOSE" config -q 2>&1 >/dev/null)"
  else
    local ARGS; mapfile -t ARGS < <(compose_args "$1")
    out="$(docker compose -p "verify-$1" "${ARGS[@]}" config -q 2>&1 >/dev/null)"
  fi
  # 경고 문자열은 `\"NAME\"` 처럼 백슬래시-이스케이프된 따옴표를 포함한다.
  # 벗기지 않으면 grep 이 매치하지 않아 거짓 "clean" 이 된다.
  printf '%s\n' "$out" \
    | sed 's/\\"/"/g' \
    | grep -o '"[A-Za-z_][A-Za-z0-9_]*" variable is not set' \
    | sed 's/^"//; s/" variable is not set$//' \
    | sort -u || true
}

unset_report=""
for p in traefik "${!COMPOSE[@]}"; do
  vars="$(unset_vars "$p" | tr '\n' ' ')"
  vars="${vars% }"
  if [ -n "$vars" ]; then
    unset_report="$unset_report  $p → $vars"$'\n'
  else
    ok "$p — 미설정 변수 없음"
  fi
done
[ -z "$unset_report" ] || fail "빈 문자열로 보간되는 compose 변수:"$'\n'"$unset_report"\
  $'\n'"→ compose 는 이를 경고로만 알립니다. 비밀번호 자리라면 컨테이너가 기동에"\
  $'\n'"   실패합니다(postgres 는 빈 POSTGRES_PASSWORD 를 거부)."\
  $'\n'"→ 값을 infra/demo/demo.env 에 추가하세요 (TASK-MONO-346)."

# ---------------------------------------------------------------------------
echo "[verify] (h) 참조 이미지가 레지스트리에 실재하는가"
# ---------------------------------------------------------------------------
# 근거(MONO-353): `bitnami/kafka:3.7` 이 Docker Hub 에서 **삭제**됐다(태그 404,
# 레포 태그 목록 자체가 빔). scm/erp/fan compose 가 이를 참조하고 있었고
# `docker compose up` 이 `failed to resolve reference` 로 즉사했다.
#
# 이 결함의 성질이 중요하다: **우리 커밋과 무관하게 외부에서 깨진다.** 따라서
# 어떤 diff-기반 검사로도 잡히지 않는다. 게다가 CI 의 scm/erp/fan E2E 는
# Testcontainers 기반이라 이 compose 파일들을 **한 번도 실행하지 않았다** —
# 3개 프로젝트의 compose 가 완전히 깨진 채로 CI 는 계속 초록이었다.
#
# 레이트리밋과 "삭제"를 구분한다. 구분 없이 실패시키면 Docker Hub 익명 한도
# (IP 당 100/6h, GH 러너는 IP 공유)에 걸려 가드가 flaky 해지고, flaky 한 가드는
# 결국 꺼진다. **확정적 부재에만 FAIL** 하고 나머지는 skip 하되 건수를 찍는다
# (조용한 truncation 금지 — skip 이 0이 아니면 커버리지가 그만큼 비었다는 뜻).
# `build:` 를 가진 서비스의 image: 는 **우리가 소스에서 굽는 태그**(`…:local`)라
# 레지스트리에 존재하지 않는다. 이를 검사에 넣으면 30여 건이 전부 "확인 실패"로
# 잡혀 skip 목록을 가득 채우고, 그 소음이 **진짜 레이트리밋 skip 을 가린다** —
# 가드의 신호가 죽는다. 서비스 블록 단위로 build: 유무를 보고 걸러낸다.
all_images() { # 렌더된 compose 전부에서 '레지스트리에서 받아오는' image: 만 뽑는다
  for p in traefik "${!COMPOSE[@]}"; do
    render "$p" | awk '
      /^  [A-Za-z0-9._-]+:$/ { if (img != "" && !hasbuild) print img; img = ""; hasbuild = 0; next }
      /^    build:/          { hasbuild = 1 }
      /^    image:/          { img = $2 }
      END                    { if (img != "" && !hasbuild) print img }
    '
  done | tr -d '"' | sed '/^$/d' | sort -u
}

img_gone=""
img_ok=0
img_skip=0
img_skip_list=""
while read -r img; do
  [ -n "$img" ] || continue
  if err="$(docker manifest inspect "$img" 2>&1 >/dev/null)"; then
    img_ok=$((img_ok + 1))
    continue
  fi
  case "$err" in
    *"manifest unknown"* | *"no such manifest"* | *"not found"* | *"repository does not exist"*)
      img_gone="$img_gone  $img"$'\n' ;;
    *)
      # 레이트리밋 / 네트워크 / 인증 — 이미지의 결함이 아니다
      img_skip=$((img_skip + 1))
      img_skip_list="$img_skip_list  $img → ${err%%$'\n'*}"$'\n' ;;
  esac
done < <(all_images)

[ -z "$img_gone" ] || fail "레지스트리에서 사라진 이미지:"$'\n'"$img_gone"\
  $'\n'"→ compose 가 참조하는 이미지가 더 이상 존재하지 않습니다. 캐시가 없는 모든"\
  $'\n'"   환경(새 개발자 머신, 데모 AMI, 캐시 미스 CI)에서 기동이 실패합니다."\
  $'\n'"→ 살아있는 대체 이미지로 교체하세요 (TASK-MONO-353: bitnami/kafka → apache/kafka)."

ok "이미지 실재 검증 커버리지 ${img_ok}/$((img_ok + img_skip)) (skip ${img_skip}건)"
if [ "$img_skip" -gt 0 ]; then
  echo "  ⚠ ${img_skip}개는 확인하지 못했습니다(레지스트리 사정 — 결함 아님)." >&2
  echo "     skip 은 '이 이미지는 멀쩡하다'가 아니라 **'모른다'** 입니다 — 그만큼 커버리지가 빈 것입니다." >&2
  printf '%s' "$img_skip_list" >&2
fi

# --- 커버리지 단언 (TASK-MONO-359) ------------------------------------------
# 위 skip 설계는 옳다: 레이트리밋을 FAIL 로 바꾸면 가드가 flaky 해지고, flaky 한
# 가드는 결국 꺼진다. 그러나 그 관용에는 대가가 있다 — **전부 skip 되면 (h) 는
# 아무것도 확인하지 않고 통과한다.** 로컬에서 실제로 그렇게 됐다(익명 pull 한도
# 소진 → 전 이미지 `toomanyrequests` → img_ok=0, 그런데도 PASS).
#
# PR 잡에서는 그 관용이 맞다(외부 레이트리밋이 머지를 막으면 안 된다). 하지만
# **시계가 달린 잡에서 무신호를 초록으로 보고하는 것은 이 가드의 존재 이유를
# 정면으로 배신한다** — 매일 밤 아무것도 안 보고 등대를 켜는 셈이다.
# 그래서 커버리지 요구는 호출자가 켠다: nightly 만 --require-coverage 를 쓴다.
#
# 개별 이미지의 판정(확정 부재만 FAIL, 나머지는 skip)은 **바꾸지 않았다**.
# 이건 그 위에 얹은 '이번 실행이 무언가를 보긴 했는가' 단언이다.
if [ "$REQUIRE_COVERAGE" -eq 1 ] && [ "$img_ok" -eq 0 ]; then
  fail "이미지 실재 검증 커버리지가 0 입니다 — 이 실행은 단 한 건도 확인하지 못했습니다."\
    $'\n'"→ 이미지의 결함이 아닙니다. 이 실행이 **아무 신호도 만들지 못했다**는 뜻입니다"\
    $'\n'"   (skip ${img_skip}건 — 대개 레지스트리 익명 pull 레이트리밋)."\
    $'\n'"→ 무신호를 초록으로 보고하면 가드가 있으나 마나이므로, 시계가 달린 실행"\
    $'\n'"   (--require-coverage)에서는 이를 실패로 취급합니다."\
    $'\n'"→ 반복되면 docker/login-action 으로 인증 pull(한도 상향)을 붙이세요."
fi

# ---------------------------------------------------------------------------
echo "[verify] (i) Traefik Host() ↔ network alias 정합"
# ---------------------------------------------------------------------------
# 근거(MONO-358): 데모 호스트명은 **두 곳에서 해소돼야** 한다.
#   · 브라우저 → 공용 DNS(sslip.io) → 공인 IP → Traefik
#   · 컨테이너 → Docker 임베디드 DNS → Traefik 컨테이너
#     (console-web 이 OIDC 코드 교환을 **서버사이드**로 하기 때문. 그리고 AWS 는
#      인스턴스가 자기 공인 IP 로 보낸 트래픽을 되돌려주지 않으므로 — IGW hairpin
#      부재 — 컨테이너가 공용 DNS 를 타면 죽는다.)
#
# 두 번째를 Traefik 컨테이너의 network alias 가 담당하는데, 그 목록은 **수기 열거**라
# 라우터가 늘면 드리프트한다. 그리고 이 드리프트의 실패 모드가 고약하다:
# **로컬에서는 hosts 파일이 여전히 해소해 주므로 전부 통과하고, 클라우드에서만 터진다.**
# 정확히 가드가 있어야 하는 자리다.
traefik_aliases() {
  render traefik | awk '
    /^    networks:/      { innet = 1; next }
    innet && /aliases:/   { inal = 1; next }
    inal && /^          - / { sub(/^          - /, ""); print; next }
    inal && !/^          - / { inal = 0; innet = 0 }
  ' | tr -d '"' | sort -u
}

router_hosts() { # 프로젝트 compose 가 선언한 모든 Host(...) 호스트명
  for p in "${!COMPOSE[@]}"; do
    render "$p" | grep -oE 'Host\(`[^`]+`\)' | sed 's/Host(`//; s/`)//'
  done | sort -u
}

aliases_file="$(mktemp)"; hosts_file="$(mktemp)"
traefik_aliases > "$aliases_file"
router_hosts    > "$hosts_file"

missing_alias="$(comm -23 "$hosts_file" "$aliases_file")"
orphan_alias="$(comm -13 "$hosts_file" "$aliases_file")"
rm -f "$aliases_file" "$hosts_file"

[ -z "$missing_alias" ] || fail "Traefik alias 가 없는 라우터 호스트명:"$'\n'"$(printf '  %s\n' $missing_alias)"\
  $'\n'"→ 브라우저에서는 동작하지만 **컨테이너 안에서 이 이름이 해소되지 않습니다.**"\
  $'\n'"   console-web 의 서버사이드 OIDC 토큰 교환처럼 컨테이너→호스트명 호출이 죽습니다."\
  $'\n'"→ 로컬은 hosts 파일 덕에 멀쩡하고 **클라우드에서만 터집니다.**"\
  $'\n'"→ infra/traefik/docker-compose.yml 의 networks.traefik-net.aliases 에 추가하세요."

[ -z "$orphan_alias" ] || fail "라우터가 없는 고아 alias:"$'\n'"$(printf '  %s\n' $orphan_alias)"\
  $'\n'"→ 서빙하는 라우터가 없는 호스트명입니다. 정확히 iam.local 이 그랬고(6개 compose 가"\
  $'\n'"   기본값으로 참조하는데 라우터는 없었다), 그래서 데모 로그인이 불가능했습니다."
# NOTE: 위 메시지에 백틱을 쓰지 말 것. bash 큰따옴표 안의 `x` 는 **명령 치환으로 실행**되어
# `iam.local: command not found` 를 뱉고 그 자리가 빈 문자열이 된다 — 진단 메시지가 조용히
# 사라진다. 가드가 무는 것과 **가드가 이유를 말해주는 것**은 별개이고, 후자를 잃으면 사람은
# 무엇을 고쳐야 하는지 알 수 없다. (mutation-check 를 돌려봤기에 발견했다.)

ok "Host() ↔ alias 정합 ($(router_hosts | wc -l | tr -d ' ') 호스트명)"

# ---------------------------------------------------------------------------
echo "[verify] (j) IPv4-only 바인딩 서비스의 헬스체크가 localhost 를 찌르지 않는가"
# ---------------------------------------------------------------------------
# 근거(MONO-358): `HOSTNAME=0.0.0.0` 은 Node 를 **IPv4 전용**으로 바인딩시킨다
# (`node server.js` 가 그 값을 그대로 `server.listen` 에 넘기고, 0.0.0.0 은 IPv4 주소다.
#  env 를 빼면 Node 는 `::` 듀얼스택으로 연다). 그런데 alpine 의 /etc/hosts 는
# `localhost` 를 127.0.0.1 **과 ::1 둘 다** 로 매핑하고, **busybox wget 은 ::1 을 골라
# 실패한 뒤 IPv4 로 폴백하지 않는다**(curl 은 폴백한다).
#
# 결과: 앱은 멀쩡한데 프로브만 죽는다. 컨테이너 안에서 실측한 값이다 —
#   wget http://127.0.0.1:3000/api/health → {"status":"ok"}
#   wget http://localhost:3000/api/health → Connection refused
#
# 파급이 오타에 비해 터무니없이 크다: **Traefik 은 healthy 가 아닌 컨테이너를 조용히
# 건너뛴다**(debug 로그, 에러 0건). 그래서 콘솔은 통합 데모에서 **라우트 자체가 없었다.**
# 잘못된 루프백 주소 하나가 콘솔 전체를 보이지 않게 만들었다.
#
# 가드를 `wget + localhost` 전체로 넓히지 않는 이유: 저장소에 그 조합이 24곳 있고 대부분
# 정상 동작한다(듀얼스택으로 바인딩하므로). **오탐은 누락보다 나쁘다** — 멀쩡한 것을
# 고치라고 사람을 압박하면 가드가 신뢰를 잃는다. 실패 조건은 정확히 **IPv4-only 바인딩
# ∧ localhost 프로브** 이며, 여기서만 문다.
bad_hc=""
for p in "${!COMPOSE[@]}"; do
  found="$(render "$p" | awk '
    /^  [A-Za-z0-9._-]+:$/ {
      if (svc != "" && ipv4only && hclocal) print svc
      svc = $1; sub(/:$/, "", svc); ipv4only = 0; hclocal = 0; inhc = 0; next
    }
    /^      HOSTNAME:[[:space:]]*"?0\.0\.0\.0"?/ { ipv4only = 1 }
    /^    healthcheck:/                          { inhc = 1; next }
    inhc && /^    [a-z]/                         { inhc = 0 }
    inhc && /localhost/                          { hclocal = 1 }
    END { if (svc != "" && ipv4only && hclocal) print svc }
  ')"
  [ -z "$found" ] || bad_hc="$bad_hc  $p → $(echo $found)"$'\n'
done

[ -z "$bad_hc" ] || fail "IPv4-only 로 바인딩하면서 헬스체크는 localhost 를 찌르는 서비스:"$'\n'"$bad_hc"\
  $'\n'"→ HOSTNAME=0.0.0.0 은 IPv4 전용 바인딩입니다. alpine 의 localhost 는 ::1 로도"\
  $'\n'"   해소되고 busybox wget 은 ::1 실패 후 IPv4 로 폴백하지 않습니다."\
  $'\n'"→ 앱이 멀쩡해도 프로브가 죽고, **Traefik 은 healthy 가 아닌 컨테이너를 건너뛰므로**"\
  $'\n'"   그 서비스는 데모에서 라우트가 통째로 사라집니다(에러 로그 없이)."\
  $'\n'"→ 헬스체크 주소를 127.0.0.1 로 바꾸세요 (web-store 가 이미 그렇게 합니다)."

ok "IPv4-only 서비스의 헬스체크 주소 정상"

# ---------------------------------------------------------------------------
echo "[verify] (k) 마이그레이션에 박힌 .local 콜백을 데모 시드가 전부 덮는가"
# ---------------------------------------------------------------------------
# 근거(MONO-358): OAuth2 `redirect_uri` 는 **정확 일치** 검증이다. 브라우저용 클라이언트의
# 콜백 URL 은 Flyway 마이그레이션에 `http://console.local/api/auth/callback` 처럼 리터럴로
# 박혀 있는데, 온디맨드 데모는 부팅 때 파생되는 도메인 위에 뜬다. 마이그레이션이 알 수
# 없는 값이므로 `seed-demo-domain.sh` 가 런타임에 등록한다.
#
# 그 시드는 **`.local/` → `.${DEMO_DOMAIN}/`** 치환 하나로 동작한다. 즉 새 클라이언트가
# 그 형태를 벗어난 `.local` 콜백(`http://x.local:8080/cb` 처럼 포트가 붙거나, 경로 없이
# `http://x.local` 로 끝나는 것)을 들고 오면 **치환이 안 되고 그 도메인 로그인만 조용히
# 죽는다** — 컨테이너는 전부 healthy 하고 에러 로그도 없다. 정확히 이 저장소가 이미 한 번
# 당한 실패 모드(healthy ≠ usable)라 정적으로 막는다.
#
# 도달 가능성: 마이그레이션 추가는 diff 로 오므로 paths-filter 가 잡는다 — 시계가 아니라
# 이 가드가 물 기회를 실제로 얻는다.
seed_sh="$ROOT/infra/demo/seed-demo-domain.sh"
[ -f "$seed_sh" ] || fail "infra/demo/seed-demo-domain.sh 가 없습니다 — 데모 도메인 로그인이 불가능합니다."
# 주석을 먼저 걷어낸다. `demo-up.sh` 는 이 스크립트를 **주석에서도** 언급하므로
# 순진한 grep 은 호출이 삭제돼도 주석에 매치돼 통과한다 — mutation-check 로 잡은 실제 결함.
sed 's/#.*//' "$ROOT/infra/demo/demo-up.sh" | grep -q 'seed-demo-domain\.sh' \
  || fail "demo-up.sh 가 seed-demo-domain.sh 를 호출하지 않습니다 — 시드가 실행되지 않으면 로그인은 401 입니다."

# 마이그레이션의 redirect_uri 리터럴 중 `.local` 을 담은 것들.
mapfile -t local_cbs < <(
  grep -rhoE "http://[A-Za-z0-9.-]+\.local[^\"',[:space:]]*" \
    "$ROOT"/projects/iam-platform/apps/auth-service/src/main/resources/db/migration/*.sql 2>/dev/null \
  | sort -u
)
# vacuity 가드: 한 건도 못 찾았다면 grep 이 깨진 것이지 "안전"한 게 아니다.
# (0건을 통과로 보고하면 가드는 아무것도 안 하면서 초록을 준다.)
[ "${#local_cbs[@]}" -gt 0 ] \
  || fail "마이그레이션에서 .local URI 를 한 건도 찾지 못했습니다 — 가드가 헛돌고 있습니다"\
     $'\n'"   (경로가 바뀌었거나 grep 패턴이 깨졌습니다). 0건을 통과로 취급하지 않습니다."

uncovered=""
for u in "${local_cbs[@]}"; do
  # 시드의 치환 앵커는 `.local/` 이다. 이걸 포함하지 않으면 치환 대상이 되지 못한다.
  case "$u" in
    *.local/*) : ;;
    *) uncovered="$uncovered  $u"$'\n' ;;
  esac
done
[ -z "$uncovered" ] || fail "seed-demo-domain.sh 의 '.local/' 치환이 덮지 못하는 콜백 URI:"$'\n'"$uncovered"\
  $'\n'"→ 시드는 '.local/' → '.\${DEMO_DOMAIN}/' 치환 하나로 동작합니다. 위 URI 는 그 형태가"\
  $'\n'"   아니어서 데모 도메인에 등록되지 않습니다."\
  $'\n'"→ OAuth2 redirect_uri 는 정확 일치 검증입니다. 미등록이면 auth-service 가"\
  $'\n'"   401 {\"code\":\"UNAUTHORIZED\",\"message\":\"Missing or invalid internal credentials\"} 를"\
  $'\n'"   돌려줍니다 — 원인을 전혀 가리키지 않는 메시지라 오진하기 쉽습니다."\
  $'\n'"→ 마이그레이션의 URI 를 '.local/…' 형태로 맞추거나, seed-demo-domain.sh 의 치환 규칙을"\
  $'\n'"   넓히세요."

ok "마이그레이션의 .local 콜백 ${#local_cbs[@]}개 전부 시드 치환 범위 안"

# ---------------------------------------------------------------------------
echo "[verify] (l) Traefik 에 노출된 auth-service 가 X-Forwarded-* 를 이해하는가"
# ---------------------------------------------------------------------------
# 근거(MONO-358, EC2 실측): SAS 는 로그인 리다이렉트를 **자기가 보는 요청 호스트**로
# 만든다. 리버스 프록시 뒤에서 `server.forward-headers-strategy` 없이 두면:
#
#     HTTP/1.1 302
#     Location: http://auth-service:8081/login     ← 내부 컨테이너 DNS 가 브라우저로 샌다
#
# 브라우저는 이 이름을 해소할 수 없다 ⇒ 로그인 화면에 도달하지 못한다. auth-service 의
# application.yml 에는 이 설정이 없으므로 **데모 오버레이가 env 로 켜 줘야만** 한다.
# 라우터 라벨과 이 env 는 항상 함께 있어야 하는 한 쌍이다 — 한쪽만 지우면 라우팅은
# 되는데 로그인만 죽는 상태가 된다(가장 진단하기 나쁜 모양).
ov="$ROOT/infra/demo/iam-traefik.override.yml"
if grep -q 'traefik.http.routers.iam-oidc' "$ov"; then
  grep -q 'SERVER_FORWARD_HEADERS_STRATEGY' "$ov" \
    || fail "iam-traefik.override.yml 이 auth-service 를 Traefik 에 노출하면서"\
       $'\n'"   SERVER_FORWARD_HEADERS_STRATEGY 를 설정하지 않습니다."\
       $'\n'"→ 이게 없으면 Spring 이 X-Forwarded-Host 를 무시하고 로그인 리다이렉트를"\
       $'\n'"   'http://auth-service:8081/login' 로 내보냅니다 — 브라우저가 해소 못 하는 이름입니다."\
       $'\n'"→ auth-service.environment 에 SERVER_FORWARD_HEADERS_STRATEGY: FRAMEWORK 를 넣으세요."
  ok "auth-service OIDC 라우터 + forward-headers 쌍 유지"
else
  fail "iam-traefik.override.yml 에 auth-service OIDC 라우터(iam-oidc)가 없습니다 —"\
    $'\n'"   게이트웨이는 /login 을 라우팅하지 않으므로(404) 로그인 화면에 도달할 수 없습니다."
fi

# ---------------------------------------------------------------------------
echo "[verify] (m) 쿠키 Secure 해제와 https 오리진이 함께 쓰이지 않는가"
# ---------------------------------------------------------------------------
# 근거(MONO-358): 데모는 평문 HTTP 다. 브라우저는 **localhost 가 아닌 오리진에서 http 로
# 온 `Secure` 쿠키를 저장조차 하지 않으므로**(curl 도 동일 — Set-Cookie 를 받고도 쿠키 자가
# 비었다) PKCE/state 쿠키가 사라지고 모든 로그인이 `invalid_state` 로 튕긴다. 그래서
# `CONSOLE_COOKIE_SECURE=false` 가 데모에 **필요**하다.
#
# 위험한 것은 그 자체가 아니라 **조합**이다: TLS 오리진(https://)에서 Secure 를 끄면 그건
# 진짜 다운그레이드다(세션 쿠키가 평문으로 샐 수 있다). 그 하나만 막는다. Secure 를 끄는
# 것 자체를 금지하면 데모가 성립하지 않으므로, 금지 대상을 정확히 좁힌다.
#
# 동시에 `CONSOLE_PUBLIC_ORIGIN` 이 데모 도메인을 가리키는지도 본다 — 빠지면 로그인 직후
# 콜백이 브라우저를 `console.local` 로 보낸다(빌드타임 인라인된 NEXT_PUBLIC_APP_URL).
console_render="$(render console)"
# NOTE: do NOT split on ': ' — a URL contains one ("http://…"), so an awk
# field-split hands back "http" and the https check below silently never
# matches. Strip exactly the `KEY:` prefix instead. (Caught by mutation-check;
# the guard passed either way, which is precisely the failure it exists to
# prevent.)
yaml_val() { # $1=key → value, quotes stripped
  printf '%s\n' "$console_render" \
    | sed -n "s/^[[:space:]]*$1:[[:space:]]*//p" | tr -d '"' | head -1
}
cookie_secure="$(yaml_val CONSOLE_COOKIE_SECURE)"
pub_origin="$(yaml_val CONSOLE_PUBLIC_ORIGIN)"

[ -n "$pub_origin" ] || fail "console 렌더에 CONSOLE_PUBLIC_ORIGIN 이 없습니다 —"\
  $'\n'"   NEXT_PUBLIC_APP_URL 은 빌드타임에 인라인되므로 프리베이크 이미지의 오리진을 바꾸지"\
  $'\n'"   못합니다. 로그인 직후 콜백이 브라우저를 http://console.local 로 보냅니다."

case "$cookie_secure:$pub_origin" in
  false:https://*)
    fail "CONSOLE_COOKIE_SECURE=false 인데 CONSOLE_PUBLIC_ORIGIN 이 https 입니다: $pub_origin"\
      $'\n'"→ TLS 오리진에서 Secure 를 끄는 것은 진짜 다운그레이드입니다(세션 쿠키 평문 노출)."\
      $'\n'"→ https 를 쓴다면 CONSOLE_COOKIE_SECURE 를 지우세요(기본값 true)."
    ;;
esac

# `false` 를 쓰는 쪽은 반드시 http 오리진이어야 하고, 그 역(https + Secure)은 항상 안전하다.
ok "쿠키 Secure=$cookie_secure ↔ 오리진 $pub_origin (조합 안전)"

# ---------------------------------------------------------------------------
echo "[verify] (n) 부팅 경로가 DEMO_DOMAIN 을 실제로 설정하는가"
# ---------------------------------------------------------------------------
# 근거(MONO-366): MONO-358 이 저장소 쪽 계약을 만들었다 — **`DEMO_DOMAIN` 을 주면 그
# 도메인으로 뜨고 로그인까지 된다.** 그런데 **부팅 자동화가 그 계약을 쓰지 않았다.**
# systemd 유닛이 `demo-up.sh` 를 직접 불렀고 `DEMO_DOMAIN` 은 어디에도 없었다:
#
#   ExecStart=... /opt/monorepo-lab/infra/demo/demo-up.sh ${DEMO_PROFILE}
#
# → `demo.env` 기본값 `local` → 라우터가 전부 `Host(x.local)` → 방문자 브라우저는
# `Host: <공인IP>` 를 보내므로 **전 도메인 404**. 그런데 **컨테이너 96개는 전부 healthy
# 하다.** healthcheck 도, compose 렌더도, CI 도 이걸 볼 수 없다 — 358 의 로그인 증명이
# 매번 손으로 재기동해서 얻어진 이유이고, 자동 경로는 한 번도 동작한 적이 없다.
#
# 이 가드는 부팅 계약의 세 고리를 본다. 하나만 끊겨도 데모는 조용히 도달 불가능해진다.
boot_sh="$ROOT/infra/demo/demo-boot.sh"
unit="$ROOT/infra/demo/demo-stack.service"
pkr="$ROOT/infra/demo/aws/packer/demo-ami.pkr.hcl"

[ -f "$boot_sh" ] || fail "infra/demo/demo-boot.sh 가 없습니다 — 부팅 시 도메인을 파생할 곳이 없습니다."
[ -f "$unit" ]    || fail "infra/demo/demo-stack.service 가 없습니다 — 유닛을 저장소가 소유해야 계약이 한 곳에 있습니다."

# (1) 유닛이 부팅 진입점을 부르는가 — `demo-up.sh` 직접 호출은 정확히 그 결함이다.
#     주석은 걷어내고 본다: 유닛 헤더가 결함을 **설명하느라** `demo-up.sh` 를 언급하므로,
#     순진한 grep 은 자기 주석에 매치해 통과한다. (k) 가 358 에서 당한 함정이다.
unit_exec="$(sed 's/#.*//' "$unit" | grep -E '^[[:space:]]*ExecStart=' || true)"
[ -n "$unit_exec" ] || fail "demo-stack.service 에 ExecStart 가 없습니다."
case "$unit_exec" in
  *demo-boot.sh*) : ;;
  *) fail "demo-stack.service 의 ExecStart 가 demo-boot.sh 를 부르지 않습니다:"\
       $'\n'"   $unit_exec"\
       $'\n'"→ demo-up.sh 를 직접 부르면 DEMO_DOMAIN 이 설정되지 않아 스택이 *.local 로 뜹니다."\
       $'\n'"   방문자 브라우저는 Host: <공인IP> 를 보내므로 어떤 라우터에도 매치되지 않습니다(전 도메인 404)."\
       $'\n'"→ systemd 의 Environment= 는 셸이 아니라 명령 치환이 안 됩니다. 공인 IP 는 부팅 시점에"\
       $'\n'"   IMDSv2 로 읽어야 하므로 파생은 반드시 스크립트(demo-boot.sh) 안에서 일어나야 합니다." ;;
esac

# (2) 진입점이 demo-up.sh 호출 **전에** DEMO_DOMAIN 을 export 하는가.
#     순서가 load-bearing 이다 — 나중에 export 하면 demo-up 은 이미 떠난 뒤다.
boot_body="$(sed 's/#.*//' "$boot_sh")"
# `|| true` 는 장식이 아니다. 이 스크립트는 `set -euo pipefail` 로 돈다 — grep 이 매치를
# 못 찾으면 1 을 반환하고, 그러면 아래의 `fail` 이 **출력되기 전에** 스크립트가 조용히
# 죽는다. 즉 가드는 "물지만 이유를 말하지 못하는" 상태가 된다. mutation-check 로 잡았다.
exp_line="$(printf '%s\n' "$boot_body" | grep -n 'export DEMO_DOMAIN' | head -1 | cut -d: -f1 || true)"
up_line="$(printf '%s\n' "$boot_body"  | grep -n 'demo-up\.sh'        | head -1 | cut -d: -f1 || true)"
[ -n "$exp_line" ] || fail "demo-boot.sh 가 DEMO_DOMAIN 을 export 하지 않습니다 —"\
  $'\n'"   그러면 demo.env 의 기본값 local 이 그대로 먹고 데모는 도달 불가능해집니다."
[ -n "$up_line" ]  || fail "demo-boot.sh 가 demo-up.sh 를 호출하지 않습니다."
[ "$exp_line" -lt "$up_line" ] || fail "demo-boot.sh 가 demo-up.sh 를 호출한 뒤에 DEMO_DOMAIN 을 export 합니다"\
  $'\n'"   (export=L$exp_line, demo-up=L$up_line) — 순서가 뒤집히면 파생값이 전달되지 않습니다."

# (3) demo.env 의 DEMO_DOMAIN 은 `${DEMO_DOMAIN:-local}` 이어야 한다.
#     bare 대입(`DEMO_DOMAIN=local`)이면 demo-up.sh 의 `set -a; source demo.env` 가
#     **demo-boot.sh 가 export 한 값을 덮어쓴다.** MONO-358 에서 실제로 당했고, 증상은
#     "파생은 성공했는데 스택은 여전히 .local" 이라 원인이 보이지 않는다.
grep -qE '^DEMO_DOMAIN="?\$\{DEMO_DOMAIN:-local\}"?' "$ROOT/infra/demo/demo.env" \
  || fail "demo.env 의 DEMO_DOMAIN 이 \${DEMO_DOMAIN:-local} 형태가 아닙니다."\
     $'\n'"→ bare 대입이면 demo-up.sh 의 'set -a; source demo.env' 가 demo-boot.sh 가 export 한"\
     $'\n'"   파생값을 덮어씁니다. 파생은 성공하는데 스택은 여전히 .local 로 뜹니다."

# (4) Packer 가 유닛을 **저장소에서** 설치하는가. 사본을 구우면 저장소가 계약을 바꿔도
#     AMI 는 옛 유닛을 들고 있다 — 이 task 가 고치는 드리프트의 근원이다.
if [ -f "$pkr" ]; then
  grep -q '/opt/monorepo-lab/infra/demo/demo-stack.service' "$pkr" \
    || fail "packer 템플릿이 systemd 유닛을 저장소 경로에서 설치하지 않습니다."\
       $'\n'"→ 사본(예: ../ec2/demo-stack.service)을 구우면 저장소가 부팅 계약을 바꿔도"\
       $'\n'"   AMI 는 옛 유닛을 들고 부팅합니다. 인스턴스에는 이미 저장소가 클론돼 있습니다."
fi

ok "부팅 계약 유지 (유닛 → demo-boot.sh → DEMO_DOMAIN export → demo-up.sh)"

echo "[verify] (o) Packer 가 AWS 에 보내는 문자열이 ASCII 인가"
# ---------------------------------------------------------------------------
# 근거(MONO-379): EC2 의 ModifyImageAttribute 는 description 에 0x7F 초과 바이트를
# 거부한다. 그런데 Packer 는 그 속성을 **이미지를 다 구운 뒤에** 설정하고, 거부를
# 빌드 실패로 취급해 **방금 만든 AMI 를 deregister 하고 스냅샷까지 지운다.**
#
#   Modifying: description
#   Error: InvalidParameterValue: Character sets beyond ASCII are not supported.
#   ==> Deregistered AMI id: ami-01b26cf9e9ae69632
#   ==> Deleted snapshot: snap-...
#
# em dash(U+2014) 하나가 40분치 빌드를 태우고 산출물을 파괴했다. **`packer validate`
# 는 통과한다** — 문법은 멀쩡하고, AWS 만이 거부한다. 이 저장소가 이미 배운 명제의
# 재현이다: 정적 검사가 통과하는 것과 동작하는 것은 다른 명제다.
#
# 왜 하필 지금 터졌나: scratchpad PoC 를 저장소로 승격하면서 산문 습관대로 em dash 가
# 들어갔고, **승격본은 한 번도 빌드된 적이 없었다.** 옛 AMI 는 승격 전 사본으로 구운
# 것이다 — 전형적인 "선언 ↔ 진실" 드리프트.
if [ -f "$pkr" ]; then
  # ami_name 도 함께 본다 — 같은 API 가 같은 이유로 거부한다.
  #
  # `grep -P '[^\x00-\x7F]'` 를 쓰지 않는다. 첫 판이 그랬는데, msys/Windows 의 grep 이
  #   grep: -P supports only unibyte and UTF-8 locales
  # 로 죽었고 `|| true` 가 그 실패를 삼켜 **가드가 정상 트리에서도, em dash 를 주입한
  # 트리에서도 통과했다.** 물지 못하는 가드다 — mutation-check 로만 잡힌다.
  # `tr` 은 바이트로 동작하므로 로케일·구현에 무관하다: 0x00–0x7F 를 지우고 남는 것이
  # 있으면 그 줄에 비-ASCII 바이트가 있다.
  bad=""
  while IFS= read -r line; do
    [ -n "$line" ] || continue
    [ -n "$(printf '%s' "$line" | LC_ALL=C tr -d '\0-\177')" ] && bad="$bad   $line"$'\n'
  done < <(grep -nE '^[[:space:]]*(ami_description|ami_name)[[:space:]]*=' "$pkr" || true)
  if [ -n "$bad" ]; then
    fail "packer 템플릿의 ami_description/ami_name 에 비-ASCII 문자가 있습니다:"\
      $'\n'"$bad"\
      $'\n'"→ EC2 ModifyImageAttribute 가 이를 거부하고, Packer 는 **이미지를 다 구운 뒤에**"\
      $'\n'"   그 속성을 설정하므로 빌드가 실패하며 **방금 만든 AMI 와 스냅샷을 지웁니다.**"\
      $'\n'"   40분과 산출물이 함께 사라집니다. packer validate 는 이것을 잡지 못합니다."\
      $'\n'"→ ASCII 하이픈(-)을 쓰십시오."
  fi
fi
ok "packer 의 AMI 이름/설명이 ASCII"

echo "[verify] (p) 로그인 페이지가 링크하는 경로가 데모 엣지에서 라우팅되는가"
# ---------------------------------------------------------------------------
# 근거(MONO-380): iam OIDC 라우터 규칙은 **경로를 열거한다** —
#
#   Host(iam.<도메인>) && (PathPrefix(/oauth2) || PathPrefix(/login) || PathPrefix(/.well-known))
#
# 그리고 `/signup` 이 빠져 있었다. 그 요청은 iam **게이트웨이** 라우터로 떨어지고
# 게이트웨이엔 그런 경로가 없어 **404** 다. 그런데 **로그인 폼 자신이 /signup 으로
# 링크를 건다.**
#
# 치명적인 이유: **갓 부팅한 데모의 credentials 테이블은 비어 있다**(실측 — 새 AMI
# 로 부팅한 인스턴스에서 `SELECT email FROM credentials` 가 0행). 즉 **가입이 유일한
# 입구**이고, 그 입구가 404 이면 **아무도 데모에 로그인할 수 없다.** 그런데 컨테이너
# 96개는 전부 healthy 하고, /login 도 200 이고, 라우터도 "있다".
#
# 358 의 로그인 증명이 통했던 것은 그 인스턴스의 DB 에 계정이 **누적돼 있었기**
# 때문이다 — 새 부팅에는 없다. 열거된 목록은 드리프트한다: 손으로 세지 말고
# **템플릿이 실제로 링크하는 경로**와 대조한다.
tpl_dir="$ROOT/projects/iam-platform/apps/auth-service/src/main/resources/templates"
ovr="$ROOT/infra/demo/iam-traefik.override.yml"
if [ -d "$tpl_dir" ] && [ -f "$ovr" ]; then
  rule_line="$(grep -F 'routers.iam-oidc.rule=' "$ovr" || true)"
  [ -n "$rule_line" ] || fail "iam-traefik.override.yml 에 iam-oidc 라우터 규칙이 없습니다."

  # 템플릿의 `@{/xxx}` (Thymeleaf 링크/폼 action) 에서 최상위 경로 세그먼트를 뽑는다.
  # 예: @{/signup} → /signup, @{'/login/oauth/' + ...} → /login
  missing=""
  while IFS= read -r seg; do
    [ -n "$seg" ] || continue
    case "$rule_line" in
      *"PathPrefix(\`/$seg\`)"*) : ;;
      *) missing="$missing   /$seg"$'\n' ;;
    esac
  done < <(grep -ohE "@\{'?/[a-zA-Z0-9_.-]+" "$tpl_dir"/*.html 2>/dev/null \
             | sed -E "s/^@\{'?\///" | sort -u)

  [ -z "$missing" ] || fail "로그인/가입 템플릿이 링크하는데 데모 엣지 라우터가 덮지 않는 경로:"\
    $'\n'"$missing"\
    $'\n'"→ 이 경로들은 iam 게이트웨이 라우터로 떨어져 **404** 가 됩니다."\
    $'\n'"→ 갓 부팅한 데모의 credentials 는 비어 있어 **가입이 유일한 입구**입니다."\
    $'\n'"   그 입구가 404 면 컨테이너가 전부 healthy 해도 **아무도 로그인할 수 없습니다.**"\
    $'\n'"→ iam-traefik.override.yml 의 iam-oidc 규칙에 PathPrefix 를 추가하세요."
  ok "브라우저 표면 경로 전부 라우팅됨 ($(grep -ohE "@\{'?/[a-zA-Z0-9_.-]+" "$tpl_dir"/*.html 2>/dev/null | sed -E "s/^@\{'?\///" | sort -u | tr '\n' ' '))"
fi

echo "[verify] (q) 문서가 부르는 terraform output 이 실제로 존재하는가"
# ---------------------------------------------------------------------------
# 근거(MONO-389): README 가 `terraform output api_endpoint` 를 시키는데 **그런 output 은
# 없었다**(실제 이름은 api_base_url). 절차를 그대로 따르면 "Output not found" 로 죽는다.
#
# **아무도 이 문서를 끝까지 따라 해본 적이 없어서** 그 한 줄이 살아남았다. 배포된 적이
# 없는 절차는 고장 났는지 알 수 없다 — 그래서 사람이 아니라 가드가 대조한다.
tf_dir="$ROOT/infra/demo/aws/terraform"
if [ -d "$tf_dir" ]; then
  declared="$(grep -hoE '^output[[:space:]]+"[^"]+"' "$tf_dir"/*.tf 2>/dev/null \
                | sed -E 's/^output[[:space:]]+"([^"]+)"/\1/' | sort -u)"
  [ -n "$declared" ] || fail "(q) outputs.tf 에서 output 을 하나도 못 읽었습니다 — 가드가 공허합니다."

  # **인용문(`>`)은 지시가 아니다.** 이 README 는 옛 절차가 왜 틀렸는지를 인용으로 설명하는데,
  # 거기 적힌 `terraform output api_endpoint` 는 *하지 말라*는 뜻이지 하라는 뜻이 아니다.
  # 그것까지 물면 이 가드는 **정직한 설명에 대해 첫날부터 RED** 이고, 첫날 RED 인 가드는
  # 꺼진다 — 그리고 꺼진 잡의 skip 은 초록으로 보고된다(MONO-360). 오탐 0 이 무는 것만큼 중요하다.
  referenced="$(grep -rnE 'terraform output [a-z_]+' "$ROOT/infra/demo/aws" "$ROOT/README.md" 2>/dev/null \
                  | grep -vE '^[^:]*:[0-9]+:[[:space:]]*>' \
                  | grep -oE 'terraform output [a-z_]+' \
                  | sed -E 's/^terraform output //' | sort -u)"
  [ -n "$referenced" ] || fail "(q) 문서에서 'terraform output <name>' 참조를 하나도 못 찾았습니다 — 가드가 공허합니다."

  ghost=""
  while IFS= read -r name; do
    [ -n "$name" ] || continue
    printf '%s\n' "$declared" | grep -qx "$name" || ghost="$ghost   terraform output $name"$'\n'
  done <<EOF
$referenced
EOF
  [ -z "$ghost" ] || fail "문서가 **존재하지 않는** terraform output 을 부릅니다:"\
    $'\n'"$ghost"\
    $'\n'"→ 선언된 output: $(printf '%s' "$declared" | tr '\n' ' ')"\
    $'\n'"→ 절차를 그대로 따르면 'Output not found' 로 죽습니다."
  ok "문서가 부르는 output 이 전부 실재함 ($(printf '%s' "$referenced" | tr '\n' ' '))"
fi

echo "[verify] (r) 배포될 페이지가 API URL 을 리터럴로 들고 있지 않은가"
# ---------------------------------------------------------------------------
# 근거(MONO-389): site/index.html 에 `const API_BASE = "https://7l4n2ydrkd.execute-api…"`
# 가 **커밋돼 있었고, 그 API 는 존재하지 않았다.** API Gateway id 는 재생성마다 바뀌는데
# 그 값이 git 에 박혀 있었으니, **드리프트가 일어난 게 아니라 일어나도록 설계돼 있었다.**
#
# 고친 방향은 "값" 이 아니라 **모양** 이다: terraform 이 자기 상태에서 config.js 를 렌더한다
# ⇒ 배포된 페이지가 자기 API 와 어긋나는 것이 표현 불가능해진다. 이 가드는 그 모양을 지킨다.
# 🔴 **`git grep` 이다. `grep -r` 이 아니다** — 형제 가드 (s) 가 이미 배운 것을 이 가드는
# 2026-08-19 까지 배우지 못했다(TASK-MONO-557 에서 발견). 명제는 *"리터럴이 **커밋**돼
# 있는가"* 인데 `grep -r` 은 파일시스템을 본다. 두 술어는 site/ 에 **빌드 산출물이 생기기
# 전까지만** 같은 답을 냈고, Vercel 이전이 정확히 그 산출물을 만든다: `site/build.sh` 가
# `site/public/config.js` 를 렌더하며 API URL 리터럴을 쓴다. 그건 **의도된 동작**이고
# (생성될 뿐 커밋되지 않는다 — `site/.gitignore` 가 막는다) `grep -r` 은 그걸 결함으로
# 신고했다(실측: 이 티켓 작업 중 RED). 술어와 모집단이 어긋난 것이다.
#
# `--untracked` 인 이유: tracked 만 보면 **아직 커밋 안 된 새 파일**의 리터럴을 놓친다.
# `--untracked` 는 untracked 를 포함하되 **gitignore 는 존중**하므로 술어가 정확히
# *"커밋될 수 있는가"* 가 된다.
site_dir="$ROOT/infra/demo/aws/site"
if [ -d "$site_dir" ]; then
  # 주석 안의 예시 URL 은 잡지 않는다 — 첫날 RED 인 가드는 꺼지고,
  # 꺼진 잡의 skip 은 초록으로 보고된다(MONO-360). 코드 줄의 리터럴만 본다.
  hits="$(git -C "$ROOT" grep --untracked -nIE '^[^#/*]*["'"'"'][^"'"'"']*execute-api[^"'"'"']*["'"'"']' \
            -- 'infra/demo/aws/site/' 2>/dev/null || true)"
  [ -z "$hits" ] || fail "배포될 페이지가 API Gateway URL 을 **리터럴로** 들고 있습니다:"\
    $'\n'"$hits"\
    $'\n'"→ API id 는 terraform 재생성마다 바뀝니다. 커밋된 리터럴은 **커밋되는 순간부터 썩습니다.**"\
    $'\n'"→ CloudFront 판은 terraform 이 config.js 를 렌더하고(aws_s3_object.config),"\
    $'\n'"   Vercel 판은 site/build.sh 가 DEMO_API_BASE 환경변수에서 렌더합니다."
  ok "site/ 에 커밋 가능한 API URL 리터럴 없음 (빌드 산출물은 gitignore 로 제외)"
fi

echo "[verify] (s) 저장소 어디에도 배포마다 바뀌는 엔드포인트가 박혀 있지 않은가"
# ---------------------------------------------------------------------------
# (r) 은 site/ 만 본다. 그런데 같은 결함은 **문서로도 들어온다.**
#
# MONO-389 를 하면서 나는 "실제 site_url 을 루트 README 에 넣겠다" 고 적었다. 그 URL 은
# CloudFront 배포 도메인이고, 배포마다 새로 할당되며, destroy 하면 죽는다 — 즉 내가 (r) 로
# 막은 **바로 그 썩는 리터럴**이다. 가드를 세워 놓고 그 옆에서 같은 짓을 할 뻔했다.
#
# 규칙: API Gateway id 와 CloudFront 배포 도메인은 **terraform 상태이지 소스가 아니다.**
# 저장소에 적히는 순간 부패가 시작된다. 값이 필요하면 `terraform output` 이 유일한 출처다.
#
# **`git grep` 이다. `grep -r` 이 아니다.** 명제는 "저장소에 *커밋*돼 있는가" 인데
# `grep -r` 은 파일시스템을 본다 — 그래서 첫 판본이 `terraform.tfstate` 를 물었다.
# 그 파일은 gitignore 되어 **커밋되지 않는다.** 술어와 모집단이 어긋난 것이고,
# 그런 가드는 첫날 빨개져서 꺼진다(MONO-360). tracked 만 보면 그 실패가 사라진다.
#
# 오탐 둘을 더 의도적으로 비켜간다:
#   1. 산문의 "CloudFront" 라는 낱말 — 구체적 id 가 붙은 호스트명만 본다.
#   2. task/ADR 이 죽은 URL 을 **증거로 인용**하는 것 — TASK-MONO-389 본문이 실제로
#      그렇게 한다. 술어는 이것이다: **task/ADR 은 "무엇이었는지" 를 기록하고,
#      README/infra 는 "무엇을 하라" 고 지시한다.** 부패하는 것은 지시뿐이다.
volatile_re='[a-z0-9]{6,}\.cloudfront\.net|[a-z0-9]{6,}\.execute-api\.[a-z0-9-]+\.amazonaws\.com'
hits="$(git -C "$ROOT" grep -nIE "$volatile_re" -- \
          ':(exclude)tasks/' ':(exclude,glob)**/adr/**' \
          ':(exclude)infra/demo/verify-demo-wrapper.sh' 2>/dev/null || true)"
[ -z "$hits" ] || fail "배포마다 바뀌는 엔드포인트가 저장소에 커밋돼 있습니다:"\
  $'\n'"$hits"\
  $'\n'"→ API Gateway id 와 CloudFront 도메인은 **terraform 상태이지 소스가 아닙니다.**"\
  $'\n'"→ destroy/재생성 한 번이면 죽습니다. 유일한 출처는 \`terraform output site_url\` 입니다."
ok "저장소에 휘발성 엔드포인트 리터럴 없음"

echo "[verify] (t) 페이지가 만드는 데모 도메인이 부팅이 파생하는 것과 같은가"
# ---------------------------------------------------------------------------
# 근거(MONO-389): `demo-boot.sh` 는 IMDSv2 로 읽은 IP 를 **대시**로 바꿔 파생한다
# (`tr '.' '-'`) 그리고 Traefik 라우터는 그 표기로만 뜬다. 그런데 `site/index.html` 은
# **점 표기**로 링크를 만들고 있었다.
#
# sslip.io 가 두 표기를 모두 해석해 주는 것이 함정이다: DNS 는 풀리고 TCP 도 붙는데
# **Traefik 이 매치되는 라우터를 못 찾아 404** 를 낸다. 실측: 점 → 404 / 대시 → 307.
# 사이트 200, `/start` 200, 96개 컨테이너 healthy — 그런데 방문자는 데모에 못 들어간다.
# **"버튼이 200 을 낸다" 와 "방문자가 도달한다" 는 다른 명제다.**
#
# 그래서 이 가드는 문자열을 열거하지 않고 **두 규칙을 같은 입력으로 실행해 대조한다.**
# 어느 쪽이 바뀌든(대시→점, 접미사 변경, 서브도메인 추가) 두 값이 갈라지는 순간 빨개진다.
site_html="$ROOT/infra/demo/aws/site/index.html"
if [ -f "$site_html" ]; then
  command -v node >/dev/null 2>&1 || fail "(t) node 가 없습니다 — 이 가드는 페이지의 규칙을 **실행**해서 대조합니다."\
    $'\n'"→ 조용히 건너뛰면 skip 이 초록으로 보고됩니다(MONO-360). 건너뛰지 않습니다."

  sample_ip="203.0.113.7"   # TEST-NET-3. 실주소가 아니므로 값이 새어도 무해하다.
  boot_host="$(printf '%s' "$sample_ip" | tr '.' '-').sslip.io"

  page_expr="$(sed -n 's/^[[:space:]]*\(const demoHost =.*\); \/\/ GUARD-T-ANCHOR.*/\1/p' "$site_html")"
  [ -n "$page_expr" ] || fail "(t) index.html 에서 GUARD-T-ANCHOR 를 못 찾았습니다 — **가드가 공허합니다.**"\
    $'\n'"→ demoHost 를 바꿨다면 앵커 주석도 함께 유지하세요."

  page_host="$(node -e "$page_expr; process.stdout.write(demoHost('$sample_ip'))")"

  [ "$page_host" = "$boot_host" ] || fail "페이지가 만드는 데모 도메인이 부팅의 파생과 다릅니다:"\
    $'\n'"    demo-boot.sh  → $boot_host"\
    $'\n'"    index.html    → $page_host"\
    $'\n'"→ Traefik 라우터는 **부팅이 파생한 표기로만** 존재합니다. 다른 표기로 링크하면"\
    $'\n'"   DNS 는 풀리고 TCP 도 붙지만 **404** 가 납니다 (실측: 점 404 / 대시 307)."
  ok "페이지와 부팅이 같은 도메인을 만든다 ($page_host)"
fi

# ---------------------------------------------------------------------------
echo "[verify] (u) 콘솔의 '.local' 기본값이 demo.env + compose 로 전부 덮이는가"
# ---------------------------------------------------------------------------
# 근거(MONO-505): 콘솔 코드는 도메인 대상 URL 마다 **하드코딩된 `.local` 기본값**을
# 들고 있다(console-web 의 zod `.default('http://wms.local/...')`, console-bff 의
# `${KEY:http://wms.local}`). 데모가 그걸 안 덮으면:
#
#   로컬 : `.local` 이 hosts 파일 + Traefik alias 로 풀린다  → **우연히 통과한다**
#   AWS  : 도메인이 `<ip>.sslip.io` 다                       → 아무데도 안 풀린다
#
# 즉 이 드리프트의 실패 모드는 **로컬에서 초록**이고 클라우드에서만 터진다. 가드 (i)
# 와 같은 부류이고, 같은 이유로 사람이 못 잡는다.
#
# 술어를 손으로 열거하지 않는다 — 그러면 키가 하나 늘 때 조용히 비어버린다(가드가
# 아무것도 안 하면서 초록을 준다). **소스에서 `.local` 기본값을 가진 키를 뽑아내고**
# 그 집합이 demo.env 에 전부 있는지 본다. 새 도메인 키가 코드에 추가되면 이 가드는
# 자동으로 그것까지 요구한다.
#
# 제외: NEXT_PUBLIC_* — Next 가 **빌드타임에 인라인**하므로 런타임 env 로 덮을 수
# 없다(그것이 CONSOLE_PUBLIC_ORIGIN 이 따로 존재하는 이유다. MONO-358).
console_web_env="$ROOT/projects/platform-console/apps/console-web/src/shared/config/env.ts"
console_bff_yml="$ROOT/projects/platform-console/apps/console-bff/src/main/resources/application.yml"
console_compose="$ROOT/projects/platform-console/docker-compose.yml"
demo_env_file="$ROOT/infra/demo/demo.env"

for f in "$console_web_env" "$console_bff_yml" "$console_compose" "$demo_env_file"; do
  [ -r "$f" ] || fail "(u) 읽을 수 없습니다: ${f#$ROOT/}"\
    $'\n'"→ 콘솔이 이동/개명됐다면 이 가드의 경로도 함께 옮기세요. 파일이 없다고 건너뛰면"\
    $'\n'"   가드는 아무것도 검사하지 않으면서 초록을 보고합니다."
done

# console-web: `KEY: z` … `.default('http://<host>.local…')` (여러 줄에 걸쳐 있다)
web_keys="$(
  tr -d '\r' < "$console_web_env" | awk '
    /^  [A-Z][A-Z0-9_]*: z/ { k=$1; sub(/:$/,"",k) }
    k != "" && /\.default\(.http:\/\/[a-zA-Z0-9.-]*\.local/ { print k; k="" }
  ' | grep -v '^NEXT_PUBLIC_' | sort -u
)"
# console-bff: `${KEY:http://<host>.local…}`
bff_keys="$(
  tr -d '\r' < "$console_bff_yml" \
    | grep -oE '\$\{[A-Z][A-Z0-9_]*:http://[a-zA-Z0-9.-]*\.local' \
    | sed 's/^\${//; s/:http.*//' | sort -u
)"

console_keys="$(printf '%s\n%s\n' "$web_keys" "$bff_keys" | grep -v '^$' | sort -u)"

[ -n "$console_keys" ] || fail "(u) 콘솔 소스에서 '.local' 기본값 키를 **하나도** 못 뽑았습니다."\
  $'\n'"→ 0건은 '없음' 이 아니라 **추출식이 깨졌다**는 신호입니다(zod/yaml 표기 변경 등)."\
  $'\n'"   추출식을 고치기 전까지 이 가드는 공허합니다."

missing_demo=""
for k in $console_keys; do
  grep -qE "^${k}=" "$demo_env_file" || missing_demo="$missing_demo $k"
done

[ -z "$missing_demo" ] || fail "demo.env 가 덮지 않은 콘솔 '.local' 기본값 키:"\
  $'\n'"$(printf '  %s\n' $missing_demo)"\
  $'\n'"→ 이 키들은 데모에서 콘솔 코드의 하드코딩 '.local' 기본값으로 떨어집니다."\
  $'\n'"→ **로컬에서는 hosts 파일과 Traefik alias 덕에 통과하고, 클라우드에서만 터집니다.**"\
  $'\n'"   컨테이너는 전부 healthy, 콘솔도 뜨고, 도메인 운영 섹션만 죽습니다."\
  $'\n'"→ infra/demo/demo.env 에 <domain>.DEMO_DOMAIN 형태로 추가하세요."

# demo.env 에 있어도 compose 가 이름을 안 적으면 컨테이너에 도달하지 않는다.
missing_compose=""
for k in $console_keys; do
  grep -qE "^[[:space:]]+${k}:" "$console_compose" || missing_compose="$missing_compose $k"
done

[ -z "$missing_compose" ] || fail "콘솔 compose 가 이름을 적지 않은 키(= 컨테이너에 도달하지 않음):"\
  $'\n'"$(printf '  %s\n' $missing_compose)"\
  $'\n'"→ 셸 env 는 **compose 가 명시적으로 보간한 자리에만** 들어갑니다. demo.env 에 값을"\
  $'\n'"   넣어도 이 목록에 없으면 그 값은 조용히 버려집니다 — 값이 있는데 무시되는"\
  $'\n'"   상태라 진단이 특히 어렵습니다."

ok "콘솔 '.local' 기본값 키 $(printf '%s\n' $console_keys | wc -l | tr -d ' ') 개가 demo.env + compose 양쪽에 있다"


# ---------------------------------------------------------------------------
echo "[verify] (w) 모든 OIDC 리소스 서버가 자기 JWKS 호스트를 해소할 수 있는가"
# ---------------------------------------------------------------------------
# 근거(MONO-507): 리소스 서버는 디코드 시점에 JWKS 를 **실제로 fetch** 한다. 그 주소를
# 해소하지 못하면 Spring 은 UnknownHost 를 **fail-closed 로 401 "Authentication
# required"** 로 바꾼다 — 즉 **연결 결함이 인증 판정으로 위장한다**. 실제로 겪은 모양:
#
#   base 토큰(tenant_id=iam)    → 403 TENANT_FORBIDDEN   ← 엣지의 테넌트 게이트
#   assumed 토큰(tenant_id=데모) → 401 UNAUTHORIZED       ← 게이트 통과 후 뒤에서 사망
#
# 403 을 받으려면 엣지에서 죽어야 하고 401 을 받으려면 엣지를 **통과**해야 한다. 그래서
# 401 은 토큰이 나쁘다는 증거가 아니라 좋다는 증거였는데, 두 상태 코드를 나란히 놓기
# 전까지는 정반대로 읽혔다. 원인은 데모가 주입하는 `iam-auth-service` 가 traefik-net
# 위에만 있는 alias 인데 백엔드 19개는 자기 프로젝트 사설망에만 있었다는 것이다.
#
# 왜 "오버레이 파일이 존재하는가" 를 묻지 않는가
# ---------------------------------------------------------------------------
# 그건 대리지표다 — 파일이 있어도 서비스 하나를 빠뜨리면 통과하고, 새 프로젝트가
# 들어오면 아무 말도 안 한다. 이 가드는 **성질 자체**를 묻는다: 렌더된 compose 에서
# 각 리소스 서버가 붙은 네트워크들 위의 이름 집합을 만들고, 그 서비스가 실제로 설정된
# JWKS URL 의 호스트가 그 집합 안에 있는지 본다. 그래서 traefik-net 이 아닌 다른
# 방법으로 도달성을 확보해도 이 가드는 옳게 통과한다.
#
# 커버리지의 한계를 여기 적어 둔다(조용한 축소 금지)
# ---------------------------------------------------------------------------
# 리소스 서버 여부는 **저장소 사실**(application.yml 이 `jwk-set-uri` 를 선언하는가)로
# 판정하고, compose 의 `build.context` 경로로 모듈↔서비스를 잇는다. 반면 사용할 호스트는
# compose 가 주입한 `*JWK*` env 에서 읽고, 없으면 데모의 정본($JWT_JWKS_URI)으로 본다.
# application.yml 안의 다단 기본값 체인까지 해석하지는 않는다 — 그 경우는 정본으로
# 근사하며, 정본과 다른 기본값을 쓰는 서비스는 이 가드의 사각지대다.
w_names="$(mktemp)"; w_rs="$(mktemp)"; w_svc="$(mktemp)"
trap 'rm -f "$names_file" "$ports_file" "$w_names" "$w_rs" "$w_svc"' EXIT

# (1) 저장소 사실 — jwk-set-uri 를 선언하는 앱 모듈 (projects/<p>/apps/<module>)
for f in "$ROOT"/projects/*/apps/*/src/main/resources/application.yml; do
  [ -r "$f" ] || continue
  grep -qE '^[[:space:]]*jwk-set-uri:' "$f" || continue
  m="${f#"$ROOT"/projects/}"; m="${m%%/src/*}"          # <project>/apps/<module>
  printf '%s\n' "$m"
done | sort -u > "$w_rs"
[ -s "$w_rs" ] || fail "(w) 리소스 서버를 한 개도 추출하지 못했습니다 — 탐지식이 깨졌습니다(0건은 '없음'이 아닙니다)."

# (2) 렌더 사실 — 서비스별 networks / aliases / container_name / JWKS 호스트 / 모듈
for p in "${!COMPOSE[@]}"; do
  render "$p" | awk -v proj="$p" '
    /^[a-z]+:/            { sec = $1; sub(/:$/, "", sec); svc = ""; blk = "" }
    sec != "services"     { next }
    /^  [a-zA-Z0-9._-]+:$/ { svc = $1; sub(/:$/, "", svc); blk = ""; net = ""; next }
    svc == ""             { next }
    /^    [a-zA-Z0-9._-]+:/ { blk = $1; sub(/:$/, "", blk) }
    /^    container_name:/  { print "C|" proj "|" svc "|" $2; next }
    /^      context:/ {
      if (blk == "build" && match($2, /projects\/[^/]+\/apps\/[^/\\]+$/)) {
        mod = substr($2, RSTART + length("projects/")); gsub(/\\/, "/", mod); print "M|" proj "|" svc "|" mod
      }
      # Windows 렌더는 백슬래시 경로다 — 그 형태도 잡는다.
      if (blk == "build" && match($2, /projects\\[^\\]+\\apps\\[^\\]+$/)) {
        mod = substr($2, RSTART + length("projects\\")); gsub(/\\/, "/", mod); print "M|" proj "|" svc "|" mod
      }
      next
    }
    blk == "environment" && /^      [A-Za-z0-9_]+:/ {
      k = $1; sub(/:$/, "", k)
      if (k ~ /JWK/) { v = $2; gsub(/"/, "", v)
        if (v ~ /^https?:\/\//) { h = v; sub(/^https?:\/\//, "", h); sub(/[:\/].*$/, "", h)
          print "J|" proj "|" svc "|" h }
      }
      next
    }
    blk == "networks" && /^      [a-zA-Z0-9._-]+:/ { net = $1; sub(/:$/, "", net); print "N|" proj "|" svc "|" net; next }
    blk == "networks" && /^          - / && net != "" { print "A|" proj "|" svc "|" net "|" $2; next }
  '
done > "$w_svc"
# traefik 컨테이너의 alias 도 같은 fabric 의 이름이다.
render traefik | awk '
  /^[a-z]+:/ { sec = $1; sub(/:$/, "", sec); svc = "" }
  sec != "services" { next }
  /^  [a-zA-Z0-9._-]+:$/ { svc = $1; sub(/:$/, "", svc); blk = ""; net = "" ; next }
  /^    [a-zA-Z0-9._-]+:/ { blk = $1; sub(/:$/, "", blk) }
  blk == "networks" && /^      [a-zA-Z0-9._-]+:/ { net = $1; sub(/:$/, "", net); print "N|traefik|" svc "|" net; next }
  blk == "networks" && /^          - / && net != "" { print "A|traefik|" svc "|" net "|" $2; next }
' >> "$w_svc"

# (3) 네트워크별 해소 가능한 이름 집합.
#     `traefik-net` 만 전역(external 공유 fabric)이고 나머지는 프로젝트 로컬이다 —
#     그래서 키를 그렇게 만든다. 여기를 뒤집으면 가드가 도달성을 과대평가한다.
awk -F'|' '
  $1 == "N" { key = ($4 == "traefik-net") ? "traefik-net" : $2 "/" $4; print key "\t" $3 }
  $1 == "A" { key = ($4 == "traefik-net") ? "traefik-net" : $2 "/" $4; print key "\t" $5 }
' "$w_svc" | sort -u > "$w_names"
# container_name 도 그 서비스가 붙은 모든 네트워크에서 해소된다.
awk -F'|' '$1 == "C" { cn[$2 "|" $3] = $4 }
           $1 == "N" { key = ($4 == "traefik-net") ? "traefik-net" : $2 "/" $4
                       if (($2 "|" $3) in cn) print key "\t" cn[$2 "|" $3] }' "$w_svc" | sort -u >> "$w_names"

# (4) 판정
w_default_host="$(printf '%s' "${JWT_JWKS_URI:-}" | sed -E 's#^https?://##; s#[:/].*$##')"
[ -n "$w_default_host" ] || fail "(w) demo.env 의 JWT_JWKS_URI 에서 호스트를 뽑지 못했습니다 (값='${JWT_JWKS_URI:-}')."
w_checked=0; w_bad=""
while IFS='|' read -r _ p svc mod; do
  grep -qxF "$mod" "$w_rs" || continue                    # 리소스 서버가 아님
  host="$(awk -F'|' -v p="$p" -v s="$svc" '$1=="J" && $2==p && $3==s { print $4; exit }' "$w_svc")"
  [ -n "$host" ] || host="$w_default_host"                # compose 가 안 주입 → 정본으로 본다
  reachable=0
  while IFS='|' read -r _ _ _ net; do
    key="traefik-net"; [ "$net" = "traefik-net" ] || key="$p/$net"
    grep -qxF "$(printf '%s\t%s' "$key" "$host")" "$w_names" && { reachable=1; break; }
  done < <(awk -F'|' -v p="$p" -v s="$svc" '$1=="N" && $2==p && $3==s' "$w_svc")
  w_checked=$((w_checked + 1))
  [ "$reachable" = 1 ] || w_bad="$w_bad"$'\n'"  $p:$svc ($mod) → JWKS 호스트 '$host' 가 이 서비스의 네트워크 어디에도 없습니다"
done < <(awk -F'|' '$1 == "M"' "$w_svc")

[ "$w_checked" -gt 0 ] || fail "(w) 데모 compose 에서 리소스 서버 서비스를 한 개도 매칭하지 못했습니다 — build.context ↔ 모듈 조인이 깨졌습니다."
[ -z "$w_bad" ] || fail "JWKS 를 fetch 할 수 없는 리소스 서버가 있습니다:$w_bad"\
  $'\n'"→ 이 서비스들은 **모든 요청을 401 \"Authentication required\" 로 떨굽니다.** 토큰이 완벽해도 그렇습니다"\
  $'\n'"   — Spring 이 JWKS fetch 실패(UnknownHost)를 fail-closed 로 401 로 바꾸기 때문입니다."\
  $'\n'"→ 게이트웨이는 토큰을 **수락한 뒤** 뒤로 넘기므로, 증상은 '엣지가 좋은 토큰을 거부한다' 로 보입니다."\
  $'\n'"→ 해당 프로젝트의 infra/demo/<slug>-identity.override.yml 에 그 서비스를 추가하고"\
  $'\n'"   infra/demo/projects.sh 의 COMPOSE[<slug>] 에 그 파일이 들어 있는지 확인하세요."
ok "리소스 서버 ${w_checked}개 전부 자기 JWKS 호스트를 해소 가능 (선언 모듈 $(wc -l < "$w_rs" | tr -d ' ')개 중 데모에 뜨는 것)"

# ---------------------------------------------------------------------------
echo "[verify] (x) 결제 mock 이 프런트·백엔드 양쪽에서 같은 상태인가"
# ---------------------------------------------------------------------------
# 근거(TASK-BE-572): 데모 결제는 **두 곳**이 동의해야 성립한다 —
#   payment-service `SPRING_PROFILES_ACTIVE` 에 `demo-pg` (mock PG 가 승인)
#   web-store `DEMO_PAYMENT_MOCK=1`        (체크아웃이 Toss SDK 를 건너뛴다)
# 한쪽만 켜지면 조용히 깨진다:
#   백엔드만 → 프런트가 더미 키로 Toss SDK 를 로드하다 실패 배너를 띄운다.
#   프런트만 → 지어낸 paymentKey 를 실 Toss 어댑터가 거부해 승인이 죽는다.
# 어느 쪽도 **기동 시점엔 아무 신호가 없고** 결제 버튼을 눌러야 드러난다.
#
# 그리고 `prod` 와 `demo-pg` 가 함께 렌더되면 payment-service 는 부팅에 실패한다
# (DemoPgProfileGuard). 그 실패는 옳지만, compose 렌더에서 미리 잡는 편이 훨씬 싸다.
#
# 술어는 **렌더된 compose** 다(demo.env 를 source 한 상태로 이 스크립트가 이미 실행 중이므로
# 데모가 실제로 띄우는 값 그대로다). 두 키를 손으로 나열하지 않고 서비스에서 뽑는다.
x_render="$(render ecommerce)"
x_profiles="$(printf '%s' "$x_render" | awk '
  /^[a-z]+:/ { sec = $1; sub(/:$/, "", sec); svc = "" }
  sec != "services" { next }
  /^  [a-zA-Z0-9._-]+:$/ { svc = $1; sub(/:$/, "", svc); blk = ""; next }
  /^    [a-zA-Z0-9._-]+:/ { blk = $1; sub(/:$/, "", blk) }
  svc == "payment-service" && blk == "environment" && /^      SPRING_PROFILES_ACTIVE:/ {
    v = $2; gsub(/"/, "", v); print v }
')"
x_flag="$(printf '%s' "$x_render" | awk '
  /^[a-z]+:/ { sec = $1; sub(/:$/, "", sec); svc = "" }
  sec != "services" { next }
  /^  [a-zA-Z0-9._-]+:$/ { svc = $1; sub(/:$/, "", svc); blk = ""; next }
  /^    [a-zA-Z0-9._-]+:/ { blk = $1; sub(/:$/, "", blk) }
  svc == "web-store" && blk == "environment" && /^      DEMO_PAYMENT_MOCK:/ {
    v = $2; gsub(/"/, "", v); print v }
')"

# 추출 실패를 통과로 보고하지 않는다. 키가 사라지면(= 데모 결제 배선이 통째로 빠지면)
# 두 값이 모두 빈 문자열이 되어 "둘 다 꺼짐" 으로 조용히 합격할 수 있다.
printf '%s' "$x_render" | grep -qE '^      SPRING_PROFILES_ACTIVE:' \
  || fail "(x) ecommerce 렌더에서 payment-service 의 SPRING_PROFILES_ACTIVE 를 찾지 못했습니다 — 탐지식이 깨졌습니다."
printf '%s' "$x_render" | grep -qE '^      DEMO_PAYMENT_MOCK:' \
  || fail "(x) ecommerce 렌더에서 web-store 의 DEMO_PAYMENT_MOCK 를 찾지 못했습니다"\
    $'\n'"→ web-store.environment 에 \`DEMO_PAYMENT_MOCK=\${DEMO_PAYMENT_MOCK:-}\` 가 있어야 합니다."\
    $'\n'"   compose 는 자기가 이름을 적은 변수만 컨테이너에 넣습니다 — demo.env 값만으로는 도달하지 않습니다."

case ",$x_profiles," in
  *,demo-pg,*) x_back=1 ;;
  *)           x_back=0 ;;
esac
[ "$x_flag" = "1" ] && x_front=1 || x_front=0

[ "$x_back" = "$x_front" ] || fail "결제 mock 설정이 한쪽만 켜져 있습니다:"\
  $'\n'"  payment-service SPRING_PROFILES_ACTIVE = '$x_profiles'  (demo-pg: $x_back)"\
  $'\n'"  web-store       DEMO_PAYMENT_MOCK      = '$x_flag'  (on: $x_front)"\
  $'\n'"→ 백엔드만 켜짐 = 프런트가 더미 키로 Toss SDK 를 로드하다 실패 배너를 띄웁니다."\
  $'\n'"→ 프런트만 켜짐 = 지어낸 paymentKey 를 실 Toss 어댑터가 거부해 결제 승인이 죽습니다."\
  $'\n'"→ 둘 다 infra/demo/demo.env 에서 설정하세요 (ECOMMERCE_PAYMENT_PROFILES / DEMO_PAYMENT_MOCK)."

case ",$x_profiles," in
  *,prod,*)
    [ "$x_back" = "0" ] || fail "payment-service 프로파일에 prod 와 demo-pg 가 함께 있습니다: '$x_profiles'"\
      $'\n'"→ demo-pg 는 **돈을 받지 않고 모든 결제를 승인**합니다. 프로덕션 배포에 실려서는 안 됩니다."\
      $'\n'"→ payment-service 는 이 조합에서 부팅에 실패합니다(DemoPgProfileGuard). 여기서 먼저 막습니다."
    ;;
esac

ok "결제 mock 정합 (payment-service='${x_profiles}' ↔ web-store DEMO_PAYMENT_MOCK='${x_flag}')"

# ---------------------------------------------------------------------------
echo "[verify] (x2) 팬 결제 mock 이 프런트·백엔드 양쪽에서 같은 상태인가"
# ---------------------------------------------------------------------------
# 근거(TASK-FAN-FE-015): 같은 성질을 팬에도 요구하되 **술어가 뒤집힌다.**
#
#   ecommerce — 실 Toss 어댑터가 기본. `demo-pg` 프로파일을 **켜야** 목이 된다.
#               ⇒ 불변식: 프런트 플래그 ON ⟺ 프로파일에 `demo-pg` 있음        (가드 x)
#   fan       — 목이 기본이다. `MockPaymentGatewayAdapter` 가 `@Profile("!portone")`
#               이므로 `portone` 을 **켜야** 실 PG 가 된다.
#               ⇒ 불변식: 프런트 플래그 ON ⟺ 프로파일에 `portone` **없음**    (여기)
#
# 🔴 그래서 (x) 의 술어를 그대로 복사하면 **정반대를 단언**하고 팬의 정상 데모 설정이
# RED 가 된다. 이 티켓이 고친 상태가 그 증거다 — 팬 백엔드는 이미 목이었고 프런트만 몰랐다.
#
# 한쪽만 켜지면 조용히 깨지는 것은 같다:
#   프런트만 켜짐(= portone 도 켜짐) → 지어낸 paymentId 를 실 PortOne 어댑터가 거부한다.
#   프런트만 꺼짐(= 이 티켓 이전)    → 백엔드 목이 승인할 준비가 됐는데도 프런트가
#                                     'PortOne 키 미설정' 으로 요청 자체를 안 보낸다.
# 어느 쪽도 기동 시점엔 신호가 없고 구독 버튼을 눌러야 드러난다.
x2_render="$(render fan)"
x2_profiles="$(printf '%s' "$x2_render" | awk '
  /^[a-z]+:/ { sec = $1; sub(/:$/, "", sec); svc = "" }
  sec != "services" { next }
  /^  [a-zA-Z0-9._-]+:$/ { svc = $1; sub(/:$/, "", svc); blk = ""; next }
  /^    [a-zA-Z0-9._-]+:/ { blk = $1; sub(/:$/, "", blk) }
  svc == "membership-service" && blk == "environment" && /^      SPRING_PROFILES_ACTIVE:/ {
    v = $2; gsub(/"/, "", v); print v }
')"
x2_flag="$(printf '%s' "$x2_render" | awk '
  /^[a-z]+:/ { sec = $1; sub(/:$/, "", sec); svc = "" }
  sec != "services" { next }
  /^  [a-zA-Z0-9._-]+:$/ { svc = $1; sub(/:$/, "", svc); blk = ""; next }
  /^    [a-zA-Z0-9._-]+:/ { blk = $1; sub(/:$/, "", blk) }
  svc == "fan-platform-web" && blk == "environment" && /^      DEMO_PAYMENT_MOCK:/ {
    v = $2; gsub(/"/, "", v); print v }
')"

# (x) 와 같은 이유로 추출 실패를 통과로 보고하지 않는다. 키가 사라지면 두 값이 모두 빈
# 문자열이 되는데, 팬에서는 그 조합이 **바로 이 티켓이 고친 결함** 이라 특히 위험하다.
printf '%s' "$x2_render" | grep -qE '^      SPRING_PROFILES_ACTIVE:' \
  || fail "(x2) fan 렌더에서 membership-service 의 SPRING_PROFILES_ACTIVE 를 찾지 못했습니다 — 탐지식이 깨졌습니다."
printf '%s' "$x2_render" | grep -qE '^      DEMO_PAYMENT_MOCK:' \
  || fail "(x2) fan 렌더에서 fan-platform-web 의 DEMO_PAYMENT_MOCK 를 찾지 못했습니다"\
    $'\n'"→ fan-platform-web.environment 에 \`DEMO_PAYMENT_MOCK: \${DEMO_PAYMENT_MOCK:-}\` 가 있어야 합니다."\
    $'\n'"   compose 는 자기가 이름을 적은 변수만 컨테이너에 넣습니다 — demo.env 값만으로는 도달하지 않습니다."

case ",$x2_profiles," in
  *,portone,*) x2_real=1 ;;
  *)           x2_real=0 ;;
esac
[ "$x2_flag" = "1" ] && x2_front=1 || x2_front=0
# 목이 기본이므로 "백엔드가 목인가" = "portone 이 꺼져 있는가".
x2_back_mock=$(( 1 - x2_real ))

[ "$x2_back_mock" = "$x2_front" ] || fail "팬 결제 mock 설정이 한쪽만 켜져 있습니다:"\
  $'\n'"  membership-service SPRING_PROFILES_ACTIVE = '$x2_profiles'  (portone: $x2_real ⇒ 목: $x2_back_mock)"\
  $'\n'"  fan-platform-web   DEMO_PAYMENT_MOCK      = '$x2_flag'  (on: $x2_front)"\
  $'\n'"→ 프런트만 켜짐 = 지어낸 paymentId 를 실 PortOne 어댑터가 거부해 구독이 죽습니다."\
  $'\n'"→ 프런트만 꺼짐 = 백엔드 목이 승인할 준비가 됐는데도 프런트가 'PortOne 키 미설정' 으로"\
  $'\n'"   요청 자체를 보내지 않습니다 (TASK-FAN-FE-015 가 고친 상태가 정확히 이것입니다)."\
  $'\n'"→ 팬은 극성이 ecommerce 와 반대입니다 — 목이 기본이고 portone 이 opt-in 입니다."

ok "팬 결제 mock 정합 (membership-service='${x2_profiles}' ↔ fan-platform-web DEMO_PAYMENT_MOCK='${x2_flag}')"

# ---------------------------------------------------------------------------
# (y) 시드의 직접-DB 는 반드시 `dbexec --why` 를 거친다 (TASK-MONO-506 AC-1)
# ---------------------------------------------------------------------------
# AC-1 은 "사유 없는 직접-DB 0건" 을 요구한다. 그 성질은 두 겹으로 지킨다:
#   1) `lib.sh` 의 `dbexec` 가 `--why` 없이는 **실행을 거부**한다(런타임 게이트)
#   2) 이 가드가 그 게이트를 **우회하는 경로**를 막는다 — 시드 스크립트가 helper 를
#      거치지 않고 `docker exec … psql/mysql` 을 직접 부르면 1)이 무력해진다.
#
# 왜 "주석에 사유가 있는가" 를 검사하지 않는가: 그것이 대리지표다. 사유가 코드
# **인자**여야 빠뜨리는 것이 불가능하다. 여기서는 우회 여부만 본다.
echo "[verify] (y) 시드 스크립트가 dbexec 를 우회해 DB 를 직접 건드리지 않는가"
y_seed_dir="$ROOT/infra/demo/seed"
if [ -d "$y_seed_dir" ]; then
  y_offenders=""
  for f in "$y_seed_dir"/seed-*.sh; do
    [ -f "$f" ] || continue
    # lib.sh 자신은 예외다 — `dbexec`/`dbquery` 의 구현이 거기 있다.
    if grep -nE 'docker[[:space:]]+exec[^|]*\b(psql|mysql)\b' "$f" >/dev/null 2>&1; then
      y_offenders="$y_offenders $(basename "$f")"
    fi
  done
  [ -z "$y_offenders" ] || fail "시드 스크립트가 docker exec psql/mysql 을 직접 호출합니다:$y_offenders"\
    $'\n'"→ `dbexec --why \"<사유>\"` 를 쓰십시오 (infra/demo/seed/lib.sh)."\
    $'\n'"→ 사유를 **인자로** 요구하는 이유: 주석 규약은 깜빡할 수 있지만 필수 인자는 그럴 수 없습니다."

  # 드라이버가 실제로 존재하고, demo-up.sh 가 그것을 부르는가 (배선을 본다 — 로직이 아니라).
  [ -f "$y_seed_dir/seed.sh" ] || fail "infra/demo/seed/seed.sh 가 없습니다 — 도메인 시드 드라이버가 사라졌습니다"
  grep -q 'seed/seed\.sh' "$ROOT/infra/demo/demo-up.sh" \
    || fail "demo-up.sh 가 seed/seed.sh 를 호출하지 않습니다"\
       $'\n'"→ 시드 스크립트가 저장소에 있어도 기동 경로에서 불리지 않으면 화면은 그대로 빕니다."
  grep -q 'DEMO_SEED' "$y_seed_dir/seed.sh" \
    || fail "seed.sh 에 DEMO_SEED 스위치가 없습니다 (AMI 재굽기·디버깅에서 끌 수 있어야 합니다)"

  y_n=$(ls "$y_seed_dir"/seed-*.sh 2>/dev/null | wc -l | tr -d ' ')
  ok "시드 배선 유지 (도메인 시드 ${y_n}개 · 전부 dbexec 경유 · demo-up.sh 가 드라이버 호출 · DEMO_SEED 스위치 존재)"
else
  fail "infra/demo/seed/ 가 없습니다 — TASK-MONO-506 의 시드 디렉터리가 사라졌습니다"
fi

# ---------------------------------------------------------------------------
echo "[verify] (z) 도메인 헬스 스냅샷 발행 경로가 끊기지 않았는가"
# ---------------------------------------------------------------------------
# 근거(MONO-477): 컨트롤 플레인의 `GET /domains` 는 SSM 파라미터를 **읽기만** 한다.
# 그 값을 채우는 것은 인스턴스의 demo-status.timer 다. 발행자가 없으면 파라미터는
# terraform 초기값 `{}` 에 머물고, 페이지의 8개 배지는 스택이 멀쩡히 떠 있어도
# 영원히 "확인 중" 이다. **아무것도 에러를 내지 않는다** — 이 저장소가 반복해서
# 데인 무증상 실패의 모양이다(96 컨테이너 healthy + 도달 불가).
#
# 이 가드가 static 구역에 있는 것은 의도다. --live 뒤에 두면 CI 의 "Demo wrapper
# smoke" 와 packer 7단계가 이것을 **한 번도 돌리지 않는다** — 러너 없는 가드는 썩는다.
z_pub="$ROOT/infra/demo/demo-status-publish.sh"
z_svc="$ROOT/infra/demo/demo-status.service"
z_tmr="$ROOT/infra/demo/demo-status.timer"
z_pkr="$ROOT/infra/demo/aws/packer/demo-ami.pkr.hcl"
z_tf="$ROOT/infra/demo/aws/terraform/main.tf"
z_lam="$ROOT/infra/demo/aws/terraform/lambda/handler.py"

[ -f "$z_pub" ] || fail "infra/demo/demo-status-publish.sh 가 없습니다 — 헬스 스냅샷을 채울 주체가 없습니다."\
  $'\n'"→ Lambda 는 읽기만 합니다. 발행자가 없으면 /domains 는 terraform 초기값 {} 만 돌려줍니다."
[ -f "$z_svc" ] || fail "infra/demo/demo-status.service 가 없습니다 (유닛은 저장소가 소유해야 합니다 — MONO-366)."
[ -f "$z_tmr" ] || fail "infra/demo/demo-status.timer 가 없습니다."

# (1) 발행자가 실제로 생산자와 발행처를 잇는가. 주석은 걷어내고 본다 —
#     이 스크립트의 헤더가 `aws ssm put-parameter` 를 **설명하느라** 언급하므로
#     순진한 grep 은 본문을 통째로 지워도 자기 주석에 매치해 통과한다((k)/(n) 의 함정).
z_pub_body="$(sed 's/#.*//' "$z_pub")"
printf '%s\n' "$z_pub_body" | grep -q 'demo-status\.sh' \
  || fail "demo-status-publish.sh 가 demo-status.sh 를 호출하지 않습니다 (주석 제외 본문 기준)."
printf '%s\n' "$z_pub_body" | grep -q 'put-parameter' \
  || fail "demo-status-publish.sh 가 put-parameter 를 호출하지 않습니다 (주석 제외 본문 기준)."

# (2) 유닛이 발행자를 부르는가 + 타이머가 그 유닛을 부르는가.
z_svc_exec="$(sed 's/#.*//' "$z_svc" | grep -E '^[[:space:]]*ExecStart=' || true)"
case "$z_svc_exec" in
  *demo-status-publish.sh*) : ;;
  *) fail "demo-status.service 의 ExecStart 가 demo-status-publish.sh 를 부르지 않습니다: ${z_svc_exec:-<없음>}" ;;
esac
sed 's/#.*//' "$z_tmr" | grep -qE '^[[:space:]]*Unit=demo-status\.service' \
  || fail "demo-status.timer 가 Unit=demo-status.service 를 가리키지 않습니다."

# (3) 🔴 AccuracySec 이 없으면 '30초 주기' 는 거짓이다.
#     systemd 기본 AccuracySec 은 1분이라 커널이 타이머를 뭉쳐 깨운다 — 유닛에는
#     30s 라고 적혀 있고 실제 주기는 ~1분이 된다. 페이지가 표시 지연을 정직하게
#     적으라는 티켓 요구의 근거가 여기서 무너지므로, 선언과 실제를 벌리지 않는다.
sed 's/#.*//' "$z_tmr" | grep -qE '^[[:space:]]*AccuracySec=' \
  || fail "demo-status.timer 에 AccuracySec 이 없습니다 — systemd 기본값 1분이 30초 주기를 삼킵니다."\
     $'\n'"→ 유닛이 선언한 주기와 실제 주기가 달라집니다(선언은 아무것도 강제하지 않는다)."

# (4) Packer 가 둘 다 **저장소에서** 설치하고, 서비스가 아니라 **타이머**를 enable 하는가.
#     서비스를 enable 하면 부팅 때 한 번 돌고 끝난다(주기 발행이 사라진다).
if [ -f "$z_pkr" ]; then
  grep -q '/opt/monorepo-lab/infra/demo/demo-status.service' "$z_pkr" \
    || fail "packer 가 demo-status.service 를 저장소 경로에서 설치하지 않습니다."
  grep -q '/opt/monorepo-lab/infra/demo/demo-status.timer' "$z_pkr" \
    || fail "packer 가 demo-status.timer 를 저장소 경로에서 설치하지 않습니다."
  grep -qE 'systemctl enable demo-status\.timer' "$z_pkr" \
    || fail "packer 가 demo-status.timer 를 enable 하지 않습니다."\
       $'\n'"→ demo-status.service 를 enable 하면 부팅 시 1회만 돌고 주기 발행이 사라집니다."
  # 발행자는 aws CLI 로 씁니다. AMI 가 CLI 를 설치하지 않으면 스크립트는 매 30초
  # 죽고, 증상은 "페이지 배지가 안 뜬다" 라 원인이 페이지 쪽처럼 보입니다.
  #
  # 🔴 이 술어는 한 번 **결함을 핀으로 고정했다.** 처음 판(#3382)은
  #    `apt-get install -y awscli` 를 요구했는데 **noble 에는 그 패키지가 없다**
  #    (ubuntu:24.04 실측: rc=100, "Package 'awscli' has no installation candidate";
  #    universe 는 켜져 있으므로 저장소 구성 문제가 아니다). 가드는 초록이었고 그 줄은
  #    한 번도 실행된 적이 없었다 — 가드가 지킨 것은 동작이 아니라 **내가 적은 문자열**
  #    이었다. 그래서 지금은 apt 저장소 구성에 의존하지 않는 공식 설치기를 요구한다.
  grep -q 'awscli-exe-linux' "$z_pkr" \
    || fail "packer 가 AWS CLI 공식 설치기를 내려받지 않습니다 — demo-status-publish.sh 가 매 주기 죽습니다."\
       $'\n'"→ apt 의 awscli 패키지는 Ubuntu noble 에 존재하지 않습니다(no installation candidate)."\
       $'\n'"   awscli-exe-linux-x86_64.zip 공식 설치기를 쓰세요."
  grep -q 'aws --version' "$z_pkr" \
    || fail "packer 가 설치 직후 aws --version 으로 확증하지 않습니다."\
       $'\n'"→ 설치가 조용히 실패하면 AMI 는 CLI 없이 완성되고, 그 사실은 라이브 인스턴스에서야 드러납니다."
fi

# (5) 🔴 파라미터 이름은 **세 곳**에 있다 — 발행자 · Lambda 기본값 · terraform.
#     한 사실이 세 곳에 있으면 한 곳만 고쳐지고, 어긋난 순간 발행자는 아무도 읽지 않는
#     파라미터에 성실하게 쓴다. 전부 초록이고 배지만 안 뜬다. 여기서 셋을 대조한다.
#     추출 실패는 통과가 아니라 실패다((x) 와 같은 이유) — 못 읽었으면 모르는 것이다.
z_pub_param="$(printf '%s\n' "$z_pub_body" \
  | sed -n 's/^HEALTH_PARAM="\${HEALTH_PARAM:-\(.*\)}"$/\1/p' | head -1)"
[ -n "$z_pub_param" ] || fail "demo-status-publish.sh 에서 HEALTH_PARAM 기본값을 추출하지 못했습니다"\
  $'\n'"→ 형태가 바뀌었다면 이 가드도 함께 고치세요. 추출 실패를 통과로 보고하지 않습니다."

if [ -f "$z_lam" ]; then
  z_lam_param="$(grep -E '^HEALTH_PARAM[[:space:]]*=' "$z_lam" \
    | sed -n 's/.*,[[:space:]]*"\([^"]*\)").*/\1/p' | head -1)"
  [ -n "$z_lam_param" ] || fail "handler.py 에서 HEALTH_PARAM 기본값을 추출하지 못했습니다."
  [ "$z_lam_param" = "$z_pub_param" ] \
    || fail "헬스 파라미터 이름이 어긋났습니다 — 발행자='$z_pub_param' ↔ handler.py='$z_lam_param'"\
       $'\n'"→ 발행자는 아무도 읽지 않는 이름에 쓰고, /domains 는 영원히 비어 있게 됩니다."
fi

if [ -f "$z_tf" ]; then
  # health_param = "/${var.project}/domains-health" + variable "project" 의 default
  z_tf_suffix="$(grep -E '^[[:space:]]*health_param[[:space:]]*=' "$z_tf" \
    | sed -n 's/.*"\/\${var\.project}\(.*\)".*/\1/p' | head -1)"
  z_tf_project="$(sed -n '/^variable "project"/,/^}/p' \
    "$ROOT/infra/demo/aws/terraform/variables.tf" \
    | sed -n 's/^[[:space:]]*default[[:space:]]*=[[:space:]]*"\(.*\)".*/\1/p' | head -1)"
  [ -n "$z_tf_suffix" ] && [ -n "$z_tf_project" ] \
    || fail "terraform 에서 health_param 을 조립하지 못했습니다 (suffix='$z_tf_suffix' project='$z_tf_project')."
  [ "/$z_tf_project$z_tf_suffix" = "$z_pub_param" ] \
    || fail "헬스 파라미터 이름이 어긋났습니다 — 발행자='$z_pub_param' ↔ terraform='/$z_tf_project$z_tf_suffix'"\
       $'\n'"→ terraform 이 만드는 파라미터와 인스턴스가 쓰는 파라미터가 다릅니다."\
       $'\n'"   IAM 정책도 terraform 쪽 ARN 으로 좁혀져 있으므로 put-parameter 가 AccessDenied 로 죽습니다."
fi

ok "헬스 발행 경로 유지 (타이머 → 유닛 → 발행자 → demo-status.sh → SSM '$z_pub_param' · 3곳 이름 일치)"

# ---------------------------------------------------------------------------
echo "[verify] (z2) packer 가 정적 가드의 도구 요구를 전부 설치하는가"
# ---------------------------------------------------------------------------
# 근거(2026-08-17 실측): AMI 빌드 7단계는 **이 스크립트를 AMI 안에서** 돌린다. 따라서
# 정적 구간이 요구하는 도구는 1단계가 전부 설치해 둬야 한다. 안 하면 빌드는 이미지
# 8개를 다 굽고 **20분을 태운 뒤** 7단계에서 죽는다.
#
# 실제로 그렇게 죽었다. 가드 (t) 는 페이지의 `demoHost()` 를 `node -e` 로 **실행해서**
# 부팅 파생과 대조하는데(MONO-389), packer 는 node 를 설치한 적이 없다. (t) 가 추가된
# 날은 마지막 성공 bake **다음 날**이라, 그 사이 아무도 굽지 않는 동안 잠복해 있었다.
#
# 이 가드가 개별 도구 이름을 박지 않는 것이 핵심이다 — 그러면 다음에 추가되는 도구는
# 또 못 잡는다. **이 스크립트 자신의 정적 구간에서 `command -v` 선언을 뽑아** packer 의
# 설치 목록과 대조한다. 새 요구가 생기면 자동으로 범위에 들어온다.
z2_pkr="$ROOT/infra/demo/aws/packer/demo-ami.pkr.hcl"
z2_self="$ROOT/infra/demo/verify-demo-wrapper.sh"
if [ -f "$z2_pkr" ] && [ -f "$z2_self" ]; then
  # 정적 구간 = 파일 처음부터 LIVE 게이트까지. 게이트를 못 찾으면 구간을 특정할 수
  # 없으므로 **통과가 아니라 실패**다((x) 와 같은 이유 — 못 읽었으면 모르는 것이다).
  z2_live="$(grep -n 'if \[ "\$LIVE" -eq 0 \]' "$z2_self" | head -1 | cut -d: -f1)"
  [ -n "$z2_live" ] || fail "(z2) 이 스크립트에서 LIVE 게이트를 찾지 못했습니다 — 정적 구간을 특정할 수 없습니다."

  z2_seen=""; z2_missing=""
  for z2_t in $(head -n "$z2_live" "$z2_self" \
                  | sed -n 's/.*command -v \([a-z0-9_-][a-z0-9_-]*\).*/\1/p' | sort -u); do
    # 도구 이름 ≠ 패키지 이름. 아는 것만 매핑하고 나머지는 동일 이름으로 본다.
    case "$z2_t" in
      node) z2_pkg="nodejs" ;;
      *)    z2_pkg="$z2_t" ;;
    esac
    z2_seen="$z2_seen $z2_t"
    grep -qE "apt-get install[^\"]*[[:space:]]$z2_pkg([[:space:]\"]|\$)" "$z2_pkr" \
      || z2_missing="$z2_missing $z2_t(패키지 $z2_pkg)"
  done

  # 추출이 0건이면 술어가 형태를 놓친 것이다. 0건을 "요구가 없다" 로 읽지 않는다.
  [ -n "$z2_seen" ] || fail "(z2) 정적 구간에서 'command -v' 선언을 하나도 추출하지 못했습니다"\
    $'\n'"→ 가드가 요구를 선언하는 형태가 바뀌었다면 이 술어도 함께 고치세요."\
    $'\n'"   추출 0건을 '의존 없음' 으로 보고하지 않습니다."

  [ -z "$z2_missing" ] || fail "packer 1단계가 정적 가드의 도구 요구를 설치하지 않습니다:$z2_missing"\
    $'\n'"→ 7단계가 이 스크립트를 AMI 안에서 돌립니다. 없으면 빌드는 이미지를 다 굽고"\
    $'\n'"   **20분 뒤** 7단계에서 죽습니다(2026-08-17 에 실제로 그렇게 죽었습니다)."\
    $'\n'"→ demo-ami.pkr.hcl 1단계에 apt 설치를 추가하고, 설치 직후 실행으로 확증하세요."\
    $'\n'"→ 패키지 존재는 noble 아카이브 인덱스로 확인하세요(packages.ubuntu.com 은 신뢰 불가 —"\
    $'\n'"   없는 awscli 에도 200 을 돌려줍니다):"\
    $'\n'"   curl -s http://archive.ubuntu.com/ubuntu/dists/noble/universe/binary-amd64/Packages.gz | zcat | grep '^Package: <pkg>$'"

  ok "packer 가 정적 가드의 도구 요구를 전부 설치함 (요구:$z2_seen)"
fi

# ---------------------------------------------------------------------------
echo "[verify] (z3) fresh clone 에서 데모 부팅이 env-preflight 을 통과하는가"
# ---------------------------------------------------------------------------
# 근거(MONO-550, 2026-08-17 실측): 재굽기한 AMI 로 apply 한 첫 부팅에서 **컨테이너 0개**.
# `check-env-preflight.sh`(MONO-548)가 `.env` 없이 뜨면 볼륨에 잘못된 자격이 각인되는
# wms·ecommerce 를 중단시켰다. **AMI 는 fresh clone 이고 `.env` 는 gitignored** 라 없다.
# 에러는 systemd 저널에만 있었고 페이지는 조용히 "전부 down" 을 그렸다.
#
# 🔴 이 가드가 **왜 워킹트리를 안 보는가**: 개발자 머신에는 `.env` 가 있다. 그 트리에서
# 물으면 술어는 언제나 초록이고, 정확히 그래서 이 결함이 로컬에서 한 번도 안 보였다.
# 그래서 **`.env` 를 애초에 복사하지 않는 임시 트리를 만들어** 그 위에서 진짜 스크립트를
# 돌린다 — fresh clone 조건이 우연이 아니라 **구성으로** 보장된다.
#
# 🔴 그리고 **대조군이 먼저다**: 프로비저닝 전에 preflight 이 rc=1 로 **막는지** 본다.
# 그게 아니면(예: 임시 트리에 compose 가 빠져서 아무것도 검사되지 않으면) 뒤이은
# "통과" 는 아무것도 증명하지 않는다 — 통과가 무효일 수 있다.
z3_prov="$ROOT/infra/demo/provision-demo-env.sh"
z3_boot="$ROOT/infra/demo/demo-boot.sh"
[ -f "$z3_prov" ] || fail "infra/demo/provision-demo-env.sh 가 없습니다 — fresh clone 에서 데모가 뜰 수 없습니다."\
  $'\n'"→ env-preflight 이 wms·ecommerce 기동을 중단시키고 부팅은 컨테이너 0개로 끝납니다(MONO-550)."

# (1) 순서 — 프로비저닝은 demo-up.sh **앞**이어야 한다. 뒤면 preflight 은 이미 지나갔고,
#     볼륨이 폴백 자격으로 초기화된 뒤 의도한 값이 나타나 영구 인증 실패가 된다.
z3_body="$(sed 's/#.*//' "$z3_boot")"
z3_pl="$(printf '%s\n' "$z3_body" | grep -n 'provision-demo-env\.sh' | head -1 | cut -d: -f1 || true)"
z3_ul="$(printf '%s\n' "$z3_body" | grep -n 'demo-up\.sh'            | head -1 | cut -d: -f1 || true)"
[ -n "$z3_pl" ] || fail "demo-boot.sh 가 provision-demo-env.sh 를 호출하지 않습니다 (주석 제외 본문 기준)."
[ -n "$z3_ul" ] || fail "demo-boot.sh 가 demo-up.sh 를 호출하지 않습니다."
[ "$z3_pl" -lt "$z3_ul" ] || fail "demo-boot.sh 가 demo-up.sh 뒤에 .env 를 프로비저닝합니다"\
  $'\n'"   (provision=L$z3_pl, demo-up=L$z3_ul) — preflight 은 이미 지나간 뒤입니다."

# (2) fresh clone 조건을 구성한다. `.env` 는 **한 번도 복사하지 않는다**.
# ⚠️ 이 블록은 `set -euo pipefail` 아래에서 돈다. `[ ... ] && cmd` 는 조건이 거짓일 때
#    상태 1 을 내고 **스크립트를 그 자리에서 죽인다** — 메시지 없이. 그리고 `ls` 는 매치가
#    0건이면 **2** 로 끝나는데, `pipefail` 이 그것을 파이프라인 상태로 올리고 대입문이
#    그 상태를 이어받는다 ⇒ `z3_leak=$(ls ... | wc -l)` 한 줄이 **exit 2, 출력 0줄**로
#    가드를 죽인다. CI 에서 실제로 그렇게 죽었다(로컬 재현은 이 줄을 빼고 돌려서 초록이었다).
#    그래서 아래는 전부 if/fi 와 glob 순회로 쓴다.
z3_tmp="$(mktemp -d)"
mkdir -p "$z3_tmp/infra"
cp -r "$ROOT/infra/demo" "$z3_tmp/infra/demo"
if [ -d "$ROOT/infra/traefik" ]; then cp -r "$ROOT/infra/traefik" "$z3_tmp/infra/traefik"; fi
for z3_d in "$ROOT"/projects/*/; do
  z3_n="$(basename "$z3_d")"
  mkdir -p "$z3_tmp/projects/$z3_n"
  for z3_f in "$z3_d"*.yml "$z3_d".env.example; do
    if [ -f "$z3_f" ]; then cp "$z3_f" "$z3_tmp/projects/$z3_n/"; fi
  done
done
# 구성이 의도대로인지 단언한다 — `.env` 가 하나라도 섞여 들어가면 이 시험은 무효다.
z3_leak=0
for z3_f in "$z3_tmp"/projects/*/.env; do
  if [ -f "$z3_f" ]; then z3_leak=$(( z3_leak + 1 )); fi
done
[ "$z3_leak" = "0" ] || { rm -rf "$z3_tmp"; fail "(z3) 임시 트리에 .env 가 ${z3_leak}개 섞였습니다 — fresh clone 조건이 깨져 시험이 무효입니다."; }
# 반대 방향도 확인한다: 아무것도 안 옮겨졌으면 뒤이은 판정은 빈 트리에 대고 묻는 것이다.
z3_ex=0
for z3_f in "$z3_tmp"/projects/*/.env.example; do
  if [ -f "$z3_f" ]; then z3_ex=$(( z3_ex + 1 )); fi
done
[ "$z3_ex" -gt 0 ] || { rm -rf "$z3_tmp"; fail "(z3) 임시 트리에 .env.example 이 0건입니다 — 트리 구성이 실패했고 이후 판정은 공허합니다."; }

# (3) 🔴 대조군 — 프로비저닝 전에는 **막혀야** 한다.
z3_before=0
bash "$z3_tmp/infra/demo/check-env-preflight.sh" "${FULL[@]}" >/dev/null 2>&1 || z3_before=$?
[ "$z3_before" -ne 0 ] || { rm -rf "$z3_tmp"; fail "(z3) 대조군 실패 — .env 없는 트리에서 preflight 이 통과했습니다."\
  $'\n'"→ 임시 트리가 실제 조건을 재현하지 못했거나(compose 누락 등) preflight 이 무력화됐습니다."\
  $'\n'"   이 경우 뒤이은 '통과' 는 아무것도 증명하지 않습니다 — 통과가 무효일 수 있습니다."; }

# (4) 프로비저닝 후에는 통과해야 한다 — 진짜 스크립트로, 진짜 조건에서.
z3_prov_rc=0
bash "$z3_tmp/infra/demo/provision-demo-env.sh" >/dev/null 2>&1 || z3_prov_rc=$?
[ "$z3_prov_rc" = "0" ] || { rm -rf "$z3_tmp"; fail "(z3) provision-demo-env.sh 가 실패했습니다 (rc=$z3_prov_rc)."; }

z3_after=0
bash "$z3_tmp/infra/demo/check-env-preflight.sh" "${FULL[@]}" >/dev/null 2>&1 || z3_after=$?
rm -rf "$z3_tmp"
[ "$z3_after" = "0" ] || fail "fresh clone 에서 데모 부팅이 여전히 env-preflight 에 막힙니다 (rc=$z3_after)"\
  $'\n'"→ 프로비저닝이 preflight 이 요구하는 것을 충족하지 못합니다. 부팅은 컨테이너 0개로 끝나고,"\
  $'\n'"   에러는 systemd 저널에만 남습니다(페이지는 조용히 '전부 down' 을 그립니다 — MONO-550)."\
  $'\n'"→ 가드를 끄는 방향으로 고치지 마세요. 막는 위험(볼륨에 각인된 자격)은 실재합니다."

ok "fresh clone 부팅이 env-preflight 을 통과 (대조군 rc=$z3_before → 프로비저닝 후 rc=0 · demo-boot 순서 L$z3_pl<L$z3_ul)"

# ---------------------------------------------------------------------------
echo "[verify] (z4) 한 도메인의 기동 실패가 나머지 도메인을 막지 않는가"
# ---------------------------------------------------------------------------
# TASK-MONO-553. 2026-08-17 실증: `/stop`→`/start` 뒤 iam-kafka 의 healthcheck 가
# 경합으로 늦게 붙었고, compose 가 `dependency failed to start` 로 포기했다.
# `demo-up.sh` 는 `set -e` 아래 있었으므로 **거기서 스크립트가 끝났다** — 나머지 7개
# 도메인은 손도 못 댔고, 재시작 정책이 되살려 둔 옛 라벨 컨테이너가 계속 서빙했다.
# 결과: 스택은 도는데 **새 주소가 전부 404**.
#
# 🔴 이 가드는 네 칸을 **모두** 본다. 첫째 칸만 보면 "실패를 `|| true` 로 삼키는" 구현과
#    구별되지 않는다 — 그리고 그 구현이야말로 이 저장소가 반복해서 당한 실패 모드다.
#      (1) 정상 경로는 여전히 초록인가          ← 대조군. 빨간 가드는 곧 꺼진다.
#      (2) 실패 뒤의 도메인도 기동을 시도하는가  ← 격리 그 자체
#      (3) 성공으로 보고하지 않는가             ← 삼킴 금지
#      (4) 실패한 도메인 이름을 대는가          ← 진단 가능성
#
# docker 는 **대역으로 바꾼다.** 진짜 스택을 띄워 kafka 를 굶기는 방식은 러너에서
# 재현 불가능하고(그게 TASK-MONO-552 다), 재현되더라도 무엇이 실패했는지 통제할 수 없다.
# 여기서 묻는 것은 "compose 가 비-0 를 냈을 때 **스크립트가** 어떻게 행동하는가" 이므로,
# 통제해야 하는 것은 정확히 compose 의 종료코드 하나다.
#
# 🔵 물리는지 확인함: 이 본문을 고침 **전**의 `demo-up.sh`(origin/main @ 4d328cfd0)에
#    대고 돌리면 (2) 에서 FAIL 한다 — console 기동 줄이 로그에 없다.
z4_tmp="$(mktemp -d)"
mkdir -p "$z4_tmp/infra" "$z4_tmp/bin"
cp -r "$ROOT/infra/demo" "$z4_tmp/infra/demo"
if [ -d "$ROOT/infra/traefik" ]; then cp -r "$ROOT/infra/traefik" "$z4_tmp/infra/traefik"; fi
for z4_d in "$ROOT"/projects/*/; do
  z4_n="$(basename "$z4_d")"
  mkdir -p "$z4_tmp/projects/$z4_n"
  for z4_f in "$z4_d"*.yml "$z4_d".env.example; do
    if [ -f "$z4_f" ]; then cp "$z4_f" "$z4_tmp/projects/$z4_n/"; fi
  done
done
if ! bash "$z4_tmp/infra/demo/provision-demo-env.sh" >/dev/null 2>&1; then
  rm -rf "$z4_tmp"
  fail "(z4) 임시 트리 .env 프로비저닝 실패 — 이후 판정이 env-preflight 에 막혀 무효가 됩니다."
fi

cat > "$z4_tmp/bin/docker" <<'Z4SHIM'
#!/bin/sh
# `docker compose -p <FAILDOM> … up -d` 만 비-0. 나머지 docker 호출은 전부 성공.
case "$1" in
  compose)
    shift; z4p=""
    while [ $# -gt 0 ]; do
      case "$1" in -p) z4p="$2"; shift 2 ;; *) shift ;; esac
    done
    if [ "$z4p" = "${FAILDOM:-}" ]; then
      echo "dependency failed to start: container ${z4p}-kafka is unhealthy" >&2
      exit 1
    fi
    exit 0 ;;
  *) exit 0 ;;
esac
Z4SHIM
chmod +x "$z4_tmp/bin/docker"

z4_run() {  # $1=FAILDOM ('' = 실패 없음) → 로그는 $z4_tmp/run.log, rc 를 echo
  local z4_rc=0
  ( cd "$z4_tmp" && PATH="$z4_tmp/bin:$PATH" FAILDOM="$1" DEMO_SEED=0 DEMO_DOMAIN=local \
      DEMO_UP_ATTEMPTS=2 DEMO_UP_RETRY_SLEEP=1 \
      bash infra/demo/demo-up.sh iam wms console ) > "$z4_tmp/run.log" 2>&1 || z4_rc=$?
  echo "$z4_rc"
}

# (1) 대조군 — 아무도 실패하지 않으면 초록이어야 한다.
z4_ok_rc="$(z4_run '')"
if [ "$z4_ok_rc" != "0" ]; then
  z4_tail="$(tail -12 "$z4_tmp/run.log")"; rm -rf "$z4_tmp"
  fail "(z4) 대조군 실패 — 아무 도메인도 실패하지 않았는데 demo-up.sh 가 rc=$z4_ok_rc 로 끝났습니다."\
    $'\n'"→ 정상 부팅이 빨간 가드는 곧 꺼지고, 꺼진 가드의 skip 은 초록으로 보고됩니다(MONO-360)."\
    $'\n'"--- 마지막 로그 ---"$'\n'"$z4_tail"
fi

# (2)(3)(4) bite — 가운데 도메인(wms)만 실패시킨다. iam 은 앞, console 은 뒤에 있다.
z4_bad_rc="$(z4_run wms)"
z4_log="$(cat "$z4_tmp/run.log")"
rm -rf "$z4_tmp"

if ! printf '%s\n' "$z4_log" | grep -q '^\[demo\] up: console'; then
  fail "(z4) wms 기동 실패가 그 뒤의 console 기동을 막았습니다 — 부분 실패가 격리되지 않습니다."\
    $'\n'"→ 실제 결과: 재시작 뒤 옛 라벨 컨테이너가 계속 서빙하고 **새 주소는 전부 404** 입니다."\
    $'\n'"→ demo-up.sh 의 기동 루프에서 compose 실패를 잡아 다음 도메인으로 진행하세요(TASK-MONO-553 A)."
fi
if [ "$z4_bad_rc" = "0" ]; then
  fail "(z4) 도메인 하나가 기동에 실패했는데 demo-up.sh 가 **성공(rc=0)** 으로 끝났습니다."\
    $'\n'"→ 실패를 삼킨 것입니다. 그러면 demo-stack.service 가 초록이 되고, 이 저장소가 반복해서"\
    $'\n'"   당한 *\"아무것도 안 보면서 초록\"* 이 됩니다."\
    $'\n'"→ 실패한 도메인을 모아 마지막에 비-0 로 끝내세요(격리 ≠ 무시)."
fi
if ! printf '%s\n' "$z4_log" | grep -q 'wms'; then
  fail "(z4) 실패한 도메인(wms)의 이름이 출력에 없습니다 — 어느 도메인이 죽었는지 알 수 없습니다."
fi
ok "부분 실패 격리 — 정상 rc=0 · wms 실패 시 console 까지 진행하고 rc=$z4_bad_rc 로 보고"

# ---------------------------------------------------------------------------
echo "[verify] (z6) 헬스 발행이 스냅샷에 **발행 시각**을 싣는가"
# ---------------------------------------------------------------------------
# TASK-MONO-551 결함 B. 발행자가 죽어도 SSM 파라미터는 **마지막 값 그대로 남는다.**
# 그것을 타임스탬프 없이 반환하면 *"방금 잰 값"* 과 *"13분 전 값"* 이 **바이트 단위로
# 구별 불가**다 — 2026-08-17 실측에서 12.8분 묵은 `99/102 정상` 이 그렇게 읽혔고,
# 그 순간 호스트는 15분째 무응답이었다.
#
# 🔴 판정은 **발행자가 실제로 내보내는 값**으로 한다. `aws` 를 대역으로 바꿔 `--value` 를
#    가로채고 그 문자열을 본다 — 소스 grep 이 아니다(이 파일도 `published_at` 이라는 낱말을
#    주석에 담으므로 grep 술어는 자기 문서에 걸린다).
#
# 🔴 그리고 **음성 대조군을 스스로 들고 있는다**: 옛 모양(감싸지 않은 평평한 스냅샷)과
#    낡은 타임스탬프를 같은 술어에 먹여 **거부되는지** 먼저 본다. 아무거나 통과시키는
#    술어는 통과해도 아무것도 증명하지 않는다.
z6_tmp="$(mktemp -d)"
mkdir -p "$z6_tmp/bin"
cat > "$z6_tmp/bin/aws" <<'Z6SHIM'
#!/bin/sh
# `aws ssm put-parameter … --value <json>` 의 값만 가로채 파일로 남긴다.
while [ $# -gt 0 ]; do
  if [ "$1" = "--value" ]; then printf '%s' "$2" > "$Z6_CAPTURE"; exit 0; fi
  shift
done
exit 0
Z6SHIM
chmod +x "$z6_tmp/bin/aws"

# 술어는 한 곳에만 둔다 — 대조군과 본 판정이 **같은 술어**를 써야 대조군에 의미가 있다.
z6_wrapped() {  # $1=발행된 값 → 감싼 모양이고 published_at 이 최근이면 0
  local at now
  printf '%s' "$1" | grep -qE '^\{"published_at":[0-9]+,"domains":\{' || return 1
  at="$(printf '%s' "$1" | sed -n 's/^{"published_at":\([0-9][0-9]*\).*/\1/p')"
  [ -n "$at" ] || return 1
  now="$(date -u +%s)"
  # 값이 있기만 하면 되는 게 아니다 — **지금** 찍힌 값이어야 한다. 하드코딩된 상수나
  # 부팅 시각이 실려도 모양은 통과하므로 나이를 본다.
  [ "$(( now - at ))" -ge -60 ] && [ "$(( now - at ))" -le 300 ]
}

# (1) 음성 대조군 — 옛 평평한 모양은 반드시 거부돼야 한다.
if z6_wrapped '{"iam":{"state":"up","healthy":5,"total":5}}'; then
  rm -rf "$z6_tmp"
  fail "(z6) 술어가 **옛 평평한 스냅샷을 통과시켰습니다** — 이 술어로는 아무것도 증명할 수 없습니다."
fi
# (2) 두 번째 음성 대조군 — 모양은 맞지만 시각이 낡은 값.
if z6_wrapped '{"published_at":1000000000,"domains":{}}'; then
  rm -rf "$z6_tmp"
  fail "(z6) 술어가 **2001년 타임스탬프를 신선하다고 판정했습니다** — 나이를 안 보고 있습니다."
fi

# (3) 진짜 발행자를 돌린다. AWS_REGION 을 주어 IMDS 경로를 타지 않게 한다(러너는 EC2 가 아니다).
Z6_CAPTURE="$z6_tmp/value.json"
export Z6_CAPTURE
z6_rc=0
( PATH="$z6_tmp/bin:$PATH" AWS_REGION=ap-northeast-2 \
    HEALTH_PARAM=/verify/z6 bash "$ROOT/infra/demo/demo-status-publish.sh" ) >/dev/null 2>&1 || z6_rc=$?
if [ "$z6_rc" != "0" ]; then
  rm -rf "$z6_tmp"
  fail "(z6) demo-status-publish.sh 가 rc=$z6_rc 로 실패했습니다 — 발행 경로가 깨졌습니다."
fi
if [ ! -f "$Z6_CAPTURE" ]; then
  rm -rf "$z6_tmp"
  fail "(z6) 발행자가 put-parameter 를 부르지 않았습니다 — 값을 가로채지 못했습니다."\
    $'\n'"→ 대역이 안 걸렸거나 발행자가 조용히 빠져나갔습니다. 어느 쪽이든 뒤이은 판정은 무효입니다."
fi
z6_value="$(cat "$Z6_CAPTURE")"
rm -rf "$z6_tmp"
if ! z6_wrapped "$z6_value"; then
  fail "(z6) 발행된 값에 최근 발행 시각이 없습니다:"\
    $'\n'"   $(printf '%s' "$z6_value" | cut -c1-120)"\
    $'\n'"→ 기대 모양: {\"published_at\":<epoch UTC>,\"domains\":{…}}"\
    $'\n'"→ 시각이 없으면 소비자는 얼어붙은 스냅샷과 방금 잰 값을 구별할 수 없습니다(MONO-551 B)."\
    $'\n'"   실측: 12.8분 묵은 '99/102 정상' 이 그대로 읽혔고 그때 호스트는 15분째 무응답이었습니다."
fi
ok "헬스 발행이 발행 시각을 싣는다 (음성 대조군 2종 거부 확인 · 실제 발행 값 ${#z6_value} bytes)"

echo "[verify] (z8) finance 시드의 입금 술어가 **재실행**에도 정직한가"
# ---------------------------------------------------------------------------
# TASK-MONO-556. 첫 판의 술어는 `입금 후 잔액 >= TOPUP_MINOR` 였고, **같은 시드의 다음
# 단계인 이체 A→B 가 그 전제를 깼다** — A 는 400,000 으로 내려가고 2회차 입금은 재생이라
# 잔액을 못 올린다 ⇒ 재시작마다 결정론적 실패. 1회차만 통과했으므로 **첫 부팅 테스트로는
# 절대 안 보인다**(볼륨이 새로 생기면 늘 1회차다).
#
# 🔴 그래서 이 가드는 **왕복을 재현한다** — 1회차 → 이체 → 2회차. 한 번만 돌리면 고침
#    전 코드도 통과하고, 그러면 이 가드는 아무것도 안 지킨다.
#
# 🔴 `curl` 이 아니라 `http` 를 대역으로 둔다. 통제해야 하는 것은 정확히 **서버가 무엇을
#    돌려주는가** 이고, 진짜 finance 스택을 띄우는 것은 러너에서 불가능할뿐더러 무엇이
#    실패했는지 통제할 수 없다.
#
# 네 칸을 본다 — 하나라도 빠지면 판정이 공허하다:
#   (1) 1회차가 통과하는가                 ← 대조군. 정상 경로가 빨간 가드는 곧 꺼진다.
#   (2) **이체 뒤 2회차**가 통과하는가      ← bite. 이것이 이 티켓 그 자체다.
#   (3) 입금이 **성립하지 않으면** 실패하는가 ← 음성 대조군. 없으면 "술어를 지워서
#                                            통과시킨" 구현과 구별되지 않는다.
#   (4) 거래내역을 **못 읽으면** 실패하는가  ← 계측 실패를 "입금 없음" 으로 읽지 않는다.

z8_tmp="$(mktemp -d)"
trap 'rm -rf "$z8_tmp"' EXIT

# --- 실제 코드에서 판정 대상 3함수를 그대로 떼어 온다 -------------------------
# 🔴 복사본을 두지 않는다 — 복사본을 검사하는 가드는 **원본이 갈라져도 초록**이다.
#    추출이 빗나가면(리팩터링으로 앵커가 사라지면) 조용히 통과하지 않고 **여기서 죽는다**.
awk '/^ledger_of\(\) \{/{f=1} f{print} /^topup "계좌 A 입금"/{exit}' "$ROOT/infra/demo/seed/seed-finance.sh" > "$z8_tmp/topup.sh"
# 🔴 여기에는 **추출 기계장치의 앵커만** 넣는다. `topup_txns()`(= 정직한 술어의 구현)를
#    이 목록에 넣으면 안 된다 — 실측했다: 고침 전 코드를 물리면 행위 셀 (2) 가 아니라 이
#    점검에서 죽고, 실패가 *"앵커가 갈라졌다"* 로 보고된다. **엉뚱한 이름에 귀속된 실패**라
#    다음 사람은 술어 결함이 아니라 리팩터링 사고를 찾으러 간다.
#    구현이 있는지는 **행위가 판정한다**(아래 (2)).
for z8_need in 'ledger_of()' 'topup()' 'TOPUP_MINOR='; do
  grep -qF "$z8_need" "$z8_tmp/topup.sh" \
    || fail "(z8) seed-finance.sh 에서 \`$z8_need\` 를 추출하지 못했습니다 — 앵커가 갈라졌습니다."$'\n'"→ 가드가 검사할 대상을 잃었습니다. 추출 범위를 고치세요(조용히 통과시키지 말 것)."
done
# 마지막 줄(`topup "계좌 A 입금" …` 호출)은 떼어 낸다 — 호출은 우리가 직접 한다.
sed -i '/^topup "계좌 A 입금"/d' "$z8_tmp/topup.sh"

# --- 가짜 finance 서버 (상태를 파일로 들고 있는다) ---------------------------
cat > "$z8_tmp/harness.sh" <<'Z8H'
set -uo pipefail
SEED_DOMAIN=finance
SEED_CREATED=0; SEED_EXISTING=0; SEED_FAILURES=0
seed_log()  { printf '    [seed] %s\n' "$*"; }
seed_fail() { printf '    [seed] ✗ %s\n' "$*"; SEED_FAILURES=$((SEED_FAILURES + 1)); }
json_objects() { printf '%s' "$1" | sed 's/},{/}\n{/g'; }
FIN="http://finance.test"; CURRENCY="KRW"

# 상태: $ST/<acc>.bal (잔액) · $ST/<acc>.txn (TOPUP 거래 JSON 조각)
ST="$STATE_DIR"
bal_of() { cat "$ST/$1.bal" 2>/dev/null || echo 0; }

# MODE: ok | nodeposit(2xx 인데 아무것도 기록 안 함) | txnfail(거래내역 조회가 5xx)
http() {
  local method="$1" url="$2" body="${3:-}" acc
  acc="$(printf '%s' "$url" | sed -E 's#.*/accounts/([^/?]+).*#\1#')"
  case "$method $url" in
    "POST "*"/topups")
      if [ "${MODE:-ok}" != "nodeposit" ] && [ ! -f "$ST/$acc.done" ]; then
        touch "$ST/$acc.done"
        echo $(( $(bal_of "$acc") + 500000 )) > "$ST/$acc.bal"
        printf '{"transactionId":"t-%s","type":"TOPUP","status":"SETTLED","money":{"amount":"500000","currency":"KRW"}}' "$acc" >> "$ST/$acc.txn"
      fi
      SEED_LAST_STATUS=200; SEED_LAST_BODY='{"data":{"type":"TOPUP"}}'; return 0 ;;
    "GET "*"/balances")
      SEED_LAST_STATUS=200; SEED_LAST_BODY="{\"data\":{\"ledger\":\"$(bal_of "$acc")\",\"available\":\"$(bal_of "$acc")\"}}"; return 0 ;;
    "GET "*"/transactions"*)
      if [ "${MODE:-ok}" = "txnfail" ]; then SEED_LAST_STATUS=503; SEED_LAST_BODY='{"error":"down"}'; return 1; fi
      local content; content="$(cat "$ST/$acc.txn" 2>/dev/null)"
      # 🔵 TRANSFER 도 한 건 섞어 둔다 — 통짜 grep 이면 그 type 과 TOPUP 의 금액이 합쳐져
      #    키메라 행이 되므로, 객체 단위 파싱을 안 하면 여기서 갈린다.
      local other='{"transactionId":"x","type":"TRANSFER","status":"SETTLED","money":{"amount":"100000","currency":"KRW"}}'
      SEED_LAST_STATUS=200
      if [ -n "$content" ]; then SEED_LAST_BODY="{\"data\":{\"content\":[$content,$other]}}"
      else SEED_LAST_BODY="{\"data\":{\"content\":[$other]}}"; fi
      return 0 ;;
  esac
  SEED_LAST_STATUS=404; SEED_LAST_BODY='{}'; return 1
}
Z8H

z8_run() { # $1=MODE $2=라벨 → 실패 건수를 echo, 로그는 $z8_tmp/run.log
  ( set +e
    export STATE_DIR="$z8_state" MODE="$1"
    # shellcheck disable=SC1090
    source "$z8_tmp/harness.sh"
    source "$z8_tmp/topup.sh"
    topup "계좌 A 입금" "acc-a" 500000
    topup "계좌 B 입금" "acc-b" 500000
    echo "RC:$SEED_FAILURES"
  ) > "$z8_tmp/run.log" 2>&1
  grep -o 'RC:[0-9]*' "$z8_tmp/run.log" | tail -1 | cut -d: -f2
}

z8_state="$z8_tmp/state1"; mkdir -p "$z8_state"

# (1) 1회차 — 대조군
z8_first="$(z8_run ok first)"
[ "$z8_first" = "0" ] || fail "(z8) 대조군 실패 — 첫 실행인데 실패 $z8_first 건입니다."$'\n'"$(cat "$z8_tmp/run.log")"

# --- 이체 A→B 를 재현한다 (시드가 실제로 하는 그 일) -------------------------
# 이것이 결함의 방아쇠다: A 가 목표 아래로 내려간다.
echo $(( $(cat "$z8_state/acc-a.bal") - 100000 )) > "$z8_state/acc-a.bal"
echo $(( $(cat "$z8_state/acc-b.bal") + 100000 )) > "$z8_state/acc-b.bal"
[ "$(cat "$z8_state/acc-a.bal")" = "400000" ] \
  || fail "(z8) 해네스 자기점검 실패 — 이체 후 A 가 400000 이어야 하는데 $(cat "$z8_state/acc-a.bal") 입니다."

# (2) 2회차 — bite
z8_second="$(z8_run ok second)"
if [ "$z8_second" != "0" ]; then
  fail "(z8) **재실행이 실패했습니다**(실패 $z8_second 건) — 입금 술어가 여전히 잔액 수준에 묶여 있습니다."\
    $'\n'"→ 이체 A→B 가 A 를 400,000 으로 내렸고, 2회차 입금은 재생이라 잔액을 못 올립니다."\
    $'\n'"→ 결과: demo-stack.service 가 **첫 재시작 이후 항상 failed**(TASK-MONO-556)."\
    $'\n'"→ 술어를 잔액이 아니라 **입금 사실**(TOPUP 거래의 존재)로 옮기세요."\
    $'\n'"$(cat "$z8_tmp/run.log")"
fi

# (3) 음성 대조군 — 입금이 성립하지 않으면 **여전히 실패**해야 한다
z8_state="$z8_tmp/state2"; mkdir -p "$z8_state"
z8_nodep="$(z8_run nodeposit control)"
[ "$z8_nodep" != "0" ] || fail "(z8) 음성 대조군 실패 — 입금이 한 건도 성립하지 않았는데 통과했습니다."\
  $'\n'"→ 술어를 **지워서** 통과시킨 것입니다. 그러면 이 시드가 지키던 회귀"\
  $'\n'"   (INSUFFICIENT_AVAILABLE_BALANCE)를 전부 놓칩니다."

# (4) 계측 실패 ≠ 입금 없음
z8_state="$z8_tmp/state3"; mkdir -p "$z8_state"
z8_txnfail="$(z8_run txnfail control)"
[ "$z8_txnfail" != "0" ] || fail "(z8) 거래내역 조회가 5xx 인데 통과했습니다 — **판정 불가를 통과로** 셌습니다."
grep -q '판정 불가' "$z8_tmp/run.log" \
  || fail "(z8) 조회 실패를 실패로 세긴 했으나 **이유가 '입금 없음' 으로 보고**됩니다 — 계측 실패와 구별되어야 합니다."

ok "입금 술어 — 1회차 rc=0 · **이체 뒤 2회차 rc=0** · 미입금 대조군 실패 · 조회불가 실패(사유 구분)"

echo "[verify] (z9) CORS 의 집이 하나로 유지되는가 (terraform 쪽)"
# ---------------------------------------------------------------------------
# TASK-MONO-557. 허용 오리진은 한때 **두 집**을 갖고 있었다 — API Gateway 의
# `cors_configuration` 과 Lambda 의 `ALLOWED_ORIGIN` 환경변수. 2026-08-18 실측이 그 둘이
# **이미 어긋나 있었음**을 보였다: 같은 `""` 가 (a)에서는 CloudFront 폴백으로 해소되고
# (b)에서는 `Access-Control-Allow-Origin: ""` 가 됐다(`os.environ.get` 은 키가 존재하면
# 기본값을 쓰지 않는다). 라이브 응답에 나타난 값은 전부 (a) 쪽이었다 — (b)는 틀린 값을
# 든 죽은 코드였고, (a)를 걷어내는 날 전면 차단으로 살아났을 것이다.
#
# 🔵 핸들러 쪽은 `tests/test_handler.py::CorsHasOneHome` 이 본다(러너 = 같은 CI 잡의
#    pytest 스텝). 여기서는 **terraform 쪽**만 본다 — 두 번째 집이 되돌아오는 경로가
#    거기이기 때문이다.
z9_tf="$ROOT/infra/demo/aws/terraform/main.tf"
[ -f "$z9_tf" ] || fail "(z9) $z9_tf 가 없습니다 — 가드가 검사할 대상을 잃었습니다."

# (1) Lambda environment 블록에 ALLOWED_ORIGIN 이 **없어야** 한다.
#     🔴 파일 전체 grep 이 아니라 environment 블록만 본다 — 주석에 그 이름이 나오는 것은
#        정상이고(왜 없는지 설명해야 하니까), 통짜 grep 은 자기 설명 문구에 걸린다.
# `aws_lambda_function "control"` 리소스 블록 전체를 뜬다(열림 → 컬럼 0 의 `}`).
# 🔴 들여쓰기에 앵커를 걸지 않는다 — 첫 판이 그렇게 했다가 실제 파일(2칸)과 어긋나
#    추출이 빈 문자열이 됐다. 그때 가드는 조용히 통과하지 않고 **여기서 죽었고**,
#    그게 이 자기점검이 있는 이유다.
z9_env="$(awk '/^resource "aws_lambda_function" "control"/{f=1} f{print} f&&/^\}/{exit}' "$z9_tf")"
[ -n "$z9_env" ] || fail "(z9) main.tf 에서 Lambda environment 블록을 찾지 못했습니다 — 앵커가 갈라졌습니다."
if printf '%s' "$z9_env" | grep -qE '^[^#]*ALLOWED_ORIGIN'; then
  fail "(z9) Lambda environment 에 ALLOWED_ORIGIN 이 되돌아왔습니다 — CORS 가 다시 두 집을 갖습니다."\
    $'\n'"→ 실측(2026-08-18): 그 두 집은 이미 어긋나 있었고, Lambda 쪽은 \`Access-Control-Allow-Origin: \"\"\` 를 실었습니다."\
    $'\n'"→ 두 곳에서 실으면 헤더가 중복되어 브라우저가 거부하기도 합니다."\
    $'\n'"→ CORS 의 집은 API Gateway 의 cors_configuration 하나입니다."
fi

# (2) 허용 오리진 목록이 CloudFront 도메인을 **참조로** 들고 있어야 한다.
#     리터럴을 박으면 재생성마다 썩는다 — TASK-MONO-389 가 고친 그 결함(결함 2)이다.
z9_local="$(awk '/cors_allowed_origins = distinct\(concat\(/{f=1} f{print} f&&/\)\)/{exit}' "$z9_tf")"
[ -n "$z9_local" ] || fail "(z9) local.cors_allowed_origins 를 찾지 못했습니다 — 앵커가 갈라졌습니다."
printf '%s' "$z9_local" | grep -q 'aws_cloudfront_distribution.site.domain_name' \
  || fail "(z9) 허용 오리진 목록이 CloudFront 도메인을 **참조**하지 않습니다."\
    $'\n'"→ 배포 시점에야 정해지는 값이라 손으로 박으면 재생성마다 썩습니다(결함 2, TASK-MONO-389)."
if printf '%s' "$z9_local" | grep -qE '"https://[a-z0-9.-]*(cloudfront|execute-api)'; then
  fail "(z9) 허용 오리진 목록에 AWS 가 발급하는 주소가 **리터럴로** 박혀 있습니다."\
    $'\n'"→ 그 값은 재생성마다 바뀝니다. 참조로 두거나 var.allowed_origins 로 받으세요."
fi

# (3) 대조군 — 가드가 (1)을 실제로 볼 수 있는가.
#     🔴 environment 블록 추출이 빈 껍데기면 (1)은 **항상 통과**한다. 다른 변수가 그
#        블록에 실제로 보이는지 확인해 추출이 살아 있음을 증명한다.
printf '%s' "$z9_env" | grep -q 'MONTHLY_BUDGET_MINUTES' \
  || fail "(z9) 대조군 실패 — environment 블록 추출에 MONTHLY_BUDGET_MINUTES 가 안 보입니다."\
    $'\n'"→ 추출이 빈 껍데기이므로 (1)의 통과는 **아무것도 증명하지 않습니다**."

ok "CORS 단일 집 (terraform) — Lambda env 에 ALLOWED_ORIGIN 없음(대조군으로 추출 유효 확인) · 오리진 목록은 CloudFront 를 참조"

echo "[verify] (z10) vercel.json 이 Vercel 이 받아들이는 모양인가"
# ---------------------------------------------------------------------------
# TASK-MONO-557. `vercel.json` 안에 설명을 넣으려고 `"//installCommand"` 라는 키를 만들었다가
# **배포를 깼다**(2026-08-19). JSON 에는 주석이 없고, **Vercel 은 vercel.json 의 모르는
# 최상위 키를 거부한다.**
#
# 🔴 이 결함이 위험한 이유는 **사이트가 멀쩡해 보였다는 것**이다. 배포가 실패하면 Vercel 은
#    마지막 성공 배포를 계속 서빙한다 ⇒ URL 을 찔러 보면 200 이고 `/config.js` 도 정상이다.
#    갈라 준 것은 커밋별 배포 상태의 **시각**이었다: 성공(07:54Z) → 그 키를 넣은 뒤 두 번
#    연속 실패(08:18Z·08:27Z). **"사이트가 뜬다" 와 "배포가 된다" 는 다른 명제다.**
#
# 🔵 **이 가드가 재는 것과 안 재는 것을 분명히 한다.** Vercel 의 실제 스키마 검증은 로컬에서
#    재현할 수 없고, 재현했다고 적으면 그건 선언이지 측정이 아니다. 여기서는 **우리가 실제로
#    밟은 함정 — 주석 흉내 키 — 과 이 프로젝트가 의존하는 세 키의 존재**만 본다.
#    JSON 문법 검증도 하지 않는다(파서를 새로 끌어오면 (z2) 의 도구 요구가 늘어난다).
z10_vj="$ROOT/infra/demo/aws/site/vercel.json"
if [ -f "$z10_vj" ]; then
  # (1) 주석 흉내 키 — 우리가 실제로 밟은 그 함정.
  z10_bad="$(grep -nE '^[[:space:]]*"(//|#)' "$z10_vj" || true)"
  [ -z "$z10_bad" ] || fail "(z10) vercel.json 에 **주석 흉내 키**가 있습니다:"    $'
'"$z10_bad"    $'
'"→ JSON 에는 주석이 없고, Vercel 은 모르는 최상위 키를 거부합니다."    $'
'"→ 2026-08-19 에 정확히 이것으로 배포가 두 번 연속 죽었습니다(사이트는 마지막"    $'
'"   성공 배포가 계속 서빙해서 겉으로는 멀쩡했습니다)."    $'
'"→ 설명은 site/build.sh 주석에 두세요 — 거기가 설명의 집입니다."

  # (2) 이 프로젝트가 의존하는 세 키가 실제로 있는가.
  #     🔴 (1)만 보면 **키를 전부 지워서 통과** 와 구별되지 않는다.
  for z10_k in buildCommand outputDirectory installCommand; do
    grep -q "\"$z10_k\"" "$z10_vj" || fail "(z10) vercel.json 에 \`$z10_k\` 가 없습니다."      $'
'"→ buildCommand 가 없으면 build.sh 가 안 돌아 public/ 이 안 만들어지고,"      $'
'"   outputDirectory 가 없으면 엉뚱한 디렉터리가 배포되며,"      $'
'"   installCommand 가 없으면 루트 pnpm-lock.yaml 을 찾아 monorepo 전체를 설치합니다."
  done
  ok "vercel.json 모양 유지 (주석 흉내 키 없음 · build/output/install 3키 존재)"
fi

echo "[verify] (z11) 론처에 적힌 로그인 계정이 시드가 실제로 쓰는 값과 같은가"
# ---------------------------------------------------------------------------
# TASK-MONO-561. 론처가 로그인 계정을 안 알려줘서 방문자가 못 들어갔고, 그 구멍을
# 메우려다 회원가입 결함 두 건(TASK-BE-580/581)을 밟았다. 이제 페이지에 계정을 적는데,
# **적어 둔 값은 아무 게이트도 없으면 반드시 썩는다.**
#
# 🔴 그리고 그 썩음은 **방문자에게만 보인다** — 로그인이 안 될 뿐, 우리 쪽 게이트는
#    전부 초록이다. 그래서 여기서 잡는다.
#
# 출처를 왜 `seed/lib.sh` 로 잡는가(마이그레이션이 아니라):
#   · `R__01_seed_demo_single_identity_credentials.sql` 의 평문은 **주석**이고, 컬럼에
#     실제로 든 것은 Argon2id 해시라 대조할 수 없다. 주석은 코드와 갈라져도 아무도 모른다.
#   · `lib.sh` 의 `user_token()` 기본값은 **부팅마다 실제로 로그인에 쓰인다.** 시드가
#     통과했다는 것은 그 값으로 로그인이 됐다는 뜻이다 — 살아 있는 출처다.
z11_html="$ROOT/infra/demo/aws/site/index.html"
z11_lib="$ROOT/infra/demo/seed/lib.sh"
if [ -f "$z11_html" ] && [ -f "$z11_lib" ]; then
  # 페이지 쪽 — id 로 뽑는다(문서 어디에나 나올 수 있는 이메일 문자열이 아니라).
  z11_p_email="$(sed -n 's/.*id="c-email"[^>]*>\([^<]*\)<.*/\1/p' "$z11_html" | head -1 | tr -d '\r')"
  z11_p_pass="$(sed -n 's/.*id="c-pass"[^>]*>\([^<]*\)<.*/\1/p' "$z11_html" | head -1 | tr -d '\r')"
  # 시드 쪽 — user_token() 의 기본값 `${DEMO_EMAIL:-…}` / `${DEMO_PASSWORD:-…}`.
  z11_s_email="$(sed -n 's/.*${DEMO_EMAIL:-\([^}]*\)}.*/\1/p' "$z11_lib" | head -1 | tr -d '\r')"
  z11_s_pass="$(sed -n 's/.*${DEMO_PASSWORD:-\([^}]*\)}.*/\1/p' "$z11_lib" | head -1 | tr -d '\r')"

  # 🔴 추출 유효성 — 이 칸이 없으면 한쪽이 빈 문자열일 때 비교가 **공허하게 통과**한다
  #    ("" == ""). 이 저장소는 CRLF 라 앵커가 한 글자만 어긋나도 빈 값이 나온다.
  for z11_pair in "페이지 이메일:$z11_p_email" "페이지 비밀번호:$z11_p_pass" \
                  "시드 이메일:$z11_s_email" "시드 비밀번호:$z11_s_pass"; do
    z11_name="${z11_pair%%:*}"; z11_val="${z11_pair#*:}"
    [ -n "$z11_val" ] || fail "(z11) $z11_name 을 뽑지 못했습니다 — 앵커가 갈라졌습니다."\
      $'\n'"→ 뽑히지 않은 값으로 비교하면 \"\" == \"\" 로 **항상 통과**합니다. 그 통과는 아무것도 증명하지 않습니다."\
      $'\n'"→ 페이지는 id=\"c-email\"/\"c-pass\", 시드는 DEMO_EMAIL/DEMO_PASSWORD 기본값을 앵커로 씁니다."
  done

  # 🔴 양방향이다 — 페이지가 바뀌어도, 시드가 바뀌어도(비밀번호 로테이션) 잡힌다.
  [ "$z11_p_email" = "$z11_s_email" ] \
    || fail "(z11) 론처의 이메일이 시드가 쓰는 값과 다릅니다: 페이지=\"$z11_p_email\" · 시드=\"$z11_s_email\""\
      $'\n'"→ 방문자는 로그인에 실패하는데 우리 쪽 게이트는 전부 초록입니다(그 실패는 방문자에게만 보입니다)."\
      $'\n'"→ 권위는 seed/lib.sh 입니다 — 그 값은 부팅마다 실제로 로그인에 쓰입니다."
  [ "$z11_p_pass" = "$z11_s_pass" ] \
    || fail "(z11) 론처의 비밀번호가 시드가 쓰는 값과 다릅니다: 페이지=\"$z11_p_pass\" · 시드=\"$z11_s_pass\""\
      $'\n'"→ 위와 같은 이유입니다. 비밀번호를 바꿨다면 이 페이지도 같이 바꾸세요."

  # 🔴 대조군 — 비교 자체가 살아 있는지 본다. 앞으로 누가 이 식을 항상 참인 모양으로
  #    바꾸면 위 두 칸은 조용히 통과한다. 한 글자 다른 값이 **다르다고 판정되는지** 확인.
  [ "${z11_p_email}x" != "$z11_s_email" ] \
    || fail "(z11) 대조군 실패 — 일부러 다르게 만든 값이 같다고 판정됐습니다. 비교가 죽어 있습니다."

  ok "론처 계정 ↔ 시드 일치 (email=$z11_p_email · 비밀번호 일치 · 네 값 모두 추출 확인 · 대조군 통과)"
fi

# ---------------------------------------------------------------------------
# ---------------------------------------------------------------------------
# ---------------------------------------------------------------------------
# ---------------------------------------------------------------------------
if [ "$LIVE" -eq 0 ]; then
  echo "[verify] 정적 검증 PASS (실기동 증명은 --live)"
  exit 0
fi

echo "[verify] (f) --live: 같은 서비스 키 'redis' 가 별도 -p 로 공존하는가"
# ---------------------------------------------------------------------------
# scm 과 fan 은 둘 다 compose 키 'redis'(redis:7-alpine)를 정의하지만
# container_name 은 scm-platform-redis / fan-platform-redis 로 다르다.
# 단일 include/-f 병합이면 하나만 살아남는다 → 둘 다 healthy 여야 통과.
cleanup_live() {
  local A
  mapfile -t A < <(compose_args scm)
  docker compose -p verify-live-scm "${A[@]}" down --remove-orphans >/dev/null 2>&1 || true
  mapfile -t A < <(compose_args fan)
  docker compose -p verify-live-fan "${A[@]}" down --remove-orphans >/dev/null 2>&1 || true
  rm -f "$names_file" "$ports_file"
}
trap cleanup_live EXIT

mapfile -t SCM_ARGS < <(compose_args scm)
mapfile -t FAN_ARGS < <(compose_args fan)
docker compose -p verify-live-scm "${SCM_ARGS[@]}" up -d redis >/dev/null
docker compose -p verify-live-fan "${FAN_ARGS[@]}" up -d redis >/dev/null

wait_healthy() {
  for _ in $(seq 1 30); do
    st="$(docker inspect -f '{{.State.Health.Status}}' "$1" 2>/dev/null || echo missing)"
    [ "$st" = "healthy" ] && return 0
    [ "$st" = "missing" ] && return 1
    sleep 2
  done
  return 1
}

wait_healthy scm-platform-redis || fail "scm-platform-redis 가 healthy 되지 않음"
ok "scm-platform-redis healthy"
wait_healthy fan-platform-redis || fail "fan-platform-redis 가 healthy 되지 않음"
ok "fan-platform-redis healthy"

running="$(docker ps --filter 'name=scm-platform-redis' --filter 'name=fan-platform-redis' -q | wc -l | tr -d ' ')"
[ "$running" = "2" ] || fail "두 redis 가 공존하지 않음 (running=$running) — 병합 회귀 의심"
ok "같은 키 'redis' 2개가 별도 -p 로 공존 (running=2)"

echo "[verify] (u) kafka 의 메모리 리밋이 브로커를 돌릴 만한가"
# ---------------------------------------------------------------------------
# 근거(MONO-397): `ecommerce-kafka` 의 리밋이 **512M** 이었고, 데모 호스트에서
# `constraint=CONSTRAINT_MEMCG` OOM 으로 **14회 재시작**했다(anon-rss 481 MB 에서 kill).
#
# **격리 상태의 512M kafka 는 죽지 않는다** — RestartCount 0, healthcheck 통과, healthy.
# 죽는 건 **함대가 붙을 때**다(ecommerce 12개 서비스가 컨슈머로 연결되면 커넥션·페치
# 버퍼가 남은 여유를 먹는다). ⇒ **"죽었는가" 를 묻는 가드는 이 결함을 통과시킨다.**
#
# **왜 512M 이 브로커를 못 돌리는가** — 리밋은 **상한이 아니라 설정**이다.
# `KAFKA_HEAP_OPTS` 가 없으므로 JVM 은 cgroup 을 읽어 힙을 리밋의 25%
# (`MaxRAMPercentage` 기본값)로 잡는다:
#
#     512M → 힙 128 MiB   ← "128 MiB 힙으로 Kafka 브로커를 돌려라"
#     1G   → 힙 256 MiB
#
# 그래서 하한은 **1 GiB** 다. 아래 elasticsearch 의 `2G`(TASK-BE-406)와 같은 종류의
# 상수이고, 같은 이유로 존재한다.
#
# ═══════════════════════════════════════════════════════════════════════════
# 📖 이 블록이 낳은 규칙은 이제 **정경 홈**을 갖는다:
#    `platform/testing-strategy.md` § CI Guards / Drift Detectors — G4
#    (아래 실측은 그 규칙의 *증거*로 여기 남긴다. 다른 가드를 쓸 사람은 그 문서를 읽는다.)
#
# ⚠️ **동적으로 재려는 시도를 두 번 했고, 두 번 다 CI 러너가 반박했다.**
#    (`MONO-360`: **가드가 무는지는 그것이 도는 곳에서 증명해야 한다.** 두 번 다
#     측정 전용 PR 이 없었다면 "가드를 넣었다" 고 보고하고 머지했을 것이다.)
#
#  시도 1 — **RSS 사용률 ≤ 75%**  (측정 전용 PR #2533)
#      내 Windows/WSL2, 512M → RSS 425.9 MiB = 83.2%  → RED
#      CI 러너(ubuntu), 512M → RSS 193.2 MiB = 38.2%  → **통과**
#      CI 러너(ubuntu), 1G   → RSS 317.3 MiB = 31.2%
#    같은 컨테이너·리밋·부하인데 RSS 가 **2.2배** 다르다(WSL2 의 `docker stats` 회계).
#    러너에선 512M(38%)과 1G(31%)이 **갈리지 않는다** — 어떤 임계로도 못 잡는다.
#
#  시도 2 — **JVM 에게 자기 힙을 묻는다** (`docker exec … java -XX:+PrintFlagsFinal`)
#                                                        (측정 전용 PR #2534)
#      내 Windows/WSL2, 512M → MaxHeapSize 128 MiB   → RED
#      CI 러너(ubuntu), 512M → MaxHeapSize **3998 MiB** → **통과**
#    러너에서는 **컨테이너 안의 JVM 이 cgroup 리밋을 아예 못 본다**(호스트 RAM 16GB 의
#    25%). "JVM 레벨 규칙이라 호스트 무관" 이라던 내 논거가 그대로 반박됐다.
#
#  ⇒ **컨테이너 안에서 재는 것은 러너에서 신뢰할 수 없다.** 세 번째 영리한 술어를
#    시도하지 않는다. **선언된 리밋 자체**를 단언한다 — 상수이고, 열거이고, **작동한다.**
#    이 주석이 그 상수의 근거이고, **실패한 두 시도를 숫자와 함께 남기는 이유는 다음
#    사람이 같은 길을 다시 걷지 않게 하기 위해서다.**
# ═══════════════════════════════════════════════════════════════════════════
#
# 하한(1 GiB)은 상수다. **모집단은 열거한다**(MONO-442). 이 가드의 첫 판본은
# `render ecommerce` 하나만 봐서, 리밋을 선언한 브로커가 하나 더 생겨도(FIN-BE-059 가
# finance 에 1G 를 넣었다) 검사 밖이었다 — 저장소의 kafka 6→7 중 사정거리는 1개뿐이었다.
# 술어를 **(B) 조건부 열거**로 바꾼다: **리밋을 *선언한* 브로커는 전부 1 GiB 하한을
# 만족해야 한다.** 미선언은 통과한다 — `MONO-397` D3 / `MONO-399` Out-of-scope 가
# **리밋은 상한이 아니라 *설정***이라 결정했으므로(미선언 JVM 에 리밋을 강제하면 전부
# cgroup 25% 힙으로 재설정된다), 미선언 통과는 **묵인이 아니라 결정**이고 그 사실을
# 로그로 말한다. 모집단은 자동으로 자란다. **(C) 전수+리밋 의무화는 D3 를 뒤집는 것이라
# 실측 근거(MONO-399 AC-2) 없이는 선택 불가다.**
#
# **원본 compose YAML 을 읽는다(render 아님).** 도커 없이 발견된 전 브로커를 결정론적으로
# 검사하고, 선언된 리밋 문자열(`1G`/`512M`)이 우리가 지키려는 바로 그 산출물이기 때문이다.
# 브로커 집합은 **열거로 발견**한다(하드코딩 목록 금지 — 그게 애초의 결함이다) → FIN-BE-059
# 머지 여부와 무관하게 동작한다. 실기동만 대표 1개로 남긴다(아래).
U_MIN_LIMIT_MIB=1024   # 힙 256 MiB. 512M(=힙 128 MiB)는 브로커를 돌릴 리밋이 아니다.
u_net="verify-u-net"; u_ctr="verify-u-kafka"
cleanup_u() { docker rm -f "$u_ctr" >/dev/null 2>&1 || true; docker network rm "$u_net" >/dev/null 2>&1 || true; }
trap 'cleanup_u; rm -f "$names_file" "$ports_file"' EXIT
cleanup_u

# <limit-string> → MiB (정수). 파싱 실패는 "" 로 돌려 **공허 통과 대신 loud FAIL** 을 낸다.
# `memory:` 부재(무제한) 와 `memory: 0`(명시 0)은 호출부에서 구분한다(Edge Case).
u_to_mib() {
  local v n u
  v="$(printf '%s' "$1" | tr -d "\"' ")"
  n="$(printf '%s' "$v" | sed -E 's/[^0-9].*$//')"
  u="$(printf '%s' "$v" | sed -E 's/^[0-9]+//')"
  [ -n "$n" ] || { printf ''; return; }
  case "$u" in
    g|G|gb|Gb|GB) printf '%s' "$(( n * 1024 ))" ;;
    m|M|mb|Mb|MB) printf '%s' "$n" ;;
    k|K|kb|Kb|KB) printf '%s' "$(( n / 1024 ))" ;;
    ''|b|B)       printf '%s' "$(( n / 1048576 ))" ;;   # 단위 없음 = bytes
    *)            printf '' ;;                            # 미상 단위 → 파싱 실패
  esac
}
# <compose-file> → 그 파일 kafka 블록의 선언 리밋(원문). 블록/리밋 없으면 "".
# ⚠️ 들여쓰기 칸수를 하드코딩하지 않는다(첫 판본이 그래서 리밋을 못 읽고 공허 FAIL 냈다).
# ⚠️ `|| true` 는 **필수**다: 미선언 브로커는 grep 이 no-match(1)를 내는데, 스크립트의
# `set -o pipefail` 이 그걸 전파하면 `raw="$(u_declared_limit_of …)"` 명령치환이 `set -e`
# 로 스크립트를 죽인다(미선언=정상 케이스인데 abort). head 의 SIGPIPE(141)도 같이 삼킨다.
u_declared_limit_of() {
  awk '/^  kafka:$/{f=1;next} /^  [A-Za-z0-9._-]+:$/{f=0} f' "$1" \
    | grep -E '^[[:space:]]*(memory|mem_limit):[[:space:]]' | head -1 \
    | sed -E 's/^[[:space:]]*(memory|mem_limit):[[:space:]]*//' || true
}

# ── 정적 하한 검사 — 발견된 전 브로커 (B) ──────────────────────────────────────
u_total=0; u_declared_n=0; u_unlimited_n=0; u_fail=""; u_rep_slug=""
for p in $(printf '%s\n' "${!COMPOSE[@]}" | LC_ALL=C sort); do
  for rel in $(compose_files "$p"); do
    f="$ROOT/$rel"
    [ -f "$f" ] || continue
    grep -qE '^  kafka:[[:space:]]*$' "$f" || continue   # 최상위 kafka 서비스만(depends_on 아님)
    u_total=$(( u_total + 1 ))
    raw="$(u_declared_limit_of "$f")"
    if [ -z "$raw" ]; then
      u_unlimited_n=$(( u_unlimited_n + 1 ))
      echo "  $p/kafka: 리밋 미선언 → 통과 (D3: 무제한은 설정을 강제하지 않는다 — 묵인 아니라 결정)"
    else
      mib="$(u_to_mib "$raw")"
      [ -n "$mib" ] || fail "(u) $p/kafka 의 리밋 '$raw' 를 파싱하지 못했습니다 — **가드가 공허합니다.**"
      u_declared_n=$(( u_declared_n + 1 ))
      [ -z "$u_rep_slug" ] && u_rep_slug="$p"
      if [ "$mib" -ge "$U_MIN_LIMIT_MIB" ]; then
        echo "  $p/kafka: 리밋 ${mib}MiB ≥ ${U_MIN_LIMIT_MIB}MiB ✅ (힙 $(( mib / 4 )) MiB)"
      else
        echo "  $p/kafka: 리밋 ${mib}MiB < ${U_MIN_LIMIT_MIB}MiB ❌"
        u_fail="$u_fail  $p/kafka=${mib}MiB"
      fi
    fi
    break   # 프로젝트당 브로커 하나
  done
done

# AC-3 커버리지 — 어떤 술어를 고르든 검사되지 않은 브로커 수가 사람에게 보여야 한다.
echo "  커버리지(AC-3): 브로커 ${u_total}개 발견 / 리밋 선언 ${u_declared_n}개(전부 하한 검사) / 미선언 ${u_unlimited_n}개(D3 통과)"

[ "$u_total" -ge 1 ] || fail "(u) kafka 브로커를 하나도 발견하지 못했습니다 — **가드가 공허합니다**(열거가 깨졌습니다)."
[ -z "$u_fail" ] || fail "선언된 kafka 리밋이 ${U_MIN_LIMIT_MIB}MiB 하한 미만:$u_fail"\
  $'\n'"→ **리밋은 상한이 아니라 설정입니다.** KAFKA_HEAP_OPTS 가 없으면 JVM 이 cgroup 을 읽어"\
  $'\n'"   힙을 리밋의 25% 로 잡습니다 (512M → 힙 128 MiB). '512M' 은 \"512MB 까지\" 가 아니라"\
  $'\n'"   \"**128 MiB 힙으로 브로커를 돌려라**\" 입니다."\
  $'\n'"→ **살아 있다는 것은 증거가 아닙니다.** 512M kafka 는 격리 상태에서 healthy 였고, 함대"\
  $'\n'"   12개 서비스가 붙자 cgroup OOM 으로 14회 재시작했습니다(MONO-397)."\
  $'\n'"→ 해당 compose 의 memory 리밋을 ${U_MIN_LIMIT_MIB}MiB 이상으로 올리세요."

# ── AC-2 픽스처 — 술어가 실제로 무는지 증명한다 (초록은 물었다는 증거가 아니다) ──
# 가드 (u) 자신이 첫 판본에서 리밋을 못 읽고 공허 통과한 전례가 이 파일에 적혀 있다.
# 같은 판별식(u_declared_limit_of + u_to_mib)에 위반 픽스처(512M)를 먹여 RED 판정을 확인한다.
u_fx="$(mktemp)"
printf '%s\n' 'services:' '  kafka:' '    image: apache/kafka:3.7.0' \
  '    deploy:' '      resources:' '        limits:' '          memory: 512M' \
  '  zookeeper:' '    image: x' > "$u_fx"
u_fx_mib="$(u_to_mib "$(u_declared_limit_of "$u_fx")")"
rm -f "$u_fx"
[ "$u_fx_mib" = "512" ] || fail "(u) AC-2 픽스처 파싱 오류: 512M → '${u_fx_mib}'MiB (512 기대) — 판별식이 깨졌습니다."
[ "$u_fx_mib" -lt "$U_MIN_LIMIT_MIB" ] || fail "(u) AC-2 픽스처 술어 오류: 512MiB 가 하한 미만으로 판정되지 않았습니다."
echo "  (AC-2) 픽스처: 512M 선언 → ${u_fx_mib}MiB < ${U_MIN_LIMIT_MIB}MiB 로 판정 = 가드가 문다 ✅"

ok "선언된 kafka 리밋 ${u_declared_n}개 전부 ≥ ${U_MIN_LIMIT_MIB}MiB · 미선언 ${u_unlimited_n}개 D3 통과 (브로커 ${u_total}개, MONO-399 가 실측으로 D3 를 재검토하면 이 판정을 뒤집을 수 있다)"

# ── 대표 실기동 — 선언된 리밋으로 실제로 부팅하는가 (호스트 무관하게 참인 명제만 단언) ──
# 정적 하한(위)은 전 브로커를 덮지만, "그 리밋으로 브로커가 실제로 부팅한다"는 명제는
# **대표 1개**로 충분하다(Edge Case: 실기동은 브로커 수만큼 CI 시간을 선형 증가시킨다).
# 리밋을 선언한 첫 브로커를 render 해서 image/env/limit 의 **실효값**을 읽어 띄운다
# (advertised listener 는 `kafka` 를 가리키므로 network-alias 로 맞춘다).
u_rep_slug="${u_rep_slug:-ecommerce}"
echo "  실기동 대표: ${u_rep_slug}/kafka (나머지 브로커는 위의 정적 하한만 검사)"
u_render="$(render "$u_rep_slug")"
[ -n "$u_render" ] || fail "(u) ${u_rep_slug} compose 렌더 실패 — 대표 실기동이 공허합니다."
u_block="$(printf '%s\n' "$u_render" | awk '/^  kafka:$/{f=1;next} /^  [A-Za-z0-9._-]+:$/{f=0} f')"
u_image="$(printf '%s\n' "$u_block" | awk '/^[[:space:]]*image:[[:space:]]/{print $2; exit}')"
u_limit="$(printf '%s\n' "$u_block" | awk '/^[[:space:]]*memory:[[:space:]]/{gsub(/"/,"",$2); print $2; exit}')"
[ -n "$u_image" ] || fail "(u) ${u_rep_slug} 렌더에서 kafka image 를 못 읽었습니다 — **대표 실기동이 공허합니다.**"
[ -n "$u_limit" ] || fail "(u) ${u_rep_slug} 렌더에서 kafka memory 리밋을 못 읽었습니다 — **대표 실기동이 공허합니다.**"
u_limit_mib=$(( u_limit / 1048576 ))
u_envs=()
while IFS= read -r line; do
  [ -n "$line" ] && u_envs+=(-e "$line")
done < <(printf '%s\n' "$u_block" | awk '
  /^    environment:$/ { e=1; next }
  e && /^      [A-Z_]+:/ { k=$1; sub(":","",k); $1=""; sub(/^ /,""); gsub(/^"|"$/,""); print k "=" $0; next }
  e && /^    [a-z]/ { e=0 }')
[ "${#u_envs[@]}" -ge 5 ] || fail "(u) kafka env 를 ${#u_envs[@]}개밖에 못 읽었습니다 — **파싱이 깨졌습니다.**"

docker network create "$u_net" >/dev/null 2>&1 || true
docker run -d --name "$u_ctr" --network "$u_net" --network-alias kafka \
  --memory "$u_limit" "${u_envs[@]}" "$u_image" >/dev/null \
  || fail "(u) kafka 기동 실패 (image=$u_image limit=${u_limit_mib}MiB)"

u_up=0
for _ in $(seq 1 40); do
  if docker exec "$u_ctr" sh -c '/opt/kafka/bin/kafka-topics.sh --list --bootstrap-server localhost:9092' >/dev/null 2>&1; then u_up=1; break; fi
  sleep 3
done
[ "$u_up" = "1" ] || fail "(u) kafka 가 ${u_limit_mib}MiB 리밋에서 기동조차 못 했습니다."

# JVM 이 이 cgroup 에서 실제로 보는 힙 — **관측이다. 단언하지 않는다.**
# 러너에서는 컨테이너 안의 JVM 이 cgroup 리밋을 못 봐서 3998 MiB 를 돌려줬다(#2534).
# 왜 그런지는 이 가드의 관심사가 아니지만, **그 값을 판정에 쓸 수 없다는 것**은 관심사다.
u_heap_bytes="$(docker exec "$u_ctr" sh -c \
  'java -XX:+PrintFlagsFinal -version 2>/dev/null | awk "/ MaxHeapSize /{print \$4}"' | tr -d '[:space:]' || true)"
case "${u_heap_bytes:-}" in
  ''|*[!0-9]*) echo "  (관측) 컨테이너 내 JVM MaxHeapSize: 읽기 실패" ;;
  *) echo "  (관측) 컨테이너 내 JVM MaxHeapSize: $(( u_heap_bytes / 1048576 )) MiB   ← 러너에서는 cgroup 을 못 본다. 판정에 쓰지 않는다." ;;
esac

# 부하 — 토픽 10 × 파티션 3, 2000 × 1KB. 볼륨보다 **파티션 수**가 RSS 를 지배한다.
docker exec "$u_ctr" sh -c '
  for i in $(seq 1 10); do /opt/kafka/bin/kafka-topics.sh --create --topic t$i --partitions 3 --replication-factor 1 --bootstrap-server localhost:9092 >/dev/null 2>&1; done
  for i in $(seq 1 10); do /opt/kafka/bin/kafka-producer-perf-test.sh --topic t$i --num-records 2000 --record-size 1024 --throughput -1 --producer-props bootstrap.servers=localhost:9092 >/dev/null 2>&1; done' >/dev/null 2>&1

# **부하가 실제로 들어갔는지 먼저 확인한다.** 안 들어간 채로 낮은 사용률을 재면
# 이 가드는 초록을 내면서 아무것도 보지 않은 것이다 (MONO-389 에서 여러 번 겪었다).
u_msgs="$(docker exec "$u_ctr" sh -c '/opt/kafka/bin/kafka-get-offsets.sh --bootstrap-server localhost:9092 --topic t5 2>/dev/null' | awk -F: '{s+=$3} END{print s+0}')"
[ "${u_msgs:-0}" -ge 2000 ] || fail "(u) 부하가 들어가지 않았습니다 (t5 메시지=${u_msgs:-0}, 2000 기대)."\
  $'\n'"→ **무부하 상태의 사용률은 이 가드가 묻는 질문의 답이 아닙니다.**"

sleep 8
u_used="$(docker stats "$u_ctr" --no-stream --format '{{.MemUsage}} ({{.MemPerc}})')"
u_rc="$(docker inspect "$u_ctr" --format '{{.RestartCount}}')"

# RSS 는 **관측이지 단언이 아니다.** 같은 컨테이너·리밋·부하인데 호스트마다 2.2배 다르다
# (Windows/WSL2 512M → 83.2% ↔ ubuntu 러너 512M → 38.2%). 그 위에 임계를 세우면 러너에서
# 512M 과 1G 가 갈리지 않아 **가드가 장식이 된다** — #2533 이 그걸 실측으로 보여줬다.
# 남겨 두는 이유는 사람이 값의 추이를 볼 수 있어서다. 판정은 위의 **힙**이 한다.
echo "  (관측) 부하 후 메모리: $u_used   ← 호스트마다 다르다. 단언에 쓰지 않는다."

# 부하 중에 죽었다면 그건 호스트와 무관하게 결함이다.
[ "$u_rc" = "0" ] || fail "(u) kafka 가 부하 중 ${u_rc}회 재시작했습니다 — 리밋이 명백히 부족합니다."
ok "kafka 부하 완주 (RestartCount=0, t5 메시지=${u_msgs})"
cleanup_u

# ---------------------------------------------------------------------------
echo "[verify] (v) admin-service 가 operator-토큰 교환 검증 env 를 배선하는가"
# ---------------------------------------------------------------------------
# 근거(MONO-456): 콘솔 로그인은 OIDC 폼 뒤에 operator-토큰 교환을 한 번 더 한다
# (POST /api/admin/auth/token-exchange). admin-service 의 subject-token validator
# (IamOidcProperties, @ConfigurationProperties(prefix="admin.oidc"))가 읽는
# issuer/jwks-uri 를 데모가 주입하지 않으면 둘 다 localhost:8081 기본값으로 폴백한다
# → JWKS fetch 실패 + iss 불일치 → 401 "Subject token verification failed".
# (l) 이 브라우저 OIDC 도달성(로그인 전반부)을 지키듯, 이 가드는 operator-교환
# 도달성(후반부)을 지킨다. 한쪽만 있으면 폼은 뜨는데 셸엔 못 들어가는, 가장 진단하기
# 나쁜 모양이 된다 — 컨테이너는 전부 healthy 하고 /login 도 200 이다.
#
# 술어는 **key 형태로 앵커한다**: 위 env 블록의 주석이 `ADMIN_OIDC_*` 를 산문으로
# 언급하므로 substring grep 은 실제 env 라인을 지워도 통과한다(대리지표). 커밋될 blob
# 자체 — `^  ADMIN_OIDC_ISSUER:` 라는 YAML key — 를 물어야 이 가드가 무는 것이다.
ov="$ROOT/infra/demo/iam-traefik.override.yml"
adm_missing=""
grep -qE '^[[:space:]]*ADMIN_OIDC_ISSUER:[[:space:]]'   "$ov" || adm_missing="$adm_missing ADMIN_OIDC_ISSUER"
grep -qE '^[[:space:]]*ADMIN_OIDC_JWKS_URI:[[:space:]]' "$ov" || adm_missing="$adm_missing ADMIN_OIDC_JWKS_URI"
[ -z "$adm_missing" ] || fail "iam-traefik.override.yml 이 admin-service subject-token validator env 를 빠뜨렸습니다:$adm_missing"\
  $'\n'"→ 없으면 validator 가 localhost:8081 기본값으로 폴백해 operator-토큰 교환이 401 로 죽습니다"\
  $'\n'"   — 컨테이너는 전부 healthy 하고 로그인 폼도 뜨지만 콘솔 셸엔 못 들어갑니다(MONO-456)."\
  $'\n'"→ admin-service.environment 에 ADMIN_OIDC_ISSUER(공개 호스트, 토큰 iss 와 문자열 일치) +"\
  $'\n'"   ADMIN_OIDC_JWKS_URI(컨테이너 DNS /internal/auth/jwks) 를 넣으세요."
ok "admin-service subject-token validator env(issuer+jwks) 유지"

# ---------------------------------------------------------------------------
echo "[verify] (z5) 라벨 드리프트 탐지가 실제로 무는가 (대조군 포함)"
# ---------------------------------------------------------------------------
# TASK-MONO-553 (C). `check-label-drift.sh` 는 이 결함의 **직접적인 술어**다 —
# 재시작 뒤 라우터 라벨이 옛 공인 IP 를 가리키면 그 컨테이너는 **옛 주소로만** 열린다.
#
# 🔴 이 가드는 라이브 구간에 있다. 정적으로는 물릴 수 없다: 술어의 입력이 **실행 중인
#    컨테이너의 라벨**이기 때문이고, 소스를 grep 하는 술어는 이 저장소 주석에 널린 예시
#    호스트명에 **자기 자신이 걸린다**(가드가 자기 문서를 물면 그것은 술어가 아니다).
#
# 세 칸을 본다 — 하나라도 빠지면 판정이 공허하다:
#   (1) 현재 도메인만 있는 상태에서 **안 무는가**   ← 대조군. 늘 무는 탐지기는 탐지기가 아니다.
#   (2) 기동 대상에 옛 도메인을 넣으면 **무는가**   ← bite
#   (3) 기동 대상 **밖**의 옛 도메인은 실패로 세지 않는가
#       ← `demo-core` 부팅에서 나머지 4개 도메인이 옛 라벨로 남는 것은 정상이다.
#         이걸 실패로 세면 정상 부팅이 빨개지고, 빨개지는 가드는 꺼진다.
z5_cur="1-2-3-4.sslip.io"
z5_old="9-9-9-9.sslip.io"
z5_drift="$ROOT/infra/demo/check-label-drift.sh"
[ -f "$z5_drift" ] || fail "(z5) infra/demo/check-label-drift.sh 가 없습니다 — 라벨 드리프트 판정이 사라졌습니다(TASK-MONO-553 C)."
z5_out="$(mktemp)"

cleanup_z5() { docker rm -f z5-cur z5-old z5-out >/dev/null 2>&1 || true; rm -f "$z5_out"; }
trap cleanup_z5 EXIT
cleanup_z5

z5_mk() {  # $1=이름 $2=compose 프로젝트 $3=호스트명
  docker run -d --name "$1" \
    --label "com.docker.compose.project=$2" \
    --label "traefik.http.routers.z5.rule=Host(\`console.$3\`)" \
    busybox sleep 300 >/dev/null
}
z5_run() { local rc=0; DEMO_DOMAIN="$z5_cur" bash "$z5_drift" iam wms > "$z5_out" 2>&1 || rc=$?; echo "$rc"; }

# (1) 대조군
z5_mk z5-cur iam "$z5_cur"
z5_ctl="$(z5_run)"
[ "$z5_ctl" = "0" ] || fail "(z5) 대조군 실패 — 라벨이 전부 현재 도메인인데 드리프트로 판정했습니다 (rc=$z5_ctl)."\
  $'\n'"$(cat "$z5_out")"

# (2) bite
z5_mk z5-old iam "$z5_old"
z5_bite="$(z5_run)"
[ "$z5_bite" != "0" ] || fail "(z5) bite 실패 — 기동 대상(iam)에 옛 도메인($z5_old) 라벨 컨테이너가 있는데 통과했습니다."\
  $'\n'"→ 이 상태가 2026-08-17 실증의 상태입니다: 컨테이너는 전부 healthy 하고 새 주소는 404 입니다."
if ! grep -q 'z5-old' "$z5_out"; then
  fail "(z5) 드리프트를 탐지했지만 **어느 컨테이너인지** 대지 않습니다 — 진단할 수 없는 실패입니다."
fi
if grep -q 'z5-cur' "$z5_out"; then
  fail "(z5) 현재 도메인 라벨 컨테이너(z5-cur)를 드리프트로 고발했습니다 — 술어가 도메인을 비교하지 않고 있습니다."
fi

# (3) 기동 대상 밖은 경고, 실패 아님
docker rm -f z5-old >/dev/null 2>&1 || true
z5_mk z5-out fan "$z5_old"
z5_out_rc="$(z5_run)"
[ "$z5_out_rc" = "0" ] || fail "(z5) 이번 기동 대상이 아닌 도메인(fan)의 옛 라벨을 **실패**로 셌습니다 (rc=$z5_out_rc)."\
  $'\n'"→ demo-core 부팅에서는 나머지 4개 도메인이 옛 라벨로 남는 것이 정상입니다. 정상 부팅이 빨개집니다."
if ! grep -q 'z5-out' "$z5_out"; then
  fail "(z5) 기동 대상 밖의 드리프트를 **조용히** 넘겼습니다 — 경고로라도 이름을 대야 합니다."
fi

cleanup_z5
trap - EXIT
ok "라벨 드리프트 탐지 — 대조군 rc=0 · bite rc=$z5_bite(이름 명시) · 기동 대상 밖은 경고만 rc=0"

# ---------------------------------------------------------------------------
echo "[verify] (z7) 정상 종료한 일회성 작업이 실패로 계상되지 않는가 (대조군 포함)"
# ---------------------------------------------------------------------------
# TASK-MONO-551 결함 A. 이전 술어는 `total = docker ps -a`(종료 포함) / `healthy = running`
# 이었다. 그래서 **정상적으로 끝난 init 컨테이너가 영원히 실패로 계상**됐다:
#   ecommerce-minio-init · wms-kafka-init · iam-kafka-init — 전부 `Exited (0)`
#   ⇒ iam 14/15 · wms 16/17 · ecommerce 33/34 = 정상인데 **영구 partial**.
# 그 세 도메인은 **어떤 상태에서도 up 이 될 수 없었다.**
#
# 🔴 대조군이 이 가드의 본체다. `Exited (0)` 이 up 이 되는 것만 보면 **"exited 는 다
#    봐준다"** 는 구현과 구별되지 않고, 그 구현은 **크래시 루프를 초록으로 만든다** —
#    이 고침이 만들 수 있는 최악의 결과다. 그래서 같은 자리를 `Exited (1)` 로 바꿔
#    partial 로 떨어지는지 함께 본다.
#
# 🔴 그리고 **모집단부터 단언한다.** 이 가드를 쓰다가 실제로 당했다: 남아 있던 `erp`
#    컨테이너 5개(전부 `Exited (137)`)가 슬러그를 오염시켜 고침 전/후가 **똑같이 partial**
#    로 나왔고, 하마터면 "안 고쳐졌다" 로 읽을 뻔했다. 빈 슬러그임을 먼저 증명한다.
z7_slug=finance      # 라이브 가드 (f) 는 scm·fan 을 쓴다 — 겹치지 않는 슬러그를 고른다
z7_verdict() { bash "$ROOT/infra/demo/demo-status.sh" \
                 | grep -o "\"$z7_slug\":{\"state\":\"[a-z]*\"" | sed 's/.*"state":"//;s/"//'; }

cleanup_z7() { docker rm -f z7-svc z7-init z7-bad >/dev/null 2>&1 || true; }
trap cleanup_z7 EXIT
cleanup_z7

z7_pre="$(docker ps -a --filter "label=com.docker.compose.project=$z7_slug" -q | wc -l | tr -d ' ')"
[ "$z7_pre" = "0" ] || fail "(z7) 모집단 오염 — 슬러그 '$z7_slug' 에 이미 컨테이너 $z7_pre 개가 있습니다."\
  $'\n'"→ 남의 컨테이너가 섞이면 판정이 이 가드가 만든 조건을 반영하지 않습니다(통과도 실패도 무효)."

docker run -d --name z7-svc --label "com.docker.compose.project=$z7_slug" busybox sleep 300 >/dev/null
docker run -d --name z7-init --label "com.docker.compose.project=$z7_slug" busybox sh -c 'exit 0' >/dev/null
# exited 로 확정될 때까지 기다린다 — `created`/`running` 상태로 읽히면 판정이 엉뚱해진다.
z7_wait=0
while [ "$(docker inspect -f '{{.State.Status}}' z7-init 2>/dev/null)" != "exited" ] && [ "$z7_wait" -lt 30 ]; do
  sleep 1; z7_wait=$(( z7_wait + 1 ))
done
[ "$(docker inspect -f '{{.State.Status}}' z7-init)" = "exited" ] \
  || fail "(z7) 주입 실패 — z7-init 이 exited 로 가지 않았습니다. 판정 전에 조건이 안 섰습니다."
[ "$(docker inspect -f '{{.State.ExitCode}}' z7-init)" = "0" ] \
  || fail "(z7) 주입 실패 — z7-init 의 종료코드가 0 이 아닙니다. 세우려던 조건이 아닙니다."

z7_ok="$(z7_verdict)"
[ "$z7_ok" = "up" ] || fail "(z7) 정상 종료한 일회성 작업(Exited 0)이 여전히 실패로 계상됩니다 — '$z7_slug' = $z7_ok"\
  $'\n'"→ iam·wms·ecommerce 는 이 술어 아래에서 **어떤 상태에서도 up 이 될 수 없습니다.**"\
  $'\n'"→ 런처는 면접관에게 항상 노란 배지 3개를 보여줍니다(MONO-551 A)."

# 대조군 — 같은 자리를 Exited (1) 로.
docker rm -f z7-init >/dev/null 2>&1 || true
docker run -d --name z7-bad --label "com.docker.compose.project=$z7_slug" busybox sh -c 'exit 1' >/dev/null
z7_wait=0
while [ "$(docker inspect -f '{{.State.Status}}' z7-bad 2>/dev/null)" != "exited" ] && [ "$z7_wait" -lt 30 ]; do
  sleep 1; z7_wait=$(( z7_wait + 1 ))
done
[ "$(docker inspect -f '{{.State.ExitCode}}' z7-bad)" = "1" ] \
  || fail "(z7) 대조군 주입 실패 — z7-bad 의 종료코드가 1 이 아닙니다."
z7_bad="$(z7_verdict)"
cleanup_z7
trap - EXIT
[ "$z7_bad" != "up" ] || fail "(z7) 대조군 실패 — Exited (1) 컨테이너가 있는데도 '$z7_slug' 가 up 입니다."\
  $'\n'"→ 종료코드를 안 보고 'exited 는 다 봐주는' 구현입니다. **크래시 루프가 초록이 됩니다** —"\
  $'\n'"   이 티켓이 만들 수 있는 최악의 결과이고, 대조군은 정확히 그것을 막으려고 있습니다."
ok "일회성 작업 계상 — Exited(0) → up · 대조군 Exited(1) → $z7_bad (모집단 사전 확인 0개)"

echo "[verify] 전체 PASS (정적 + 실기동 증명)"
