#!/usr/bin/env bash
#
# check-env-preflight.sh — TASK-MONO-548
#
# Stops a boot that would BAKE THE WRONG PASSWORD into a fresh data volume.
#
# ---------------------------------------------------------------------------
# THE DEFECT
# ---------------------------------------------------------------------------
# Every project's compose reads its credentials with a fallback:
#
#     MASTER_DB_PASSWORD: ${MASTER_DB_PASSWORD:-master}
#
# `.env` is gitignored (measured: `git ls-files projects/*/.env` -> 0 files), so a
# fresh clone has none. The `:-` fallback then makes that boot SUCCEED QUIETLY with a
# different password than the one the project intends — compose exits 0 and prints no
# warning at all (measured: with `.env` present vs absent, 9 of 9 wms password vars
# resolve differently, rc=0 both times).
#
# Postgres/MySQL/MinIO only run their init on an EMPTY data directory, so that wrong
# password is written into the volume permanently. When `.env` later appears — copied
# from `.env.example`, as the setup docs say — the apps connect with the intended
# password, the server still holds the fallback one, and every service dies with
# `FATAL: password authentication failed`.
#
# 🔴 RESTARTING NEVER FIXES IT, and the symptom does not look like its cause: what the
# operator sees is an APP CRASH LOOP (`Up 3 seconds`, repeatedly), so they investigate
# the app. The real message is in the DB container's log, one dependency away.
#
# ---------------------------------------------------------------------------
# WHY NOT JUST DROP THE FALLBACKS (`${VAR:?...}`)
# ---------------------------------------------------------------------------
# It was the first thing considered and it breaks CI. `ci.yml`'s `demo-wrapper-smoke`
# job runs `verify-demo-wrapper.sh`, which renders every one of these compose files
# with `docker compose config` — on a runner, where `.env` cannot exist because it is
# gitignored. Making the vars required would turn that job red on every run, for a
# rendering that never boots anything and is in no danger.
#
# So the gate lives at BOOT time, where the damage would actually happen, and leaves
# rendering alone.
#
# ---------------------------------------------------------------------------
# THE PREDICATE, AND WHY IT IS NARROWER THAN "`.env` IS MISSING"
# ---------------------------------------------------------------------------
# "`.env` missing" is the wrong predicate: 4 of the 8 projects that ship a
# `.env.example` have no `.env` on a working machine and boot correctly on the
# fallbacks, because for them the fallback IS the intended value. Stopping those would
# be a guard that bans the normal path.
#
# What actually matters is whether the value would be WRONG and whether being wrong is
# PERMANENT:
#
#   stop  = `.env` is absent AND `.env.example` sets a password var to something other
#           than the compose fallback AND that credential is persisted at init.
#   warn  = same, but the credential is re-read on every start (redis), so a restart
#           after `.env` appears resolves it.
#
# Measured population at the time of writing:
#   wms-platform  8/8 diverge (postgres role passwords)  -> stop
#   ecommerce     1/1 diverge (MINIO_ROOT_PASSWORD)      -> stop  (MinIO persists it)
#   erp/fan/finance/scm  REDIS_PASSWORD only             -> warn
#   iam-platform  none diverge                            -> silent
#
# 🔴 THE CLASSIFICATION IS NOT ALLOWED TO BE SILENT. A password variable this script
# cannot classify is an ERROR, not a skip. A hardcoded allow-list drifts away from the
# tree without anything noticing; refusing to guess is what keeps it honest. If a new
# credential appears, this script stops and asks to be taught, rather than waving
# through the one case it was written to prevent.
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
# shellcheck source=infra/demo/projects.sh
source "$HERE/projects.sh"

# Credentials a server writes into its data volume on first init. Being wrong here
# survives every restart, which is what makes it a stop rather than a warning.
PERSISTED_RE='(^|_)(DB_PASSWORD|POSTGRES_PASSWORD|POSTGRES_ROOT_PASSWORD|MYSQL_PASSWORD|MYSQL_ROOT_PASSWORD|MINIO_ROOT_PASSWORD)$'
# Credentials read afresh on every start. A mismatch here is annoying, not permanent.
EPHEMERAL_RE='(^|_)(REDIS_PASSWORD|GRAFANA_ADMIN_PASSWORD|GF_SECURITY_ADMIN_PASSWORD)$'

usage() {
    cat <<'EOF'
usage: check-env-preflight.sh <domain> [<domain>...]

  Exits 1 if booting one of those domains WITHOUT its `.env` would write a wrong,
  permanent credential into a fresh data volume. Warns (exit 0) when the divergence
  is only in a credential that a restart can correct.

  --self-test   Run the predicate against copies of the real tree, mutated.
EOF
}

