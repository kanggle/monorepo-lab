# Task ID

TASK-MONO-733

# Status

done

# Title

에러 코드 레지스트리 가드가 `errorCode()` 재정의를 못 본다 — wms 도메인 코드 32개가 가드 밖이다

# Owner

monorepo

# Task Tags

- guard
- error-handling
- scripts

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet 5 — 수집 모양 하나 추가 + 자기검사 + bite. 코드 변경 없음(가드만).

---

# Goal

`scripts/check-error-code-registry.sh` 는 «서비스가 HTTP 에러 응답에 실을 수 있는 코드» 를 세 모양으로만 모은다 —
`ErrorResponse.of("…")` · `ApiErrorBody.of("…")` · `super("…", …)`. 🔴 **예외 클래스가 `errorCode()` 를 재정의해 리터럴을
돌려주는 모양은 수집하지 않는다.** 그 모양이 wms 도메인 예외의 표준이라, 가드가 wms 코드 대부분을 **보지 못한 채 초록**이다.

## 실측 (2026-09-25 UTC · 저장소만)

- `src/main` 의 `errorCode()` 재정의 **49개** (wms inbound 16 · outbound 18 · inventory 12 · iam 3). 반환이 문자열 리터럴인 것
  **45개 → 서로 다른 코드 38개**, 나머지 4개는 필드 반환(`return errorCode;` 등 — 리터럴 수집 불가).
- 🔴 **38개 중 32개는 현재 가드의 수집 결과(`emitted` 282개)에 없다** — 즉 가드가 못 본다. 목록:
  `ADJUSTMENT_NOT_FOUND ADJUSTMENT_REASON_REQUIRED ASN_ALREADY_CLOSED ASN_NOT_FOUND ASN_NO_DUPLICATE DUPLICATE_REQUEST
  EXTERNAL_SERVICE_UNAVAILABLE INSPECTION_INCOMPLETE INSPECTION_NOT_FOUND INSPECTION_QUANTITY_MISMATCH INVENTORY_NOT_FOUND
  LOCATION_INACTIVE LOT_REQUIRED LOT_SUBSTITUTION_NOT_ALLOWED ORDER_ALREADY_SHIPPED ORDER_LINE_MISMATCH ORDER_NO_DUPLICATE
  PACKING_INCOMPLETE PACKING_UNIT_NOT_FOUND PARTNER_INVALID_TYPE PICKING_INCOMPLETE PICKING_REQUEST_NOT_FOUND
  PUTAWAY_INSTRUCTION_NOT_FOUND PUTAWAY_LINE_NOT_FOUND PUTAWAY_QUANTITY_EXCEEDED RESERVATION_NOT_FOUND
  RESERVATION_QUANTITY_MISMATCH SHIPMENT_NOT_FOUND SKU_INACTIVE TRANSFER_NOT_FOUND TRANSFER_SAME_LOCATION WAREHOUSE_MISMATCH`
- 🔵 **지금 미등록은 0건이다** — 38개 전부 `platform/error-handling.md` 에 있다. ⇒ 이 티켓은 결함 수리가 아니라
  **다음 누락을 막는 예방**이다. `TASK-BE-596` 이 `ORDER_LINE_MISMATCH` 를 이 모양으로 추가했을 때 가드는 그것을 보지 않았고,
  등록은 사람이 기억해서 됐다.
- 🔵 **수집해도 오탐이 없다(가드 헤더의 SOUND 원칙)**: wms `GlobalExceptionHandler` 가 `e.errorCode()` 를 그대로 응답의
  `code` 로 싣는다(예: inventory `GlobalExceptionHandler.java:72` `return body(status, e.errorCode(), e.getMessage());`).
  🔴 구현 전 **나머지 두 wms 서비스와 iam 3개 파일의 핸들러도** 같은지 확인하라 — 아니면 그 파일은 제외하고 사유를 적는다.

# Scope

## 포함

