#!/usr/bin/env bash
#
# check-cross-project-topic-relay.sh — TASK-MONO-511 (ADR-MONO-062 B)
#
# 크로스프로젝트 이벤트가 도달하지 않는 상태는 **아무 에러도 내지 않는다.** 그것이 이
# 결함이 오래 살아남은 이유이고, 이 스크립트가 존재하는 이유다.
#
# ---------------------------------------------------------------------------
# 술어 — 🔴 "토픽이 존재한다" 는 쓰지 않는다
#
# ADR-MONO-062 § 결과 3 이 그것을 명시적으로 금지한다. 소비자가 붙기만 해도 브로커가 빈
# 토픽을 auto-create 하므로, 토픽의 존재는 **컨슈머의 산물**이지 배선의 증거가 아니다
# (발굴 티켓이 정확히 그 빈 토픽에 속았다).
#
# 여기서 쓰는 술어는 **집합의 동등**이다:
#
#     {자기 프로젝트가 발행하지 않는 토픽을 구독하는 @KafkaListener 의 (소비 프로젝트, 토픽)}
#          ==
#     {릴레이 화이트리스트가 실어 나르는 (목적지 프로젝트, 토픽)}
#
# 왼쪽이 코드의 사실이고 오른쪽이 배선의 선언이다. 둘이 어긋나는 방향은 둘 다 결함이다:
#   * 리스너에 있는데 화이트리스트에 없다 → 그 리스너는 **영원히 조용하다**(원래 결함).
#   * 화이트리스트에 있는데 리스너가 없다 → 아무도 안 읽는 토픽을 복제한다(경계 확대).
#
# ---------------------------------------------------------------------------
# 추가로 지키는 두 가지
#
#   (b) **순환 안전성** — mm2.properties 는 IdentityReplicationPolicy 를 쓴다(토픽 이름을
#       보존해야 소비자가 읽는다). 접두 정책과 달리 순환을 자동으로 끊지 못하므로,
#       양방향 쌍(ecommerce↔wms)의 화이트리스트가 **서로소**임을 단언한다. 이것이 깨지면
#       메시지가 무한 증식한다 — 주석으로 남기면 다음 사람이 토픽 하나 추가할 때 안 읽는다.
#
#   (c) **override 리스너 드리프트** — `infra/demo/*-relay.override.yml` 은 compose 가
#       스칼라를 병합하지 않고 치환하기 때문에 base 의 `KAFKA_ADVERTISED_LISTENERS` 를
#       통째로 다시 적는다. base 가 바뀌면 override 가 조용히 낡는다 ⇒ base 값이 override
#       값의 **접두**인지 검사한다.
#
# ---------------------------------------------------------------------------
# 이 스크립트가 하지 **않는** 것 (의도 — "고치기" 전에 읽어라)
#
#   * 라이브 도달을 재지 않는다. CI 러너에는 브로커가 없다. 라이브 판정은
#     `infra/demo/relay/probe-relay.sh` 가 하고, 그쪽은 실제 레코드를 넣고 반대편에서
#     꺼내 본다(오프셋 증가가 술어).
#   * 릴레이가 **떠 있는지** 보지 않는다. 그건 배선의 정합이 아니라 런타임 상태다.
#
# Usage: bash scripts/check-cross-project-topic-relay.sh [--self-test]
# Exit:  0 = 정합, 1 = 드리프트, 2 = 실행 불가

set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MM2="${MM2_PROPERTIES:-$ROOT/infra/demo/relay/mm2.properties}"
PROJECTS_DIR="${PROJECTS_DIR:-$ROOT/projects}"
DEMO_DIR="${DEMO_DIR:-$ROOT/infra/demo}"

fail_count=0
fail() { printf 'DRIFT: %s\n' "$*" >&2; fail_count=$((fail_count + 1)); }

[ -r "$MM2" ]        || { echo "FATAL: cannot read $MM2" >&2; exit 2; }
[ -d "$PROJECTS_DIR" ] || { echo "FATAL: cannot read $PROJECTS_DIR" >&2; exit 2; }

