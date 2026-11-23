# Task ID

TASK-PC-FE-385

# Title

console-web IAM 계열 `_proxy.ts` 7개의 손으로 짠 `mapError` 를 공유 팩토리 하나로 추출 + 13벌 복사된 `badRequest()` 를 한 곳으로 — **응답 바이트 불변** 리팩토링

# Status

review

# Owner

platform-console

# Task Tags

- code
- refactor
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (기계적 추출 · 동작 불변 · 기존 시험이 판정)
> 기안 2026-11-24 UTC. 출처: 소유자 요청 «모노레포 전체 리팩토링 검토» → 제안 순서 4번 → 소유자 «전부 진행».

# Dependency Markers

- 선행: 없음.
- 형제: `shared/api/proxy-factory.ts` 의 `makeProxyErrorMapper`(비-IAM 도메인 10개가 이미 사용).

# Goal

`src/app/api/{tenants,operators,groups,org-nodes,partnerships,subscriptions,domain-permission-sets}/_proxy.ts` 의 `mapError` 7개는 **줄바꿈 말고는 같은 코드**다(2026-11-24 실측: 클래스명 · 로그 이벤트 이름 · 503 메시지 문자열을 정규화하고 주석을 빼면 diff 0, 셋은 `logger.warn` 인자 줄바꿈만 다름). 고칠 일이 생기면 7곳을 따로 고쳐야 하고, 하나만 고쳐지는 것이 이 저장소가 반복해서 밟은 결함 부류다.

🔴 **기존 `makeProxyErrorMapper` 로 옮기면 안 된다 — 동작이 다르다.** IAM 계열은:

| 분기 | IAM 계열(손으로 짠 7개) | `makeProxyErrorMapper` |
|---|---|---|
| 401 | `{code: err.code, message:'session expired'}` | `{code: err.code \|\| 'UNAUTHORIZED', …}` |
| 403 | **다른 `ApiError` 와 함께 통과**(서버 `code` · `message` 그대로) | `{code, message:'not permitted'}` 로 **바꿈** |
| `ApiError` 통과 | `details` **버림** | `details` 있으면 **통과** |
| 최종 폴백 | `DOWNSTREAM_ERROR` | `SERVICE_UNAVAILABLE` |

그래서 IAM 계열 동작을 **그대로** 담는 두 번째 팩토리를 같은 파일에 둔다.

`badRequest()`(422 `VALIDATION_ERROR` · `'invalid request body'`)는 `_proxy.ts` 13개에 **바이트 동일**하게 복사돼 있다(본문 md5 13/13 일치).

# Scope

## In Scope

- `src/shared/api/proxy-factory.ts` 에 추가:
  - `makeIamProxyErrorMapper({ logPrefix, UnavailableErrorClass, unavailableMessage })` — 위 표의 IAM 열 동작 그대로(분기 순서: 401 → `NO_ACTIVE_TENANT` → `ApiError` 통과 → Unavailable 503(`logger.warn('<logPrefix>_proxy_degraded', {requestId, reason})`) → `logger.error('<logPrefix>_proxy_error', {requestId})` + 503 `DOWNSTREAM_ERROR`).
  - `badRequest()` 하나.
- 7개 `_proxy.ts` 의 `mapError` 를 `export const mapError = makeIamProxyErrorMapper({...})` 로. 각 파일의 로그 이벤트 이름과 503 메시지 문자열은 **현재 값 그대로**.
- 13개 `_proxy.ts` 의 `badRequest` 정의를 `export { badRequest } from '@/shared/api/proxy-factory'` 로 — **import 하는 쪽(route 파일 85개)은 건드리지 않는다.**
- `proxy-factory` 의 새 함수 단위 시험.

## Out of Scope

- 비-IAM 매퍼의 동작, 그리고 IAM 계열을 비-IAM 동작으로 «통일» — 응답이 바뀌는 결정이라 이 티켓이 아니다.
- `errors.ts` 의 `*UnavailableError` 클래스 통합(별도 후보).
- 각 `_proxy.ts` 의 zod 스키마 · 주석 블록.

# Acceptance Criteria

- [x] AC-1: 7개 `_proxy.ts` 에 `function mapError` 정의가 0개이고, 13개에 `function badRequest` 정의가 0개다.
- [x] AC-2: 이 7개 도메인의 **기존** route/proxy 시험이 **수정 없이** 통과한다(동작 불변의 판정). 시험 파일을 고쳐야 통과한다면 동작이 바뀐 것이다 → 멈추고 보고.
- [x] AC-3: 새 단위 시험이 표의 IAM 열 다섯 분기를 각각 단언한다 — 특히 ① 403 이 서버 `code`·`message` 를 **그대로** 낸다 ② `details` 가 응답에 **없다** ③ 폴백 코드가 `DOWNSTREAM_ERROR` 다 ④ 로그 이벤트 이름이 `<logPrefix>_proxy_degraded` / `_proxy_error` 다.
- [x] AC-4: 새 시험 bite — 팩토리의 403 분기를 비-IAM 동작으로 바꾸면 AC-3 시험이 빨강, 되돌리면 초록(복원된 트리에서 재실행).
- [x] AC-5: `tsc --noEmit` · `lint` · console-web vitest 전체 rc=0(각각 독립 실행 + 명시 rc).

# Related Specs

- `projects/platform-console/specs/` 의 console-integration-contract § 2.4 / § 2.5(오류 → HTTP 매핑) — 바뀌지 않는다.

# Related Skills

- `.claude/skills/` refactor-code.

# Related Contracts

- 없음(같은 출처 프록시 응답 바이트 불변 — 계약 변경 없음).

# Target App

- `projects/platform-console/apps/console-web`

# Implementation Notes

- `UnavailableErrorCtor` 타입은 이미 `proxy-factory.ts` 에 있다 — 재사용.
- 7개 파일 각각의 현재 로그 이름/메시지(실측): tenants `tenant management unavailable` · operators `operators unavailable` · groups `operator-group service unavailable` · org-nodes `org-node hierarchy unavailable` · partnerships `partnerships unavailable` · subscriptions `subscriptions unavailable` · domain-permission-sets `업무 권한 세트 service unavailable`. 로그 접두사는 각 파일에서 그대로 읽어 옮긴다.

# Edge Cases

- `NO_ACTIVE_TENANT` 는 401 다음, 일반 `ApiError` 통과 **앞**이어야 한다(400 이라 순서가 바뀌면 통과 분기에 먹힌다).
- 401 의 `code` 는 `err.code` 그대로(빈 문자열이어도 대체하지 않음 — 비-IAM 과 다른 점).

# Failure Scenarios

- 한 파일의 로그 접두사를 잘못 옮기면 시험은 초록이고 운영 로그 검색만 깨진다 → AC-3 ④ 를 7개 모두에 대해 파일별 값으로 확인(파라미터화 시험).
- re-export 로 바꾼 뒤 route 파일의 상대 import 가 그대로 해석되는지 — `tsc` 가 판정.

# Test Requirements

- `proxy-factory` 단위 시험(신규) + 기존 route 시험 무수정 통과.

# Definition of Done

- AC 전부 · PR CI 초록 · 머지.