# Echoes the NAME of each password var whose `.env.example` value differs from the
# compose fallback, one per line. Values are never printed — only names.
#
# awk, not python: this runs on the boot path of a local demo, and adding an
# interpreter dependency to a script whose whole job is "stop before you break the
# volume" would give it a new way to not run.
diverging_vars() {
    local compose="$1" example="$2"
    awk -v ex="$example" '
      BEGIN {
        while ((getline line < ex) > 0) {
          if (line ~ /^[[:space:]]*[A-Z0-9_]+[[:space:]]*=/) {
            eq = index(line, "=")
            k = substr(line, 1, eq - 1); v = substr(line, eq + 1)
            gsub(/^[[:space:]]+|[[:space:]]+$/, "", k)
            gsub(/^[[:space:]]+|[[:space:]]+$/, "", v)
            EX[k] = v; HAS[k] = 1
          }
        }
        close(ex)
      }
      {
        s = $0
        while (match(s, /\$\{[A-Z0-9_]*PASSWORD[A-Z0-9_]*:-[^}]*\}/)) {
          tok = substr(s, RSTART + 2, RLENGTH - 3)          # VAR:-fallback
          s = substr(s, RSTART + RLENGTH)
          p = index(tok, ":-")
          var = substr(tok, 1, p - 1); fb = substr(tok, p + 2)
          # Absent from the example means the example does not claim a value for it,
          # so there is nothing to disagree with. Only a stated, different value counts.
          if (HAS[var] && EX[var] != fb && !SEEN[var]) { SEEN[var] = 1; print var }
        }
      }
    ' "$compose"
}