# ---------------------------------------------------------------------------
# 토픽 접두 → 소유 프로젝트
#
# 🔴 발행처를 리터럴로 찾을 수 없는 토픽이 있다 — wms 는 토픽명을 **계산**한다
# (`"wms.master." + aggregate + ".v1"`, MasterOutboxPublisher). 리터럴 grep 의 "0건" 은
# 부재가 아니라 계측기의 한계였다. 그래서 소유는 접두 규약으로 정한다(레포 전체가 그
# 규약을 지킨다: <소유 도메인>.<...>).
# ---------------------------------------------------------------------------
owner_of() {
    case "$1" in
        account.*|auth.*)                 echo iam-platform ;;
        wms.*)                            echo wms-platform ;;
        scm.*)                            echo scm-platform ;;
        ecommerce.*|order.*|product.*|payment.*|shipping.*|user.*|promotion.*|settlement.*|search.*)
                                          echo ecommerce-microservices-platform ;;
        fan.*)                            echo fan-platform ;;
        erp.*)                            echo erp-platform ;;
        finance.*)                        echo finance-platform ;;
        *)                                echo "" ;;
    esac
}

# 프로젝트 디렉터리명 → mm2 클러스터 별칭
cluster_of() {
    case "$1" in
        iam-platform)                     echo iam ;;
        ecommerce-microservices-platform) echo ecommerce ;;
        wms-platform)                     echo wms ;;
        scm-platform)                     echo scm ;;
        *)                                echo "" ;;
    esac
}

# ---------------------------------------------------------------------------
# (1) 코드의 사실 — 크로스프로젝트 리스너
# ---------------------------------------------------------------------------
# `@KafkaListener(topics = ...)` 는 리터럴이거나 같은 파일의 상수다(scm 은 전부 상수라,
# 리터럴만 보는 판독기는 scm 을 통째로 놓친다 — 개발 중 실제로 그랬다).
# 🔴 **파일 안의 토픽처럼 생긴 문자열을 전부 세면 안 된다.** 첫 판은 그렇게 했고, 컨슈머가
# 이벤트 *타입* 문자열(`"wms.inventory.adjusted"` — 토픽은 `...adjusted.v1`)까지 토픽으로
# 세어 존재하지 않는 라우트 3건을 만들어 냈다. 반드시 `topics = ...` 인자만 읽어야 한다.
#
# 🔵 그리고 그 인자는 리터럴일 수도 **상수**일 수도 있다 — scm 컨슈머는 전부 `topics = TOPIC`
# 이라, 리터럴만 보는 판독기는 scm 을 통째로 놓친다(개발 중 실제로 그랬다).
listener_topics_of_file() {
    local f="$1" flat args body ident decl
    flat="$(tr '\n' ' ' < "$f")"
    while IFS= read -r args; do
        [ -n "$args" ] || continue
        case "$args" in *topics*) : ;; *) continue ;; esac
        body="${args#*topics}"; body="${body#*=}"
        # `{...}` 배열이면 닫는 중괄호까지, 아니면 다음 쉼표까지가 이 인자의 값이다.
        case "$(echo "$body" | sed 's/^[[:space:]]*//' | cut -c1)" in
            '{') body="${body%%\}*}" ;;
            *)   body="${body%%,*}" ;;
        esac
        # 리터럴
        echo "$body" | grep -oE '"[^"]+"' | tr -d '"' || true
        # 리터럴이 없으면 같은 파일의 상수 선언으로 해소한다
        if ! echo "$body" | grep -q '"'; then
            while IFS= read -r ident; do
                [ -n "$ident" ] || continue
                decl="$(grep -oE "String[[:space:]]+${ident}[[:space:]]*=[[:space:]]*\"[^\"]+\"" "$f" | head -1 \
                        | grep -oE '"[^"]+"' | tr -d '"')" || true
                [ -n "$decl" ] && echo "$decl"
            done < <(echo "$body" | grep -oE '\b[A-Z_][A-Z0-9_]*\b' || true)
        fi
    done < <(echo "$flat" | grep -oE '@KafkaListener[[:space:]]*\([^)]*\)' || true)
}

