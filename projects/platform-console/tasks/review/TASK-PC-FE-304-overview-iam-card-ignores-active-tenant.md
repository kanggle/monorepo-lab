# Task ID

TASK-PC-FE-304

# Status

review (2026-10-04 UTC — impl PR 생성 대기 중, 로컬 게이트 전부 통과: lint/tsc/vitest 334파일·3738시험 rc=0, bite 확인)

# Title

운영 개요 **IAM 카드**가 활성(선택) 테넌트를 무시하고 운영자 홈 테넌트를 센다 — 라벨도 그 계정이 "전체"가 아님을 밝히지 않는다

# Owner

platform-console

# Task Tags

- console-web
- integration
- bugfix

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet — 레그 하나의 쿼리 파라미터 추가 + 라벨 문구 2곳. 범위가 좁고 틀릴 여지가 적다.

---

# Dependency Markers

없음(선행/후속 모두 없음). 발견 경로는 `TASK-MONO-758`(review, 19차 데모 창 라이브 확인)이지만 그 티켓은 이 코드를 소유하지 않는다 — 결함은 읽기 전용 조사로 거기서 나왔고, 고치는 작업은 이 티켓이다. 두 티켓 사이에 머지 순서 의존은 없다.

# Goal

`shared/composition/console-composition.ts`의 운영 개요(`operator-overview`, 계약 § 2.4.9.1) IAM 레그가 `X-Tenant-Id` 헤더만 보내고 있어, iam-platform `admin-service`의 `AccountAdminController#search`가 (그 헤더를 읽지 않고) `tenantId` 쿼리 파라미터만 읽어 테넌트를 정하는 것과 엇갈린다 — 그 파라미터가 비어 있으면 `QueryTenantScopeGate`가 운영자의 **홈 테넌트**로 되돌아간다. 그래서 운영자가 테넌트를 전환해도 IAM 카드만 홈 테넌트를 계속 센다(나머지 5장은 전환된 테넌트를 센다). 이 레그에 `tenantId` 쿼리 파라미터를 추가해 accounts 화면(`searchAccounts`, TASK-BE-357)과 같은 스코프 규칙을 따르게 한다. 더불어 "전체 계정" 라벨이 "전체"(플랫폼 전체 혹은 운영자 포함)로 읽혀 실제로는 "이 테넌트의 회원 계정(운영자 제외)"이라는 뜻과 불일치하므로 두 자리 모두 다시 쓴다.

# Scope

## In Scope

- `shared/composition/console-composition.ts` `operatorOverviewLegs()`의 IAM 레그 URL에 `tenantId=<활성 테넌트>` 쿼리 파라미터 추가(`X-Tenant-Id` 헤더는 유지).
- `specs/contracts/console-integration-contract.md` § 2.4.9.1 Surface 표 row 1 + 증설 설명 — 코드보다 먼저 계약을 고친다(스펙이 먼저).
- `specs/contracts/fixtures/operator-overview-leg-bodies.json`의 iam leg `producer` 문서 문자열(참고용, 매칭에 안 쓰임) 갱신.
- `features/operator-overview/components/DomainCardSummaries.tsx`(운영 개요 6카드 IAM 카드)와 `features/dashboards/components/IamComposedOverviewScreen.tsx`(IAM 전용 drill-down, `계정` 카드의 `전체 계정` 지표)의 라벨을 "회원 계정 (이 테넌트)"로 교체. 후자는 이미 `searchAccounts()`로 테넌트가 맞게 스코프되어 있다 — 라벨만 틀렸다.
- `tests/unit/sample-overview-cards-match-lists.test.ts`의 테스트 제목(문구 일치, 단언 대상 아님) 동기화.
- 단위 시험 + bite(주입→레드 확인→복원).
- `/api/admin/accounts`를 부르는 다른 레그/라우트 전수 grep — 결과: `shared/api/iam-accounts-read.ts`의 `searchAccounts()`는 이미 TASK-BE-357로 `tenantId`를 보낸다(이 버그 없음). `shared/sample/fixtures/{iam,dashboards}.ts` · `shared/sample/coverage.ts`는 테스트/샘플 고정값이고 `splitPath`로 pathname만 매칭해 쿼리 파라미터 추가에 영향받지 않는다(변경 불필요, 읽어서 확인).

## Out of Scope

