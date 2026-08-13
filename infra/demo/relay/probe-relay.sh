#!/usr/bin/env bash
#
# probe-relay.sh — 라이브 도달 판정 (TASK-MONO-511 / ADR-MONO-062 B)
#
# 정적 가드(`scripts/check-cross-project-topic-relay.sh`)는 **선언이 코드와 맞는지**를 본다.
# 이 스크립트는 **실제로 건너가는지**를 본다. 둘은 다른 질문이고, 둘 다 필요하다 —
# 화이트리스트가 완벽해도 릴레이가 죽어 있으면 아무것도 도달하지 않는다.
#
# ## 술어 — 🔴 "토픽이 존재한다" 를 쓰지 않는다
#
# 각 라우트마다 **탐침 레코드를 원본 클러스터에 넣고, 목적지 클러스터에서 그 레코드 수가
# 증가하는지**를 본다. 존재가 아니라 **증분**이 술어다. (컨슈머가 붙기만 해도 브로커가 빈
# 토픽을 만들기 때문에 존재는 아무것도 말하지 않는다 — 이 결함이 오래 숨은 이유다.)
#
# ## 음성 대조가 함께 있다
#
# 양성만 재면 *"복제가 된다"* 와 *"전부 다 복제된다"* 를 구별할 수 없다. 그래서 각 탐침이
# **화이트리스트에 없는 목적지**에도 도착하지 않았는지를 같이 확인한다. 그 칸이 깨지면
# 화이트리스트가 경계의 유일한 출처라는 ADR 의 구속력이 깨진 것이다.
#
# Usage: bash infra/demo/relay/probe-relay.sh
# Exit:  0 = 모든 라우트 도달 + 음성 대조 통과, 1 = 실패, 2 = 실행 불가(스택 미기동)

set -uo pipefail

BROKER_iam=iam-kafka
BROKER_ecommerce=ecommerce-kafka
BROKER_wms=wms-kafka
BROKER_scm=scm-platform-kafka

fails=0
note() { printf '%s\n' "$*"; }
bad()  { printf 'FAIL: %s\n' "$*" >&2; fails=$((fails + 1)); }

broker_of() { eval "printf '%s' \"\${BROKER_$1}\""; }

for c in iam ecommerce wms scm; do
    b="$(broker_of "$c")"
    docker exec "$b" true >/dev/null 2>&1 || {
        echo "FATAL: $b 컨테이너가 없다 — 네 도메인이 모두 떠 있어야 한다." >&2; exit 2; }
done
docker exec demo-event-relay true >/dev/null 2>&1 || {
    echo "FATAL: demo-event-relay 가 없다 — 릴레이가 안 떠 있다." >&2; exit 2; }

# 토픽의 총 레코드 수(모든 파티션 end-offset 합). 토픽이 없으면 0.
count_records() {
    local broker="$1" topic="$2"
    docker exec "$broker" sh -c \
        "/opt/kafka/bin/kafka-get-offsets.sh --bootstrap-server kafka:9092 --topic '$topic' 2>/dev/null" \
        | awk -F: '{ s += $3 } END { print s + 0 }'
}

produce() {
    local broker="$1" topic="$2" payload="$3"
    docker exec -i "$broker" sh -c \
        "/opt/kafka/bin/kafka-console-producer.sh --bootstrap-server kafka:9092 --topic '$topic'" \
        <<< "$payload" >/dev/null 2>&1
}

# probe <source-cluster> <topic> <dest-cluster...> — 마지막 인자 뒤의 `!` 이후는 음성 대조
probe() {
    local src="$1" topic="$2"; shift 2
    local dests=() negatives=() seen_bang=0 a
    for a in "$@"; do
        if [ "$a" = "!" ]; then seen_bang=1; continue; fi
        if [ "$seen_bang" = "1" ]; then negatives+=("$a"); else dests+=("$a"); fi
    done

    local before_d=() before_n=() d n i
    for d in "${dests[@]}";     do before_d+=("$(count_records "$(broker_of "$d")" "$topic")"); done
    for n in "${negatives[@]}"; do before_n+=("$(count_records "$(broker_of "$n")" "$topic")"); done

    produce "$(broker_of "$src")" "$topic" "{\"probe\":\"mono511\",\"topic\":\"$topic\"}"

    # 릴레이는 폴링 주기가 있다. 도달할 때까지 최대 60초 기다린다 — 못 기다리면
    # "안 왔다" 와 "아직 안 왔다" 를 구별하지 못한다.
    local waited=0 all_ok=0
    while [ "$waited" -lt 60 ]; do
        all_ok=1
        for i in "${!dests[@]}"; do
            [ "$(count_records "$(broker_of "${dests[$i]}")" "$topic")" -gt "${before_d[$i]}" ] || all_ok=0
        done
        [ "$all_ok" = "1" ] && break
        sleep 5; waited=$((waited + 5))
    done

    for i in "${!dests[@]}"; do
        local after; after="$(count_records "$(broker_of "${dests[$i]}")" "$topic")"
        if [ "$after" -gt "${before_d[$i]}" ]; then
            note "  ✓ $src -> ${dests[$i]}  $topic   ${before_d[$i]} -> $after"
        else
            bad "$src -> ${dests[$i]}  $topic   ${before_d[$i]} -> $after (증가 없음, ${waited}s 대기)"
        fi
    done
    for i in "${!negatives[@]}"; do
        local after; after="$(count_records "$(broker_of "${negatives[$i]}")" "$topic")"
        if [ "$after" -eq "${before_n[$i]}" ]; then
            note "  ✓ (음성) $src -/-> ${negatives[$i]}  $topic   ${before_n[$i]} 유지"
        else
            bad "음성 대조 실패: $topic 이 화이트리스트에 없는 ${negatives[$i]} 에 도착했다 (${before_n[$i]} -> $after)"
        fi
    done
}

note "=== 크로스프로젝트 릴레이 도달 탐침 (증분이 술어 — 존재가 아니다) ==="
note "iam -> ecommerce"
probe iam account.created ecommerce ! wms scm
note "wms -> ecommerce, scm"
probe wms wms.inventory.adjusted.v1 ecommerce scm ! iam
note "wms -> ecommerce (scm 화이트리스트에는 없다)"
probe wms wms.master.sku.v1 ecommerce ! scm iam
note "ecommerce -> wms"
probe ecommerce ecommerce.fulfillment.requested.v1 wms ! scm iam
note "scm -> wms"
probe scm scm.procurement.inbound-expected.v1 wms ! ecommerce iam

if [ "$fails" -gt 0 ]; then
    printf '\nprobe-relay: FAILED — %d 건.\n' "$fails" >&2
    exit 1
fi
printf '\nprobe-relay: OK — 5계열 전부 도달했고, 화이트리스트 밖 목적지에는 한 건도 가지 않았다.\n'
