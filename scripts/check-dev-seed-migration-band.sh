#!/usr/bin/env bash
# =============================================================================
# check-dev-seed-migration-band.sh — TASK-MONO-524, widened by TASK-MONO-531
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
# 🔴 WHY THE PREDICATE WAS WIDENED (TASK-MONO-531 — read this before narrowing it)
# -----------------------------------------------------------------------------
# On 2026-08-14 wms admin-service crash-looped with the *same* exception
# (`not applied to database: 3.` — TASK-BE-584 AC-0: restarts=12, all eight
# dashboard surfaces 500). This guard was GREEN throughout. Two measured reasons:
#
#   (1) Its glob named `db/migration-dev` only. All four other wms services keep
#       their dev seeds in `db/seed` — 8 files that had never once been examined.
#   (2) admin-service kept its dev seed INSIDE `db/migration`, so the guard read
#       `V99__seed_dev_data.sql` as the *production ceiling*. It did not merely
#       miss the violation; it counted the violating file as the baseline it
#       compares everything else against. A guard can be wrong in that direction.
#
# So the green never meant "wms is safe" — it meant "wms was not looked at".
# The fix is not only "add one glob": a fixed list of directory names is exactly
# what failed, because nobody re-checked the list against the tree. Hence the
# LOCATION INVENTORY below — this guard now fails when a `db/<something>/`
# appears that it has no verdict for, instead of silently ignoring it.
#
# THE PREDICATE
# -----------------------------------------------------------------------------
#   for every  <svc>/db/{migration-dev,seed}/V<n>__*.sql
#       n  MUST BE  <=  max(version of <svc>/db/migration/**/V<n>__*.sql)
#
# Measured, not exempted. That matters:
#   · iam admin-service keeps three VERSIONED dev seeds (V0014/V0023/V0028) and
#     passes, because its production timeline has since reached V0045. Its seeds
#     are interleaved into reserved production gaps, which never poisons ordering
#     — the band does. That is the whole difference, and it is why admin-service
#     is untouched by TASK-MONO-524 (AC-6). No exception list is needed to say so.
#   · iam auth-service and account-service have no versioned dev seeds at all
#     (converted to R__ repeatables), so they pass vacuously — and would fail the
#     moment someone reintroduces a V9xxx file.
#   · the four wms services were converted the same way by TASK-MONO-531.
#
# R__ (repeatable) seeds are out of scope by construction: they carry no version,
# so they cannot be out of order. That is the remediation this guard points to.
#
# 🔴 NOT a name-based predicate. Treating any `*seed*` file as a dev seed was
# measured and rejected (TASK-MONO-531): `db/migration/` holds 31 files matching
# `*seed*` and most are legitimate PRODUCTION migrations (tenant / OIDC client
# reference data in iam and ecommerce). That predicate is ~30 false positives.
# Placement is the signal; the file name is not.
#
# Exit 0 = every dev seed is inside its production range. Exit 1 = drift.
# =============================================================================
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

fail=0

# ---------------------------------------------------------------------------
# Directory classification.
#
# PROD_KINDS  — merged into the production version sequence; sets the ceiling.
# DEV_KINDS   — dev-only locations, merged into the SAME sequence when a profile
#               or a compose override opens them. These are what get judged.
# INERT_KINDS — known, deliberately NOT judged. Each needs a measured reason:
#   · migration-h2 — ecommerce product-service `application-local.yml` sets
#     `locations: classpath:db/migration-h2` ALONE (it replaces db/migration
#     rather than adding to it), so the two never share one sequence and cannot
#     be out of order relative to each other. Verified 2026-08-15.
#
# Anything else under src/main/resources/db/ is an error, not a skip — see the
# LOCATION INVENTORY below.
# ---------------------------------------------------------------------------
PROD_KINDS=(migration)
DEV_KINDS=(migration-dev seed)
INERT_KINDS=(migration-h2)

