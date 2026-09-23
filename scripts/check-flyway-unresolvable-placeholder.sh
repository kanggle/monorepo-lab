#!/usr/bin/env bash
# =============================================================================
# check-flyway-unresolvable-placeholder.sh — TASK-MONO-723
#
# A Flyway migration must not contain a placeholder that nothing resolves.
#
# 🔴 THIS DOES NOT BAN PLACEHOLDERS. Placeholders are a legitimate Flyway
# feature. What is banned is an UNRESOLVABLE one: a name for which no
# `spring.flyway.placeholders.<name>` exists in that service's configuration and
# which Flyway does not provide itself. Banning the feature was considered and
# rejected (TASK-MONO-723 § Scope/제외): the first migration that legitimately
# uses a configured placeholder would have to switch this guard off, and a guard
# that gets switched off protects nothing.
#
# WHY THIS EXISTS — twice, and the second time was the warning itself
# -----------------------------------------------------------------------------
#   2026-09-23  V0036 (TASK-MONO-717)  a header QUOTED a config key and spelled
#                                      the placeholder sequence while quoting it
#   2026-09-23  V0037 (TASK-MONO-721)  🔴🔴 a paragraph saying "never write this
#                                      sequence" was itself written with it; the
#                                      ellipsis in the prose became the
#                                      placeholder NAME
#
# After the first occurrence this repo decided not to build a guard, on the
# reasoning that a placeholder is a legitimate feature and the author now knew
# the trap. The second occurrence was committed by someone who knew the trap, in
# the act of documenting it. "Knowing it is enough" is therefore disproved, and
# this repository's rule is that an exemption is withdrawn at the second
# occurrence — not re-argued.
#
# 🔴 THE COST IS THAT THE SYMPTOM DOES NOT NAME THE CAUSE
# -----------------------------------------------------------------------------
# Flyway substitutes placeholders inside COMMENTS too, and it fails at parse
# time, which in Spring is context load. What CI printed was:
#
#     CredentialJpaRepositoryTest        8 tests, 8 FAILED
#     DeviceSessionJpaRepositoryTest     4 tests, 4 FAILED
#     E2E (fan-platform v1 live-trio smoke)   container never became healthy
#
# Three jobs, one comment line, and the only signal pointing at a comment was
# `PlaceholderReplacingReader` four frames down a stack trace. fan-platform went
# red for a reason that has nothing to do with fan-platform — it starts an iam
# auth-service container — so the next reader starts in the wrong project. The
# whole value of this guard is deleting that distance: it names the file, the
# line and the placeholder name before anything is built.
#
# 🔴 LOCAL TESTS CANNOT CATCH THIS ON EVERY HOST (measured, not assumed)
# -----------------------------------------------------------------------------
# On the Windows development host of 2026-09-23, `:auth-service:test` was rc=0
# while `CredentialJpaRepositoryTest` reported tests="8" skipped="8" — every cell
# that boots a Flyway context is Docker-gated and skipped. So a green local suite
# there is a green that never parsed a migration. That is why this check is a
# FILE check that needs no database, in the same spirit as
# `check-dev-seed-migration-band.sh`.
#
# WHAT THIS DOES NOT COVER (deliberate boundaries, each with a reason)
# -----------------------------------------------------------------------------
# · Files outside a `src/{main,test}/resources/db/<kind>/` directory. Task files,
#   ADRs and incident notes in this repo quote the failing output verbatim —
#   including TASK-MONO-723 itself — and they are RIGHT to. Widening the
#   population to the whole repository would make this guard fail on the
#   documentation of the defect it exists for, which is the exact trap named in
#   that ticket's Failure Scenario 1. The population is migrations because
#   migrations are what Flyway parses.
# · Placeholders configured only through the environment
#   (`SPRING_FLYWAY_PLACEHOLDERS_*` in compose or a deployment). This guard reads
#   configuration FILES, so such a value is invisible to it and the migration
#   would be reported. Measured 2026-09-23: no service in this repo does that.
#   The remediation if one ever does is to declare the placeholder in
#   `application.yml` as well — which is also where the next human looks.
# · Whether the placeholder's VALUE is correct. Only whether a value exists.
# · A reconfigured placeholder prefix/suffix — that is not skipped, it is a hard
#   failure below, because this guard's pattern would silently stop matching.
#
# Exit 0 = every placeholder in every migration resolves. Exit 1 = one does not.
# =============================================================================
set -euo pipefail

