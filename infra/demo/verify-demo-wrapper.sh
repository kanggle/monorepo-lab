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

# ---------------------------------------------------------------------------
# grepq — 파이프 뒤에서 쓰는 `grep -q` 대체 (TASK-MONO-615)
# ---------------------------------------------------------------------------
# 🔴🔴 `A | grep -q PAT` 는 이 파일의 `set -o pipefail` 아래에서 **매치했는데 실패**한다.
# `grep -q` 는 첫 매치에서 즉시 끝나며 읽는 쪽 파이프를 닫는다. 앞단(sed/printf)이 아직
# 쓸 것이 남아 있으면 그 write 가 EPIPE/SIGPIPE 로 죽어 **141** 을 내고, `pipefail` 이
# 파이프라인의 결과를 141 로 만든다. 즉 술어는 참인데 가드는 FAIL 을 외친다.
#
# 실측 (2026-09-03, 데모 호스트, GNU grep/sed):
#   sed 578줄 → grep -q (매치 334행)   : 40회 중 **6회** 실패 (rc=141)
#   printf 25,476B → grep -q (앞쪽 매치): 300회 중 **295회** 실패
#   같은 자리에서 `-q` 만 빼면          : 60회 중 **0회** 실패
#
# 🔴 이것이 실제로 일으킨 일: 가드 (k) 가 *"demo-up.sh 가 seed-demo-domain.sh 를 호출하지
#    않습니다"* 라고 **거짓 고발**했다. 호출은 334행에 멀쩡히 있었다. 그리고 그 FAIL 이
#    실행을 중단시켜 뒤의 --live 칸((z18)·(z21))이 **미도달**로 남았다 — 이 저장소가
#    이미 두 번 당한 모양이다(B3 의 (w) 가 (z18)을 가린 그 자리, 그리고 (w) 가 (z12)를
#    가린 자리). 첫 실패가 뒤를 덮는다.
#
# 🔵 `-q` 를 빼면 grep 은 입력을 끝까지 읽는다. 그것이 고침의 전부다 — 여기서 읽는 것은
#    수십 KB 라 비용은 무시할 수 있고, 대신 「앞단이 SIGPIPE 를 받을 창」이 사라진다.
# 🔵 종료코드 의미는 그대로다(매치 0 → 1). 출력만 버린다.
# 가드 (z25)가 파이프 뒤의 `grep` 플래그 `-q` 가 이 파일에 다시 들어오는 것을 막는다.
grepq() { grep "$@" >/dev/null; }
fail() { echo "  FAIL: $*" >&2; exit 1; }
ok()   { echo "  ok: $*"; }

# -----------------------------------------------------------------------------
# LIVE 게이트 앵커 — 🔴 **한 곳에만 적는다** (TASK-MONO-609)
# -----------------------------------------------------------------------------
# `TASK-MONO-608` 이 (z16) 의 앵커를 «열 0 + 전체 줄» 로 고쳤지만 그때 (z2) 는 범위 밖이었다.
# 그래서 이 파일에 **서로 다른 두 앵커**가 남았고, (z2) 의 느슨한 쪽은 이 파일의 **주석**에
# 먼저 걸려 자기 정적 구간을 **215줄 짧게** 잡고 있었다(608 AC-3 이 셌다. 당시 두 경계가
# 뽑는 도구 집합이 우연히 같아서 아무 증상도 없었다 — **잠복이지 부재가 아니다**).
#
# 🔴 리터럴을 다시 여러 곳에 적으면 다음에 또 한쪽만 고쳐진다. 그래서 **한 상수**로 둔다.
#    608 시점에 이 리터럴은 코드에 **5곳**(z2 1 + z16 4) 있었다.
# 🔵 정규식이 아니라 **고정 문자열**이다. `grep -x -F` 와 awk 의 `$0 == gate` 가 같은 뜻으로
#    읽으며, awk 의 **동적 정규식 이스케이프**(`\[`·`\$` 를 awk 가 어떻게 읽는지)에 의존하지 않는다.
LIVE_GATE_LINE='if [ "$LIVE" -eq 0 ]; then'

# 게이트 줄 번호를 stdout 으로. **종료코드가 «몇 개였나» 를 말한다**:
#   0 = 정확히 하나(줄 번호 출력) · 1 = 없음 · 2 = 둘 이상(개수 출력)
# 🔴 판정 **문구**는 부르는 쪽이 정한다 — 칸마다 그 사실이 뜻하는 결함이 다르다.
#    다만 «둘 이상 = 판정 불가» 라는 **결론은 같아야** 한다(둘의 판정이 갈리면 안 된다).
live_gate_line() { # $1=파일
  local n
  n="$(grep -c -x -F "$LIVE_GATE_LINE" "$1" || true)"
  case "$n" in
    0) return 1 ;;
    1) grep -n -x -F "$LIVE_GATE_LINE" "$1" | cut -d: -f1; return 0 ;;
    *) echo "$n"; return 2 ;;
  esac
}

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
sed 's/#.*//' "$ROOT/infra/demo/demo-up.sh" | grepq 'seed-demo-domain\.sh' \
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
    printf '%s\n' "$declared" | grepq -x "$name" || ghost="$ghost   terraform output $name"$'\n'
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
  $'\n'"→ destroy/재생성 한 번이면 죽습니다. 유일한 출처는 \`terraform output api_base_url\` 입니다."\
  $'\n'"→ (이 문구는 TASK-MONO-579 에서 고쳤다: 예전엔 \`site_url\` 을 가리켰는데 그 output 은 CloudFront 판과 함께 폐기됐다."\
  $'\n'"   가드의 실패 메시지도 **지시**이므로 같이 썩는다 — 그리고 (q) 는 자기 자신을 안 본다.)"
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

# 경로 B — **선언된 공개 IdP** (TASK-MONO-615 B3 / ADR-MONO-069 C2)
# ---------------------------------------------------------------------------
# C2 아래서 IdP 는 고정 공개 이름 뒤에 서고 Vercel 이 TLS 를 끝낸다. 그 이름은 docker
# alias 가 **아니므로** (1)~(3)이 만든 이름 집합에 영원히 없다 — 그런데 그것이 의도다.
# 실측(TASK-MONO-615, 뒤집힌 demo.env 로 이 스크립트를 그대로 실행): 이 칸은
# `fan:membership-service` **한 건**에서 FAIL 했다. 그 서비스만
# `INTERNAL_JWT_JWK_SET_URI=${IAM_PUBLIC_URL}/oauth2/jwks` 를 받기 때문이다
# (projects/fan-platform/docker-compose.yml:193). 그리고 그 FAIL 이 실행을 중단시켜
# 뒤의 칸들이 **미도달**로 남았다 — `TASK-MONO-606` AC-4′ ②가 그렇게 못 재졌다.
#
# 🔴 **완화가 아니라 좁은 면제다.** 면제되는 호스트는 **정확히 하나**이고 저장소에 박지
#    않는다 — `demo.env` 자신의 `IAM_PUBLIC_URL` 에서 파생한다. 그리고 **`https` 일
#    때만** 면제한다. 기본값 `http://iam.${DEMO_DOMAIN}` 아래서는 이 절이 **완전히
#    불활성**이고(아래 ok 줄의 카운트가 0), 활성화되는 시점은 정확히 C3(뒤집기)이
#    랜딩될 때다. 그래서 이 변경 자체는 오늘의 판정을 하나도 바꾸지 않는다.
#
# 🔴 **정적으로 증명되는 것이 줄어든다는 사실을 감추면 그게 완화다.** 경로 A 는
#    「이름이 컨테이너 fabric 안에서 해소된다」를 증명했다. 경로 B 는 「그 이름이 데모가
#    선언한 공개 IdP 다」까지만 증명한다 — **실제 도달성은 라이브 축의 몫**이고, 그 공백은
#    `TASK-MONO-615` B3 § 남은 공백에 이름이 적혀 있다(「JVM 이 그 JWKS 로 토큰을 실제로
#    검증했다」는 아직 미측정). 여기서 그 공백을 메운 척하지 않는다.
#
# 🔴 MONO-507 의 원래 근거는 그대로 살아 있다 — 해소 실패가 401 로 위장한다는 것.
#    술어를 **지우지 않고** 두 번째 경로를 더한 이유다.
w_public_idp=""
case "${IAM_PUBLIC_URL:-}" in
  https://*) w_public_idp="$(printf '%s' "$IAM_PUBLIC_URL" | sed -E 's#^https://##; s#[:/].*$##')" ;;
esac
w_public_used=0
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
  # 경로 B. 🔴 `=` 비교다 — 접미사 매치가 아니다. `evil-auth.hubwang.com` 도,
  #          `auth.hubwang.com.attacker.tld` 도 통과하지 않는다.
  if [ "$reachable" != 1 ] && [ -n "$w_public_idp" ] && [ "$host" = "$w_public_idp" ]; then
    reachable=1
    w_public_used=$((w_public_used + 1))
  fi
  w_checked=$((w_checked + 1))
  [ "$reachable" = 1 ] || w_bad="$w_bad"$'\n'"  $p:$svc ($mod) → JWKS 호스트 '$host' 가 이 서비스의 네트워크 어디에도 없습니다"
done < <(awk -F'|' '$1 == "M"' "$w_svc")

[ "$w_checked" -gt 0 ] || fail "(w) 데모 compose 에서 리소스 서버 서비스를 한 개도 매칭하지 못했습니다 — build.context ↔ 모듈 조인이 깨졌습니다."
# 🔴 면제가 모집단 **전체**를 덮으면 경로 A 는 한 번도 실행되지 않은 것이고, 이 칸은
#    「이름이 해소되는가」를 더 이상 재지 않는다. 그건 통과가 아니라 **재설계 신호**다.
#    실측 기준선: 뒤집힌 demo.env 에서 면제 대상은 리소스 서버 중 **1건**이었다.
[ "$w_public_used" -lt "$w_checked" ] || fail "(w) 공개 IdP 면제가 리소스 서버 ${w_checked}건 **전부**를 덮었습니다."\
  $'\n'"→ 그러면 이 칸은 도커 이름 해소를 하나도 재지 않습니다 — 통과가 아니라 가드가 죽은 것입니다."\
  $'\n'"→ 전부가 공개 IdP 를 쓰는 배치라면 이 가드의 축 자체를 다시 설계해야 합니다(라이브 fetch 로 옮기세요)."
[ -z "$w_bad" ] || fail "(w) JWKS 를 fetch 할 수 없는 리소스 서버가 있습니다:$w_bad"\
  $'\n'"→ 이 서비스들은 **모든 요청을 401 \"Authentication required\" 로 떨굽니다.** 토큰이 완벽해도 그렇습니다"\
  $'\n'"   — Spring 이 JWKS fetch 실패(UnknownHost)를 fail-closed 로 401 로 바꾸기 때문입니다."\
  $'\n'"→ 게이트웨이는 토큰을 **수락한 뒤** 뒤로 넘기므로, 증상은 '엣지가 좋은 토큰을 거부한다' 로 보입니다."\
  $'\n'"→ 해당 프로젝트의 infra/demo/<slug>-identity.override.yml 에 그 서비스를 추가하고"\
  $'\n'"   infra/demo/projects.sh 의 COMPOSE[<slug>] 에 그 파일이 들어 있는지 확인하세요."
ok "리소스 서버 ${w_checked}개 전부 자기 JWKS 호스트를 해소 가능 (선언 모듈 $(wc -l < "$w_rs" | tr -d ' ')개 중 데모에 뜨는 것 · 도커 이름 $((w_checked - w_public_used))건 · 선언된 공개 IdP 면제 ${w_public_used}건${w_public_idp:+ ($w_public_idp)})"

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
printf '%s' "$x_render" | grepq -E '^      SPRING_PROFILES_ACTIVE:' \
  || fail "(x) ecommerce 렌더에서 payment-service 의 SPRING_PROFILES_ACTIVE 를 찾지 못했습니다 — 탐지식이 깨졌습니다."
# 🔴🔴 모집단이 **줄었다** — TASK-MONO-604 가 데모에서 web-store 를 억제했다.
# 그러면 이 가드의 «프런트 절반» 은 렌더에서 사라진다. 여기서 두 가지를 다 피해야 한다:
#   · 그냥 통과시키면 → 빈 문자열 둘이 "둘 다 꺼짐" 으로 **공허 합격**한다.
#   · 그냥 FAIL 시키면 → 소유자가 Vercel env 를 넣기 전까지 main 이 **영구 빨강**이고,
#     빨간 가드는 꺼진다(TASK-MONO-360).
# ⇒ 사라진 것이 **선언된 억제 때문인지** 를 먼저 확인하고, 맞으면 백엔드 절반만 재되
#   프런트 절반이 **어디로 갔는지·누가 들고 있는지**를 매 실행마다 이름으로 남긴다.
x_store_present=0
printf '%s' "$x_render" | grepq -E '^  web-store:' && x_store_present=1
x_suppressed="infra/demo/ecommerce-vercel.override.yml"

if [ "$x_store_present" = "0" ]; then
  case " ${COMPOSE[ecommerce]:-} " in
    *" $x_suppressed "*) : ;;
    *) fail "(x) 데모 렌더에 web-store 가 없는데, 선언된 억제($x_suppressed)도 체인에 없습니다."\
        $'\n'"→ 즉 «누가 지웠는지 모르는» 상태입니다. 결제 mock 정합의 프런트 절반이 사라졌는데"\
        $'\n'"  사유가 기록돼 있지 않으면, 다음 사람은 이 가드를 «원래 그런 것» 으로 읽습니다." ;;
  esac
else
  printf '%s' "$x_render" | grepq -E '^      DEMO_PAYMENT_MOCK:' \
    || fail "(x) ecommerce 렌더에서 web-store 의 DEMO_PAYMENT_MOCK 를 찾지 못했습니다"\
      $'\n'"→ web-store.environment 에 \`DEMO_PAYMENT_MOCK=\${DEMO_PAYMENT_MOCK:-}\` 가 있어야 합니다."\
      $'\n'"   compose 는 자기가 이름을 적은 변수만 컨테이너에 넣습니다 — demo.env 값만으로는 도달하지 않습니다."
fi

case ",$x_profiles," in
  *,demo-pg,*) x_back=1 ;;
  *)           x_back=0 ;;
esac
[ "$x_flag" = "1" ] && x_front=1 || x_front=0

[ "$x_store_present" = "0" ] || [ "$x_back" = "$x_front" ] || fail "결제 mock 설정이 한쪽만 켜져 있습니다:"\
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

if [ "$x_store_present" = "1" ]; then
  ok "결제 mock 정합 (payment-service='${x_profiles}' ↔ web-store DEMO_PAYMENT_MOCK='${x_flag}')"
else
  # 🔴 «검사했다» 가 아니라 «반쪽만 검사했다» 라고 말한다. 미집행 축은 매 실행마다
  #    이름이 찍혀야 한다 — 조용한 공백은 다음 사람에게 «원래 그런 것» 으로 읽힌다.
  #
  # 🔴🔴 TASK-MONO-612 가 이 축의 처분을 정했다 — **선택지 2: 판정 불가를 명시적으로
  #    수용하고, 문구를 «누가·언제 손으로 확인하는가» 로 바꾼다.** 나머지 둘을 왜 안
  #    골랐는지는 그 티켓에 있다(요약: 토큰 가드는 소유자·보안 사안이고 **그래도 선언만
  #    잰다** — env 변경은 다음 배포부터다 · 프런트 절반을 코드로 유도하는 안은 AC-1 의
  #    방향을 코드로 선점한다).
  # 🔴 **날짜 박힌 실측값을 여기에 넣지 않는다.** 소유자가 값을 넣는 순간 거짓이 되는데
  #    이 스크립트에는 그것을 빨갛게 만들 수단이 없다. 실측값은 원장과 티켓이 든다.
  ok "결제 mock — 백엔드만 검사 (payment-service='${x_profiles}'). 프런트 절반은 저장소 밖이다:"\
     $'\n'"     DEMO_PAYMENT_MOCK 의 집은 Vercel 프로젝트 kanggle-store 의 env 다 — compose 가 아니다."\
     $'\n'"     ⇒ CI 도 이 스크립트도 이 축을 **판정할 수 없다**. TASK-MONO-612 가 그것을 수용했고,"\
     $'\n'"       그래서 판정은 사람이 한다:"\
     $'\n'"         누가·언제 : 소유자가 — 데모 기동 창마다, 그리고 kanggle-store 의 env 를 만질 때마다"\
     $'\n'"         명령      : vercel env ls production --project kanggle-store | grep DEMO_PAYMENT_MOCK"\
     $'\n'"         기대값    : DEMO_PAYMENT_MOCK=1   (백엔드가 demo-pg 인 한 — 위 '${x_profiles}' 가 그 절반이다)"\
     $'\n'"         원장      : projects/ecommerce-microservices-platform/apps/web-store/VERCEL.md"\
     $'\n'"     🔴 env 목록에 있다 = 켜져 있다 가 **아니다**. env 변경은 다음 배포부터 적용되고,"\
     $'\n'"       최종 판정은 /api/store-config 가 {\"demoPayment\":true} 를 내는 것이다(로그인 필요)."
fi

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
printf '%s' "$x2_render" | grepq -E '^      SPRING_PROFILES_ACTIVE:' \
  || fail "(x2) fan 렌더에서 membership-service 의 SPRING_PROFILES_ACTIVE 를 찾지 못했습니다 — 탐지식이 깨졌습니다."

# ---------------------------------------------------------------------------
# 🔴🔴 TASK-MONO-618 — 팬 프런트가 렌더에서 사라질 수 있다 (ADR-MONO-067 단계 4)
# ---------------------------------------------------------------------------
# 억제하면 이 등식의 **한쪽이 없어진다.** 형제 (x) 가 단계 2 에서 밟은 자리와 같은
# 모양이지만 **극성이 반대**다 — 아래 미집행 문구에 demo-pg 를 적으면 팬 축에서
# 거짓이 된다(팬의 mock 조건은 portone 이 **꺼져 있는 것**이다).
#
# 🔴 부재를 그냥 통과시키지 않는다: 그 부재가 **선언된 억제 때문인지** 먼저 확인한다.
#    아니면 «누가 지웠는지 모르는» 상태이고, 그것은 통과가 아니라 FAIL 이다.
x2_web_present=0
printf '%s' "$x2_render" | grepq -E '^  fan-platform-web:' && x2_web_present=1
x2_suppressed="infra/demo/fan-vercel.override.yml"

if [ "$x2_web_present" = "0" ]; then
  case " ${COMPOSE[fan]:-} " in
    *" $x2_suppressed "*) : ;;
    *) fail "(x2) 데모 렌더에 fan-platform-web 이 없는데, 선언된 억제($x2_suppressed)도 체인에 없습니다."\
        $'\n'"→ 즉 «누가 지웠는지 모르는» 상태입니다. 팬 결제 mock 정합의 프런트 절반이 사라졌는데"\
        $'\n'"  사유가 기록돼 있지 않으면, 다음 사람은 이 가드를 «원래 그런 것» 으로 읽습니다." ;;
  esac
else
  printf '%s' "$x2_render" | grepq -E '^      DEMO_PAYMENT_MOCK:' \
    || fail "(x2) fan 렌더에서 fan-platform-web 의 DEMO_PAYMENT_MOCK 를 찾지 못했습니다"\
      $'\n'"→ fan-platform-web.environment 에 \`DEMO_PAYMENT_MOCK: \${DEMO_PAYMENT_MOCK:-}\` 가 있어야 합니다."\
      $'\n'"   compose 는 자기가 이름을 적은 변수만 컨테이너에 넣습니다 — demo.env 값만으로는 도달하지 않습니다."
fi

case ",$x2_profiles," in
  *,portone,*) x2_real=1 ;;
  *)           x2_real=0 ;;
esac
[ "$x2_flag" = "1" ] && x2_front=1 || x2_front=0
# 목이 기본이므로 "백엔드가 목인가" = "portone 이 꺼져 있는가".
x2_back_mock=$(( 1 - x2_real ))

[ "$x2_web_present" = "0" ] || [ "$x2_back_mock" = "$x2_front" ] || fail "팬 결제 mock 설정이 한쪽만 켜져 있습니다:"\
  $'\n'"  membership-service SPRING_PROFILES_ACTIVE = '$x2_profiles'  (portone: $x2_real ⇒ 목: $x2_back_mock)"\
  $'\n'"  fan-platform-web   DEMO_PAYMENT_MOCK      = '$x2_flag'  (on: $x2_front)"\
  $'\n'"→ 프런트만 켜짐 = 지어낸 paymentId 를 실 PortOne 어댑터가 거부해 구독이 죽습니다."\
  $'\n'"→ 프런트만 꺼짐 = 백엔드 목이 승인할 준비가 됐는데도 프런트가 'PortOne 키 미설정' 으로"\
  $'\n'"   요청 자체를 보내지 않습니다 (TASK-FAN-FE-015 가 고친 상태가 정확히 이것입니다)."\
  $'\n'"→ 팬은 극성이 ecommerce 와 반대입니다 — 목이 기본이고 portone 이 opt-in 입니다."

if [ "$x2_web_present" = "1" ]; then
  ok "팬 결제 mock 정합 (membership-service='${x2_profiles}' ↔ fan-platform-web DEMO_PAYMENT_MOCK='${x2_flag}')"
else
  # 🔴 «검사했다» 가 아니라 «반쪽만 검사했다» 라고 말한다. 미집행 축은 매 실행마다
  #    이름이 찍혀야 한다 — 조용한 공백은 다음 사람에게 «원래 그런 것» 으로 읽힌다.
  # 🔴 **아무것도 안 하고 문구만 지우는 것은 금지다** (TASK-MONO-618 AC-3).
  #
  # 🔴🔴 **극성이 형제와 반대다.** 여기에 demo-pg 를 쓰면 정반대를 단언한다 —
  #    팬은 목이 기본이고(MockPaymentGatewayAdapter = @Profile("!portone"))
  #    실 PG 가 되려면 portone 을 **켜야** 한다. 그래서 백엔드 절반의 판정은
  #    «portone 이 **없는가**» 이고, 위 x2_profiles 가 그 절반이다.
  # 🔴 **날짜 박힌 실측값을 여기에 넣지 않는다.** 소유자가 값을 넣는 순간 거짓이 되는데
  #    이 스크립트에는 그것을 빨갛게 만들 수단이 없다. 실측값은 원장과 티켓이 든다.
  # 🔴🔴 **그리고 이 주석만으로는 안 지켜졌다** (TASK-MONO-622 실측). TASK-MONO-618 은 이
  #    주석을 형제 (x) 에서 그대로 복사해 놓고 **여섯 줄 아래 메시지에서 어겼다** — 소유자가
  #    값을 넣은 바로 그날 거짓이 되는 현재형 단정을 적었고, 아무것도 그것을 빨갛게 만들지
  #    못했다. 🔴 규칙이 없어서가 아니었다. **주석은 게이트가 아니다.**
  #    ⇒ 칸 **(z29)** 가 이 규칙을 «실행되는 판정» 으로 만든다. 여기에 규칙을 더 크게 쓰는
  #      것으로는 다음 사람도 못 막는다 — 그 방법은 이미 한 번 실패했다.
  ok "팬 결제 mock — 백엔드만 검사 (membership-service='${x2_profiles}' ⇒ 목: ${x2_back_mock}). 프런트 절반은 저장소 밖이다:"\
     $'\n'"     DEMO_PAYMENT_MOCK 의 집은 Vercel 프로젝트 kanggle-fan 의 env 다 — compose 가 아니다."\
     $'\n'"     ⇒ CI 도 이 스크립트도 이 축을 **판정할 수 없다**. TASK-MONO-618 이 그것을 수용했고,"\
     $'\n'"       그래서 판정은 사람이 한다:"\
     $'\n'"         누가·언제 : 소유자가 — 데모 기동 창마다, 그리고 kanggle-fan 의 env 를 만질 때마다"\
     $'\n'"         명령      : vercel env ls production --project kanggle-fan | grep DEMO_PAYMENT_MOCK"\
     $'\n'"         기대값    : DEMO_PAYMENT_MOCK=1   (팬 백엔드에 portone 이 **없는 한** — 위 '${x2_profiles}' 가 그 절반이다)"\
     $'\n'"         원장      : projects/fan-platform/web/fan-platform-web/VERCEL.md"\
     $'\n'"     🔴 env 목록에 있다 = 켜져 있다 가 **아니다**. env 변경은 다음 배포부터 적용되고,"\
     $'\n'"       최종 판정은 /api/payment-config 가 {\"demoPayment\":true} 를 내는 것이다."\
     $'\n'"     🔴 팬은 표면 전체가 로그인 뒤에 있다(middleware 가 fail-closed) — 세션 없이"\
     $'\n'"       부르면 307 로 로그인 페이지가 오고, 그 응답은 «값이 false» 와 **구별되지 않는다**."\
     $'\n'"     🔵 기전(현재 상태가 아니다): 이 값이 없으면 프런트가 'PortOne 키 미설정' 으로"\
     $'\n'"       구독 요청 자체를 보내지 않는다 — TASK-FAN-FE-015 가 고친 상태가 정확히 그것이다."
fi

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
    $'\n'"→ \`dbexec --why \"<사유>\"\` 를 쓰십시오 (infra/demo/seed/lib.sh)."\
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
printf '%s\n' "$z_pub_body" | grepq 'demo-status\.sh' \
  || fail "demo-status-publish.sh 가 demo-status.sh 를 호출하지 않습니다 (주석 제외 본문 기준)."
printf '%s\n' "$z_pub_body" | grepq 'put-parameter' \
  || fail "demo-status-publish.sh 가 put-parameter 를 호출하지 않습니다 (주석 제외 본문 기준)."

# (2) 유닛이 발행자를 부르는가 + 타이머가 그 유닛을 부르는가.
z_svc_exec="$(sed 's/#.*//' "$z_svc" | grep -E '^[[:space:]]*ExecStart=' || true)"
case "$z_svc_exec" in
  *demo-status-publish.sh*) : ;;
  *) fail "demo-status.service 의 ExecStart 가 demo-status-publish.sh 를 부르지 않습니다: ${z_svc_exec:-<없음>}" ;;
esac
sed 's/#.*//' "$z_tmr" | grepq -E '^[[:space:]]*Unit=demo-status\.service' \
  || fail "demo-status.timer 가 Unit=demo-status.service 를 가리키지 않습니다."

# (3) 🔴 AccuracySec 이 없으면 '30초 주기' 는 거짓이다.
#     systemd 기본 AccuracySec 은 1분이라 커널이 타이머를 뭉쳐 깨운다 — 유닛에는
#     30s 라고 적혀 있고 실제 주기는 ~1분이 된다. 페이지가 표시 지연을 정직하게
#     적으라는 티켓 요구의 근거가 여기서 무너지므로, 선언과 실제를 벌리지 않는다.
sed 's/#.*//' "$z_tmr" | grepq -E '^[[:space:]]*AccuracySec=' \
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
  #
  # 🔴 TASK-MONO-609: 예전 앵커는 `grep -n 'if \[ "\$LIVE" -eq 0 \]' | head -1` 이었다. 열을 안 봐서
  #    **첫 매치가 (z16) 의 주석**이었고, 이 구간이 215줄 짧았다. 이제 공용 `live_gate_line` 을 쓴다.
  z2_rc=0
  z2_live="$(live_gate_line "$z2_self")" || z2_rc=$?
  case "$z2_rc" in
    1) fail "(z2) 이 스크립트에서 LIVE 게이트를 찾지 못했습니다 — 정적 구간을 특정할 수 없습니다." ;;
    2) fail "(z2) LIVE 게이트가 ${z2_live}개입니다 — 어느 것이 정적 구간의 끝인지 판정할 수 없습니다."\
         $'\n'"→ (z16) 도 같은 이유로 판정 불가로 세웁니다. 두 칸의 판정이 갈리면 안 됩니다." ;;
  esac

  z2_seen=""; z2_missing=""
  for z2_t in $(head -n "$z2_live" "$z2_self" \
                  | sed -n 's/.*command -v \([a-z0-9_-][a-z0-9_-]*\).*/\1/p' | sort -u); do
    # 도구 이름 ≠ 패키지 이름. 아는 것만 매핑하고 나머지는 동일 이름으로 본다.
    #
    # 🔴 TASK-MONO-615 — 세 번째 부류가 있다: **base 이미지가 보장해서 apt 목록에 없는
    #    것이 정상인 도구.** `timeout` 은 coreutils 이고 Ubuntu 에서 essential 이다.
    #    이걸 모르면 이 칸은 «packer 가 timeout 패키지를 설치하지 않는다» 는 **없는 죄**를
    #    고발한다(실측: (z24) 가 들어온 순간 그렇게 됐다).
    # 🔵 그렇다고 「없어도 된다」로 넘기지 않는다 — 면제의 근거는 «base 에 있다» 이므로
    #    그 근거 자체를 단언한다. 이 스크립트는 packer 7단계에서 **AMI 안에서도** 돌기
    #    때문에, 그 단언은 정확히 AMI 안의 실재를 재는 것이 된다. 근거가 거짓이 되는 날
    #    (base 이미지가 바뀌는 등) 이 칸이 그 자리에서 빨개진다.
    case "$z2_t" in
      timeout|realpath)
        command -v "$z2_t" >/dev/null 2>&1 \
          || fail "(z2) '$z2_t' 을 base 제공(coreutils)으로 분류했는데 이 환경에 없습니다."\
          $'\n'"→ 면제의 근거(coreutils 는 essential)가 거짓입니다. packer 1단계에 coreutils"\
          $'\n'"  설치를 추가하거나, 그 도구를 쓰는 칸의 폴백 경로를 다시 보세요."\
          $'\n'"  (timeout: demo-boot.sh / demo-up.sh · realpath: 가드 (z26)의 경로 정규화)"
        z2_seen="$z2_seen $z2_t(base)"
        continue ;;
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

if ! printf '%s\n' "$z4_log" | grepq '^\[demo\] up: console'; then
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
if ! printf '%s\n' "$z4_log" | grepq 'wms'; then
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
  printf '%s' "$1" | grepq -E '^\{"published_at":[0-9]+,"domains":\{' || return 1
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
if printf '%s' "$z9_env" | grepq -E '^[^#]*ALLOWED_ORIGIN'; then
  fail "(z9) Lambda environment 에 ALLOWED_ORIGIN 이 되돌아왔습니다 — CORS 가 다시 두 집을 갖습니다."\
    $'\n'"→ 실측(2026-08-18): 그 두 집은 이미 어긋나 있었고, Lambda 쪽은 \`Access-Control-Allow-Origin: \"\"\` 를 실었습니다."\
    $'\n'"→ 두 곳에서 실으면 헤더가 중복되어 브라우저가 거부하기도 합니다."\
    $'\n'"→ CORS 의 집은 API Gateway 의 cors_configuration 하나입니다."
