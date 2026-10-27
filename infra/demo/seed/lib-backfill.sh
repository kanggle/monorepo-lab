#!/usr/bin/env bash
# =============================================================================
# infra/demo/seed/lib-backfill.sh — «최근 4주» 과거 이력 행의 SQL 생성기 (TASK-MONO-820)
# =============================================================================
# 콘솔의 «실적» 일별 그래프와 운영자 개요의 «이번 주 vs 지난 주» 는 **요청 시각 기준
# 최근 N일**을 그린다. 시드가 API 로 만드는 행은 전부 «부팅한 오늘» 에 찍히므로 그래프는
# 하루치 막대 하나뿐이고 «지난 주» 는 0 이다. 이 파일은 SCM · ERP · IAM · WMS 시드가
# `dbexec --why` 로 흘려 넣을 **과거 행 SQL** 을 만든다.
#
# 이 파일은 SQL 을 **만들기만** 한다 — DB 에 아무것도 쓰지 않는다(`dbexec` 는 각
# seed-<domain>.sh 가 부른다). 그래서 Docker 없이 `--self-test` 로 전부 잴 수 있다.
#
# -----------------------------------------------------------------------------
# 규칙 (README § 「시각 경계는 고정 리터럴」의 예외 — 이 파일이 그 예외의 유일한 구현)
# -----------------------------------------------------------------------------
#   1. 창 = **KST** 로 «오늘 − 28일» ~ «어제». 오늘은 넣지 않는다(오늘 막대는 시드의 실제
#      API 행 몫이다). KST 는 고정 +9h 로 계산한다(한국은 서머타임이 없다) — 호스트의
#      TZ · tzdata 에 기대지 않는다. 시드 셸은 UTC 호스트에서 돈다.
#   2. 행의 **모든 내용**(id · 수량 · 상태 · 시각)은 그 행의 **KST 날짜(+ 그날 안의 순번)**
#      만의 함수다. 부팅 날짜는 «어느 날짜를 넣을지»(창)만 정한다. ⇒ 같은 날 두 번 돌리면
#      같은 SQL, 다른 날 돌리면 겹치는 날짜의 행은 **글자 그대로 같다** — 그래서
#      `ON CONFLICT DO NOTHING` / `INSERT IGNORE` / `WHERE NOT EXISTS` 만으로 수렴한다.
#   3. 행의 마지막 사건(입고 · 결재 확정 등)까지 **전부 어제 이전**일 때만 그 행을 낸다.
#      다음 날로 넘어가는 사건이 있는 행은 그날이 지난 뒤의 부팅에서 처음 나타난다.
#      (그렇지 않으면 «어제 상신, 오늘 입고» 행이 부팅 날짜에 따라 다른 상태로 박힌다.)
#   4. 하루 건수는 날짜 해시로 1~수 건 — **모든 날이 같은 값이 되지 않게**(TASK-PC-FE-380:
#      같은 값은 막대가 같아 보인다). 각 도메인의 headline 지표는 **매일 ≥1 건**이고 그날의
#      첫 건(순번 0)은 이른 시각에 둔다 — «지난 주» 창은 «같은 경과 시각» 에서 끊기므로
#      (KstPeriodBounds#previousWindow) 월요일 아침에도 지난 월요일 몫이 0 이 되지 않게.
#
# id 체계 (전부 날짜에서 유도, 손으로 만드는 API 행과 공간이 겹치지 않는다):
#   UUID 꼴   00000820-<도메인 코드 4자리>-<YYYY>-<MMDD>-<순번 12자리>   (16진 숫자만 — 유효한 UUID)
#               0101 scm 발주 · 0102 scm 발주 라인 · 2101 iam 로그인 성공 · 2102 iam 로그인 실패
#               2103 iam 잠금 · 4101 wms 적치 사실 · 4102 wms 출하 · 4103 wms 출하의 주문 id
#   erp       appr-bf-<YYYYMMDD>-<순번 2자리> · ars-bf-<YYYYMMDD>-<순번 2자리>
#
# 각 생성기는 SQL 맨 앞에 `-- bf-planned <table> <n>` 주석을 찍는다 — 호출자가 넣은 뒤
# 그 날짜 범위의 행 수를 다시 세어 대조한다(«썼다» 가 아니라 «있다» 를 잰다).
#
# 단독 실행: bash infra/demo/seed/lib-backfill.sh --self-test   (CI: verify-demo-wrapper (y2))
# =============================================================================

BF_WINDOW_DAYS=28
BF_KST_OFFSET_S=32400

# bf_init [epoch] — 창을 정한다. 인자 > $BF_NOW_EPOCH(시험용) > 지금.
bf_init() {
  local now="${1:-${BF_NOW_EPOCH:-}}"
  if [ -z "$now" ]; then printf -v now '%(%s)T' -1; fi
  case "$now" in ''|*[!0-9]*) echo "bf_init: epoch 가 숫자가 아닙니다: '$now'" >&2; return 2 ;; esac
  BF_NOW="$now"
  BF_TODAY=$(( (now + BF_KST_OFFSET_S) / 86400 ))   # KST 오늘의 epoch-day
  BF_FIRST=$(( BF_TODAY - BF_WINDOW_DAYS ))
  BF_LAST=$(( BF_TODAY - 1 ))
}