SELF_ABS="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/$(basename "${BASH_SOURCE[0]}")"

SELF_TEST=0
[ "${1:-}" = "--self-test" ] && SELF_TEST=1

# The two-character opening sequence, built from its hex code rather than typed.
# Not because this script could ever bite itself — it is not in the population —
# but because a reader who greps for the sequence to learn "is this allowed?"
# must not find it spelled out in the file that forbids it. The same discipline
# the corrected V0037 header uses: describe the syntax, do not write it.
PH_OPEN="$(printf '\x24{')"

# =============================================================================
# The scan, as a function, so the self-test can run the whole thing end to end
# against a throwaway repository instead of testing a re-implementation of it.
# =============================================================================
run_scan() {
    cd "$(git rev-parse --show-toplevel)"

    local fail=0

    # -------------------------------------------------------------------------
    # FAIL CLOSED on a reconfigured prefix/suffix.
    #
    # This guard's pattern is Flyway's DEFAULT delimiter pair. If a service
    # changes it, every placeholder in that service stops matching and this
    # guard goes green for a reason that has nothing to do with correctness —
    # the worst failure mode a guard has. So: notice it and stop.
    # -------------------------------------------------------------------------
    local prefix_cfg
    prefix_cfg="$(git ls-files --cached --others --exclude-standard \
        '*.yml' '*.yaml' '*.properties' \
        | tr '\n' '\0' \
        | xargs -0 -r grep -lE 'placeholder-(prefix|suffix)|placeholderPrefix|placeholderSuffix' 2>/dev/null \
        || true)"
    if [ -n "$prefix_cfg" ]; then
        echo "✗ 플레이스홀더 구분자를 바꾼 설정이 있습니다:"
        echo "$prefix_cfg" | sed 's/^/    /'
        echo "  이 가드는 Flyway 기본 구분자만 찾습니다. 구분자가 바뀌면 이 가드는"
        echo "  «위반이 없어서» 가 아니라 «못 찾아서» 초록이 됩니다 — 그 상태로 두지"
        echo "  않기 위해 여기서 멈춥니다. 패턴을 그 설정에 맞춰 확장하십시오."
        return 1
    fi

    # -------------------------------------------------------------------------
    # POPULATION — every SQL file under a `db/<kind>/` resources directory.
    #
    # `--others --exclude-standard` on purpose: the arrival path is somebody
    # ADDING a migration, and a plain `git ls-files` would not see it until it is
    # staged. CI is unaffected (everything is tracked after checkout); this keeps
    # a pre-stage local run from being a green that means nothing.
    #
    # src/test/resources is included, unlike in the dev-seed-band guard. There
    # the scoping mattered because test fixtures are DELIBERATELY malformed in
    # ways production migrations must not be (a collision fixture, an H2
    # dialect copy). Here there is no such thing as a deliberately unresolvable
    # placeholder: a test-resource migration is parsed by Flyway exactly like a
    # shipping one and dies exactly the same way.
    # -------------------------------------------------------------------------
    local -a files=()
    mapfile -t files < <(git ls-files --cached --others --exclude-standard \
        '*/src/main/resources/db/*/*.sql' \
        '*/src/test/resources/db/*/*.sql')

    if [ "${#files[@]}" -eq 0 ]; then
        echo "✗ 마이그레이션 파일을 하나도 찾지 못했습니다."
        echo "  이 저장소에는 수백 개가 있습니다 ⇒ 0건은 «위반 없음» 이 아니라"
        echo "  계측 실패입니다. git ls-files 글롭을 확인하십시오."
        return 1
    fi

    # -------------------------------------------------------------------------
    # One grep over the whole population. No subshell per file: this repo runs on
    # msys, where a spawn-per-item loop over a few hundred files reads as a hang.
    # -------------------------------------------------------------------------
    # -H is not decoration. xargs may split the list into several grep calls, and
    # a call that happens to receive exactly ONE file prints no filename prefix —
    # which would make every hit in that batch unattributable, and makes the
    # single-file self-test fixtures below parse as garbage. Force the prefix.
    local hits
    hits="$(printf '%s\0' "${files[@]}" \
        | xargs -0 -r grep -HnEo '[$][{][^}]*[}]' 2>/dev/null || true)"

    echo "[flyway-placeholder] 마이그레이션 ${#files[@]}개를 검사했습니다."

    if [ -z "$hits" ]; then
        # 0 hits is the expected state today and is NOT reported as a pass on its
        # own — the population count above is what makes this green meaningful.
        echo "[flyway-placeholder] OK — 플레이스홀더 사용 0건."
        return 0
    fi

    # -------------------------------------------------------------------------
    # RESOLVABILITY. A name resolves when either:
    #   (a) the owning service declares `spring.flyway.placeholders.<name>`, or
    #   (b) Flyway provides it itself — the `flyway:` prefixed built-ins
    #       (defaultSchema, user, database, table, timestamp, filename,
    #       workingDirectory, …). Those are supplied by Flyway at run time, so
    #       they are resolvable without any configuration.
    # -------------------------------------------------------------------------
    local line file lineno raw name svc
    while IFS= read -r line; do
        [ -n "$line" ] || continue
        file="${line%%:*}"
        raw="${line#*:}"
        lineno="${raw%%:*}"
        name="${raw#*:}"
        name="${name#"$PH_OPEN"}"
        name="${name%\}}"

        # Flyway's own built-ins.
        case "$name" in
            flyway:*) continue ;;
        esac

        svc="${file%%/src/*}"
        if configured_placeholders "$svc" | grep -qxF -- "$name"; then
            continue
        fi

        echo
        echo "✗ $file:$lineno"
        echo "  못 푸는 Flyway 플레이스홀더입니다 — 이름: «$name»"
        echo
        echo "  이 이름에 값을 주는 곳이 없습니다. 찾아본 곳:"
        echo "    · ${svc}/src/main/resources/application*.{yml,yaml,properties}"
        echo "      의 spring.flyway.placeholders.<이름>"
        echo "    · Flyway 가 스스로 제공하는 flyway: 접두 내장 이름"
        echo
        echo "  🔴 이 줄이 주석이어도 마찬가지입니다 — Flyway 는 주석 안의 플레이스홀더도"
        echo "     치환하고, 못 풀면 파싱 단계에서 마이그레이션 전체를 실패시킵니다."
        echo "  🔴 그래서 증상이 원인을 안 가리킵니다. CI 에는 «리포지토리 슬라이스"
        echo "     테스트가 전부 FAILED» · «E2E 컨테이너가 안 뜸» 으로 나오고, 주석"
        echo "     한 줄이라는 신호는 PlaceholderReplacingReader 스택 4단계 아래뿐입니다."
        echo "     (V0036/TASK-MONO-717, V0037/TASK-MONO-721 에서 두 번 실측)"
        echo
        echo "  고치는 법 — 둘 중 하나입니다:"
        echo "    ① 이 줄이 설명이라면: 구분자를 «적지 말고 말로 서술»하십시오."
        echo "       («달러 기호 뒤에 중괄호로 감싼 이름» 처럼). V0037 헤더가 그 모양입니다."
        echo "    ② 정말 치환이 필요하다면: 그 서비스의 application.yml 에"
        echo "       spring.flyway.placeholders.<이름> 을 선언하십시오. 그러면 이 가드는"
        echo "       통과시킵니다 — 이 가드가 막는 것은 «못 푸는» 것뿐입니다."
        fail=1
    done <<< "$hits"

    if [ "$fail" -ne 0 ]; then
        echo
        echo "[flyway-placeholder] FAIL"
        return 1
    fi

    echo "[flyway-placeholder] OK — 발견된 플레이스홀더가 전부 해소됩니다."
    return 0
}