fi

# (2) 허용 오리진 목록의 **출처**가 옳은가.
#
# 🔴🔴 **이 칸은 2026-08-26 에 뒤집혔다 (TASK-MONO-579).** 이전 판은 이렇게 단언했다:
#      *"목록이 `aws_cloudfront_distribution.site.domain_name` 을 **참조**해야 한다."*
#      그 핀이 지키려던 주제는 **옳았고 지금도 옳다** — AWS 가 발급하는 주소를 손으로 박으면
#      재생성마다 썩는다(결함 2, TASK-MONO-389).
#
#      틀렸던 것은 핀이 그 주제를 **"CloudFront 를 참조하라"로 좁혀 적었다**는 것이다.
#      `ADR-MONO-067` D3 으로 CloudFront 판이 폐기되자, 그 문장은 주제를 지키는 대신
#      **주제의 해소를 막는 문장**이 됐다. ⇒ 지우지 않고 **뒤집었다.**
#      금지 명제(리터럴 금지)는 그대로 두고, "CloudFront 참조" 만 "비면 안 된다"로 옮겼다.
z9_local="$(awk '/cors_allowed_origins = distinct\(/{print; exit}' "$z9_tf")"
[ -n "$z9_local" ] || fail "(z9) local.cors_allowed_origins 를 찾지 못했습니다 — 앵커가 갈라졌습니다."

# (2a) 목록은 **변수에서** 와야 한다. 목록을 리소스 안에 직접 적으면 배포처를 바꿀 때
#      terraform 코드를 고쳐야 하고, 그러면 tfvars 가 거짓말을 하게 된다.
printf '%s' "$z9_local" | grepq 'var.allowed_origins' \
  || fail "(z9) 허용 오리진 목록이 var.allowed_origins 에서 오지 않습니다."\
    $'\n'"→ 배포처는 환경마다 다릅니다. 목록의 출처는 변수 하나여야 합니다."

# (2b) 🔴 **주제 보존** — AWS 발급 주소를 리터럴로 박으면 안 된다. 이 명제는 CloudFront
#      폐기와 무관하게 살아 있다(`execute-api` 는 지금도 매 재생성마다 바뀐다).
if printf '%s' "$z9_local" | grepq -E '"https://[a-z0-9.-]*(cloudfront|execute-api)'; then
  fail "(z9) 허용 오리진 목록에 AWS 가 발급하는 주소가 **리터럴로** 박혀 있습니다."\
    $'\n'"→ 그 값은 재생성마다 바뀝니다. var.allowed_origins 로 받으세요(결함 2, TASK-MONO-389)."
fi

# (2c) 🔴🔴 **빈 목록을 무엇이 막는가.** CloudFront 참조가 사라진 지금, 목록이 비면 CORS 는
#      아무 오리진도 허용하지 않고 **론처의 Start 버튼이 조용히 죽는다** — 그리고 그 실패는
#      `plan` 에 안 보인다. 그것을 막는 것은 `variables.tf` 의 validation 하나뿐이므로,
#      그것이 지워지면 **구멍이 소리 없이 돌아온다.** 여기서 그 존재를 핀으로 잡는다.
#      🔵 예전에는 CloudFront 참조가 이 구멍을 **우연히** 가려 주고 있었다. 우연을 규칙으로
#         바꾸는 것이 이 칸의 몫이다.
z9_vars="$ROOT/infra/demo/aws/terraform/variables.tf"
z9_ao="$(awk '/^variable "allowed_origins"/{f=1} f{print} f&&/^\}/{exit}' "$z9_vars")"
[ -n "$z9_ao" ] || fail "(z9) variables.tf 에서 allowed_origins 블록을 찾지 못했습니다 — 앵커가 갈라졌습니다."
printf '%s' "$z9_ao" | grepq 'length(var.allowed_origins) > 0' \
  || fail "(z9) allowed_origins 에 **비어 있으면 실패**하는 validation 이 없습니다."\
    $'\n'"→ CloudFront 자동 포함이 폐기된 뒤(TASK-MONO-579) 빈 목록 = 허용 오리진 0개 = Start 버튼 사망입니다."\
    $'\n'"→ 그 실패는 plan 에 안 보이고 런타임에 옵니다. validation 이 그것을 plan 으로 끌어옵니다."

# (3) 대조군 — 가드가 (1)·(2c)를 실제로 볼 수 있는가.
#     🔴 블록 추출이 빈 껍데기면 그 단언들은 **항상 통과**한다. 각 추출에 반드시 있는
#        다른 것이 보이는지 확인해 추출이 살아 있음을 증명한다.
printf '%s' "$z9_env" | grepq 'MONTHLY_BUDGET_MINUTES' \
  || fail "(z9) 대조군 실패 — environment 블록 추출에 MONTHLY_BUDGET_MINUTES 가 안 보입니다."\
    $'\n'"→ 추출이 빈 껍데기이므로 (1)의 통과는 **아무것도 증명하지 않습니다**."
printf '%s' "$z9_ao" | grepq 'type        = list(string)' \
  || fail "(z9) 대조군 실패 — allowed_origins 블록 추출에 type 선언이 안 보입니다."\
    $'\n'"→ 추출이 빈 껍데기이므로 (2c)의 통과는 **아무것도 증명하지 않습니다**."

ok "CORS 단일 집 (terraform) — Lambda env 에 ALLOWED_ORIGIN 없음 · 목록 출처 = var.allowed_origins · AWS 발급 주소 리터럴 없음 · 빈 목록을 막는 validation 존재 (대조군 2개로 추출 유효 확인)"

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
  [ -z "$z10_bad" ] || fail "(z10) vercel.json 에 **주석 흉내 키**가 있습니다:"    $'\n'"$z10_bad"    $'\n'"→ JSON 에는 주석이 없고, Vercel 은 모르는 최상위 키를 거부합니다."    $'\n'"→ 2026-08-19 에 정확히 이것으로 배포가 두 번 연속 죽었습니다(사이트는 마지막"    $'\n'"   성공 배포가 계속 서빙해서 겉으로는 멀쩡했습니다)."    $'\n'"→ 설명은 site/build.sh 주석에 두세요 — 거기가 설명의 집입니다."

  # (2) 이 프로젝트가 의존하는 세 키가 실제로 있는가.
  #     🔴 (1)만 보면 **키를 전부 지워서 통과** 와 구별되지 않는다.
  for z10_k in buildCommand outputDirectory installCommand; do
    grep -q "\"$z10_k\"" "$z10_vj" || fail "(z10) vercel.json 에 \`$z10_k\` 가 없습니다."      $'\n'"→ buildCommand 가 없으면 build.sh 가 안 돌아 public/ 이 안 만들어지고,"      $'\n'"   outputDirectory 가 없으면 엉뚱한 디렉터리가 배포되며,"      $'\n'"   installCommand 가 없으면 루트 pnpm-lock.yaml 을 찾아 monorepo 전체를 설치합니다."
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
echo "[verify] (z12) 데모가 주입하는 issuer/JWKS 가 전부 데모 IAM 을 가리키는가"
# ---------------------------------------------------------------------------
# TASK-MONO-554. 라이브에서 콘솔의 이커머스 운영 화면 6개가 **전부 401** 이었다. 토큰은
# 유효했고(만료 26분 남음) 같은 쿠키로 wms·scm·erp·원장은 200 이었다. 원인은 ecommerce
# compose 가 `OIDC_ALLOWED_ISSUERS: ${OIDC_ALLOWED_ISSUERS:-http://iam.local,iam}` 로
# **항상 값을 채워** application.yml 의 안전한 폴백을 덮은 것이다 — 즉 값을 *설정한 것*
# 이 결함이었다. 콘솔은 그 401 을 "session expired" 로 번역하므로 **배선 결함이 세션
# 만료로 위장한다**: 방문자에게는 로그인 문제로 보이고, 우리 게이트는 전부 초록이다.
#
# 🔴 왜 "게이트웨이 전수" 가 아닌가 — 모집단을 셰이프로 정의하면 새 셰이프에 무반응이다
# ---------------------------------------------------------------------------
# 티켓은 "게이트웨이를 인벤토리로 열거하라" 고 적었지만, 그 모집단 자체가 **실측에서
# 틀렸다**(AC-0 재측정):
#   · wms 는 게이트웨이 말고도 admin/inbound/inventory/master/outbound **5개 리소스
#     서버**에 같은 변수를 준다 — 게이트웨이만 세면 5개가 사각지대다.
#   · fan 은 키 이름이 아예 다르다(`INTERNAL_JWT_ISSUER`) — 이름으로 찾으면 못 본다.
# 그래서 이 가드는 서비스 이름도 키 이름도 박지 않는다. **렌더된 compose 전체에서
# issuer/JWKS 를 나르는 env 를 발견**하고 그 값의 호스트를 본다. 새 도메인·새 서비스·
# 새 키 이름은 자동으로 모집단에 들어온다.
#
# 🔴🔴 프로브 도메인으로 렌더하는 것이 이 가드의 핵심이다 (여기를 되돌리면 가드가 죽는다)
# ---------------------------------------------------------------------------
# demo.env 의 기본값은 `DEMO_DOMAIN=${DEMO_DOMAIN:-local}` 이다. 그 값으로 렌더하면
# 데모 issuer 가 `http://iam.local` 이 되어, ecommerce 의 하드코딩 기본값
# `http://iam.local,iam` 이 **데모 issuer 를 포함해 버린다** ⇒ 결함이 있는 판이 초록이다.
# 즉 CI 의 기본 환경은 이 결함을 **구조적으로 볼 수 없다.** 데모 호스트의 DEMO_DOMAIN 은
# IMDSv2 에서 파생된 값이라 결코 `local` 이 아니므로, 결함은 거기서만 나타난다.
# ⇒ 판정 축(호스트)이 대조 축(기본값)과 같은 문자열이 되지 않도록 **일부러 다른 도메인**
#   으로 렌더한다. 이 저장소가 반복해서 배운 명제다: 단언이 형제 출처로 충족되면 그
#   초록은 아무것도 증명하지 않는다.
# 🔵 같은 이유로 가드 (w) 도 이 결함을 못 봤다 — (w) 는 기본 DEMO_DOMAIN 으로 재고,
#   서비스당 JWK env 를 **첫 건만** 본다(membership-service 는 두 개를 갖는다).
z12_probe="z12-probe.invalid"
z12_rows="$(mktemp)"
z12_bad=""; z12_seen=0

# 프로브 도메인으로 재-source 한 뒤 렌더한다. 서브셸이라 바깥 env 는 건드리지 않는다.
(
  set -a; DEMO_DOMAIN="$z12_probe"; source "$HERE/demo.env"; set +a
  for z12_p in "${!COMPOSE[@]}"; do
    render "$z12_p" | awk -v proj="$z12_p" '
      /^[a-z]+:/             { sec = $1; sub(/:$/, "", sec); svc = ""; blk = "" }
      sec != "services"      { next }
      /^  [A-Za-z0-9._-]+:$/ { svc = $1; sub(/:$/, "", svc); blk = ""; next }
      svc == ""              { next }
      /^    [A-Za-z0-9._-]+:/ { blk = $1; sub(/:$/, "", blk) }
      blk == "environment" && /^      [A-Za-z0-9_]+:/ {
        k = $1; sub(/:$/, "", k)
        v = $0; sub(/^      [A-Za-z0-9_]+:[ ]*/, "", v); gsub(/"/, "", v)
        if (k ~ /ISSUER|JWK/ && v != "") print proj "|" svc "|" k "|" v
      }'
  done
) > "$z12_rows"

# 🔴 추출 0건은 "issuer 를 주는 서비스가 없다" 가 아니라 **술어가 깨진 것**이다.
[ -s "$z12_rows" ] || fail "(z12) 렌더된 compose 에서 issuer/JWKS env 를 한 건도 추출하지 못했습니다"\
  $'\n'"→ 0건을 '없음' 으로 보고하지 않습니다. compose 렌더 또는 추출식이 깨졌습니다."

# 🔴 0건은 **축마다** 본다 — 도메인 하나의 렌더가 조용히 실패해도 합계는 멀쩡하다.
for z12_p in "${!COMPOSE[@]}"; do
  grep -q "^$z12_p|" "$z12_rows" \
    || fail "(z12) '$z12_p' 도메인에서 issuer/JWKS env 를 한 건도 못 봤습니다 — 그 도메인의 렌더가 실패했을 수 있습니다."
done

# 판정. 값은 콤마 구분 목록일 수 있다(allowed-issuers). 항목마다 호스트를 뽑아 본다.
#   · 점이 있는 호스트 = 공개 이름 → 반드시 프로브 도메인에서 파생돼야 한다.
#   · 점이 없는 호스트(`iam-auth-service`, `iam`) = 컨테이너 네트워크 이름 →
#     도달성은 가드 (w) 의 관할이므로 여기서는 판정하지 않는다.
#   · `localhost` 는 점이 없지만 **알려진 나쁜 기본값**이라 명시적으로 잡는다.
# 선언된 공개 IdP 는 **도메인 파생이 아니다** — 그것이 C2 의 요점이다(부팅마다 바뀌는
# 이름 뒤에 IdP 를 두면 브라우저가 못 따라온다). 그래서 이 칸도 그 한 호스트를 면제한다.
# 🔴 (w) 와 **같은 규칙**으로 파생한다: `demo.env` 의 `IAM_PUBLIC_URL` 이 `https` 일 때
#    그 호스트 하나. 기본값(`http://iam.${DEMO_DOMAIN}`)에서는 불활성이라 오늘의 판정을
#    바꾸지 않는다. 🔵 프로브 도메인으로 다시 source 하는 이유는 위 렌더와 **같은 세계**의
#    값을 봐야 하기 때문이다(바깥 스코프의 값은 DEMO_DOMAIN 이 다르다).
z12_public_idp="$(
  set -a; DEMO_DOMAIN="$z12_probe"; . "$HERE/demo.env"; set +a
  case "${IAM_PUBLIC_URL:-}" in
    https://*) printf '%s' "$IAM_PUBLIC_URL" | sed -E 's#^https://##; s#[:/].*$##' ;;
  esac
)"
z12_public_used=0

while IFS='|' read -r z12_proj z12_svc z12_key z12_val; do
  z12_seen=$((z12_seen + 1))
  z12_item_list="$(printf '%s' "$z12_val" | tr ',' '\n')"
  while IFS= read -r z12_item; do
    [ -n "$z12_item" ] || continue
    z12_host="$(printf '%s' "$z12_item" | sed -E 's#^[a-zA-Z][a-zA-Z0-9+.-]*://##; s#[:/].*$##')"
    [ -n "$z12_host" ] || continue
    # 선언된 공개 IdP 는 통과. 🔴 `=` 비교이므로 다른 공개 호스트는 그대로 BAD 다 —
    #    이 절이 「점 있는 호스트는 전부 봐준다」로 넓어지면 가드가 죽는다.
    if [ -n "$z12_public_idp" ] && [ "$z12_host" = "$z12_public_idp" ]; then
      z12_public_used=$((z12_public_used + 1)); continue
    fi
    case "$z12_host" in
      localhost)            : ;;                                   # 아래에서 BAD 처리
      *.$z12_probe)         continue ;;                            # 프로브에서 파생됨 ✓
      *.*)                  : ;;                                   # 점 있는데 프로브 밖 → BAD
      *)                    continue ;;                            # 컨테이너 이름 → (w) 관할
    esac
    z12_bad="$z12_bad"$'\n'"  $z12_proj:$z12_svc  $z12_key = $z12_item   (호스트 '$z12_host')"
  done <<< "$z12_item_list"
done < "$z12_rows"

[ -z "$z12_bad" ] || fail "(z12) 데모 도메인을 따라가지 않는 issuer/JWKS 주입이 있습니다:$z12_bad"\
  $'\n'"→ 이 값들은 데모에서 **틀린 IAM 을 가리킵니다.** 데모의 issuer 는 \`http://iam.\${DEMO_DOMAIN}\`"\
  $'\n'"   이고 DEMO_DOMAIN 은 부팅마다 IMDSv2 에서 파생됩니다 — 저장소에 박힌 호스트는 맞을 수 없습니다."\
  $'\n'"→ 증상은 인증 실패로 보입니다: issuer 불일치는 401, JWKS 호스트 미해소는 Spring 이"\
  $'\n'"   fail-closed 로 바꾼 401 입니다. 콘솔은 그 401 을 \"session expired\" 로 번역하므로"\
  $'\n'"   **방문자에게는 로그인 문제로 보이고 우리 게이트는 전부 초록입니다.**"\
  $'\n'"→ 고치는 곳은 서비스가 아니라 **데모 층**입니다: infra/demo/demo.env 에 그 변수를"\
  $'\n'"   \${IAM_PUBLIC_URL} / \${IAM_JWKS_URL} 로 선언하세요. compose 의 \`\${VAR:-기본값}\` 은"\
  $'\n'"   셸 환경이 있으면 그것을 씁니다(demo-up.sh 가 \`set -a; source demo.env\` 합니다)."\
  $'\n'"→ 🔴 이 가드를 DEMO_DOMAIN=local 로 되돌려 통과시키지 마세요. 그러면 하드코딩된"\
  $'\n'"   \`iam.local\` 이 데모 issuer 와 **구별되지 않아** 가드가 조용히 죽습니다."

ok "issuer/JWKS 주입 ${z12_seen}건 전부 데모 도메인 파생 · 컨테이너 이름 · 또는 선언된 공개 IdP (도메인 ${#COMPOSE[@]}개 전수 · 프로브='$z12_probe' · 공개 IdP 면제 ${z12_public_used}건${z12_public_idp:+ ($z12_public_idp)})"
rm -f "$z12_rows"

# ---------------------------------------------------------------------------
echo "[verify] (z13) 부팅 판정이 스냅샷이 아니라 재측정인가 — 그리고 예산 고갈이 보이는가"
# ---------------------------------------------------------------------------
# TASK-MONO-559. 2026-08-19 라이브: `demo-stack.service` 가 **스택이 완전히 정상인 채로**
# `failed` 였다.
#
#   11:57:40  [demo] ✖ iam 기동 실패        → 12:07:17 exit 1 → 유닛 failed
#   같은 시각 /domains: iam {"state":"up","healthy":15,"total":15}
#   docker inspect iam-kafka: healthy · **restarts=0**
#
# `restarts=0` 이 핵심이다 — kafka 는 죽지도 되살아나지도 않았다. compose 가 healthcheck
# 창이 열려 있는 동안 먼저 포기했을 뿐이다. `failed` 는 **포기한 시각의 스냅샷**이고,
# 그 뒤로 아무도 다시 보지 않았다.
#
# 🔴 그리고 A 를 고치면 B 가 영원히 안 보인다. A 의 고침("끝에서 다시 재고 수렴했으면
#    초록")은 **예산이 없어 재시도를 못 받은 도메인도 똑같이 초록으로** 만든다 — 어차피
#    나중에 수렴하기 때문이다. 그래서 이 가드는 둘을 **같이** 본다.
#
# 네 칸을 모두 본다. 하나라도 빼면 다음 구현과 구별되지 않는다:
#   (1) 전부 정상          → rc=0 · "늦게 수렴" 0건      ← 대조군(빨간 가드는 곧 꺼진다)
#   (2) 늦게 수렴 (bite)   → rc=0 · 그 도메인 **이름이 찍힘**
#   (3) 끝내 안 뜸 (대조군)→ rc≠0                        ← 삼킴 금지
#   (4) 재측정 실패        → rc≠0 · **"판정 불가" 로 구별**  ← 이걸 빼면 (3)이 (2)로 오분류
#   (5) 예산 고갈 + 수렴   → rc=0 이면서 **예산 신호가 남는가**  ← A 의 고침이 B 를 지우는 자리
#
# 🔴🔴 (4)를 빼면 왜 치명적인가: `demo-status.sh` 는 **설계상** 도커가 없어도 에러가
#    아니라 전 도메인 `down` 을 돌려준다. 그 출력만 보면 *"못 쟀다"* 와 *"안 떴다"* 가
#    구별 불가라, 계측 실패가 **도메인 판정으로 번역**된다(rc 는 어차피 비-0 이라 그냥
#    넘어가고 싶어지지만, 그러면 다음 사람이 틀린 사유를 물려받는다).
#
# docker 는 대역으로 바꾼다 — (z4) 와 같은 이유다. 여기서 통제해야 하는 것은 정확히
# 두 개다: compose 의 종료코드, 그리고 **재측정이 보는 컨테이너 상태**.
z13_tmp="$(mktemp -d)"
mkdir -p "$z13_tmp/infra" "$z13_tmp/bin"
cp -r "$ROOT/infra/demo" "$z13_tmp/infra/demo"
if [ -d "$ROOT/infra/traefik" ]; then cp -r "$ROOT/infra/traefik" "$z13_tmp/infra/traefik"; fi
for z13_d in "$ROOT"/projects/*/; do
  z13_n="$(basename "$z13_d")"
  mkdir -p "$z13_tmp/projects/$z13_n"
  for z13_f in "$z13_d"*.yml "$z13_d".env.example; do
    if [ -f "$z13_f" ]; then cp "$z13_f" "$z13_tmp/projects/$z13_n/"; fi
  done
done
if ! bash "$z13_tmp/infra/demo/provision-demo-env.sh" >/dev/null 2>&1; then
  rm -rf "$z13_tmp"
  fail "(z13) 임시 트리 .env 프로비저닝 실패 — 이후 판정이 env-preflight 에 막혀 무효가 됩니다."
fi

# 대역: `compose … -p <FAILDOM> … up -d` 만 비-0.
#       `ps -a --filter label=…project=<slug>` 는 RECHECK 에 따라 답을 바꾼다.
#       `ps -a -q`(생존 프로브)는 DOCKER_DEAD 일 때만 비-0.
cat > "$z13_tmp/bin/docker" <<'Z13SHIM'
#!/bin/sh
if [ "$1" = "compose" ]; then
  shift; p=""; is_up=0
  # 🔴 매달림 대역은 **`up` 호출에만** 건다. 이 스크립트는 같은 슬러그로 `config` 도
  #    부르고(신선도 검사), 거기까지 매달리면 대조군과의 차이가 «매달림» 이 아니라
  #    «대역이 아무 데서나 잔다» 가 된다 — 첫 판본이 그 때문에 34s 차를 냈다.
  case " $* " in *" up "*) is_up=1 ;; esac
  while [ $# -gt 0 ]; do
    case "$1" in -p) p="$2"; shift 2 ;; *) shift ;; esac
  done
  if [ "$is_up" = "1" ] && [ "$p" = "${HANGDOM:-}" ]; then
    # 매달림 대역 — 실제 창에서 본 모양이다: compose 가 의존 대기 줄만 찍고 돌아오지 않는다.
    echo "Container ${p}-kafka Waiting" >&2
    sleep "${HANG_SECS:-30}"
    exit 0
  fi
  if [ "$p" = "${FAILDOM:-}" ]; then
    echo "dependency failed to start: container ${p}-kafka is unhealthy" >&2
    exit 1
  fi
  exit 0
fi
if [ "$1" = "ps" ]; then
  [ "${DOCKER_DEAD:-0}" = "1" ] && { echo "cannot connect to the docker daemon" >&2; exit 1; }
  # 생존 프로브(`ps -a -q`) — 여기까지 왔으면 살아 있다.
  case " $* " in *" -q "*) echo "deadbeef"; exit 0 ;; esac
  slug=""
  for a in "$@"; do
    case "$a" in label=com.docker.compose.project=*) slug="${a##*=}" ;; esac
  done
  if [ "$slug" = "${FAILDOM:-}" ] && [ "${RECHECK:-up}" = "down" ]; then
    exit 0            # 컨테이너 0개 ⇒ demo-status.sh 가 state=down 으로 읽는다
  fi
  echo "running|Up 3 minutes (healthy)"
  exit 0
fi
exit 0
Z13SHIM
chmod +x "$z13_tmp/bin/docker"

z13_run() {  # $1=FAILDOM  $2=RECHECK(up|down)  $3=DOCKER_DEAD(0|1) → rc 를 echo
  local rc=0
  ( cd "$z13_tmp" && PATH="$z13_tmp/bin:$PATH" \
      FAILDOM="$1" RECHECK="$2" DOCKER_DEAD="$3" HANGDOM="${HANGDOM:-}" \
      DEMO_SEED=0 DEMO_DOMAIN=local DEMO_UP_ATTEMPTS=2 DEMO_UP_RETRY_SLEEP=1 \
      bash infra/demo/demo-up.sh iam wms console ) > "$z13_tmp/run.log" 2>&1 || rc=$?
  echo "$rc"
}
z13_die() { rm -rf "$z13_tmp"; fail "$@"; }

# (1) 대조군 — 아무도 실패하지 않으면 초록이고, "늦게 수렴" 은 0건이어야 한다.
z13_rc1="$(z13_run '' up 0)"
z13_log1="$(cat "$z13_tmp/run.log")"
[ "$z13_rc1" = "0" ] || z13_die "(z13) 대조군 실패 — 아무 도메인도 실패하지 않았는데 rc=$z13_rc1 입니다."\
  $'\n'"→ 정상 부팅이 빨간 가드는 곧 꺼지고, 꺼진 가드의 skip 은 초록으로 보고됩니다."
printf '%s\n' "$z13_log1" | grepq '^\[demo\] ◑ 늦게 수렴:' \
  && z13_die "(z13) 대조군에서 '늦게 수렴' 이 보고됐습니다 — 아무도 실패하지 않았는데 재측정이 뭔가를 만들어 냈습니다."

# (2) bite — wms 의 up 은 실패하지만 재측정에서는 healthy. 초록이되 **이름이 찍혀야** 한다.
z13_rc2="$(z13_run wms up 0)"
z13_log2="$(cat "$z13_tmp/run.log")"
[ "$z13_rc2" = "0" ] || z13_die "(z13) 늦게 수렴한 도메인이 여전히 실패로 끝났습니다 (rc=$z13_rc2)."\
  $'\n'"→ 이것이 이 티켓의 결함 A 다: compose 가 포기한 **시각의 스냅샷**을 종료코드로 쓰고 있습니다."\
  $'\n'"→ 2026-08-19 실측에서 iam 은 15/15 healthy · kafka restarts=0 인데 유닛이 failed 였습니다."\
  $'\n'"→ 마지막에 demo-status.sh 로 **다시 재고**, 수렴했으면 종료코드에서 빼세요(삼키는 것이 아니라 재는 것)."
printf '%s\n' "$z13_log2" | grepq 'wms' \
  || z13_die "(z13) 늦게 수렴한 도메인의 **이름이 어디에도 없습니다** — 초록이지만 무슨 일이 있었는지 알 수 없습니다."

# (3) 대조군 — 끝내 안 뜬 도메인은 여전히 비-0 이어야 한다. 재측정은 삼킴이 아니다.
z13_rc3="$(z13_run wms down 0)"
z13_log3="$(cat "$z13_tmp/run.log")"
[ "$z13_rc3" != "0" ] && [ -n "$z13_rc3" ] \
  || z13_die "(z13) 끝내 안 뜬 도메인이 있는데 **성공(rc=0)** 으로 끝났습니다."\
  $'\n'"→ 재측정을 \`|| true\` 처럼 쓴 것입니다. 그러면 유닛이 항상 초록이 되고, 이 저장소가"\
  $'\n'"   반복해서 당한 *\"아무것도 안 보면서 초록\"* 이 됩니다(재측정 ≠ 삼킴)."
printf '%s\n' "$z13_log3" | grepq '^\[demo\] ◑ 늦게 수렴:' \
  && z13_die "(z13) 끝내 안 뜬 도메인이 '늦게 수렴' 으로 보고됐습니다 — (3)이 (2)로 오분류됩니다."

# (4) 재측정 자체가 실패 — rc≠0 이되 사유가 **'판정 불가'** 로 구별돼야 한다.
z13_rc4="$(z13_run wms up 1)"
z13_log4="$(cat "$z13_tmp/run.log")"
[ "$z13_rc4" != "0" ] \
  || z13_die "(z13) 재측정이 실패했는데 성공으로 끝났습니다 — 못 잰 것을 통과로 읽었습니다."
printf '%s\n' "$z13_log4" | grepq '^\[demo\] ✖ 판정 불가 도메인:' \
  || z13_die "(z13) 재측정 실패가 **'판정 불가' 로 구별되지 않습니다.**"\
  $'\n'"→ demo-status.sh 는 설계상 도커가 없어도 에러가 아니라 전 도메인 \`down\` 을 돌려줍니다."\
  $'\n'"→ 그래서 그 출력만 보면 '못 쟀다' 와 '안 떴다' 가 **구별 불가**이고, 계측 실패가"\
  $'\n'"   도메인 판정으로 번역됩니다. 재측정 앞에 도커 생존 프로브를 두세요."
printf '%s\n' "$z13_log4" | grepq '^\[demo\] ◑ 늦게 수렴:' \
  && z13_die "(z13) 재측정이 실패했는데 '늦게 수렴' 으로 보고됐습니다 — 못 잰 것을 수렴으로 읽었습니다."

# (5) 🔴🔴 AC-2 의 핵심 — **A 의 고침이 B 의 유일한 증상을 지우지 않는가.**
# 예산이 없어 재시도를 못 받은 도메인도 어차피 나중에 수렴하므로, (2)의 고침만 있으면
# 그 도메인은 "늦게 수렴" 초록이 되고 **운영자는 배분이 빠듯했다는 사실을 알 방법이 없다.**
# 그래서 예산을 일부러 굶긴 판에서 **두 신호가 함께** 나오는지 본다: 초록이면서도
# 예산 고갈이 이름과 함께 남아야 한다.
z13_starve() {
  local rc=0
  ( cd "$z13_tmp" && PATH="$z13_tmp/bin:$PATH" \
      FAILDOM="$1" RECHECK=up DOCKER_DEAD=0 \
      DEMO_SEED=0 DEMO_DOMAIN=local DEMO_UP_ATTEMPTS=3 DEMO_UP_RETRY_SLEEP=1 \
      DEMO_UP_RETRY_BUDGET=1 \
      bash infra/demo/demo-up.sh iam wms console ) > "$z13_tmp/run.log" 2>&1 || rc=$?
  echo "$rc"
}
z13_rc5="$(z13_starve iam)"
z13_log5="$(cat "$z13_tmp/run.log")"
printf '%s\n' "$z13_log5" | grepq '재시도 예산이 남지 않아' \
  || z13_die "(z13) 예산을 굶겼는데 **예산 때문에 포기했다는 말이 없습니다.**"\
  $'\n'"→ 그러면 '안 떠서 실패' 와 '예산이 없어서 포기' 가 구별되지 않습니다."
printf '%s\n' "$z13_log5" | grepq '^\[demo\] ⚠ 재시도 배분:' \
  || z13_die "(z13) 예산 고갈이 **최종 요약에 남지 않습니다** — 이것이 이 티켓의 결함 B 입니다."\
  $'\n'"→ AC-1 의 재측정이 그 도메인을 '늦게 수렴' 초록으로 만들면, 예산이 빠듯했다는 사실은"\
  $'\n'"   **어디에도 남지 않고 사라집니다**(A 의 고침이 B 의 유일한 증상을 지운다)."\
  $'\n'"→ 예산 고갈은 재측정 결과와 **무관하게** 보고하세요."
printf '%s\n' "$z13_log5" | grepq '^\[demo\] ◑ 늦게 수렴:' \
  || z13_die "(z13) (5)번 칸의 전제가 성립하지 않습니다 — 굶긴 도메인이 '늦게 수렴' 으로 잡히지 않았습니다."\
  $'\n'"→ 이 칸은 **초록인 채로도** 예산 신호가 남는지를 묻습니다. 전제가 깨지면 판정이 무의미합니다."
[ "$z13_rc5" = "0" ] \
  || z13_die "(z13) (5)번 칸이 rc=$z13_rc5 로 끝났습니다 — 수렴했으므로 초록이어야 하고, 그 초록 위에서 예산 신호가 남는지가 이 칸의 질문입니다."

# (7) 🔴🔴 TASK-MONO-615 B4 — `up -d` 가 **매달려도** 스크립트가 돌아오는가
# ---------------------------------------------------------------------------
# 2026-09-02 기동 창(V5, 두 번째 부팅): 14:24:01 의존 대기 줄 뒤로 **17분 침묵**, 그 사이
# 다음 도메인의 `[demo] up:` 줄이 하나도 없었고 systemd 가 1200s 에 SIGTERM 을 보냈다.
# 즉 호출 하나가 매달렸고, 그러면 재시도·수렴 재측정·요약·상태 발행이 **전부 실행되지
# 않는다.** 이 칸은 그 모양을 대역으로 재현해 세 가지를 묻는다:
#   ① 스크립트가 돌아오는가(묶였는가) ② 매달림이 '기동 실패' 와 **다른 말**로 남는가
#   ③ 뒤의 도메인이 계속 시도되는가
# 🔵 이 칸은 경합 자체를 재지 않는다 — 경합은 기동 창의 몫이다. 여기서 지키는 것은
#    「매달렸을 때 진단이 남는다」는 성질이고, 그것이 다음 창에서 B4 를 잴 수 있게 한다.
# $1=매달릴 도메인 ('' = 없음)  $2=빨리 실패할 도메인 ('' = 없음)
# 🔴 표면 재시도(기본 12회 × 10s)를 1회·0s 로 고정한다. 그 잡음이 아래 대조군 비교를
#    통째로 삼킨다 — 첫 판본이 그것 때문에 42s 차를 «안 묶였다» 로 읽었다.
z13_hang() {
  local rc=0
  ( cd "$z13_tmp" && PATH="$z13_tmp/bin:$PATH" \
      FAILDOM="$2" RECHECK=up DOCKER_DEAD=0 HANGDOM="$1" HANG_SECS=30 \
      DEMO_SEED=0 DEMO_DOMAIN=local DEMO_UP_ATTEMPTS=2 DEMO_UP_RETRY_SLEEP=1 \
      DEMO_UP_CALL_TIMEOUT=2 DEMO_UP_TOTAL_BUDGET=60 \
      DEMO_SURFACE_ATTEMPTS=1 DEMO_SURFACE_SLEEP=0 \
      bash infra/demo/demo-up.sh iam wms console ) > "$z13_tmp/run.log" 2>&1 || rc=$?
  echo "$rc"
}
# 🔴🔴 **대조군을 먼저 돌린다.** 「끊었는가」를 총 실행시간의 절대값으로 판정하면 안 된다 —
#    이 스크립트는 up 루프 말고도 신선도·preflight·재측정·표면검사를 하고, 그 시간이 대역
#    sleep 보다 길다. (이 칸의 첫 판본이 정확히 그렇게 틀렸다: 묶기는 제대로 묶었는데
#    총 40s 를 «안 묶였다» 로 읽었다.) 재는 것은 **매달림이 더한 시간**이다.
# 🔴🔴 대조군은 «실패가 없는 판» 이 아니라 **«같은 도메인이 빨리 실패하는 판»** 이다.
#    실패가 없으면 수렴 재측정·표면 검사 경로 자체를 안 타므로 두 판의 차이가
#    «매달림» 이 아니라 «실패 경로의 유무» 가 된다 — 그 대조군은 다른 것을 잰다.
z13_t0="$(date +%s)"
z13_rc7base="$(z13_hang '' iam)"
z13_base_elapsed=$(( $(date +%s) - z13_t0 ))
z13_t1="$(date +%s)"
z13_rc7="$(z13_hang iam '')"
z13_hang_elapsed=$(( $(date +%s) - z13_t1 ))
z13_delta=$(( z13_hang_elapsed - z13_base_elapsed ))
z13_log7="$(cat "$z13_tmp/run.log")"

# ① 묶였는가 — **직접 증거는 아래 ②의 문구**다(그 줄은 종료코드 124, 즉 timeout 이 실제로
#    끊었을 때만 나온다). 여기서는 그 위에 대조군 한 겹을 더 얹는다: 안 묶였다면 시도 2회 ×
#    대역 sleep 30s = 최소 60s 가 대조군 위에 얹힌다. 묶였다면 몇 초다.
[ "$z13_delta" -lt 30 ] \
  || z13_die "(z13) 매달린 up -d 를 **끊지 못했습니다** — 대조군 ${z13_base_elapsed}s → 매달림 판 ${z13_hang_elapsed}s (차 ${z13_delta}s, 대역 sleep 30s × 시도 2회)."\
  $'\n'"→ 그러면 데모 호스트에서는 systemd TimeoutStartSec 이 유일한 상한이고, 요약도 상태 발행도 못 합니다."\
  $'\n'"→ demo-up.sh 의 up_call() 이 timeout 으로 묶는지, HAVE_TIMEOUT 이 1 인지 보세요."
# 🔵 대조군 자신이 성립하는지도 본다 — 대조군이 실패로 끝나면 위 비교의 기준이 무의미하다.
[ "$z13_rc7base" = "0" ] \
  || z13_die "(z13) (7)번 칸의 **대조군**(빨리 실패)이 rc=$z13_rc7base 로 끝났습니다 — 두 판이 같은 하류 경로를 타는지부터 다시 보세요."

# ② 매달림이 '기동 실패' 와 구별되는가.
printf '%s\n' "$z13_log7" | grepq '매달림' \
  || z13_die "(z13) 매달림이 **'기동 실패' 와 구별되지 않습니다.**"\
  $'\n'"→ 두 사유는 진단이 다릅니다: '떠서 실패' 는 의존이 unhealthy 로 끝난 것이고,"\
  $'\n'"  '매달림' 은 그 healthcheck 가 대기를 **묶지 못했다**는 뜻입니다(615 B4 가 찾는 신호)."

# ③ 뒤의 도메인이 계속 시도되는가 — 한 도메인의 매달림이 나머지를 삼키면 안 된다.
printf '%s\n' "$z13_log7" | grepq '^\[demo\] up: wms' \
  || z13_die "(z13) 앞 도메인이 매달리자 **뒤 도메인이 시도조차 되지 않았습니다.**"\
  $'\n'"→ 한 도메인의 매달림이 나머지를 삼키면, 고쳐 둔 '부분 실패 내성' 이 무효가 됩니다."

# ④ 🔴🔴 **초록인 채로도 신호가 남는가** — 칸 (5)와 같은 축이다.
#    이 스크립트의 설계는 「compose 는 포기했지만 스택이 수렴했으면 초록」이다. 그래서
#    매달린 도메인도 재시도 뒤 수렴하면 rc=0 이 **옳다**. 위험한 것은 그때 매달림이
#    함께 사라지는 것이다 — 그러면 B4 가 찾는 유일한 증상이 «늦게 수렴» 초록에 먹힌다.
#    (A 의 고침이 B 의 유일한 증상을 지운다 — 이 저장소가 559 에서 이미 당한 모양.)
[ "$z13_rc7" = "0" ] \
  || z13_die "(z13) (7)번 칸의 전제가 깨졌습니다 — 대역은 수렴을 답하는데 rc=$z13_rc7 입니다."\
  $'\n'"→ 이 칸은 **초록 위에서** 매달림 신호가 살아남는지를 묻습니다. 전제가 깨지면 판정이 무의미합니다."
printf '%s\n' "$z13_log7" | grepq '^\[demo\] ◑ 늦게 수렴:' \
  || z13_die "(z13) (7)번 칸에서 매달린 도메인이 '늦게 수렴' 으로 잡히지 않았습니다 — 전제가 성립하지 않습니다."
printf '%s\n' "$z13_log7" | grepq '^\[demo\] ⏱ 매달림:' \
  || z13_die "(z13) 수렴 초록이 **매달림 신호를 지웠습니다.**"\
  $'\n'"→ 매달림은 재측정 결과와 무관하게 최종 요약에 남아야 합니다. 안 남으면 다음 기동 창에서"\
  $'\n'"  B4 를 잴 방법이 사라집니다 — 유닛은 초록이고 아무도 매달렸다는 것을 모릅니다."

ok "(z13) 매달린 up -d 를 끊는다 — 대조군(빨리 실패) ${z13_base_elapsed}s → 매달림 ${z13_hang_elapsed}s (차 ${z13_delta}s · 대역 sleep 30s×2) · 뒤 도메인 계속 시도 · '늦게 수렴' 초록 위에서도 매달림 신호 유지"

# (6) AC-2 — 재시도 총 상한이 TimeoutStartSec 아래인가. 🔴 산술을 주석에 두지 않고 여기서 다시 센다.
#     `FULL` 이 커지면 여기서 빨개진다 — 데모 호스트에서 systemd 가 SIGTERM 을 보내기 전에.
z13_unit="$ROOT/infra/demo/demo-stack.service"
z13_timeout="$(sed -n 's/^TimeoutStartSec=\([0-9][0-9]*\).*/\1/p' "$z13_unit" | head -1)"
[ -n "$z13_timeout" ] || z13_die "(z13) demo-stack.service 에서 TimeoutStartSec 을 못 읽었습니다 — 상한 계산을 할 수 없습니다."
z13_sleep="$(sed -n 's/^UP_RETRY_SLEEP="\${DEMO_UP_RETRY_SLEEP:-\([0-9][0-9]*\)}".*/\1/p' "$ROOT/infra/demo/demo-up.sh" | head -1)"
[ -n "$z13_sleep" ] || z13_die "(z13) demo-up.sh 에서 UP_RETRY_SLEEP 기본값을 못 읽었습니다."
# 기동 자체에 드는 시간 — 2026-08-19 실측(총 840s 중 sleep 300s 를 뺀 540s).
# 🔵 이것은 **관측 1건**이지 상수가 아니다. 그래서 여유를 넉넉히 두고, 넘으면 FAIL 한다.
z13_base=540
# 🔴 615 B4 — 전역 기동 예산(demo-up.sh 의 UP_TOTAL_BUDGET)도 같은 상한 아래여야 한다.
#    이 값이 TimeoutStartSec 을 넘으면 «묶었다» 는 말이 거짓이 된다: 스크립트가 자기
#    마감에 닿기 전에 systemd 가 먼저 SIGTERM 을 보낸다.
z13_total="$(sed -n 's/^UP_TOTAL_BUDGET="\${DEMO_UP_TOTAL_BUDGET:-\([0-9][0-9]*\)}".*/\1/p' "$ROOT/infra/demo/demo-up.sh" | head -1)"
[ -n "$z13_total" ] || z13_die "(z13) demo-up.sh 에서 UP_TOTAL_BUDGET 기본값을 못 읽었습니다 — 마감이 상한 아래인지 셀 수 없습니다."
[ "$z13_total" -lt "$z13_timeout" ] \
  || z13_die "(z13) 전역 기동 예산이 TimeoutStartSec 이상입니다: ${z13_total}s >= ${z13_timeout}s"\
  $'\n'"→ 그러면 demo-up.sh 가 자기 마감에 닿기 전에 systemd 가 SIGTERM 을 보냅니다 —"\
  $'\n'"  묶어 둔 의미가 사라지고 요약도 상태 발행도 다시 잃습니다."