# _bf_ymd <epoch-day> → _Y _M _D (Howard Hinnant civil_from_days — fork 0, tzdata 0)
_bf_ymd() {
  local z=$(( $1 + 719468 ))
  local era=$(( (z >= 0 ? z : z - 146096) / 146097 ))
  local doe=$(( z - era * 146097 ))
  local yoe=$(( (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365 ))
  local doy=$(( doe - (365 * yoe + yoe / 4 - yoe / 100) ))
  local mp=$(( (5 * doy + 2) / 153 ))
  _D=$(( doy - (153 * mp + 2) / 5 + 1 ))
  _M=$(( mp < 10 ? mp + 3 : mp - 9 ))
  _Y=$(( yoe + era * 400 + (_M <= 2 ? 1 : 0) ))
}

# _bf_days <y> <m> <d> → _DAYS (days_from_civil, 위의 역함수 — 자기시험이 왕복을 잰다)
_bf_days() {
  local y=$(( $2 <= 2 ? $1 - 1 : $1 )) m="$2" d="$3"
  local era=$(( (y >= 0 ? y : y - 399) / 400 ))
  local yoe=$(( y - era * 400 ))
  local doy=$(( (153 * (m > 2 ? m - 3 : m + 9) + 2) / 5 + d - 1 ))
  local doe=$(( yoe * 365 + yoe / 4 - yoe / 100 + doy ))
  _DAYS=$(( era * 146097 + doe - 719468 ))
}

# bf_day <epoch-day> → BF_YMD(YYYYMMDD) BF_DATE(YYYY-MM-DD) BF_YYYY BF_MMDD
bf_day() {
  _bf_ymd "$1"
  printf -v BF_YYYY '%04d' "$_Y"
  printf -v BF_MMDD '%02d%02d' "$_M" "$_D"
  BF_YMD="$BF_YYYY$BF_MMDD"
  printf -v BF_DATE '%04d-%02d-%02d' "$_Y" "$_M" "$_D"
}

# bf_ts <epoch> → BF_PG('YYYY-MM-DDTHH:MM:SSZ', TIMESTAMPTZ) BF_MY('YYYY-MM-DD HH:MM:SS.000000', UTC DATETIME(6))
bf_ts() {
  local e="$1" s
  _bf_ymd $(( e / 86400 ))
  s=$(( e % 86400 ))
  printf -v BF_PG '%04d-%02d-%02dT%02d:%02d:%02dZ' "$_Y" "$_M" "$_D" $(( s / 3600 )) $(( s % 3600 / 60 )) $(( s % 60 ))
  printf -v BF_MY '%04d-%02d-%02d %02d:%02d:%02d.000000' "$_Y" "$_M" "$_D" $(( s / 3600 )) $(( s % 3600 / 60 )) $(( s % 60 ))
}

# bf_at <epoch-day> <KST 자정부터의 초> → BF_AT (UTC epoch)
bf_at() { BF_AT=$(( $1 * 86400 + $2 - BF_KST_OFFSET_S )); }

# bf_hash <day> <idx> <salt> → BF_H ∈ [0, 2^31) — 날짜·순번·용도만의 결정적 함수
bf_hash() {
  local x=$(( ($1 * 73856093) ^ ($2 * 19349663) ^ ($3 * 83492791) ))
  x=$(( (x ^ (x >> 13)) * 1274126177 ))
  x=$(( x ^ (x >> 16) ))
  BF_H=$(( x & 2147483647 ))
}

# bf_uuid <도메인 코드 4자리> <epoch-day> <idx> → BF_UUID
bf_uuid() {
  _bf_ymd "$2"
  printf -v BF_UUID '00000820-%s-%04d-%02d%02d-%012d' "$1" "$_Y" "$_M" "$_D" "$3"
}

# bf_sec <h> <kind> → BF_SEC (KST 자정부터의 초)
#   early = 07:00–08:59 (업무 · 그날 첫 건)  night = 01:00–07:59 (로그인 · 출하 첫 건)
#   day   = 09:00–20:59                     → 뒤에 붙는 사건(최대 +3h)도 그날 안에 남는다
bf_sec() {
  case "$2" in
    early) BF_SEC=$(( 7 * 3600 + $1 % 7200 )) ;;
    night) BF_SEC=$(( 1 * 3600 + $1 % 25200 )) ;;
    *)     BF_SEC=$(( 9 * 3600 + $1 % 43200 )) ;;
  esac
}

# bf_planned <sql> <table> → 표준출력에 계획 행 수 (없으면 빈 값)
bf_planned() {
  local re="-- bf-planned $2 ([0-9]+)"
  [[ $1 =~ $re ]] && printf '%s' "${BASH_REMATCH[1]}"
}

# bf_id_range <도메인 코드> → BF_LO BF_HI (창 안의 UUID 꼴 id 전부를 덮는 사전순 범위)
bf_id_range() {
  bf_uuid "$1" "$BF_FIRST" 0;  BF_LO="$BF_UUID"
  bf_uuid "$1" "$BF_LAST" 0;   BF_HI="${BF_UUID%-*}-999999999999"
}