# -----------------------------------------------------------------------------
# configured_placeholders <service-root> — one name per line.
#
# Reads the service's own configuration files. YAML is handled with a small
# indentation state machine rather than a parser: a `placeholders:` key nested
# under a `flyway:` key, then every scalar key indented below it. The reason this
# is trustworthy enough is not the code — it is self-test cell (2), which feeds a
# real configured placeholder through the whole guard and requires a PASS. A
# reader of this function cannot otherwise tell a correct parser from one that
# always returns nothing, because this repository currently configures ZERO
# placeholders (censused 2026-09-23) and both would look identical.
# -----------------------------------------------------------------------------
configured_placeholders() {
    local svc="$1"
    local -a cfgs=()
    mapfile -t cfgs < <(git ls-files --cached --others --exclude-standard \
        "${svc}/src/main/resources/application*" 2>/dev/null || true)
    [ "${#cfgs[@]}" -gt 0 ] || return 0

    local c
    for c in "${cfgs[@]}"; do
        [ -f "$c" ] || continue
        case "$c" in
            *.properties)
                sed -nE 's/^[[:space:]]*spring\.flyway\.placeholders\.([^=:[:space:]]+)[[:space:]]*[=:].*/\1/p' "$c"
                ;;
            *.yml|*.yaml)
                awk '
                    function indent(s) { match(s, /^[ ]*/); return RLENGTH }
                    /^[[:space:]]*#/ { next }
                    {
                        ind = indent($0)
                        if ($0 ~ /^[[:space:]]*flyway:[[:space:]]*$/) { fly = ind; ph = -1; next }
                        if (fly >= 0 && ind <= fly && $0 !~ /^[[:space:]]*$/) { fly = -1; ph = -1 }
                        if (fly >= 0 && $0 ~ /^[[:space:]]*placeholders:[[:space:]]*$/) { ph = ind; next }
                        if (ph >= 0) {
                            if (ind > ph && match($0, /^[[:space:]]*[A-Za-z0-9_.-]+[[:space:]]*:/)) {
                                key = $0
                                sub(/^[[:space:]]*/, "", key)
                                sub(/[[:space:]]*:.*$/, "", key)
                                print key
                            } else if ($0 !~ /^[[:space:]]*$/ && ind <= ph) {
                                ph = -1
                            }
                        }
                    }
                    BEGIN { fly = -1; ph = -1 }
                ' "$c"
                ;;
        esac
    done
}

