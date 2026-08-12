#!/usr/bin/env bash
# =============================================================================
# check-erp-single-tenant-ratchet.sh — ADR-ERP-001 — D 래칫의 **라이브 절반**
# (TASK-ERP-BE-043 AC-7)
# =============================================================================
# ADR-ERP-001 — D 는 이벤트 평면의 테넌트 **거부**를 걷어내고 그 자리에 사후 탐지를
# 놓았다: *"erp 전체에서 distinct `tenant_id` 가 2 이상이면 RED — 그때가 Option B
# (다중 테넌트 승격)를 다시 논의할 시점이다."*  거부를 없애면서 탐지를 안 놓으면
# 교환의 한쪽만 실행한 것이다.
#
# ── 래칫은 두 개다. 서로의 대체물이 아니다 ────────────────────────────────────
#
#   (1) CI 절반 — `SingleTenantRatchetIntegrationTest` (read-model + notification,
#       `erp-integration-tests` 레인, Testcontainers 실 MySQL). 모든 erp PR 에서 돈다.
#       보는 것: **코드가** 사실 옆에 상수를 찍어 두 번째 테넌트를 만드는 경우
#       (이 티켓이 찾은 바로 그 모양 — `delegation_fact_proj.tenant_id` 가 매핑되지
#       않아 DDL `DEFAULT 'erp'` 를 먹는 반면 원본 grant 는 `demo-corp` 였다).
#       못 보는 것: 런타임에 들어온 테넌트. CI 에는 살아 있는 erp DB 가 없다.
#
#   (2) 이 스크립트 — 살아 있는 스택에 대고 **네 개 erp 스키마 전부**를 실측한다.
#       보는 것: 운영자가 다른 erp-entitled 테넌트를 assume 해서 API 로 쓴 경우
#       (= ADR 이 "그때 Option B 를 다시 논의하라" 고 말한 바로 그 사건).
#       못 보는 것: 스택이 안 떠 있으면 아무것도 못 본다(그때는 SKIP 이 아니라 실패다 —
#       아래 참조).
#
# ── 0건은 통과가 아니다 ───────────────────────────────────────────────────────
# `tenant_id` 컬럼을 가진 테이블을 하나도 못 찾으면 그것은 "위반 없음" 이 아니라
# **계측 실패**다(글롭/스키마명이 틀렸거나 마이그레이션이 안 돌았거나). exit 1.
#
# 테이블 목록을 손으로 적지 않는다 — `information_schema` 에 물어본다. 손으로 적은
# 목록은 일곱 번째 테이블이 생기는 날 조용히 그것을 빼놓는다.
#
# 사용:  scripts/check-erp-single-tenant-ratchet.sh
# 환경:  ERP_MYSQL_CONTAINER (기본 erp-platform-mysql) · MYSQL_ROOT_PASSWORD (기본 root)
# =============================================================================
set -uo pipefail

CONTAINER="${ERP_MYSQL_CONTAINER:-erp-platform-mysql}"
ROOT_PW="${MYSQL_ROOT_PASSWORD:-root}"

# erp 가 소유한 스키마 전부. 스키마 자체는 고정 집합이라 여기 적지만, 그 안의
# **테이블**은 아래에서 information_schema 로 발견한다.
SCHEMAS="'erp_db','erp_read_model_db','erp_approval_db','erp_notification_db'"

# 🔴 `%s` 하나로만 찍는다 — 호출자가 포맷 문자열을 넘기지 않는다는 뜻이다. 첫 판은
#    발화 메시지에 `%d` 를 넣고 인자를 붙였는데, fail 이 `$*` 를 **값**으로 받으므로
#    `%d` 가 그대로 출력되고 숫자가 문장 끝에 붙었다(bite 실행에서 실측). 메시지는
#    호출 전에 완성해서 넘긴다.
fail() { printf '✗ %s\n' "$*" >&2; exit 1; }

command -v docker >/dev/null 2>&1 || fail "docker 가 없습니다 — 이 스크립트는 살아 있는 스택을 재는 용도입니다."

