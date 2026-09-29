# ADR-007: 공유 로그인 페이지의 클라이언트별 브랜딩 — 폼은 하나, 서비스별 값만 다르게

**Status:** ACCEPTED
**Date**: 2026-09-29 (proposed) · 2026-09-29 (accepted)
**Deciders**: kanggle
**Supersedes**: —
**Relates to**: ADR-001 (OIDC 채택 — IAM 이 6개 도메인의 공유 인증 공급자), ADR-006 (커스텀 `/login` 페이지 — 소셜 로그인 통합), `TASK-BE-581`(로그인 페이지가 시작 client 로 회원가입 노출을 가른다), 모노레포 `TASK-MONO-728`(세 앱 로그인 버튼 문구)

---

## Context

### 요청 (2026-09-24 포트폴리오 UX 요청 § 1, 소유자)

| 서비스 | 로그인 서비스 | 문구 |
|---|---|---|
| Platform Console | IAM | IAM 로그인 |
| Fan Platform | GAP | GAP로 로그인 |
| E-commerce Store | Global Account | Global Account로 로그인하여 쇼핑을 계속하세요. / Global Account로 로그인 |

그리고 § 1-4: *로그인 폼 레이아웃·입력 필드·버튼·오류·로딩·비밀번호 표시/숨김·반응형·접근성·로고·상단 구조를 공통화하고, 서비스별로는 `serviceName · authProvider · title · description · logo · primaryColor · postLoginRedirect` 만 다르게.*

### 지금 구조 — 폼은 이미 하나다

- **자격증명을 입력하는 화면은 IAM `auth-service` 의 `templates/login.html` 하나뿐**이다(ADR-006 이 만든 커스텀 `/login`, `LoginPageController`). 콘솔·팬·스토어를 포함한 모든 OIDC client 가 이 한 페이지를 쓴다.
- 세 앱의 `/login` 은 폼이 아니라 **IAM 으로 보내는 버튼 화면**이다. 그 버튼 문구는 `TASK-MONO-728` 이 요청대로 맞췄다.
- `postLoginRedirect` 는 이미 client 별이다 — 등록된 `redirect_uri` 가 그것이다.

### 결함 — 브랜드가 버튼까지만 맞고 입력 화면에서 깨진다

`login.html` 은 **누가 보냈든** `<title>Sign in — Global Account</title>` · `<h1>Sign in to your Global Account</h1>` 를 그린다. 그래서 콘솔에서 「IAM 로그인」을 눌러도, 팬에서 「GAP로 로그인」을 눌러도 도착한 화면은 «Global Account» 라고 말한다. `signup.html` 도 같다(`Create your Global Account`). 곁결함: 두 페이지 모두 영어·한국어가 섞여 있고(라벨·오류는 영어, 회원가입 안내는 한국어), 비밀번호 표시/숨김·제출 중 표시·오류의 `role="alert"` 가 없다.

### 이미 있는 판별 수단 — 새로 만들 필요가 없다

`SavedRequestTenantResolver` 가 **저장된 `/oauth2/authorize` 요청에서만** `client_id` 를 읽고(임의의 저장 URL 이 실은 `client_id` 를 거부), 등록 client 의 `ClientSettings`(`custom.tenant_id` · `custom.tenant_type`)에서 테넌트를 꺼낸다. `TASK-BE-581` 이 이 값으로 회원가입 링크를 가른다. 브랜딩은 **같은 출처, 같은 신뢰 규칙**으로 고를 수 있다.

## Decision

소유자가 아래 각 칸에서 하나를 고른다. 🔵 표시는 **제안자(에이전트)의 추천이지 결정이 아니다**.

### 채택 (2026-09-29, 소유자)

> `ADR-007 ACCEPTED — D1=A D2=A D3=A(문구는 "IAM 로그인") D4=A D5=A`

| 칸 | 채택 | 뜻 |
|---|---|---|
| D1 | A | 등록 client 의 `ClientSettings` 커스텀 키(`custom.branding.*`), Flyway 로 설정 |
| D2 | A | `serviceName` · `title` · `description` · `logo`(선택) · `primaryColor` |
| D3 | A + **단서** | 판별 불가·미설정 client 에도 **기본 브랜딩이 있다**(D3-A 의 구조). 단 그 기본값의 문구는 «Global Account» 가 아니라 **«IAM 로그인»** — 소유자가 적은 단서다. 🔴 아래 D3 표의 D3-A 설명(«지금 모양 그대로 Global Account»)은 제안 당시 문장이고, **문구는 이 단서가 이긴다**. 그래서 직접 방문(`/login` 을 주소로 연 경우)과 브랜딩 없는 client(scm·finance·erp·wms 등)의 화면은 오늘과 **달라진다**: «Global Account» → «IAM». 스토어는 자기 브랜딩(`Global Account`)을 명시적으로 가지므로 영향이 없다 |
| D4 | A | 비밀번호 표시/숨김 · 제출 중 표시 · `role="alert"`/`aria-describedby` · 한국어 통일 |
| D5 | A | `signup.html` 도 같은 브랜딩·개선을 받는다 |

