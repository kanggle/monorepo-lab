# Task ID

TASK-MONO-349

# Title

⏳ DEFERRED — iam auth 계약의 방향-미정 모순 3건: `INVALID_STATE`(iam 401 ↔ ecommerce 400 ↔ 레지스트리 400 + 거짓 "identical semantics" 단언) · `INVALID_CREDENTIALS`(한 서비스, 한 코드, 두 상태) · `INVALID_CODE`(emitter 0건 유령 행 — 실제로는 502 로 나감). **AC-0 방향 결정은 사람 몫.**

# Status

ready

# Owner

monorepo

# Task Tags

- contracts
- bug

---

# ⏳ DEFERRAL GUARD (read first)

이 task 는 **의도적으로 보류된 백로그**다. root 리프사이클에 `backlog/` 폴더가 없어 `ready/` 에 두되, **AC-0 를 먼저 통과**해야 한다. AC-0 는 **에이전트가 단독으로 통과시킬 수 없다** — 방향 선택이 **라이브 로그인 흐름의 클라이언트 가시 계약 변경**이기 때문이다. 일괄 구현 픽업(`/process-tasks` 등) 시에도 AC-0 에서 **STOP**.

---

# Goal

`platform/error-handling.md` 가 두 코드에 대해 **실제 배포된 동작과 다른 상태를 등록**하고 있고, 그중 하나는 **명시적으로 거짓인 단언**을 담고 있다.

## 모순 1 — `INVALID_STATE` (거짓 단언 포함)

**레지스트리** `platform/error-handling.md:81`:

> `| INVALID_STATE | 400 | OAuth state 파라미터 누락/오형/저장된 CSRF 토큰과 불일치 (RFC 6749 §10.12). TASK-MONO-052 에서 Platform-Common 승격 — **emitted by both ecommerce auth-service and IAM auth-service with identical semantics** |`

**배포 현실**:

| 서비스 | 코드 | 상태 |
|---|---|---|
| ecommerce `auth-service` | `presentation/advice/GlobalExceptionHandler.java:58-63` — `@ResponseStatus(HttpStatus.BAD_REQUEST)` | **400** |
| iam `auth-service` | `presentation/exception/AuthExceptionHandler.java:139-144` — `ResponseEntity.status(HttpStatus.UNAUTHORIZED)` | **401** |

→ **"identical semantics" 는 거짓이다.** 같은 코드가 두 서비스에서 다른 상태로 나가고, 레지스트리는 한쪽(400)만 등록했다. iam 의 계약(`auth-api.md:691`)은 **401 로 코드와 일치**하므로, 어긋나는 축은 iam-계약↔레지스트리다.

## 모순 2 — `INVALID_CREDENTIALS` (한 코드 두 상태, 한 서비스 안에서)

**레지스트리** `:77`: `| INVALID_CREDENTIALS | 401 | Login credentials rejected |` — 단일 상태.

**iam auth-service 배포 현실** — 같은 코드를 두 상태로 발행한다:

| 흐름 | 핸들러 | 상태 |
|---|---|---|
| 로그인 실패 | `AuthExceptionHandler:17-25` | **401** |
| 비밀번호 변경 시 현재 비번 불일치 | `AuthExceptionHandler:49-53` (`CurrentPasswordMismatchException`) | **400** |

iam 계약은 **둘 다 문서화**한다(`auth-api.md:362` = 401, `:726` = 400 "현재 패스워드 불일치"). 즉 **의도된 이중 상태**이나 **레지스트리가 그 예외를 담지 않는다**.

이건 `TASK-MONO-348` 이 finance/erp 에서 제거한 것과 **같은 클래스**(한 코드 두 상태 → `code` 로 분기하는 클라이언트가 깨짐)지만, 저기와 달리 **계약이 그 이중성을 명시적으로 승인**하고 있어 "결함"이라 단정할 수 없다. 레지스트리에 선례도 있다 — `DOWNSTREAM_ERROR`(`:134`)는 502/503 **의도적 이중 등록**이며 "deliberate, not drift (TASK-MONO-244)" 라고 못박아 두었다.

---

# AC-0 — 착수 게이트 (사람이 통과시켜야 함)

