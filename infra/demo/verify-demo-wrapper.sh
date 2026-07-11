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
#   bash infra/demo/verify-demo-wrapper.sh          # 정적 (a)~(e),(g)
#   bash infra/demo/verify-demo-wrapper.sh --live   # + (f) 실기동 증명
# =============================================================================
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
# shellcheck source=infra/demo/projects.sh
source "$HERE/projects.sh"
# shellcheck source=infra/demo/demo.env
set -a; source "$HERE/demo.env"; set +a

LIVE=0
[ "${1:-}" = "--live" ] && LIVE=1

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

ok "이미지 ${img_ok}개 확인됨"
if [ "$img_skip" -gt 0 ]; then
  echo "  ⚠ ${img_skip}개는 확인하지 못했습니다(레지스트리 사정 — 결함 아님):" >&2
  printf '%s' "$img_skip_list" >&2
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
grep -q 'seed-demo-domain.sh' "$ROOT/infra/demo/demo-up.sh" \
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

echo "[verify] 전체 PASS (정적 + 실기동 증명)"