구현 = `TASK-BE-613`(iam-platform `tasks/ready/`, 이 ACCEPT 와 같은 PR 에서 기안).

### 값 변경 (2026-09-29, 소유자) — 팬 = GAP → IAM

> 소유자: «IAM으로 변경» (팬의 «GAP» 표기를 IAM 으로)

결정 칸(D1~D5)은 그대로이고 **아래 «적용 대상» 표의 팬 행 값만** 바뀐다. 근거: «GAP» 은 IAM 의 옛 이름이다(테넌트 슬러그 `gap` → `iam` 은 `V0024` 에서 이미 바뀌었다) — 화면 문구만 옛 이름으로 남아, 같은 공급자가 IAM·GAP·Global Account 세 이름으로 불리고 있었다. 팬을 «Global Account» 로 맞추지 않은 이유: 팬과 스토어 계정은 테넌트가 달라 서로 다른 계정인데(`TASK-BE-611`), 같은 이름이면 한 계정으로 양쪽을 쓴다고 읽힌다.

| client | serviceName | title | description |
|---|---|---|---|
| `fan-platform-user-flow-client` | IAM | IAM 로그인 | IAM으로 안전하게 로그인합니다 |

팬 앱 진입 버튼도 같이 바뀐다: «GAP로 로그인» → «IAM 로그인»(`TASK-MONO-728` 이 맞춘 문구의 후속). 스토어는 «Global Account» 그대로.

### D1 — 브랜딩 값을 어디에 두는가

| 선택지 | 내용 | 대가 |
|---|---|---|
| **D1-A** 🔵 | 등록 client 의 `ClientSettings` 커스텀 키(`custom.branding.*`) — Flyway 로 client 별 설정 | 테넌트와 **같은 자리·같은 판별 경로**. IAM 코드가 소비자 제품 이름을 모른다(공유 공급자 경계 유지). 문구를 바꾸려면 마이그레이션 |
| D1-B | `auth-service` 코드 안의 정적 표(`client_id` → 값) | 마이그레이션 불필요, 테스트로 고정 쉬움. 대신 **IAM 코드에 소비자 브랜드가 하드코딩**되고 client 추가마다 IAM 코드 변경 |
| D1-C | 브랜딩 전용 테이블 | 값 다섯 개에 과한 구조 |

### D2 — client 마다 달라지는 값

| 선택지 | 내용 |
|---|---|
| **D2-A** 🔵 | `serviceName` · `title`(`<title>`·`<h1>`) · `description`(부제) · `logo` · `primaryColor` — 요청 § 1-4 목록 중 IAM 이 그리는 것 전부. `logo` 는 선택(없으면 글자만) |
| D2-B | 문구만(`serviceName` · `title` · `description`). 로고·색은 공통 |

`authProvider` 는 화면에서 `serviceName` 과 같은 말이라 따로 두지 않는다. `postLoginRedirect` 는 이미 `redirect_uri` 가 맡는다 — **브랜딩에 넣지 않는다**(리다이렉트 결정은 인가 서버의 기존 검증에만 맡긴다).

### D3 — client 를 모르거나 브랜딩이 없을 때(직접 방문 · 미설정 client)

| 선택지 | 내용 |
|---|---|
| **D3-A** 🔵 | 지금 모양 그대로 «Global Account» — 기본 소비자 브랜드이고, 오늘의 동작과 같다 |
| D3-B | 중립 문구(«로그인») |

### D4 — 모든 client 가 함께 받는 폼 개선

| 선택지 | 내용 |
|---|---|
| **D4-A** 🔵 | ① 비밀번호 표시/숨김(작은 인라인 스크립트 — **JS 가 없어도 폼은 지금처럼 동작**) ② 제출 중 버튼 비활성 + 진행 문구 ③ 오류 `role="alert"` · 입력칸 `aria-describedby` ④ **한국어로 통일**(세 앱 모두 한국어 UI) |
| D4-B | ①②③ 만 — 언어는 그대로 |