# (8) 🔴🔴 TASK-MONO-615 B4-ii — 위 (6)의 부등식은 **참이었는데 요약이 안 나왔다.**
# ---------------------------------------------------------------------------
# 2026-09-03 기동 창, 손대지 않은 판의 부팅 2회:
#   boot #1  up 루프 종료 02:27:57 → 시드 13분 → 02:43:04 systemd SIGTERM
#   boot #2  up 루프 종료 02:56:29 → 시드 14분 → 03:10:32 systemd SIGTERM
#   두 판 모두 '늦게 수렴' 0건 · 'HTTP 표면' 0건 · '재시도 배분' 0건 · '매달림' 0건
# 그때 `UP_TOTAL_BUDGET(1020) < TimeoutStartSec(1200)` 은 **참이었다.**
#
# 🔴 즉 (6)은 **잰 값이 아니라 센 항**이 틀렸다. up 루프 뒤에도 시드·드리프트·재측정·
#    표면 검사가 있고 그것들에는 상한이 없었다. 「전역 예산이 상한 아래」는 필요조건일
#    뿐 충분조건이 아니고, 그 차이가 정확히 «요약이 나오는가» 였다. 한 단계만 묶으면
#    무한한 다음 단계가 그 보증을 통째로 먹는다.
#
# ⇒ 이 칸은 **합**을 센다. 항이 하나라도 빠지면 같은 자리로 돌아온다.
z13_down="$(sed -n 's/.*DEMO_DOWN_BUDGET:-\([0-9][0-9]*\)}.*/\1/p' "$ROOT/infra/demo/demo-boot.sh" | head -1)"
[ -n "$z13_down" ] || z13_die "(z13) demo-boot.sh 에서 DEMO_DOWN_BUDGET 기본값을 못 읽었습니다 — 합을 셀 수 없습니다."\
  $'\n'"→ 후보 ⓐ 의 잔존 정리 단계는 실측 160~184s 를 먹습니다. 항에서 빠지면 합이 거짓이 됩니다."
z13_post="$(sed -n 's/^POST_UP_BUDGET="\${DEMO_POST_UP_BUDGET:-\([0-9][0-9]*\)}".*/\1/p' "$ROOT/infra/demo/demo-up.sh" | head -1)"
[ -n "$z13_post" ] || z13_die "(z13) demo-up.sh 에서 POST_UP_BUDGET 기본값을 못 읽었습니다 — 시드 단계가 다시 무한해집니다."
z13_reserve="$(sed -n 's/^SUMMARY_RESERVE="\${DEMO_SUMMARY_RESERVE:-\([0-9][0-9]*\)}".*/\1/p' "$ROOT/infra/demo/demo-up.sh" | head -1)"
[ -n "$z13_reserve" ] || z13_die "(z13) demo-up.sh 에서 SUMMARY_RESERVE 기본값을 못 읽었습니다 — 요약 몫이 선언돼 있지 않습니다."
z13_sum=$(( z13_down + z13_total + z13_post + z13_reserve ))
[ "$z13_sum" -le "$z13_timeout" ] \
  || z13_die "(z13) 단계 예산의 **합**이 TimeoutStartSec 을 넘습니다: 정리 ${z13_down} + up ${z13_total} + 시드 ${z13_post} + 요약예비 ${z13_reserve} = ${z13_sum}s > ${z13_timeout}s"\
  $'\n'"→ 그러면 systemd 가 먼저 SIGTERM 을 보내고 **최종 요약이 한 줄도 안 나옵니다.**"\
  $'\n'"  2026-09-03 부팅 2회가 정확히 그 상태였고, 그때도 '전역 예산 < 상한' 은 참이었습니다."\
  $'\n'"→ 항을 지우지 말고 값을 조정하거나 TimeoutStartSec 을 재산정하세요."
# 🔵 요약 몫이 0 이어도 위 부등식은 통과한다 — 그러면 합은 맞는데 요약은 또 못 나온다.
#    그래서 예비 자체에 하한을 둔다(실측: 시드 끝~요약 끝 105~108s).
[ "$z13_reserve" -ge 60 ] \
  || z13_die "(z13) 요약 예비가 ${z13_reserve}s 입니다 — 재측정·표면검사·상태발행·요약이 그 안에 안 끝납니다(실측 105~108s)."

z13_worst=$(( ${#FULL[@]} * z13_sleep + z13_base ))
[ "$z13_worst" -le "$z13_timeout" ] \
  || z13_die "(z13) 재시도 최대 총합이 TimeoutStartSec 을 넘습니다: ${#FULL[@]}도메인 × ${z13_sleep}s + 기동 ${z13_base}s = ${z13_worst}s > ${z13_timeout}s"\
  $'\n'"→ 이 상태로 데모가 느리게 뜨면 systemd 가 SIGTERM 을 보내고, '부분 실패를 견디는 고침' 이"\
  $'\n'"   오히려 **전체를 죽입니다**(demo-up.sh 의 예산 문단이 경고한 그 모양)."\
  $'\n'"→ UP_RETRY_SLEEP 을 줄이거나 TimeoutStartSec 을 올리세요. 하한을 상수로 되돌리지는 마세요."

rm -rf "$z13_tmp"
ok "부팅 판정이 재측정이다 — 정상 rc=0 · 늦게수렴 rc=0(이름 찍힘) · 미기동 rc=$z13_rc3 · 재측정실패 rc=$z13_rc4(판정 불가) · 재시도 상한 ${z13_worst}s ≤ ${z13_timeout}s · 단계 예산 합 정리${z13_down}+up${z13_total}+시드${z13_post}+예비${z13_reserve}=${z13_sum}s ≤ ${z13_timeout}s"

# ---------------------------------------------------------------------------
echo "[verify] (z14) 방문자 화면 링크가 전부 있고, 안 뜬 화면은 열리지 않는가"
# ---------------------------------------------------------------------------
# TASK-MONO-560. 론처에 링크가 **하나뿐**이었고 목적지는 콘솔이었다. 나머지 두 화면
# (이커머스 스토어·팬 플랫폼)은 **존재를 알 방법이 없었다** — 소유자 본인이 자기 데모에서
# 그 둘을 못 찾은 것이 발견 경로다.
#
# 🔴 그러나 "링크가 3개 있는가" 만 물으면 안 된다. 부팅에 약 10분이 걸리고 그 창에서 각
#    화면은 404 이며, 안 뜬 화면에 링크를 주면 방문자는 **"고장났다"** 로 읽는다.
#    그래서 이 가드는 **판정을 실행해서** 네 칸을 본다(문자열 grep 이 아니다 —
#    grep 술어는 자기 문서에 걸린다. 이 저장소가 (z12)에서 그 함정을 밟았다).
#
#   (1) 도메인 up        → 링크 활성 + href 가 demoHost() 파생
#   (2) down/unknown     → **비활성**, href 자체가 없다 (bite)
#   (3) partial          → 명시된 결정대로 **비활성**
#   (4) health_stale     → state 가 up 이어도 **비활성** (551 이 만든 필드)
#
# 🔵 (4)를 빼지 마라. stale 일 때 up 을 믿으면 **꺼진 스택의 링크를 초록으로** 준다.
#
# 🔴🔴 TASK-MONO-583 — **축이 하나 늘었다.** `ADR-MONO-067` 이 일부 화면을 Vercel 로
#    옮겼고, 그 화면은 **데모가 꺼져 있어도 떠야 한다**. 위 네 칸을 그 행에 그대로 걸면
#    가드가 **이관 자체를 결함으로 판정**한다. 그렇다고 "스토어 행은 예외" 로 느슨하게
#    만들면 **데모 호스트 행의 정적 주소도 함께 통과**한다(칸을 잃는 뒤집기).
#
#    그래서 새 축은 **「각 화면이 어디서 서빙되는가 — 그리고 그 출처에 맞는 판정을
#    받는가」** 다. 행이 `data-served` 로 자기 출처를 선언하고, 이 가드는 **두 정책 모두**
#    를 실행 대조한다. 칸은 줄지 않는다:
#
#      demo-host → 위 네 칸 그대로 + href 가 demoHost() 파생
#      vercel    → 다섯 상태(up/down/unknown/partial/stale) **전부 활성** + 정적 주소
#
#    🔴 선언이 없거나 모르는 값이면 **판정 불가로 실패**한다(모르는 것은 통과가 아니다).
#    🔴 **양쪽 모집단이 각각 ≥1** 임을 단언한다 — 0 이면 그 정책은 안 재고도 초록이다.
z14_site="$ROOT/infra/demo/aws/site/index.html"
[ -f "$z14_site" ] || fail "(z14) index.html 이 없습니다: $z14_site"

# 마크업에서 화면 목록을 **인벤토리로** 뽑는다 — 손으로 나열하면 그 순간 드리프트가 시작된다.
# 🔴🔴 TASK-MONO-583 — 속성을 **순서에 의존하지 않고 하나씩** 뽑는다. 초판은
#    `data-domain=".."[[:space:]]*data-host=".."` 로 **인접**을 요구했고, 그래서 두 속성
#    사이에 새 속성을 하나 넣는 것만으로 그 행이 통째로 파싱에서 빠진다. 그 상태에서
#    가드는 "링크 2개" 를 정상으로 보고할 뻔했다(아래 loose 대조가 그것을 잡는다 —
#    잡히는 것과 안 깨지는 것은 다르다. 여기서는 안 깨지게 만든다).
z14_rows="$(awk '
  /<a [^>]*data-surface/ {
    d = ""; s = ""; h = ""; u = "";
    if (match($0, /data-domain="[^"]*"/)) d = substr($0, RSTART+13, RLENGTH-14);
    if (match($0, /data-served="[^"]*"/)) s = substr($0, RSTART+13, RLENGTH-14);
    if (match($0, /data-host="[^"]*"/))   h = substr($0, RSTART+11, RLENGTH-12);
    if (match($0, /data-url="[^"]*"/))    u = substr($0, RSTART+10, RLENGTH-11);
    if (d != "") print d "|" s "|" h "|" u;
  }
' "$z14_site")"
[ -n "$z14_rows" ] || fail "(z14) index.html 에서 data-surface 링크를 한 개도 뽑지 못했습니다"\
  $'\n'"→ 0건은 '링크가 없다' 가 아니라 **추출식이 깨진 것**입니다(마크업이 바뀌었다면 이 술어도 함께 고치세요)."

# 🔴 추출이 **조용히 한 줄을 흘리지 않았는지** 본다. 속성 하나만 오타가 나도 위 정규식은
#    그 행을 통째로 빠뜨리고, 그러면 이 가드는 "링크 2개" 를 정상으로 보고한다.
# 🔵 `<a ...>` 만 센다 — JS 의 querySelectorAll("[data-surface]") 도 같은 낱말을 담는다
#    (그것까지 세면 이 대조가 늘 1 어긋나고, 그 어긋남은 결함처럼 보인다).
z14_loose="$(grep -c '<a [^>]*data-surface' "$z14_site" || true)"
z14_got="$(printf '%s\n' "$z14_rows" | grep -c . || true)"
[ "$z14_loose" = "$z14_got" ] || fail "(z14) data-surface 는 ${z14_loose}개인데 속성까지 파싱된 것은 ${z14_got}개입니다"\
  $'\n'"→ 한 행의 data-domain/data-host 가 예상과 다른 모양입니다. 그 행은 판정에서 **통째로 빠집니다**."

# 🔴 화면 수 하한. 2026-08-20 에 Traefik Host 규칙을 전수로 세어 확정한 값이다:
#    브라우저 화면 3(console · web.ecommerce · web.fan-platform) · API 게이트웨이 7 · 관측 도구 4.
#    admin.ecommerce 는 화면이 아니다 — IAM 시드에 콜백이 남아 있으나 그 앱은 제거됐고
#    (TASK-MONO-259) 운영자 UI 는 platform-console 로 갔다(시드 회수 마이그레이션만 미뤄져 있다).
#    화면이 늘면 이 숫자를 **올려야** 한다 — 그 순간이 링크를 추가할 자리다.
# 🔵 TASK-MONO-583 — 이 하한은 여전히 3 이고, 그 3 의 뜻은 **론처가 방문자에게 약속하는
#    화면의 총 수**다. 서빙 출처가 갈렸다고 줄지 않는다(web.ecommerce 는 데모 호스트에서
#    Vercel 로 옮겨졌을 뿐 화면으로는 그대로 있다). 🔴 `demo-up.sh` 의 하한은 이것과
#    **다른 축**이다 — 그쪽은 «부팅 때 찌를 수 있는 표면» 이라 데모 호스트 행만 센다.
z14_floor=3
[ "$z14_got" -ge "$z14_floor" ] || fail "(z14) 방문자 화면 링크가 ${z14_got}개뿐입니다(하한 ${z14_floor})"\
  $'\n'"→ 링크가 빠진 화면은 **존재를 알 방법이 없습니다**. 이 티켓이 고친 결함이 정확히 그것입니다."

# 각 링크의 도메인이 실재하는 도메인인가 — projects.sh 가 단일 출처다(하드코딩 금지).
# 그리고 **서빙 출처 선언**(TASK-MONO-583 AC-1)을 검사한다.
#
# 🔴 축이 바뀌었다: 예전에는 "모든 링크가 demoHost() 를 통과하는가" 였고, 이제는
#    **"각 화면이 어디서 서빙되는가 — 그리고 그 출처에 맞는 판정을 받는가"** 다.
#    "스토어 행은 예외" 로 느슨하게 만들면 **데모 호스트 행의 정적 주소도 통과**한다.
#    그래서 칸을 빼지 않고, 행마다 정책을 선언시킨 뒤 **두 정책 모두**를 집행한다.
# 🔴 **기본값을 두지 않는다.** 선언이 없거나 모르는 값이면 판정 불가로 **실패**시킨다.
#    빠뜨린 행이 조용히 한쪽 정책을 받는 것이 이 축이 막아야 할 실패다.
z14_n_demo=0; z14_n_vercel=0
while IFS='|' read -r z14_d z14_s z14_h z14_u; do
  [ -n "$z14_d" ] || continue
  [ -n "${COMPOSE[$z14_d]+x}" ] \
    || fail "(z14) 링크가 존재하지 않는 도메인을 가리킵니다: '$z14_d' (유효: ${!COMPOSE[*]})"\
      $'\n'"→ 그 링크는 영원히 비활성이 됩니다 — 헬스 스냅샷에 그 키가 없기 때문입니다."
  case "$z14_s" in
    demo-host)
      z14_n_demo=$(( z14_n_demo + 1 ))
      [ -n "$z14_h" ] || fail "(z14) '$z14_d' 행이 data-served=\"demo-host\" 인데 data-host 가 없습니다."\
        $'\n'"→ 호스트 접두사 없이는 demoHost() 파생 주소를 만들 수 없습니다 — 그 링크는 영원히 죽습니다."
      [ -z "$z14_u" ] || fail "(z14) '$z14_d' 행이 demo-host 인데 data-url 을 함께 갖고 있습니다: '$z14_u'"\
        $'\n'"→ 두 속성은 **상호 배타**입니다. 데모 호스트 행에 정적 주소를 쓰는 것이 이 가드가"\
        $'\n'"   막는 회귀입니다(재기동마다 IP 가 바뀌므로 그 링크만 죽고 원인이 안 보입니다)."
      ;;
    vercel)
      z14_n_vercel=$(( z14_n_vercel + 1 ))
      [ -n "$z14_u" ] || fail "(z14) '$z14_d' 행이 data-served=\"vercel\" 인데 data-url 이 없습니다."\
        $'\n'"→ 그 행은 항상 활성인데 갈 곳이 없습니다. 자리표시자도 안 됩니다 — 가드는 초록이 되고"\
        $'\n'"   방문자는 404 를 봅니다(TASK-MONO-583 Failure Scenarios)."
      [ -z "$z14_h" ] || fail "(z14) '$z14_d' 행이 vercel 인데 data-host 가 남아 있습니다: '$z14_h'"\
        $'\n'"→ 아무도 안 읽는 낡은 값입니다. 다음 사람은 그것을 보고 그 화면이 여전히 데모 호스트에"\
        $'\n'"   있다고 읽습니다. 그리고 demo-up.sh 가 그 행을 다시 찌를 위험이 생깁니다."
      case "$z14_u" in
        https://*) : ;;
        *) fail "(z14) '$z14_d' 행의 data-url 이 https 절대주소가 아닙니다: '$z14_u'" ;;
      esac
      ;;
    "")
      fail "(z14) '$z14_d' 행에 **서빙 출처 선언(data-served)이 없습니다.**"\
        $'\n'"→ 기본값은 없습니다. 선언이 빠진 행이 조용히 한쪽 정책을 받는 것이 TASK-MONO-583 이"\
        $'\n'"   막는 실패입니다. data-served=\"demo-host\" 또는 \"vercel\" 을 적으세요."
      ;;
    *)
      fail "(z14) '$z14_d' 행의 서빙 출처가 **모르는 값**입니다: '$z14_s'"\
        $'\n'"→ 모르는 것은 통과가 아닙니다. 새 출처를 도입했다면 이 가드의 정책 표부터 넓히세요."
      ;;
  esac
done <<< "$z14_rows"

# 🔴🔴 **양쪽 모집단이 각각 ≥1** 이어야 한다. 한쪽이 0 이면 그 정책은 **안 재고도 초록**이다
#    — 아래 실행 대조는 존재하지 않는 행에 대해 아무 단언도 하지 않기 때문이다.
[ "$z14_n_demo" -ge 1 ] || fail "(z14) demo-host 정책을 받는 행이 **0개**입니다 — 그 정책은 안 재고 초록이 됩니다."
[ "$z14_n_vercel" -ge 1 ] || fail "(z14) vercel 정책을 받는 행이 **0개**입니다 — 그 정책은 안 재고 초록이 됩니다."\
  $'\n'"→ ADR-MONO-067 이 화면을 Vercel 로 옮기고 있습니다. 0 이면 이관이 사라진 것이거나"\
  $'\n'"   이 가드가 그 축을 잃은 것입니다 — 둘 다 조용히 지나가면 안 됩니다."

# 🔴 하드코딩된 sslip 호스트가 있으면 AC-3 회귀다. 단 GUARD-T-ANCHOR 줄은 정본이므로 제외.
# 🔴🔴 주석을 **먼저 걷어낸다.** 이 페이지는 sslip 표기 함정(MONO-389)을 주석으로 길게
#    설명하고 있어서 순진한 grep 은 **자기 문서에 걸린다** — 판정 축과 검색 축이 같은
#    매체가 되는 그 함정이다(이 저장소가 반복해서 밟는다). `//` 줄주석과 `<!-- -->`
#    블록을 지운 뒤 본문만 본다.
z14_code="$(awk '
  /<!--/ { inc = 1 }
  inc    { if (/-->/) inc = 0; next }
  /^[[:space:]]*\/\// { next }
  { print FNR "\t" $0 }
' "$z14_site")"
# 대조군 — 걷어내기가 본문까지 먹으면 이 술어는 **빈 입력**을 보게 되고 늘 통과한다.
printf '%s\n' "$z14_code" | grepq 'GUARD-T-ANCHOR' \
  || fail "(z14) 주석 제거가 본문까지 지웠습니다 — GUARD-T-ANCHOR 줄이 남아 있지 않습니다."\
    $'\n'"→ 빈 입력에 대고 grep 하면 언제나 통과합니다. 그 통과는 아무것도 증명하지 않습니다."
# 🔵 TASK-MONO-583 — 이 금지는 **파일 본문 전체**에 그대로 둔다. Vercel 행의 주소는
#    커스텀 도메인이라 sslip 을 담지 않으므로 이 술어는 여전히 정확하고, 파일 전체가
#    행 단위보다 **엄격**하다. 행별 축(= href 가 demoHost() 파생인가)은 아래 실행
#    대조에서 **데모 호스트 행에만** 건다 — AC-2 가 요구하는 분리는 그쪽이다.
z14_hard="$(printf '%s\n' "$z14_code" | grep 'sslip\.io' | grep -v 'GUARD-T-ANCHOR' || true)"
[ -z "$z14_hard" ] || fail "(z14) index.html 본문에 sslip 호스트가 직접 적혀 있습니다:"$'\n'"$z14_hard"\
  $'\n'"→ 재기동마다 IP 가 바뀝니다. 한 곳이라도 직접 조립하면 **그 링크만** 죽고 나머지가"\
  $'\n'"   멀쩡해서 원인이 안 보입니다. 전부 demoHost() 를 통과시키세요."

# 판정 로직을 **실행한다.** 앵커가 없으면 통과가 아니라 실패다(못 읽었으면 모르는 것이다).
z14_b="$(grep -n 'GUARD-Z14-BEGIN' "$z14_site" | head -1 | cut -d: -f1)"
z14_e="$(grep -n 'GUARD-Z14-END' "$z14_site" | head -1 | cut -d: -f1)"
{ [ -n "$z14_b" ] && [ -n "$z14_e" ] && [ "$z14_e" -gt "$z14_b" ]; } \
  || fail "(z14) index.html 에서 GUARD-Z14 앵커 구간을 찾지 못했습니다 — **가드가 공허합니다.**"
z14_anchor="$(sed -n 's/^[[:space:]]*\(const demoHost =.*\); \/\/ GUARD-T-ANCHOR.*/\1/p' "$z14_site")"
[ -n "$z14_anchor" ] || fail "(z14) GUARD-T-ANCHOR 를 찾지 못했습니다 — demoHost 를 실행할 수 없습니다."

# 🔴🔴 TASK-MONO-603 — 가드 구간이 **`render()` 를 포함해야** 한다.
# 583 은 행 정책을 고쳤고 bite 8/8 이 전부 참이었는데, 방문자 경로는 **안 바뀌었다**:
# 블록의 가시성을 정하는 줄이 `render()` 안, 즉 **이 구간 바깥**에 있어서 가드가 그 코드를
# 한 번도 실행해 본 적이 없었기 때문이다(*내용물에 건 가드는 컨테이너에 건 가드가 아니다*).
# ⇒ 구간을 넓혔고, **넓어진 채로 있는지 여기서 단언한다.** 앵커가 다시 좁아지면 이 축은
#   조용히 사라진다 — 그때 이 줄이 빨개진다.
printf '%s\n' "$z14_code" | grepq 'function render(' \
  || fail "(z14) GUARD-Z14 구간 밖으로 render() 가 나갔거나 이름이 바뀌었습니다 — 컨테이너 축을 실행할 수 없습니다."
sed -n "$(( z14_b + 1 )),$(( z14_e - 1 ))p" "$z14_site" | grepq 'function render(' \
  || fail "(z14) **가드 구간이 render() 를 포함하지 않습니다.**"\
    $'\n'"→ 그러면 \`#surfaces\` 의 가시성을 정하는 줄이 **한 번도 실행되지 않습니다.**"\
    $'\n'"→ TASK-MONO-583 이 정확히 그렇게 통과했습니다: 행 정책 bite 8/8 이 전부 참인데"\
    $'\n'"   링크는 활성인 채로 영영 안 보였습니다(TASK-MONO-603)."

z14_js="$(mktemp)"
{
  printf '%s;\n' "$z14_anchor"
  sed -n "$(( z14_b + 1 )),$(( z14_e - 1 ))p" "$z14_site"
  cat <<'Z14DRV'
// --- 최소 DOM 대역 ---------------------------------------------------------
// 🔴🔴 TASK-MONO-603 — 구간이 render() 까지 넓어졌으므로 대역도 넓어진다. render() 는
//    start/stop/bar/surfaces/domains/msg 를, renderSurfaces 는 smsg 를 만진다.
// 🔴 초판 대역은 `$` 가 **모든 id 에 같은 객체**를 돌려줬다 — 그 상태로 이 구간을 넓히면
//    #bar 의 display 가 #surfaces 의 display 를 덮어써도 모른다(**실물보다 관대한 스텁**).
//    id 마다 다른 객체를 준다. 대역을 넓힐 때 관대해지는 것이 이 저장소의 반복 함정이다.
// 🔵 `$` 를 여기서 정의하지 않는다 — 구간 안에 `const $ = (id) => document.getElementById(id)`
//    가 있으므로, 여기서 또 선언하면 **중복 선언으로 죽는다.** 대신 getElementById 를 준다.
const _els = {};
function _el(id) {
  if (!_els[id]) _els[id] = { id, textContent: "", disabled: false, style: {} };
  return _els[id];
}
function mkA(domain, served, host, url) {
  // 🔵 빈 문자열은 **속성이 없는 것**으로 만든다 — 브라우저의 dataset 도 없는 속성에는
  //    undefined 를 준다. 빈 문자열을 그대로 두면 `if (a.dataset.url)` 같은 술어가
  //    실물과 다르게 동작해서, 이 대역이 **실물보다 관대한 스텁**이 된다.
  const ds = { domain };
  if (served) ds.served = served;
  if (host) ds.host = host;
  if (url) ds.url = url;
  return { dataset: ds, _cls: new Set(), href: undefined,
           classList: { toggle(c, on) { on ? this._o._cls.add(c) : this._o._cls.delete(c); } },
           removeAttribute(k) { delete this[k]; } };
}
// 🔴 앵커 목록은 **마크업에서 온다**(Z14_ROWS 로 주입). 여기 하드코딩하면 드라이버의
//    모집단이 트리와 어긋나도 모르고, 링크를 지워도 가드가 조용히 통과한다 —
//    실제로 그렇게 통과했다(이 가드를 쓰면서 그 자리에서 물렸다).
const _as = process.env.Z14_ROWS.trim().split("\n").map((l) => l.split("|"))
  .map(([d,s,h,u]) => { const a = mkA(d,s,h,u); a.classList._o = a; return a; });
global.document = { querySelectorAll: () => _as, getElementById: _el };
const IP = "1.2.3.4";
// 🔴🔴 TASK-MONO-603 — renderSurfaces() 가 아니라 **render() 를 부른다.** 컨테이너
//    가시성은 그 안에 있고, 그것을 안 부르면 583 이 통과한 그 구멍이 그대로 남는다.
function run(state, ip, snap, stale) {
  lastSnap = snap; lastStale = stale;
  render(state, ip, null);
  return {
    rows: _as.map(a => ({ d: a.dataset.domain, off: a._cls.has("off"), href: a.href })),
    disp: _el("surfaces").style.display,
  };
}
// 🔴🔴 TASK-MONO-583 — 스냅샷을 **도메인마다 다르게** 주지 않고 **한 상태로 통일**한다.
//    초판은 상태별로 도메인을 섞어 놓아서 각 칸이 사실상 한 행씩만 쟀다. 통일하면
//    **모든 데모 호스트 행이 네 칸 전부**를 받고, Vercel 행은 **다섯 상태 전부에서**
//    활성이어야 한다는 뒤집힌 단언을 받는다. 모집단이 늘어도 자동으로 덮인다.
const all = (st) => Object.fromEntries(_as.map(a => [a.dataset.domain, { state: st }]));
const out = {
  // 아래 다섯은 **데모가 떠 있는** 판이다(도메인별 헬스만 다르다).
  up:      run("running", IP, all("up"), false),
  down:    run("running", IP, all("down"), false),
  unknown: run("running", IP, {}, false),        // 스냅샷에 키가 없다 → "unknown"
  partial: run("running", IP, all("partial"), false),
  stale:   run("running", IP, all("up"), true),  // state 는 전부 up 인데 헬스가 stale
  // 🔴🔴 TASK-MONO-603 의 칸 — **데모가 꺼져 있다.** ip 도 없다. 이것이 대부분의 시간이다.
  stopped: run("stopped", null, {}, false),
};
const say = (k) => out[k].disp + "|" +
  out[k].rows.map(r => `${r.d}:${r.off ? "off" : "on"}:${r.href || "-"}`).join(" ");
for (const k of ["up","down","unknown","partial","stale","stopped"])
  console.log(k.toUpperCase() + "|" + say(k));
Z14DRV
} > "$z14_js"

z14_out="$(Z14_ROWS="$z14_rows" node "$z14_js" 2>&1)" || { rm -f "$z14_js"; fail "(z14) 링크 판정 실행 실패:"$'\n'"$z14_out"; }
# 🔵 TASK-MONO-603 — 드라이버를 아직 지우지 않는다. 아래 **vercel-0 대조군**이 같은
#    드라이버를 **다른 행 집합**으로 한 번 더 돌린다(대조군은 코드가 아니라 입력이 다르다).
z14_js2="$z14_js"
z14_line() { printf '%s\n' "$z14_out" | sed -n "s/^$1|//p"; }

# 🔴 판정은 **행마다 자기 정책으로** 본다. 아래 두 루프가 AC-2 의 표를 그대로 집행한다:
#
#            | up            | down/unknown/partial/stale
#   vercel   | 활성(정적)     | **활성(정적)**  ← 뒤집힌 칸. 데모가 꺼져도 뜨는 것이 목적이다.
#   demo-host| 활성(파생)     | **비활성 · href 없음**
#
z14_has() { case "$(z14_line "$1")" in *"$2"*) return 0 ;; *) return 1 ;; esac; }
z14_disp() { z14_line "$1" | cut -d'|' -f1; }

