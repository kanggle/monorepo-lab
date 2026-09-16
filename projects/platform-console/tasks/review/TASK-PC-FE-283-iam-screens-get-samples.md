# Task ID

TASK-PC-FE-283

# Title

IAM 화면이 샘플로 선다 — 계정·감사·운영자·그룹·조직·파트너십·권한·구독·테넌트 (`ADR-MONO-074` 실행 2/8)

# Status

review

# Owner

platform-console

# Task Tags

- code
- test
- demo

---

# Goal

`TASK-PC-FE-282` 가 깐 샘플 모드 위에서 **IAM 도메인의 GET 을 전부 `ready`** 로 만든다. 익명 방문자가 아래 화면을 열면
«준비 중» 이 아니라 실제 화면이 합성 값으로 선다.

⏳ **`TASK-PC-FE-282` 머지 전 착수 금지.** 🔴 도메인 티켓 여섯(283~288)은 샘플 라우터 등록부와 원장 파일을 **공유**한다 ⇒
병렬 worktree 금지, **직렬 머지**.

화면: `/account` · `/accounts` · `/audit` · `/iam` · `/iam/guide`(정적) · `/operator-groups` · `/operators` · `/org-hierarchy` ·
`/partnerships` · `/permission-sets` · `/permissions` · `/subscriptions` · `/tenants` · `/tenants/[tenantId]`

코어: `callAdminGateway` (`shared/api/iam-gateway.ts`). ADR 인벤토리: GET **18** · 쓰기 **38**(코드 읽기 수).

---

# Scope

## In Scope

- 위 화면이 부르는 IAM GET 픽스처 전부 + 원장 `pending → ready`
- 목록 ↔ 상세 id 일관, 화면에 노출된 필터·페이지 동작

## Out of Scope

- 샘플 모드 기반(판정·코어 분기·셸·가드) — `TASK-PC-FE-282`
- 쓰기 동작 — 전부 `SAMPLE_READ_ONLY`(R1ⓐ, 282 가 이미 처리)

---

# Acceptance Criteria

- [x] **AC-0** `TASK-PC-FE-282` AC-0 표와 원장에서 IAM `pending` GET 목록을 뽑아 이 파일에 적는다(18 과 다르면 그 수가 범위). — § Implementation notes "AC-0 — 재인벤토리" (9 surface, 20 GET 리터럴; ADR 18 은 다른 단위, 282 가 이미 명시).
- [x] **AC-1** 그 GET 전부 `ready`. 픽스처마다 **실제 파서**(zod 스키마/parse 함수)를 통과하는 테스트. — `tests/unit/sample-fixtures-schema-iam.test.ts` (9 surface 전부, 실제 프로덕션 zod 스키마로 파싱).
- [x] **AC-2** «(샘플)» 표기(R2ⓐ) — 282 AC-7 의 규칙 테스트가 이 픽스처도 순회하고 초록. — `sample-label-rule.test.ts` 의 `it.each(SAMPLE_FIXTURE_DOCUMENTS)` 가 IAM 문서 9개를 순회, 초록. 새 키 분류는 § D4/D7, bite B10.
- [x] **AC-3** 목록에 나오는 id 로 상세(`/tenants/[tenantId]` 등)가 **찾아진다**. 없는 id 는 실제와 같은 404 모양. — tenants/org-nodes/groups 각각 리스트→상세 id 일치 + 미존재 id → `fixtureNotFound` 404 테스트, bite B11. accounts 는 실제로 detail-by-id 가 없어 그 축을 따르지 않는다(§ D3 에 근거 기록).
- [x] **AC-4** 화면에 노출된 필터·검색·페이지는 **픽스처 위에서 적용**된다(무시하면 «안 좁혀짐» 이 고장으로 보인다). 페이지 메타의 총계는 실제 행 수와 같다. — accounts(status/email) · audit(source) · operators(status) · tenants(status/tenantType) · groups(tenantId) · partnerships(role/status) 각각 "부분집합·비지 않음·조건 만족" 3중 단언 + totalElements=실제 필터후 행수. accounts 이메일은 § D5(코디네이터 지적)로 재작업, bite B12.
- [x] **AC-5** 대표 쓰기 1개(예: 계정 잠금)가 «샘플 화면에서는 실행되지 않습니다» 를 보인다. — `sample-fixtures-schema-iam.test.ts` "AC-5" 절: POST 계정 잠금 → 403 `SAMPLE_READ_ONLY` → `messageForCode` 매핑 문구 정확히 일치.
- [x] **AC-6** `e2e-smoke` 에 익명 `/accounts` 렌더 1칸(배너 + 표 + «(샘플)» 문자열). — `e2e-smoke/sample-visitor-iam.spec.ts`, `pnpm e2e:smoke` rc=0 (19 passed, 신규 1건 포함).
- [x] **AC-7** 🔴 감사 로그·운영자 이메일 등 **사람을 식별하는 값**은 명백한 합성(`*.example` 도메인, 실재하지 않는 이름)이다. — 계정/운영자 이메일 전부 `*.sample@example.com`, 감사 로그 operatorId/accountId 는 합성 id(`op-sample-*`/`acc-sample-*`), 실명은 전부 가상 한국 이름 + «(샘플)» 접미.