# =============================================================================
# SELF-TEST — the guard must be able to FAIL, and must be able to NOT fail.
#
# Every cell builds a throwaway git repository and runs THIS script inside it.
# Nothing re-implements the predicate, so a self-test green means the shipped
# code bit, not that a copy of it did.
# =============================================================================
fixture_repo() {
    local dir="$1"
    mkdir -p "$dir"
    git -C "$dir" init -q
}

run_in() { ( cd "$1" && bash "$SELF_ABS" ) ; }

if [ "$SELF_TEST" = "1" ]; then
    tmp="$(mktemp -d)"
    trap 'rm -rf "$tmp"' EXIT
    st_fail=0
    SVC="projects/demo-platform/apps/demo-service"

    # -- (1) BITE -------------------------------------------------------------
    # The fixture is NOT invented. It is the first version of V0037's warning
    # paragraph — the text that actually reached CI and turned three jobs red —
    # reconstructed here: a sentence forbidding the sequence, written with it,
    # whose ellipsis becomes the placeholder name.
    r1="$tmp/bite"; fixture_repo "$r1"
    mkdir -p "$r1/$SVC/src/main/resources/db/migration"
    {
        printf -- '-- TASK-MONO-XXX example migration\n'
        printf -- '--\n'
        printf -- '-- Never write Flyway placeholder syntax (%s...}) anywhere in a migration,\n' "$PH_OPEN"
        printf -- '-- COMMENTS INCLUDED. Flyway substitutes placeholders inside comments too.\n'
        printf -- 'UPDATE oauth_clients SET scopes = %s WHERE client_id = %s;\n' "'a'" "'b'"
    } > "$r1/$SVC/src/main/resources/db/migration/V0037__example.sql"
    if out1="$(run_in "$r1" 2>&1)"; then
        echo "SELF-TEST (1) FAILED: 경고문이 그 문법으로 쓰인 V0037 첫 판을 통과시켰습니다."
        st_fail=1
    else
        for needle in "V0037__example.sql" "못 푸는 Flyway 플레이스홀더" "주석 안의 플레이스홀더도"; do
            case "$out1" in
                *"$needle"*) : ;;
                *) echo "SELF-TEST (1) FAILED: 메시지에 «$needle» 이 없습니다."; st_fail=1 ;;
            esac
        done
        # AC-2 wants file AND line AND name. The name here is the ellipsis.
        case "$out1" in
            *"V0037__example.sql:3"*) : ;;
            *) echo "SELF-TEST (1) FAILED: 메시지가 파일:줄 을 대지 않습니다."; st_fail=1 ;;
        esac
        case "$out1" in
            *"«...»"*) : ;;
            *) echo "SELF-TEST (1) FAILED: 메시지가 플레이스홀더 이름을 대지 않습니다."; st_fail=1 ;;
        esac
        echo "self-test (1): 경고문-자기위반 픽스처를 문다 — OK"
    fi

    # -- (6) the message must not spell the sequence ---------------------------
    # Measured on the real output of cell (1), not on this file's source: bash
    # parameter expansion legitimately contains the same two characters, so a
    # source-level grep would report a violation that does not exist. What can
    # actually teach the next person the wrong lesson is what the guard PRINTS.
    if [ -n "${out1:-}" ]; then
        case "$out1" in
            *"$PH_OPEN"*)
                echo "SELF-TEST (6) FAILED: 실패 메시지가 금지된 구분자를 그대로 적고 있습니다."
                echo "  그러면 다음 사람이 그것을 보고 «이 문법은 써도 되는구나» 로 읽습니다."
                st_fail=1 ;;
            *) echo "self-test (6): 실패 메시지가 그 문법을 적지 않는다 — OK" ;;
        esac
    fi

    # -- (2) a CONFIGURED placeholder must PASS -------------------------------
    r2="$tmp/configured"; fixture_repo "$r2"
    mkdir -p "$r2/$SVC/src/main/resources/db/migration"
    cat > "$r2/$SVC/src/main/resources/application.yml" <<'YAML'
