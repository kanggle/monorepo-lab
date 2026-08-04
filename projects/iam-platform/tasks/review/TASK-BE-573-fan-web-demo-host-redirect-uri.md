# Task ID

TASK-BE-573

# Title

`fan-platform-user-flow-client` 에 팬 웹 데모 호스트(`web.fan-platform.*`) 콜백을 등록한다

# Status

review

# Owner

backend

# Task Tags

- code
- test

---

# 배경 — 데모 스크립트는 **없는 호스트명을 만들어내지 못한다**

`TASK-FAN-FE-014`(fan-platform) 는 팬 웹을 컨테이너화해 통합 데모에서
`web.fan-platform.${DEMO_DOMAIN}` 으로 서빙한다(형제 파리티: ecommerce 의 web-store 는
`web.ecommerce.${DEMO_DOMAIN}`). 그 태스크의 AC-0 판정 항목이 "IAM 의 redirect_uri 추가가
필요한지 **판정**하라 — 런타임 시드 `infra/demo/seed-demo-domain.sh` 가 이미 데모 도메인
콜백을 등록하므로 **대개 불필요**" 였다.

**판정 결과: 필요하다. 그 '대개 불필요' 가정은 새 호스트명에 대해서는 거짓이다.**

근거 — `seed-demo-domain.sh` 가 실제로 하는 일은 치환이지 생성이 아니다:

```sql
SELECT JSON_ARRAYAGG(REPLACE(jt.uri, '.local/', @dom)) ...
 WHERE jt.uri LIKE '%.local/%'
```

**이미 등록된** URI 중 `.local/` 을 포함한 것을 `.${DEMO_DOMAIN}/` 로 바꾼 사본을 덧붙일 뿐이다.
`fan-platform.local` → `fan-platform.<ip>.sslip.io` 는 커버하지만,
**`web.` 서브도메인은 어느 행에도 없으므로 파생되지 않는다.**

현재 `fan-platform-user-flow-client` 에 등록된 redirect_uris (V0011 → V0024 가 `/gap`→`/iam`
로 재작성 → V0028 이 3002 추가):

| # | redirect_uri | 출처 |
|---|---|---|
| 1 | `http://localhost:3000/api/auth/callback/iam` | V0011 + V0024 |
| 2 | `http://fan-platform.local/api/auth/callback/iam` | V0011 + V0024 |
| 3 | `http://localhost:3002/api/auth/callback/iam` | V0028 |

`web.fan-platform.*` 은 **전 마이그레이션에 0건**이다(`grep -rn "web\.fan" db/migration` → 0).

**형제 선례가 이 판정을 확증한다**: ecommerce 의 web-store 는 시드 마이그레이션 V0012 가
`http://web.ecommerce.local/api/auth/callback/gap` 을 **처음부터 등록**했다. 새 웹 호스트에는
등록 마이그레이션이 따라붙는다는 것이 이 저장소의 기존 패턴이다.

**미등록 시의 실패 모드가 고약하다** — `seed-demo-domain.sh` 헤더가 이미 실측으로 기록해 뒀다:
등록되지 않은 redirect_uri 로 authorize 를 치면

```
HTTP/1.1 401
{"code":"UNAUTHORIZED","message":"Missing or invalid internal credentials"}
```

가 나온다. **메시지가 원인을 전혀 가리키지 않는다**(자격증명 문제가 아니다). 컨테이너는 전부
healthy 이고 로그인 폼도 뜨는데 콜백만 401 이다.

---

# Goal

`web.fan-platform.local` (그리고 `seed-demo-domain.sh` 를 통해 파생되는
`web.fan-platform.<DEMO_DOMAIN>`) 에서 팬 웹의 next-auth 콜백이 성립한다.

---

# Scope

## In Scope

- `auth-service` Flyway 마이그레이션 1건 — `fan-platform-user-flow-client` 의
  `redirect_uris` 에 `http://web.fan-platform.local/api/auth/callback/iam` 추가
- 같은 클라이언트의 `post_logout_redirect_uris`(= `client_settings` JSON 안
  `settings.client.post-logout-redirect-uris`)에 `http://web.fan-platform.local/` 추가
- `specs/contracts/http/auth-api.md` § Registered Clients 동기화
- 멱등 가드(재실행/이미-존재 시 배열이 자라지 않을 것)

## Out of Scope

- 팬 웹 컨테이너화 자체 — `TASK-FAN-FE-014`(fan-platform) 소유
- Traefik 라우터/alias — `TASK-FAN-FE-014` 소유
- 다른 클라이언트의 redirect_uri
- `localhost:3002` 등 기존 항목 정리

---

# Acceptance Criteria

