#!/usr/bin/env bash
# =============================================================================
# check-project-adr-index-drift.sh — TASK-MONO-525
#
# 프로젝트 내부 ADR(`projects/<name>/docs/adr/ADR-*.md`)을 형제 `README.md` 색인과
# 대조한다. 루트 ADR 이 `check-adr-index-drift.sh` 로 이미 갖고 있는 보증을 같은
# 성질로 프로젝트에도 준다.
#
# WHY THIS EXISTS
# -----------------------------------------------------------------------------
# `ADR-ERP-001` 을 Proposed → Accepted 로 넘기는 PR(#3287)에서 `check-adr-index-drift`
# 잡이 **skip** 됐다. 경로 필터 탓으로 의심했지만 물려 보니 아니었다 — README 행을
# 되돌린 채 그 가드를 돌려도 `rc=0` 이었다. 스크립트가 프로젝트 ADR 을 **명시적 비-대상**
# 으로 적어 뒀기 때문이다(그 파일 § WHAT THIS SCRIPT DOES NOT GUARD).
# ⇒ skip 은 정상 동작이었고, 결함은 **그 모집단을 보는 가드가 하나도 없다**는 것이었다.
#
# 실측(2026-08-13): 프로젝트 ADR 23개 중 비교 가능한 것은 전부 일치(드리프트 0).
# 🔵 **0건이 "가드가 필요 없다"는 뜻은 아니다.** 지금 안 틀렸을 뿐이고, 틀려도 아무것도
#    울리지 않는다. 그리고 `iam-platform` 은 ADR 6개에 색인이 **아예 없었다** — 어느
#    색인에도 없는 결정 6건. 그것이 이 가드가 처음 잡은 것이다.
#
# 왜 루트 스크립트를 확장하지 않고 새로 쓰는가 (AC-3 근거)
# -----------------------------------------------------------------------------
#   1. **id 가 전역 유일하지 않다.** 루트는 `ADR-MONO-<n>` 하나의 이름공간이지만
#      프로젝트는 `ADR-001` 이 다섯 개 있다(ecommerce/erp/fan/finance/scm). 루트
#      스크립트의 id-keyed 맵을 그대로 쓰면 서로 덮어쓴다.
#   2. **색인의 위치와 모양이 다르다.** 루트는 단일 `docs/adr/INDEX.md`(4열, Date 열
#      포함), 프로젝트는 **디렉터리마다 README.md**(3열, Date 열 없음).
#   3. 루트 스크립트는 Date 축을 함께 검사한다 — 프로젝트 README 에는 그 열이 없으므로
#      조건 분기가 스크립트 전체에 퍼진다.
# ⇒ 두 모집단은 "같은 규칙의 두 사례" 가 아니라 **다른 자료 구조**다. 루트 스크립트의
#   § WHAT THIS SCRIPT DOES NOT GUARD 주석은 이 파일을 가리키도록 갱신했다 — 안 그러면
#   다음 사람이 "제외돼 있다" 는 주석만 읽고 여기까지 오지 않는다.
#
# 검사 항목
# -----------------------------------------------------------------------------
#   index    (dir)  ADR 이 있는 디렉터리는 README.md 를 가져야 한다  ← iam 이 걸린 항목
#   declared (file) 모든 ADR 이 Status 헤더를 선언한다
#   notation (file) 그 헤더가 정규형 `**Status:**` 다 (아래 참조)
#   forward  (file -> row) 모든 ADR 이 README 에 행을 갖는다
#   reverse  (row -> file) 모든 행이 실재하는 파일을 가리킨다
#   status   (row == file) 상태 칸이 ADR 본문의 Status 와 일치한다
#
# 🔴 표기(notation) 를 파싱과 분리한 이유: **읽기는 관용적으로, 강제는 명시적으로.**
#    Status 를 읽는 정규식이 정규형만 읽으면, 옛 표기로 쓴 ADR 이 "Status 없음" 이라는
#    **거짓 부재**로 잡힌다 — 이 티켓의 배경이 정확히 그 사고다(#3287 PR 본문이
#    "iam/ADR-001 은 Status 헤더가 없다" 고 적었는데, 있었고 정규식이 못 읽은 것이었다).
#    그래서 파서는 알려진 세 형식을 **전부** 읽고, 정규형이 아니면 별도 항목으로 고발한다.
#
# 상태 값 정규화 규칙 (Edge Case: `Accepted — **D**` vs `**Accepted — D**`)
# -----------------------------------------------------------------------------
#   1. `**` `~~` `` ` `` 를 제거한다 (README 는 상태를 볼드로 강조하고 본문은 옵션 글자만
#      볼드로 쓰는 등 강조 위치가 자유롭다 — 강조는 의미가 아니다)
#   2. 첫 알파벳 토큰을 대문자로 (`ACCEPTED (2026-06-14)` → `ACCEPTED`,
#      `Superseded by ADR-MONO-031` → `SUPERSEDED`)
#   3. **em-dash 뒤에 홀로 선 대문자 한 글자**가 있으면 옵션으로 붙인다
#      (`Accepted — D` → `ACCEPTED-D`). 이것이 없으면 README 가 `— A` 인데 본문이
#      `— D` 여도 통과한다 — 이 저장소에서 옵션 글자는 결정의 일부다.
#      🔴 하이픈이 아니라 **em-dash 만** 본다. 하이픈까지 보면 `ADR-MONO-031` 의 `-M` 을
#      옵션으로 읽는다(실제로 그렇게 짜면 ecommerce ADR-003 이 오탐이 된다).
#      🔴 뒤에 글자가 더 오면 옵션이 아니다(`— Phase 2 …` 의 `P`).
#
# 사용법:
#   check-project-adr-index-drift.sh              저장소 전체
#   check-project-adr-index-drift.sh <ROOT>       지정한 루트만 (자기검증이 쓴다)
#   check-project-adr-index-drift.sh --self-test  술어가 무는지 자기검증
#
# Exit 0 = 일치, 1 = 드리프트.
# =============================================================================
set -uo pipefail