kind_class() {
    local k="$1" x
    for x in "${PROD_KINDS[@]}";  do [ "$k" = "$x" ] && { echo prod;  return; }; done
    for x in "${DEV_KINDS[@]}";   do [ "$k" = "$x" ] && { echo dev;   return; }; done
    for x in "${INERT_KINDS[@]}"; do [ "$k" = "$x" ] && { echo inert; return; }; done
    echo unknown
}

# ---------------------------------------------------------------------------
# LOCATION INVENTORY (TASK-MONO-531).
#
# The way this guard failed was not a bug in its comparison — the comparison was
# right. It was that its list of directory names had drifted from the tree and
# nothing could notice. So: enumerate every `db/<kind>/` that exists and fail on
# any kind this script has no verdict for. A new dev-seed location can then only
# arrive loudly.
#
# Scoped to src/main/resources on purpose: src/test/resources holds deliberate
# fixtures (erp approval `db/collision/`, iam auth `db/h2/`) that must never be
# judged as shipping migrations. Measured — those are the only two, and neither
# is a `migration` directory, so this scoping changes no pre-existing verdict.
# ---------------------------------------------------------------------------
mapfile -t kinds < <(git ls-files --cached --others --exclude-standard \
    '*/src/main/resources/db/*' \
    | sed -nE 's#.*/src/main/resources/db/([^/]+)/.*#\1#p' | sort -u)

if [ "${#kinds[@]}" -eq 0 ]; then
    echo "✗ src/main/resources/db/ 아래에서 아무 디렉터리도 찾지 못했습니다."
    echo "  이 저장소에는 존재합니다 ⇒ 계측 실패입니다. git ls-files 글롭을 확인하십시오."
    exit 1
fi

for k in "${kinds[@]}"; do
    if [ "$(kind_class "$k")" = unknown ]; then
        echo "✗ 분류되지 않은 마이그레이션 위치: db/$k/"
        echo "  이 가드는 모르는 위치를 건너뛰지 않습니다 — 조용히 건너뛴 것이 정확히"
        echo "  TASK-MONO-531 이 고치는 결함입니다(db/seed/ 8개 파일이 그렇게 무검사였습니다)."
        echo "  이 위치가 production 버전 순서에 합류하면 PROD_KINDS/DEV_KINDS 에,"
        echo "  합류하지 않는다면 INERT_KINDS 에 **근거를 적어** 추가하십시오."
        fail=1
    fi
done

# One git call. No per-file subprocesses in the loop below — this repo runs on
# msys, where a spawn-per-file loop over the tree reads as a hang.
#
# --others --exclude-standard on purpose: the arrival path this guard polices is
# somebody ADDING a new dev seed, and a plain `git ls-files` would not see that
# file until it is staged. CI would still catch it (everything is tracked after
# checkout), but the author running the guard locally before staging would get a
# green that means nothing. Untracked-but-not-ignored files count.
mapfile -t files < <(git ls-files --cached --others --exclude-standard \
    '*/src/main/resources/db/*/*.sql')

declare -A prod_max=()      # <svc-root>  ->  highest production version
declare -A dev_dirs=()      # <svc-root>  ->  comma-joined dev kinds seen there
declare -A prod_dirs=()     # <svc-root>  ->  1, seen a production migration file
declare -A kind_seen=()     # kind        ->  1, at least one file found
dev_versioned=()            # "<svc-root>|<n>|<kind>/<basename>"