# Related Specs

- `docs/adr/ADR-MONO-074-anonymous-visitors-see-the-real-console-with-sample-data.md`
- `projects/platform-console/tasks/ready/TASK-PC-FE-282-anonymous-visitors-enter-the-real-console-and-the-gateways-answer-with-samples.md`
- `projects/platform-console/specs/services/console-web/architecture.md`

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4 (IAM admin) — 픽스처는 이 응답 모양을 따른다

# Edge Cases

- `/tenants` 는 실제로는 플랫폼 스코프가 있어야 열린다 — 샘플에서는 282 AC-12(샘플 운영자 = 전 화면 열림)를 따른다.
- 빈 목록 화면 하나는 empty-state 가 보이도록 픽스처에 0행 케이스를 둘지 결정해 적는다.

# Failure Scenarios

- 픽스처가 스키마를 어김 → 섹션 degrade 로 보여 «고장» 과 구별 안 됨 ⇒ AC-1 파서 테스트.
- 원장 파일 동시 수정 → 직렬 머지 규칙 위반, 충돌.

# Test Requirements

- `pnpm lint` · `npx tsc --noEmit` · `pnpm test` · `pnpm test:e2e:smoke`(각각 독립 + `rc=$?`)

# Definition of Done

- [x] 원장의 IAM `pending` 0 — `coverage.ts` `SURFACE_COVERAGE`/`SCREEN_COVERAGE` 의 IAM 행 전부 `ready` (9 surface + 14 screen).
- [x] 로그인 운영자 경로 테스트 무수정 초록 — § Implementation notes "병합 트리 관점 — AC-13 대조군". `pnpm test` BEFORE(parked main) 302/303 files·3218/3219 tests → AFTER(워크트리) 304/304·3261/3261, 실패 0.

분석=Opus 5 / 구현 권장=Sonnet 5.

---

# Implementation notes (구현 에이전트, 2026-09-16 UTC)

## AC-0 — 재인벤토리

`TASK-PC-FE-282` 는 원장 granularity 를 엔드포인트가 아니라 **surface**(게이트웨이 프로필 `logPrefix`)로
잡았다(282 D1) — 그 이유(경로가 래퍼 여러 겹을 거쳐 조립돼 엔드포인트 추출기가 추출기 자신을 재는 문제)가 이
티켓에도 그대로 적용된다. 282 의 AC-0 표에서 `callAdminGateway`(iam) 행을 그대로 인용한다:

| 코어 | GET 리터럴 | 쓰기 리터럴 | surface 9 |
|---|---:|---:|---|
| `callAdminGateway` (iam) | 20 | 35 | accounts · audit · operators · rbac · subscriptions · partnerships · tenants · org_nodes · groups |

