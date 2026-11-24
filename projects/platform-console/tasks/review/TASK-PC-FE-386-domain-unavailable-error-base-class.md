# Task ID

TASK-PC-FE-386

# Title

console-web `shared/api/errors.ts` — 본문이 같은 `*UnavailableError` 20개를 공통 기반 클래스 하나로 (JSDoc 유지 · `instanceof` · `name` · 속성 순서 불변)

# Status

review

# Owner

platform-console

# Task Tags

- code
- refactor
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (기계적 · 기존 시험이 판정)
> 기안 2026-11-24 UTC. 출처: 소유자 «추천 순서대로 진행» → 리팩토링 추천 2순위. (1순위 ecommerce 통합 테스트 기반 클래스는 AC-0 성격의 전제 측정에서 반박되어 착수하지 않음 — 최근 200 CI 런에서 ecommerce 통합 A/B/C 132/132 성공, 실패 0, 각 ~7분.)

# Dependency Markers

- 선행: 없음. 형제: `TASK-PC-FE-385`(같은 파일군의 proxy 매퍼 추출, done).

# Goal

`src/shared/api/errors.ts`(1262줄)의 `export class XxxUnavailableError extends Error` 중 **20개가 이름만 다르고 본문이 바이트 동일**하다(2026-11-24 실측 — 클래스명을 `X` 로 정규화해 묶으면 한 무리 20개):

`Accounts · Audit · Operators · Rbac · Wms · WmsOutbound · Scm · ScmReplenishment · Finance · Ledger · Erp · Ecommerce · Fan · Onboarding · Subscriptions · Partnerships · Tenants · OrgNodes · Groups · DomainPermissionSets`

본문 14줄(`reason` 유니온 · `code` · 생성자 · `this.name` 리터럴)이 20번 반복된다. 유니온이나 속성이 하나 늘면 20곳을 고쳐야 한다.

🔵 파일 분량의 대부분은 각 클래스의 JSDoc(그 도메인의 저하 계약 설명)이다 — **문서는 남긴다.** 줄어드는 것은 본문뿐이다(약 200줄).

# Scope

## In Scope

- `errors.ts` 에 `export type UnavailableReason = 'timeout' | 'circuit_open' | 'downstream'` 과 `export abstract class DomainUnavailableError extends Error` 추가 — 생성자 `(name, reason, code, message)` — **지금과 같은 own-property 순서**(`reason, code, name` — 아래 Edge Cases 정정 참고).
- 20개 클래스를 `extends DomainUnavailableError` + 같은 공개 시그니처 `(reason, code, message)` 생성자 하나로. `this.name` 은 **문자열 리터럴**(클래스명 추론 금지 — 프로덕션 빌드 압축이 클래스명을 바꿀 수 있다).
- 각 클래스의 JSDoc 블록은 그대로.
- 기반 클래스 단위 시험.

## Out of Scope

- 모양이 다른 5개: `ApiError` · `RegistryUnavailableError`(유니온에 `unauthorized`, `code` 없음) · `OperatorExchangeError` · `AssumeTenantError` · `ScmRateLimitedError`.
- `proxy-factory.ts` 의 `UnavailableErrorLike` 정리 · 호출부 변경.

# Acceptance Criteria

- [x] AC-1: 20개 클래스 각각의 인스턴스가 지금과 같다 — `instanceof` 자기 클래스 · `instanceof Error` · `name` · `reason` · `code` · `message` · **`Object.keys()` 순서**(`reason, code, name` — 구현 착수 시 원본에서 실측). 20개 전부 파라미터화 시험.
- [x] AC-2: 기존 시험 **수정 없이** 통과(console-web vitest 전체). 시험을 고쳐야 통과하면 동작이 바뀐 것 → 멈추고 보고.
- [x] AC-3: bite — 기반 클래스의 대입 순서를 바꾸거나(`code` 먼저) 한 서브클래스의 `name` 리터럴을 바꾸면 AC-1 시험이 빨강, 되돌리면 초록.
- [x] AC-4: `tsc --noEmit` · `lint` · `next build` rc=0(각각 독립 실행 + 명시 rc). `next build` 는 압축된 번들에서도 `name` 이 리터럴임을 확인하는 판정이다.
- [x] AC-5: `errors.ts` 의 JSDoc 블록 수가 전후 같다(`/**` 개수 대조).

