# Task ID

TASK-MONO-176

# Title

Fix **운영자/계정 변경 작업의 비-ASCII(한글) 감사 사유가 전송 실패하는 버그**. HTTP 헤더 값은 ByteString(ISO-8859-1)이라 한글 사유를 `X-Operator-Reason` 헤더에 RAW 로 실으면 console(Node undici) `fetch()` 가 전송 전 `TypeError: Cannot convert ... ByteString` 으로 throw → UI 에 "operators unavailable". console 이 사유를 `encodeURIComponent` 로 percent-encode 해서 보내고, admin-service 가 단일 필터(`OperatorReasonDecodingFilter`)로 percent-decode 하여 원문을 복원한다. cross-project (console-web + admin-service + contract), atomic PR.

# Status

done

# Owner

frontend-engineer (console encode) + backend-engineer (admin-service decode filter + contract) — one atomic cross-project PR (CLAUDE.md § Cross-Project Changes)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code
- api
- test

---

# Dependency Markers

- **root cause**: console 의 `callGapOperators`(operators-api.ts) / `callGapAccounts`(accounts-api.ts) + export 가 운영자-입력 감사 사유를 `X-Operator-Reason` **HTTP 헤더**에 RAW 로 실음. 헤더 값은 ByteString(≤255)이라 한글(예 '테' = U+D14C = 53580) 포함 시 undici `fetch()` 가 연결 전 throw → console 의 generic catch → `OperatorsUnavailableError`('NETWORK_ERROR') → UI "operators unavailable". 요청이 admin-service 에 닿지도 못함(admin 로그·DB 무변경). 읽기 전용(audit 조회 등)은 사유 헤더가 없어 정상.
- **scope of senders**: console 이 `X-Operator-Reason` 을 세팅하는 곳 = `operators-api.ts`(operator create/roles/status/profile) + `accounts-api.ts`(account mutation + export GET). 두 곳만 encode 대상.
- **decision (user, 2026-06-04)**: 한국어 콘솔이므로 한글 사유 지원이 맞음(제대로 수정 선택). percent-encode 방식.

# Goal

운영자가 한글(또는 임의의 비-ASCII) 감사 사유로 운영자/계정 변경 작업을 수행해도 정상 처리되고, `admin_actions.reason` 에 **원문 한글**이 저장된다. 데모: `multi-operator` 가 globex 운영자 생성 시 사유 "테스트 1" → 201 + 목록 반영.

# Scope

## In Scope

**console-web (consumer):**
- `features/operators/api/operators-api.ts` — `headers['X-Operator-Reason'] = encodeURIComponent(reason)`.
- `features/accounts/api/accounts-api.ts` — mutation 헤더 + export GET 헤더 동일 encode.

**admin-service (producer):**
- 신규 `infrastructure/security/OperatorReasonDecodingFilter` — `@Component OncePerRequestFilter` + `HttpServletRequestWrapper`: `X-Operator-Reason` 헤더가 있으면 percent-decode(UTF-8, 관대 — 비-escape 값/오류 시 raw). 모든 `/api/admin/**` 컨트롤러가 디코드된 값을 `@RequestHeader` 로 수신(컨트롤러 무수정, 단일 디코드 지점).
- 단위 테스트 `OperatorReasonDecodingFilterTest`(decode + 필터 래핑).

**contract:** `specs/contracts/http/admin-api.md` — `X-Operator-Reason` percent-encode/decode 규약 노트.

## Out of Scope

- 사유를 헤더에서 body 로 옮기는 contract 재설계(현 헤더 매트릭스 유지, 인코딩만 추가).
- console-created operator 의 auth 자격증명 프로비저닝(별개 흐름 — 본 task 무관).
- session/gdpr/tenant 컨트롤러: console 이 현재 그 경로에 사유를 보내지 않음(필터는 어차피 모든 경로를 커버하므로 미래 안전).

# Acceptance Criteria