🔴 ADR 본문의 **18** 은 코드 읽기 스윕(호출 함수 이름 단위)의 수이고, 위 20/9 는 `method: '<VERB>'` 리터럴
스윕(282 실측)이다 — 단위가 다르다(282 § AC-0 재인벤토리 절 참조). 이 티켓의 **실제 범위는 원장의 IAM
`pending` 9 행**이고, ADR 의 18 과 직접 비교하지 않는다(282 가 이미 그렇게 적어 뒀다). 원장 `SCREEN_COVERAGE`
의 IAM `pending` 화면도 14개(작업 지시서에 나열된 것과 일치, `/iam/guide` 는 `static`으로 이미 302 소유).

## 스크린 ↔ 서페이스 매핑 (구현 중 실측)

- `/accounts` → `iam:accounts` (목록 + 이메일 단건 조회)
- `/audit` → `iam:audit`
- `/operators` → `iam:operators` (목록 + `/api/admin/me` self-row 게이트 + `/grantable-roles` 프리필터 +
  `/{id}/assignments`)
- `/account` → 코어 GET 없음(`getIdToken`/`getAccessToken`/`getActiveTenant` 직접 읽기 + `getCatalog()` 만 사용,
  이미 282 에서 ready). 새 픽스처 불필요 — 화면 원장만 `ready` 로 뒤집으면 된다.
- `/iam` → `iam-overview` 가 `accounts`+`audit`+`operators` 를 소형 쿼리(size=1)로 fan-out
- `/dashboards`(IAM GAP 상세) → `features/dashboards` 가 같은 3 개 read 를 fan-out(이미 282 소유의
  `operator-overview`/`domain-health`/`notifications-inbox` 와 다른, GAP 카드 드릴다운)
- `/operator-groups` → `iam:groups`
- `/org-hierarchy` → `iam:org_nodes`
- `/partnerships` → `iam:partnerships`
- `/permissions`, `/permission-sets` → 둘 다 `iam:rbac`(같은 두 read, TASK-BE-486 의 "권한 세트는 role 재프레임"
  결정 — 282 의 rbac-catalog.ts 헤더가 이미 문서화)
- `/subscriptions` → 코어 GET 없음(ADR-MONO-023: 구독 상태는 레지스트리 카탈로그에서 파생 — `getCatalog()`,
  이미 282 에서 ready). `iam:subscriptions` surface 자체는 원장에 있지만 **쓰기 전용**(§ 아래 D5).
- `/tenants`, `/tenants/[tenantId]` → `iam:tenants`

## 편차 / 구현자 선택

- **D1 라우터 계약 확장 — 쿼리 스트링 스트립 → 전체 경로 전달.** 282 의 `sampleResponse()` 는
  `fixture(req.path.split('?')[0])` 로 쿼리를 버렸다(그 4개 대시보드/레지스트리 픽스처가 인자를 아예 무시해서
  드러나지 않았다). AC-4(필터·검색·페이지가 실제로 좁혀짐)를 만족하려면 픽스처가 쿼리를 읽어야 해서
  `sampleResponse()` 가 `fixture(req.path)`(쿼리 포함)를 넘기도록 바꿨다. **영향 범위 확인(코디네이터 요청)**:
  `grep -rn "sampleResponse(\|SAMPLE_FIXTURES\[" src/` → 프로덕션 호출자는 `shared/api/sample-gate.ts`
  하나뿐이고 그 경로는 항상 `req.path`(코어가 원래 갖고 있던, 쿼리 포함 경로)를 넘긴다 — 스트립은
  `sampleResponse()` **내부**에서만 일어났으므로 이 변경은 그 함수 밖의 어떤 계약도 바꾸지 않는다. 테스트 쪽
  호출자는 `sample-coverage-ledger.test.ts`(대표 경로 사용, 아래 D2) · `sample-read-only-copy.test.tsx`(POST 라서
  경로 무관) · 이 티켓의 새 파일 하나뿐. 282 의 4개 픽스처는 인자를 무시하므로 무변화(회귀 0, `pnpm test` 로
  실측).
