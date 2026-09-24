# Task ID

TASK-MONO-728

# Title

로그인 버튼·안내 문구 정합 — 콘솔·팬·스토어 3앱, 조사(2026-09-24)를 좁힌 실행 티켓

# Status

review (2026-09-24 UTC — AC-1~AC-7 닫힘)

# Owner

monorepo

# Task Tags

- code
- frontend
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet 5 — 세 앱 각각 한 줄(버튼) + 한 줄(조사) 문자열 치환, 로직 변경 없음. 새 계약·백엔드 변경 없음.

---

# Goal

2026-09-24 포트폴리오 UX 전수조사(소유자 승인)에서 확정된 로그인 관련 문구 3건을 정확한 형태로 맞춘다. 소유자 결정: **조사가 붙은 한국어 표기는 요청받은 그대로**(띄어쓰기 없이 조사를 앞말에 붙인다) 적용한다. 이 티켓이 끝나면 세 앱의 로그인 화면에 남아 있던 조사 오류·낡은 주석·형제 표면(테스트·README)의 낡은 인용이 모두 사라진다.

# Scope

## In Scope

- **콘솔** `projects/platform-console/apps/console-web/src/app/(auth)/login/page.tsx:123` 버튼 문구 「IAM 계정으로 로그인」→「IAM 로그인」(2026-09-24 UTC 실측 확인, testid `iam-login` **불변**). 같은 파일 :12의 에러 메시지(「IAM 로그인 중 오류가 발생했습니다…」)는 **그대로 둔다** — Scope 밖.
  - 같은 파일 :42 JSDoc 주석 `* renders the "Sign in with GAP" link and any returned error.`(2026-09-24 UTC 실측 확인)은 **화면에 렌더되지 않는 낡은 문구**다 — 실제 렌더 문자열(현재는 「IAM 로그인」)을 가리키도록 정정한다.
- **팬** `projects/fan-platform/web/fan-platform-web/src/app/(auth)/login/page.tsx:161` 「GAP 로 로그인」→「GAP로 로그인」(2026-09-24 UTC 실측 확인, 조사 `로` 를 앞말 `GAP` 에 붙인다).
  - `e2e-smoke/login.spec.ts:14` 의 정규식과 `__tests__/login-page.test.tsx` 가 이 문자열을 단언하면 같은 표기로 맞춘다.
- **스토어** `projects/ecommerce-microservices-platform/apps/web-store/src/features/auth/ui/LoginForm.tsx`:
  - :116 「Global Account 로 로그인하여 쇼핑을 계속하세요.」→「Global Account로 로그인하여 쇼핑을 계속하세요.」
  - :126 버튼 「Global Account 로 로그인」→「Global Account로 로그인」
  - (2026-09-24 UTC 실측 확인, 조사 `로` 를 앞말 `Global Account` 에 붙인다)
  - `__tests__/login-form.test.tsx:38,50,62,73` 이 이 문자열을 단언하면 같은 표기로 맞춘다.
- 세 앱 각각의 유닛 테스트 + typecheck + build를 로컬에서 통과시킨다.
- 세 앱 각각의 `/login`(또는 해당 라우트) 화면을 브라우저(로컬 dev 서버 또는 헤드리스)로 직접 확인한다.
- **저장소 전체 재그렙** — 변경 대상 옛 표기(「IAM 계정으로 로그인」·「GAP 로 로그인」·「Global Account 로 로그인」류 — 조사가 띄어진 형태)가 **사용자 눈에 보이는 텍스트**(코드 리터럴·README 캡션·스크린샷 alt/캡션 텍스트)에 더 남아 있는지 확인한다. 이번 조사가 이미 지목한 후보: 스토어/팬 README 스크린샷 캡션, 론처 `infra/demo/aws/site/index.html` 카드 문구 중 로그인 관련 표현. 발견되면 **같은 표기 규칙**으로 정정하고, README 캡션이면 캡션 텍스트만 고치고 이미지 자체(스크린샷 바이트)는 재촬영하지 않는다 — 이미지 안의 문구가 낡아지는 것은 이 티켓이 책임지지 않되, 발견한 이미지 목록은 AC 결과에 기록한다.

## Out of Scope

