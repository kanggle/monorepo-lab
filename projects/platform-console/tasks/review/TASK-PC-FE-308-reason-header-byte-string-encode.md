# Task ID

TASK-PC-FE-308

# Status

review

# Title

erp 결재 반려·회수 사유가 한글이면 `X-Operator-Reason` 헤더가 유효한 HTTP ByteString 이 아니라 `fetch()` 가 던지고, 콘솔은 그것을 "erp unavailable" 503 으로 오보한다 — `flat-envelope-gateway.ts` 가 `encodeURIComponent` 없이 헤더를 그대로 실어서 생긴 결함. iam-gateway 의 TASK-MONO-176 패턴을 그대로 적용해 percent-encode 한다

# Owner

platform-console

# Task Tags

- console-web
- frontend
- bugfix
- erp

---

> **분석 모델:** Opus 5.5 (1M context) / **구현 권장:** Sonnet — 한 줄 수정(`encodeURIComponent`) + 기존 형제 패턴(iam-gateway TASK-MONO-176) 복사. 설계 질문 없음.

---

# Dependency Markers

- 출처: 23차 AMI 창(2026-10-06 UTC), 라이브 실측. `platform@demo.com`(또는 운영자 계정)으로 결재 반려(`reject`)를 한글 사유 `'배치 근거 자료 보완 후 재상신 바랍니다(23차 점검).'` 로 호출 → UI 와 direct 호출 모두 `503 {"code":"NETWORK_ERROR","message":"erp unavailable"}` 두 번 재현. erp-platform-gateway / erp-platform-approval 로그에 그 요청의 흔적이 **전혀 없음**(콘솔 서버를 나가지 못했다). 대조군: 동일 요청을 ASCII 사유로 보내면 `200 REJECTED`. 사유 없는 approve 는 이전에 성공한 바 있음.
- 선행/후속: 없음. 형제 결함(같은 메커니즘)은 이미 TASK-MONO-176(`iam-gateway.ts`)에서 고쳐져 있고, 그 고정도 `operators-api.test.ts`/`tenants-client.test.ts` 등에 ASCII-정규식 + `encodeURIComponent` 등가 + `decodeURIComponent` 라운드트립 3단 단언 패턴으로 남아 있다 — 이 티켓은 그 패턴을 **같은 저장소 안의 형제 함수**(`flat-envelope-gateway.ts`)에 복사한다 (`feedback_grep_siblings_before_fixing_yourself` — 08-30 메모리 규칙).

# Goal

`apps/console-web/src/shared/api/flat-envelope-gateway.ts` 의 `prepareFlatHeaders()` 가 `X-Operator-Reason` 헤더를 `encodeURIComponent` 로 percent-encode 해서 싣는다 — 한글(비-Latin-1) 사유가 `fetch()` 의 ByteString 헤더 검사에서 던지는 일이 없어야 한다. 이 헤더의 유일한 실사용 경로인 erp `approval-service` 의 reject/withdraw/reasoned-approve 가 한글 사유로도 성공해야 한다.

# Scope

## In Scope

- `apps/console-web/src/shared/api/flat-envelope-gateway.ts:429-430`(구 라인 번호) — `headers['X-Operator-Reason'] = req.operatorReason` → `encodeURIComponent(req.operatorReason)`로 교체. 관련 JSDoc(모듈 헤더의 "Mutation headers" invariant 절, `FlatEnvelopeGatewayRequest.operatorReason` 필드 주석) 갱신.
- `features/erp-ops/api/approval-call.ts`(`CallOptions.operatorReason` 주석) / `features/erp-ops/api/approval-mutations.ts`(모듈 헤더 주석) — percent-encoding 사실 + erp 프로듀서가 이 헤더를 **읽지 않는다**는 사실(AC-0 재측정)을 기록.
- `tests/unit/approval-api.test.ts` — 기존 3개 단언(`approve WITH reason`, `reject`, `withdraw`)이 **원문 한글이 헤더 값**이라고 단언하고 있었다(버그가 테스트에 그대로 코드화되어 있었다) → ASCII-정규식 + `encodeURIComponent` 등가 + `decodeURIComponent` 라운드트립 3단 단언으로 교체(iam-gateway 형제 패턴, `operators-api.test.ts:238-266` 미러).

## Out of Scope