for f in "${files[@]}"; do
    # <svc-root>/src/main/resources/db/<kind>/[<sub>/]<file>.sql — the kind is the
    # segment directly under db/, NOT the parent directory of the file.
    # fan-platform and scm-platform nest their production migrations one level
    # deeper (`db/migration/artist`, `db/migration/procurement`, … — their
    # `spring.flyway.locations` names the subdirectory). The old `${dir##*/}`
    # extraction read those as kind=artist / kind=procurement and dropped them
    # silently, so 21 production migrations never counted toward any ceiling.
    [[ $f =~ ^(.*)/src/main/resources/db/([^/]+)/(.*)$ ]] || continue
    svc="${BASH_REMATCH[1]}"
    kind="${BASH_REMATCH[2]}"
    base="${f##*/}"
    kind_seen["$kind"]=1
    class="$(kind_class "$kind")"

    case "$class" in
        prod)  prod_dirs["$svc"]=1 ;;
        dev)   case ",${dev_dirs[$svc]:-}," in
                   *",$kind,"*) : ;;
                   *) dev_dirs["$svc"]="${dev_dirs[$svc]:+${dev_dirs[$svc]},}$kind" ;;
               esac ;;
        *)     continue ;;
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

    if [ "$class" = prod ]; then
        cur="${prod_max[$svc]:-0}"
        if [ "$n" -gt "$cur" ]; then prod_max["$svc"]=$n; fi
    else
        dev_versioned+=("$svc|$n|$kind/$base")
    fi
done

# ---------------------------------------------------------------------------
# 0건은 통과가 아니라 계측 실패다. 글롭이 어긋나거나 디렉터리가 이동하면 이 가드는
# 아무것도 못 찾고 조용히 초록이 된다 — 그 상태와 "모두 정상" 은 구별되어야 한다.
#
# 🔴 dev 위치마다 **따로** 센다. 예전에는 migration-dev 하나만 셌기 때문에, seed 를
# 한 건도 못 찾는 상태에서도 iam 의 migration-dev 3개가 이 검사를 통과시켰다.
# 그것이 wms 8개 파일이 무검사로 남은 방식이다 — 집계된 0 아님은 위치별 0 을 가린다.
# ---------------------------------------------------------------------------
for k in "${DEV_KINDS[@]}"; do
    if [ -z "${kind_seen[$k]:-}" ]; then
        echo "✗ db/$k/ 에서 파일을 하나도 찾지 못했습니다."
        echo "  이 저장소에는 존재합니다(migration-dev = iam auth/account/admin,"
        echo "  seed = wms master/inbound/inventory/outbound) ⇒ 못 찾은 것은 계측"
        echo "  실패입니다. git ls-files 글롭을 확인하십시오."
        exit 1
    fi
done

echo "[dev-seed-band] 검사 대상 dev 시드 디렉터리 ${#dev_dirs[@]}개:"
for svc in $(printf '%s\n' "${!dev_dirs[@]}" | sort); do
    echo "  · ${svc##*/apps/}  [${dev_dirs[$svc]}]  (production 최고 = V$(printf '%04d' "${prod_max[$svc]:-0}"))"
done

for svc in "${!dev_dirs[@]}"; do
    if [ -z "${prod_dirs[$svc]:-}" ]; then
        echo "✗ ${svc##*/apps/}/db/${dev_dirs[$svc]} 는 있는데 형제 db/migration 이 없습니다."
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
        echo "✗ ${svc##*/apps/}/db/${base}"
        echo "  dev 시드 버전 $n 이 production 최고 버전 $max 을 넘습니다."
        echo "  이러면 이 시드를 적용한 DB 에서 이후의 모든 production 마이그레이션이"
        echo "  out-of-order 가 되어 Flyway 가 기동을 거부합니다(TASK-MONO-524 실측,"
        echo "  wms admin-service 에서 TASK-BE-584 가 재확인)."
        echo "  해결: 이 파일을 R__ (repeatable) 로 만드십시오 — 버전이 없으므로 충돌도"
        echo "  순서 위반도 불가능하고, 항상 versioned 마이그레이션 뒤에 실행됩니다."
        echo "  🔴 한 디렉터리에 여러 개면 FK 순서를 R__NN_ 접두사로 고정하십시오"
        echo "     (repeatable 은 description 사전순으로 실행됩니다)."
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