- 코드 식별자 — `GAP_TOKEN_SENTINEL`, OIDC provider id `'iam'`, 콜백 경로 등. 문자열 리터럴이 아닌 식별자는 건드리지 않는다.
- IAM Thymeleaf 로그인 템플릿(`login.html`, 타이틀 "Sign in — Global Account")은 **6개 클라이언트가 공유하는 단일 페이지**다 — 클라이언트별 브랜딩이 없으므로 이 화면에서 문구를 분기하는 것은 이 티켓의 범위가 아니다(별도 ADR 필요).
- 공유 크로스앱 로그인 폼 패키지 — 소유자 결정: **범위 밖**(자격증명 입력 지점은 이미 IAM Thymeleaf 단일 페이지 하나이고, 이를 패키지로 추출하려면 ADR이 필요하다). 이 티켓은 각 앱의 "IAM/GAP로 이동" 버튼 문구만 다루고 그 폼 자체를 건드리지 않는다.
- 로그인 로직·리다이렉트·에러 판정 흐름 변경 — 전부 무변경, 문자열만 바뀐다.

# Acceptance Criteria

- [ ] **AC-1** — 콘솔 버튼 문구가 정확히 「IAM 로그인」이고 `data-testid="iam-login"` 이 그대로다. 에러 메시지(:12)는 무변경.
- [ ] **AC-2** — 콘솔 :42 JSDoc이 실제 렌더 문자열을 가리킨다("Sign in with GAP" 문구 삭제 또는 정정).
- [ ] **AC-3** — 팬 로그인 버튼이 정확히 「GAP로 로그인」이고, 이 문자열을 단언하는 테스트가 있다면 같은 표기로 갱신되어 통과한다.
- [ ] **AC-4** — 스토어 LoginForm의 두 문구(안내문 + 버튼)가 각각 「Global Account로 로그인하여 쇼핑을 계속하세요.」/「Global Account로 로그인」이고, `__tests__/login-form.test.tsx` 의 관련 단언이 갱신되어 통과한다.
- [ ] **AC-5** — 저장소 전체에서 옛 표기(조사가 띄어진 세 문자열)를 재그렙해 **렌더되는 텍스트 기준 0건**을 확인한다. README 캡션 등 렌더되지 않는 곳(이미지 바이트 내부)에서 발견되면 목록으로 기록하고, 캡션 텍스트는 고친다.
- [ ] **AC-6** — 세 앱 각각 유닛 테스트 + typecheck + build 가 로컬에서 통과한다(각 앱의 명령을 개별 statement로 실행하고 종료 코드를 확인 — 파이프로 가려 읽지 않는다).
- [ ] **AC-7** — 세 앱의 로그인 화면을 브라우저로 열어 눈으로 확인한다(콘솔 `/login`, 팬 `(auth)/login`, 스토어 로그인 폼이 나타나는 화면).

# Related Specs

- `projects/platform-console/apps/console-web/src/app/(auth)/login/page.tsx`
- `projects/fan-platform/web/fan-platform-web/src/app/(auth)/login/page.tsx`
- `projects/ecommerce-microservices-platform/apps/web-store/src/features/auth/ui/LoginForm.tsx`
- 2026-09-24 UTC 포트폴리오 UX 전수조사(소유자 승인) — 이 티켓·`TASK-MONO-729`·`TASK-PC-FE-297`~`299`·`TASK-BE-597`·`TASK-FAN-FE-024` 의 공통 출처.

# Related Contracts

- 없음(문자열 변경만, API/이벤트 계약 무변경).

# Edge Cases

- 같은 문자열이 스크린샷 캡션·README 이미지 alt 텍스트 등 **렌더되지 않는 곳**에 남아 있을 수 있다 — AC-5가 그 경우를 목록으로만 기록하고 이미지 재촬영은 강제하지 않는다.
- IAM Thymeleaf 공유 로그인 페이지의 타이틀 "Sign in — Global Account" 는 세 앱 중 어디의 문자열도 아니므로 이 재그렙이 오검출하지 않도록 검색 범위를 세 앱 코드 + README/캡션으로 한정한다.
- 팬/스토어의 조사 표기(`GAP로`, `Global Account로`)가 영어 고유명사 뒤에 붙으므로, 다른 화면에 같은 영어 고유명사 + 조사 조합이 이미 붙어 표기되어 있는지도 재그렙 결과에서 함께 확인한다(일관성 확인 목적, 강제 변경 아님).

# Failure Scenarios