- erp `approval-service` 백엔드 변경 — AC-0 에서 확인: 프로듀서는 `X-Operator-Reason` 헤더를 **전혀 읽지 않는다**(아래 참조). 디코드가 필요 없으므로 후속 erp 티켓도 불필요.
- 계약(`console-integration-contract.md`) 변경 — 이 헤더의 인코딩 방식은 계약이 규정하는 바가 아니고(계약은 "echo via header" 서술만 가짐, 인코딩 세부는 구현 디테일), 변경 없음.
- `callFlatEnvelopeGateway` 의 에러 매핑 세분화(헤더 생성 `TypeError` 를 "client bug" 로 별도 분류) — 아래 Failure Scenarios § 2 참조. 이번 수정으로 이 결함 경로 자체가 없어지므로(인코딩하면 더 이상 던지지 않음) 긴급성이 없고, `TypeError` 를 범용적으로 "헤더 생성 오류" 로 재분류하면 URL 생성 등 다른 합법적 `TypeError` 원인과 혼동될 위험이 있어 범위 밖으로 둔다.
- `flat-envelope-gateway.ts` 의 다른 5개 소비 클라이언트(`scm-gateway.ts`/`ledger-client.ts`/`finance-accounts-read.ts`/`fan-api.ts`/`erp-client.ts`/`delegation-api.ts`) — 전수 grep 으로 확인: 이 중 **어느 것도 `operatorReason` 필드를 설정하지 않는다**(전부 `No matches found`). 공유 코어를 고치므로 혜택은 받지만 동작 변화는 없다(보내던 값이 없으므로).

# Acceptance Criteria

- [x] **AC-0** — 착수 시 재측정(file:line): ① `flat-envelope-gateway.ts` 의 버그 위치, ② `iam-gateway.ts` 의 형제 고정(TASK-MONO-176) 위치, ③ erp `approval-service` 프로듀서가 `X-Operator-Reason` 헤더를 실제로 읽는지(컨트롤러 전수 grep), ④ `flat-envelope-gateway.ts` 의 `operatorReason` 필드를 실제로 설정하는 호출부 전수(7개 call-core 중 몇 개).
- [x] **AC-1** — 단위 시험: 한글 사유 → 헤더 값이 ASCII-only(`/^[\x00-\x7F]*$/`) 이고 `encodeURIComponent(원문)` 과 동일하며 `decodeURIComponent` 로 원문이 복원된다. ASCII 사유는 의미 불변(`encodeURIComponent` 가 공백만 `%20` 으로 바꾸는 등 — 영문 단어만 쓰는 기존 셀은 그대로 통과).
- [x] **AC-2** — bite: 고정을 되돌리면(복사본 비교 기반, `git checkout --` 미사용) `approve WITH reason`/`reject`/`withdraw` 3개 셀만 빨강, 나머지 20개는 그대로 초록.
- [ ] **AC-3 (라이브, ⚪)** — 다음 데모 창(23차 이후): 한글 사유로 `reject` → `200 REJECTED`(지금은 `503`). `withdraw` 도 동일하게 확인.

# Related Specs