listener_routes() {
    local f project topic owner ocl dcl
    while IFS= read -r f; do
        grep -q '@KafkaListener' "$f" || continue
        project="${f#"$PROJECTS_DIR"/}"; project="${project%%/*}"
        dcl="$(cluster_of "$project")"
        [ -n "$dcl" ] || continue
        while IFS= read -r topic; do
            [ -n "$topic" ] || continue
            # `${prop:default}` — 스프링 플레이스홀더. wms 컨슈머는 이 형태로 바인딩하므로,
            # 이것을 버리면 wms 로 들어오는 두 계열(ecommerce→wms · scm→wms)이 통째로
            # 안 보인다. 기본값이 그 서비스가 실제로 구독하는 토픽이다.
            case "$topic" in
                '${'*:*'}') topic="${topic#*:}"; topic="${topic%\}}" ;;
                '${'*'}')
                    fail "기본값 없는 플레이스홀더 토픽 [$topic] ($(basename "$f")) — 정적으로 판정할 수 없다."
                    continue ;;
            esac
            case "$topic" in [a-z]*) : ;; *) continue ;; esac
            owner="$(owner_of "$topic")"
            [ -n "$owner" ] || continue
            [ "$owner" = "$project" ] && continue
            ocl="$(cluster_of "$owner")"
            [ -n "$ocl" ] || continue
            printf '%s->%s %s\n' "$ocl" "$dcl" "$topic"
        done < <(listener_topics_of_file "$f" | sort -u)
    # 🔴 후보를 **한 번의 grep 으로** 좁힌다. 첫 판은 `find` 로 전체 .java(≈1500개)를 돌며
    # 파일마다 `grep -q` 를 했고, msys 에서 5분을 넘겨 CI 에 못 쓸 물건이었다. 도는 데
    # 오래 걸리는 가드는 언젠가 꺼진다 — 그리고 꺼진 가드는 초록을 보고한다(MONO-360).
    done < <(grep -rl --include='*.java' '@KafkaListener' "$PROJECTS_DIR" | grep '/src/main/')
}

# ---------------------------------------------------------------------------
# (2) 배선의 선언 — 릴레이 화이트리스트
# ---------------------------------------------------------------------------
whitelist_routes() {
    local line flow topics t
    while IFS= read -r line; do
        flow="${line%%.topics*}"; flow="$(echo "$flow" | tr -d '[:space:]')"
        topics="${line#*=}"
        # 정규식 대안 목록을 토픽으로 되돌린다: `a\.b|c\.d` → 두 줄
        echo "$topics" | tr '|' '\n' | sed -e 's/\\//g' -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//' \
        | while IFS= read -r t; do
            [ -n "$t" ] && printf '%s %s\n' "$flow" "$t"
        done
    done < <(grep -E '^[a-z]+->[a-z]+\.topics[[:space:]]*=' "$MM2")
}