while IFS='|' read -r z14_d z14_s z14_h z14_u; do
  [ -n "$z14_d" ] || continue
  if [ "$z14_s" = "vercel" ]; then
    # 🔴🔴 뒤집힌 칸 — **다섯 상태 전부에서** 활성이고 주소는 선언된 정적 주소 그대로다.
    #    값을 여기 다시 적지 않는다(두 벌이면 하나만 고쳐진다) — 마크업이 선언한 값을 쓴다.
    for z14_k in UP DOWN UNKNOWN PARTIAL STALE STOPPED; do
      z14_has "$z14_k" "$z14_d:on:$z14_u" \
        || fail "(z14) [$z14_k] Vercel 에서 서빙되는 '$z14_d' 링크가 열리지 않거나 주소가 틀립니다:"\
          $'\n'"   $(z14_line "$z14_k")"\
          $'\n'"→ 그 화면의 **존재 이유가 데모 호스트를 안 타는 것**입니다(ADR-MONO-067)."\
          $'\n'"   데모 상태에 묶으면 데모가 꺼진 동안(= 대부분의 시간) 죽고, 이관이 무의미해집니다."
    done
    # 🔴 그리고 그 주소는 demoHost() 파생이 **아니어야** 한다 — 파생이면 재기동마다 바뀐다.
    z14_has UP "$z14_d:on:$z14_u" && case "$z14_u" in
      *sslip.io*|*1-2-3-4*) fail "(z14) Vercel 행 '$z14_d' 의 주소가 데모 호스트 파생입니다: '$z14_u'" ;;
    esac
  else
    # ── 데모 호스트 행 — 네 칸 그대로. href 는 **demoHost() 파생**이어야 한다.
    z14_has UP "$z14_d:on:http://$z14_h.1-2-3-4.sslip.io/" \
      || fail "(z14) (1) 도메인이 up 인데 '$z14_d' 링크가 열리지 않거나 주소가 demoHost() 파생이 아닙니다:"\
        $'\n'"   $(z14_line UP)"\
        $'\n'"→ 한 곳이라도 IP 를 직접 조립하면 **그 링크만** 죽고 나머지가 멀쩡해 원인이 안 보입니다."
    # (2) bite — down/unknown/데모정지 는 비활성이고 href 가 **아예 없어야** 한다.
    # 🔵 STOPPED 는 TASK-MONO-603 이 넣었다 — 컨테이너가 보이게 된 뒤에도 **데모 호스트
    #    행은 여전히 닫혀 있어야** 한다. 안 그러면 방문자에게 404 를 주게 된다.
    for z14_k in DOWN UNKNOWN STOPPED; do
      z14_has "$z14_k" "$z14_d:off:-" \
        || fail "(z14) (2) [$z14_k] 안 뜬 화면 '$z14_d' 가 열려 있습니다: $(z14_line "$z14_k")"\
          $'\n'"→ 부팅 창에서 그 화면은 404 이고, 방문자는 그것을 **\"고장났다\"** 로 읽습니다."\
          $'\n'"→ 비활성은 보기만 흐린 것으로는 부족합니다 — href 를 제거하세요(클릭도 새 탭도 없어야 합니다)."
    done
    # (3) partial — 명시된 결정(비활성)대로 동작하는가.
    z14_has PARTIAL "$z14_d:off:-" \
      || fail "(z14) (3) partial 도메인 '$z14_d' 의 링크가 열려 있습니다: $(z14_line PARTIAL)"\
        $'\n'"→ partial 은 그 도메인의 일부가 unhealthy 라는 뜻이고, 웹 표면이 그중 하나인지"\
        $'\n'"   이 페이지는 알 수 없습니다. 결정은 **비활성**이며 index.html 에 근거가 적혀 있습니다."
    # (4) stale — state 가 전부 up 이어도 비활성이어야 한다.
    z14_has STALE "$z14_d:off:-" \
      || fail "(z14) (4) 헬스가 stale 인데 '$z14_d' 링크가 열려 있습니다: $(z14_line STALE)"\
        $'\n'"→ health_stale 은 TASK-MONO-551 이 만든 필드이고, 참일 때 up 을 믿으면"\
        $'\n'"   **꺼진 스택의 링크를 초록으로** 줍니다(실측 당시 호스트는 15분째 무응답이었습니다)."
  fi
done <<< "$z14_rows"

# ---------------------------------------------------------------------------
# 🔴🔴 TASK-MONO-603 — **컨테이너 축.** 행이 열려 있어도 블록이 숨겨져 있으면 안 보인다.
# ---------------------------------------------------------------------------
# 583 은 이 축을 하나도 안 쟀고, 그래서 링크 정책을 다 고쳐 놓고도 방문자 경로가 안
# 바뀌었다(실측: 서빙본은 새 마크업인데 `/status`=stopped 라 `display:none` 이었다).
for z14_k in UP DOWN UNKNOWN PARTIAL STALE STOPPED; do
  [ "$(z14_disp "$z14_k")" = "block" ] \
    || fail "(z14) [$z14_k] #surfaces 가 보이지 않습니다 (display='$(z14_disp "$z14_k")')."\
      $'\n'"→ 데모가 떠 있으면 당연히 보여야 하고, **꺼져 있어도 Vercel 행이 있으면 보여야** 합니다."\
      $'\n'"→ 행만 활성으로 두고 블록을 숨기면 그 링크는 **활성인 채로 영영 안 보입니다.**"\
      $'\n'"   그것이 TASK-MONO-583 이 통과하면서 남긴 구멍이고 TASK-MONO-603 이 닫은 것입니다."
done

# 🔴🔴 **대조군 — vercel 행이 0개면 예전 동작(숨김)이 유지돼야 한다.**
# 이것이 없으면 "항상 보이게" 로 바꾼 것과 구별되지 않는다. 그 구별이 이 칸의 전부다:
# 술어가 **마크업 선언에서 파생**됐는지, 아니면 그냥 상수 true 가 됐는지를 가른다.
z14_rows_demo="$(printf '%s\n' "$z14_rows" | awk -F'|' '$2=="demo-host"')"
if [ -n "$z14_rows_demo" ]; then
  z14_out_d="$(Z14_ROWS="$z14_rows_demo" node "$z14_js2" 2>&1)" \
    || fail "(z14) vercel-0 대조군 실행 실패:"$'\n'"$z14_out_d"
  z14_disp_d="$(printf '%s\n' "$z14_out_d" | sed -n 's/^STOPPED|//p' | cut -d'|' -f1)"
  [ "$z14_disp_d" = "none" ] \
    || fail "(z14) 대조군 실패 — vercel 행이 **0개**인데 데모가 꺼진 상태에서 #surfaces 가 보입니다 (display='$z14_disp_d')."\
      $'\n'"→ 가시성이 마크업 선언에서 파생되지 않고 **상수로 굳은 것**입니다."\
      $'\n'"→ 그러면 ADR-MONO-067 단계 3·4 가 행을 옮기거나 되돌릴 때 그 상수는 **조용히 거짓**이 되고,"\
      $'\n'"   방문자는 아무것도 열 수 없는 빈 블록을 봅니다."
  # 🔵 그 대조군 판에서도 **데모가 떠 있으면** 보여야 한다 — 예전 동작을 잃지 않았는지.
  z14_disp_du="$(printf '%s\n' "$z14_out_d" | sed -n 's/^UP|//p' | cut -d'|' -f1)"
  [ "$z14_disp_du" = "block" ] \
    || fail "(z14) 대조군 실패 — vercel 행이 없어도 **데모가 떠 있으면** #surfaces 는 보여야 합니다 (display='$z14_disp_du')."\
      $'\n'"→ 이 티켓은 조건을 **넓힌 것**이지 예전 조건을 대체한 것이 아닙니다."
else
  fail "(z14) demo-host 행이 0개라 vercel-0 대조군을 만들 수 없습니다 — 이 칸이 공허해집니다."
fi
rm -f "$z14_js2"

ok "방문자 화면 링크 ${z14_got}개 — demo-host ${z14_n_demo}행(up 활성·down/unknown/partial/데모정지 비활성·href 제거·demoHost() 파생) · vercel ${z14_n_vercel}행(여섯 상태 전부 활성·정적 주소) · 컨테이너 축(꺼진 데모에서도 보임 · vercel-0 대조군은 숨김 유지)"