### D5 — `signup.html` 도 같이 하는가

| 선택지 | 내용 |
|---|---|
| **D5-A** 🔵 | 같이 — 로그인 화면의 「회원가입」에서 이어지는 한 흐름이다. 브랜드가 한 화면 건너 다시 «Global Account» 로 돌아가면 같은 결함이 남는다 |
| D5-B | 로그인만 |

### 불변 조건 (선택지가 아니다 — 어느 조합이든 지킨다)

1. 🔴 **브랜딩 값은 요청에서 절대 읽지 않는다.** 저장된 authorize 요청의 `client_id` → 등록 client 설정뿐(`SavedRequestTenantResolver` 와 같은 규칙). 쿼리 파라미터로 문구·색·로고를 바꿀 수 없다.
2. 모든 값은 Thymeleaf `th:text`/속성 이스케이프로만 출력. `primaryColor` 는 `#RRGGBB` 검증, 실패하면 기본색. `logo` 는 `auth-service` 가 서빙하는 **허용 목록의 정적 자산 이름**만 — 외부 URL 금지.
3. 🔴 **폼 계약은 그대로**: 입력칸 `id`/`name`(`username`·`password`), CSRF 필드, `POST /login`, 소셜 링크 경로. web-store e2e(`e2e/helpers/auth.ts`)와 `scripts/capture-portfolio.mjs` 가 이 폼을 직접 조작한다.
4. 인가·동의·토큰·리다이렉트 흐름은 **바뀌지 않는다** — 화면 표시만 바뀐다.

### 적용 대상 (D1~D5 가 정해진 뒤의 값 — 요청표 그대로)

| client | serviceName | title | description |
|---|---|---|---|
| `platform-console-web` | IAM | IAM 로그인 | 운영자 계정으로 로그인합니다 |
| `fan-platform-user-flow-client` | ~~GAP~~ IAM | ~~GAP로 로그인~~ IAM 로그인 | ~~GAP으로~~ IAM으로 안전하게 로그인합니다 (값 변경 2026-09-29 — 위 절) |
| `ecommerce-web-store-client` | Global Account | Global Account로 로그인 | Global Account로 로그인하여 쇼핑을 계속하세요. |
| 그 밖 · 판별 불가 | D3 의 기본값 → 채택: IAM | IAM 로그인 | (없음) |

(description 은 각 앱 진입 화면에 이미 있는 문구를 가져왔다 — 팬 `TASK-MONO-728` CORRECTION, 스토어 `LoginForm.tsx`. 콘솔 문구는 제안이다.)

## Consequences

- **좋아지는 것**: 요청 § 1-1 의 브랜드 정책이 버튼에서 끝나지 않고 **실제 입력 화면까지** 이어진다. 폼 개선이 한 곳에서 모든 client 로 간다 — 요청 § 1-4 의 «공통 컴포넌트 + 서비스별 설정값» 이 이미 있는 구조 위에서 그대로 성립한다.
- **다시 확인할 것**: `SignupPageBlockedSliceTest` 가 `"Create your Global Account"` 를 표지로 핀한다 — D3·D4·D5 결과에 맞춰 갱신. e2e(web-store `auth.ts`, 콘솔·팬 e2e, `capture-portfolio.mjs`)는 불변 조건 3 이 지켜지면 무영향이어야 하고, 그것을 실행으로 확인한다.
- **배포**: `auth-service` 는 데모 AMI 에 구워진다 — 반영은 **재굽기 뒤**. 다음 재굽기(18차) 전에 머지되면 같은 창에서 확인할 수 있다.
- **범위 밖**: 세 앱의 진입 버튼 화면을 교차 프로젝트 프런트 패키지로 묶는 일(소유자가 이번에 고르지 않은 선택지 B — 필요하면 별도 모노레포 ADR), 세션 만료·로그아웃 처리의 통일(각 앱 몫).

## ACCEPT 형식

`platform/architecture-decision-rule.md § The ACCEPTED Gate` 에 따라, 소유자가 **이 ADR 을 이름으로 부르고 칸마다 값을 적어야** ACCEPTED 가 된다. 예: `ADR-007 ACCEPTED — D1=A D2=A D3=A D4=A D5=A`. «진행» · «OK» 는 ACCEPT 가 아니다. ACCEPT 하는 PR 에서 구현 티켓을 같이 기안한다(번호는 그때 잡는다 — 동시 세션 ID 충돌 방지).