- 콘솔 버튼의 `data-testid="iam-login"` 을 실수로 바꾸면 그 testid를 참조하는 e2e/유닛 테스트가 전부 깨진다 — 문자열만 바꾸고 속성은 건드리지 않는다.
- 재그렙을 소스 코드로만 한정하고 README/캡션을 빠뜨리면 AC-5가 거짓 초록이 된다.
- 세 앱의 테스트/빌드를 파이프(`| tail` 등)로 가려 읽으면 실패가 조용히 통과로 보고될 수 있다(이 호스트에서 반복 관측된 함정) — 각 게이트를 독립 statement로 실행하고 종료 코드를 직접 확인한다.

---

# 구현 기록 (2026-09-24 UTC · 분석=Opus 5.5 · 구현=Sonnet 5)

## AC-1·AC-2 — ✅ 콘솔

- `page.tsx:123` 버튼 문구 `IAM 계정으로 로그인` → `IAM 로그인`. `data-testid="iam-login"` 무변경(속성 자체를 건드리지 않았다). `:12` 에러 메시지 무변경.
- `:42` JSDoc `renders the "Sign in with GAP" link` → `renders the "IAM 로그인" link`.

## AC-3 — ✅ 팬

- `(auth)/login/page.tsx:161` 버튼 `GAP 로 로그인` → `GAP로 로그인`.
- `e2e-smoke/login.spec.ts:14` 정규식 `/GAP 로 로그인/` → `/GAP로 로그인/`. `__tests__/login-page.test.tsx` 는 이 문자열을 단언하지 않아(testid 기반) 무변경.

## AC-4 — ✅ 스토어

- `LoginForm.tsx:116` 안내문 `Global Account 로 로그인하여 쇼핑을 계속하세요.` → `Global Account로 로그인하여 쇼핑을 계속하세요.`
- `LoginForm.tsx:126` 버튼(템플릿 리터럴 안) `Global Account 로 로그인` → `Global Account로 로그인`.
- `__tests__/login-form.test.tsx:38,50,62,73` 네 단언 갱신, 통과.

## AC-5 — ✅ 저장소 전체 재그렙 — 렌더 텍스트 기준 0건

옛 표기 세 문자열(`IAM 계정으로 로그인` · `GAP 로 로그인` · `Global Account 로 로그인`)을 저장소 전체에서 재그렙했다. 위 6개 소스/테스트 파일 수정 후 **남은 매치는 전부 렌더되지 않는 문서/메타 파일**이다:

| 파일 | 성격 | 처리 |
|---|---|---|
| `tasks/ready(→in-progress)/TASK-MONO-728-…md` 본문 · `tasks/INDEX.md` 728 행 | 이 티켓 자신의 "before→after" 설명 | 그대로 둠(서술이지 렌더 UI 아님) |
| `projects/fan-platform/tasks/ready(→in-progress)/TASK-FAN-FE-024-…md` | 선행 관계 설명("728 이 먼저 고친다") | 그대로 둠 |
| `projects/platform-console/tasks/done/TASK-PC-FE-021-…md` · `projects/ecommerce-microservices-platform/tasks/done/TASK-FE-097-…md` · `projects/iam-platform/tasks/done/TASK-BE-396-…md` | `done/` — 동결, 편집 금지(CLAUDE.md) | 미수정 |
| `knowledge/incidents/2026-05-05-ci-regression.md` | 과거 사건 기록(그 시점 실제 문구의 정확한 인용) | 미수정 — 역사적 기록을 "지금 문구"로 덮어쓰면 사건 기록이 거짓이 된다 |
| `projects/iam-platform/specs/features/oauth-social-login.md` · `projects/iam-platform/docs/adr/ADR-006-external-idp-login-sas-integration.md` | 세 앱 범위 밖(iam-platform 프로젝트 자신의 스펙/ADR 산문, Related Specs 목록 밖) | 미수정 — 발견 사실만 기록 |

**세 앱 코드 + 테스트 + `scripts/capture-portfolio.mjs`(론처 캡처 스크립트 주석 3곳 — 렌더 UI는 아니지만 스크립트가 실제로 매칭하는 문자열을 문서화한 주석이라 함께 정정) = 렌더 대상 옛 표기 0건.** README 스크린샷 캡션·`infra/demo/aws/site/index.html`은 재그렙했으나 매치 0건(기안 당시 지목한 후보 위치들이 애초에 이 세 문자열을 포함하지 않았다).

`scripts/capture-portfolio.mjs` 는 텍스트 정확 매칭이 아니라 `button:has-text("로그인")` 일반 셀렉터를 쓰므로 문구 변경으로 기능이 깨지지 않는다(주석만 낡아 있었다).