# =============================================================================
# SCM — purchase_orders · purchase_order_lines · po_status_history (scm_procurement)
# =============================================================================
# 지표: poCount/poAmount:KRW = submitted_at 이 창 안 · status ≠ CANCELED,
#       receivedCount = to_status='RECEIVED' 이력의 occurred_at (PurchaseOrderMetricsService).
# 상태는 PoStatusMachine 이 허락하는 경로만: DRAFT→SUBMITTED(OPERATOR) →ACKNOWLEDGED(SUPPLIER)
#   →CONFIRMED(OPERATOR) →RECEIVED(SYSTEM, 다음 날) · SUBMITTED→CANCELED(OPERATOR, 대조군).
# 하루 1~4건. 순번 0 은 CANCELED·RECEIVED 가 되지 않는다(headline poAmount 가 매일 >0,
#   다음 날 입고 때문에 그날 막대가 미뤄지지 않게).
bf_scm_sql() {
  local tenant="$1" supplier="$2" buyer="$3" sku="$4"
  if [ -z "$tenant" ] || [ -z "$supplier" ] || [ -z "$buyer" ] || [ -z "$sku" ]; then
    echo "bf_scm_sql: tenant/supplier/buyer/sku 가 비었습니다" >&2; return 2
  fi
  local -a prices=(1000 2500 4800 12000 35000)
  local body="" day n i k h r st nl qty price total po pono sub ack conf canc recv created upd ver
  local sub_s ack_s conf_s canc_s canc_r recv_s created_s upd_s rq planned=0 nlines=0 nhist=0 sec
  for (( day=BF_FIRST; day<=BF_LAST; day++ )); do
    bf_day "$day"
    bf_hash "$day" 0 11; n=$(( 1 + BF_H % 4 ))
    for (( i=0; i<n; i++ )); do
      bf_hash "$day" "$i" 12; h=$BF_H; r=$(( h % 10 ))
      if [ "$i" -eq 0 ]; then
        if [ "$r" -lt 3 ]; then st=SUBMITTED; elif [ "$r" -lt 5 ]; then st=ACKNOWLEDGED; else st=CONFIRMED; fi
        bf_sec "$h" early
      else
        case "$r" in
          0) st=CANCELED ;; 1) st=SUBMITTED ;; 2) st=ACKNOWLEDGED ;;
          3|4|5) st=CONFIRMED ;; *) st=RECEIVED ;;
        esac
        bf_sec "$h" day
      fi
      # 규칙 3: 다음 날 입고는 그 다음 날이 «어제 이전» 이 된 뒤의 부팅에서 처음 낸다.
      if [ "$st" = RECEIVED ] && [ $(( day + 1 )) -gt "$BF_LAST" ]; then continue; fi
      sec=$BF_SEC
      bf_at "$day" "$sec"; sub=$BF_AT
      ack=$(( sub + 300 + h % 1500 ))
      conf=$(( ack + 1800 + (h >> 8) % 5400 ))
      canc=$(( sub + 600 + (h >> 8) % 3600 ))
      bf_at $(( day + 1 )) $(( 10 * 3600 + (h >> 12) % 28800 )); recv=$BF_AT
      created=$(( sub - 120 - h % 600 ))
      bf_uuid 0101 "$day" "$i"; po="$BF_UUID"
      printf -v pono 'PO-BF-%s-%02d' "$BF_YMD" $(( i + 1 ))
      bf_ts "$sub"; sub_s="'$BF_PG'"
      bf_ts "$created"; created_s="'$BF_PG'"
      ack_s=NULL; conf_s=NULL; canc_s=NULL; canc_r=NULL; upd=$sub; ver=1
      case "$st" in
        ACKNOWLEDGED) bf_ts "$ack"; ack_s="'$BF_PG'"; upd=$ack; ver=2 ;;
        CONFIRMED)    bf_ts "$ack"; ack_s="'$BF_PG'"; bf_ts "$conf"; conf_s="'$BF_PG'"; upd=$conf; ver=3 ;;
        RECEIVED)     bf_ts "$ack"; ack_s="'$BF_PG'"; bf_ts "$conf"; conf_s="'$BF_PG'"; upd=$recv; ver=4 ;;
        CANCELED)     bf_ts "$canc"; canc_s="'$BF_PG'"; canc_r="'데모 이력 — 수요 변경으로 취소'"; upd=$canc; ver=2 ;;
      esac
      bf_ts "$upd"; upd_s="'$BF_PG'"
      nl=$(( 1 + (h >> 4) % 2 )); total=0
      local lines_sql=""
      for (( k=0; k<nl; k++ )); do
        bf_hash "$day" $(( i * 10 + k )) 13
        qty=$(( 10 + BF_H % 191 )); price=${prices[$(( (BF_H >> 8) % 5 ))]}
        total=$(( total + qty * price ))
        rq=0; [ "$st" = RECEIVED ] && rq=$qty
        bf_uuid 0102 "$day" $(( i * 10 + k ))
        lines_sql+="INSERT INTO purchase_order_lines (id, po_id, tenant_id, line_no, sku, quantity, unit_price, received_quantity) VALUES ('$BF_UUID', '$po', '$tenant', $(( k + 1 )), '$sku', $qty, $price.00, $rq) ON CONFLICT DO NOTHING;"$'\n'
        nlines=$(( nlines + 1 ))
      done
      body+="INSERT INTO purchase_orders (id, tenant_id, po_number, supplier_id, buyer_account_id, status, total_amount, currency, submitted_at, acknowledged_at, confirmed_at, canceled_at, cancellation_reason, created_at, updated_at, version, origin) VALUES ('$po', '$tenant', '$pono', '$supplier', '$buyer', '$st', $total.00, 'KRW', $sub_s, $ack_s, $conf_s, $canc_s, $canc_r, $created_s, $upd_s, $ver, 'OPERATOR') ON CONFLICT DO NOTHING;"$'\n'
      body+="$lines_sql"
      _bf_scm_hist DRAFT SUBMITTED "$buyer" OPERATOR "$sub"
      case "$st" in
        ACKNOWLEDGED|CONFIRMED|RECEIVED) _bf_scm_hist SUBMITTED ACKNOWLEDGED "" SUPPLIER "$ack" ;;
      esac
      case "$st" in
        CONFIRMED|RECEIVED) _bf_scm_hist ACKNOWLEDGED CONFIRMED "$buyer" OPERATOR "$conf" ;;
      esac
      [ "$st" = RECEIVED ] && _bf_scm_hist CONFIRMED RECEIVED "" SYSTEM "$recv"
      [ "$st" = CANCELED ] && _bf_scm_hist SUBMITTED CANCELED "$buyer" OPERATOR "$canc"
      planned=$(( planned + 1 ))
    done
  done
  printf -- '-- bf-planned purchase_orders %d\n-- bf-planned purchase_order_lines %d\n-- bf-planned po_status_history %d\n' \
    "$planned" "$nlines" "$nhist"
  printf 'BEGIN;\n%sCOMMIT;\n' "$body"
}

# _bf_scm_hist <from> <to> <actor 계정|''> <actor_type> <epoch> — body 와 nhist 를 바깥 함수에서 늘린다.
# 이력에는 자연키가 없다(BIGSERIAL) ⇒ (po_id, to_status) 로 NOT EXISTS. 한 PO 는 같은 상태로 두 번 가지 않는다.
_bf_scm_hist() {
  local actor="NULL"; [ -n "$3" ] && actor="'$3'"
  bf_ts "$5"
  body+="INSERT INTO po_status_history (po_id, tenant_id, from_status, to_status, actor_account_id, actor_type, reason, occurred_at) SELECT '$po', '$tenant', '$1', '$2', $actor::varchar, '$4', NULL::varchar, '$BF_PG'::timestamptz WHERE NOT EXISTS (SELECT 1 FROM po_status_history WHERE po_id = '$po' AND to_status = '$2');"$'\n'
  nhist=$(( nhist + 1 ))
}

