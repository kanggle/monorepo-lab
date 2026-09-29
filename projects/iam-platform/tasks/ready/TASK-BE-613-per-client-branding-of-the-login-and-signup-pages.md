# Task ID

TASK-BE-613

# Status

ready

# Title

로그인·회원가입 화면이 **시작 client 의 브랜드**를 그린다 — 폼은 하나, 서비스명·제목·설명·로고·색만 client 별 (`ADR-007` 구현)

# Owner

iam-platform

# Task Tags

- auth-service
- oidc
- ui

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet — 새 흐름이 아니라 기존 판별 경로(`SavedRequestTenantResolver`)에 표시값 하나를 더 얹는 일이다. 설계 결정은 ADR-007 에서 끝났다. 가장 조심할 곳은 불변 조건 3(폼 계약)과 마이그레이션의 JSON 컬럼 다루기.

---

# Goal

`ADR-007` ACCEPTED(2026-09-29, 소유자: `D1=A D2=A D3=A(문구는 "IAM 로그인") D4=A D5=A`)를 구현한다.

지금 `templates/login.html` 은 누가 보냈든 `Sign in to your Global Account` 를, `signup.html` 은 `Create your Global Account` 를 그린다. 콘솔에서 「IAM 로그인」, 팬에서 「GAP로 로그인」을 눌러도 도착한 입력 화면은 «Global Account» 라고 말한다. 이 티켓이 끝나면:

| 시작 client | 서비스명 | 제목(`<title>`·`<h1>`) | 설명 | 로고 | 대표색 |
|---|---|---|---|---|---|
| `platform-console-web` | IAM | IAM 로그인 | 운영자 계정으로 로그인합니다 | 콘솔 아이콘 | `#171717` |
| `fan-platform-user-flow-client` | GAP | GAP로 로그인 | GAP으로 안전하게 로그인합니다 | — | `#9333ea` |
| `ecommerce-web-store-client` | Global Account | Global Account로 로그인 | Global Account로 로그인하여 쇼핑을 계속하세요. | — | `#1a1a2e` |
| 그 밖 · 판별 불가(D3 + 소유자 단서) | IAM | IAM 로그인 | — | — | `#2563eb`(지금 색) |

(색은 각 앱이 이미 쓰는 값을 가져왔다: 콘솔 `--primary: 0 0% 9%`, 팬 `tailwind brand-600`, 스토어 `--color-primary`. 로고는 **파일로 있는 것만** 쓴다 — 콘솔 `console-web/src/app/icon.svg` 는 SVG 파일이고, 팬·스토어는 아이콘 파일이 없다(`git ls-files` 로 확인, 2026-09-29). 없으면 글자만 — ADR D2-A 가 로고를 선택 항목으로 정했다.)

# Scope

## In Scope

1. **판별** — `SavedRequestTenantResolver` 가 이미 하는 «저장된 `/oauth2/authorize` 요청에서만 `client_id` → 등록 client» 를 브랜딩도 쓰게 한다. `client_id` 추출을 새로 짜지 말고 **그 클래스에서 등록 client(또는 그 `ClientSettings`)를 돌려주는 메서드를 하나 열어** 재사용한다 — 신뢰 규칙이 두 곳에 복제되면 한쪽만 고쳐진다.
2. **값** — `ClientSettings` 커스텀 키:
   - `custom.branding.service-name`
   - `custom.branding.title`
   - `custom.branding.description`
   - `custom.branding.logo` — 허용 목록의 **자산 이름**(예: `console.svg`)
   - `custom.branding.primary-color` — `#RRGGBB`
   키 상수는 `OAuthClientMapper` 의 `SETTING_TENANT_ID` 곁에 둔다.
3. **해석 규칙**(`LoginBranding` 값 객체 + 해석기, 단위 테스트로 고정):
   - client 없음·키 없음·빈 문자열 → 그 **칸만** 기본값(서비스명 `IAM`, 제목 `<서비스명> 로그인`, 설명 없음, 로고 없음, 색 `#2563eb`)
   - `primary-color` 가 `^#[0-9a-fA-F]{6}$` 가 아니면 기본색
   - `logo` 가 허용 목록(코드 상수, `static/branding/` 아래 실제 파일과 1:1)에 없으면 로고 없음. 🔴 URL·경로(`/`, `..`, `:`)는 허용 목록 비교 전에 이미 탈락해야 한다 — 비교는 **정확한 이름 일치**만