SELF="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/$(basename "${BASH_SOURCE[0]}")"

# 세 형식을 전부 읽는 관용 정규식. 정규형 강제는 별도 항목(notation)이다.
STATUS_ANY='^[[:space:]]*(-[[:space:]]+)?\*\*Status(:\*\*|\*\*:)'
STATUS_CANON='^[[:space:]]*(-[[:space:]]+)?\*\*Status:\*\*'

# 상태 값 정규화 — 위 § 규칙 1~3.
normalize_status() {
    local s="$1" head opt
    s="${s//\*\*/}"
    s="${s//\~\~/}"
    s="${s//\`/}"
    head="$(printf '%s' "$s" | grep -oE '[A-Za-z]+' | head -1 | tr '[:lower:]' '[:upper:]')"
    opt="$(printf '%s' "$s" | grep -oE '—[[:space:]]*[A-Z]([^A-Za-z]|$)' | head -1 \
             | grep -oE '[A-Z]' | head -1)"
    if [ -n "$opt" ]; then printf '%s-%s' "$head" "$opt"; else printf '%s' "$head"; fi
}

check_root() {
    local root="$1"
    local fail=0 adr_count=0 dir_count=0

    # 디렉터리 열거 — glob 만 쓴다. git ls-files 는 자기검증의 임시 트리에서 못 쓰고,
    # `find` 는 이 호스트(msys)에서 큰 트리를 훑으면 죽는다.
    local dirs=()
    local d
    for d in "$root"/projects/*/docs/adr; do
        [ -d "$d" ] || continue
        dirs+=("$d")
    done

    if [ "${#dirs[@]}" -eq 0 ]; then
        echo "✗ projects/*/docs/adr 디렉터리를 하나도 찾지 못했습니다."
        echo "  이 저장소에는 존재합니다 ⇒ 못 찾은 것은 계측 실패입니다(글롭/경로 확인)."
        return 1
    fi

    for d in "${dirs[@]}"; do
        local proj rel readme
        proj="$(basename "$(dirname "$(dirname "$d")")")"
        rel="projects/$proj/docs/adr"

        # --- 이 디렉터리의 ADR 파일 ---
        local files=() f
        for f in "$d"/ADR-*.md; do
            [ -e "$f" ] || continue
            files+=("$f")
        done
        [ "${#files[@]}" -gt 0 ] || continue     # ADR 이 없는 디렉터리는 대상이 아니다
        dir_count=$((dir_count + 1))
        adr_count=$((adr_count + ${#files[@]}))

        readme="$d/README.md"
        if [ ! -r "$readme" ]; then
            echo "✗ NO-INDEX  $rel — ADR ${#files[@]}개가 있는데 README.md(색인)가 없습니다."
            echo "  루트 docs/adr/INDEX.md 는 자기 서문에서 monorepo-level 로 범위를 한정하므로"
            echo "  이 ADR 들은 **어느 색인에도 없습니다** — 검색하는 사람은 '결정된 적 없다' 는"
            echo "  답을 받습니다. 형제 프로젝트 README 와 같은 형식(| # | 제목 | 상태 |)으로"
            echo "  만드십시오(TASK-MONO-525 AC-2)."
            fail=1
            continue
        fi

        # --- 파일 쪽: id -> 정규화 상태 ---
        local ids=() norms=()
        for f in "${files[@]}"; do
            local base id line raw
            base="$(basename "$f")"
            id="$(printf '%s' "$base" | grep -oE '^ADR-[0-9]+[a-z]?')"
            line="$(tr -d '\r' < "$f" | grep -m1 -E "$STATUS_ANY")"
            if [ -z "$line" ]; then
                echo "✗ NO-STATUS $rel/$base — Status 헤더가 없습니다."
                echo "  정규형: '**Status:** <VALUE>' (콜론을 볼드 안에)."
                fail=1
                continue
            fi
            if ! printf '%s' "$line" | grep -qE "$STATUS_CANON"; then
                echo "✗ NOTATION  $rel/$base — Status 헤더 표기가 정규형이 아닙니다."
                echo "  현재: $(printf '%s' "$line" | cut -c1-40)"
                echo "  정규형: '**Status:**' — 콜론이 볼드 **안**에 와야 합니다."
                echo "  표기가 갈리면 Status 를 읽는 도구가 **거짓 부재**를 냅니다(TASK-MONO-525 배경)."
                fail=1
            fi
            raw="$(printf '%s' "$line" | sed -E "s/$STATUS_ANY//")"
            ids+=("$id")
            norms+=("$(normalize_status "$raw")")
        done

        # --- README 쪽: 상태 열을 헤더 행에서 찾는다 (열 인덱스 하드코딩 금지) ---
        local body col
        body="$(tr -d '\r' < "$readme")"
        col="$(printf '%s\n' "$body" | awk -F'|' '
            /^\|/ {
                for (i = 2; i < NF; i++) {
                    c = $i
                    gsub(/^[ \t]+|[ \t]+$/, "", c)
                    if (c == "상태" || tolower(c) == "status") { print i; exit }
                }
            }')"
        local ncol
        ncol="$(printf '%s\n' "$body" | awk -F'|' '
            /^\|/ {
                for (i = 2; i < NF; i++) {
                    c = $i
                    gsub(/^[ \t]+|[ \t]+$/, "", c)
                    if (c == "상태" || tolower(c) == "status") { print NF; exit }
                }
            }')"
        if [ -z "$col" ]; then
            echo "✗ NO-COLUMN $rel/README.md — 표에서 '상태'(또는 'Status') 헤더 열을 찾지 못했습니다."
            echo "  열 위치를 하드코딩하지 않으므로 헤더가 없으면 판정할 수 없습니다(fail-closed)."
            fail=1
            continue
        fi

        # 행 파싱: | [ADR-001](file.md) | 제목 | 상태 |
        local rows
        rows="$(printf '%s\n' "$body" | awk -F'|' -v col="$col" -v ncol="$ncol" '
            /^\| *\[ADR-[0-9]/ {
                if (NF != ncol) { print "__BADCOLS__", NF; next }
                if (match($2, /ADR-[0-9]+[a-z]?/)) {
                    id = substr($2, RSTART, RLENGTH)
                    href = ""
                    if (match($2, /\(([^)]+)\)/)) href = substr($2, RSTART + 1, RLENGTH - 2)
                    s = $col
                    gsub(/^[ \t]+|[ \t]+$/, "", s)
                    print id, href, s
                }
            }')"

        if printf '%s\n' "$rows" | grep -q '^__BADCOLS__'; then
            echo "✗ BAD-ROW   $rel/README.md — 헤더가 선언한 열 수와 다른 ADR 행이 있습니다."
            echo "  셀 안의 이스케이프되지 않은 '|' 가 흔한 원인입니다. 열 위치를 추측하지 않고"
            echo "  실패로 끝냅니다 — 추측하면 한 프로젝트에서만 조용히 틀립니다."
            fail=1
            continue
        fi

        # --- forward: file -> row ---
        local i
        for i in "${!ids[@]}"; do
            if ! printf '%s\n' "$rows" | grep -qE "^${ids[$i]} "; then
                echo "✗ MISSING   $rel/${ids[$i]} — ADR 파일은 있는데 README 에 행이 없습니다."
                echo "  색인에서 이 결정을 찾는 사람은 '결정된 적 없다' 는 답을 받습니다."
                fail=1
            fi
        done

        # --- reverse + status ---
        local rid rhref rstatus
        while read -r rid rhref rstatus; do
            [ -n "$rid" ] || continue
            if [ -n "$rhref" ] && [ ! -e "$d/$rhref" ]; then
                echo "✗ PHANTOM   $rel/$rid — README 행이 가리키는 '$rhref' 파일이 없습니다."
                fail=1
                continue
            fi
            local fnorm=""
            for i in "${!ids[@]}"; do
                if [ "${ids[$i]}" = "$rid" ]; then fnorm="${norms[$i]}"; break; fi
            done
            if [ -z "$fnorm" ]; then
                echo "✗ PHANTOM   $rel/$rid — README 에 행이 있는데 대응하는 ADR 파일이 없습니다."
                fail=1
                continue
            fi
            local rnorm
            rnorm="$(normalize_status "$rstatus")"
            if [ "$rnorm" != "$fnorm" ]; then
                echo "✗ STATUS    $rel/$rid — README 는 '$rstatus'(→ $rnorm), 파일은 '$fnorm' 입니다."
                echo "  **파일이 권위입니다** — 행을 고치십시오, ADR 을 고치지 마십시오."
                echo "  (ACCEPTED 승격은 사람의 게이트다 — platform/architecture-decision-rule.md"
                echo "   § The ACCEPTED Gate. 색인이 앞서가면 그것은 결정을 위조하는 것이다.)"
                fail=1
            fi
        done <<< "$rows"
    done

    # 0건은 통과가 아니라 계측 실패다.
    if [ "$adr_count" -eq 0 ]; then
        echo "✗ 프로젝트 ADR 파일을 한 건도 찾지 못했습니다."
        echo "  디렉터리는 ${#dirs[@]}개 찾았는데 ADR-*.md 가 0건 ⇒ 글롭이 어긋났거나 파일이"
        echo "  옮겨졌습니다. '전부 정상' 과 구별하기 위해 실패로 끝냅니다."
        return 1
    fi

    if [ "$fail" -ne 0 ]; then
        echo
        echo "[project-adr-index] FAIL — TASK-MONO-525"
        return 1
    fi

    echo "[project-adr-index] OK — 프로젝트 ${dir_count}개 · ADR ${adr_count}건: 전부 색인돼 있고, 유령 행 없고, 상태가 파일과 일치합니다."
    return 0
}

# -----------------------------------------------------------------------------
# 자기검증 — 술어가 무는지 본다.
# 🔴 픽스처를 손으로 짓지 않는다. **실제 ADR 트리를 복사해서 변형**한다. 손으로 지은
#    픽스처는 실물보다 관대하기 쉽고, 그러면 초록이 아무것도 증명하지 못한다.
# -----------------------------------------------------------------------------
self_test() {
    local repo tmp rc pass=0 total=0
    repo="$(git rev-parse --show-toplevel)"
    [ -d "$repo/projects/iam-platform/docs/adr" ] || {
        echo "✗ 자기검증 원본이 없습니다: projects/iam-platform/docs/adr"; return 1; }

    tmp="$(mktemp -d)"
    trap 'rm -rf "$tmp"' RETURN

    fresh() {   # fresh <이름> -> $tmp/<이름> 에 실제 ADR 트리를 복사
        rm -rf "$tmp/$1"
        mkdir -p "$tmp/$1/projects"
        cp -r "$repo/projects/iam-platform" "$tmp/$1/projects/"
        cp -r "$repo/projects/erp-platform" "$tmp/$1/projects/" 2>/dev/null || true
        # ADR 디렉터리만 남긴다(복사 비용 절감이 아니라, 다른 파일이 판정에 끼지 않게)
        find "$tmp/$1/projects" -mindepth 2 -maxdepth 2 ! -name docs -exec rm -rf {} + 2>/dev/null
        printf '%s' "$tmp/$1"
    }

    check() {   # check <이름> <기대 rc> <루트>
        total=$((total + 1))
        bash "$SELF" "$3" >"$tmp/out.txt" 2>&1
        rc=$?
        if [ "$rc" = "$2" ]; then
            echo "  ✔ $1 (rc=$rc)"; pass=$((pass + 1))
        else
            echo "  ✗ $1 — 기대 rc=$2, 실제 rc=$rc"; sed 's/^/      /' "$tmp/out.txt"
        fi
    }

    echo "[project-adr-index] 자기검증 — 실제 ADR 트리를 복사·변형해 술어가 무는지 본다"

    local r
    r="$(fresh a)"; check "원본은 통과한다" 0 "$r"

    r="$(fresh b)"
    sed -i 's/| ACCEPTED |/| Proposed |/' "$r/projects/iam-platform/docs/adr/README.md"
    check "README 상태를 되돌리면 문다 (STATUS)" 1 "$r"

    r="$(fresh c)"
    rm -f "$r/projects/iam-platform/docs/adr/README.md"
    check "색인이 없으면 문다 (NO-INDEX = iam 이 걸린 결함)" 1 "$r"

    r="$(fresh d)"
    grep -v 'ADR-005-service-to-service' "$r/projects/iam-platform/docs/adr/README.md" \
        > "$r/tmp.md" && mv "$r/tmp.md" "$r/projects/iam-platform/docs/adr/README.md"
    check "행이 빠지면 문다 (MISSING)" 1 "$r"

    r="$(fresh e)"
    printf '| [ADR-099](ADR-099-nonexistent.md) | 없는 결정 | ACCEPTED |\n' \
        >> "$r/projects/iam-platform/docs/adr/README.md"
    check "유령 행이 있으면 문다 (PHANTOM)" 1 "$r"

    r="$(fresh f)"
    sed -i 's/^\*\*Status:\*\*/**Status**:/' \
        "$r/projects/iam-platform/docs/adr/ADR-002-admin-tenant-scope-sentinel.md"
    check "표기가 정규형이 아니면 문다 (NOTATION)" 1 "$r"

    # 옵션 글자 축: erp 는 `Accepted — D`. README 를 `— A` 로 바꾸면 물어야 한다.
    r="$(fresh g)"
    if [ -f "$r/projects/erp-platform/docs/adr/README.md" ]; then
        sed -i 's/\*\*Accepted — D\*\*/**Accepted — A**/' \
            "$r/projects/erp-platform/docs/adr/README.md"
        check "옵션 글자가 갈리면 문다 (— D vs — A)" 1 "$r"
    fi

    r="$(fresh h)"
    rm -f "$r"/projects/*/docs/adr/ADR-*.md
    check "ADR 0건은 통과가 아니라 실패다" 1 "$r"

    echo "[project-adr-index] 자기검증 $pass/$total"
    [ "$pass" = "$total" ]
}

main() {
    if [ "${1:-}" = "--self-test" ]; then
        self_test
        exit $?
    fi
    local root="${1:-}"
    if [ -z "$root" ]; then
        root="$(git rev-parse --show-toplevel)"
    fi
    check_root "$root"
}

main "$@"