# Related Specs

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.5(저하 신호) — 바뀌지 않는다.

# Related Skills

- refactor-code.

# Related Contracts

- 없음(런타임 동작 불변).

# Target App

- `projects/platform-console/apps/console-web`

# Implementation Notes

- 서브클래스에서 `X['reason']` 타입 참조는 `UnavailableReason` 으로 써도 같다(외부 참조 0 — 2026-11-24 grep).

# Edge Cases

- `useDefineForClassFields`: 기반 클래스에 `readonly reason: UnavailableReason;` 같은 **초기화 없는 필드 선언**을 두면 ES2022 define 의미론에서 `super()` 뒤 `undefined` 로 먼저 정의돼 순서가 바뀔 수 있다 → `declare readonly` 를 쓰거나 생성자 대입만 둔다. AC-1 의 `Object.keys` 순서 시험이 이것을 잡는다.

# Failure Scenarios

- `this.name = new.target.name` 같은 추론은 압축 빌드에서 `name` 을 바꾼다 — 로그 · `instanceof` 대체 검사에 쓰이면 조용히 깨진다. 리터럴 고정 + AC-4 `next build`.

# Test Requirements

- 기반 클래스 + 20개 파라미터화 시험(신규), 기존 시험 무수정.

# Definition of Done

- AC 전부 · PR CI 초록 · 머지.

## CORRECTION (구현 착수 중, 2026-11-24 UTC) — Edge Cases 의 방향이 반대였다

기안의 Edge Cases 는 «초기화 없는 필드 선언을 두면 순서가 바뀐다 → `declare` 를 써라» 라고 적었다. **원본을 재 보니 반대다**: 원래의 20개 클래스 자신이 `readonly reason: …; readonly code: string;` 를 선언하므로 ES2022 define 의미론에서 `reason` · `code` 가 `super()` 직후 먼저 정의되고 `name` 이 그 뒤에 붙는다 — 원본의 `Object.keys()` 는 **`['reason', 'code', 'name']`**(`origin/main` 의 `errors.ts` 로 일시 교체해 vitest 로 실측). `declare` 를 쓰면 오히려 `name, reason, code` 로 바뀐다. 그래서 기반 클래스는 **보통 필드 선언**을 두고, AC-1 시험은 실측 순서를 고정한다.

# Implementation Evidence (2026-11-24 UTC)

- `errors.ts` 1262 → 1116줄. 20개 클래스 본문 → `extends DomainUnavailableError` + 3줄 생성자(`name` 리터럴). 기반 클래스는 첫 서브클래스 **앞**에(클래스 선언은 호이스팅되지 않는다).
- **AC-1** — 새 시험 `tests/unit/shared/api/domain-unavailable-error.test.ts` 22칸: 20개 각각 instanceof(자기 · `Error` · 기반) · `name` · 필드 · `Object.keys()` = `['reason','code','name']`(원본 실측값) + 도메인 간 instanceof 분리 + «기반을 상속하는 클래스 = 정확히 이 20개» 전수.
- **AC-2** — vitest 전체 455 파일 / 5663 칸 rc=0(기존 5641 + 신규 22), 기존 시험 수정 0.
- **AC-3** — bite ① 기반 필드를 `declare` 로 → 20칸 빨강 ② `LedgerUnavailableError` 의 `name` 리터럴 변경 → 1칸 빨강. 백업으로 복원(바이트 동일 `cmp`) → rc=0.
- **AC-4** — `tsc --noEmit` rc=0 · `lint` rc=0 · `next build` rc=0(각각 독립 실행).
- **AC-5** — 기존 JSDoc 33개 그대로 + 기반 클래스 문서 2개(33 → 35).