# ---------------------------------------------------------------------------
# ---------------------------------------------------------------------------
# ---------------------------------------------------------------------------
# ---------------------------------------------------------------------------
# =============================================================================
# (z15) 부팅 완료 판정이 **HTTP 표면**을 보는가 — TASK-MONO-552 AC-3
# =============================================================================
# 🔴 이 티켓의 발견: 컨테이너 **99/102 가 healthy 인 채로 표면이 전멸**했다. 그러니
#    컨테이너 헬스(그 재측정 포함, MONO-559)는 이 명제의 증거가 아니다.
# 🔴 형제 가드 (z13)은 이 축을 **못 본다** — `DEMO_DOMAIN=local` 로 돌기 때문에 표면
#    검사가 건너뛰기 가지로만 간다. 즉 z13 이 초록인 것은 이 코드가 옳다는 뜻이 아니다.
#    (통과가 무효일 수 있다 — 그래서 여기서 **찌르는 경로 자체**를 돌린다.)
# 🔴 그래서 `curl` 을 스텁으로 갈아끼우고 **응답 코드를 우리가 정한다.**
echo "[verify] (z15) 부팅 판정이 HTTP 표면을 보는가 (TASK-MONO-552 AC-3)"
# 🔴 트리 준비는 형제 가드 (z13) 과 **같은 모양**이어야 한다. 초판은 `projects/*/`의
#    compose 파일을 안 옮겼고, 그러면 `demo-up.sh` 가 없는 파일을 붙들고 재시도를 돌아
#    **가드가 끝나지 않는다**(실측: 600s 타임아웃). 초록도 빨강도 아닌 그 상태가 제일 나쁘다.
z15_tmp="$(mktemp -d)"
mkdir -p "$z15_tmp/infra" "$z15_tmp/bin"
cp -r "$ROOT/infra/demo" "$z15_tmp/infra/demo"
if [ -d "$ROOT/infra/traefik" ]; then cp -r "$ROOT/infra/traefik" "$z15_tmp/infra/traefik"; fi
for z15_d in "$ROOT"/projects/*/; do
  z15_n="$(basename "$z15_d")"
  mkdir -p "$z15_tmp/projects/$z15_n"
  for z15_f in "$z15_d"*.yml "$z15_d".env.example; do
    if [ -f "$z15_f" ]; then cp "$z15_f" "$z15_tmp/projects/$z15_n/"; fi
  done
done
if ! bash "$z15_tmp/infra/demo/provision-demo-env.sh" >/dev/null 2>&1; then
  rm -rf "$z15_tmp"
  fail "(z15) 임시 트리 .env 프로비저닝 실패 — 이후 판정이 env-preflight 에 막혀 무효가 됩니다."
fi
# 🔴 `seed-demo-domain.sh` 는 **`DEMO_DOMAIN=local` 일 때만 no-op** 이다. 이 가드는 표면을
#    찌르려고 **진짜 모양의 도메인**을 써야 하므로 그 no-op 가지로 못 간다 — 그러면 없는 DB 를
#    5분 기다리다 실패하고, **표면과 무관한 이유로** 대조군이 빨개진다(실측: z15 초판이 그랬다).
#    형제 (z13)이 이 함정을 안 밟는 것은 `DEMO_DOMAIN=local` 로 돌기 때문이고, 그것이 곧
#    z13 이 이 축을 못 보는 이유이기도 하다. 시험 대상이 아닌 것은 대역으로 바꾼다.
printf '#!/usr/bin/env bash
exit 0
' > "$z15_tmp/infra/demo/seed-demo-domain.sh"
chmod +x "$z15_tmp/infra/demo/seed-demo-domain.sh"

cat > "$z15_tmp/bin/docker" <<'Z15DOCKER'
#!/usr/bin/env bash
if [ "$1" = "compose" ]; then
  p=""; while [ $# -gt 0 ]; do case "$1" in -p) p="$2"; shift 2 ;; *) shift ;; esac; done
  if [ "$p" = "${FAILDOM:-}" ]; then echo "dependency failed: ${p}" >&2; exit 1; fi
  exit 0
fi
if [ "$1" = "ps" ]; then
  case " $* " in *" -q "*) echo "deadbeef"; exit 0 ;; esac
  slug=""; for a in "$@"; do case "$a" in label=com.docker.compose.project=*) slug="${a##*=}" ;; esac; done
  if [ "$slug" = "${FAILDOM:-}" ] && [ "${RECHECK:-up}" = "down" ]; then exit 0; fi
  echo "running|Up 3 minutes (healthy)"; exit 0
fi
exit 0
Z15DOCKER
chmod +x "$z15_tmp/bin/docker"

# 🔴 스텁 curl 은 **요청한 URL 을 파일에 적는다.** "안 물었다" 와 "찌른 적이 없다" 는
#    다른 사건이고, 후자는 초록으로 보인다 — 무는지 읽기 전에 **주입을 증명한다.**
cat > "$z15_tmp/bin/curl" <<'Z15CURL'
#!/usr/bin/env bash
url=""; for a in "$@"; do case "$a" in http://*) url="$a" ;; esac; done
[ -n "$url" ] && echo "$url" >> "${Z15_PROBE_LOG:-/dev/null}"
case "$url" in
  *"${Z15_DEAD_HOST:-@@none@@}"*) printf '000' ;;
  *) printf '%s' "${Z15_CODE:-200}" ;;
esac
Z15CURL
chmod +x "$z15_tmp/bin/curl"

z15_run() {  # $1=FAILDOM  $2=RECHECK  $3=DEAD_HOST  $4=SURFACE_SRC(옵션) → rc 를 echo
  local rc=0
  # 🔴 조건부 할당 접두사로 쓰지 마라. bash 는 **할당 접두사를 확장 전에** 판별하므로
  #    그 확장 결과는 할당이 아니라 **명령**으로 실행되고 rc=127 이 된다. 초판이 그랬고,
  #    아래 첫 검사(rc != 0)가 그 127 을 **"제대로 빨개졌다" 로 통과시켰다** — 문구 검사가
  #    없었으면 이 칸은 아무것도 시험하지 않으면서 초록이었다. 항상 값을 정해 넘긴다.
  local src="${4:-$z15_tmp/infra/demo/aws/site/index.html}"
  : > "$z15_tmp/probe.log"
  ( cd "$z15_tmp" && PATH="$z15_tmp/bin:$PATH" \
      FAILDOM="$1" RECHECK="$2" Z15_DEAD_HOST="$3" Z15_PROBE_LOG="$z15_tmp/probe.log" \
      DEMO_SEED=0 DEMO_DOMAIN=1-2-3-4.sslip.io DEMO_UP_ATTEMPTS=2 DEMO_UP_RETRY_SLEEP=1 \
      DEMO_SURFACE_ATTEMPTS=1 DEMO_SURFACE_SLEEP=0 DEMO_SURFACE_SRC="$src" \
      bash infra/demo/demo-up.sh console ecommerce fan ) > "$z15_tmp/run.log" 2>&1 || rc=$?
  echo "$rc"
}
z15_die() { rm -rf "$z15_tmp"; fail "$@"; }

# (1) 대조군 — 표면이 전부 응답하면 초록이고, 검사했다는 사실이 보여야 한다.
z15_rc1="$(z15_run '' up '')"
z15_log1="$(cat "$z15_tmp/run.log")"
z15_probe1="$(cat "$z15_tmp/probe.log")"
[ "$z15_rc1" = "0" ] || z15_die "(z15) 대조군 실패 — 표면이 전부 200 인데 rc=$z15_rc1 입니다."\
  $'\n'"→ 정상 부팅을 빨갛게 만드는 가드는 곧 꺼진다. 로그: $z15_log1"
# 🔴🔴 **주입 증명이 먼저다.** 찌른 적이 없으면 아래 bite 는 아무것도 시험하지 않는다.
# 🔴🔴 TASK-MONO-583 — 기대 건수를 **상수로 적지 않는다.** 찌르는 대상은 이제 «데모 호스트에서
#    서빙되는 행» 이고 그 수는 (z14) 가 마크업에서 세어 둔 `z14_n_demo` 다. 여기에 3 을
#    박아 두면 화면 하나가 Vercel 로 옮겨질 때마다 이 칸이 **엉뚱한 이유로** 빨개진다.
#    🔴 그렇다고 없으면 통과시키지 않는다 — 못 읽었으면 모르는 것이다.
[ -n "${z14_n_demo:-}" ] && [ "$z14_n_demo" -ge 1 ] \
  || z15_die "(z15) (z14) 가 센 데모 호스트 행 수를 못 읽었습니다 — 기대 건수를 정할 수 없습니다."
z15_n="$(printf '%s\n' "$z15_probe1" | grep -c 'http://' || true)"
[ "$z15_n" -ge "$z14_n_demo" ] || z15_die "(z15) 표면을 **찌른 적이 없습니다** (요청 $z15_n 건 / 기대 $z14_n_demo 건)."\
  $'\n'"→ 판정이 HTTP 를 보지 않는다는 뜻이고, 이 가드의 나머지 칸은 전부 공허해집니다."\
  $'\n'"→ 컨테이너 99/102 가 healthy 인 채로 표면이 전멸한 것이 이 티켓의 발견입니다."
printf '%s\n' "$z15_log1" | grepq "HTTP 표면 ${z14_n_demo}/${z14_n_demo}" \
  || z15_die "(z15) 표면을 몇 개 봤는지 로그가 말하지 않습니다(기대 ${z14_n_demo}/${z14_n_demo}) — 셀 수 없는 검사는 줄어도 모릅니다."\
    $'\n'"→ 로그: $(printf '%s\n' "$z15_log1" | grep 'HTTP 표면' || echo '(HTTP 표면 줄 없음)')"

# 🔴🔴 TASK-MONO-583 — 아래 (2)(3)의 대상을 `web.ecommerce` → `web.fan-platform` 으로 옮겼다.
#    ecommerce 행은 이제 Vercel 에서 서빙되므로 **부팅 판정이 아예 찌르지 않는다.** 그 이름을
#    그대로 뒀다면 (2)의 bite 는 죽일 표면이 없어 **안 물고**, (3)의 "안 찔렀다" 는
#    **공허하게 참**이 된다 — 둘 다 초록인 채로 아무것도 시험하지 않는 모양이다.
#
# 🔴🔴 TASK-MONO-618 (2026-09-04) — **두 번째 이사다. `web.fan-platform` → `console`.**
#    단계 4 가 팬 행마저 Vercel 로 옮겼고, 그래서 이 가드는 **자기 bite 대상이 사라진 것을
#    스스로 잡아냈다** — 이 변경을 처음 랜딩했을 때 (2)가 *"표면이 안 뜨는데 성공으로
#    끝났습니다"* 로 빨개졌다. 🔵 **그것이 이 칸이 옳게 동작한 증거다**: 583 이 예견한
#    「대상이 Vercel 로 가면 bite 가 공허해진다」가 정확히 재현됐고, 가드가 먼저 물었다.
#
#    🔴🔴 **다음 사람에게 — 이 축은 단계 3 에서 «죽는다».** 남은 데모 호스트 표면은
#    `console` **하나뿐**이고, 그것마저 Vercel 로 가면(ADR-MONO-067 단계 3) 이 가드는
#    죽일 표면이 0개가 되어 **다시 옮길 곳이 없다.** 그때 필요한 것은 세 번째 이사가
#    아니라 **설계 변경**이다(`SURFACE_FLOOR` 가 0 이 되면 「부팅 판정이 HTTP 표면을
#    본다」는 명제 자체가 공허해진다). 🔴 그 시점에 이 칸을 **조용히 지우지 마라** —
#    지우면 552 가 산 축이 아무 기록 없이 사라진다.
#
# 🔵 대상 선정을 **상수 하나로** 모았다. 세 곳에 흩어져 있으면 다음 이사에서 한 곳만
#    고쳐지고, 안 고쳐진 쪽은 공허하게 참이 된다.
z15_target_host="console"   # 데모 호스트가 실제로 서빙하는 표면 (2026-09-04 기준 유일)
z15_target_dom="console"    # 그 표면을 소유한 도메인 슬러그

# (2) bite — 도메인은 up 인데 표면 하나가 안 뜬다. **빨개져야 하고 이름이 찍혀야 한다.**
z15_rc2="$(z15_run '' up "$z15_target_host")"
z15_log2="$(cat "$z15_tmp/run.log")"
[ "$z15_rc2" != "0" ] || z15_die "(z15) 표면이 안 뜨는데 **성공으로 끝났습니다.**"\
  $'\n'"→ 이것이 이 티켓 그 자체다: 컨테이너는 전부 healthy 인데 방문자가 여는 주소가 404 다."\
  $'\n'"→ 판정이 컨테이너만 보면 면접관이 보는 화면과 부팅 결과가 갈라집니다."\
  $'\n'"→ 🔴 bite 대상이 '$z15_target_host' 인데, 그 행이 Vercel 로 옮겨갔다면 이 칸은"\
  $'\n'"   죽일 표면이 없어 공허해집니다. 위 주석의 «이 축은 단계 3 에서 죽는다» 를 읽으세요."
printf '%s\n' "$z15_log2" | grepq "HTTP 표면 미도달.*$z15_target_host" \
  || z15_die "(z15) 안 뜬 표면의 **이름이 없습니다** — 빨간데 어디가 문제인지 알 수 없습니다."

# (3) 대조군 — 도메인 자체가 안 뜬 표면은 **찌르지 않는다.**
# 🔴 찌르면 한 결함이 두 줄로 보고돼 원인이 둘인 것처럼 읽히고, 실패 사유가 흐려진다.
z15_rc3="$(z15_run "$z15_target_dom" down '')"
z15_log3="$(cat "$z15_tmp/run.log")"
z15_probe3="$(cat "$z15_tmp/probe.log")"
printf '%s\n' "$z15_probe3" | grepq "$z15_target_host" \
  && z15_die "(z15) 도메인이 안 떴는데 그 표면을 찔렀습니다 — 한 결함이 두 줄로 보고됩니다."
printf '%s\n' "$z15_log3" | grepq "HTTP 표면 미검사.*$z15_target_host" \
  || z15_die "(z15) 안 찌른 표면을 **침묵으로** 넘겼습니다 — 검사하지 않았다는 사실이 남아야 합니다."

# (3b) 🔴🔴 TASK-MONO-583 — **Vercel 행은 아예 찌르지 않는다.** 데모 호스트에 그 표면이
#    존재하지 않으므로, 찌르면 부팅 판정이 영원히 열리지 않는 주소를 12번 재시도하며
#    기다리고 그 실패는 **"데모가 안 떴다"** 로 읽힌다. (1)의 정상 판에서 확인한다 —
#    (3)처럼 도메인이 죽어서 안 찌른 것과 구별해야 하므로 **전부 up 인 판**에서 본다.
# 🔴🔴 TASK-MONO-618 — **Vercel 행이 하나에서 둘이 됐다.** 이름을 하나만 적어 두면
#    새로 옮겨간 행은 **아무도 안 보는 채로** 남는다(그리고 그 칸은 초록이다).
#    ⇒ 목록으로 돌린다. 단계 3 이 console 을 옮기면 **여기에 한 줄을 더해야 한다.**
# 🔵 바닥: 목록이 비면 이 칸은 아무것도 안 하면서 통과한다 — 그것을 막는다.
z15_vercel_hosts="web.ecommerce web.fan-platform"
z15_vercel_n=0
for z15_vh in $z15_vercel_hosts; do
  z15_vercel_n=$((z15_vercel_n + 1))
  printf '%s\n' "$z15_probe1" | grepq "$z15_vh" \
    && z15_die "(z15) Vercel 에서 서빙되는 표면 '$z15_vh' 을 데모 호스트에서 찔렀습니다."\
      $'\n'"→ 그 주소는 데모 호스트에 존재하지 않습니다(ADR-MONO-067). 12번 재시도한 뒤"\
      $'\n'"   실패로 세어지고, 그 실패는 '데모가 안 떴다' 로 읽힙니다."
done
[ "$z15_vercel_n" -ge 2 ] || z15_die \
  "(z15) Vercel 행 목록이 ${z15_vercel_n}개뿐입니다 (바닥 2: web.ecommerce · web.fan-platform)."\
  $'\n'"→ 목록이 줄면 이 칸은 «안 찔렀다» 를 공허하게 통과합니다. ADR-MONO-067 이 화면을"\
  $'\n'"  데모로 되돌린 것이 아니라면 목록을 복구하세요."

# (4) 판정 불가 — 표면 목록을 못 읽으면 **초록이면 안 된다.**
# 🔴 빈 목록이면 이 검사는 아무것도 안 하면서 통과한다. 하한이 그것을 막는지 본다.
: > "$z15_tmp/empty.html"
z15_rc4="$(z15_run '' up '' "$z15_tmp/empty.html")"
z15_log4="$(cat "$z15_tmp/run.log")"
[ "$z15_rc4" != "0" ] || z15_die "(z15) 표면 목록이 **비었는데 초록**입니다 — 아무것도 안 보면서 통과합니다."
# 🔴 127 은 "가드가 물었다" 가 아니라 "하네스가 죽었다" 다. 둘을 섞으면 칸이 공허해진다.
[ "$z15_rc4" != "127" ] || z15_die "(z15) 하네스가 죽었습니다(rc=127) — 이 칸은 아무것도 시험하지 않았습니다."
printf '%s\n' "$z15_log4" | grepq 'HTTP 표면 판정 불가' \
  || z15_die "(z15) 목록을 못 읽은 것이 '판정 불가' 로 구별되지 않습니다 — '표면 정상' 과 섞입니다."

# (5) 🔴 목록이 **론처 마크업에서 온다**는 것 — 복사본이 아니라 그 파일을 읽는가.
# 마크업에 표면을 하나 더 넣고 판정이 **따라오는지** 본다. 안 따라오면 어딘가에 두 번째
# 목록이 있는 것이고, 그 어긋남은 "약속했는데 안 열리는 화면" 으로 나타난다.
# 🔴 TASK-MONO-583 — 추가하는 행도 **출처를 선언해야** 한다. 선언이 없으면 demo-up.sh 가
#    그 행을 판정 불가로 처리하므로(그것이 옳다), 이 칸이 "안 찔렀다" 로 빨개진다.
sed 's#<div id="smsg"></div>#<a class="open" data-surface data-domain="wms" data-served="demo-host" data-host="z15probe" target="_blank"></a><div id="smsg"></div>#' \
  "$ROOT/infra/demo/aws/site/index.html" > "$z15_tmp/extra.html"
z15_rc5="$(z15_run '' up '' "$z15_tmp/extra.html")"
z15_probe5="$(cat "$z15_tmp/probe.log")"
printf '%s\n' "$z15_probe5" | grepq 'z15probe' \
  || z15_die "(z15) 마크업에 표면을 추가했는데 판정이 **찌르지 않았습니다.**"\
  $'\n'"→ 목록을 그 파일에서 읽지 않고 어딘가에 **복사해 둔** 것입니다. 한쪽만 고쳐집니다."

rm -rf "$z15_tmp"
ok "부팅 판정이 HTTP 표면을 본다 — ${z14_n_demo}/${z14_n_demo} 확인(데모 호스트 행만) · Vercel 행은 안 찌름 · 표면 하나 죽이면 bite · 안 뜬 도메인은 미검사 · 목록 0건은 판정 불가 · 목록은 론처 마크업에서 읽음"

# =============================================================================
# (z16) 정적 칸이 `--live` 게이트 **안에 갇혀** 있지 않은가
# =============================================================================
# 🔴🔴 왜 이 칸이 있는가 (2026-08-22, TASK-MONO-552 AC-3 에서 **실제로 밟았다**):
#    (z15) 를 `if [ "$LIVE" -eq 0 ]` 블록 **안**에 넣었다. 그 블록은 비-live 일 때만
#    돌므로 로컬(`--live` 없이)에서는 6칸이 전부 물었고 **스위트도 초록**이었다.
#    그런데 CI 의 "Demo wrapper smoke" 와 packer 7단계는 **`--live` 로만** 돌린다
#    ⇒ 새 가드는 러너에서 **한 번도 실행되지 않은 채** 체크는 초록이었다.
#    로그를 열어 `(z15)` 가 0건인 것을 보기 전까지, 초록은 그 사실을 말해 주지 않는다.
# 🔵 이 파일 1176행에 *"러너 없는 가드는 썩는다"* 라고 이미 적혀 있었다. 규칙을 적어
#    두는 것으로는 안 막힌다 — **가드가 없는 그 한 지점이 결함이 나는 자리다.**
# 🔴 그러므로 판정 대상은 "칸이 옳은가" 가 아니라 **"칸이 도는 자리에 있는가"** 다.
echo "[verify] (z16) 정적 칸이 --live 게이트 안에 갇혀 있지 않은가"
z16_self="$ROOT/infra/demo/verify-demo-wrapper.sh"
[ -f "$z16_self" ] || fail "(z16) 자기 자신을 찾지 못했습니다: $z16_self"

# 게이트 블록 = **열 0 의** `if [ "$LIVE" -eq 0 ]; then` 부터 열 0 의 첫 `fi` 까지.
# 그 안에 있어도 되는 것은 "정적 검증 PASS" 와 `exit 0` 뿐이다.
#
# 🔴🔴 앵커는 **열 0 고정**이다 (TASK-MONO-608). 예전 앵커는 열을 안 봤고, 이 파일에서
#    그 문자열의 첫 등장은 게이트가 아니라 **바로 위 이 주석**이었다 ⇒ `head -1` 이
#    (z16) 자기 본문을 집었다. 그리고 안전망이 «구간에 '정적 검증 PASS' 가 있나» 를
#    물은 탓에 — 잘못 집힌 구간이 하필 그 문자열을 세 번 언급하는 코드라 — 통과했다.
#    **파수꾼이 「내가 맞는 방을 봤나」를 물었는데, 잘못 든 방이 그 답을 벽에 적어 둔 방이었다.**
# 🔴 그래서 안전망도 **문자열이 아니라 구조**로 묻는다: 구간이 자기 본문과 겹치지 않고
#    (`z16_` 토큰이 없고), `exit 0` 이 있고, 짧아야 한다. 사유마다 다른 값을 돌려준다 —
#    원인을 지목하는 메시지에는 그 원인만 무는 술어가 붙어야 하기 때문이다.
# 🔴 게이트가 **둘 이상**이면 `head -1` 이 다시 위험해진다 ⇒ 판정 불가로 세운다.
z16_trapped() {
  # 🔵 TASK-MONO-609: 앵커 리터럴은 이제 `live_gate_line` 안에만 있다.
  local z16_rc=0
  z16_g="$(live_gate_line "$1")" || z16_rc=$?
  if [ "$z16_rc" -eq 1 ]; then echo "__NOGATE__"; return 0; fi
  if [ "$z16_rc" -eq 2 ]; then echo "__MULTIGATE__:$z16_g"; return 0; fi
  z16_fi="$(awk -v s="$z16_g" 'NR>s && /^fi$/ {print NR; exit}' "$1")"
  [ -n "$z16_fi" ] || { echo "__NOFI__"; return 0; }
  sed -n "${z16_g},${z16_fi}p" "$1" > "$z16_region"
  # 블록을 제대로 집었는지 먼저 확인한다 — 엉뚱한 구간에서 "0건" 이 나오면 그건
  # 통과가 아니라 **판정 불가**다.
  if grep -q 'z16_' "$z16_region"; then echo "__WRONGBLOCK__:self"; return 0; fi
  if ! grep -qE '^[[:space:]]*exit 0$' "$z16_region"; then echo "__WRONGBLOCK__:noexit"; return 0; fi
  if [ "$(wc -l < "$z16_region")" -gt 12 ]; then echo "__WRONGBLOCK__:toolong"; return 0; fi
  sed -n 's/.*echo "\[verify\] (\([A-Za-z0-9]*\)).*/\1/p' "$z16_region"
}

z16_region="$(mktemp)"

# 추출기가 실제로 칸을 뽑을 줄 아는지부터 — 파일 전체에서 0건이면 술어가 형태를 놓친 것이다.
z16_all="$(sed -n 's/.*echo "\[verify\] (\([A-Za-z0-9]*\)).*/\1/p' "$z16_self" | wc -l)"
[ "$z16_all" -ge 10 ] || { rm -f "$z16_region"; fail "(z16) 이 파일에서 칸 선언을 ${z16_all}건밖에 못 뽑았습니다 — 술어가 형태를 놓쳤습니다."\
  $'\n'"→ 0건(또는 극소수)을 '갇힌 칸 없음' 으로 읽지 않습니다."; }

# (1) 본체 — 게이트 안에 갇힌 칸이 하나도 없어야 한다.
z16_bad="$(z16_trapped "$z16_self")"
case "$z16_bad" in
  __NOGATE__)     rm -f "$z16_region"; fail "(z16) 열 0 의 LIVE 게이트를 찾지 못했습니다 — 구간을 특정할 수 없습니다."\
                    $'\n'"→ 게이트가 들여쓰기됐다면 고칠 것은 가드가 아니라 **게이트**입니다." ;;
  __MULTIGATE__:*) rm -f "$z16_region"; fail "(z16) 열 0 의 LIVE 게이트가 ${z16_bad#__MULTIGATE__:}개입니다 — 어느 것이 그 게이트인지 판정할 수 없습니다."\
                    $'\n'"→ 하나로 합치거나, 이 술어에 «어느 것을 보는가» 를 명시하세요." ;;
  __NOFI__)       rm -f "$z16_region"; fail "(z16) LIVE 게이트의 닫는 fi 를 찾지 못했습니다 — 구간을 특정할 수 없습니다." ;;
  __WRONGBLOCK__:self)
                  rm -f "$z16_region"; fail "(z16) 집힌 구간이 **(z16) 자기 본문**입니다(\`z16_\` 토큰이 들어 있습니다) — 판정 불가입니다."\
                    $'\n'"→ TASK-MONO-608 이 고친 결함이 되돌아온 것입니다. 앵커가 다시 열을 안 보고 있습니다." ;;
  __WRONGBLOCK__:noexit)
                  rm -f "$z16_region"; fail "(z16) 집힌 구간에 \`exit 0\` 이 없습니다 — 게이트 블록이 아닙니다. 판정 불가입니다." ;;
  __WRONGBLOCK__:toolong)
                  rm -f "$z16_region"; fail "(z16) 집힌 구간이 12줄을 넘습니다 — 게이트 블록이 아닙니다. 판정 불가입니다." ;;
esac
[ -z "$z16_bad" ] || { rm -f "$z16_region"; fail "(z16) 이 칸들이 \`--live\` 게이트 안에 갇혀 있습니다: $(echo $z16_bad)"\
  $'\n'"→ CI 의 'Demo wrapper smoke' 와 packer 7단계는 **--live 로만** 돌립니다."\
  $'\n'"   갇힌 칸은 러너에서 한 번도 실행되지 않으면서 체크는 초록입니다."\
  $'\n'"→ 게이트(\`^if [ \"\$LIVE\" -eq 0 ]; then\`) **위쪽** 정적 구간으로 옮기세요."; }

# (2) bite — 갇힌 칸을 하나 만들어 넣으면 술어가 반드시 물어야 한다.
#     🔴 주입이 실제로 됐는지를 **먼저** 확인한다(안 물린 게 아니라 안 넣어진 경우와
#        구별되지 않으면 이 칸은 아무것도 시험하지 않는다).
# 🔴🔴 주입 앵커도 **열 0 고정**이다 (TASK-MONO-608). 예전에는 추출기와 **같은** 느슨한
#    앵커를 써서, 둘이 같은 잘못된 구간에 동의한 채 bite 가 초록으로 보고됐다 —
#    **주입기와 판정기가 같은 오류를 공유하면 대조군이 되지 못한다.**
z16_copy="$(mktemp)"
awk -v gate="$LIVE_GATE_LINE" '{print} $0 == gate && !d {print "  echo \"[verify] (zz9) 주입된 가짜 칸\""; d=1}' \
  "$z16_self" > "$z16_copy"
# 🔴 «파일 어딘가에 zz9 가 있다» 로는 부족하다 — **게이트 블록 «안»에** 들어갔는지를 묻는다.
z16_ig="$(live_gate_line "$z16_copy" || true)"
z16_ifi="$(awk -v s="${z16_ig:-0}" 'NR>s && /^fi$/ {print NR; exit}' "$z16_copy")"
if [ -z "$z16_ig" ] || [ -z "$z16_ifi" ] || ! sed -n "${z16_ig},${z16_ifi}p" "$z16_copy" | grepq 'zz9'; then
  rm -f "$z16_region" "$z16_copy"
  fail "(z16) bite 하네스가 가짜 칸을 **게이트 블록 안에 주입하지 못했습니다** — 이 칸은 아무것도 시험하지 않았습니다."\
    $'\n'"→ 주입 앵커와 판정 앵커가 어긋났습니다. 둘 다 열 0 고정이어야 합니다(TASK-MONO-608)."
fi
z16_bit="$(z16_trapped "$z16_copy")"
rm -f "$z16_region" "$z16_copy"
case " $z16_bit " in
  *" zz9 "*) : ;;
  *) fail "(z16) 갇힌 칸을 주입했는데 술어가 **물지 않았습니다** (반환='$z16_bit')"\
       $'\n'"→ 술어가 틀렸습니다. 통과는 무효입니다." ;;
esac

ok "정적 칸이 --live 게이트에 갇히지 않았다 — 갇힌 칸 0건(칸 선언 ${z16_all}건 추출) · 주입 확인 후 bite"

# =============================================================================
# (z17) DB 데이터가 **자격 출처보다 오래 살 수 없는가** — TASK-MONO-550 AC-4
# =============================================================================
# 🔴 AC-4 는 원래 *"기존 볼륨 위 재기동에서 DB 초기화 값과 앱이 쓰는 값이 어긋나는가"* 를
#    라이브로 재려 했다. 2026-08-22 에 구조를 읽어 보니 **그 명제는 반증 불가능하다**:
#      · EBS 는 루트 볼륨 하나뿐 — 별도 데이터 볼륨도 attachment 도 없다 ⇒ docker 볼륨이 루트에 산다.
#      · `ami = var.ami_id` 라 AMI 를 바꾸면 인스턴스가 **교체**되고 루트 볼륨째 사라진다.
#      · `provision-demo-env.sh` 는 **커밋된 `.env.example` 을 복사**하고 `.env` 가 있으면 건너뛴다
#        — 생성기도 난수도 없다.
#    ⇒ `/stop`→`/start` 는 `.env` 도 DB 도 그대로 살아남아 **값이 같을 수밖에 없고**, AMI 를 바꾸면
#      볼륨이 통째로 사라져 애초에 시험되지 않는다. **틀린 입력이 없는 판정은 초록이어도 공허하다.**
# 🔴 그러므로 이 AC 를 부팅으로 재지 않는다. 재는 대신 **그 불변식을 지킨다** — 오염이 가능해지려면
#    아래 셋 중 하나가 깨져야 하고, 그때 빨개지는 것이 이 칸의 일이다.
#      (1) DB 데이터가 루트 밖(별도 EBS)에 살기 시작한다
#      (2) 루트가 인스턴스 종료 뒤에도 남는다 (`delete_on_termination = false`)
#      (3) AMI 변경이 교체를 일으키지 않는다 (`ignore_changes` 에 `ami`)
# 🔵 08-17 라이브 판정(비-루프백 `psql`, 대조군 rc=2 거부, `master@master_db` rc=0)은 유효하지만
#    그것이 증명한 것은 **신선 볼륨에서 값이 일치한다** 이다. 위 불변식이 그것을 항구화한다.
echo "[verify] (z17) DB 데이터가 자격 출처보다 오래 살 수 없는가 (TASK-MONO-550 AC-4)"
z17_tf="$ROOT/infra/demo/aws/terraform/main.tf"
[ -f "$z17_tf" ] || fail "(z17) terraform main.tf 를 찾지 못했습니다: $z17_tf"

# 위반 코드를 줄 단위로 낸다. 추출이 성립하지 않으면 **빈 출력(통과)이 아니라 사유 코드**를 낸다.
z17_probe() {
  grep -q '^resource "aws_instance"' "$1"  || { echo "__NOINSTANCE__"; return 0; }
  grep -q 'root_block_device' "$1"         || { echo "__NOROOTBLK__";  return 0; }
  grep -q '^resource "aws_ebs_volume"' "$1"       && echo "EBS_VOLUME"
  grep -q '^resource "aws_volume_attachment"' "$1" && echo "VOLUME_ATTACHMENT"
  grep -qE '^[[:space:]]*ebs_block_device[[:space:]]*\{' "$1" && echo "EBS_BLOCK_DEVICE"
  # (2) root_block_device 블록 **안에서만** 본다 — 파일 전체 grep 이면 남의 블록에 걸린다.
  awk '/root_block_device[[:space:]]*\{/{i=1}
       i && /delete_on_termination[[:space:]]*=[[:space:]]*false/{print "ROOT_PERSISTS"; exit}
       i && /^[[:space:]]*\}[[:space:]]*$/{i=0}' "$1"
  # (3) ignore_changes 의 대괄호 안을 토큰으로 쪼갠다 — 부분문자열 매칭은 실재 오답을 만든다.
  awk '/ignore_changes[[:space:]]*=/{ l=$0; gsub(/.*\[|\].*/,"",l); n=split(l,a,/[ ,]+/);
       for(j=1;j<=n;j++) if(a[j]=="ami") print "AMI_IGNORED" }' "$1"
  return 0
}

# (1) 본체 — 위반 0건이어야 한다.
z17_bad="$(z17_probe "$z17_tf")"
case "$z17_bad" in
  *__NOINSTANCE__*) fail "(z17) main.tf 에서 aws_instance 를 찾지 못했습니다 — 판정 불가(구조가 바뀌었습니다)." ;;
  *__NOROOTBLK__*)  fail "(z17) main.tf 에서 root_block_device 를 찾지 못했습니다 — 판정 불가." ;;
esac
[ -z "$z17_bad" ] || fail "(z17) DB 데이터가 자격 출처보다 오래 살 수 있게 됐습니다: $(echo $z17_bad)"\
  $'\n'"→ EBS_VOLUME / VOLUME_ATTACHMENT / EBS_BLOCK_DEVICE = DB 데이터가 루트 밖에 산다는 뜻입니다."\
  $'\n'"→ ROOT_PERSISTS = 인스턴스가 사라져도 루트가 남습니다."\
  $'\n'"→ AMI_IGNORED = AMI 를 바꿔도 인스턴스가 교체되지 않습니다."\
  $'\n'"이 중 하나라도 참이면 **옛 자격으로 초기화된 DB 가 새 \`.env\` 를 만나는 상태**가 가능해집니다"\
  $'\n'"(TASK-MONO-550 AC-4 가 걱정한 바로 그 조건). 그때는 이 가드가 아니라 **라이브 판정**이 필요하며,"\
  $'\n'"비-루프백에서 재야 합니다 — \`pg_hba\` 의 \`127.0.0.1/32 trust\` 는 틀린 비밀번호도 통과시킵니다."

# (2)~(4) bite — 세 갈래를 각각 주입해 물리는지 본다.
#     🔴 주입이 실제로 됐는지를 **먼저** 확인한다. 안 물린 것과 안 넣어진 것을 구별하지 못하면
#        이 칸은 아무것도 시험하지 않는다(이 저장소가 반복해서 데인 자리다).
z17_bite() {   # $1=설명  $2=기대코드  $3=주입 sed/awk 프로그램  $4=주입확인 grep 패턴
  z17_cp="$(mktemp)"
  awk "$3" "$z17_tf" > "$z17_cp"
  if ! grep -qE "$4" "$z17_cp"; then
    rm -f "$z17_cp"; fail "(z17) bite '$1' 의 **주입이 실패했습니다** — 이 칸은 아무것도 시험하지 않았습니다."
  fi
  z17_got="$(z17_probe "$z17_cp")"; rm -f "$z17_cp"
  case " $(echo $z17_got) " in
    *" $2 "*) : ;;
    *) fail "(z17) bite '$1': $2 를 주입했는데 술어가 **물지 않았습니다** (반환='$(echo $z17_got)')" ;;
  esac
}
z17_bite "별도 EBS 볼륨"        "EBS_VOLUME"   '{print} END{print "resource \"aws_ebs_volume\" \"z17probe\" {"; print "  size = 1"; print "}"}' '^resource "aws_ebs_volume" "z17probe"'
z17_bite "루트가 살아남음"       "ROOT_PERSISTS" '{print} /root_block_device[[:space:]]*\{/{print "    delete_on_termination = false"}' 'delete_on_termination = false'
z17_bite "AMI 변경이 무시됨"     "AMI_IGNORED"  '{ if ($0 ~ /ignore_changes[[:space:]]*=/) sub(/\[user_data\]/, "[user_data, ami]"); print }' 'ignore_changes = \[user_data, ami\]'

ok "DB 데이터가 자격 출처보다 오래 살 수 없다 — 별도 EBS 0건 · 루트 종료시 삭제 · AMI 변경이 교체를 강제 · bite 3/3(주입 확인 후)"

# ---------------------------------------------------------------------------
# (z18) 죽은 sslip OAuth 콜백이 0건인가 — TASK-MONO-606
# ---------------------------------------------------------------------------
# `seed-demo-domain.sh` 는 부팅마다 `.local/` 콜백의 사본을 현재 데모 도메인으로
# 만들어 **덧붙인다.** 원본을 지우지 않는 것은 의도지만(같은 DB 를 로컬로도 쓴다),
# **걷어내기가 없어서** 죽은 공인 IP 가 등록된 채 쌓였다 — 실측 3세대 / 30 URI.
#
# 죽은 등록은 그 IP 를 나중에 할당받은 사람에게 **등록된 redirect_uri** 를 준다.
# PKCE 는 이 축을 막지 않고(공격자가 요청자 자신이라 verifier 를 자기가 고른다),
# 막는 것은 `client_secret` 인데 `platform-console-web` 은 의도된 **public 클라이언트**다
# (V0015 / iam ADR-003).
#
# 🔴 판정을 **순수 함수로 분리한 이유**: 본체는 라이브 DB 를 읽지만 «무는가» 는 DB 없이
# 증명할 수 있어야 한다. 그리고 이 가드가 조용히 통과할 수 있는 길이 셋이다 —
# ① 질의 실패 ② 빈 출력 ③ **모집단 0**(DEMO_DOMAIN=local 이라 시드가 early-exit 한 경우:
# AMI 를 굽는 중이 정확히 그 상태다). 셋 다 «초록» 이 아니라 «판정 안 함» 으로 갈라 두고
# `--require-coverage` 가 그것을 FAIL 로 승격한다 — 가드 (h) 와 같은 규율이다.
#
# 출력: FAIL:<사유> | NOCOVER:<사유> | OK:<판정한 sslip URI 수>
judge_stale_sslip() { # $1=질의 rc  $2=죽은 수  $3=전체 sslip 수
  local rc="$1" dead="$2" total="$3"
  [ "$rc" -ne 0 ] && { echo "NOCOVER:질의 실패(IdP DB 에 닿지 못했다, rc=$rc)"; return 0; }
  case "$dead"  in ''|*[!0-9]*) echo "NOCOVER:죽은-수를 못 읽었다('$dead')";   return 0 ;; esac
  case "$total" in ''|*[!0-9]*) echo "NOCOVER:전체-수를 못 읽었다('$total')"; return 0 ;; esac
  [ "$total" -eq 0 ] && { echo "NOCOVER:sslip 등록이 0건 — 판정할 모집단이 없다"; return 0; }
  [ "$dead" -gt 0 ]  && { echo "FAIL:죽은 sslip 등록 ${dead}건 (전체 sslip ${total}건)"; return 0; }
  echo "OK:$total"
}

echo "[verify] (z18s) 죽은 sslip 판정기가 무는가 — 주입 6칸"
z18s_expect() { # $1=기대 접두어, 나머지=judge 인자
  local want="$1"; shift
  local got; got="$(judge_stale_sslip "$@")"
  case "$got" in
    "$want"*) ;;
    *) fail "(z18s) 인자 [$*] 에서 '$want*' 를 기대했는데 '$got' 이 나왔다" ;;
  esac
}
z18s_expect FAIL    0 1  10   # 죽은 1건 → 문다
z18s_expect FAIL    0 3  30   # 죽은 3건 → 문다
z18s_expect OK      0 0  10   # 깨끗하고 모집단 있음 → 통과
z18s_expect NOCOVER 1 0  10   # 질의 실패를 «이상 없음» 으로 세지 않는다
z18s_expect NOCOVER 0 '' 10   # 빈 출력을 0 으로 세지 않는다
z18s_expect NOCOVER 0 0  0    # 모집단 0 을 통과로 세지 않는다
ok "(z18s) 판정기 6/6 — 죽은 것은 물고, «질의 실패»·«빈 출력»·«모집단 0» 중 어느 것도 초록이 아니다"