- 다른 5개 레그(wms/scm/finance/erp/ecommerce)의 테넌트 스코프 — 전부 도메인-용 토큰의 JWT claim으로 스코프되고(§ 2.4.9 D4) `X-Tenant-Id`/`tenantId` 어느 쪽도 쓰지 않는다. 대조 결과 다른 결함 없음.
- iam-platform `admin-service` 백엔드 코드 변경 — 그 쪽은 스펙대로 정확히 동작한다(결함은 소비자 쪽).
- AC-3(라이브 데모 확인)의 실제 재굽기/배포 — 그것은 다음 데모 창에서만 가능.

# Acceptance Criteria

- [x] **AC-0** — 코드 인용으로 결함 확인: `console-composition.ts:351`(수정 전)이 `X-Tenant-Id`만 보냄 / iam-platform `AccountAdminController.java:72-89`가 `tenantId` 쿼리 파라미터만 읽음 / `QueryTenantScopeGate.java:71-75`가 비어 있으면 운영자 홈 테넌트로 폴백. 계약 § 2.4.9.1 row 1이 결함과 같은(헤더만) URL을 적어 두고 있었다 → **계약을 먼저 고쳤다**(스펙-선-코드).
- [x] **AC-1** — IAM 레그가 `tenantId=<활성 테넌트>`를 쿼리 파라미터로 보낸다(헤더도 유지, 계약이 금지하지 않는 한). 전수 grep 결과 다른 레그/라우트에 같은 결함 없음(위 Scope 근거).단위 시험이 URL을 `new URL().searchParams`로 파싱해 테넌트 값을 단언. bite: 파라미터를 제거하면 그 시험 1개만 레드(19 통과 유지).
- [x] **AC-2** — 카드 라벨을 "전체 계정" → "회원 계정 (이 테넌트)"로 교체(2곳: 운영 개요 6카드 + IAM 전용 drill-down). testid는 그대로(렌더 셀렉터 깨지지 않음 — grep 결과 둘 다 testid로만 조회됨). 저장소 전체(e2e 포함) grep 결과 "전체 계정" 리터럴 텍스트를 단언하는 시험/스펙 0건(전부 testid 기반) — 바꿀 선택자 없음, 테스트 제목 문구 1곳만 동기화.
- [ ] **AC-3** (라이브, ⚪) — Vercel 배포 뒤 다음 데모 창에서: 테넌트 `demo-corp` → IAM 카드 0, 테넌트 `ecommerce` → IAM 카드 ≥1(동시에 두 테넌트 모두 다른 카드들과 일치). **미확인으로 남긴다** — 다음 창에서 라우트 로그와 함께 재측정.

# Related Specs

- `projects/platform-console/specs/services/console-web/architecture.md` (§ composition — `shared/composition/console-composition.ts`)
- `docs/adr/ADR-MONO-081-console-composition-in-the-console-server.md` D2 (레그별 자격, 재구현하지 않음 — 이 티켓은 쿼리 파라미터 하나만 추가)

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.9.1 (Surface 표 row 1 — 이 티켓으로 갱신)

# Edge Cases

- 활성 테넌트가 없는 요청은 이 레그에 도달하기 전에 `400 NO_ACTIVE_TENANT`로 이미 막힌다(§ 2.4.9 D 선행 게이트, 변경 없음) — `tenantId=` 빈 문자열이 나갈 일이 없다.
- 운영자가 SUPER_ADMIN이라 `tenantId`가 자기 홈 밖이어도, `QueryTenantScopeGate`는 `isPlatformScope`면 그대로 통과시킨다(기존 백엔드 동작, 변경 없음) — 이 레그는 그 경로를 그대로 탄다.
- 선택된 테넌트가 end-user 계정을 소유하지 않는 테넌트(예: 운영자만 있는 테넌트)면 카드는 정당하게 0을 보인다 — 그것은 버그가 아니라 설계대로다(새 라벨이 그 의미를 드러낸다).

# Failure Scenarios

1. `tenantId` 쿼리 파라미터만 추가하고 `X-Tenant-Id` 헤더를 지우면, 이 레그의 자격 시험(AC-5, TASK-PC-FE-302)이 깨진다 — 헤더는 유지해야 한다.
2. 라벨만 바꾸고 쿼리 파라미터를 안 고치면 "회원 계정 (이 테넌트)"라는 라벨이 실제로는 "운영자 홈 테넌트"를 세는 거짓 라벨이 된다 — 두 변경은 분리되면 안 된다.
3. 계약을 코드보다 나중에 고치면(또는 안 고치면) § 2.4.9.1이 결함과 같은 URL을 계속 정의하게 되어, 다음 사람이 계약을 "정답"으로 읽고 되돌린다.