4. **마이그레이션 `V0040`** — 세 client 에 위 값을 넣는다.
   - `client_settings` 는 MySQL **`JSON` 컬럼**이다(V0008). 🔴 기존 마이그레이션들의 «직렬화 텍스트에 `REPLACE()`» 방식은 **따옴표로 싼 URI 조각**을 닻으로 삼아서 통했다 — MySQL 이 JSON 을 정규화해 출력하므로(`": "`·`", "` 공백, 키 재정렬) `{"@class":...,` 같은 **구조 조각**을 닻으로 삼으면 0행 갱신인 채 SUCCESS 가 난다. `JSON_MERGE_PATCH(client_settings, '<patch>')` 를 쓴다.
   - 그 방식을 피하던 이유(«H2 슬라이스 테스트가 MySQL 전용 JSON 함수에서 깨진다», V0011/V0028/V0035 머리말)는 **이 서비스의 현재 테스트에는 해당하지 않는다** — `OAuth2AuthorizationServerSliceTest` 는 `spring.flyway.enabled=false` 로 스키마를 `db/h2/oauth2-authorization-schema.sql` 에서 만든다(2026-09-29 확인). 착수 시 **Flyway 를 H2 에서 돌리는 테스트가 여전히 없는지** 다시 grep 하라(AC-0).
   - **ASCII 만**(V0035 머리말의 규칙 — 이 디렉터리의 마이그레이션은 전부 ASCII). 한글 값은 JSON `\uXXXX` 이스케이프로 쓴다. 🔴 MySQL 문자열 리터럴에서 `\u` 의 역슬래시는 **먹힌다**(인식 못 하는 이스케이프 → 역슬래시 제거) — SQL 에는 `\\uXXXX` 로 적어야 JSON 파서가 `\uXXXX` 를 본다. 디코딩된 값을 IT 가 한글 원문과 **등호로** 비교하는 것이 이 함정의 유일한 판정이다.
   - 멱등: 이미 `custom.branding.` 를 가진 행은 건드리지 않는다(`WHERE ... NOT LIKE`).
   - 머리말 주석은 기존 관례대로 `${...}` 형태를 쓰지 않는다(Flyway 플레이스홀더 치환 — V0031 이 두 번 죽었다).
5. **`login.html`** — `<title>`·`<h1>`·설명·로고·대표색을 모델의 `branding` 에서. 대표색은 CSS 변수 하나(`--brand`)로 받아 기본 버튼·링크 색에 쓴다. 모든 값은 `th:text`/속성 이스케이프로만 출력(`th:utext` 금지).
6. **`signup.html`**(D5) — 같은 `branding`. 제목은 `<서비스명> 회원가입`. `SignupPageController` 의 GET·POST 재렌더 경로 **모두**가 `branding` 을 싣는다(오류로 다시 그릴 때 빠지면 브랜드가 한 번 더 깨진다).
7. **D4 공통 개선**(두 페이지 모두):
   - ① 비밀번호 표시/숨김 — `type="button"` 토글(`aria-pressed`·`aria-controls`), 작은 인라인 스크립트. **JS 가 없어도 폼은 지금처럼 동작**(토글은 스크립트가 붙일 때만 보이게)
   - ② 제출 중 — submit 시 버튼 비활성 + 진행 문구(`로그인 중…`/`가입 중…`). 뒤로가기(bfcache) 복귀 시 되살린다(`pageshow`)
   - ③ 오류 영역 `role="alert"`, 입력칸 `aria-describedby` 가 오류/힌트를 가리킨다
   - ④ **한국어 통일** — 라벨·버튼·안내·오류 문구 전부. `<html lang="ko">`. 소셜 버튼의 공급자 이름(GOOGLE 등)은 그대로
8. **테스트**
   - 해석기 단위 테스트: 세 client · 미설정 client · 판별 불가 · 칸별 폴백 · 잘못된 색 · 허용 목록 밖 로고 · 경로 문자 섞인 로고 · 쿼리 파라미터로 값이 바뀌지 않음(불변 조건 1)
   - 슬라이스 테스트(로그인·회원가입): 브랜딩이 화면에 그려짐, 기본값, 이스케이프(`<script>` 가 든 제목이 텍스트로 나감)
   - 기존 핀 갱신: `SignupPageBlockedSliceTest`(`"Create your Global Account"`), `LoginPageSignupLinkSliceTest`(`"Sign in"`) — 표지를 **새 화면에서도 항상 있는 문자열**로 옮긴다. 표지가 무엇이든 «폼이 없을 때도 있다» 는 원래 역할을 잃지 않게
   - Testcontainers MySQL IT: 마이그레이션 뒤 세 client 의 `RegisteredClient` 를 **리포지터리로 읽어** 다섯 값이 한글 원문과 같고, 다른 client 에는 `custom.branding.*` 가 없으며, 기존 설정(`post-logout-redirect-uris` 타입 태그, `require-proof-key`)이 그대로임을 단언
9. **스펙** — `specs/features/oauth-social-login.md` `GET /login` 행에 브랜딩 판별 한 문장.

## Out of Scope

- 세 앱의 진입 버튼 화면(이미 요청대로 — `TASK-MONO-728`)
- 인가·동의·토큰·리다이렉트 흐름(ADR 불변 조건 4)
- 오류 코드 체계·`AuthExceptionHandler` JSON 메시지(화면이 아닌 API)
- 팬·스토어 로고 제작(파일이 생기면 허용 목록에 한 줄 + 마이그레이션 한 줄)
- 데모 반영(AMI 재굽기 — 소유자 승인 사항, 아래 AC-5)

# Acceptance Criteria

