#!/usr/bin/env bash
# =============================================================================
# check-kafka-log-dirs.sh — TASK-MONO-526
#
# 선언된 kafka 볼륨은 브로커가 실제로 쓰는 디렉터리여야 한다.
#
# WHY THIS EXISTS (실측, 추정 아님)
# -----------------------------------------------------------------------------
# 7개 프로젝트의 compose 가 `kafka-data:/var/lib/kafka/data` 를 선언하고 있었는데,
# `apache/kafka:3.7.0` 은 로그 디렉터리를 컨테이너 `/tmp/kafka-logs` 에 잡는다. 즉
# **선언된 볼륨은 아무것도 담지 않았다.** 2026-08-13 erp 에서 잰 값:
#
#     토픽 생성 + 3건 produce      → end-offset  mono526.probe:0:3
#     /var/lib/kafka/data (마운트) → 비어 있음
#     /tmp/kafka-logs             → meta.properties + mono526.probe-0
#     docker compose down → up    → **토픽 자체가 사라짐** (오프셋이 아니라 토픽)
#
# 실패도 경고도 없다. Kafka 가 없는 토픽을 자동 생성하고 소비자 대부분이
# `auto-offset-reset=earliest` 로 붙으므로 **정상처럼 보인다.** 그래서 이 결함은
# 볼륨이 디스크를 잡고 `docker volume ls` 에 이름이 뜨는 동안에도 살아 있었다 —
# 영속이 아닌 것보다 나쁜, **영속인 줄 아는 상태**다.
#
# 🔴 이미지 기본값을 grep 으로 확인할 수 없다는 점이 이 가드의 존재 이유의 절반이다.
#    이미지 안의 config 파일은 `log.dirs=/tmp/kraft-combined-logs` 라고 적혀 있는데
#    실제로 쓰이는 경로는 `/tmp/kafka-logs` 다(엔트리포인트가 넣는다). 문서로 추적할 수
#    없는 기본값에 영속성을 걸면 안 된다 — 명시적으로 적는다.
#
# 🔵 CI 는 이 결함을 구조적으로 볼 수 없다. Testcontainers 도 e2e 스택도 매 런 새
#    브로커를 띄우므로 "재기동 사이의 유실" 이라는 성질 자체가 CI 에 존재하지 않는다.
#    그래서 이 가드는 런타임이 아니라 **선언**을 검사한다. 런타임 테스트로는 불가능하다.
#
# THE PREDICATE
# -----------------------------------------------------------------------------
# compose 의 각 서비스 중 `image: apache/kafka*` 이면서 `KAFKA_PROCESS_ROLES` 를 가진
# 것(= 브로커. 같은 이미지를 쓰는 `kafka-init` 1회성 잡은 브로커가 아니다)에 대해:
#
#   (1) `kafka-data:<경로>` 마운트가 있으면  ⇒  KAFKA_LOG_DIRS == <경로> 여야 한다
#   (2) 마운트가 없으면                      ⇒  `KAFKA-EPHEMERAL:` 선언이 있어야 한다
#   (3) 마운트가 없는데 KAFKA_LOG_DIRS 만 있으면 ⇒ 갈 곳 없는 경로다. 실패
#
# (2) 가 주석 토큰인 것은 의도적이다. 침묵은 "영속" 으로 읽힌다 — 이 티켓이 정확히 그
# 오독에서 나왔다. 휘발성은 **선언되어야** 하고, 선언은 기계가 읽을 수 있어야 한다.
#
# 🔴 주석 줄은 env/마운트 파싱에서 제외한다. 이 저장소의 compose 주석들이 산문으로
#    `KAFKA_LOG_DIRS` 를 언급하기 때문에, 제외하지 않으면 가드가 **자기 설명문을 설정으로
#    오인해** 전부 초록이 된다. (반대로 `KAFKA-EPHEMERAL:` 은 주석 줄에서만 읽는다.)
#
# 0건은 통과가 아니다 — 브로커를 하나도 못 찾으면 계측 실패로 보고 exit 1.
#
# 사용법:
#   check-kafka-log-dirs.sh              저장소 전체(git ls-files)
#   check-kafka-log-dirs.sh FILE...      명시한 파일만 (자기검증이 쓴다)
#   check-kafka-log-dirs.sh --self-test  술어가 무는지 자기검증
#
# Exit 0 = 선언과 실제가 일치. Exit 1 = 드리프트.
# =============================================================================
set -uo pipefail