declare -A ENABLED=()
while IFS= read -r line; do
    flow="$(echo "${line%%.enabled*}" | tr -d '[:space:]')"
    val="$(echo "${line#*=}" | tr -d '[:space:]')"
    ENABLED[$flow]="$val"
done < <(grep -E '^[a-z]+->[a-z]+\.enabled[[:space:]]*=' "$MM2")

LISTENERS="$(listener_routes | sort -u)"
WHITELIST="$(whitelist_routes | sort -u)"

# 비공허성 — 판독기가 0건을 내면 "일치" 가 아니라 실패다.
if [ -z "$LISTENERS" ]; then
    echo "FATAL: 크로스프로젝트 리스너를 한 건도 못 찾았다 — 판독기가 고장났거나 경로가 틀렸다." >&2
    echo "       (탐지식의 0건은 부재의 증거가 아니다.)" >&2
    exit 2
fi
if [ -z "$WHITELIST" ]; then
    echo "FATAL: 화이트리스트가 비어 있다 — $MM2 를 못 읽었거나 형식이 바뀌었다." >&2
    exit 2
fi

# ---------------------------------------------------------------------------
# (a) 집합 동등 — 양방향
# ---------------------------------------------------------------------------
while IFS= read -r route; do
    [ -n "$route" ] || continue
    if ! printf '%s\n' "$WHITELIST" | grep -qxF "$route"; then
        fail "listener route [$route] 가 릴레이 화이트리스트에 없다 — 그 리스너는 영원히 조용하다."
    fi
done <<< "$LISTENERS"

while IFS= read -r route; do
    [ -n "$route" ] || continue
    if ! printf '%s\n' "$LISTENERS" | grep -qxF "$route"; then
        fail "whitelist route [$route] 를 읽는 리스너가 없다 — 아무도 안 읽는 토픽을 프로젝트 경계 너머로 복제한다."
    fi
done <<< "$WHITELIST"

# 켜지지 않은 흐름의 화이트리스트는 선언만 있고 실어 나르지 않는다.
while IFS= read -r route; do
    [ -n "$route" ] || continue
    flow="${route%% *}"
    if [ "${ENABLED[$flow]:-false}" != "true" ]; then
        fail "flow [$flow] 이 .enabled=true 가 아닌데 화이트리스트에 [$route] 가 있다 — 선언과 배선이 어긋난다."
    fi
done <<< "$WHITELIST"

# ---------------------------------------------------------------------------
# (b) 순환 안전성 — IdentityReplicationPolicy 는 순환을 스스로 못 끊는다
# ---------------------------------------------------------------------------
while IFS= read -r fwd; do
    [ -n "$fwd" ] || continue
    flow="${fwd%% *}"; topic="${fwd#* }"
    src="${flow%%->*}"; dst="${flow#*->}"
    rev="$dst->$src"
    if printf '%s\n' "$WHITELIST" | grep -qxF "$rev $topic"; then
        fail "cycle: [$topic] 이 $flow 와 $rev 양쪽 화이트리스트에 있다 — 이름을 보존하는 복제라서 무한 증식한다."
    fi
done <<< "$WHITELIST"

# ---------------------------------------------------------------------------
# (c) override 리스너 드리프트 — base 값이 override 값의 접두여야 한다
# ---------------------------------------------------------------------------
check_listener_override() {
    local base_compose="$1" override="$2" key="$3"
    local base_val over_val
    [ -r "$base_compose" ] || { fail "base compose 를 못 읽는다: $base_compose"; return; }
    [ -r "$override" ]     || { fail "relay override 를 못 읽는다: $override"; return; }
    # 🔴 주석을 먼저 지운다. 첫 판은 안 지웠고, override 헤더가 설명하려고 인용해 둔
    # `KAFKA_ADVERTISED_LISTENERS: ...` 를 **값으로 읽어** base 와 똑같은 문자열을 뽑았다 —
    # 그래서 실패 메시지가 "base 와 override 가 어긋난다: 둘 다 같은 값" 이라는 말이 안 되는
    # 모양으로 나왔다. 설명이 값을 가장하는 것이 이 검사에서 가장 쉬운 오작동이다.
    base_val="$(sed -e 's/#.*$//' "$base_compose" | grep -oE "${key}[:=][[:space:]]*[^[:space:]]+" | head -1 | sed -E "s/^${key}[:=][[:space:]]*//")"
    over_val="$(sed -e 's/#.*$//' "$override"     | grep -oE "${key}[:=][[:space:]]*[^[:space:]]+" | head -1 | sed -E "s/^${key}[:=][[:space:]]*//")"
    [ -n "$base_val" ] || { fail "$base_compose 에서 $key 를 못 찾았다 — 판독기가 낡았다."; return; }
    [ -n "$over_val" ] || { fail "$override 에서 $key 를 못 찾았다."; return; }
    case "$over_val" in
        "$base_val",*) : ;;
        *) fail "$(basename "$override") 의 $key 가 base 와 어긋난다. base=[$base_val] override=[$over_val] — override 는 base 값을 그대로 앞에 두고 RELAY 만 덧붙여야 한다." ;;
    esac
}

check_listener_override "$PROJECTS_DIR/iam-platform/docker-compose.yml"                     "$DEMO_DIR/iam-relay.override.yml"       KAFKA_ADVERTISED_LISTENERS
check_listener_override "$PROJECTS_DIR/wms-platform/docker-compose.yml"                     "$DEMO_DIR/wms-relay.override.yml"       KAFKA_ADVERTISED_LISTENERS
check_listener_override "$PROJECTS_DIR/ecommerce-microservices-platform/docker-compose.yml" "$DEMO_DIR/ecommerce-relay.override.yml" KAFKA_ADVERTISED_LISTENERS
check_listener_override "$PROJECTS_DIR/scm-platform/docker-compose.yml"                     "$DEMO_DIR/scm-relay.override.yml"       KAFKA_ADVERTISED_LISTENERS

# ---------------------------------------------------------------------------
if [ "$fail_count" -gt 0 ]; then
    printf '\ncheck-cross-project-topic-relay: FAILED — %d drift finding(s).\n' "$fail_count" >&2
    exit 1
fi

route_n="$(printf '%s\n' "$LISTENERS" | wc -l | tr -d ' ')"
flow_n="$(printf '%s\n' "$WHITELIST" | awk '{print $1}' | sort -u | wc -l | tr -d ' ')"
printf 'check-cross-project-topic-relay: OK — %s cross-project routes across %s relay flows;\n' "$route_n" "$flow_n"
printf '  listeners and whitelist agree in both directions, no name-preserving cycle,\n'
printf '  and every relay override still carries its base advertised listeners.\n'