- **AC-0** 착수=재측정: ① `login.html`·`signup.html` 의 문구가 위 Goal 인용과 같다 ② 세 client_id 가 마이그레이션에 등록돼 있다 ③ Flyway 를 H2 에서 돌리는 테스트가 없다 ④ 폼 계약 소비자 목록(아래 AC-3)이 그대로다. 다르면 이 티켓을 고치고 시작.
- **AC-1** 세 client 로 시작한 흐름의 `/login`·`/signup` 이 Goal 표의 값을 그리고, 판별 불가·미설정 client 는 `IAM` / `IAM 로그인` 을 그린다 — 슬라이스 테스트로.
- **AC-2** 불변 조건 1·2: 브랜딩은 요청에서 읽지 않는다, 출력은 이스케이프, 색·로고 검증 — 단위 테스트로. 각 검증에 **bite**(검증을 끄면 그 테스트가 빨개진다)를 한 번씩 확인하고 결과를 이 파일에 적는다.
- **AC-3** 불변 조건 3(폼 계약 불변): `#username`·`#password`(`name` 동일)·CSRF 필드·`form[action="/login"]`·**`button[type="submit"]` 이 폼마다 정확히 하나**(토글은 `type="button"`)·소셜 링크 `/login/oauth/{slug}`. 소비자:
  - web-store `e2e/helpers/auth.ts:75-77`, `e2e/rp-initiated-logout.spec.ts:58`, `e2e/account-type-guard.spec.ts:47`
  - console-web `tests/e2e/fixtures/login.ts:216`(`page.click('button[type="submit"]')`)
  - `scripts/capture-portfolio.mjs:240-272`
  슬라이스 테스트가 이 선택자들을 **그대로** 문다(HTML 파싱해서 개수까지).
- **AC-4** 마이그레이션 IT(Testcontainers MySQL)가 In Scope 8 의 단언으로 초록. 로컬 Docker 가 안 되면 CI 의 `Integration (iam A|B)` 결과로 — 어느 쪽인지 적는다.
- **AC-5** 데모 반영 확인은 **재굽기 뒤 창**에서: 콘솔·팬·스토어 로그인 버튼을 각각 눌러 IAM 화면의 `<h1>` 을 읽는다(3/3). 재굽기·창은 소유자 승인 사항이므로 이 AC 는 review 상태에서 대기할 수 있다.

# Related Specs

- `projects/iam-platform/docs/adr/ADR-007-per-client-branding-of-the-shared-login-page.md`(결정·불변 조건)
- `projects/iam-platform/docs/adr/ADR-006-*`(커스텀 `/login`)
- `projects/iam-platform/specs/features/oauth-social-login.md`
- `projects/iam-platform/specs/services/auth-service/architecture.md`

# Related Contracts

- 외부 API 계약 변경 없음. 화면 폼 계약(AC-3)이 사실상의 계약이며 바뀌지 않는다.

# Edge Cases

- 저장된 요청이 `/oauth2/authorize` 가 아닌 URL(예: 앱 내부 페이지) → 판별 불가 → 기본값. 그 URL 의 `client_id` 파라미터를 믿지 않는다(기존 규칙).
- `/login?error` 로 다시 그릴 때 — 세션의 저장 요청은 그대로이므로 브랜딩이 유지되어야 한다(슬라이스로).
- 회원가입 성공 → `/login?registered` — 같은 세션이므로 같은 브랜드.
- 로그아웃 뒤 `/login?logout` — 저장 요청이 없을 수 있다 → 기본값(`IAM`). 의도된 동작이다.
- 매우 긴 제목·설명 — 줄바꿈으로 흘러야 하고 카드 폭을 넘지 않는다(`overflow-wrap:anywhere`).
- 다크 모드 — 지금 `:root { color-scheme: light dark }` 인데 카드 배경이 흰색 고정이다. 이 티켓은 바꾸지 않되, 토글 버튼·진행 문구가 흰 카드 위에서 읽히는지만 본다.

# Failure Scenarios

- 마이그레이션이 0행을 갱신하고 SUCCESS — 닻/조건이 맞지 않을 때. IT 의 등호 단언이 막는다.
- `\u` 역슬래시가 먹혀 `uAC00` 같은 글자가 저장됨 — IT 의 한글 원문 등호가 막는다.
- `JSON_MERGE_PATCH` 가 기존 키의 **배열 값**을 건드림 — 패치에 없는 키는 그대로여야 한다. IT 가 `post-logout-redirect-uris` 의 타입 태그 배열을 단언한다.
- 토글 버튼이 `type` 없이 들어가 기본값 `submit` 이 됨 → `button[type="submit"]`… 는 여전히 하나지만 `form button` 이 둘이 되고, 토글을 누르면 **폼이 제출된다**. `type="button"` 을 슬라이스로 고정.
- 제출 중 비활성화가 뒤로가기 뒤에도 남아 버튼이 죽음 — `pageshow` 복원.
- 판별 경로를 새로 짜서 `/oauth2/authorize` 검사가 빠짐 → 임의 저장 URL 의 `client_id` 로 다른 브랜드를 띄울 수 있음(피싱 화면 구성). In Scope 1 의 재사용이 막는다.