## AC-6 — ✅ 세 앱 개별 게이트(파이프 없이, 각 statement 종료 코드 직접 확인)

| 앱 | typecheck | lint | unit test | build |
|---|---|---|---|---|
| 콘솔(console-web) | `pnpm exec tsc --noEmit` rc=0 | `pnpm run lint` rc=0 | `pnpm run test` — 최초 rc=1(316개 중 14파일·19건 실패, 전부 로그인과 무관한 화면— Ledger/Approval/Delegation/Wms/Operators/Accounts/Tenants/CreateOrganizationForm 등). **격리 재실행으로 재현**: 같은 14파일을 단독/소그룹으로 재실행 → 전부 rc=0(114/114 + 67/67). 전체 스위트 동시부하(451초, `userEvent` 타이밍)로 인한 환경 flake로 판정 — 실패 파일 중 로그인 페이지(`login-error-messages.test.tsx`·`relogin-loop.test.tsx`)는 전체 스위트에서도 처음부터 초록(16/16, 7/7) | `pnpm run build` rc=0 |
| 팬(fan-platform-web) | `pnpm exec tsc --noEmit` rc=0 | `pnpm run lint` rc=0 | `pnpm run test` rc=0 (36 files / 305 tests, 최초 1건 신규 테스트 파일이 `server-only` 해석 실패로 낙제 → 저장소 관행(`vi.mock` 으로 `server-only` 임포트 모듈만 교체)으로 고쳐 rc=0) | `pnpm run build` rc=0 (`/login` 라우트 생성 확인) |
| 스토어(web-store) | `pnpm exec tsc --noEmit` rc=0 | `pnpm run lint` rc=0 | ⚪ **로컬 실행 불가 — 호스트 환경 한계(기존 관측)**: vitest 4.1.0 이 이 호스트 Node 24 에서 `ERR_PACKAGE_IMPORT_NOT_DEFINED "#module-evaluator"` 로 기동 자체가 안 된다(코드 문제 아님 — CI 는 Node 20 이라 통과). typecheck(전체 `**/*.ts` 포함 e2e)와 lint 로 로컬에서 낼 수 있는 판정은 다 냈다. 권위는 CI `frontend-unit-tests`(Node 20) — PR 오픈 후 그 결과를 확인할 것 | `pnpm run build` — `✓ Compiled successfully` · `✓ Generating static pages (23/23)` **이후**, `output:'standalone'` 트레이스 복사 단계에서 Windows 전용 `EPERM: symlink` 로 rc=1(기존 관측된 이 호스트 한계 — 이 저장소의 `next.config.ts` 가 항상 `output:'standalone'` 이고 CI(Linux)는 통과). 컴파일·정적 페이지 생성(로그인 포함) 자체는 성공을 확인했다 |

## AC-7 — ✅ 브라우저로 세 앱 로그인 화면 확인(로컬 `next dev`, 데스크톱 1280px + 모바일 ~400px)

- 콘솔 `/login`: 버튼 "IAM 로그인" 렌더 확인(스크린샷).
- 팬 `(auth)/login`: 버튼 "GAP로 로그인" 렌더 확인(스크린샷) — `TASK-FAN-FE-024` 의 헤더 추가와 겹쳐서 같은 화면에서 함께 확인(순서대로 진행했으므로).
- 스토어 로그인 폼: 안내문 "Global Account로 로그인하여 쇼핑을 계속하세요." + 버튼 "Global Account로 로그인" 렌더 확인(스크린샷). 🔵 최초 시도에서 화면이 완전히 빈 채로 렌더됐다 — 원인은 이 worktree에 `.env.local` 이 없어(신규 worktree 관행적 결함) `NEXTAUTH_SECRET` 미설정 → `useSession()` 이 `loading` 상태에서 안 풀려 `LoginPageContent` 가 계속 `null` 을 반환했다. `.env.local.example` 을 복사하고 `NEXTAUTH_SECRET` 만 채워 재기동해 해결(코드 결함 아님, `.env.local` 은 gitignore 대상이라 커밋되지 않았다).

## 편차 없음

티켓의 리터럴 AC를 벗어난 변경은 없다. 스코프 밖으로 명시된 항목(코드 식별자·IAM Thymeleaf 공유 페이지·공유 로그인 폼 패키지·로그인 로직)은 전부 무변경.