- 수집 모양 추가: `errorCode()` / `getErrorCode()` 재정의가 **문자열 리터럴 하나를 곧바로 반환**하는 경우(여러 줄 허용).
- 미등록 보고의 `site` 열도 새 모양을 찾게 한다(지금은 옛 세 모양만 grep 한다).
- 가드 헤더의 SOUND/NOT COMPLETE 설명에 새 모양과, 여전히 못 보는 모양(필드 반환)을 적는다.

## 제외

- 필드를 반환하는 재정의 4개 — 리터럴이 아니다. 헤더의 «accepted gap» 에 이름으로 적는다.
- `errorCode()` 가 아닌 다른 이름의 접근자(`code()` 등) — 이번 실측 범위 밖. 필요하면 별도 실측부터.
- 코드 등록 자체 — 지금 미등록 0건이다.

# Acceptance Criteria

- [x] **AC-0** — 착수 시 위 수치를 **다시 잰다**(49 · 45 · 38 · 32). 달라졌으면 달라진 값으로 진행하고 적는다.
- [x] **AC-1** — 새 모양 수집. 🔴 `--list` 의 `emitted` 가 **정확히 blind 개수만큼** 늘어야 한다(2026-09-25 기준 282 → 314).
      다른 수가 나오면 추출기가 다른 것을 잡은 것이다 — 멈추고 차이를 본다.
- [x] **AC-2** — bite 양방향: ① 레지스트리에서 `ORDER_LINE_MISMATCH` 행을 지우면 rc=1 이고 `site` 가
      `OrderLineMismatchException.java` 를 가리킨다 · ② 복원하면 rc=0. 🔴 ① 은 **새 모양으로만 잡히는 코드**여야 한다
      (옛 모양으로도 잡히는 코드로 bite 하면 새 수집이 동작했다는 증거가 아니다).
- [x] **AC-3** — 대조군: 필드 반환 재정의 4개는 여전히 수집되지 **않는다**(헤더의 선언과 동작이 같다).
- [x] **AC-4** — `scripts/` 파일 추가·삭제는 없지만 가드 한 개를 고치므로, 이 가드를 부르는 CI 잡과 필수 3종을 스테이지 후 돌린다.

# Related Specs

- `platform/error-handling.md` (레지스트리 · Change protocol)
- `tasks/done/TASK-MONO-352-…` (이 가드의 출처 — «38 live codes had drifted»)
- `projects/wms-platform/tasks/…/TASK-BE-596-…` (`ORDER_LINE_MISMATCH` — 이 모양으로 추가된 최근 코드)

# Related Contracts

- 없음. HTTP 계약·코드 값은 바꾸지 않는다.

# Edge Cases

| 상황 | 기대 |
|---|---|
| 한 파일에 `errorCode()` 가 둘(상속 계층 안) | 각 리터럴을 모두 수집한다 — 어느 쪽이든 응답에 나갈 수 있다 |
| 재정의가 삼항식 등 리터럴 둘 이상 | 이번 모양에서 제외(필드 반환과 같은 gap). 발견 시 헤더에 이름으로 적는다 |
| `errorCode()` 가 HTTP 에 안 나가는 곳(내부 재시도 분류 등) | 🔴 수집하면 오탐이다 — 그 파일의 핸들러 경로를 확인하고, 안 나가면 제외 + 사유 |

# Failure Scenarios

1. **정규식이 한 줄만 본다** → `return` 이 다음 줄인 49개 전부를 놓치고 초록(이번 실측이 처음 그렇게 0건을 냈다).
2. **bite 를 옛 모양 코드로 한다** → 새 수집이 죽어 있어도 rc=1 이 나와 통과한 것처럼 보인다(AC-2 🔴).
3. **`emitted` 증가량을 안 본다** → 추출기가 엉뚱한 리터럴(메시지 문자열 등)을 먹어도 모른다(AC-1 🔴).

---

# 구현 기록 (2026-09-25 UTC · 분석=Opus 5.5)

## 무엇을 바꿨나 — `scripts/check-error-code-registry.sh` 한 파일

- 수집 모양 추가: `errorCode()`/`getErrorCode()` 가 **리터럴 하나를 곧바로 반환**하는 재정의. 재정의가 있는 파일만 골라
  줄바꿈을 공백으로 편 뒤 `…ErrorCode() { return "CODE";` 를 뽑는다(패턴이 메서드 이름에 묶여 있어 줄을 합쳐도 다른
  메서드의 리터럴과 짝지어지지 않는다).