spring:
  application:
    name: demo
  flyway:
    enabled: true
    placeholders:
      seedTenant: demo-corp
      seedRegion: kr
YAML
    printf -- 'INSERT INTO tenants (code) VALUES (%sseedTenant});\n' "$PH_OPEN" \
        > "$r2/$SVC/src/main/resources/db/migration/V0001__seed.sql"
    if out2="$(run_in "$r2" 2>&1)"; then
        echo "self-test (2): 값이 설정된 플레이스홀더는 통과한다 — OK"
    else
        echo "SELF-TEST (2) FAILED: 설정된 플레이스홀더를 오탐했습니다. 가드가 꺼집니다."
        echo "$out2" | sed 's/^/    /'
        st_fail=1
    fi

    # -- (2b) …and the same file WITHOUT the config must fail -------------------
    # Without this pair, cell (2) proves nothing: a parser that always returns
    # "configured" would pass it too.
    r2b="$tmp/unconfigured"; fixture_repo "$r2b"
    mkdir -p "$r2b/$SVC/src/main/resources/db/migration"
    cp "$r2/$SVC/src/main/resources/db/migration/V0001__seed.sql" \
       "$r2b/$SVC/src/main/resources/db/migration/V0001__seed.sql"
    if run_in "$r2b" >/dev/null 2>&1; then
        echo "SELF-TEST (2b) FAILED: 설정이 없는데도 통과했습니다 — 파서가 언제나"
        echo "  «설정됨» 을 답하고 있을 수 있습니다."
        st_fail=1
    else
        echo "self-test (2b): 같은 파일이 설정 없이는 빨개진다 — OK"
    fi

    # -- (3) Flyway's own built-ins must PASS ----------------------------------
    r3="$tmp/builtin"; fixture_repo "$r3"
    mkdir -p "$r3/$SVC/src/main/resources/db/migration"
    printf -- 'CREATE TABLE %sflyway:defaultSchema}.t (id INT);\n' "$PH_OPEN" \
        > "$r3/$SVC/src/main/resources/db/migration/V0001__t.sql"
    if run_in "$r3" >/dev/null 2>&1; then
        echo "self-test (3): Flyway 내장 이름은 통과한다 — OK"
    else
        echo "SELF-TEST (3) FAILED: flyway: 내장 이름을 오탐했습니다."
        st_fail=1
    fi

    # -- (4) JSON path expressions must PASS -----------------------------------
    # FS-2: a naive search for the dollar sign alone bites every MySQL JSON path
    # in the repository — V0037's own body is exactly that — and a guard that
    # reds hundreds of correct files gets switched off.
    r4="$tmp/jsonpath"; fixture_repo "$r4"
    mkdir -p "$r4/$SVC/src/main/resources/db/migration"
    cat > "$r4/$SVC/src/main/resources/db/migration/V0001__json.sql" <<'SQL'