SELF="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/$(basename "${BASH_SOURCE[0]}")"

# -----------------------------------------------------------------------------
# 파서 — 파일 목록을 받아 브로커 1개당 레코드 1줄을 낸다.
#   file|service|mount|logdirs|ephemeral(0|1)
#
# 🔴 구분자가 탭이면 안 된다. 탭은 IFS 공백류라 bash `read` 가 **연속 구분자를 하나로
#    접는다** — 빈 필드가 사라지고 뒤 필드가 앞으로 밀린다. 실제로 그렇게 물렸다:
#    마운트 없는 브로커(mount="" logdirs="")의 ephemeral=1 이 mount 자리로 올라와
#    "'kafka-data:1' 을 마운트하는데 KAFKA_LOG_DIRS 가 없습니다" 라는 **오탐**이 났다.
#    compose 경로/값에 '|' 는 나오지 않으므로 비공백 구분자를 쓴다.
# -----------------------------------------------------------------------------
extract() {
    awk '
    function flush() {
        if (svc != "" && is_kafka && is_broker)
            printf "%s|%s|%s|%s|%d\n", FILENAME, svc, mount, logdirs, ephemeral
        svc = ""; is_kafka = 0; is_broker = 0; mount = ""; logdirs = ""; ephemeral = 0
    }
    FNR == 1 { flush() }
    # 최상위 키(들여쓰기 0)는 services 블록의 끝
    /^[A-Za-z0-9_.-]+:/ { flush(); next }
    # 서비스 헤더 = 2칸 들여쓰기 + 키 + 줄 끝
    /^  [A-Za-z0-9_.-]+:[ \t]*$/ {
        flush()
        svc = $1; sub(/:$/, "", svc)
        next
    }
    svc == "" { next }
    # --- 주석 줄: 휘발성 토큰만 읽는다 -------------------------------------
    /^[ \t]*#/ {
        if (index($0, "KAFKA-EPHEMERAL:") > 0) ephemeral = 1
        next
    }
    # --- 설정 줄 -----------------------------------------------------------
    /^[ \t]*image:[ \t]*"?apache\/kafka/           { is_kafka = 1; next }
    /^[ \t]*-?[ \t]*KAFKA_PROCESS_ROLES[:=]/       { is_broker = 1; next }
    /^[ \t]*-[ \t]*kafka-data:/ {
        line = $0
        sub(/^[ \t]*-[ \t]*kafka-data:/, "", line)
        sub(/:.*$/, "", line)            # `:ro` 같은 접미 옵션 제거
        gsub(/[ \t"]/, "", line)
        mount = line
        next
    }
    /KAFKA_LOG_DIRS/ {
        line = $0
        sub(/^.*KAFKA_LOG_DIRS[:=][ \t]*/, "", line)
        gsub(/[ \t"]/, "", line)
        logdirs = line
        next
    }
    END { flush() }
    ' "$@"
}

# -----------------------------------------------------------------------------
# 판정
# -----------------------------------------------------------------------------
judge() {
    local fail=0 count=0 persistent=0 ephemeral_count=0

    while IFS='|' read -r file svc mount logdirs eph; do
        [ -n "$file" ] || continue
        count=$((count + 1))

        if [ -n "$mount" ]; then
            persistent=$((persistent + 1))
            if [ -z "$logdirs" ]; then
                echo "✗ $file → 서비스 '$svc'"
                echo "  'kafka-data:$mount' 를 마운트하는데 KAFKA_LOG_DIRS 가 없습니다."
                echo "  apache/kafka 는 컨테이너 /tmp/kafka-logs 에 쓰므로 이 볼륨은 **비어 있게 됩니다.**"
                echo "  재기동마다 토픽·오프셋·컨슈머 그룹·DLT 가 조용히 사라집니다(TASK-MONO-526 실측)."
                echo "  해결:  KAFKA_LOG_DIRS: $mount   를 environment 에 추가"
                fail=1
            elif [ "$logdirs" != "$mount" ]; then
                echo "✗ $file → 서비스 '$svc'"
                echo "  마운트 지점은 '$mount' 인데 KAFKA_LOG_DIRS 는 '$logdirs' 입니다."
                echo "  브로커는 '$logdirs' 에 쓰고 볼륨은 '$mount' 를 잡습니다 — 둘 다 무의미해집니다."
                echo "  해결: 두 값을 같게 맞추십시오."
                fail=1
            fi
        else
            ephemeral_count=$((ephemeral_count + 1))
            if [ -n "$logdirs" ]; then
                echo "✗ $file → 서비스 '$svc'"
                echo "  KAFKA_LOG_DIRS='$logdirs' 를 선언했는데 그 경로에 마운트된 볼륨이 없습니다."
                echo "  경로만 있고 저장소가 없으면 컨테이너 수명과 함께 사라집니다."
                echo "  해결: 'kafka-data:$logdirs' 마운트를 추가하거나, 휘발성이면 KAFKA_LOG_DIRS 를 지우고"
                echo "        서비스 블록 안에 주석으로 'KAFKA-EPHEMERAL: <이유>' 를 선언하십시오."
                fail=1
            elif [ "$eph" != "1" ]; then
                echo "✗ $file → 서비스 '$svc'"
                echo "  볼륨도 없고 휘발성 선언도 없습니다. 침묵은 '영속' 으로 읽힙니다 —"
                echo "  TASK-MONO-526 이 정확히 그 오독에서 시작했습니다."
                echo "  해결: 영속이 필요하면 'kafka-data:<경로>' 마운트 + 같은 값의 KAFKA_LOG_DIRS 를,"
                echo "        휘발성이 맞으면 서비스 블록 안에 'KAFKA-EPHEMERAL: <이유>' 주석을 넣으십시오."
                fail=1
            fi
        fi
    done

    # 0건은 통과가 아니라 계측 실패다. 글롭이 어긋나거나 파서의 서비스 헤더 정규식이
    # 깨지면 이 가드는 아무것도 못 찾고 조용히 초록이 된다.
    if [ "$count" -eq 0 ]; then
        echo "✗ apache/kafka 브로커를 하나도 찾지 못했습니다."
        echo "  이 저장소에는 존재합니다 ⇒ 못 찾은 것은 계측 실패입니다."
        echo "  git ls-files 글롭과 파서의 서비스 헤더 정규식을 확인하십시오."
        return 1
    fi

    if [ "$fail" -ne 0 ]; then
        echo
        echo "[kafka-log-dirs] FAIL"
        return 1
    fi

    echo "[kafka-log-dirs] OK — 브로커 ${count}개 (영속 ${persistent} · 휘발성 선언 ${ephemeral_count})"
    return 0
}

# -----------------------------------------------------------------------------
# 자기검증 — 술어가 무는지 본다.
#
# 🔴 픽스처를 손으로 짓지 않는다. **실제 compose 파일을 복사해서 변형**한다. 손으로 지은
#    픽스처는 실물보다 관대하기 쉽고, 그러면 초록이 아무것도 증명하지 못한다.
# -----------------------------------------------------------------------------
self_test() {
    local root tmp src rc pass=0 total=0
    root="$(git rev-parse --show-toplevel)"
    src="$root/projects/erp-platform/docker-compose.yml"
    [ -f "$src" ] || { echo "✗ 자기검증 원본이 없습니다: $src"; return 1; }

    tmp="$(mktemp -d)"
    trap 'rm -rf "$tmp"' RETURN

    check() {   # check <이름> <기대 rc> <파일>
        total=$((total + 1))
        bash "$SELF" "$3" >"$tmp/out.txt" 2>&1
        rc=$?
        if [ "$rc" = "$2" ]; then
            echo "  ✔ $1 (rc=$rc)"; pass=$((pass + 1))
        else
            echo "  ✗ $1 — 기대 rc=$2, 실제 rc=$rc"; sed 's/^/      /' "$tmp/out.txt"
        fi
    }

    echo "[kafka-log-dirs] 자기검증 — 실제 erp compose 를 변형해 술어가 무는지 본다"

    cp "$src" "$tmp/a.yml"
    check "원본은 통과한다" 0 "$tmp/a.yml"

    # (1) KAFKA_LOG_DIRS 제거 → 526 이 고친 바로 그 상태
    grep -v '^      KAFKA_LOG_DIRS:' "$src" > "$tmp/b.yml"
    check "KAFKA_LOG_DIRS 를 지우면 문다 (= 526 이전 상태)" 1 "$tmp/b.yml"

    # (2) 경로 불일치
    sed 's|^      KAFKA_LOG_DIRS: .*|      KAFKA_LOG_DIRS: /tmp/kafka-logs|' "$src" > "$tmp/c.yml"
    check "마운트와 다른 경로면 문다" 1 "$tmp/c.yml"

    # (3) 볼륨 마운트만 제거 → 휘발성 선언 없는 침묵
    grep -v '^      - kafka-data:/var/lib/kafka/data' "$src" > "$tmp/d.yml"
    check "볼륨도 휘발성 선언도 없으면 문다" 1 "$tmp/d.yml"

    # (4) (3) 에 휘발성 선언을 넣으면 통과 — 단, KAFKA_LOG_DIRS 도 함께 지워야 한다
    grep -v '^      - kafka-data:/var/lib/kafka/data' "$src" \
        | grep -v '^      KAFKA_LOG_DIRS:' \
        | sed 's|^  kafka:$|  kafka:\n    # KAFKA-EPHEMERAL: 자기검증용 선언|' > "$tmp/e.yml"
    check "휘발성을 선언하면 통과한다" 0 "$tmp/e.yml"

    # (5) 브로커가 없는 파일 → 0건 = 계측 실패
    printf 'services:\n  redis:\n    image: redis:7-alpine\n' > "$tmp/f.yml"
    check "브로커 0건은 통과가 아니라 실패다" 1 "$tmp/f.yml"

    # (6) kafka-init(같은 이미지, PROCESS_ROLES 없음)은 브로커가 아니다 ⇒ 0건 실패로 떨어진다
    sed -n '1,/^  kafka:/p' "$src" | head -n -1 > "$tmp/g.yml"
    printf '  kafka-init:\n    image: apache/kafka:3.7.0\n    command: ["true"]\n' >> "$tmp/g.yml"
    check "kafka-init 은 브로커로 세지 않는다 (0건 → 실패)" 1 "$tmp/g.yml"

    echo "[kafka-log-dirs] 자기검증 $pass/$total"
    [ "$pass" = "$total" ]
}

# -----------------------------------------------------------------------------
main() {
    if [ "${1:-}" = "--self-test" ]; then
        self_test
        exit $?
    fi

    local files=()
    if [ "$#" -gt 0 ]; then
        files=("$@")
    else
        cd "$(git rev-parse --show-toplevel)"
        # --others --exclude-standard: 새 compose 를 **추가**하는 것이 이 드리프트의 주요
        # 도착 경로다. 스테이징 전에는 git ls-files 가 그 파일을 못 보므로, 로컬에서
        # 미리 돌린 초록이 아무 의미가 없어진다.
        mapfile -t files < <(git ls-files --cached --others --exclude-standard \
            '*docker-compose*.yml' '*docker-compose*.yaml')
    fi

    if [ "${#files[@]}" -eq 0 ]; then
        echo "✗ docker-compose 파일을 하나도 찾지 못했습니다 — 계측 실패입니다."
        exit 1
    fi

    extract "${files[@]}" | judge
}

main "$@"