- `projects/platform-console/specs/services/console-web/architecture.md` (erp-ops 섹션 — approval 변이 훅)
- `projects/erp-platform/specs/contracts/http/approval-api.md` § *Operator reason*(`:134-138`) — "accept the reason in the request body and echo it via the X-Operator-Reason header for the audit trail"; 이 서술은 유지(바뀌지 않음) — 프로듀서가 헤더를 읽지 않는다는 사실은 이 문장과 상충하지 않는다(헤더는 "echo" 대상일 뿐, 그 echo 가 실제로 코드에 구현돼 있지는 않다 — 계약이 과장한 것이 아니라, 헤더가 **감사 추적 echo 의도**였고 프로듀서 구현이 그 의도를 완성하지 않은 상태로 멈춰 있다는 뜻).

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` — 변경 없음(이 헤더의 인코딩 방식은 계약이 규정하지 않는 구현 디테일).

# Edge Cases

- **ASCII 사유** — `encodeURIComponent` 는 영문/숫자/일부 기호는 그대로 두고 공백 등만 `%20` 류로 바꾼다. `operators-api.test.ts:225` 가 이미 `'onboarding new support operator'` → `'onboarding%20new%20support%20operator'` 로 바뀜을 단언하는 형제 패턴이라, erp 쪽도 같은 변화(공백이 있으면 `%20`)가 생긴다 — 프로듀서가 헤더를 안 읽으므로 영향 없음.
- **approve (optional reason)** — 사유 없이 approve 하면 `operatorReason` 자체가 `undefined` 라 헤더가 전혀 안 붙는다(기존 동작 그대로, AC-1 대상 아님).
- **빈 문자열 사유** — `approval-mutations.ts` 는 `reason ? {...} : {}` 로 spread 하므로 빈 문자열(`''`)은 falsy → 헤더 자체가 안 붙는다(이 티켓이 만지는 코드 경로 밖 — reject/withdraw 는 호출부에서 reason 을 필수 파라미터로 받아 producer 가 body 쪽에서 빈 값을 400 으로 거부).
- **erp 프로듀서의 헤더 무시** — AC-0 에서 확인했듯 `ApprovalRequestController.java` 의 `approve`/`reject`/`withdraw` 는 `@RequestHeader("Idempotency-Key")` 만 받고 `X-Operator-Reason` 은 전혀 파라미터로 선언돼 있지 않다. 즉 이 헤더는 **프로듀서에 한 번도 도달해 효과를 낸 적이 없는 값**이었다(인코딩 여부와 무관하게). 고정 전에는 그 사실이 안 보였던 이유는 헤더가 ByteString 검사에서 먼저 죽어 요청 자체가 안 나갔기 때문 — 고정 후에는 요청이 나가고, 헤더는 여전히 프로듀서에 무시되지만 body `reason` 이 진짜 사유 전달 경로임이 드러난다.

# Failure Scenarios

1. **인코딩만 하고 테스트는 안 고친다** — `tests/unit/approval-api.test.ts` 의 기존 3개 단언이 원문 한글을 기대하므로 고정 자체가 CI 에서 빨강이 된다(이번 PR 에서 함께 고쳤다).
2. **에러 매핑을 함께 욕심내 고친다** — `callFlatEnvelopeGateway` 의 catch 블록은 7개 소비 클라이언트가 공유하는 코어다. `TypeError`(헤더 생성 실패)를 범용적으로 "client bug" 로 재분류하면, URL 생성 실패 등 **진짜 네트워크측 `TypeError`** 까지 오분류할 위험이 생긴다 — 이번 티켓은 근본 원인(인코딩 누락)을 없애 이 경로 자체가 더는 발생하지 않게 하는 것으로 충분하다고 판단해 범위 밖으로 뺐다(Out of Scope 참조).
3. **다른 5개 소비 클라이언트도 손 댄다** — 전수 grep 으로 `operatorReason` 필드를 설정하지 않음을 확인했으므로, 그 클라이언트들의 동작 변화는 없다(공유 코어 수정의 자연스러운 부작용 범위 안).

---

# Implementation Notes (2026-10-06 UTC)

> 분석=Opus 5.5 (1M context) / 구현=Opus 5.5 (1M context).

## AC-0 — 재측정 (착수 시)

| 칸 | file:line | 읽은 것 |
|---|---|---|
| 버그 위치 | `apps/console-web/src/shared/api/flat-envelope-gateway.ts:429-430`(수정 전) | `if (req.operatorReason !== undefined) { headers['X-Operator-Reason'] = req.operatorReason; }` — 인코딩 없음 |
| 형제 고정 | `apps/console-web/src/shared/api/iam-gateway.ts:336,349` | `headers['X-Operator-Reason'] = encodeURIComponent(reason);` (TASK-MONO-176, 주석 `:317-318`에 "percent-encode the reason so a non-Latin-1 value does not make `fetch()` throw on the ByteString header") — 이번 고정이 글자 그대로 복사한 패턴 |
| erp 프로듀서가 헤더를 읽는가 | `projects/erp-platform/apps/approval-service/src/main/java/com/example/erp/approval/presentation/controller/ApprovalRequestController.java:100-141` | `approve`/`reject`/`withdraw` 전부 `@RequestHeader("Idempotency-Key")` 만 선언. `X-Operator-Reason` 은 **파라미터로도, 코드 전체에도 등장하지 않음**(erp-platform 전체 `grep -r "Operator-Reason"` → `apps/` 아래 매치 0건). 사유는 `@RequestBody` 의 `req.reason()` 에서만 읽는다(`reject`:124, `withdraw`:138, `approve`:106) |
| 계약 서술 | `projects/erp-platform/specs/contracts/http/approval-api.md:134-138` | "accept the reason in the request body **and** echo it via the `X-Operator-Reason` header for the audit trail" — 코드가 이 "echo" 를 구현하지 않은 상태(계약과 구현의 괴리, 이 티켓이 만들지 않은 기존 상태 — 범위 밖, Out of Scope) |
| `operatorReason` 필드 설정 호출부 전수 | `callFlatEnvelopeGateway` 소비 클라이언트 7개(`scm-gateway.ts`/`ledger-client.ts`/`finance-accounts-read.ts`/`fan-api.ts`/`erp-client.ts`/`delegation-api.ts`/`approval-call.ts`) | `operatorReason` 필드를 실제로 설정(비-`undefined`)하는 곳은 **`approval-call.ts:105`(← `approval-mutations.ts:97,117,137`) 하나뿐**. 나머지 6개는 grep 0건 — 이 헤더를 쓰는 유일한 erp 결재 전이(approve-with-reason/reject/withdraw) |

## 구현

- `flat-envelope-gateway.ts` — `prepareFlatHeaders()` 의 `X-Operator-Reason` 대입을 `encodeURIComponent(req.operatorReason)` 으로 교체 + 모듈 헤더 invariant 절 + `FlatEnvelopeGatewayRequest.operatorReason` JSDoc 갱신.
- `approval-call.ts` — `CallOptions.operatorReason` JSDoc 에 percent-encoding + "프로듀서는 이 헤더를 읽지 않는다" 사실 기록.
- `approval-mutations.ts` — 모듈 헤더 주석에 동일 사실 추가.
- `tests/unit/approval-api.test.ts` — 기존 `approve WITH reason`/`reject`/`withdraw` 3개 셀의 단언을 ASCII-정규식(`/^[\x00-\x7F]*$/`) + `toBe(encodeURIComponent(원문))` + `decodeURIComponent` 라운드트립 3단으로 교체(`iam-gateway` 형제 `operators-api.test.ts:238-266` 미러). 셀 이름에 `TASK-PC-FE-308` 표식.
- `tests/unit/approval-proxy.test.ts` — 전체 스위트 1차 실행에서 **같은 버그가 코드화된 네 번째 셀**(`reject WITH reason → upstream POST .../reject + X-Operator-Reason + body reason`, erp 라우트 핸들러를 통한 프록시 경로)을 추가로 발견 — 같은 3단 단언으로 교체. 이 발견 자체가 "고치기 전에 형제를 grep" 규칙의 실측 사례(approval-api.test.ts 만 보고 approval-proxy.test.ts 를 놓칠 뻔함 — 전체 스위트 실행이 잡아냄).

## 검증 (worktree `monorepo-lab-pcfe308`, `pnpm install --frozen-lockfile`)

- **AC-2 bite** — `flat-envelope-gateway.ts` 를 스크래치패드 백업(복사본) 대조로 되돌려(`encodeURIComponent` 제거) `npx vitest run tests/unit/approval-api.test.ts` 실행 → **3 failed / 20 passed**(실패 셸: `approve WITHOUT reason → ... WITH reason → header echoed + body`, `reject → ...`, `withdraw → ...` — 정확히 고친 3개 셀만). 원본은 `cmp` 로 백업과 바이트 일치 확인 후 복원.
- **AC-1 + 고정 확인** — 복원 후 `tests/unit/approval-api.test.ts` **23 passed / 23**, rc=0.
- `pnpm lint` → `✔ No ESLint warnings or errors`, rc=0 (1차 + `approval-proxy.test.ts` 수정 후 2차 모두).
- `npx tsc --noEmit` → rc=0 (1차 + 2차 모두, 출력 없음).
- `npx vitest run --minWorkers 2 --maxWorkers 2`(전체 스위트, 짝 워커 메모리 규칙) — **1차 실행에서 `approval-proxy.test.ts` 의 네 번째 미발견 셸이 빨강**(`1 failed | 3804 passed`, 정확히 그 셸 하나). 그 셸을 고친 뒤 **2차 전체 재실행: 337 파일 / 3805 테스트 전량 통과, rc=0**.

### 4차원 (close chore 시 채울 자리 — 지금은 비움)

| 차원 | 결과 |
|---|---|
| (a) | — |
| (b) | — |
| (c) | — |
| (d) | AC-0~AC-2 `[x]`, **AC-3 = 라이브 ⚪, 다음 데모 창에서 닫는다** |
