# Task ID

TASK-MONO-728

# Title

로그인 버튼·안내 문구 정합 — 콘솔·팬·스토어 3앱, 조사(2026-09-24)를 좁힌 실행 티켓

# Status

ready

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