# =============================================================================
# ERP — approval_request · approval_route_stage · approval_action (erp_approval_db, MySQL)
# =============================================================================
# 지표: submitted = submitted_at · approved/rejected = status + finalized_at (ApprovalMetricsService).
# 1단계 라우트(current_stage_index 0 · total_stages 1 — V2 기본값과 같다). 상신자 = 연결된
# 사원(이운영), 승인자 = 연결된 사원(김본부) — 자기결재가 아니고, 대기 건은 승인자의 결재함에
# 실제로 뜨며 승인 버튼이 동작한다(라우트 단계 행이 있으므로). 액터는 사원 id(v2.4).
# 하루 1~3건. 순번 0 = APPROVED(headline 이 매일 >0). 나머지: 10% 대기(SUBMITTED) · 20% 반려.
# 결재 확정은 같은 날 10분~2.5시간 뒤 — 규칙 3 의 이월이 생기지 않는다.
bf_erp_sql() {
  local tenant="$1" submitter="$2" approver="$3" s_ops="$4" s_fin="$5" s_hq="$6" s_e3="$7" s_e4="$8"
  local a
  for a in "$@"; do
    [ -n "$a" ] || { echo "bf_erp_sql: 인자 8개 중 빈 값이 있습니다(사원/부서 id)" >&2; return 2; }
  done
  [ "$submitter" != "$approver" ] || { echo "bf_erp_sql: 상신자 = 승인자(자기결재)" >&2; return 2; }
  local -a stype=(DEPARTMENT DEPARTMENT DEPARTMENT EMPLOYEE EMPLOYEE)
  local -a sid=("$s_ops" "$s_fin" "$s_hq" "$s_e4" "$s_e3")
  local -a titles=("운영본부 비품 구매 승인 요청" "재무팀 분기 예산 집행 승인 요청" "본사 출장비 정산 승인 요청" \
                   "최사원 직무 교육 참가 승인 요청" "박재무 초과근무 승인 요청")
  local body="" day n i h r st j id rs sub fin created sub_s fin_s upd_s ver planned=0
  for (( day=BF_FIRST; day<=BF_LAST; day++ )); do
    bf_day "$day"
    bf_hash "$day" 0 21; n=$(( 1 + BF_H % 3 ))
    for (( i=0; i<n; i++ )); do
      bf_hash "$day" "$i" 22; h=$BF_H; r=$(( h % 10 ))
      if [ "$i" -eq 0 ]; then st=APPROVED; bf_sec "$h" early
      else
        if [ "$r" -eq 0 ]; then st=SUBMITTED; elif [ "$r" -le 2 ]; then st=REJECTED; else st=APPROVED; fi
        bf_sec "$h" day
      fi
      j=$(( (h >> 3) % 5 ))
      printf -v id 'appr-bf-%s-%02d' "$BF_YMD" "$i"
      printf -v rs 'ars-bf-%s-%02d' "$BF_YMD" "$i"
      bf_at "$day" "$BF_SEC"; sub=$BF_AT
      fin=$(( sub + 600 + (h >> 8) % 9000 ))
      created=$(( sub - 300 - h % 1200 ))
      bf_ts "$sub"; sub_s="$BF_MY"
      bf_ts "$created"; local created_s="$BF_MY"
      if [ "$st" = SUBMITTED ]; then fin_s=NULL; upd_s="'$sub_s'"; ver=1
      else bf_ts "$fin"; fin_s="'$BF_MY'"; upd_s="'$BF_MY'"; ver=2; fi
      body+="INSERT IGNORE INTO approval_request (id, tenant_id, subject_type, subject_id, title, creation_reason, approver_id, submitter_id, status, created_at, submitted_at, finalized_at, updated_at, version, current_stage_index, total_stages) VALUES ('$id', '$tenant', '${stype[$j]}', '${sid[$j]}', '${titles[$j]} (${BF_DATE:5:2}/${BF_DATE:8:2} #$(( i + 1 )))', '데모 이력(시드 백필 — TASK-MONO-820)', '$approver', '$submitter', '$st', '$created_s', '$sub_s', $fin_s, $upd_s, $ver, 0, 1);"$'\n'
      body+="INSERT IGNORE INTO approval_route_stage (id, tenant_id, request_id, stage_index, approver_id, created_at) VALUES ('$rs', '$tenant', '$id', 0, '$approver', '$created_s');"$'\n'
      body+="INSERT INTO approval_action (tenant_id, approval_request_id, transition, actor, reason, stage, occurred_at) SELECT '$tenant', '$id', 'SUBMITTED', '$submitter', NULL, 0, '$sub_s' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM approval_action WHERE approval_request_id = '$id' AND transition = 'SUBMITTED');"$'\n'
      case "$st" in
        APPROVED) body+="INSERT INTO approval_action (tenant_id, approval_request_id, transition, actor, reason, stage, occurred_at) SELECT '$tenant', '$id', 'APPROVED', '$approver', NULL, 0, ${fin_s} FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM approval_action WHERE approval_request_id = '$id' AND transition = 'APPROVED');"$'\n' ;;
        REJECTED) body+="INSERT INTO approval_action (tenant_id, approval_request_id, transition, actor, reason, stage, occurred_at) SELECT '$tenant', '$id', 'REJECTED', '$approver', '근거 자료 보완 후 재상신 바랍니다', 0, ${fin_s} FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM approval_action WHERE approval_request_id = '$id' AND transition = 'REJECTED');"$'\n' ;;
      esac
      planned=$(( planned + 1 ))
    done
  done
  printf -- '-- bf-planned approval_request %d\n' "$planned"
  printf 'SET NAMES utf8mb4;\nSTART TRANSACTION;\n%sCOMMIT;\n' "$body"
}