- **D2 대표 경로 원장 (`SURFACE_SAMPLE_PATH`).** `sample-coverage-ledger.test.ts` 의 "the ledger promises what
  the router does" 절이 모든 `ready` 행에 `path: '/'` 로 200 을 기대하던 것이 IAM 픽스처(구체적인 프로듀서
  경로에만 응답)와 충돌하는 함정이었다(브리핑이 미리 지목). **선호안**(가드가 재는 것을 좁히지 않고, 각 행에
  답할 수 있는 질문을 주는 쪽)을 택해 `coverage.ts` 에 `SURFACE_SAMPLE_PATH` 맵을 추가하고 그 테스트가
  이 맵(없으면 `/` 폴백)을 쓰도록 바꿨다. 🔴 **드리프트 방지(코디네이터 요청)** — 이 맵 자체가 원장과 독립적으로
  낡을 수 있으므로, "`iam` 계열 4개 예외 밖의 모든 `ready` 행은 반드시 이 맵에 있다" + "이 맵에 원장에 없는/
  `ready` 아닌 stale 항목이 없다" 는 가드 2개를 추가했다(§ Bites B8/B9 로 발화 증명).
- **D3 상세 404 지원 (`fixtureNotFound`).** 라우터가 200/403/503 만 낼 수 있어서 AC-3("없는 id 는 실제와 같은
  404 모양")을 낼 수 없었다. `shared/sample/router.ts` 에 `FixtureNotFound`(`{notFound:true, code, message}`) +
  `fixtureNotFound()` 헬퍼를 추가하고, `sampleResponse()` 가 이 값을 받으면 코어별 봉투(FLAT/wms-NESTED)를
  그대로 재사용해 `404` 로 응답한다 — 9 서페이스 전부 non-wms 라 이번엔 항상 FLAT. `tenants/{tenantId}` ·
  `org-nodes/{id}` · `org-nodes/{id}/tenants` · `org-nodes/{id}/admins` · `groups/{id}` ·
  `groups/{id}/members` · `groups/{id}/grants` · `operators/{id}/assignments` 에서 씀. 🔴 **accounts 는 예외** —
  `GET /api/admin/accounts?email=…` 는 실제로 detail-by-id 엔드포인트가 아니라 **필터된 LIST read**다
  (`accounts-api.ts` `getAccountByEmail()` 자신의 주석: "the producer has no dedicated GET-by-id"). 그래서
  매치 없는 이메일은 `fixtureNotFound` 가 아니라 **200 빈 페이지**(진짜 프로듀서 동작)를 낸다 — 이건
  코디네이터가 처음 제안한 방향(이메일도 404)을 이 근거로 되돌린 것이고, 테스트로 양쪽(진짜 404 표면 vs
  진짜 빈-페이지 표면)을 각각 문는다.
- **D4 `email` 을 사람이 읽는 키로 분류 (label-rule.ts).** `AccountSummarySchema`/`OperatorSummarySchema` 에는
  `name`/`title`/`description` 류가 전혀 없다(`{id,email,status,createdAt}` 뿐) — AC-6(방문자 `/accounts` 화면에
  «(샘플)» 문자열)과 AC-7(사람 식별 값의 명백한 합성)이 만날 유일한 필드가 `email` 이다. **코디네이터 확인**:
  `type="email"` 두 자리(`AccountsSearchBar.tsx:44` 검색창, `CreateOperatorForm.tsx:134` 생성 폼)는 모두 사람이
  타이핑하는 빈 입력이고 `acc.email`/`op.email` 값으로 미리 채워지지 않는다 — 렌더가 깨지는 경로는 없다.
  `mailto:` 는 `src/` 전체에 0건. `AccountSummarySchema` 는 `z.string()`(형식 제약 없음)이라 접미가 붙은 값도
  파싱된다. — 표시값은 `hana.kim.sample@example.com (샘플)` 형태.
- **D5 `email` 이 검색 키이기도 하다는 점 (코디네이터 2차 확인 · AC-3/AC-4 상호작용).** `use-accounts.ts` 가
  타이핑한 값을 그대로 `email` 쿼리로 보내고(`getAccountByEmail(email)` 도 같은 read), 표에 찍히는 값엔 접미가
  붙어 있다 — 사람이 표를 그대로 복사-붙여넣기 하면 접미 포함 문자열로 검색한다. 매처는 양쪽 철자
  (평문/접미 포함)를 SUFFIX 를 벗겨 정규화한 뒤 **정확 일치**로 비교한다(부분일치가 아님 — 시드 이메일이 전부
  `sample`/`.example` 어휘를 공유해서 부분일치면 "좁혀지지 않음"이 AC-4 자신의 실패 모드가 된다. 이걸
  `email=sample` 케이스로 테스트하고 bite 로 증명했다). `AccountsPagination.tsx` 의 "단건 검색" 라벨은
  `query.email` 의 존재만 보므로 페이지 메타 모양은 문제되지 않는다.
- **D6 구독 서페이스는 실제 GET 이 없다.** `ADR-MONO-023`: 구독 상태는 레지스트리 카탈로그에서 파생되고
  (`SubscriptionsScreen` 은 `getCatalog()` 만 읽는다, 이미 282 ready), `subscriptions-client.ts` 의
  `logPrefix: 'subscriptions'` 는 **쓰기 두 개(구독/상태전환)에만** 쓰인다. 그래서 `iam:subscriptions` 픽스처는
  어떤 GET 에도 `{}` 를 돌려주는, DoD("원장의 IAM `pending` 0")를 만족시키기 위한 구조적 장치일 뿐 — 어떤 실제
  화면도 이 GET 을 호출하지 않는다(⚪, 아래 절에 다시 기록).
- **D7 문서-키 재구성 (label 가드).** IAM 서페이스 문서(`SAMPLE_FIXTURE_DOCUMENTS`)를 처음에 `{items: ...,
  tenants: MAP, admins: MAP}` 형태로 짰더니, MAP(`Record<orgNodeId, Row[]>`)을 그대로 중첩하면 라벨 가드가
  문자열을 **MAP 의 키**(`org-sample-0001` 같은 id)로 분류해 `unclassified-key` 오탐이 났다(실측, § Bites 아님 —
  구현 중 첫 `pnpm test` 에서 잡힘, 상세는 § 측정 절 "AFTER before-fix" 참고). `Object.values(MAP).flat()` 로
  id-키잉을 벗기고 진짜 분류된 키(`tenantIds`, 혹은 items 가 객체라 키 무관한 `admins`/`members`/`grants`) 아래
  평탄화했다. `operators` 문서의 `grantableRoles` 배열도 미분류였어서 `MACHINE_KEYS` 에 추가했다.
- **D8 기존 테스트 2개가 A1/샘플 재분류와 충돌 — 282 D8 과 같은 종류의 재발.**
  - `tests/unit/features/operators/self-operator-id.test.ts` (f) "no operator session (cookie missing)" 이
    쿠키 빈 병으로 "세션 없음"을 모델링했는데, 빈 병은 이제(282 D11, main 병합분) `isSampleVisitor()===true`고
    `operators` 가 이 티켓에서 `ready` 가 됐으므로 `getSelfOperatorIdOrNull()` 이 `null` 대신 샘플
    `op-sample-0001` 을 반환했다. 셀의 **의도**(운영자 토큰이 없는 상태 → pre-flight 에서 거부됨)를 보존하면서
    샘플 재분류를 피하려고, ACCESS 쿠키를 심어 "IAM 로그인은 됐지만 운영자 토큰 없음"(A1 자신의 예외 조항 —
    "액세스 쿠키만 있음 → 샘플 아님")으로 셋업을 바꿨다. 단언(`null`, fetch 0회)은 한 글자도 안 바꿨다.
  - `tests/unit/sample-mode-cores.test.ts` 의 "① pending GET → section degrade" (`callAdminGateway (iam)`)
    가 `logPrefix: 'accounts'` 를 재사용했는데, 이 티켓이 `accounts` 를 `ready` 로 만들어서 더 이상 pending 이
    아니게 됐다 — 그 셀이 테스트하려는 건 **코어의 제네릭 분기 메커니즘**(어떤 surface든 원장에 없으면 이
    경로)이지 `accounts` 자신의 내용이 아니므로, 원장에 없는 `logPrefix: 'no-such-surface'` 를 쓰도록 바꿨다
    (`findSurfaceCoverage` 가 `undefined` 를 돌려주는 것은 실제 `pending` 행과 동일한 분기). 단언 불변.
  - 🔵 두 파일 모두 ADR-MONO-074/TASK-PC-FE-283 을 인용하는 주석을 남겼다.

## Edge Case 결정 — 빈 목록 화면

작업지시서의 Edge Case("빈 목록 화면 하나는 empty-state 를 보이도록 0행 케이스를 둘지 결정")에 대한 내 선택:
**별도의 0행 서페이스는 두지 않았다.** 대신 accounts 의 "매치 없는 이메일 검색" 경로(§ D3)가 실제로
`AccountsEmptyState` 컴포넌트를 실경로로 렌더한다 — 화면마다 인위적인 0행 시드를 만드는 대신, 이미 있는
필터 경로 하나로 empty-state 렌더링을 증명하는 쪽을 택했다(테스트: `sample-fixtures-schema-iam.test.ts` "email
lookup ... 200 empty PAGE").

## AC-12 상속

282 의 AC-12(샘플 레지스트리는 6 제품 전부 `available: true`)가 이미 모든 화면의 등록부 사전-확인을 통과시킨다
— 이 티켓은 그 결정을 바꾸지 않았다. `/tenants` 자체의 SUPER_ADMIN 게이트는 샘플 라우터가 역할을 검사하지
않으므로(A2 의 설계) 무관하다.

## ⚪ 측정하지 못한 것 / 실제로 실행되지 않는 것

- **`iam:subscriptions` 는 어떤 실제 화면도 호출하지 않는다** (§ D6) — 원장 구조상 `ready` 로만 존재. 이
  surface 를 실제로 때리는 시나리오가 코드베이스에 아예 없어서, "그 화면이 실제로 이 픽스처를 쓴다"는 실측이
  불가능하다(측정 대상이 없음 — 부재의 증거이지 실패가 아니다).
- **`/api/admin/accounts/{id}/export`(GET-with-reason)** — accounts 의 "내보내기" 액션이 부르는 GET 이다.
  샘플 방문자에게는 도달 가능하지만(GET 이라 쓰기 거부 대상이 아님), 이 티켓의 AC 어디에도 그 화면 요소를
  요구하지 않아 픽스처를 만들지 않았다 — 방문자가 "내보내기"를 누르면 `SAMPLE_NOT_READY` 섹션 degrade 로
  떨어진다(크래시 없음, 그냥 아직 없는 케이스로 정직하게 남는다).
- **operator-groups 의 members/grants 다이얼로그, org-hierarchy 의 tenants/admins 패널, tenants 의 create/edit
  폼** 등 클라이언트 상호작용으로만 도달하는 하위 자원은 `sample-fixtures-schema-iam.test.ts` 에서
  `sampleResponse()` 직접 호출로만 검증했다 — Playwright 로 그 클릭 경로까지 구동하는 e2e 는 AC-6 의 "1 칸"
  요구를 넘어서는 범위라 추가하지 않았다.

## 측정 (이 워크트리 · Windows 호스트 · 각 게이트 독립 실행 + 명시 rc)

BEFORE 는 **파킹된 main 체크아웃**(`C:/Users/kangdow/dev/project/ai-project/monorepo-lab/projects/platform-console/
apps/console-web`, `git log -1` = `7b706d304`, 이 워크트리의 분기점과 동일 HEAD, read-only)에서 쟀다.

| 게이트 | 트리 | 결과 |
|---|---|---|
| `pnpm lint` | 워크트리(AFTER) | rc=0 · «No ESLint warnings or errors» |
| `npx tsc --noEmit` | 워크트리(AFTER) | rc=0 |
| `pnpm test` | **parked main(BEFORE)** | rc=1 · **302 files / 3218 tests passed**, 1 file / 1 test 실패
  (`LedgerOpsScreen.test.tsx` — finance/ledger 화면, IAM 과 무관, `findByTestId` 타임아웃형 실패 — 기지 알려진
  Windows 타이밍 flake 계열, memory 의 "OperatorsScreen.test.tsx" 항목과 같은 부류의 다른 파일) |
| `pnpm test` (1차, 수정 전) | 워크트리(AFTER) | rc=1 · 300 files / 4 failed · 3255/6 failed — 아래 §
  "구현 중 잡힌 회귀" 6건, 전부 고쳤다 |
| `pnpm test` (최종) | 워크트리(AFTER) | rc=0 · **304 files / 3261 tests passed**, 실패 0 — `LedgerOpsScreen`
  flake 도 이 실행에선 재현 안 됨(비결정적) |
| `pnpm build` | 워크트리 | rc=0 (11.1 min — OTel 관련 3개 module-not-found 경고는 기존, 이 티켓과 무관) |
| `pnpm e2e:smoke` | 워크트리(프로덕션 빌드, 백엔드 전부 loopback 127.0.0.1:1) | rc=0 · **19 passed** — 기존
  18개 + 신규 `sample-visitor-iam.spec.ts` 1개(AC-6) |

🔵 BEFORE/AFTER 테스트 **파일 수** 차이(302→304, 총 개수 303→304 — AFTER 는 실패 0 이라 총량이 곧 통과량)는
새 파일 `sample-fixtures-schema-iam.test.ts` 1개(+31 케이스). **테스트 수** 차이(3219→3261 = +42)는
그 31 + `sample-coverage-ledger.test.ts` 의 새 가드 2개 + `sample-label-rule.test.ts` 의
`it.each(Object.entries(SAMPLE_FIXTURE_DOCUMENTS))` 가 IAM 문서 9개를 새로 순회하며 늘어난 9 = 42.

### 구현 중 잡힌 회귀 (수정 전 `pnpm test` 1회, 6 셀 실패 — 커밋에는 없음, 전부 위 § 편차에서 고쳤다)

| # | 파일 | 원인 | 고침 |
|---|---|---|---|
| 1 | `sample-label-rule.test.ts` › iam:operators | `grantableRoles` 미분류 키 | D7 — `MACHINE_KEYS` 에 추가 |
| 2 | `sample-label-rule.test.ts` › iam:org_nodes | MAP 을 그대로 중첩해 id-키가 분류를 가로챔 | D7 — `Object.values(...).flat()` |
| 3 | `sample-mode-cores.test.ts` › callAdminGateway pending GET | `accounts` 가 이 티켓에서 ready 로 바뀜 | D8 — `logPrefix: 'no-such-surface'` |
| 4 | `self-operator-id.test.ts` › (f) 무세션 | 빈 쿠키 병 = 이제 샘플 방문자 | D8 — ACCESS 쿠키로 pre-operator 셋업 |
| 5–6 | `LedgerOpsScreen.test.tsx` (2 셀) | 무관 — 기존 Windows 타이밍 flake, 재실행에서 재현 안 됨 | 손대지 않음 |

### Bites (금지/결함 코드를 넣어 빨강 → 되돌려 복원 트리에서 초록)

| # | 가드 | 주입 | 빨강 | 복원 후 |
|---|---|---|---|---|
| B8 | `sample-coverage-ledger.test.ts` "every `ready` row … has an explicit SURFACE_SAMPLE_PATH entry" (D2) | `iam:accounts` 대표경로 항목을 주석 처리 | rc=1 · `iam:accounts at /: expected 503 to be 200` + `expected [ 'iam:accounts' ] to deeply equal []` (두 단언 모두 발화) | rc=0 |
| B9 | `sample-coverage-ledger.test.ts` "SURFACE_SAMPLE_PATH has no stale entry" (D2) | 존재하지 않는 서페이스 키 `iam:no-such-surface-BITE-B9` 추가 | rc=1 · `expected [ 'iam:no-such-surface-BITE-B9' ] to deeply equal []` | rc=0 |
| B10 | `sample-label-rule.test.ts` (D4 — `email` 분류) | `HUMAN_READABLE_KEYS` 에서 `'email'` 제거 | rc=1 · `iam:accounts`/`iam:operators` 양쪽에서 `unclassified-key` 다건 | rc=0 |
| B11 | `sample-fixtures-schema-iam.test.ts` AC-3 (tenants 404, D3) | `fixtureNotFound(...)` 대신 `undefined` 반환 | rc=1 · `expected 503 to be 404` | rc=0 |
| B12 | `sample-fixtures-schema-iam.test.ts` AC-4 (accounts 이메일 정확일치, D5) | `===` → `.includes(needle)` (부분일치) | rc=1 · `email=sample` 이 5건 전부 매치(`expected 5 to be +0`) | rc=0 |

B8–B12 모두 단독 주입 → 실행 → 복원 순으로 개별 확인했다(`git status`로 잔여 `BITE-` 마커 0건 확인,
`grep -rn "BITE-" src tests` = 0건). 복원 트리 재실행(§ 측정의 "최종" 행)이 전체 스위트 기준의 최종 복원
확인이다.

### 병합 트리 관점 — AC-13 대조군 (로그인 운영자 경로)

`sample-mode-cores.test.ts` 의 "② authenticated" / "③ half session" 셀과 `self-operator-id.test.ts` (a)–(e),
(g) 셀은 무수정 초록이다 — 이 티켓이 건드린 것은 (f) 셀 하나(§ D8)뿐이고 그 이유도 "빈 쿠키 병이 이제 샘플
방문자"라는, **282 가 이미 결정한** 재분류의 재발이지 이 티켓이 새로 만든 결정이 아니다. 인증 운영자 경로
(`②`/`③`/(a)-(e)/(g))는 한 글자도 안 바뀌었다.

---

## CORRECTION — 조정자 지시 3건 반영 (2026-09-16 UTC)

원 구현 보고를 제출하기 전, 조정자가 3가지를 명시적으로 재확인하라고 지시했다 — 결과를 여기 추가한다
(review/ 동결 규칙과 같은 이유로, in-progress 상태에서 위 본문에 이미 직접 반영했고 별도 CORRECTION 절이
필요하지 않다 — 굳이 남기는 이유는 "무엇을 확인했는가"를 명시적 기록으로 남기기 위해서다):

1. **`FixtureHandler` 계약 확장 영향 범위** — § D1 에 근거(grep 결과)와 함께 기록. 유일한 프로덕션 호출자
   `sample-gate.ts` 는 무영향, 282 의 4개 픽스처는 인자를 무시해 무변화, 테스트 호출자는 전부 파악됨.
2. **`email` 분류가 검색/상세 키와 충돌** — § D5 에 정규화 + 정확일치 매칭 + 관련 파일(그 필드가 검색 키로도
   쓰이는 지점: `use-accounts.ts`, `accounts-api.ts` `getAccountByEmail`, `AccountsPagination.tsx`)까지
   포함해 기록. 테스트 4개 추가(평문 매치·접미 매치·부분일치 거부·매치없음=200빈페이지) + bite B12.
3. **`SURFACE_SAMPLE_PATH` 드리프트 가드** — § D2 에 기록, 가드 2개 추가 + bite B8/B9.