UPDATE oauth_clients
SET authorization_grant_types = JSON_ARRAY_APPEND(authorization_grant_types, '$', 'x')
WHERE JSON_SEARCH(authorization_grant_types, 'one', 'x') IS NULL
  AND JSON_EXTRACT(meta, '$.a.b') IS NOT NULL
  AND JSON_EXTRACT(meta, '$[0]') IS NOT NULL;
SQL
    if run_in "$r4" >/dev/null 2>&1; then
        echo "self-test (4): JSON 경로 표현식은 통과한다 — OK"
    else
        echo "SELF-TEST (4) FAILED: JSON 경로를 물었습니다 (FS-2). 이 가드는 꺼질 것입니다."
        st_fail=1
    fi

    # -- (5) the sequence OUTSIDE a migration must PASS ------------------------
    # The documentation of this defect quotes the failing output verbatim — this
    # guard's own ticket does. If the population were "the whole repository",
    # the guard would fail on the file that explains it. That is FS-1.
    r5="$tmp/outside"; fixture_repo "$r5"
    mkdir -p "$r5/tasks/ready" "$r5/$SVC/src/main/resources/db/migration"
    printf -- 'FlywayException: No value provided for placeholder: %s...}\n' "$PH_OPEN" \
        > "$r5/tasks/ready/TASK-MONO-723-incident.md"
    printf -- 'SELECT 1;\n' > "$r5/$SVC/src/main/resources/db/migration/V0001__ok.sql"
    if run_in "$r5" >/dev/null 2>&1; then
        echo "self-test (5): 마이그레이션 밖의 인용은 통과한다 — OK"
    else
        echo "SELF-TEST (5) FAILED: 사고 기록 문서를 물었습니다 — 이 결함을 설명하는"
        echo "  문서가 상시 빨강이 됩니다 (FS-1)."
        st_fail=1
    fi

    # -- (7) non-vacuity against the REAL repository ---------------------------
    # A guard whose glob has drifted finds nothing and says OK. The population
    # count is printed by the scan itself; here we require it to be large.
    real_out="$(run_scan 2>&1 || true)"
    real_n="$(printf '%s\n' "$real_out" | sed -nE 's/^\[flyway-placeholder\] 마이그레이션 ([0-9]+)개.*/\1/p')"
    if [ -z "$real_n" ] || [ "$real_n" -lt 100 ]; then
        echo "SELF-TEST (7) FAILED: 실제 저장소 모집단이 ${real_n:-?} 입니다 (100 미만)."
        echo "  글롭이 어긋났을 가능성이 높습니다 — 0건 초록과 구별되어야 합니다."
        st_fail=1
    else
        echo "self-test (7): 실제 모집단 ${real_n}개 — 공허하지 않다 — OK"
    fi

    if [ "$st_fail" -ne 0 ]; then
        echo
        echo "[flyway-placeholder] SELF-TEST FAILED"
        exit 1
    fi
    echo "[flyway-placeholder] self-test: 7칸 전부 OK"
    exit 0
fi

run_scan