# =============================================================================
# IAM — login_history · account_lock_history (security_db, MySQL)
# =============================================================================
# 지표: loginSuccess/loginFailure = outcome + occurred_at, tenant_id = X 또는 site_tenant_id = X ·
#       accountLocked = account_lock_history.occurred_at, tenant_id = X (SecurityMetricsQueryService).
# 🔴 두 테이블은 지울 수도 고칠 수도 없다(V0002 · V0010 트리거) ⇒ event_id(UNIQUE) + INSERT IGNORE.
#    누적 상한(README § 예외): 하루 ≤ 성공 7 + 실패 3 + 잠금 1 = 11행. 날짜당 한 번만 들어가므로
#    총 행 수 ≤ 11 × (28 + 첫 부팅과 마지막 부팅 사이의 날 수).
# 하루 성공 2~7 · 실패 1~3(첫 건은 01:00–07:59) · 잠금은 약 9일에 한 번(그날 마지막 실패 1분 뒤,
#   reason AUTO_DETECT · source system · locked_by = 시스템 UUID — AccountLockedConsumer 의 규약).
bf_iam_sql() {
  local tenant="$1" account="$2"
  if [ -z "$tenant" ] || [ -z "$account" ]; then echo "bf_iam_sql: tenant/account 가 비었습니다" >&2; return 2; fi
  local -a uas=(Chrome Edge Safari Firefox)
  local -a ips=('203.0.*.*' '198.51.*.*' '192.0.*.*')
  local logins="" locks="" day s f i h t last_fail nsucc=0 nfail=0 nlock=0
  for (( day=BF_FIRST; day<=BF_LAST; day++ )); do
    bf_hash "$day" 0 31; s=$(( 2 + BF_H % 6 ))
    bf_hash "$day" 0 32; f=$(( 1 + BF_H % 3 ))
    for (( i=0; i<s; i++ )); do
      bf_hash "$day" "$i" 34; h=$BF_H
      bf_at "$day" $(( 7 * 3600 + h % 57600 )); bf_ts "$BF_AT"
      bf_uuid 2101 "$day" "$i"
      logins+="INSERT IGNORE INTO login_history (tenant_id, site_tenant_id, event_id, account_id, outcome, ip_masked, user_agent_family, device_fingerprint, geo_country, occurred_at) VALUES ('$tenant', NULL, '$BF_UUID', '$account', 'SUCCESS', '${ips[$(( (h >> 5) % 3 ))]}', '${uas[$(( (h >> 9) % 4 ))]}', NULL, 'KR', '$BF_MY');"$'\n'
      nsucc=$(( nsucc + 1 ))
    done
    last_fail=0
    for (( i=0; i<f; i++ )); do
      bf_hash "$day" "$i" 35; h=$BF_H
      if [ "$i" -eq 0 ]; then bf_sec "$h" night; else bf_sec "$h" day; fi
      bf_at "$day" "$BF_SEC"; t=$BF_AT; [ "$t" -gt "$last_fail" ] && last_fail=$t
      bf_ts "$t"; bf_uuid 2102 "$day" "$i"
      logins+="INSERT IGNORE INTO login_history (tenant_id, site_tenant_id, event_id, account_id, outcome, ip_masked, user_agent_family, device_fingerprint, geo_country, occurred_at) VALUES ('$tenant', NULL, '$BF_UUID', '$account', 'FAILURE', '${ips[$(( (h >> 5) % 3 ))]}', '${uas[$(( (h >> 9) % 4 ))]}', NULL, 'KR', '$BF_MY');"$'\n'
      nfail=$(( nfail + 1 ))
    done
    bf_hash "$day" 0 33
    if [ $(( BF_H % 9 )) -eq 0 ]; then
      bf_ts $(( last_fail + 60 )); bf_uuid 2103 "$day" 0
      locks+="INSERT IGNORE INTO account_lock_history (tenant_id, event_id, account_id, reason, locked_by, source, occurred_at) VALUES ('$tenant', '$BF_UUID', '$account', 'AUTO_DETECT', '00000000-0000-0000-0000-000000000000', 'system', '$BF_MY');"$'\n'
      nlock=$(( nlock + 1 ))
    fi
  done
  printf -- '-- bf-planned login_history %d\n-- bf-planned login_history_success %d\n-- bf-planned login_history_failure %d\n-- bf-planned account_lock_history %d\n' \
    $(( nsucc + nfail )) "$nsucc" "$nfail" "$nlock"
  printf 'START TRANSACTION;\n%s%sCOMMIT;\n' "$logins" "$locks"
}