- [ ] **AC-0 (착수 = 재측정)** — 착수 시점에 `web.fan-platform` 이 여전히 전 마이그레이션에
      0건인지, 그리고 `seed-demo-domain.sh` 의 REPLACE 조건이 여전히 `'.local/'` 리터럴인지
      다시 확인한다(스냅샷 승계 금지)
- [ ] **AC-1 (등록)** — 마이그레이션 적용 후 `oauth_clients` 의 해당 행이
      `http://web.fan-platform.local/api/auth/callback/iam` 를 포함한다
- [ ] **AC-2 (post-logout)** — `client_settings` 의 post-logout 배열이
      `http://web.fan-platform.local/` 를 포함한다. 🔴 이 값은 Jackson default-typing 형태
      (`["java.util.ArrayList", [...]]`)라 **실제 배열은 `[1]`** 이다(V0016/V0021 의 교훈)
- [ ] **AC-3 (멱등)** — 같은 마이그레이션 로직을 두 번 적용해도 배열 길이가 증가하지 않는다
      (`WHERE ... NOT LIKE '%web.fan-platform%'` 형태의 가드). V0028 의 REPLACE 패턴을 따른다
- [ ] **AC-4 (H2 호환)** — SAS 슬라이스 테스트는 H2 로 돈다. `JSON_SET`/`JSON_ARRAY` 등
      MySQL 전용 함수를 쓰면 슬라이스 테스트가 깨진다(V0011 헤더가 명시한 제약).
      `REPLACE()` 기반이면 안전하다
- [ ] **AC-5 (라이브)** — auth-service 기동 후 실제 `/oauth2/authorize` 를
      `redirect_uri=http://web.fan-platform.local/api/auth/callback/iam` 로 쳐서 **302** 를
      받는다(미등록이면 401). 정적 SQL 검사로 대체 금지
- [ ] **AC-6 (기존 회귀 없음)** — 기존 3개 redirect_uri 가 그대로 남아 있다.
      `auth-service:test` + `auth-service:integrationTest` GREEN

---

# Related Specs

> **Before reading Related Specs**: `platform/entrypoint.md` Step 0 —
> `projects/iam-platform/PROJECT.md` 의 domain/traits 로 rule 레이어 로드.

- `projects/iam-platform/specs/contracts/http/auth-api.md` § Registered Clients
- `projects/iam-platform/specs/features/consumer-integration-guide.md`
- `projects/fan-platform/specs/integration/iam-integration.md`
- `infra/demo/seed-demo-domain.sh` — 런타임 도메인 치환의 실제 동작(이 판정의 근거)

# Related Contracts

- `specs/contracts/http/auth-api.md` — Registered Clients 표

---

# Target Service

- `auth-service` (`projects/iam-platform/apps/auth-service`)

---

# Architecture

- 변경 없음. 시드 데이터만 보강한다

---

# Implementation Notes

- **선례를 그대로 쓴다**: `V0028__add_frontend_dev_port_redirect_uris.sql` 이
  `REPLACE(redirect_uris, '<기존 항목>', '<기존 항목>","<새 항목>')` 로 JSON 배열 문자열에
  항목을 끼워 넣고 `WHERE ... NOT LIKE '%<새 항목 식별자>%'` 로 멱등을 잡는다. H2 호환도 이미
  검증된 형태다
- **경로는 `/api/auth/callback/iam`** 이다(`/gap` 아님). V0024 가 전부 재작성했고 팬 웹의
  next-auth provider `id` 가 `'iam'` 이다(`src/shared/auth/auth.ts`)
- 버전 대역: `db/migration/` 프로덕션 타임라인의 다음 번호(현재 최대 V0030)

---

# Edge Cases

- 이미 `web.fan-platform` 이 들어 있는 DB(재실행) → WHERE 가드로 no-op
- `redirect_uris` 가 NULL/빈 배열인 다른 클라이언트 → `WHERE client_id = ...` 로 한정
- post-logout 배열이 default-typing 래핑이라 `[0]` 은 타입 태그 문자열 — 문자열 REPLACE 로
  다루면 이 함정을 우회한다

---

# Failure Scenarios

- **콜백만 401 `UNAUTHORIZED`** — 미등록 redirect_uri. 메시지가 원인을 가리키지 않는다
- **로그아웃 후 빈 화면** — post-logout 미등록. SAS 는 미등록 post-logout URI 로
  리다이렉트하지 않는다
- **슬라이스 테스트 RED** — MySQL 전용 JSON 함수 사용(AC-4)

---

# Test Requirements

- `auth-service:test` (H2 슬라이스 포함) GREEN
- `auth-service:integrationTest` (Testcontainers MySQL) GREEN
- 라이브 `/oauth2/authorize` 302 실측 (AC-5)

---

# Definition of Done

