#!/usr/bin/env bash
# =============================================================================
# check-dev-seed-migration-band.sh — TASK-MONO-524
#
# A dev-only Flyway seed must never carry a version ABOVE the highest version in
# its sibling production db/migration directory.
#
# WHY THIS EXISTS (measured, not theoretical)
# -----------------------------------------------------------------------------
# iam auth-service put its dev seeds in a high "V9000+ band" (TASK-BE-571,
# following TASK-MONO-207) so they could never collide with a production version
# number. Under the e2e profile Flyway merges db/migration and db/migration-dev
# into ONE version sequence, so once V9001 is applied it becomes the highest
# APPLIED version — and every production migration added afterwards resolves
# BELOW it. That is an out-of-order migration, which Flyway rejects by default:
#
#   Validate failed: Detected resolved migration not applied to database: 0032.
#
# auth-service crash-looped on exactly that on 2026-08-11, on every host with an
# existing volume. account-service was one migration away from the same fate.
#
# 🔴 CI IS STRUCTURALLY BLIND TO THIS. CI and any fresh start create an empty
# volume, where all versions apply in ascending order and nothing is ever out of
# order. Only a database that already applied the band breaks. So this guard is
# deliberately a check on FILE PLACEMENT, not on runtime behaviour — no runtime
# test in this repo can fail on the defect.
#
# THE PREDICATE
# -----------------------------------------------------------------------------
#   for every  <svc>/db/migration-dev/V<n>__*.sql
#       n  MUST BE  <=  max(version of <svc>/db/migration/V<n>__*.sql)
#
# Measured, not exempted. That matters:
#   · iam admin-service keeps three VERSIONED dev seeds (V0014/V0023/V0028) and
#     passes, because its production timeline has since reached V0045. Its seeds
#     are interleaved into reserved production gaps, which never poisons ordering
#     — the band does. That is the whole difference, and it is why admin-service
#     is untouched by TASK-MONO-524 (AC-6). No exception list is needed to say so.
#   · auth-service and account-service now have no versioned dev seeds at all
#     (converted to R__ repeatables), so they pass vacuously — and would fail the
#     moment someone reintroduces a V9xxx file.
#
# R__ (repeatable) seeds are out of scope by construction: they carry no version,
# so they cannot be out of order. That is the remediation this guard points to.
#
# Exit 0 = every dev seed is inside its production range. Exit 1 = drift.
# =============================================================================
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

fail=0

# One git call. No per-file subprocesses in the loop below — this repo runs on
# msys, where a spawn-per-file loop over the tree reads as a hang.
#
# --others --exclude-standard on purpose: the arrival path this guard polices is
# somebody ADDING a new dev seed, and a plain `git ls-files` would not see that
# file until it is staged. CI would still catch it (everything is tracked after
# checkout), but the author running the guard locally before staging would get a
# green that means nothing. Untracked-but-not-ignored files count.
mapfile -t files < <(git ls-files --cached --others --exclude-standard \
    '*/db/migration/*.sql' '*/db/migration-dev/*.sql')

declare -A prod_max=()      # <svc>/db  ->  highest production version
declare -A dev_dirs=()      # <svc>/db  ->  1, seen a migration-dev file (any kind)
declare -A prod_dirs=()     # <svc>/db  ->  1, seen a migration file (any kind)
dev_versioned=()            # "<svc>/db|<n>|<basename>"

for f in "${files[@]}"; do
    base="${f##*/}"
    dir="${f%/*}"           # .../db/migration | .../db/migration-dev
    kind="${dir##*/}"
    svc="${dir%/*}"         # .../db

    case "$kind" in
        migration)     prod_dirs["$svc"]=1 ;;
        migration-dev) dev_dirs["$svc"]=1 ;;
        *)             continue ;;
    esac

    # Repeatable migrations carry no version — nothing to order against.
    [[ $base == R__* ]] && continue
    [[ $base == V* ]] || continue

    ver="${base#V}"
    ver="${ver%%__*}"
    if [[ ! $ver =~ ^[0-9]+$ ]]; then
        # Fail closed: an unparseable version means this guard cannot judge the
        # file, and silently skipping it is how a guard passes for a wrong reason.
        echo "✗ $f"
        echo "  버전 '$ver' 을 숫자로 읽을 수 없습니다. 이 가드는 판정할 수 없는 파일을"
        echo "  건너뛰지 않습니다 — 이름을 V<숫자>__… 로 맞추거나 가드를 확장하십시오."
        fail=1
        continue
    fi
    n=$((10#$ver))

    if [ "$kind" = "migration" ]; then
        cur="${prod_max[$svc]:-0}"
        if [ "$n" -gt "$cur" ]; then prod_max["$svc"]=$n; fi
    else
        dev_versioned+=("$svc|$n|$base")
    fi
done

# ---------------------------------------------------------------------------
# 0건은 통과가 아니라 계측 실패다. 글롭이 어긋나거나 디렉터리가 이동하면 이 가드는
# 아무것도 못 찾고 조용히 초록이 된다 — 그 상태와 "모두 정상" 은 구별되어야 한다.
# ---------------------------------------------------------------------------
if [ "${#dev_dirs[@]}" -eq 0 ]; then
    echo "✗ db/migration-dev 디렉터리를 하나도 찾지 못했습니다."
    echo "  이 저장소에는 존재합니다(iam auth/account/admin) ⇒ 파일을 못 찾은 것은"
    echo "  계측 실패입니다. git ls-files 글롭을 확인하십시오."
    exit 1
fi

echo "[dev-seed-band] 검사 대상 migration-dev 디렉터리 ${#dev_dirs[@]}개:"
for svc in $(printf '%s\n' "${!dev_dirs[@]}" | sort); do
    echo "  · ${svc%/src/main/resources/db}  (production 최고 = V$(printf '%04d' "${prod_max[$svc]:-0}"))"
done

for svc in "${!dev_dirs[@]}"; do
    if [ -z "${prod_dirs[$svc]:-}" ]; then
        echo "✗ ${svc}/migration-dev 는 있는데 형제 ${svc}/migration 이 없습니다."
        echo "  비교 대상이 없으면 판정할 수 없습니다(fail-closed)."
        fail=1
    fi
done

for entry in "${dev_versioned[@]}"; do
    svc="${entry%%|*}"
    rest="${entry#*|}"
    n="${rest%%|*}"
    base="${rest#*|}"
    max="${prod_max[$svc]:-0}"
    if [ "$n" -gt "$max" ]; then
        echo "✗ ${svc}/migration-dev/${base}"
        echo "  dev 시드 버전 $n 이 production 최고 버전 $max 을 넘습니다."
        echo "  이러면 이 시드를 적용한 DB 에서 이후의 모든 production 마이그레이션이"
        echo "  out-of-order 가 되어 Flyway 가 기동을 거부합니다(TASK-MONO-524 실측)."
        echo "  해결: 이 파일을 R__ (repeatable) 로 만드십시오 — 버전이 없으므로 충돌도"
        echo "  순서 위반도 불가능하고, 항상 versioned 마이그레이션 뒤에 실행됩니다."
        echo "  참고: projects/iam-platform/docs/flyway-dev-seed-migrations.md"
        fail=1
    fi
done

if [ "$fail" -ne 0 ]; then
    echo
    echo "[dev-seed-band] FAIL"
    exit 1
fi

echo "[dev-seed-band] OK — 버전 있는 dev 시드 ${#dev_versioned[@]}개 전부 production 범위 안"