# =============================================================================
# WMS — admin_putaway_fact · admin_shipment_summary + 일 카운터 (admin_db, 읽기 모델)
# =============================================================================
# 지표: qtyReceived = admin_putaway_fact.completed_at · qtyShipped/shipmentCount =
#       admin_shipment_summary.shipped_at — 테넌트 필터 없음(WarehouseMetricsService).
# /dashboard/throughput 는 일 카운터를 읽는다 ⇒ 같은 사실로 맞춘다:
#   입고 — **이번에 새로 들어간 사실만** 그 (KST 날짜, 창고) 카운터에 더한다(CTE RETURNING).
#          재실행이면 RETURNING 이 0행이라 아무것도 더하지 않는다. 🔵 V6 에 재구성 함수
#          admin_rebuild_inbound_daily_kst() 가 **있지만** 사실 < 카운터인 볼륨(V6 이전 날짜가
#          남은 볼륨)에서는 RAISE EXCEPTION 으로 거절하므로 부팅 경로에서 부르지 않는다.
#   출고 — admin_rebuild_outbound_daily_kst() (요약 행이 출하별 정본 — V6 의 설계).
# 출하 행: tenant_id = NULL(봉투 테넌트가 없는 출하 — 테넌트 소유 목록에는 무제한 호출자에게만
#   보인다). order_id 는 어떤 outbound 주문도 가리키지 않는 날짜 유도 UUID — 콘솔의 출하 행은
#   링크가 없다(WmsRecentShipments · WmsShipmentsDataTable), orderId 역조회는 outbound 주문
#   상세에서만 일어나므로 이 id 로 가는 화면 경로가 없다.
# 하루 적치 1~3건(20~150) · 출하 1~3건(5~60), 두 창고(WH01 · WH-MAIN)에 해시로 나눈다.
bf_wms_sql() {
  local wh1="$1" wh2="$2"
  if [ -z "$wh1" ] || [ -z "$wh2" ]; then echo "bf_wms_sql: 창고 id 2개가 필요합니다" >&2; return 2; fi
  local facts="" ships="" day n i h wh qty sep="" nput=0 nship=0
  for (( day=BF_FIRST; day<=BF_LAST; day++ )); do
    bf_day "$day"
    bf_hash "$day" 0 41; n=$(( 1 + BF_H % 3 ))
    for (( i=0; i<n; i++ )); do
      bf_hash "$day" "$i" 42; h=$BF_H
      qty=$(( 20 + h % 131 )); wh=$wh1; [ $(( (h >> 5) % 2 )) -eq 1 ] && wh=$wh2
      if [ "$i" -eq 0 ]; then bf_sec "$h" night; else bf_sec "$h" day; fi
      bf_at "$day" "$BF_SEC"; bf_ts "$BF_AT"; bf_uuid 4101 "$day" "$i"
      facts+="$sep"$'\n'"  ('$BF_UUID', '$wh', '$BF_PG', $qty)"; sep=","
      nput=$(( nput + 1 ))
    done
    bf_hash "$day" 0 43; n=$(( 1 + BF_H % 3 ))
    for (( i=0; i<n; i++ )); do
      bf_hash "$day" "$i" 44; h=$BF_H
      qty=$(( 5 + h % 56 )); wh=$wh1; [ $(( (h >> 5) % 2 )) -eq 1 ] && wh=$wh2
      if [ "$i" -eq 0 ]; then bf_sec "$h" night; else bf_sec "$h" day; fi
      bf_at "$day" "$BF_SEC"; bf_ts "$BF_AT"
      bf_uuid 4103 "$day" "$i"; local order="$BF_UUID"
      bf_uuid 4102 "$day" "$i"
      ships+="INSERT INTO admin_shipment_summary (shipment_id, order_id, order_no, warehouse_id, shipment_no, carrier_code, tracking_no, shipped_at, total_qty, last_event_at, version, tenant_id) VALUES ('$BF_UUID', '$order', 'SO-BF-$BF_YMD-$(( i + 1 ))', '$wh', 'SHP-BF-$BF_YMD-$(( i + 1 ))', NULL, NULL, '$BF_PG', $qty, '$BF_PG', 1, NULL) ON CONFLICT (shipment_id) DO NOTHING;"$'\n'
      nship=$(( nship + 1 ))
    done
  done
  printf -- '-- bf-planned admin_putaway_fact %d\n-- bf-planned admin_shipment_summary %d\n' "$nput" "$nship"
  printf 'BEGIN;\n'
  printf 'WITH ins AS (\n INSERT INTO admin_putaway_fact (event_id, warehouse_id, completed_at, qty_received) VALUES%s\n ON CONFLICT (event_id) DO NOTHING\n RETURNING warehouse_id, completed_at, qty_received\n)\n' "$facts"
  # 🔴 한 줄이어야 한다 — 자기시험 (7)·(7b) 가 «FROM ins» 와 수렴 절을 같은 문장에서 잰다.
  # 이 DO UPDATE 는 «더하기» 다. 멱등은 ON CONFLICT 가 아니라 **FROM ins**(이번에 새로 들어간
  # 사실만)가 보장한다 — 그것을 FROM admin_putaway_fact 로 바꾸면 매 부팅 두 배가 된다.
  printf "INSERT INTO admin_throughput_inbound_daily (date, warehouse_id, putaway_count, qty_received, last_event_at, version) SELECT (completed_at AT TIME ZONE 'Asia/Seoul')::date, warehouse_id, count(*), sum(qty_received), max(completed_at), 1 FROM ins GROUP BY 1, 2 ON CONFLICT (date, warehouse_id) DO UPDATE SET putaway_count = admin_throughput_inbound_daily.putaway_count + EXCLUDED.putaway_count, qty_received = admin_throughput_inbound_daily.qty_received + EXCLUDED.qty_received, last_event_at = GREATEST(admin_throughput_inbound_daily.last_event_at, EXCLUDED.last_event_at), version = admin_throughput_inbound_daily.version + 1;\n"
  printf '%s' "$ships"
  printf 'SELECT before_count, before_qty, after_count, after_qty FROM admin_rebuild_outbound_daily_kst();\n'
  printf 'COMMIT;\n'
}

# =============================================================================
# --self-test — Docker 없이 생성기의 성질을 잰다 (CI: verify-demo-wrapper.sh (y2))
# =============================================================================
_bf_t_fail=0
_bf_t() { if [ "$1" = ok ]; then echo "  ok: $2"; else echo "  FAIL: $2" >&2; _bf_t_fail=$(( _bf_t_fail + 1 )); fi; }

# 표본 인자 — 실제 값의 모양(UUID · 사원 id)을 흉내 낸다. 시험은 값이 아니라 성질을 본다.
_BF_T_SCM=(demo-corp 0199aaaa-0000-7000-8000-000000000001 0199de70-0000-7000-8000-00000000ad03 SKU-APPLE-001)
_BF_T_ERP=(demo-corp emp-sub emp-app dept-ops dept-fin dept-hq emp-3 emp-4)
_BF_T_IAM=(demo-corp 0199de70-0000-7000-8000-00000000ad07)
_BF_T_WMS=(01910000-0000-7000-8000-000000000001 01910000-0000-7000-8000-000000000002)

_bf_gen() { # _bf_gen <domain> <epoch>
  bf_init "$2" || return 2
  case "$1" in
    scm) bf_scm_sql "${_BF_T_SCM[@]}" ;;
    erp) bf_erp_sql "${_BF_T_ERP[@]}" ;;
    iam) bf_iam_sql "${_BF_T_IAM[@]}" ;;
    wms) bf_wms_sql "${_BF_T_WMS[@]}" ;;
  esac
}