---

# 결과 (2026-10-04 UTC)

**바뀐 것**:

1. `specs/contracts/console-integration-contract.md` § 2.4.9.1 Surface 표 row 1 — URL에 `&tenantId={activeTenant}` 추가 + "Domain credential" 칸에 이유 설명 + "Read content surfaced" 칸에 "활성 테넌트의" 명시. "Producer immutability" 단락 뒤에 TASK-PC-FE-304 증설 노트 추가(스펙을 코드보다 먼저 고침).
2. `specs/contracts/fixtures/operator-overview-leg-bodies.json` — iam leg `producer` 문서 문자열 동기화(매칭에 쓰이지 않는 참고용 필드).
3. `apps/console-web/src/shared/composition/console-composition.ts` — IAM 레그 URL에 `&tenantId=${encodeURIComponent(c.tenant)}` 추가, `X-Tenant-Id` 헤더는 그대로 유지. 주석으로 근거(iam-platform 파일:줄) 기록.
4. `apps/console-web/src/features/operator-overview/components/DomainCardSummaries.tsx` — IAM 카드 `dt` "전체 계정" → "회원 계정 (이 테넌트)" + 설명 주석.
5. `apps/console-web/src/features/dashboards/components/IamComposedOverviewScreen.tsx` — `계정` 카드의 `Metric label` "전체 계정" → "회원 계정 (이 테넌트)" + 설명 주석.
6. `apps/console-web/tests/unit/sample-overview-cards-match-lists.test.ts` — 테스트 제목 문구만 동기화(단언 대상 아님).
7. `apps/console-web/tests/unit/shared/console-composition.test.ts` — 신규 시험 "iam leg sends tenantId as a query param for the ACTIVE (selected) tenant, not only X-Tenant-Id (TASK-PC-FE-304)" 추가(`CREDS.tenant`를 `'ecommerce'`로 바꿔 호출 → URL의 `tenantId` 쿼리 파라미터가 그 값과 같음을 단언).

| AC | 판정 | 근거 |
|---|---|---|
| AC-0 | ✅ | iam-platform `AccountAdminController.java` `@GetMapping search(...)`(실제 라인 72~89 — `tenantId` 쿼리 파라미터만 읽음, `X-Tenant-Id` 헤더 미사용) + `QueryTenantScopeGate.java`(실제 라인 55~84 — `requestedTenantId`가 null/blank면 `operatorTenantId`로 폴백) 직접 Read로 확인. 계약 § 2.4.9.1 row 1(수정 전)이 `tenantId` 없는 URL을 적고 있었음 — 그래서 계약을 코드 전에 고쳤다 |
| AC-1 | ✅ | `console-composition.test.ts` 신규 시험 1개 추가, 20개 중 1개만 새로 생김. **bite**: 쿼리 파라미터를 제거하고 재실행 → 그 1개만 레드(`expected null to be 'ecommerce'`), 나머지 19개는 그대로 통과 — 원본 복원 뒤 바이트 동일 확인(`diff` 0). 전수 grep(`/api/admin/accounts`) 결과 `iam-accounts-read.ts`의 `searchAccounts()`는 TASK-BE-357로 이미 `tenantId`를 보내고, 샘플 픽스처(`iam.ts`/`dashboards.ts`/`coverage.ts`)는 `splitPath`로 pathname만 매칭해 쿼리 파라미터 추가에 영향 없음(읽어서 확인, 수정 불필요) |
| AC-2 | ✅ | 2곳 교체(`DomainCardSummaries.tsx`·`IamComposedOverviewScreen.tsx`). 저장소 전체(e2e-smoke/tests/e2e 포함) grep 결과 "전체 계정" 리터럴 텍스트를 단언하는 선택자 0건 — `operator-overview-card-iam-total`·`overview-accounts-total` 둘 다 testid 기반 조회만 사용. 테스트 제목 문구 1곳(`sample-overview-cards-match-lists.test.ts`) 동기화 |
| AC-3 | ⚪ 미확인 | 다음 데모 창에서 라우트 로그와 함께 재측정 — 이 PR 은 그 창을 만들지 않는다 |

**로컬 게이트**: `pnpm lint` rc=0 · `npx tsc --noEmit` rc=0 · `npx vitest run --maxWorkers=4 --minWorkers=1` **334 파일 / 3,738 시험** 전부 통과(rc=0). 추가로 `node scripts/check-fetch-resolution.mjs`(repo root) rc=0.

**티켓과 다르게 한 것**: 없음.