echo "[verify] (z19)·(z28) Vercel 로 옮겨간 화면이 데모에서 억제되는가 (ADR-MONO-067 단계 2·4)"
# -----------------------------------------------------------------------------
# 방문자 화면이 Vercel 로 옮겨갔는데 데모 호스트가 자기 사본을 계속 서빙하던 결함이다.
# 억제는 도메인마다 `infra/demo/<slug>-vercel.override.yml` **한 곳**에 선언된다.
#
# 🔴 이 가드가 «없음» 만 보면 안 되는 이유: 렌더가 깨지면 서비스 목록이 통째로 비고,
#    그 0행은 «억제됨» 과 **구별되지 않는다.** 그래서 다섯 칸을 같이 본다 —
#      (1) 억제 파일을 뺀 렌더에는 **있어야** 한다      ← 주입 확인(bite 기준선)
#      (2) 실제 체인 렌더에는 **없어야** 한다
#      (3) base 단독(로컬 모양)에는 **있어야** 한다     ← 대조군
#      (4) (1)과 (2)의 차이가 **정확히 그 서비스 하나**여야 한다
#      (5) 렌더 어디에도 **유령 참조**(depends_on 등)가 남으면 안 된다
#    그리고 어떤 렌더든 서비스 수가 바닥 아래면 FATAL 로 세운다(공허 통과 금지).
#
# -----------------------------------------------------------------------------
# 🔴🔴 TASK-MONO-618 — **왜 함수인가.** 단계 4(fan)가 같은 다섯 칸을 요구했고, 그때
#    선택지는 「(z19)를 90줄 복사해 (z28)을 만든다」 였다. 이 저장소가 반복해서 당한
#    모양이 정확히 그것이다 — **한 사실이 두 절에 있으면 한쪽만 고쳐진다.** 칸을 하나
#    고칠 때 사본이 안 따라오면, 안 따라온 쪽은 **초록인 채로 아무것도 안 잰다.**
#    ⇒ 로직은 한 벌이고 **축마다 호출**한다. 단계 3(console)이 오면 **한 줄**이다.
# 🔴 그렇다고 모집단을 «전자동 유도» 로 하지 않았다 — 축마다 **다른 것**이 필요하다
#    (기대 서비스 이름 · base compose 경로 · 바닥 · 로컬 사용처 문구 · Vercel 주소).
#    그것들을 유도하려 들면 술어가 규약(파일명 패턴)에 의존하게 되고, 규약이 어긋난
#    날 가드는 **모집단 0 으로 조용히 통과**한다. 617 의 런타임 판정자는 유도해도
#    되지만(그쪽 술어는 «존재/부재» 하나다) 여기는 축마다 대조군이 다르다.
# 🔵 대신 **호출 수의 바닥**을 아래에 둔다 — 축이 조용히 사라지는 것을 그것이 막는다.
# -----------------------------------------------------------------------------

z19_render() {  # $@ = compose 파일들 → 서비스 이름 목록(정렬)
  local a=() f
  for f in "$@"; do a+=(-f "$ROOT/$f"); done
  (cd "$ROOT" && docker compose --env-file "$HERE/demo.env" "${a[@]}" config --services 2>/dev/null) | sort
}

# assert_vercel_suppressed <태그> <슬러그> <억제파일> <서비스> <base compose> <바닥> <로컬 사용처> <Vercel 주소>
z19_axes=0
assert_vercel_suppressed() {
  local tag="$1" slug="$2" supp="$3" svc="$4" base="$5" floor="$6" localuse="$7" vercel="$8"
  local chain before_files f before after localr nb na nl diff only dangling pair name cnt

  z19_axes=$((z19_axes + 1))

  chain="${COMPOSE[$slug]:-}"
  [ -n "$chain" ] || fail "($tag) projects.sh 에 [$slug] 체인이 없습니다."

  case " $chain " in
    *" $supp "*) : ;;
    *) fail "($tag) 억제 파일 '$supp' 이 [$slug] 체인에 등록돼 있지 않습니다."\
        $'\n'"→ 선언은 두 곳입니다: 그 파일(무엇을 끄는가) + projects.sh 의 체인(어디에 거는가)."\
        $'\n'"  파일만 있고 체인에 없으면 아무 효력이 없고, 그 상태는 조용합니다." ;;
  esac

  before_files=""
  for f in $chain; do
    [ "$f" = "$supp" ] && continue
    before_files="$before_files $f"
  done

  before="$(z19_render $before_files)"
  after="$(z19_render $chain)"
  localr="$(z19_render "$base")"

  nb="$(printf '%s\n' "$before" | grep -c . || true)"
  na="$(printf '%s\n' "$after"  | grep -c . || true)"
  nl="$(printf '%s\n' "$localr" | grep -c . || true)"

  for pair in "억제전:$nb" "억제후:$na" "로컬:$nl"; do
    name="${pair%%:*}"; cnt="${pair##*:}"
    [ "$cnt" -ge "$floor" ] || fail \
      "($tag) '$name' 렌더가 서비스 ${cnt}개뿐입니다 (바닥 ${floor})."\
      $'\n'"→ 이것은 «억제됐다» 가 아니라 **렌더가 실패했다** 입니다. 0행을 부재로 읽지 않으려고"\
      $'\n'"  이 바닥이 있습니다. 먼저 docker compose config 를 손으로 돌려 사유를 보세요."
  done

  # 🔴🔴 TASK-MONO-618 — **순서가 진단을 정한다.** 예전에는 «억제전 렌더» 칸이 먼저였고,
  #    그래서 base 에 profiles: 가 들어가는 위반(= 이 함수가 잡아야 하는 대표 사고)에서
  #    **두 칸이 동시에 참이 되는데 덜 구체적인 쪽이 먼저 보고**됐다. bite 로 실측했다:
  #    base 에 profiles: 를 주입하면 옛 순서는 *"억제 파일을 뺀 렌더에도 없습니다"* 를
  #    냈고, 그 문구는 진짜 원인(«base 를 건드렸다»)을 **각주로만** 언급한다.
  #    ⇒ 대조군(로컬) 칸을 **먼저** 둔다. 두 칸의 분업이 이제 정확하다:
  #      · 로컬에도 없다      → **base 를 건드렸다** (Scope Out 위반)
  #      · 로컬엔 있는데 억제전에 없다 → **다른 데모 오버라이드**가 지웠다
  #    첫 원인만 보고하고 둘째를 가리는 것은 이 저장소가 반복해 당한 모양이다.
  printf '%s\n' "$localr" | grepq -x "$svc" || fail \
    "($tag) **로컬(base 단독) 렌더에서도** $svc 가 사라졌습니다."\
    $'\n'"→ 이 티켓의 Scope Out 을 넘었습니다. 억제는 데모 체인에만 걸려야 하고, base 는"\
    $'\n'"  로컬이 그대로 씁니다: $localuse"\
    $'\n'"  base compose 에서 profiles/삭제를 되돌리세요."

  printf '%s\n' "$before" | grepq -x "$svc" || fail \
    "($tag) 억제 파일을 **뺀** 렌더에도 $svc 가 없습니다 (로컬 base 에는 있습니다)."\
    $'\n'"→ 그러면 이 가드는 아무것도 증명하지 못합니다 — 억제한 것이 이 파일인지 다른 것인지"\
    $'\n'"  구별할 수 없기 때문입니다."\
    $'\n'"→ base 는 멀쩡하므로 범인은 **다른 데모 오버라이드**입니다: $before_files"

  ! printf '%s\n' "$after" | grepq -x "$svc" || fail \
    "($tag) 데모 렌더에 $svc 가 여전히 있습니다 — 억제가 안 걸렸습니다."\
    $'\n'"→ 데모 호스트가 Vercel 로 옮겨간 화면의 **사본을 다시 서빙**하게 됩니다."\
    $'\n'"  방문자 경로는 $vercel 이고, 사본은 아무도 안 보는 컨테이너입니다."\
    $'\n'"→ 고치는 곳: $supp (그 파일의 profiles: 가 억제 기전입니다)."

  diff="$(comm -23 <(printf '%s\n' "$before") <(printf '%s\n' "$after") | grep -c . || true)"
  only="$(comm -23 <(printf '%s\n' "$before") <(printf '%s\n' "$after") | tr '\n' ' ')"
  [ "$diff" = "1" ] || fail \
    "($tag) 억제 파일이 서비스 ${diff}개를 지웁니다 (기대: 1개, $svc 만)."\
    $'\n'"  지워진 것: ${only}"\
    $'\n'"→ 이 파일의 권한은 «Vercel 로 옮겨간 표면 하나» 입니다. 다른 서비스를 같이 끄면"\
    $'\n'"  데모의 다른 도메인이 조용히 반쪽이 됩니다."

  dangling="$( (cd "$ROOT" && docker compose --env-file "$HERE/demo.env" \
    $(for f in $chain; do printf -- '-f %s ' "$ROOT/$f"; done) config 2>/dev/null) \
    | grep -c "$svc" || true)"
  [ "$dangling" = "0" ] || fail \
    "($tag) 억제 뒤에도 렌더 안에 '$svc' 참조가 ${dangling}건 남았습니다 (depends_on 등)."\
    $'\n'"→ compose 는 없는 서비스에 대한 depends_on 을 기동 시점에 거부합니다 — 증상은 이"\
    $'\n'"  스택 전체가 안 뜨는 것이고, 렌더는 통과하므로 가드 (a)로는 안 잡힙니다."

  ok "($tag) $slug/$svc 억제 — 억제전 ${nb} → 억제후 ${na} (차이 1개, $svc) · 로컬 ${nl}개엔 남아 있다(대조군) · 유령 참조 0건"
}

# --- 축 등록 --------------------------------------------------------------
# 🔴 여기에 줄을 더하는 것이 «단계 N 의 억제» 의 전부다. 지우면 아래 바닥이 문다.
assert_vercel_suppressed z19 ecommerce \
  infra/demo/ecommerce-vercel.override.yml web-store \
  projects/ecommerce-microservices-platform/docker-compose.yml 20 \
  "docs/guides/interview-demo-walkthrough.md §2 · npm run ecommerce:up" \
  "https://store.hubwang.com"

# 🔵 팬의 바닥이 6 인 이유: base 가 9서비스(억제 후 8)라 ecommerce 의 20 을 그대로
#    쓰면 **항상 FATAL** 이다. 바닥은 «렌더가 깨졌는가» 를 재는 것이므로 스택 크기에
#    맞춰야 하고, 상속하면 그 축이 죽는다. (2026-09-04 실측: 9 → 8 · 로컬 9)
assert_vercel_suppressed z28 fan \
  infra/demo/fan-vercel.override.yml fan-platform-web \
  projects/fan-platform/docker-compose.yml 6 \
  "pnpm fan-platform:up (package.json:41-45)" \
  "https://fan.hubwang.com"

# 🔴 축이 조용히 사라지는 것을 막는 바닥. 유도가 아니라 등록이므로, 등록 줄을 지우면
#    그 억제는 **아무도 안 재는 상태로 초록**이 된다 — 그 구멍을 여기서 닫는다.
#    🔵 이 수는 ADR-MONO-067 이 «Vercel 로 옮긴 화면» 을 늘릴 때만 올라간다(단계 3).
z19_axes_floor=2
[ "$z19_axes" -ge "$z19_axes_floor" ] || fail \
  "(z19/z28) 억제 축이 ${z19_axes}개만 등록됐습니다 (바닥 ${z19_axes_floor})."\
  $'\n'"→ 등록 줄이 지워지면 그 도메인의 억제는 아무도 안 재면서 초록이 됩니다."\
  $'\n'"  ADR-MONO-067 이 화면을 되돌린 것이 아니라면 등록 줄을 복구하세요."
ok "(z19/z28) 억제 축 ${z19_axes}개가 등록돼 있고 전부 판정됐다 (바닥 ${z19_axes_floor})"

# ---------------------------------------------------------------------------
# idp_path_prefixes <discovery-json> — URL 값 필드에서 경로의 **첫 세그먼트** 집합
# ---------------------------------------------------------------------------
# idp_path_prefixes <discovery-json> — URL 값 필드에서 경로의 **첫 세그먼트** 집합
# ---------------------------------------------------------------------------
# (z21) 이 라이브 문서에 돌리고, (z21s) 가 **같은 함수**를 픽스처로 검사한다.
# 🔴 함수로 뽑은 이유가 그것이다 — 자체 검사가 사본을 재면 「검사한 코드」와
#    「도는 코드」가 갈라진다.
# issuer 처럼 경로 없는 URL 은 마지막 `grep '^/'` 에서 걸러진다.
# 🔴 `|| true`: grep 0건은 종료코드 1 이고 `set -e` 아래 명령치환 실패는 스크립트를
#    **아무 메시지 없이** 죽인다. 호출부가 «0건» 을 직접 판정해야 한다.
# 🔴 sed 구분자가 `|` 인 이유: 경로 문자 클래스가 `#` 를 포함하므로 `s#...#...#` 는
#    구분자와 클래스 문자가 겹쳐 읽기 어렵다. 겹치지 않는 구분자를 쓴다.
idp_path_prefixes() {
  printf '%s' "$1" \
    | grep -oE '"https?://[^"]+"' \
    | tr -d '"' \
    | sed -E 's|^https?://[^/]+||' \
    | sed -E 's|^/([^/?#]+).*|/\1|' \
    | grep '^/' \
    | sort -u \
    || true
}

echo "[verify] (z27) 억제 런타임 판정자가 존재하고 **부팅에 배선돼 있는가** (TASK-MONO-617)"
# ---------------------------------------------------------------------------
# (z19)는 **렌더**를 본다. 그 축의 공백이 `TASK-MONO-604` § CORRECTION 에 적혀 있다:
# *"렌더는 초록인데 컨테이너는 도는 상태를 (z19)가 판정하지 못한다."*
# `check-suppressed-containers.sh` 가 그 다음 축(**컨테이너의 존재**)을 본다.
#
# 🔴 이 칸은 그 판정자를 **다시 구현하지 않는다.** 정적으로 확인할 수 있는 것만 본다:
#   (1) 파일이 있는가            — 사라지면 축이 통째로 사라진다
#   (2) 문법이 성립하는가        — `bash -n`. 깨진 스크립트는 부팅에서 rc≠0 로만 보인다
#   (3) **부팅에 배선돼 있는가**  — 🔴 파일만 있고 호출이 없으면 효력이 0 인데 **조용하다**.
#                                  (z19)의 첫 칸이 같은 이유로 존재한다.
#   (4) 모집단 유도가 **공허하지 않은가** — `--derive` 를 실제로 돌려 ≥1 줄을 받는다.
#       🔵 유도 로직을 여기 복제하지 않으려고 스크립트에 `--derive` 를 두었다. 한 사실이
#          두 곳에 있으면 한쪽만 고쳐진다.
#   (5) 판정 로직이 서비스 이름을 **하드코딩하지 않는가** — 하드코딩하면 단계 3·4
#       (console·fan)에서 같은 결함이 조용히 재발한다.
#
# 🔴 이 칸은 «억제 대상이 안 돈다» 를 증명하지 **않는다**. 그것은 데모 호스트에서만
#    판정되고(부팅 경로), 여기서 흉내 내면 CI 에서 늘 «컨테이너 0개» 라 공허해진다.
z27_s="$ROOT/infra/demo/check-suppressed-containers.sh"
[ -f "$z27_s" ] || fail "(z27) infra/demo/check-suppressed-containers.sh 가 없습니다 — 억제의 런타임 판정이 사라졌습니다(TASK-MONO-617)."\
  $'\n'"→ (z19)는 렌더만 봅니다. 이 파일이 없으면 «렌더는 초록인데 컨테이너는 도는» 상태를 아무도 판정하지 않습니다."

bash -n "$z27_s" 2>/dev/null || fail "(z27) check-suppressed-containers.sh 의 문법이 깨졌습니다 (bash -n 실패)."

# (3) 배선 — demo-up.sh 가 post_up_call 로 부른다
z27_up="$ROOT/infra/demo/demo-up.sh"
grep -q 'post_up_call .*check-suppressed-containers\.sh' "$z27_up" || fail \
  "(z27) demo-up.sh 가 check-suppressed-containers.sh 를 post_up_call 로 부르지 않습니다."\
  $'\n'"→ 파일만 있고 호출이 없으면 **효력이 0 인데 조용합니다.**"\
  $'\n'"→ 🔴 \`verify --live\` 로 옮기지 마세요: 칸 (f) 가 container_name 고정 때문에 떠 있는"\
  $'\n'"   데모 호스트에서 구조적으로 실패하고, 그 뒤 칸은 **도달하지 않습니다**"\
  $'\n'"   (TASK-MONO-615·616 두 창에서 실측). 러너 없는 검사는 썩습니다."

# (4) 모집단 유도가 공허하지 않은가 — 실제로 돌린다
z27_out="$(bash "$z27_s" --derive 2>/dev/null)"; z27_rc=$?
z27_n=$(printf '%s\n' "$z27_out" | grep -c . || true)
[ "$z27_rc" = "0" ] || fail "(z27) --derive 가 rc=$z27_rc 로 끝났습니다 — 모집단 유도가 성립하지 않습니다."\
  $'\n'"→ \`docker compose config\` 가 실패했을 수 있습니다. 0행을 «억제 대상이 없다» 로 읽으면 안 됩니다."
[ "$z27_n" -ge 1 ] || fail \
  "(z27) 유도된 억제 대상이 **0건** 입니다 — 이 판정자는 지금 아무것도 안 봅니다."\
  $'\n'"→ 오늘 데모 체인에는 억제가 최소 하나 있습니다(ADR-MONO-067 단계 2, web-store)."\
  $'\n'"  0건이면 억제 선언이 체인에서 빠졌거나 유도가 깨진 것입니다 — (z19)도 함께 보세요."\
  $'\n'"  🔴 «억제할 게 없어졌다» 라면 그것은 이 가드를 지울 사유가 아니라 **ADR 을 되돌린** 것입니다."

# (5) 서비스 이름 하드코딩 금지 — 주석을 뺀 본문에서 본다
#     🔴 주석까지 세면 헤더의 설명 문구에 자기가 걸린다(TASK-MONO-604 가 밟은 그 함정:
#        판별자가 자기 설명에 매치한다).
while IFS=$'\t' read -r z27_slug z27_svc; do
  [ -n "${z27_svc:-}" ] || continue
  z27_hits=$(sed 's/#.*//' "$z27_s" | grep -c -- "$z27_svc" || true)
  [ "$z27_hits" = "0" ] || fail \
    "(z27) 판정자 본문(주석 제외)에 서비스 이름 '$z27_svc' 이 ${z27_hits}건 하드코딩돼 있습니다."\
    $'\n'"→ 모집단은 체인에서 **유도**해야 합니다. 목록으로 적으면 ADR-MONO-067 단계 3·4"\
    $'\n'"  (console·fan)에서 같은 결함이 **조용히** 재발합니다."
done <<< "$z27_out"

ok "억제 런타임 판정자: 존재·문법·부팅 배선·유도 ${z27_n}건(하드코딩 0)"

echo "[verify] (z20) IdP 라우터가 discovery 가 광고하는 경로를 전부 덮는가 (TASK-MONO-615 B1)"
# ---------------------------------------------------------------------------
# 근거(TASK-MONO-610 기동 창 V4): discovery 가 `end_session_endpoint` 로
# `<issuer>/connect/logout` 을 **광고하는데** iam-oidc 라우터의 PathPrefix 목록에
# `/connect` 가 없었다 ⇒ 바깥에서 404. 컨테이너 직격은 401 이라 **엔드포인트는 존재**한다.
# 즉 「로그아웃이 없다」가 아니라 **「가는 길이 없다」**이고, 로그인만 재는 검증은 이것을
# 영원히 못 본다. 같은 결함이 `/signup` 으로 이미 한 번 났다(TASK-MONO-380, 가드 (p)).
#
# 🔵 이 칸은 (p) 의 형제다 — (p) 는 **로그인 템플릿의 링크**에서, 이 칸은 **discovery 가
#    광고하는 경로**에서 파생한다. 목록을 손으로 열거하는 한 다음 엔드포인트에서 또 난다.
# 🔴 이 칸은 **정적**이다(게이트 앞). 라이브 IdP 가 없는 CI 에서도 물어야 하기 때문이다 —
#    라이브에서만 도는 칸은 IdP 가 없으면 skip 이고, skip 은 판정이 아니다.
# ---------------------------------------------------------------------------
z20_ovr="$ROOT/infra/demo/iam-traefik.override.yml"
z20_pin="$ROOT/infra/demo/idp-advertised-path-prefixes.txt"
[ -f "$z20_ovr" ] || fail "(z20) $z20_ovr 가 없습니다."
[ -f "$z20_pin" ] || fail "(z20) 핀 파일이 없습니다: $z20_pin"\
  $'\n'"→ 이 파일이 없으면 라우터가 무엇을 덮어야 하는지 아무도 모릅니다."

z20_rule="$(grep -m1 'routers\.iam-oidc\.rule=' "$z20_ovr" || true)"
[ -n "$z20_rule" ] || fail "(z20) iam-oidc 라우터 규칙을 못 찾았습니다 — **술어가 형태를 놓쳤습니다**(0건은 '없음'이 아닙니다)."

z20_have="$(printf '%s' "$z20_rule" | grep -oE 'PathPrefix\(`[^`]+`\)' | sed 's/PathPrefix(`//; s/`)//' | sort -u || true)"
z20_nhave="$(printf '%s\n' "$z20_have" | grep -c '^/' || true)"
[ "${z20_nhave:-0}" -ge 1 ] || fail "(z20) 규칙에서 PathPrefix 를 **하나도** 못 뽑았습니다 — 파싱이 깨졌습니다(가드가 공허해집니다)."

# 🔴 `|| true` 가 없으면 안 된다: grep 이 0건이면 종료코드 1 이고, `set -e` 아래
#    명령치환 실패로 스크립트가 **아무 메시지 없이** 죽는다 — 빌드는 멈추는데
#    원인을 대는 문장이 안 나온다. (이 줄의 결함을 아래 bite ③ 이 잡았다.)
z20_want="$(grep -vE '^[[:space:]]*(#|$)' "$z20_pin" | sort -u || true)"
z20_nwant="$(printf '%s\n' "$z20_want" | grep -c '^/' || true)"
[ "${z20_nwant:-0}" -ge 1 ] || fail "(z20) 핀이 비어 있습니다 ($z20_pin) — **모집단 0 은 통과가 아니라 고장입니다.**"

z20_missing="$(comm -23 <(printf '%s\n' "$z20_want") <(printf '%s\n' "$z20_have") | tr '\n' ' ')"
[ -z "${z20_missing// /}" ] || fail "(z20) discovery 가 광고하는 경로인데 라우터가 안 덮습니다: ${z20_missing}"\
  $'\n'"→ 그 경로는 iam 게이트웨이 라우터로 떨어져 **404** 가 됩니다. 컨테이너 직격은 401 이므로"\
  $'\n'"  엔드포인트는 존재합니다 — 「없다」가 아니라 「가는 길이 없다」입니다."\
  $'\n'"→ infra/demo/iam-traefik.override.yml 의 iam-oidc.rule 에 PathPrefix 를 더하세요."

printf '%s\n' "$z20_have" | grepq -x '/\.well-known' || fail "(z20) 라우터가 \`/.well-known\` 을 안 덮습니다."\
  $'\n'"→ discovery 문서 자신이 거기 삽니다. 안 덮으면 이 핀을 만들 수조차 없습니다."\
  $'\n'"  (그래서 이 접두사는 핀이 아니라 여기서 따로 단언합니다 — 문서는 자기 자신을 광고하지 않습니다.)"

ok "(z20) IdP 라우터가 광고 경로를 전부 덮는다 — 핀 ${z20_nwant}건($(printf '%s' "$z20_want" | tr '\n' ' ')) ⊆ 라우터 ${z20_nhave}건 · /.well-known 별도 확인"

echo "[verify] (z21s) discovery 접두사 추출기가 무는가 — 픽스처 3칸 (TASK-MONO-615 B1)"
# ---------------------------------------------------------------------------
# (z21) 은 라이브 IdP 가 있어야 돌고, 없으면 skip 이다 — **skip 은 판정이 아니다.**
# 그래서 그 칸이 쓰는 추출기만이라도 **정적으로** 증명한다. (z18s) 와 같은 형태다.
# 픽스처 ①은 지어낸 것이 아니라 TASK-MONO-610 기동 창에서 **실제로 받은 바이트**다.
# ---------------------------------------------------------------------------
z21s_real='{"issuer":"http://iam.43-202-166-3.sslip.io","authorization_endpoint":"http://iam.43-202-166-3.sslip.io/oauth2/authorize","device_authorization_endpoint":"http://iam.43-202-166-3.sslip.io/oauth2/device_authorization","token_endpoint":"http://iam.43-202-166-3.sslip.io/oauth2/token","token_endpoint_auth_methods_supported":["client_secret_basic"],"jwks_uri":"http://iam.43-202-166-3.sslip.io/oauth2/jwks","userinfo_endpoint":"http://iam.43-202-166-3.sslip.io/oauth2/userinfo","end_session_endpoint":"http://iam.43-202-166-3.sslip.io/connect/logout","revocation_endpoint":"http://iam.43-202-166-3.sslip.io/oauth2/revoke"}'
z21s_got="$(idp_path_prefixes "$z21s_real" | tr '\n' ' ')"
[ "${z21s_got% }" = "/connect /oauth2" ] || fail "(z21s) 실제 문서에서 기대한 접두사가 안 나왔습니다: '"'"'${z21s_got}'"'"' (기대 '"'"'/connect /oauth2'"'"')"

# 음성 대조군 — 경로 없는 URL 만 있으면 0건이어야 한다. 0건이 나와야 (z21) 의
# "하나도 못 뽑았습니다" 단언이 **공허하지 않다**.
z21s_none="$(idp_path_prefixes '{"issuer":"https://auth.example.com"}' | grep -c '^/' || true)"
[ "${z21s_none:-0}" -eq 0 ] || fail "(z21s) 음성 대조군에서 ${z21s_none}건이 나왔습니다 — 추출기가 경로 없는 URL 을 접두사로 셉니다."

# 양성 대조군 — **새 접두사가 섞이면 반드시 보여야 한다.** 이것이 (z21) 의 존재 이유
# (핀이 낡는 축)이므로, 안 잡히면 그 칸은 영원히 초록이다.
z21s_new="$(idp_path_prefixes '{"a":"https://x/registration/new","b":"https://x/oauth2/token"}' | tr '\n' ' ')"
case "$z21s_new" in
  *"/registration"*) : ;;
  *) fail "(z21s) 양성 대조군에서 새 접두사 /registration 을 못 잡았습니다: ${z21s_new}"       $'\n'"→ 그러면 (z21) 은 핀이 낡아도 영원히 초록입니다." ;;
esac

ok "(z21s) 추출기 3/3 — 실제 문서(/connect /oauth2) · 음성 대조군 0건 · 양성 대조군이 새 접두사를 잡는다"

echo "[verify] (z22) projects/*/.env.example 에 같은 키가 두 번 선언돼 있지 않은가 (TASK-MONO-615 B2)"
# ---------------------------------------------------------------------------
# 근거(TASK-MONO-610 기동 창 V1): 팬 로그인이 `?error=Configuration` 으로 죽었고
# 런타임 `OIDC_CLIENT_SECRET` 이 `replace-with-secret-from-iam-seed` 였다. 값이
# **틀리게 적혀 있었던 게 아니다** — `projects/fan-platform/.env.example` 이 같은 키를
# 두 번 선언했고(25행 = 시드와 맞는 값, 73행 = 자리표시자), dotenv 는 **마지막 선언이
# 이긴다.** `provision-demo-env.sh` 가 그 파일을 그대로 `.env` 로 복사하므로 데모
# 호스트의 유효 값은 73행이었다.
#
# 🔵 두 선언은 **서로 다른 절**에 있었다(백엔드 절 · 프런트 절). 한 사실이 두 절에
#    있으면 한쪽만 고쳐진다 — 73행의 주석은 아직도 "이 클라이언트를 V0011 시드에
#    추가해야 한다" 고 적혀 있었지만 V0011 은 이미 그 클라이언트를 시드하고 있었다.
#    고쳐진 결함의 자리표시자가 회수되지 않은 것이다.
# 🔴 술어를 「fan 의 OIDC_CLIENT_SECRET 이 맞는가」로 쓰지 않는다. **재선언 자체**를
#    문다 — 다음에 다른 파일 다른 키에서 나도 물어야 하기 때문이다.
# 🔴 이 칸은 **정적**이다(게이트 앞). 데모 호스트가 없어도 물어야 한다 — 결함의 출처는
#    저장소의 파일이고, 라이브에서만 도는 칸은 IdP 가 없으면 skip 이다.
# ---------------------------------------------------------------------------
z22_files=0
z22_keys=0
z22_bad=""
for z22_f in "$ROOT"/projects/*/.env.example; do
  [ -f "$z22_f" ] || continue
  z22_files=$(( z22_files + 1 ))
  z22_proj="$(basename "$(dirname "$z22_f")")"

  # 주석·빈 줄을 뺀 `KEY=` 의 키 부분만. `|| true` 는 필수다 — grep 0건은 종료코드 1
  # 이고 `set -e` 아래 명령치환 실패는 스크립트를 **아무 메시지 없이** 죽인다.
  z22_all="$(grep -oE '^[A-Za-z_][A-Za-z0-9_]*=' "$z22_f" | tr -d '=' || true)"
  z22_n="$(printf '%s' "$z22_all" | grep -c . || true)"
  z22_keys=$(( z22_keys + z22_n ))

  z22_dup="$(printf '%s' "$z22_all" | sort | uniq -d || true)"
  for z22_k in $z22_dup; do
    z22_lines="$(grep -n "^$z22_k=" "$z22_f" | cut -d: -f1 | tr '\n' ' ' || true)"
    z22_bad="$z22_bad
  - $z22_proj/.env.example : $z22_k (행 $z22_lines)"
  done
done

# 0건은 "재선언이 없다"가 아니라 "아무 파일도 못 찾았다"일 수 있다.
[ "$z22_files" -ge 1 ] || fail "(z22) projects/*/.env.example 을 하나도 찾지 못했습니다 (ROOT=$ROOT) — **모집단 0 은 통과가 아니라 고장입니다.**"
[ "$z22_keys" -ge 1 ] || fail "(z22) $z22_files 개 파일에서 키를 **하나도** 못 뽑았습니다 — 술어가 형태를 놓쳤습니다(가드가 공허해집니다)."

[ -z "$z22_bad" ] || fail "(z22) .env.example 이 같은 키를 두 번 선언합니다:$z22_bad"\
  $'\n'"→ dotenv 는 **마지막 선언이 이깁니다.** compose 는 파일 옆의 .env 를 자동 로드하므로"\
  $'\n'"  런타임 값은 아래쪽 선언이고, 위쪽의 옳은 값은 아무 효력이 없습니다."\
  $'\n'"→ 선언을 하나로 합치세요. 절이 둘이면 아래 절에는 값 대신 위를 가리키는 주석만 두세요."

ok "(z22) .env.example 재선언 0건 — 파일 $z22_files 개 · 키 $z22_keys 개 검사"