**에이전트는 방향을 스스로 고르지 말 것.** 각 모순마다 아래에서 **사람이 하나를 선택**해야 한다. 에이전트가 임의로 "문서만 고치는" 쪽을 고르면 그건 **아무 결정 없이 일어난 계약 완화**이고, "코드를 고치는" 쪽을 고르면 **라이브 로그인 응답의 클라이언트 가시 변경**이다.

## 모순 1 — `INVALID_STATE`

- **방향 A — iam 을 400 으로 정렬** (코드 + `auth-api.md:691` 수정). 원칙적으로 가장 정합: RFC 6749 §10.12 는 CSRF/파라미터 문제이고, state 불일치는 **인증 실패가 아니라 잘못된 요청**이다. 레지스트리·ecommerce 와도 일치. **대가**: 라이브 OAuth 콜백의 응답 상태가 401→400 으로 바뀐다(클라이언트 가시).
- **방향 B — 레지스트리를 이중 등록으로** (`DOWNSTREAM_ERROR` 502/503 선례 적용: "400=ecommerce, 401=iam, 의도적 분리"). **대가**: 두 서비스가 같은 코드로 다른 말을 하는 상태를 **공식화**한다. 정당화가 필요하다 — 정말 의미가 다른가, 아니면 그냥 안 맞춘 것인가? (후자로 보인다.)
- **방향 C — ecommerce 를 401 로 정렬.** 원칙적으로 약하다(400 이 더 맞다). 추천하지 않음.

## 모순 2 — `INVALID_CREDENTIALS`

- **방향 A — 레지스트리에 예외 주석 추가**(가장 가벼움): `INVALID_CREDENTIALS | 401` 행에 "iam 비밀번호 변경 흐름은 동일 코드를 **400** 으로 발행 — 로그인 실패가 아니라 요청 검증 실패(`auth-api.md:726`). 의도적."
- **방향 B — 별도 코드 신설**(예: `CURRENT_PASSWORD_MISMATCH` 400). 코드↔상태 1:1 을 회복하지만 **새 코드 = 클라이언트 가시 변경**.

## 모순 3 — `INVALID_CODE` (유령 행: 계약에만 있고 아무도 발행하지 않음)

`auth-api.md:692` 는 소셜 로그인 에러표에 이렇게 적어 둔다:

> `| 401 | INVALID_CODE | authorization code 교환 실패 (만료, 위조 등) |`

**전 코드베이스에 이 코드를 발행하는 곳이 0 건이다** (`projects/` + `libs/` Java 전수 grep). 계약이 약속한 응답이 **존재하지 않는다.**

실제로 authorization-code 교환이 실패하면 `OAuthProviderException` 이 던져지고 → `AuthExceptionHandler:161-167` → **502 `PROVIDER_ERROR`**("OAuth provider token endpoint 또는 userinfo API 장애")로 나간다. 즉 **만료·위조된 code(=클라이언트 오류)가 게이트웨이 장애(502)로 보고된다.** 이건 `TASK-MONO-348` 의 ledger `CURRENCY_MISMATCH` 와 같은 부류다 — 코드가 원인을 잘못 이름 붙인다.

**방향 선택 (사람 몫)**:

- **방향 A — 행 삭제**(계약을 현실에 맞춤). 가장 가볍지만, "구현 예정이던 것"을 없애는 것일 수 있다.
- **방향 B — 구현**(현실을 계약에 맞춤): provider 의 code-교환 실패를 upstream 장애와 **구분**해 `401 INVALID_CODE` 로 내보낸다. 502 를 4xx 로 정정하는 **의미상 옳은 방향**이지만 **코드 변경 + 클라이언트 가시**다.

방향 B 가 원칙적으로 옳아 보인다(만료된 code 는 provider 장애가 아니다). 하지만 **502 → 401 은 재시도 로직을 가진 클라이언트의 동작을 바꾼다.**

---

**셋 다 "지금 당장 아픈 곳"이 아니다** — 프런트/BFF 가 이 코드들로 분기하는 곳은 **없다**(TS/TSX 전수 grep 0건). 착수 트리거를 만나기 전엔 **보류가 올바른 상태**다.

# 착수 트리거 (하나라도 관측 시)