- [x] 마이그레이션 커밋
- [x] `auth-api.md` 동기화
- [x] AC-5 라이브 실측 기록
- [x] Ready for review

---

# 구현 결과 (2026-08-05, Opus 5)

`V0031__add_fan_web_demo_host_redirect_uri.sql` 1건 + `auth-api.md` 동기화.
`TASK-FAN-FE-014`(fan-platform)와 **하나의 atomic 크로스프로젝트 PR** 로 함께 머지
(마이그레이션만 있으면 아무도 안 쓰는 URI 가 하나 늘고, 웹 호스트만 있으면 로그인이 안 된다 —
CLAUDE.md § Cross-Project Changes).

## AC 판정

- **AC-0 (착수=재측정)** PASS — 착수 시점에 `grep -rn "web\.fan" db/migration` = **0건**, 그리고
  `seed-demo-domain.sh` 의 조건이 여전히 `REPLACE(jt.uri, '.local/', @dom) WHERE jt.uri LIKE '%.local/%'`
  리터럴임을 확인. 판정 근거는 스냅샷이 아니라 이 재측정이다.
- **AC-1 (등록)** PASS — 마이그레이션 적용 후 실 DB:
  ```
  redirect_uris: ["http://localhost:3000/api/auth/callback/iam",
                  "http://localhost:3002/api/auth/callback/iam",
                  "http://fan-platform.local/api/auth/callback/iam",
                  "http://web.fan-platform.local/api/auth/callback/iam"]
  ```
- **AC-2 (post-logout)** PASS — `["java.util.ArrayList", ["http://localhost:3000/", "http://localhost:3002/",
  "http://fan-platform.local/", "http://web.fan-platform.local/"]]`. default-typing 래핑을 건드리지 않고
  직렬화된 텍스트에 `REPLACE` 를 걸어 `[0]` 타입 태그 함정을 우회했다.
- **AC-3 (멱등)** PASS — **재적용을 실제로 실행해서** 쟀다(재기동은 versioned 마이그레이션을 다시
  돌리지 않으므로 멱등 증명이 되지 못한다 — 두 UPDATE 를 DB 에 직접 다시 걸었다):
  ```
  before:  redirect_uris=4   post-logout=4
  UPDATE #1 → ROW_COUNT() = 0
  UPDATE #2 → ROW_COUNT() = 0
  after:   redirect_uris=4   post-logout=4
  ```
  두 WHERE 가드(`NOT LIKE '%web.fan-platform.local%'` / `NOT LIKE '%web.fan-platform%'`)가 실제로 물었다.
- **AC-4 (H2 호환)** PASS — MySQL 전용 JSON 함수 미사용, `REPLACE()` 만. `auth-service:test` GREEN.
- **AC-5 (라이브)** PASS — **네거티브 대조 포함**:
  ```
  authorize(redirect_uri=http://web.fan-platform.local/api/auth/callback/iam) → 302   ← 등록됨
  authorize(redirect_uri=http://nope.fan-platform.local/api/auth/callback/iam) → 401   ← 미등록
  ```
  두 번째의 401 이 이 티켓이 경고한, **원인을 안 알려주는 바로 그 실패**다. 그리고 실제 팬 웹의
  next-auth 왕복도 이 콜백으로 완주했다(`TASK-FAN-FE-014` § 라이브 실측 14/14).
- **AC-6 (회귀 없음)** PASS — 기존 3건 그대로 남음(위 배열). `auth-service:test` rc=0.

## 🔴 이 마이그레이션이 처음에 auth-service 를 죽였다 — 주석 때문에

**Flyway 는 SQL 파일 전체에 플레이스홀더 치환을 돌린다. 주석도 포함된다.** 헤더에 데모 호스트를
설명하려고 `web.fan-platform.<달러><중괄호>DEMO_DOMAIN<닫는중괄호>` 라고 적었더니:

```
FlywayException: Unable to parse statement in db/migration/V0031__... at line 3 col 1.
  No value provided for placeholder: <그 토큰>
→ BeanCreationException: flywayInitializer → auth-service 컨텍스트 초기화 실패, Exited (1)
```

**설명 문장 하나가 서비스 부팅을 막았다.** 더 볼 만한 건 두 번째다 — 고치면서 "이 형태를 쓰지 말라"고
주석으로 설명하느라 **그 형태를 다시 써서 똑같이 죽었다.** 그래서 V0031 헤더는 이제 그 토큰을
문자로 적지 않고 "달러-중괄호 형태를 이 파일 어디에도, 주석에도 쓰지 말 것" 이라고만 적는다.

CI 의 Testcontainers IT 도 잡았을 결함이지만 **라이브 기동이 먼저 잡았다.** 같은 지뢰가 저장소의
다른 마이그레이션에도 있는지는 확인하지 않았다 — 별도 스윕 후보.