echo "[verify] (z23) .env.example 의 OIDC 클라이언트 시크릿이 IAM 시드가 아는 값인가 (TASK-MONO-615 B2)"
# ---------------------------------------------------------------------------
# (z22) 는 **재선언**을 문다. 그러나 재선언 없이 값 하나만 틀려도 로그인은 똑같이 죽고,
# 증상은 `?error=Configuration` 이라 **어느 env 가 틀렸는지 말하지 않는다.** 그래서 값
# 자체를 시드와 대조한다.
#
# 왜 이 대조가 성립하나 — 시드는 BCrypt 해시를 저장하므로 평문을 되읽을 수 없다.
# 대신 각 시드 SQL 이 평문을 주석으로 **문서화**한다("matches \"scm-dev\"").
# 그 주석이 유일한 기계가독 연결고리다. 형식이 바뀌면 아래 하한이 문다.
#
# 모집단 판별 — `*CLIENT_SECRET` 전부가 대상이 **아니다**. IAM 자신의 .env.example 에는
# 구글·카카오·MS 시크릿이 있는데 그건 **상류 소셜 IdP 자격**이지 이 IdP 에 등록된
# 클라이언트가 아니다. 그래서 **형제 `*CLIENT_ID` 의 값이 시드에 등록된 client_id 인
# 것만** 센다 — 프로젝트 이름을 하드코딩한 제외가 아니라 **증거로 걸러낸다.**
# 실측(2026-09-03): 시크릿 키 8개 → 소셜 3개는 형제 ID 가 시드에 없어 자동 제외,
# 나머지 5개가 모집단(ecommerce 는 형제 ID 가 .env.example 이 아니라 compose 기본값에
# 있어서 거기까지 찾는다 — 안 찾으면 그 한 칸이 조용히 미커버로 남는다).
#
# 🔴 이 블록은 **sed 후방참조를 안 쓴다.** B1 에서 `s|...|/\1|` 이 삽입 경로를 지나며
#    제어문자 0x01 로 접혔고, 함수는 rc=0 으로 빈 값을 뱉었다 — 자체 검사가 없었으면
#    가드는 태어날 때부터 공허했다. 여기서는 후방참조 대신 `grep -o` 로 두 번 거른다.
#    (남은 백슬래시는 fail 메시지의 `$'\n'` 뿐이고, 이 블록은 삽입 경로가 아니라
#     파일로 직접 작성됐다.)
# ---------------------------------------------------------------------------
z23_mig="$ROOT/projects/iam-platform/apps/auth-service/src/main/resources/db/migration"
[ -d "$z23_mig" ] || fail "(z23) IAM 시드 디렉터리가 없습니다: $z23_mig"\
  $'\n'"→ 경로가 바뀌었다면 이 칸의 술어도 같이 바꿔야 합니다. 못 찾은 것을 '검사할 게 없다'로 읽지 않습니다."

# 시드가 문서화한 평문 집합. 후방참조를 안 쓰려고 두 번 거른다.
z23_plain="$(grep -rhoE '(matches|literal string) "[^"]+"' "$z23_mig"/*.sql | grep -oE '"[^"]+"' | tr -d '"' | sort -u || true)"
z23_nplain="$(printf '%s' "$z23_plain" | grep -c . || true)"
[ "${z23_nplain:-0}" -ge 1 ] || fail "(z23) 시드 SQL 에서 평문 시크릿을 **하나도** 못 뽑았습니다 — 주석 형식이 바뀌었습니다."\
  $'\n'"→ 시드는 BCrypt 해시만 저장하므로 주석이 유일한 기계가독 연결고리입니다."\
  $'\n'"  주석을 고쳤다면 이 칸의 추출 패턴도 같이 고치세요. 0건을 통과로 읽지 않습니다."

# 시드에 등록된 client_id 집합.
z23_ids="$(grep -rhoE "'[a-z0-9-]+-client'" "$z23_mig"/*.sql | tr -d "'" | sort -u || true)"
z23_nids="$(printf '%s' "$z23_ids" | grep -c . || true)"
[ "${z23_nids:-0}" -ge 1 ] || fail "(z23) 시드 SQL 에서 client_id 를 **하나도** 못 뽑았습니다 — 모집단을 정할 수 없습니다."

z23_checked=0
z23_bad=""
for z23_f in "$ROOT"/projects/*/.env.example; do
  [ -f "$z23_f" ] || continue
  z23_proj="$(basename "$(dirname "$z23_f")")"
  z23_dir="$(dirname "$z23_f")"

  for z23_k in $(grep -oE '^[A-Z0-9_]*CLIENT_SECRET=' "$z23_f" | tr -d '=' | sort -u || true); do
    z23_idk="${z23_k%CLIENT_SECRET}CLIENT_ID"

    # 형제 ID — 같은 파일의 **마지막** 선언(dotenv 의미론), 없으면 compose 기본값.
    z23_idv="$(grep "^$z23_idk=" "$z23_f" | tail -1 | cut -d= -f2- || true)"
    if [ -z "$z23_idv" ]; then
      z23_idv="$(grep -hoE "$z23_idk:-[^}]+" "$z23_dir"/docker-compose*.yml 2>/dev/null | head -1 | sed "s/.*$z23_idk:-//" || true)"
    fi

    # 형제 ID 가 시드에 없으면 이 시크릿은 이 IdP 의 것이 아니다(상류 소셜 자격 등).
    printf '%s' "$z23_ids" | grepq -x "$z23_idv" || continue

    z23_checked=$(( z23_checked + 1 ))
    z23_v="$(grep "^$z23_k=" "$z23_f" | tail -1 | cut -d= -f2- || true)"
    printf '%s' "$z23_plain" | grepq -x "$z23_v" || z23_bad="$z23_bad
  - $z23_proj/.env.example : $z23_k=$z23_v (클라이언트 $z23_idv)"
  done
done

# 🔵 하한을 1 로 둔 이유 — 이 모집단은 「대기실」이 아니라 안정된 규약(프로젝트마다
#    등록 클라이언트 하나)이다. 0 이 되는 길은 둘뿐이고 **둘 다 이 칸을 고쳐야 하는
#    사건**이다: 키 이름 규약이 바뀌었거나, 시드에서 클라이언트가 사라졌거나.
[ "$z23_checked" -ge 1 ] || fail "(z23) 대조할 클라이언트 시크릿을 **하나도** 찾지 못했습니다 — 0건은 '검사할 게 없다'가 아닙니다."\
  $'\n'"→ 키 이름 규약(*CLIENT_SECRET / *CLIENT_ID)이 바뀌었다면 이 칸의 술어도 같이 바꾸세요."

[ -z "$z23_bad" ] || fail "(z23) .env.example 의 시크릿을 IAM 시드가 모릅니다:$z23_bad"\
  $'\n'"→ 이 값은 \`.env\` 로 복사돼 런타임 자격이 됩니다(provision-demo-env.sh). 시드는 BCrypt"\
  $'\n'"  해시를 갖고 있으므로 \`.env\` 쪽을 바꿔야 맞습니다 — 토큰 요청이 invalid_client 401 로"\
  $'\n'"  떨어지고, 브라우저에는 \`?error=Configuration\` 만 보여 원인을 말하지 않습니다."\
  $'\n'"→ 시드가 아는 평문: $(printf '%s' "$z23_plain" | tr '\n' ' ')"

ok "(z23) 클라이언트 시크릿 ${z23_checked}건이 시드 평문 ${z23_nplain}건 안에 있다 — 등록 client_id ${z23_nids}건 기준으로 모집단 판별"

# ---------------------------------------------------------------------------
echo "[verify] (z26) 컨텍스트 밖 워크스페이스 의존이 이미지 빌드에 실제로 전달되는가 (TASK-MONO-615 C2)"
# ---------------------------------------------------------------------------
# 무엇이 있었나 (2026-09-03, AMI 굽기 7차가 여기서 죽었다):
#
#   #78 ./src/shared/config/demo-backend.ts
#   #78 Module not found: Can't resolve '@demo/backend-resolver'
#   Build 'amazon-ebs.demo' errored after 10 minutes 53 seconds
#
# `fan-platform-web/package.json` 이 `"@demo/backend-resolver":
# "link:../../../../infra/demo/backend-resolver"` 를 갖는데, 그 경로는 이 이미지의
# 빌드 컨텍스트(`projects/fan-platform`) **밖**이다.
#
# 🔴 install 은 통과한다. `pnpm install --frozen-lockfile` 이 576 resolved 로 끝나고
#    (댕글링 심링크를 만든다) 죽는 것은 그 다음 `next build` 다. 즉 **「install 초록」은
#    워크스페이스가 온전하다는 증거가 아니다.**
#
# 🔴🔴 왜 아무도 못 봤나 — **각각 옳은 두 제외가 합쳐져 구멍이 됐다**:
#    · CI 는 `pnpm --filter <app> build` 를 러너에서 돈다(저장소 루트라 링크가 해소된다).
#      **이 이미지를 굽는 CI 잡은 없다.**
#    · 형제 web-store 는 데모에서 억제돼(ADR-MONO-067 단계 2) 데모 굽기가 그 이미지를
#      만들지 않고, Vercel 은 저장소 루트에서 빌드한다.
#    ⇒ 컨테이너 안에서 이 패키지를 빌드하는 자리는 **AMI 굽기 하나뿐**이었고, `link:` 가
#      들어온 `c2df17060`(#3586) 이후 아무도 굽지 않아 **잠복**했다. AMI 굽기는 1시간짜리
#      피드백 루프라 그 자리에서 발견하는 것은 가장 비싼 방법이다.
#
# 술어는 「fan 이 resolver 를 갖는가」가 아니다 — 그건 한 사례다. **모양**을 문다:
#   프로젝트 밖을 가리키는 `link:`/`file:` 의존이 있으면, 그 프로젝트 compose 가
#   같은 대상을 `additional_contexts` 로 넘기고, 그 앱 Dockerfile 이 그 이름을
#   `COPY --from=` 으로 받아야 한다.
# 🔵 이 칸은 경로 정규화에 `realpath -m` 을 쓴다. 요구를 **선언**해 두어야 (z2)가
#    그것을 범위에 넣는다 — 선언하지 않으면 「AMI 안에 그 도구가 있는가」를 아무도 안 묻고,
#    없으면 packer 7단계에서야 죽는다(그게 (z2)의 존재 이유다).
command -v realpath >/dev/null 2>&1 \
  || fail "(z26) 'realpath' 가 없습니다 — 이 칸은 경로를 정규화해서 «컨텍스트 밖인가» 를 판정합니다."\
  $'\n'"→ 없는 채로 통과시키면 모든 의존이 '컨텍스트 안' 으로 보입니다(빈 문자열 비교)."

z26_scanned=0; z26_esc=0; z26_bad=""
while IFS= read -r z26_pkg; do
  case "$z26_pkg" in projects/*/package.json|projects/*/*/package.json|projects/*/*/*/package.json|projects/*/*/*/*/package.json) : ;; *) continue ;; esac
  z26_scanned=$(( z26_scanned + 1 ))
  z26_proj="${z26_pkg#projects/}"; z26_proj="projects/${z26_proj%%/*}"
  z26_app="$(dirname "$z26_pkg")"
  # "<name>": "link:<path>"  /  "file:<path>"  — 상대경로만 대상이다.
  while IFS= read -r z26_line; do
    z26_name="$(printf '%s' "$z26_line" | sed -n 's/^[[:space:]]*"\([^"]*\)"[[:space:]]*:.*/\1/p')"
    z26_tgt="$(printf '%s' "$z26_line" | sed -n 's/.*"\(link\|file\):\([^"]*\)".*/\2/p')"
    [ -n "$z26_tgt" ] || continue
    case "$z26_tgt" in /*) continue ;; esac
    z26_abs="$(realpath -m "$ROOT/$z26_app/$z26_tgt")"
    z26_projabs="$(realpath -m "$ROOT/$z26_proj")"
    case "$z26_abs" in
      "$z26_projabs"/*) continue ;;    # 컨텍스트 안 — 문제 없음
    esac
    z26_esc=$(( z26_esc + 1 ))
    z26_rel="${z26_abs#$(realpath -m "$ROOT")/}"
    # (1) 이 프로젝트의 compose 가 그 대상을 추가 컨텍스트로 넘기는가.
    z26_ctx=""
    for z26_yml in "$ROOT/$z26_proj"/docker-compose*.yml; do
      [ -f "$z26_yml" ] || continue
      z26_ctx="$(sed -n 's/^[[:space:]]*\([A-Za-z0-9_-]*\):[[:space:]]*\.\{0,2\}[^[:space:]]*'"$(basename "$z26_abs")"'[[:space:]]*$/\1/p' "$z26_yml" | head -1)"
      [ -n "$z26_ctx" ] && break
    done
    if [ -z "$z26_ctx" ]; then
      z26_bad="$z26_bad   $z26_app → $z26_rel (compose 에 additional_contexts 없음)"$'\n'
      continue
    fi
    # (2) 그 앱의 Dockerfile 이 그 이름을 실제로 받는가. 이름만 선언하고 안 받으면
    #     컨텍스트는 전달되지만 이미지 안에는 안 들어간다 — 증상이 똑같다.
    z26_df=""
    for z26_c in "$ROOT/$z26_app/Dockerfile" "$ROOT/$z26_proj/Dockerfile"; do
      [ -f "$z26_c" ] && { z26_df="$z26_c"; break; }
    done
    if [ -z "$z26_df" ]; then
      z26_bad="$z26_bad   $z26_app → $z26_rel (Dockerfile 을 못 찾음 — 술어가 형태를 놓쳤습니다)"$'\n'
    elif ! grep -E "COPY[[:space:]]+--from=$z26_ctx([[:space:]]|\$)" "$z26_df" >/dev/null; then
      z26_bad="$z26_bad   $z26_app → $z26_rel (compose 는 '$z26_ctx' 를 넘기는데 Dockerfile 이 COPY --from=$z26_ctx 로 안 받음)"$'\n'
    fi
  done < <(grep -E '"(link|file):' "$ROOT/$z26_pkg" || true)
done < <(cd "$ROOT" && find projects \
           -name node_modules -prune -o -name .next -prune -o \
           -name build -prune -o -name dist -prune -o \
           -name package.json -print 2>/dev/null)

# 🔴 모집단이 0이면 술어가 형태를 놓친 것이다. 「탈출 의존 0건」을 통과로 읽지 않는다 —
#    하한은 **탈출 의존 수**가 아니라 **스캔한 파일 수**에 건다. 탈출 의존은 legitimately
#    0이 될 수 있지만(해석기를 프로젝트 안으로 옮기면), 스캔이 0이면 계측기가 고장난 것이다.
#
# 🔴 이 하한이 실제로 물었다 (2026-09-03). 첫 판은 열거를 `git ls-files` 로 했는데,
#    데모 호스트에서 이 스크립트는 **root** 로 도는 반면 저장소는 ubuntu 소유라
#    git 이 *"detected dubious ownership"* 로 죽어 **0줄**을 냈다. 그 0을 술어가
#    「탈출 의존 없음」으로 읽었다면 이 칸은 **고장난 채 영원히 초록**이었을 것이다.
#    🔵 그래서 열거를 git 에서 떼어 냈다 — 소유권에도, **스테이지 여부에도** 안 걸린다
#    (`git ls-files` 는 추적된 파일만 보므로 새 package.json 이 스테이지 전이면 안 보인다).
[ "$z26_scanned" -ge 5 ] \
  || fail "(z26) package.json 을 ${z26_scanned}개밖에 못 찾았습니다 — 열거가 깨졌습니다."\
  $'\n'"→ 0건을 '탈출 의존 없음' 으로 보고하지 않습니다. 계측기부터 보세요."\
  $'\n'"→ find 가 projects/ 아래를 볼 수 있는지, 경로 깊이 패턴이 여전히 맞는지 확인하세요."

[ -z "$z26_bad" ] || fail "(z26) 컨텍스트 밖 워크스페이스 의존이 이미지 빌드에 전달되지 않습니다:"$'\n'"$z26_bad"\
  $'\n'"→ 증상은 install 이 아니라 **빌드**에서 납니다: pnpm install 은 통과하고(댕글링 심링크)"\
  $'\n'"  'Module not found: Can't resolve <pkg>' 로 죽습니다."\
  $'\n'"→ compose 에 additional_contexts 로 넘기고 Dockerfile 에서 COPY --from=<이름> 으로 받으세요."\
  $'\n'"→ 이 결함은 CI 가 못 잡습니다(러너 빌드는 저장소 루트에서 링크가 해소됩니다). 발견 자리는"\
  $'\n'"  **AMI 굽기 하나뿐**이고 그건 1시간짜리 피드백 루프입니다 — 그래서 여기서 정적으로 막습니다."

ok "(z26) 컨텍스트 밖 워크스페이스 의존 ${z26_esc}건이 전부 추가 컨텍스트로 전달된다 — package.json ${z26_scanned}개 스캔"

# ---------------------------------------------------------------------------
echo "[verify] (z25) 파이프 뒤의 grep 이 조기 종료해 앞단을 SIGPIPE 로 죽이지 않는가 (TASK-MONO-615)"
# ---------------------------------------------------------------------------
# 이 칸은 **자기 자신을 검사한다.** 근거와 실측은 파일 상단 grepq() 주석에 있다.
# 요약: `set -o pipefail` 아래에서 `A | grep -q PAT` 는 매치했는데 141 로 실패할 수 있고,
# 그 실패는 «술어가 거짓» 과 구별되지 않는다 — 가드가 없는 죄를 고발한다.
#
# 🔴 술어를 「(k) 가 통과하는가」로 쓰지 않는다. 그건 이 결함의 **한 증상**일 뿐이고,
#    다음번엔 다른 칸에서 난다. 술어는 **모양**을 문다.
z25_self="$ROOT/infra/demo/verify-demo-wrapper.sh"

# (1) 모양 금지 — 파이프 뒤에 `grep -q…` 가 있으면 FAIL.
#     🔴 자기 문서에 걸리지 않게 주석을 먼저 걷어낸다. (z12)가 정확히 그 함정을 밟았고,
#        이 파일의 상단 주석은 설명을 위해 그 모양을 **일부러** 적고 있다.
z25_hits="$(sed 's/#.*//' "$z25_self" | grep -nE '\|[[:space:]]*grep[[:space:]]+-[A-Za-z]*q' || true)"
[ -z "$z25_hits" ] || fail "(z25) 파이프 뒤에 'grep -q' 가 있습니다:"$'\n'"$z25_hits"\
  $'\n'"→ pipefail 아래에서 앞단이 SIGPIPE(141)로 죽어 **매치했는데 FAIL** 이 됩니다."\
  $'\n'"  실측: printf 25KB 앞쪽 매치 → 300회 중 295회 오검출."\
  $'\n'"→ grepq 를 쓰세요(파일 상단). 종료코드 의미는 같고 출력만 버립니다."

# (2) 🔴 헬퍼가 실재하고 **실제로 그 성질을 갖는가**. (1)만 있으면 grepq 의 본문이
#     `grep -q "$@" >/dev/null` 로 바뀌어도 모양 검사는 통과한다 — 이름만 남는다.
declare -F grepq >/dev/null \
  || fail "(z25) grepq 헬퍼가 정의돼 있지 않습니다 — (1)의 처방이 가리키는 것이 없습니다."
z25_body="$(declare -f grepq)"
printf '%s\n' "$z25_body" | grep -E '(^|[^-])-[A-Za-z]*q' >/dev/null \
  && fail "(z25) grepq 본문이 여전히 -q 를 씁니다: $z25_body"\
  $'\n'"→ 이름만 바뀌고 결함은 그대로입니다."

# (3) 🔴🔴 행동 bite — 술어가 아니라 **성질**을 잰다. 대역을 만들어 두 모양을 같은
#     자리에서 돌린다: 앞단이 크고 매치가 앞쪽이면 `grep -q` 는 실제로 죽어야 하고
#     `grepq` 는 죽지 않아야 한다. 🔵 양성 대조군이 없으면 이 칸은 "환경이 관대해서"
#     초록일 수 있고, 그러면 (1)은 근거 없는 금지가 된다.
z25_big="$(head -c 200000 /dev/zero | tr '\0' 'x' | fold -w 100)"   # 200KB, 2000줄
z25_big="MATCHME"$'\n'"$z25_big"
# 🔴🔴 양성 대조군은 **금지된 모양 그 자체**여야 한다 — 그래서 (1)이 자기 대조군을
#    물었다(실측: 첫 실행에서 이 줄을 고발했다). 문자열로 박으면 대조군을 죽여야 하고,
#    대조군을 죽이면 (1)의 금지는 근거를 잃는다. 그래서 **플래그를 변수로 만든다**:
#    실행되는 것은 진짜 `grep -q` 이고, 파일에는 그 리터럴이 없다.
# 🔴 이것이 (1)에 구멍을 하나 낸다는 것을 적어 둔다 — 누군가 `grep "$f"` 처럼 쓰면
#    (1)은 못 문다. 그 회피는 우연히 일어나지 않고, 여기서만 의도적으로 쓴다.
z25_qflag="-q"
z25_qfail=0; z25_gfail=0
for z25_i in 1 2 3 4 5 6 7 8 9 10; do
  ( set -euo pipefail; printf '%s\n' "$z25_big" | grep "$z25_qflag" 'MATCHME' ) || z25_qfail=$(( z25_qfail + 1 ))
  ( set -euo pipefail; printf '%s\n' "$z25_big" | grepq 'MATCHME' )             || z25_gfail=$(( z25_gfail + 1 ))
done
[ "$z25_qfail" -gt 0 ] \
  || fail "(z25) 양성 대조군이 성립하지 않습니다 — 200KB 입력·앞쪽 매치인데 'grep -q' 가 10/10 통과했습니다."\
  $'\n'"→ 이 환경에서는 결함이 재현되지 않는다는 뜻이고, 그러면 (1)의 금지는 여기서 증명되지 않습니다."\
  $'\n'"  (금지를 지우지는 마세요 — 데모 호스트에서는 실제로 재현됐습니다. 이 칸의 대역을 키우세요.)"
[ "$z25_gfail" -eq 0 ] \
  || fail "(z25) grepq 가 같은 입력에서 ${z25_gfail}/10 실패했습니다 — 처방이 결함을 안 고칩니다."
ok "(z25) 파이프 뒤 'grep -q' 0건 · 대역 200KB 에서 grep -q ${z25_qfail}/10 실패 ↔ grepq 0/10"

# ---------------------------------------------------------------------------
echo "[verify] (z24) 부팅 리셋이 **부팅에서만** 도는가 (TASK-MONO-615 B4 후보 ⓐ)"
# ---------------------------------------------------------------------------
# 무엇을 지키나. `demo-boot.sh` 는 up 앞에 `demo-down.sh` 를 돌려 dockerd 가 되살린
# 잔존 컨테이너를 치운다(2026-09-03 실측: 그것 하나로 부팅 2/2 실패 → 2/2 성공).
# 🔴 그런데 이 스크립트에는 **부팅 말고 다른 호출자**가 있다. 컨트롤 플레인이 방문자의
#    "이 도메인 켜기" 를 `demo-boot.sh <name>` 으로 보내고(handler.py domain_start), 그
#    화이트리스트에는 `full`·`demo-core` 도 들어 있다. 거기서 전체 down 이 돌면 방문자가
#    보고 있는 데모를 통째로 내렸다 올린다 — **고침이 아니라 사고**다.
#
# 그래서 계약이 두 파일에 걸친다(유닛이 플래그를 준다 · 스크립트가 그때만 내린다).
# 🔴 두 곳에 나뉜 계약은 한쪽만 바뀐다 — 이 저장소가 MONO-366 에서 이미 당한 모양이라
#    (유닛이 demo-up.sh 를 직접 불러 DEMO_DOMAIN 계약을 몰랐다) **쌍으로** 묶는다.
#
# 🔵 그리고 문자열 3칸으로 끝내지 않는다. grep 술어는 자기 문서에 걸리고(이 저장소가
#    (z12)에서 밟았다), "읽는다" 와 "그때만 내린다" 는 다른 명제다. (4)(5)(6)이 실제로
#    돌려 본다.
z24_unit="$ROOT/infra/demo/demo-stack.service"
z24_boot="$ROOT/infra/demo/demo-boot.sh"
z24_handler="$ROOT/infra/demo/aws/terraform/lambda/handler.py"

# (1) 유닛이 플래그를 준다.
grep -qE '^Environment=DEMO_BOOT_RESET=1[[:space:]]*$' "$z24_unit" \
  || fail "(z24) demo-stack.service 에 'Environment=DEMO_BOOT_RESET=1' 이 없습니다."\
  $'\n'"→ 그러면 부팅에서도 잔존 정리가 돌지 않고, B4 경합이 그대로 돌아옵니다"\
  $'\n'"  (iam 의존 healthcheck 가 부하로 죽어 auth/gateway 가 'Created' 로 남습니다)."

# (2) ExecStart 가 여전히 demo-boot.sh 다 — 플래그를 줘도 유닛이 다른 것을 부르면 무의미하다.
grep -qE '^ExecStart=.*demo-boot\.sh' "$z24_unit" \
  || fail "(z24) demo-stack.service 의 ExecStart 가 demo-boot.sh 가 아닙니다 — (1)의 플래그가 아무 데도 도달하지 않습니다."

# (3) 🔴 컨트롤 플레인의 per-domain 경로는 이 플래그를 **주면 안 된다**.
#     이 칸이 없으면 «편의상» handler 에 플래그를 넣는 변경이 조용히 통과하고, 그 순간
#     방문자의 「도메인 켜기」가 전체 재기동이 된다.
if [ -f "$z24_handler" ]; then
  grep -q 'DEMO_BOOT_RESET' "$z24_handler" \
    && fail "(z24) 컨트롤 플레인(handler.py)이 DEMO_BOOT_RESET 을 언급합니다."\
    $'\n'"→ per-domain 기동(\`demo-boot.sh <name>\`)에서 이 플래그가 켜지면 방문자가 보고 있는"\
    $'\n'"  데모를 통째로 내렸다 올립니다. 이 플래그의 유일한 출처는 systemd 유닛입니다."
fi

# --- 행동 bite ---------------------------------------------------------------
# 🔴 주입부터 단언한다. 스텁이 안 깔렸는데 "안 돌았다" 를 읽으면 이 칸은 언제나 초록이다.
z24_tmp="$(mktemp -d)"
z24_die() { rm -rf "$z24_tmp"; fail "$@"; }
mkdir -p "$z24_tmp/infra/demo"
cp "$z24_boot" "$z24_tmp/infra/demo/demo-boot.sh"
cat > "$z24_tmp/infra/demo/provision-demo-env.sh" <<'Z24STUB'
#!/usr/bin/env bash
exit 0
Z24STUB
cat > "$z24_tmp/infra/demo/demo-down.sh" <<'Z24STUB'
#!/usr/bin/env bash
echo "DOWN-RAN" >> "$Z24_MARK"
[ "${Z24_HANG:-0}" = "1" ] && sleep 30
exit 0
Z24STUB
cat > "$z24_tmp/infra/demo/demo-up.sh" <<'Z24STUB'
#!/usr/bin/env bash
echo "UP-RAN:$*" >> "$Z24_MARK"
exit 0
Z24STUB
chmod +x "$z24_tmp/infra/demo/"*.sh
for z24_f in provision-demo-env.sh demo-down.sh demo-up.sh; do
  [ -x "$z24_tmp/infra/demo/$z24_f" ] \
    || z24_die "(z24) 주입 확인 실패 — 스텁 $z24_f 가 실행 가능하지 않습니다. 아래 판정은 전부 무효입니다."
done

z24_run() {  # $1=플래그(0|1) $2..=인자 → 마커 파일 내용을 echo
  : > "$z24_tmp/mark"
  ( export Z24_MARK="$z24_tmp/mark" Z24_HANG="${Z24_HANG:-0}" DEMO_DOMAIN=z24.invalid
    if [ "$1" = "1" ]; then export DEMO_BOOT_RESET=1; else unset DEMO_BOOT_RESET; fi
    shift
    bash "$z24_tmp/infra/demo/demo-boot.sh" "$@" ) > "$z24_tmp/out" 2>&1 || true
  cat "$z24_tmp/mark"
}

# (4) 플래그가 있으면 down 이 up **앞에** 돈다.
z24_on="$(z24_run 1 full)"
printf '%s\n' "$z24_on" | grepq '^DOWN-RAN$' \
  || z24_die "(z24) DEMO_BOOT_RESET=1 인데 잔존 정리가 **돌지 않았습니다.**"\
  $'\n'"→ 그러면 부팅 경합(B4)이 그대로입니다. demo-boot.sh 의 게이트를 보세요."
printf '%s\n' "$z24_on" | grepq '^UP-RAN:full$' \
  || z24_die "(z24) 잔존 정리 뒤 demo-up.sh 가 원래 인자로 불리지 않았습니다: [$z24_on]"
[ "$(printf '%s\n' "$z24_on" | head -1)" = "DOWN-RAN" ] \
  || z24_die "(z24) 잔존 정리가 up **뒤에** 돌았습니다 — 순서가 뒤집히면 방금 올린 스택을 내립니다: [$z24_on]"

# (5) 🔴🔴 핵심 — 플래그가 없으면 **절대** 안 돈다(컨트롤 플레인의 per-domain 경로).
z24_off="$(z24_run 0 fan)"
printf '%s\n' "$z24_off" | grepq '^DOWN-RAN$' \
  && z24_die "(z24) DEMO_BOOT_RESET 이 없는데 잔존 정리가 돌았습니다."\
  $'\n'"→ 방문자가 「fan 켜기」를 누르면 **떠 있는 데모 전체가 내려갑니다.** 지금보다 나쁩니다."
printf '%s\n' "$z24_off" | grepq '^UP-RAN:fan$' \
  || z24_die "(z24) 플래그 없는 호출에서 demo-up.sh 가 안 불렸습니다 — 대조군이 성립하지 않습니다: [$z24_off]"

# (6) 🔴 down 도 매달릴 수 있다. 묶는지 본다 — up 을 묶어 놓고 down 을 안 묶으면
#     같은 결함이 한 칸 앞으로 옮겨간 것뿐이다.
if command -v timeout >/dev/null 2>&1; then
  z24_t0="$(date +%s)"
  Z24_HANG=1 DEMO_DOWN_BUDGET=2 z24_run 1 full >/dev/null
  z24_elapsed=$(( $(date +%s) - z24_t0 ))
  [ "$z24_elapsed" -lt 20 ] \
    || z24_die "(z24) 매달린 잔존 정리를 **끊지 못했습니다** — 대역 sleep 30s 인데 ${z24_elapsed}s 걸렸습니다."\
    $'\n'"→ 그러면 ⓐ 가 경합을 고치면서 새 매달림 자리를 하나 만든 것이 됩니다."
  grep -q '끊었습니다' "$z24_tmp/out" \
    || z24_die "(z24) 잔존 정리를 끊었는데 **그렇게 말하지 않습니다** — 로그에 '끊었습니다' 가 없습니다."
  z24_hangnote=" · 매달린 정리 ${z24_elapsed}s 만에 끊고 말한다"
else
  z24_hangnote=" · (timeout 없음 — 매달림 칸 skip)"
fi

rm -rf "$z24_tmp"
ok "(z24) 잔존 정리는 부팅에서만 돈다 — 플래그 有: down→up 순서 · 플래그 無(per-domain): down 0회${z24_hangnote}"


echo "[verify] (z29) 미집행 축 안내가 «현재 상태» 를 단정하지 않고, 지목한 원장이 실재하는가 (TASK-MONO-622)"
# ---------------------------------------------------------------------------
# 이 칸은 **주석 하나를 게이트로 바꾼 것**이다.
#
# 칸 (x)(ecommerce 결제축)의 주석이 규칙을 적어 뒀다:
#     "🔴 날짜 박힌 실측값을 여기에 넣지 않는다. 소유자가 값을 넣는 순간 거짓이 되는데
#      이 스크립트에는 그것을 빨갛게 만들 수단이 없다. 실측값은 원장과 티켓이 든다."
#
# 🔴🔴 그리고 그 규칙은 **주석만으로는 안 지켜졌다.** TASK-MONO-618 은 그 주석을 (x2) 로
#    복사해 놓고 **여섯 줄 아래 메시지에서 어겼다** — "2026-09-04 실측 기준 이 값은
#    kanggle-fan 에 없었다" 가 소유자가 값을 넣은 **바로 그날** 거짓이 됐고, 아무것도
#    그것을 빨갛게 만들지 못했다. 규칙이 없어서가 아니었다. 주석은 게이트가 아니다.
#
# 🔴 그리고 같은 블록이 **원장을 지목만 하고 채우지는 않았다** — (x2) 가 가리킨
#    fan-platform-web/VERCEL.md 에 그 키가 0건이었다(형제 (x) 의 원장에는 각각 5건).
#    "실측값은 원장이 든다" 는 처방이 **원장이 비어 있으면 아무것도 아니다.**
#
# 무엇을 보는가 — 미집행 안내 블록마다 셋:
#   (1) 날짜 박힌 값이 **없다**              — 현재형 단정은 소유자 조작 한 번에 거짓이 된다
#   (2) 원장을 **지목한다**                  — 안 지목하면 "실측값은 원장이 든다" 가 공허하다
#   (3) 그 원장이 실재하고 **그 키를 담는다** — 지목만 하고 비어 있으면 지시가 막다른 길이다
#
# 🔴 모집단을 **성질로** 고른다: `ok`/`warn` 으로 시작하고 "판정할 수 없다" 를 담은 메시지
#    블록. 파일·줄 위치로 고르면 단계 3(console)이 세 번째 축을 추가할 때 그 성질이 사라진다.
#    🔴🔴 그리고 **`fail` 은 뺀다** — 미집행 안내는 **정의상 실패하지 않는다**(실패할 수
#    있으면 그 축은 이미 집행되는 것이다). 첫 판은 마커 문자열만 봤고,
#    `check-cross-project-topic-relay.sh` 의 `fail "… 정적으로 판정할 수 없다"` 를
#    **부분문자열로** 물었다. 마커를 길게 하는 것은 처방이 아니다 — 다음 문구에서 또 걸린다.
#
# 🔴🔴 **하한은 «발견 수» 가 아니라 «추출기» 에 건다.** 미집행 축은 **줄어드는 것이 목표**다
#    (축이 집행 가능해지면 안내가 사라진다) — 발견 수에 하한을 두면 개선이 실패가 된다.
#    대신 "메시지 블록이 하나도 안 뽑히면" 을 FATAL 로 잡는다. 그것은 모집단이 빈 것이
#    아니라 **추출기가 깨진 것**이고, 둘은 0행으로 구별되지 않는다.
#
# 🔴🔴 **왜 awk 한 번인가 — 첫 판은 fork 고갈로 죽었다.** 블록마다 `printf | grep` 서브셸을
#    띄우니 68파일 × 149블록에서 프로세스가 1,500개를 넘었고, msys 에서
#    `dofork: ... exit code 0xC0000142 / Resource temporarily unavailable` 로 중단됐다.
#    이 파일이 이미 적어 둔 원칙 그대로다: **도는 데 오래 걸리는 가드는 언젠가 꺼지고,
#    꺼진 가드는 초록을 보고한다**(MONO-360). 그래서 추출·판별을 awk **한 번**에 넣고,
#    셸은 발견된 안내(오늘 2건)에 대해서만 파일을 읽는다.
#
# 이 칸이 **안 보는 것**: 값 자체(Vercel env). 저장소 밖이고 TASK-MONO-612·618 이 판정
# 불가를 근거를 들어 수용했다. 이 칸은 "재라" 가 아니라 "거짓을 말하지 마라" 다.

# 한 번의 awk 로 전부 뽑는다. 출력:
#   N<TAB>파일<TAB>날짜샘플(없으면 -)<TAB>원장경로(없으면 -)<TAB>키(없으면 -)
#   ALL<TAB>추출된 메시지 블록 총수
# 🔴 주석 줄은 블록에 절대 안 들어간다 — 판별자가 자기 설명 문구에 걸리는 것을 막는다
#    (TASK-MONO-604 가 밟은 함정).
z29_awk='
function flush(   i, n, lines, ld, dt, ky, ok1) {
  if (!inb) { blk=""; return }
  all++
  if (kind == "ok" || kind == "warn") {
    if (index(blk, "판정할 수 없다") > 0) {
      dt = "-"
      if (match(blk, /20[0-9][0-9]-[0-9][0-9]-[0-9][0-9]/))
        dt = substr(blk, RSTART, RLENGTH)
      ld = "-"
      n = split(blk, lines, "\n")
      for (i = 1; i <= n; i++)
        if (index(lines[i], "원장") > 0 && match(lines[i], /[A-Za-z0-9._\/-]+\.md/)) {
          ld = substr(lines[i], RSTART, RLENGTH); break
        }
      ky = "-"
      if (match(blk, /DEMO_[A-Z0-9_]+/)) ky = substr(blk, RSTART, RLENGTH)
      printf "N\t%s\t%s\t%s\t%s\n", bfile, dt, ld, ky
    }
  }
  inb = 0; blk = ""
}
BEGIN { q = sprintf("%c", 39) }
{ t = $0; sub(/^[ \t]+/, "", t) }
t ~ /^#/                        { flush(); next }
t ~ /^(ok|warn|fail)[ \t]+"/    { flush(); match(t, /^[a-z]+/); kind = substr(t, 1, RLENGTH);
                                  inb = 1; bfile = FILENAME; blk = $0; next }
inb && substr(t, 1, 2) == ("$" q) { blk = blk "\n" $0; next }
                                { flush() }
END { flush(); printf "ALL\t%d\n", all }
'

# z29_report <출력파일> <파일...>  — 판별 결과를 <종류>\t<상세> 로 쓴다. rc 는 항상 0.
# 부수효과: z29_all(추출 블록 수) · z29_notice(미집행 안내 수)
z29_all=0
z29_notice=0
z29_report() {
  local out="$1"; shift
  local rec tag f dt ld ky
  : > "$out"
  z29_all=0; z29_notice=0
  rec="$(awk "$z29_awk" "$@")"
  while IFS=$'\t' read -r tag f dt ld ky; do
    case "$tag" in
      ALL) z29_all="$f" ;;
      N)
        z29_notice=$(( z29_notice + 1 ))
        [ "$dt" = "-" ] || printf 'date\t%s: 현재 상태를 %s 와 함께 단정했습니다\n' "$f" "$dt" >> "$out"
        if [ "$ld" = "-" ]; then
          printf 'no-ledger\t%s: 미집행 안내가 원장을 지목하지 않습니다\n' "$f" >> "$out"
        elif [ ! -f "$ROOT/$ld" ]; then
          printf 'ledger-missing\t%s: 지목한 원장이 없습니다 -> %s\n' "$f" "$ld" >> "$out"
        elif [ "$ky" != "-" ] && ! grepq -F "$ky" "$ROOT/$ld"; then
          printf 'ledger-key\t%s: 원장 %s 에 %s 가 0건입니다\n' "$f" "$ld" "$ky" >> "$out"
        fi
        ;;
    esac
  done <<< "$rec"
}