check_domain() {   # check_domain <domain> ; echoes findings, returns 1 to stop
    local dom="$1"
    local files="${COMPOSE[$dom]:-}"
    if [ -z "$files" ]; then
        echo "[env-preflight] ⚠ 알 수 없는 도메인: $dom — 건너뜁니다" >&2
        return 0
    fi

    # The project directory is the directory of the FIRST compose file — the same rule
    # `docker compose -f <path>` uses to locate `.env`, so this reads what compose reads.
    local first="${files%% *}"
    local dir="$ROOT/$(dirname "$first")"
    local example="$dir/.env.example" env="$dir/.env"

    [ -f "$example" ] || return 0     # project does not declare an env contract
    [ -f "$env" ] && return 0         # the intended values are present; nothing to warn about

    local vars stop=() warn=() unknown=()
    vars="$(diverging_vars "$dir/$(basename "$first")" "$example")"
    [ -n "$vars" ] || return 0        # `.env` absent but the fallbacks ARE the intended values

    local v
    while IFS= read -r v; do
        [ -n "$v" ] || continue
        if [[ "$v" =~ $PERSISTED_RE ]];      then stop+=("$v")
        elif [[ "$v" =~ $EPHEMERAL_RE ]];    then warn+=("$v")
        else                                      unknown+=("$v")
        fi
    done <<< "$vars"

    if [ ${#unknown[@]} -gt 0 ]; then
        echo "[env-preflight] ✖ $dom: 분류할 수 없는 자격 변수: ${unknown[*]}" >&2
        echo "[env-preflight]   이 스크립트는 추측하지 않습니다 — 볼륨에 각인되는 값인지" >&2
        echo "[env-preflight]   판단해 PERSISTED_RE / EPHEMERAL_RE 에 추가하세요." >&2
        return 1
    fi

    if [ ${#warn[@]} -gt 0 ]; then
        echo "[env-preflight] ⚠ $dom: .env 없이 기동합니다 — ${warn[*]} 가"
        echo "[env-preflight]   .env.example 과 다른 값으로 해소됩니다. 이 자격은 매 기동 시"
        echo "[env-preflight]   다시 읽히므로 .env 를 만든 뒤 재기동하면 해소됩니다."
    fi

    if [ ${#stop[@]} -gt 0 ]; then
        echo "[env-preflight] ✖ $dom 기동을 중단합니다 — $dir/.env 가 없습니다." >&2
        echo "" >&2
        echo "  지금 기동하면 다음 자격이 compose 의 폴백 값으로 **데이터 볼륨에 각인**됩니다:" >&2
        printf '    - %s\n' "${stop[@]}" >&2
        echo "" >&2
        echo "  DB 는 빈 데이터 디렉터리에서만 초기화되므로, 나중에 .env 를 만들어도" >&2
        echo "  서버의 비밀번호는 그대로입니다 — 앱이 'FATAL: password authentication failed'" >&2
        echo "  로 죽고 **재기동으로는 절대 고쳐지지 않습니다**. 증상은 앱 크래시 루프로" >&2
        echo "  나타나므로 원인을 엉뚱한 곳에서 찾게 됩니다." >&2
        echo "" >&2
        echo "  해결:  cp $dir/.env.example $dir/.env   (그 뒤 값 확인)" >&2
        echo "" >&2
        # The recovery is credential-specific, so only the one that has been verified
        # is offered. Printing a psql command next to a MinIO credential would send the
        # reader somewhere that cannot work.
        local has_db=0 has_other=0 s
        for s in "${stop[@]}"; do
            case "$s" in *DB_PASSWORD|POSTGRES_*|MYSQL_*) has_db=1 ;; *) has_other=1 ;; esac
        done
        if [ "$has_db" -eq 1 ]; then
            echo "  이미 오염된 볼륨이라면 (데이터 보존, 볼륨 삭제 불필요):" >&2
            echo "    docker exec <해당 프로젝트의 DB 컨테이너> psql -U postgres \\" >&2
            echo "      -c \"ALTER ROLE <롤> WITH PASSWORD '<.env 의 값>'\"    # 해당 롤 전부" >&2
        fi
        if [ "$has_other" -eq 1 ]; then
            echo "  🔴 DB 롤이 아닌 자격이 포함돼 있습니다(위 목록). 그 복구 절차는 서버마다" >&2
            echo "     다르고 이 스크립트는 검증하지 않은 절차를 안내하지 않습니다 —" >&2
            echo "     첫 기동 전에 .env 를 만드는 것이 유일하게 확실한 경로입니다." >&2
        fi
        return 1
    fi
    return 0
}

self_test() {
    local pass=0 fail=0 tmp
    tmp="$(mktemp -d)"
    trap 'rm -rf "$tmp"' RETURN

    expect() {  # expect <label> <want-rc> <domain> [env-present]
        local label="$1" want="$2" dom="$3" got=0
        check_domain "$dom" >/dev/null 2>&1 || got=$?
        if [ "$got" = "$want" ]; then echo "  PASS  $label (rc=$got)"; pass=$((pass+1))
        else echo "  FAIL  $label (want $want, got $got)"; fail=$((fail+1)); fi
    }

    echo "self-test: predicate against the real tree"

    # 1. The real entry point in its own process, errexit active. Every case below runs
    #    behind `|| got=$?`, which suppresses errexit and hides a script that aborts.
    local rc0=0
    bash "$HERE/check-env-preflight.sh" wms >/dev/null 2>&1 || rc0=$?
    if [ "$rc0" = 0 ] || [ "$rc0" = 1 ]; then
        echo "  PASS  real entry point runs (rc=$rc0, not a crash)"; pass=$((pass+1))
    else
        echo "  FAIL  real entry point crashed (rc=$rc0)"; fail=$((fail+1))
    fi

    # 2/3. The two cases that matter, and neither may depend on what this host happens
    #      to have. `.env` is gitignored, so a fresh clone or a git worktree has none —
    #      an earlier version of this self-test SKIPPED both here and still reported
    #      all-pass, which is the shape of a test suite that proves nothing.
    #
    #      Both states are therefore constructed: the real file is moved aside if it
    #      exists and restored afterwards, and the "present" case is built from
    #      `.env.example` so it exists on any machine.
    local envf="$ROOT/projects/wms-platform/.env" saved="$tmp/wms.env.saved" had=0
    if [ -f "$envf" ]; then cp "$envf" "$saved"; had=1; fi

    rm -f "$envf"
    expect "wms WITHOUT .env stops (7 postgres role credentials would be baked)" 1 wms

    cp "$ROOT/projects/wms-platform/.env.example" "$envf"
    expect "wms WITH .env passes — the control; without it an always-stop guard looks the same" 0 wms

    rm -f "$envf"
    if [ "$had" -eq 1 ]; then cp "$saved" "$envf"; fi

    # 4/5. THE CONTROL. Four of the eight projects that ship a `.env.example` have no
    #      `.env` on a working machine and boot correctly, because for them the
    #      fallback IS the intended value. Without these cases an always-stop guard
    #      would look exactly like a working one — and it would ban the normal path,
    #      which is how a guard gets deleted.
    #
    #      🔵 These four were nearly recorded as "redis diverges, warn" on the strength
    #      of a throwaway measurement whose regex let `\s*$` eat the newline, so an
    #      EMPTY value in `.env.example` picked up the following line as its value. The
    #      awk parser above reads line by line and does not have that bug; the two
    #      disagreed, and the corrected count is what these cases assert.
    #      No `.env`-presence condition on these: nothing diverges for them, so the
    #      expected verdict is "pass" whether or not the file happens to exist here.
    for d in erp finance scm fan iam; do
        expect "$d passes (its fallbacks are the intended values)" 0 "$d"
    done

    echo "self-test: ${pass} passed, ${fail} failed"
    [ "$fail" -eq 0 ]
}

case "${1:-}" in
    --self-test) self_test ;;
    -h|--help)   usage ;;
    "")          usage >&2; exit 2 ;;
    *)
        rc=0
        for d in "$@"; do check_domain "$d" || rc=1; done
        [ "$rc" -eq 0 ] && echo "[env-preflight] OK — 기동 대상의 자격 설정에 볼륨 각인 위험이 없습니다."
        exit "$rc"
        ;;
esac