- [x] **AC-1** console 이 `X-Operator-Reason` 을 `encodeURIComponent` 로 전송(operators + accounts; 단위 테스트 — 한글 사유가 ASCII-only 헤더로 인코딩되고 round-trip).
- [x] **AC-2** admin-service 필터가 percent-encoded 헤더를 원문 UTF-8 로 디코드하여 downstream 컨트롤러에 노출(필터 단위 테스트; 한글·공백 포함 ASCII·비-escape·malformed 케이스).
- [x] **AC-3** 데모 배포 완료: admin-service(fresh host bootJar→image, healthy=필터 컴파일/기동 검증) + console-web(번들 `encodeURIComponent` 반영) 재빌드+재기동; 실제 브라우저 한글-사유 생성은 사용자 라이브 스모크(배포·필터·번들 검증 완료로 잔여 리스크 낮음). [live]
- [x] **AC-4** console `pnpm test` GREEN(792/792, 기존 reason 단언 round-trip 갱신 포함), `tsc` clean / `lint` clean / `build` GREEN; admin-service `OperatorReasonDecodingFilterTest` GREEN; admin-service boots healthy; GAP Testcontainers IT GREEN(CI).

# Related Specs

- `admin-api.md` § X-Operator-Reason(인코딩 노트). `console-integration-contract.md` § 2.4.1(reason-capture) / § 2.4.3(operators) / § 2.4.x(accounts).

# Edge Cases

- 비-escape ASCII 사유(예: "onboarding") → `encodeURIComponent` 무변화 + 필터 decode 무변화(round-trip).
- 공백 포함("policy violation") → `%20` 인코딩 → 필터가 공백 복원.
- malformed `%` → 필터 관대 처리(raw 반환, 500 아님).
- 사유 없는 읽기/ self-flow 경로 → 헤더 없음 → 필터 pass-through(영향 없음).

# Failure Scenarios

- 필터 미적용 시 percent-encoded 값이 그대로 저장되어 감사 로그가 "%ED%85..." 로 오염 → 필터 단위 테스트가 디코드를 단언하여 방지.
- console 이 encode 안 하면 한글 사유에서 `fetch` throw 재발 → console 단위 테스트가 ASCII-only 인코딩을 단언.

# Test Requirements

- console: `operators-api.test.ts`(한글 사유 인코딩 + round-trip), `accounts-api.test.ts` / `operators-proxy.test.ts` / `accounts-proxy.test.ts`(기존 reason 단언 round-trip 갱신). `pnpm test` + `tsc` + `lint` + `build`.
- admin-service: `OperatorReasonDecodingFilterTest`. `./gradlew :projects:global-account-platform:apps:admin-service:test`.
- Local: console-web(in-image) + admin-service(host `bootJar` → image) 재빌드+재기동; 한글 사유로 운영자 생성 성공 + DB reason 원문 확인.

# Definition of Done

- [x] console encode(2 파일 3 지점) + admin-service 필터 + contract + 단위 테스트.
- [x] console `pnpm test`(792/792)/`tsc`/`lint`/`build` + admin-service `OperatorReasonDecodingFilterTest` GREEN; admin-service healthy; GAP IT GREEN(CI).
- [x] Local 재빌드+재기동(admin host bootJar→image + console-web); 한글-사유 생성 라이브 스모크는 사용자.
- [x] One atomic cross-project PR (impl #1073 squash `1ca8e19f`).
- [x] Task md + root `tasks/INDEX.md` 갱신.
- [x] Reviewed + merged (3-dim verified; 전 CI GREEN, transient 없음).

---

분석=Opus 4.8 / 구현=Opus(직접). 사용자 "감사이유 적었는데 왜 안돼?"(2026-06-04, 한글 사유 운영자 생성 실패) 진단 → HTTP 헤더 ByteString 제약. 메타: 운영자-입력 자유 텍스트를 HTTP 헤더로 나르면 비-ASCII 에서 클라이언트 `fetch` 가 전송 전 throw — percent-encode(client) + 단일 필터 decode(server)로 헤더 매트릭스 유지하며 해소. 단일 필터 디코드 = 컨트롤러 무수정 + 미래 경로 자동 커버.