1. 클라이언트(프런트/BFF/외부)가 `INVALID_STATE` 또는 `INVALID_CREDENTIALS` 의 **HTTP 상태로 분기**하기 시작한다.
2. 레지스트리의 "identical semantics" 문구를 **근거로 삼은 산출물**(코드/문서/에이전트 판단)이 관측된다 — 거짓 전제가 실제 피해를 낳은 시점.
3. iam ↔ ecommerce 인증 표면을 통합/공유하는 작업이 착수된다.

---

# Scope (방향 결정 후에만 유효)

- `platform/error-handling.md` (`:77`, `:81`) — 공유 레지스트리
- 방향 A 선택 시: `projects/iam-platform/apps/auth-service/.../AuthExceptionHandler.java` + `specs/contracts/http/auth-api.md`
- 방향 C 선택 시: `projects/ecommerce-microservices-platform/apps/auth-service/.../GlobalExceptionHandler.java` + 해당 계약

**공유 경로(`platform/`)를 건드리므로 root task** (`tasks/INDEX.md` § When to Use Root vs Project Tasks).

---

# Acceptance Criteria

- **AC-0** — 위 방향을 **사람이 선택**했다. 미선택 시 **STOP**(no-op 이 올바른 구현).
- **AC-1** — 선택된 방향에 따라 레지스트리 · 코드 · 계약 **셋이 한 값을 말한다**.
- **AC-2** — `INVALID_STATE` 의 "identical semantics" 문구가 **사실이 되거나, 제거되거나, 이중 등록으로 정정**된다. 거짓인 채로 남지 않는다.
- **AC-3** — 코드를 바꾸는 방향이면 **핸들러 단위 테스트로 새 (code, status) 쌍을 고정**한다(MONO-348 교훈: 이 표면들은 테스트가 없어서 드리프트했다).
- **AC-4** — 상태 변경이 클라이언트 가시라면 **어디에도 소비자가 없음**을 재확인(grep)하고 그 사실을 근거로 기록한다.

# Related Specs

- `platform/error-handling.md` (`:77` `:81` `:134` — `DOWNSTREAM_ERROR` 이중등록 선례)
- 선행 맥락: `TASK-MONO-052`(INVALID_STATE 를 Platform-Common 으로 승격 — "identical semantics" 문구의 출처) · `TASK-MONO-244`(의도적 이중등록 선례) · `TASK-MONO-348`(한 코드 두 상태를 결함으로 규정하고 제거한 선행 판례)

# Related Contracts

- `projects/iam-platform/specs/contracts/http/auth-api.md` (`:362` `:691` `:726`)
- ecommerce auth 계약(해당 표면)

# Edge Cases

- **`TASK-MONO-348` 과의 관계** — 저 task 는 "한 코드 두 상태 = 결함" 이라는 판례를 세웠다. 하지만 그건 **계약이 그 이중성을 승인하지 않은** 경우였다(오히려 표가 단일 상태를 선언했다). 여기 `INVALID_CREDENTIALS` 는 **계약이 명시적으로 이중**이다. 판례를 기계적으로 적용하지 말 것.
- **`DOWNSTREAM_ERROR` 선례가 방향 B 를 정당화하지 않는다** — 저건 "operator semantics 가 실제로 다르다"는 **논증이 있었다**. 여기서 같은 결론을 내려면 **왜 iam 의 state 불일치가 인증 실패(401)이고 ecommerce 의 것은 잘못된 요청(400)인지**를 설명할 수 있어야 한다. 설명할 수 없다면 그건 이중등록이 아니라 **그냥 드리프트**다.

# Failure Scenarios

- **에이전트가 AC-0 를 스스로 통과시킨다** — 가장 큰 위험. "문서만 고치면 안전하다"는 착각으로 레지스트리를 iam 에 맞춰 401 로 바꾸면, RFC 와 ecommerce 에 반하는 값을 **공식화**하게 된다.
- **`TASK-BE-500` 과 뒤섞인다** — BE-500 은 같은 파일(`auth-api.md`)을 건드리지만 **방향이 유일한** 드리프트(403→423/410)만 다룬다. 두 task 를 합치면 사람-결정이 필요한 변경이 자동 승인 변경에 묻힌다.