# --- 실모집단 ---------------------------------------------------------------
z29_files=()
while IFS= read -r z29_f; do z29_files+=("$z29_f"); done < <(
  find "$ROOT/infra/demo" "$ROOT/scripts" -type f -name '*.sh' 2>/dev/null | sort
)
[ "${#z29_files[@]}" -ge 10 ] || fail \
  "(z29) 스캔 대상 .sh 가 ${#z29_files[@]}개뿐입니다 — 파일 수집이 깨졌습니다."\
  $'\n'"→ 0에 가까운 모집단은 «위반 없음» 과 구별되지 않습니다."

z29_tmp="$(mktemp -d)"
z29_report "$z29_tmp/hits" "${z29_files[@]}"
z29_real_all="$z29_all"; z29_real_notice="$z29_notice"

# 🔴 추출기 건전성 — 여기에만 하한을 건다. 발견 수(=미집행 축 수)에는 걸지 않는다.
[ "$z29_real_all" -ge 20 ] || { rm -rf "$z29_tmp"; fail \
  "(z29) 메시지 블록이 ${z29_real_all}개만 추출됐습니다 — 추출기가 깨졌을 가능성이 높습니다."\
  $'\n'"→ 이 저장소의 가드들은 ok/fail 메시지를 수백 개 냅니다. 0에 가까운 값은 «위반 없음»"\
  $'\n'"  이 아니라 «아무것도 안 봤다» 입니다 — 둘은 0행으로 구별되지 않습니다."\
  $'\n'"→ 🔴 미집행 축 자체가 0건인 것은 **정상**입니다(축이 집행 가능해지면 사라집니다)."\
  $'\n'"  그래서 하한은 발견 수가 아니라 추출기에 걸려 있습니다."; }

if [ -s "$z29_tmp/hits" ]; then
  z29_n=$(grep -c . "$z29_tmp/hits" || true)
  z29_body="$(sed 's/^/     /' "$z29_tmp/hits")"
  rm -rf "$z29_tmp"
  fail "(z29) 미집행 축 안내에 ${z29_n}건의 위반이 있습니다:"\
    $'\n'"$z29_body"\
    $'\n'"→ date          : 현재 상태를 날짜와 함께 단정했습니다. 소유자가 값을 바꾸면 그 문장은"\
    $'\n'"                  거짓이 되는데 이 스크립트에는 그것을 빨갛게 만들 수단이 없습니다."\
    $'\n'"                  계약과 확인 방법만 남기고, 실측값은 원장과 티켓이 들게 하세요."\
    $'\n'"→ no-ledger     : 미집행 안내는 실측값을 둘 자리를 지목해야 합니다."\
    $'\n'"→ ledger-missing: 지목한 원장 파일이 없습니다."\
    $'\n'"→ ledger-key    : 원장을 지목만 하고 그 키를 안 담았습니다 — 지시가 막다른 길입니다."
fi

# --- self-test: 픽스처가 아니라 **실블록을 복사해 변형**한다 -----------------
# 🔴 손으로 지어낸 스텁은 실물보다 관대해서 초록이 공허해진다. 실제 (x2) 블록을 떠와서
#    한 군데씩 망가뜨린다. 그리고 **주입이 실제로 됐는지를 먼저 단언**한다 — 주입이
#    안 된 채 «안 물었다» 를 읽으면 술어가 무죄인데 유죄로 보인다.
z29_self="$ROOT/infra/demo/verify-demo-wrapper.sh"
awk "$z29_awk" "$z29_self" > "$z29_tmp/self.rec" 2>/dev/null || true
z29_selfline="$(grep -m1 '^N' "$z29_tmp/self.rec" || true)"
[ -n "$z29_selfline" ] || { rm -rf "$z29_tmp"; fail \
  "(z29) self-test 의 대조군 블록을 못 떠왔습니다 — 추출기나 마커가 바뀌었습니다."\
  $'\n'"→ 이 저장소에 미집행 안내가 정말 0건이 됐다면 이 self-test 는 의미를 잃습니다."\
  $'\n'"  그때는 이 칸을 지우는 것이 아니라 **왜 0건이 됐는지**를 헤더에 적으세요."; }

# 대조군 픽스처 = 실제 (x2) 블록을 줄 범위로 떠온 것
z29_s0=$(grep -n '팬 결제 mock — 백엔드만 검사' "$z29_self" | head -1 | cut -d: -f1)
z29_s1=$(awk -v s="$z29_s0" 'NR>=s && /^fi$/ {print NR; exit}' "$z29_self")
[ -n "$z29_s0" ] && [ -n "$z29_s1" ] && [ "$z29_s1" -gt "$z29_s0" ] || { rm -rf "$z29_tmp"; fail \
  "(z29) self-test 대조군 블록의 줄 범위를 못 잡았습니다 (s0=$z29_s0 s1=$z29_s1)."; }
sed -n "${z29_s0},$((z29_s1-1))p" "$z29_self" > "$z29_tmp/a.sh"
grepq -F '판정할 수 없다' "$z29_tmp/a.sh" || { rm -rf "$z29_tmp"; fail \
  "(z29) self-test 대조군에 마커가 없습니다 — 범위 추출이 어긋났습니다."; }

z29_case() {   # $1=이름  $2=픽스처  $3=기대 종류(빈 문자열이면 «위반 0»)  $4=기대 안내 수
  local name="$1" fx="$2" want="$3" wantn="$4" n
  z29_report "$z29_tmp/case.out" "$fx"
  [ "$z29_notice" = "$wantn" ] || fail \
    "(z29) self-test [$name] 의 안내 수가 ${z29_notice} 입니다 (기대 ${wantn})."\
    $'\n'"→ 안내로 안 잡히면 아래 단언들은 **실패할 수 없는 단언**이 됩니다."
  n=$(grep -c . "$z29_tmp/case.out" || true)
  if [ -z "$want" ]; then
    [ "$n" = "0" ] || fail "(z29) self-test [$name] 이 위반 ${n}건을 냈습니다 — 0이어야 합니다."\
      $'\n'"$(sed 's/^/     /' "$z29_tmp/case.out")"
  else
    [ "$n" -ge 1 ] || fail "(z29) self-test [$name] 이 **안 물었습니다** — 주입했는데 위반 0건입니다."
    cut -f1 "$z29_tmp/case.out" | grepq -x -F "$want" || fail \
      "(z29) self-test [$name] 이 다른 종류로 물었습니다: 기대=$want 실제=$(cut -f1 "$z29_tmp/case.out" | tr '\n' ',')"
  fi
}

# (a) 대조군 — 실블록 그대로. 🔴 이것이 초록이어야 나머지의 빨강이 «주입 때문» 이다.
z29_case "대조군(실블록 그대로)" "$z29_tmp/a.sh" "" 1

# (b) 날짜 주입
sed 's|판정할 수 없다|판정할 수 없다(2026-01-02 실측 기준 지금은 없다)|' "$z29_tmp/a.sh" > "$z29_tmp/b.sh"
grepq -F '2026-01-02' "$z29_tmp/b.sh" || fail "(z29) self-test [날짜] 주입이 안 됐습니다 — 앵커가 바뀌었습니다."
z29_case "날짜 주입" "$z29_tmp/b.sh" "date" 1

# (c) 원장 경로를 없는 파일로
sed 's|[A-Za-z0-9._/-]*VERCEL\.md|projects/nope-z29/VERCEL.md|' "$z29_tmp/a.sh" > "$z29_tmp/c.sh"
grepq -F 'projects/nope-z29/VERCEL.md' "$z29_tmp/c.sh" || fail "(z29) self-test [없는 원장] 주입이 안 됐습니다."
[ ! -f "$ROOT/projects/nope-z29/VERCEL.md" ] || fail "(z29) self-test [없는 원장] 의 경로가 실재합니다 — 음성 대조군이 성립하지 않습니다."
z29_case "없는 원장" "$z29_tmp/c.sh" "ledger-missing" 1

# (d) 실재하지만 그 키가 없는 원장 — 🔴 «파일이 있다» 와 «키가 있다» 는 다른 명제다
z29_empty="infra/demo/.z29-empty-ledger-fixture.md"
printf '%s\n' '# z29 fixture — 이 파일에는 그 키가 없다' > "$ROOT/$z29_empty"
sed "s|[A-Za-z0-9._/-]*VERCEL\.md|$z29_empty|" "$z29_tmp/a.sh" > "$z29_tmp/d.sh"
grepq -F "$z29_empty" "$z29_tmp/d.sh" || { rm -f "$ROOT/$z29_empty"; fail "(z29) self-test [빈 원장] 주입이 안 됐습니다."; }
z29_case "빈 원장(파일 有·키 無)" "$z29_tmp/d.sh" "ledger-key" 1
rm -f "$ROOT/$z29_empty"

# (e) 🔴 음성 대조군 — `fail` 블록은 마커를 가져도 **안 잡혀야 한다**.
#     첫 판의 거짓 양성이 정확히 이 모양이었다(check-cross-project-topic-relay.sh:147).
#     문구가 아니라 «실패할 수 있는가» 가 축이다.
sed 's|^  ok "|  fail "|' "$z29_tmp/a.sh" > "$z29_tmp/e.sh"
grepq -E '^[[:space:]]*fail[[:space:]]+"' "$z29_tmp/e.sh" || fail "(z29) self-test [fail 블록] 주입이 안 됐습니다."
z29_case "fail 블록은 안내가 아니다" "$z29_tmp/e.sh" "" 0

rm -rf "$z29_tmp"
ok "(z29) 미집행 축 안내 ${z29_real_notice}건: 날짜 단정 0 · 원장 실재+키 보유. 블록 ${z29_real_all}개 추출(파일 ${#z29_files[@]}, awk 1회) · self-test 5칸(대조군·날짜·없는 원장·빈 원장·fail 제외)"


echo "[verify] (z30) 호스트 드리프트 술어가 mode 변경과 내용 변경을 구별하는가 (TASK-MONO-615 C3)"
# ---------------------------------------------------------------------------
# `TASK-MONO-615` AC-3 의 C3 판정은 *"호스트 로컬 수정이 **0건**"* 이고, 기동 창 #2 가
# 그것을 `git status --short` 로 쟀다. 1줄이 나왔다 — `demo-boot.sh` 의 **실행 비트**다
# (내용 diff 0/0, AMI 굽기가 남기는 자국이며 두 세대에서 재현됐다).
#
# 🔴🔴 즉 **술어가 mode change 를 content change 와 구별하지 못했다.** 그 상태로 두면
#    둘 중 하나가 일어난다: 다음 사람이 그 한 줄을 드리프트로 오독하거나, 술어를 느슨하게
#    고쳐(`git status` 를 지워) **진짜 내용 변경까지 통과**시킨다.
#
# 🔴 그리고 그 술어는 **어느 스크립트에도 없었다** — 티켓 산문에만 있었고, 운영자가
#    손으로 `git status` 를 쳤다. `TASK-MONO-622` 가 방금 랜딩한 교훈 그대로다:
#    **산문(주석)은 게이트가 아니다.** 그래서 `check-host-drift.sh` 로 옮겼다.
#
# 이 칸이 보는 것 — 🔴 판정자를 **다시 구현하지 않는다**. 정적으로 확인 가능한 것만:
#   (1) 파일이 있는가        — 사라지면 C3 판정이 다시 「기억」으로 돌아간다
#   (2) 문법이 성립하는가    — `bash -n`
#   (3) **self-test 가 통과하는가** — 이것이 본체다. 술어가 mode/content 를 실제로
#       구별하는지는 임시 저장소를 만들어서만 알 수 있고, 그 실행이 여기서 일어난다.
#
# 🔵 **누가 부르는가**: 이 칸(CI) + 기동 창 운영자(호스트에서 SSM 으로 수동). 호스트
#    체크아웃은 데모 호스트에만 존재하므로 «자동 실행» 은 원리적으로 불가능하다 —
#    그래서 CI 가 도는 것은 **술어 자신**이고, 값은 창에서 잰다. 둘을 섞지 않는다.
z30_s="$ROOT/infra/demo/check-host-drift.sh"
[ -f "$z30_s" ] || fail "(z30) infra/demo/check-host-drift.sh 가 없습니다 — C3 판정이 다시 손으로 치는 git status 로 돌아갑니다."\
  $'\n'"→ TASK-MONO-615 § C3 이 남긴 술어 결함(mode change 를 content 와 구별 못 함)이 그 자리입니다."

bash -n "$z30_s" 2>/dev/null || fail "(z30) check-host-drift.sh 의 문법이 깨졌습니다 (bash -n 실패)."

z30_out="$(bash "$z30_s" --self-test 2>&1)"; z30_rc=$?
z30_cells=$(printf '%s\n' "$z30_out" | grep -c '✓' || true)
[ "$z30_rc" = "0" ] || fail "(z30) check-host-drift.sh --self-test 가 rc=$z30_rc 로 실패했습니다:"\
  $'\n'"$(printf '%s' "$z30_out" | sed 's/^/     /')"
# 🔴 통과 칸 수에 하한을 둔다 — self-test 가 «아무 칸도 안 돌고» rc=0 이면 그것은
#    「술어가 옳다」가 아니라 「아무것도 안 쟀다」이고, 둘은 rc=0 으로 구별되지 않는다.
[ "$z30_cells" -ge 7 ] || fail \
  "(z30) self-test 가 ${z30_cells}칸만 통과했습니다 — 7칸 이상이어야 합니다."\
  $'\n'"→ rc=0 인데 칸이 줄었다면 술어가 옳아진 것이 아니라 **칸이 사라진** 것입니다."\
  $'\n'"→ 칸을 의도적으로 줄였다면 이 하한도 같은 PR 에서 내리고 왜인지 적으세요."

ok "(z30) 호스트 드리프트 술어 — 존재·문법·self-test ${z30_cells}칸(대조군 · mode만 · 내용 · 내용+mode · 미추적 · gitignore · 저장소 아님)"

if [ "$LIVE" -eq 0 ]; then

  echo "[verify] 정적 검증 PASS (실기동 증명은 --live)"
  exit 0
fi

echo "[verify] (z18) --live: 죽은 sslip OAuth 콜백이 0건인가 (TASK-MONO-606)"
z18_container="${IAM_MYSQL_CONTAINER:-iam-mysql}"
z18_dom="${DEMO_DOMAIN:-local}"
z18_sql="
SELECT
  SUM(CASE WHEN u.uri NOT LIKE '%${z18_dom}%' THEN 1 ELSE 0 END),
  COUNT(*)
FROM (
  SELECT jt.uri FROM oauth_clients c,
    JSON_TABLE(c.redirect_uris, '\$[*]' COLUMNS (uri VARCHAR(512) PATH '\$')) jt
   WHERE jt.uri LIKE '%sslip.io%'
  UNION ALL
  SELECT jt.uri FROM oauth_clients c,
    JSON_TABLE(JSON_EXTRACT(c.client_settings,
      '\$.\"settings.client.post-logout-redirect-uris\"[1]'),
      '\$[*]' COLUMNS (uri VARCHAR(512) PATH '\$')) jt
   WHERE jt.uri LIKE '%sslip.io%'
) u;"
z18_out="$(docker exec "$z18_container" mysql \
             -u"${AUTH_DB_USERNAME:-auth_user}" -p"${AUTH_DB_PASSWORD:-auth_pass}" \
             "${AUTH_DB_NAME:-auth_db}" -N -B -e "$z18_sql" 2>/dev/null)" && z18_rc=0 || z18_rc=$?
z18_dead="$(printf '%s' "$z18_out" | awk 'NR==1{print $1}')"
z18_total="$(printf '%s' "$z18_out" | awk 'NR==1{print $2}')"
# 모집단이 0이면 SUM() 은 NULL 이고 mysql 은 그것을 'NULL' 로 찍는다 — 숫자가 아니다.
# judge 가 NOCOVER 로 잡긴 하지만 사유가 «못 읽었다» 로 잘못 붙으므로 여기서 정규화한다.
[ "${z18_dead:-}" = "NULL" ] && [ "${z18_total:-}" = "0" ] && z18_dead=0
z18_verdict="$(judge_stale_sslip "$z18_rc" "${z18_dead:-}" "${z18_total:-}")"
case "$z18_verdict" in
  FAIL:*)
    fail "(z18) ${z18_verdict#FAIL:} — DEMO_DOMAIN=$z18_dom"\
      $'\n'"→ 그 IP 는 더 이상 우리 것이 아니고, 등록된 redirect_uri 는 IdP 의 exact-match 를 통과합니다."\
      $'\n'"→ infra/demo/seed-demo-domain.sh 의 회수 절이 돌았는지 확인하세요(덧붙이기와 같은 트랜잭션)." ;;
  NOCOVER:*)
    if [ "$REQUIRE_COVERAGE" -eq 1 ]; then
      fail "(z18) 판정 못 함: ${z18_verdict#NOCOVER:} — --require-coverage 이므로 FAIL 입니다."
    fi
    echo "  skip: (z18) 판정 못 함 — ${z18_verdict#NOCOVER:} (컨테이너=$z18_container · DEMO_DOMAIN=$z18_dom)" ;;
  OK:*)
    ok "(z18) 죽은 sslip 등록 0건 — 판정한 sslip URI ${z18_verdict#OK:}건 · DEMO_DOMAIN=$z18_dom" ;;
esac

echo "[verify] (z21) --live: discovery 가 광고하는 경로가 핀·라우터와 일치하는가 (TASK-MONO-615 B1)"
# ---------------------------------------------------------------------------
# (z20) 은 「라우터 ⊇ 핀」을 잰다. 그러나 핀이 낡으면 (z20) 의 초록은 「덮었다」가 아니라
# **「덜 알고 있다」**이다. 이 칸이 그 축을 잰다 — **라이브 문서에서 다시 파생해서**
# 핀과 대조하고, 라우터가 라이브 경로를 전부 덮는지도 직접 본다.
# 🔴 IdP 에 못 닿으면 **skip 이다 — 판정이 아니다**(--require-coverage 에서는 FAIL).
# ---------------------------------------------------------------------------
z21_dom="${DEMO_DOMAIN:-local}"
z21_pin="$ROOT/infra/demo/idp-advertised-path-prefixes.txt"
z21_nocover=""
if [ "$z21_dom" = "local" ] || [ -z "$z21_dom" ]; then
  z21_nocover="DEMO_DOMAIN='$z21_dom' — 라이브 IdP 호스트명이 없습니다"
elif [ ! -f "$z21_pin" ]; then
  z21_nocover="핀 파일이 없습니다: $z21_pin"
else
  z21_doc="$(curl -fsS --max-time 15 "http://iam.${z21_dom}/.well-known/openid-configuration" 2>/dev/null || true)"
  case "$z21_doc" in
    *'"issuer"'*) : ;;
    "")  z21_nocover="discovery 를 못 받았습니다 (http://iam.${z21_dom}/.well-known/openid-configuration)" ;;
    *)   z21_nocover="받은 본문이 discovery 문서가 아닙니다 (issuer 필드 없음, ${#z21_doc}바이트)" ;;
  esac
fi

if [ -n "$z21_nocover" ]; then
  if [ "$REQUIRE_COVERAGE" -eq 1 ]; then
    fail "(z21) 판정 못 함: $z21_nocover — --require-coverage 이므로 FAIL 입니다."
  fi
  echo "  skip: (z21) 판정 못 함 — $z21_nocover"
else
  # URL 값 필드에서 경로의 **첫 세그먼트**만. issuer 는 경로가 비어 걸러진다.
  z21_live="$(idp_path_prefixes "$z21_doc")"
  z21_nlive="$(printf '%s\n' "$z21_live" | grep -c '^/' || true)"
  [ "${z21_nlive:-0}" -ge 1 ] || fail "(z21) 라이브 문서에서 경로 접두사를 **하나도** 못 뽑았습니다 — 추출기가 깨졌습니다(${#z21_doc}바이트를 받았는데 0건)."

  z21_want="$(grep -vE '^[[:space:]]*(#|$)' "$z21_pin" | sort -u || true)"
  z21_rule="$(grep -m1 'routers\.iam-oidc\.rule=' "$ROOT/infra/demo/iam-traefik.override.yml" || true)"
  z21_have="$(printf '%s' "$z21_rule" | grep -oE 'PathPrefix\(`[^`]+`\)' | sed 's/PathPrefix(`//; s/`)//' | sort -u || true)"

  # ① 라이브에 있는데 라우터가 안 덮는다 — 이용자가 실제로 404 를 만난다
  z21_unrouted="$(comm -23 <(printf '%s\n' "$z21_live") <(printf '%s\n' "$z21_have") | tr '\n' ' ')"
  [ -z "${z21_unrouted// /}" ] || fail "(z21) IdP 가 광고하는데 라우터가 안 덮는 경로: ${z21_unrouted}"\
    $'\n'"→ 이용자는 그 엔드포인트에서 404 를 만납니다. DEMO_DOMAIN=$z21_dom"

  # ② 라이브에 있는데 핀에 없다 — **핀이 낡았다**. (z20) 의 초록이 「덜 알고 있다」였다
  z21_newpfx="$(comm -23 <(printf '%s\n' "$z21_live") <(printf '%s\n' "$z21_want") | tr '\n' ' ')"
  [ -z "${z21_newpfx// /}" ] || fail "(z21) 핀에 없는 경로를 IdP 가 광고합니다: ${z21_newpfx}"\
    $'\n'"→ $z21_pin 이 낡았습니다. 손으로 덧붙이지 말고 그 파일 헤더의 재파생 명령 **출력으로 덮으세요**."

  # ③ 핀에 있는데 라이브에 없다 — 반대 방향의 드리프트(엔드포인트가 사라졌다)
  z21_gone="$(comm -13 <(printf '%s\n' "$z21_live") <(printf '%s\n' "$z21_want") | tr '\n' ' ')"
  [ -z "${z21_gone// /}" ] || fail "(z21) 핀에 있는데 IdP 가 더 이상 광고하지 않는 경로: ${z21_gone}"\
    $'\n'"→ 엔드포인트가 사라졌거나 이 IdP 가 다른 설정으로 떠 있습니다. 어느 쪽인지 확인한 뒤 핀을 다시 받아 적으세요."

  ok "(z21) 라이브 discovery ↔ 핀 ↔ 라우터 일치 — 광고 접두사 ${z21_nlive}건($(printf '%s' "$z21_live" | tr '\n' ' ')) · DEMO_DOMAIN=$z21_dom"
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