if ! docker ps --format '{{.Names}}' | grep -qx "$CONTAINER"; then
    fail "컨테이너 '$CONTAINER' 가 떠 있지 않습니다.
    이것은 SKIP 이 아니라 실패입니다 — 안 떠 있으면 래칫은 '위반 없음' 이 아니라
    '아무것도 재지 못함' 입니다. 스택을 올리고 다시 실행하세요:
      bash infra/demo/demo-up.sh iam erp console"
fi

mysql_q() {
    docker exec "$CONTAINER" mysql -uroot -p"$ROOT_PW" -N -B -e "$1" 2>/dev/null
}

# --- 1) tenant_id 컬럼을 가진 테이블 발견 ------------------------------------
TABLES_SQL="SELECT CONCAT(table_schema,'.',table_name)
            FROM information_schema.columns
            WHERE column_name = 'tenant_id' AND table_schema IN ($SCHEMAS)
            ORDER BY 1"
TABLES="$(mysql_q "$TABLES_SQL")"
rc=$?
[ "$rc" -eq 0 ] || fail "information_schema 조회 실패 (rc=$rc) — 계측 실패이지 통과가 아닙니다."

TABLE_COUNT="$(printf '%s\n' "$TABLES" | grep -c '[^[:space:]]')"
if [ "$TABLE_COUNT" -eq 0 ]; then
    fail "erp 스키마에서 tenant_id 컬럼을 가진 테이블을 **하나도** 찾지 못했습니다.
    0건은 '위반 없음' 이 아니라 계측 실패입니다 — 스키마명/마이그레이션을 확인하세요."
fi
printf 'tenant_id 컬럼 보유 테이블: %d개\n' "$TABLE_COUNT"

# --- 2) 전 테이블의 distinct tenant_id 합집합 --------------------------------
UNION=""
while read -r qualified; do
    [ -n "$qualified" ] || continue
    schema="${qualified%%.*}"; table="${qualified#*.}"
    part="SELECT DISTINCT tenant_id AS t, '$qualified' AS src FROM \`$schema\`.\`$table\`
          WHERE tenant_id IS NOT NULL"
    if [ -z "$UNION" ]; then UNION="$part"; else UNION="$UNION UNION ALL $part"; fi
done <<EOF
$TABLES
EOF

ROWS="$(mysql_q "SELECT t, GROUP_CONCAT(DISTINCT src ORDER BY src SEPARATOR ' ')
                 FROM ($UNION) u GROUP BY t ORDER BY t")"
rc=$?
[ "$rc" -eq 0 ] || fail "테넌트 집계 조회 실패 (rc=$rc) — 계측 실패이지 통과가 아닙니다."

DISTINCT="$(printf '%s\n' "$ROWS" | grep -c '[^[:space:]]')"

printf '\n실측 — erp 전체의 distinct tenant_id: %d\n' "$DISTINCT"
printf '%s\n' "$ROWS" | sed 's/^/  /'

# --- 3) 판정 -----------------------------------------------------------------
if [ "$DISTINCT" -eq 0 ]; then
    fail "테넌트 값을 담은 행이 **한 건도** 없습니다.
    0건은 통과가 아닙니다 — 시드가 안 돌았거나 잘못된 스택을 보고 있습니다."
fi

if [ "$DISTINCT" -ge 2 ]; then
    printf '\n' >&2
    fail "🔴 ADR-ERP-001 — D 래칫 발화: erp 에 서로 다른 tenant_id 가 ${DISTINCT}개 있습니다.

    이것은 '고쳐야 할 버그' 가 아니라 **결정을 다시 열라는 신호**입니다.
    D 는 이벤트 평면의 테넌트 거부를 사후 탐지와 맞바꾸면서, 울리는 그 순간이
    Option B(erp 를 다중 테넌트로 승격 — PROJECT.md traits · 읽기 테넌트 필터 ·
    프로젝션 스키마 마이그레이션)를 다시 논의할 시점이라고 명시했습니다.
    → projects/erp-platform/docs/adr/ADR-001-erp-event-plane-tenant-axis.md"
fi

printf '\n✓ erp 는 여전히 단일 테넌트입니다 (distinct tenant_id = 1). ADR-ERP-001 — D 유지.\n'