# 한 줄의 «행 날짜»(id 에서) → _RD (epoch-day) 또는 빈 값
_bf_row_day() {
  _RD=""
  local re1='00000820-[0-9]{4}-([0-9]{4})-([0-9]{2})([0-9]{2})-' re2='(appr|ars)-bf-([0-9]{4})([0-9]{2})([0-9]{2})-'
  if [[ $1 =~ $re1 ]]; then _bf_days "${BASH_REMATCH[1]}" $(( 10#${BASH_REMATCH[2]} )) $(( 10#${BASH_REMATCH[3]} )); _RD=$_DAYS
  elif [[ $1 =~ $re2 ]]; then _bf_days "${BASH_REMATCH[2]}" $(( 10#${BASH_REMATCH[3]} )) $(( 10#${BASH_REMATCH[4]} )); _RD=$_DAYS
  fi
}

bf_self_test() {
  echo "[bf-self-test] lib-backfill.sh (TASK-MONO-820)"
  local d y m dd
  # (1) 달력 계산 — 알려진 날짜(윤년 · 세기 경계 포함)와 왕복
  for d in "1970-01-01 0" "1999-12-31 10956" "2000-02-29 11016" "2000-03-01 11017" "2024-02-29 19782" "2026-10-28 20754" "2100-03-01 47541"; do
    set -- $d; IFS=- read -r y m dd <<<"$1"
    _bf_days "$y" $(( 10#$m )) $(( 10#$dd ))
    bf_day "$2"
    if [ "$_DAYS" = "$2" ] && [ "$BF_DATE" = "$1" ]; then _bf_t ok "달력 $1 = epoch-day $2 (양방향)"; else _bf_t fail "달력 $1: days=$_DAYS date=$BF_DATE (기대 $2)"; fi
  done
  local bad=0
  for (( d=20000; d<20800; d++ )); do bf_day "$d"; _bf_days "$_Y" "$_M" "$_D"; [ "$_DAYS" = "$d" ] || bad=$(( bad + 1 )); done
  [ "$bad" = 0 ] && _bf_t ok "달력 왕복 800일 전부 일치" || _bf_t fail "달력 왕복 불일치 $bad 일"

  # (2) KST 경계 — UTC 날짜와 KST 날짜가 갈리는 9시간
  local E1=1793156400            # 2026-10-28T03:00:00Z = KST 12:00 (10-28)
  bf_init 1793113500; bf_day "$BF_TODAY"  # 2026-10-27T15:05:00Z = KST 00:05 (10-28)
  [ "$BF_DATE" = 2026-10-28 ] && _bf_t ok "UTC 10-27 15:05 → KST 오늘 2026-10-28 (자정 직후 부팅)" || _bf_t fail "KST 자정 직후: 오늘=$BF_DATE"
  bf_day "$BF_LAST"; [ "$BF_DATE" = 2026-10-27 ] && _bf_t ok "그때 창의 끝(어제) = 2026-10-27" || _bf_t fail "창의 끝=$BF_DATE"
  bf_init 1793113140; bf_day "$BF_TODAY"  # 2026-10-27T14:59:00Z = KST 23:59 (10-27)
  [ "$BF_DATE" = 2026-10-27 ] && _bf_t ok "UTC 10-27 14:59 → KST 오늘 2026-10-27 (자정 직전)" || _bf_t fail "KST 자정 직전: 오늘=$BF_DATE"
  bf_init "$E1"; bf_day "$BF_FIRST"
  [ "$BF_DATE" = 2026-09-30 ] && [ $(( BF_LAST - BF_FIRST + 1 )) = 28 ] && _bf_t ok "창 = 2026-09-30 ~ 2026-10-27 (28일)" || _bf_t fail "창 시작=$BF_DATE 길이=$(( BF_LAST - BF_FIRST + 1 ))"

  # (3) id 유도가 결정적이고 UUID 꼴인가
  local u1 u2
  bf_uuid 0101 20753 3; u1=$BF_UUID; bf_uuid 0101 20753 3; u2=$BF_UUID
  [[ $u1 = "$u2" && $u1 =~ ^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$ && $u1 = 00000820-0101-2026-1027-000000000003 ]] \
    && _bf_t ok "bf_uuid 결정적 · UUID 꼴 ($u1)" || _bf_t fail "bf_uuid: '$u1' / '$u2'"

  local self; self="${BASH_SOURCE[0]}"
  local dom a b c line lines_a missing=0 ts bad_ts=0 later
  for dom in scm erp iam wms; do
    a="$(_bf_gen "$dom" "$E1")"; local rc=$?
    [ "$rc" = 0 ] && [ -n "$a" ] || { _bf_t fail "$dom: 생성 실패 rc=$rc"; continue; }
    # (4) 같은 부팅 시각 두 번 = 같은 SQL — **다른 프로세스**에서(전역 상태·난수 누수 배제)
    b="$(bash -c '. "$1"; _bf_gen "$2" "$3"' _ "$self" "$dom" "$E1")"
    [ "$a" = "$b" ] && _bf_t ok "$dom: 같은 부팅 시각 → 다른 프로세스에서도 바이트 동일 (${#a} bytes)" || _bf_t fail "$dom: 두 실행의 SQL 이 다릅니다"
    # 같은 KST 날짜 안의 다른 시각(아침 vs 밤) — «같은 날 재실행» 이 수렴하는가
    c="$(_bf_gen "$dom" $(( E1 - 10000 )))"
    [ "$a" = "$c" ] && _bf_t ok "$dom: 같은 KST 날의 다른 시각에 돌려도 동일" || _bf_t fail "$dom: 같은 날 다른 시각에 SQL 이 바뀝니다"

    # (5) 모든 시각이 창 안 — 오늘 · 미래 · 28일보다 오래된 것 0
    bf_init "$E1"; bad_ts=0
    local re_pg="'([0-9]{4})-([0-9]{2})-([0-9]{2})[T ]([0-9]{2}):([0-9]{2}):([0-9]{2})"
    local rest sec kd nts=0
    while IFS= read -r line; do
      rest="$line"
      while [[ $rest =~ $re_pg ]]; do
        _bf_days "${BASH_REMATCH[1]}" $(( 10#${BASH_REMATCH[2]} )) $(( 10#${BASH_REMATCH[3]} ))
        sec=$(( _DAYS * 86400 + 10#${BASH_REMATCH[4]} * 3600 + 10#${BASH_REMATCH[5]} * 60 + 10#${BASH_REMATCH[6]} ))
        kd=$(( (sec + BF_KST_OFFSET_S) / 86400 )); nts=$(( nts + 1 ))
        { [ "$kd" -ge "$BF_FIRST" ] && [ "$kd" -le "$BF_LAST" ]; } || bad_ts=$(( bad_ts + 1 ))
        rest="${rest#*"${BASH_REMATCH[0]}"}"
      done
    done <<<"$a"
    [ "$bad_ts" = 0 ] && [ "$nts" -gt 0 ] && _bf_t ok "$dom: 시각 $nts 개 전부 KST 날짜가 [오늘−28, 어제] 안" || _bf_t fail "$dom: 창 밖 시각 $bad_ts/$nts 개"

    # (6) 수렴 — 3일 뒤 부팅의 SQL 이 겹치는 날짜의 행을 **글자 그대로** 담는가
    later="$(_bf_gen "$dom" $(( E1 + 3 * 86400 )))"
    local -A seen=()
    while IFS= read -r line; do seen["${line%,}"]=1; done <<<"$later"
    bf_init $(( E1 + 3 * 86400 )); missing=0; local checked=0
    while IFS= read -r line; do
      line="${line%,}"
      _bf_row_day "$line"; [ -n "$_RD" ] || continue
      [ "$_RD" -ge "$BF_FIRST" ] || continue
      checked=$(( checked + 1 ))
      [ -n "${seen[$line]:-}" ] || missing=$(( missing + 1 ))
    done <<<"$a"
    unset seen
    [ "$missing" = 0 ] && [ "$checked" -gt 0 ] && _bf_t ok "$dom: 3일 뒤 부팅도 겹치는 날짜의 행 $checked 줄을 그대로 낸다" || _bf_t fail "$dom: 겹치는 날짜 행 $missing/$checked 줄이 3일 뒤 SQL 에 없습니다"

    # (7) 수렴 절 — 모든 단일행 INSERT 가 ON CONFLICT / INSERT IGNORE / WHERE NOT EXISTS
    local nins=0 nbad=0
    while IFS= read -r line; do
      [[ $line == INSERT* ]] || continue
      nins=$(( nins + 1 ))
      [[ $line == *"ON CONFLICT"* || $line == "INSERT IGNORE"* || $line == *"WHERE NOT EXISTS"* ]] || nbad=$(( nbad + 1 ))
    done <<<"$a"
    [ "$nbad" = 0 ] && [ "$nins" -gt 0 ] && _bf_t ok "$dom: INSERT $nins 문 전부 수렴 절을 가진다" || _bf_t fail "$dom: 수렴 절 없는 INSERT $nbad/$nins"
    # (7b) wms 의 일 카운터 증분은 «이번에 새로 들어간 사실» 에서만 더한다
    if [ "$dom" = wms ]; then
      local inc; inc="$(grep -F 'INSERT INTO admin_throughput_inbound_daily' <<<"$a")"
      [[ $inc == *" FROM ins GROUP BY "* && $inc != *"FROM admin_putaway_fact"* ]] \
        && [[ $a == *"ON CONFLICT (event_id) DO NOTHING"$'\n'" RETURNING "* ]] \
        && _bf_t ok "wms: 입고 일 카운터는 RETURNING 된 새 사실만 더한다 (재실행 = +0)" \
        || _bf_t fail "wms: 입고 일 카운터 증분이 새 사실(FROM ins)에 묶이지 않았습니다 — 매 부팅 누적됩니다"
    fi

    # (8) 계획 수 주석이 실제 행 수와 같은가 (호출자의 사후 대조가 이 숫자를 믿는다)
    local tbl pl cnt
    case "$dom" in scm) tbl=purchase_orders ;; erp) tbl=approval_request ;; iam) tbl=login_history ;; wms) tbl=admin_shipment_summary ;; esac
    pl="$(bf_planned "$a" "$tbl")"
    cnt=0; while IFS= read -r line; do case "$line" in "INSERT INTO $tbl "*|"INSERT IGNORE INTO $tbl "*) cnt=$(( cnt + 1 )) ;; esac; done <<<"$a"
    [ -n "$pl" ] && [ "$pl" = "$cnt" ] && _bf_t ok "$dom: bf-planned $tbl = $pl = 실제 INSERT 수" || _bf_t fail "$dom: bf-planned $tbl='$pl' · 실제 $cnt"
  done

  # (9) 막대 모양 — headline 지표의 일별 값이 매일 ≥1, 전부 같지 않고, 주 대비 증가·감소가 섞인다
  bf_init "$E1"
  local -A per=()
  local sqlv dom2 key k v up=0 down=0 zero=0 distinct
  for dom2 in scm erp iam wms; do
    per=(); sqlv="$(_bf_gen "$dom2" "$E1")"
    while IFS= read -r line; do
      case "$dom2" in
        scm) [[ $line == "INSERT INTO purchase_orders "* && $line != *"'CANCELED'"* ]] || continue ;;
        erp) [[ $line == "INSERT IGNORE INTO approval_request "* && $line == *"'APPROVED'"* ]] || continue ;;
        iam) [[ $line == *"'FAILURE'"* ]] || continue ;;
        wms) [[ $line == "INSERT INTO admin_shipment_summary "* ]] || continue ;;
      esac
      _bf_row_day "$line"; per[$_RD]=$(( ${per[$_RD]:-0} + 1 ))
    done <<<"$sqlv"
    up=0; down=0; zero=0; distinct=""
    for (( k=BF_FIRST; k<=BF_LAST; k++ )); do
      v=${per[$k]:-0}; [ "$v" -eq 0 ] && zero=$(( zero + 1 ))
      case " $distinct " in *" $v "*) ;; *) distinct="$distinct $v" ;; esac
      if [ $(( k - 7 )) -ge "$BF_FIRST" ]; then
        [ "$v" -gt "${per[$(( k - 7 ))]:-0}" ] && up=$(( up + 1 ))
        [ "$v" -lt "${per[$(( k - 7 ))]:-0}" ] && down=$(( down + 1 ))
      fi
    done
    set -- $distinct
    if [ "$zero" = 0 ] && [ "$#" -ge 2 ] && [ "$up" -gt 0 ] && [ "$down" -gt 0 ]; then
      _bf_t ok "$dom2: headline 일별 — 0인 날 0 · 서로 다른 값 $#종 · 7일 전 대비 증가 $up / 감소 $down"
    else
      _bf_t fail "$dom2: headline 일별 — 0인 날 $zero · 값 종류 $# · 증가 $up · 감소 $down"
    fi
  done

  # (10) 빈 인자는 거절한다 (호출자가 못 얻은 id 를 빈 문자열로 넘기는 사고)
  bf_init "$E1"
  if bf_scm_sql demo-corp "" x y >/dev/null 2>&1; then _bf_t fail "bf_scm_sql 이 빈 supplier 를 받아들였습니다"; else _bf_t ok "빈 인자 거절 (scm)"; fi
  if bf_erp_sql demo-corp same same a b c d e >/dev/null 2>&1; then _bf_t fail "bf_erp_sql 이 자기결재를 받아들였습니다"; else _bf_t ok "자기결재 인자 거절 (erp)"; fi

  if [ "$_bf_t_fail" -gt 0 ]; then echo "[bf-self-test] FAIL $_bf_t_fail" >&2; return 1; fi
  echo "[bf-self-test] PASS"
}

if [ "${BASH_SOURCE[0]}" = "$0" ]; then
  case "${1:-}" in
    --self-test) bf_self_test; exit $? ;;
    *) echo "usage: bash $0 --self-test   (이 파일은 seed-*.sh 가 source 한다)" >&2; exit 2 ;;
  esac
fi