- 미등록 보고의 `site` 가 옛 세 모양에서 못 찾으면 재정의 파일에서 찾는다.
- 헤더: 수집 모양 셋째를 추가하고, 여전히 못 보는 **필드 반환 재정의 4개**를 이름으로 적었다(accepted gap).
- 🔴 `set -euo pipefail` 아래 **빈 결과가 스크립트를 조용히 죽이는 자리 둘**을 막았다(재정의 추출의 `grep` 무매치 ·
  `site` 조회의 무매치 — 새 모양 전용 코드는 옛 grep 이 0건이라 그대로 두면 보고 도중 종료).

## AC 판정

| AC | 결과 |
|---|---|
| AC-0 재측정 | 🟢 재정의 파일 **49** · 리터럴 매치 **45** · 서로 다른 코드 **38** · 옛 수집 밖 **32** — 기안 수치와 동일 |
| AC-1 `emitted` 증가량 | 🟢 `--list` **282 → 314 (+32)** = blind 수와 정확히 같다 · `registered` 384 불변 · rc=0 |
| AC-2 bite (새 모양 전용 코드) | 🟢 레지스트리에서 `ORDER_LINE_MISMATCH` 행 삭제(1→0행) → **rc=1**, `site` = `outbound-service/…/OrderLineMismatchException.java` · 복원(백업 복사) → **rc=0**, `git status platform/` 차이 0 |
| AC-2 대조군 | 🟢 **옛 가드**(`origin/main` 판)를 같은 삭제 상태에 돌리면 **rc=0** — 이 코드를 못 본다 ⇒ 위 rc=1 은 새 수집이 만든 것이다. 🔵 `TASK-BE-596` § 게이트 기록(«행을 지워도 rc=0») 이 기록한 바로 그 상태가 닫혔다 |
| AC-3 필드 반환 4개 | 🟢 파일별 새 패턴 매치 **0** — `NonRetryableDownstreamException` · `AccountStatusException` · `SignupNotPossibleException` · `MasterRefInactiveException` |
| AC-4 게이트 | 아래 |

🔵 **오탐 전제 확인** — wms 세 서비스 핸들러가 모두 `e.errorCode()` 를 응답 `code` 로 싣는다: inbound
`GlobalExceptionHandler.java:68` · outbound `:80`(+`:93` 503) · inventory `:72`. 기안의 «나머지 핸들러는 착수 시 확인» 을 닫았다.
🔴 첫 확인은 Grep 의 glob(`{inbound,outbound}-service/src/main/**`)이 경로에 안 맞아 **0건**을 냈다 — «없다» 가 아니라 «못 찾음»
이었고, 디렉터리를 바로 지정해 다시 쟀다.

## 게이트 기록

| 게이트 | 결과 |
|---|---|
| `bash -n scripts/check-error-code-registry.sh` | 🟢 |
| `check-error-code-registry.sh` | 🟢 rc=0 · emitted 314 |
| `check-domain-error-code-registry.sh` (레지스트리 파싱을 공유) | 🟢 rc=0 |
| 필수 3종 | 🟢 rc=0 (스테이지 후) |
| `scripts/check-*.sh` 전체 37개 (스테이지 후) | 🟢 35 · 🔴 2 — `check-erp-single-tenant-ratchet`(떠 있는 `erp-platform-mysql` 필요, 이 호스트 Docker 꺼짐) · `check-prerendered-demo-verdict`(`DEMO_API_BASE` 로 빌드한 web-store 산출물 필요). 🔵 **대조군: 같은 두 가드가 main 체크아웃(`326c4f852`)에서도 rc=1** ⇒ 이 변경과 무관한 환경 의존 |

🔴 **안 돌린 것**: 없음에 가깝다 — 코드·계약 변경 없음. `scripts/` 에 파일 추가·삭제 없음(bite 대조군용 임시 사본은 같은 실행 안에서 지웠다).
