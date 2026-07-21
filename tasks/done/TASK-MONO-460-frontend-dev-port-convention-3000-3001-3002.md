# TASK-MONO-460 — 세 제품 프론트(console/web-store/fan)가 dev 포트 + OIDC redirect_uri 를 전부 `localhost:3000` 에 하드코딩해 동시 dev 실행·로그인이 불가능하다 — 앱별 고정 포트 규약 코드화

> **리넘버 노트**: 이 티켓은 최초 `TASK-MONO-458` 로 파일링됐으나, 동시 세션이 `TASK-MONO-458`(gateway-it-parity, PR #2833) 과 `TASK-MONO-459`(adr-citation-drift) 를 이미 점유 중이어서 **460 으로 재번호**했다(메모리 `env_concurrent_session_task_id_collision`).

**Status:** done

**Type:** TASK-MONO
**Analysis model:** Opus 4.8 / **Recommended impl model:** Sonnet 4.6 (기계적 편집 + 신규 Flyway 마이그레이션 1개 + e2e/env 소비처 전수 갱신. 다만 AC-0 의 "실제 소비처(Playwright·NEXTAUTH_URL·CI)를 전수 셌나" 재측정이 실질 — 범위 물려받기 금지)

> 2026-07-21 fan-platform 풀스택 로컬 로그인 라이브 검증 중 발견. 세 제품 SPA 를 `next dev` 로 띄우면 전부 `:3000` 을 점유해 **둘 이상 동시 실행 불가**이고, IAM 에 등록된 dev **redirect_uri 도 셋 다 `localhost:3000`** 이라 포트를 옮기면 `redirect_uri_mismatch` 로 로그인이 깨진다. 즉 "포트만 바꾸면 됨"이 아니라 dev 포트 + redirect_uri 세트를 함께 코드화해야 한다.

---

## Goal

각 제품 프론트에 **충돌 없는 고정 dev 포트**를 부여하고, 그 포트를 각 앱의 IAM OIDC redirect_uri 등록과 정합시킨다. 채택 규약:

| 프론트 | 앱 경로 | dev 포트 | 비고 |
|---|---|---|---|
| console-web | `projects/platform-console/apps/console-web` | **3000** | 변경 없음(현 default `next dev` + V0015 redirect 이미 `localhost:3000`) |
| web-store | `projects/ecommerce-microservices-platform/apps/web-store` | **3001** | `--port 3001` + 신규 redirect `localhost:3001` |
| fan-platform-web | `projects/fan-platform/web/fan-platform-web` | **3002** | `--port 3002` + 신규 redirect `localhost:3002` |

> 이 규약은 **`next dev`/`next start` 단독 실행 포트에만** 적용된다. 커밋된 정식 접근인 **Traefik `.local` 호스트네임 라우팅**(`console.local`/`web.ecommerce.local`/`fan-platform.local`, 전부 :80)은 이 변경과 무관하며 그 경로에선 애초에 포트 충돌이 없다. 즉 "여러 프론트를 dev 모드로 동시에 띄우는 개발 편의" 레이어를 확정하는 것이다.

### 현재 실태 (전수 grep 확인, 2026-07-21)

**dev 포트 — 셋 다 3000:**
- `console-web/package.json`: `"dev": "next dev"` → Next 기본값 3000
- `web-store/package.json`: `"dev": "next dev --port 3000"`
- `fan-platform-web/package.json`: `"dev": "next dev --port 3000"`

**IAM 등록 dev redirect_uri — 셋 다 localhost:3000:**
- console: `V0015` → `["http://console.local/api/auth/callback","http://localhost:3000/api/auth/callback"]` (콜백 경로에 `/iam` 접미사 **없음**)
- web-store: `V0012` (첫 client) → `["…/callback/gap","…"]` → `V0024` 로 `/callback/iam` 리네임 → `localhost:3000/api/auth/callback/iam`
- fan: `V0011` → `V0024` → `localhost:3000/api/auth/callback/iam`

(참고: `V0012` 둘째 client 는 이미 `localhost:3001` 을 갖지만 이는 **폐기된 `admin-dashboard`**(ADR-MONO-031/TASK-MONO-259 삭제)용 별개 client 다. web-store 가 3001 을 가져가도 client 가 달라 무해하나, 신규 redirect 는 web-store 자기 client 에 추가해야 한다.)

---

## Scope

**In scope**

1. **web-store 포트 3001**: `web-store/package.json` `dev`/`start` → `--port 3001`.
2. **fan 포트 3002**: `fan-platform-web/package.json` `dev`/`start` → `--port 3002`.
3. **신규 Flyway 마이그레이션 1개**(`projects/iam-platform/apps/auth-service/src/main/resources/db/migration/V00NN__…`): web-store client 에 `http://localhost:3001/api/auth/callback/iam`, fan client 에 `http://localhost:3002/api/auth/callback/iam` 를 redirect_uris 에 **추가**(기존 3000 은 **유지** — CI/e2e 하위호환, additive·non-breaking). post_logout_redirect_uris 도 앱루트(`localhost:3001/`, `localhost:3002/`) 병행 추가.
4. **각 앱 `NEXTAUTH_URL`/env 기본값 정합**: fan `web/fan-platform-web/src/shared/config/env.ts` `nextAuthUrl` 기본 `localhost:3000` → `3002`; web-store 의 동등 값(next-auth base) → `3001`. `.env.example`/README 있으면 동기화.
5. **e2e/CI 포트 소비처 전수 갱신**: Playwright config, docker/compose web env, README/기동 스크립트 등 `localhost:3000` 을 가정한 web-store/fan 소비처를 새 포트로. (console 은 3000 유지라 무영향.)
6. **규약 문서화**: dev-server 포트 규약을 정경 위치에 기록(`TEMPLATE.md` Local Network Convention 하위 절 또는 `docs/guides/monorepo-workflow.md`) — "Traefik 호스트네임과 별개, dev 단독 실행 전용"임을 명시.

**Out of scope**

- **console-web 변경 0** — 3000 유지가 규약과 정합(현 default + V0015 redirect 일치).
- **Traefik `.local` 라우팅/`infra/traefik` 변경 0** — 이 규약은 dev 포트 한정.
- 기존 `localhost:3000` redirect **제거 금지** — additive 만(하위호환).
- 폐기된 admin-dashboard client(`V0012` 둘째) 정리 — 별개 청소 티켓.
- fan-platform-web OIDC **scope 드리프트**(client 요청 scope ⊄ 등록 scope) — **별개 발견/티켓 후보**(본 세션 메모리 `project_fan_platform_local_bringup` 기록, TASK-MONO-456 형제류). 포트와 무관.

---

## Acceptance Criteria

- **AC-0 (gate — 재측정, 범위 물려받기 금지)** — 착수 시 다음을 실측 재확인한다(이 티켓의 숫자·경로는 **가설**이다):
  1. 세 `package.json` dev 포트 + 세 client 의 `oauth2_registered_client.redirect_uris` 실값을 재확인(코드가 이긴다).
  2. **소비처 전수 재열거** — `localhost:3000` / `--port 3000` / `NEXTAUTH_URL` / Playwright `baseURL` 을 web-store·fan 트리에서 grep 해 In-scope §5 목록이 완전한지 검증. 누락 발견 시 목록 확장.
  3. web-store 가 실제로 next-auth OIDC 로그인을 쓰는지(= redirect 갱신이 유효한지) 확인 — 안 쓰면 §3 web-store 부분은 포트만.
- **AC-1** — web-store `:3001`, fan `:3002` 로 `next dev` 기동, console `:3000` 과 **동시 실행 가능**(포트 충돌 0).
- **AC-2** — 신규 마이그레이션 적용 후 web-store(`:3001`)·fan(`:3002`) 브라우저 OIDC 로그인이 `redirect_uri_mismatch` 없이 **콜백까지** 완주. 재현 근거를 PR 본문에.
- **AC-3** — 기존 `localhost:3000` redirect 유지로 CI/e2e(3000 가정)가 GREEN. `./gradlew :projects:iam-platform:apps:auth-service:flywayValidate`(또는 slice/IT) + 마이그레이션이 **H2 portable**(V0011 주석의 H2 슬라이스 제약 — `JSON_SET`/`JSON_ARRAY` 금지, 리터럴 배열 임베드).
- **AC-4** — 규약 문서에 "dev 단독 실행 전용, Traefik 호스트네임과 별개" 명시 + 표(3000/3001/3002).

---

## Related Specs / Contracts

- `TEMPLATE.md` § Local Network Convention (Traefik 호스트네임 master — 이 티켓은 그 **자매(dev 포트)** 레이어이며 상충하지 않음)
- `projects/iam-platform/apps/auth-service/src/main/resources/db/migration/V0011`·`V0012`·`V0015`·`V0024` (기존 redirect_uris 시드 — 편집 금지, 신규 마이그레이션으로 additive)
- `projects/platform-console/specs/contracts/console-integration-contract.md`(console OIDC client 계약 — 3000 유지 확인용)
- API/이벤트 계약 변경 0 — 순수 dev 배선 + 시드 데이터 추가.

---

## Edge Cases

1. **historical 마이그레이션 편집 금지** — `V0011`/`V0012`/`V0015` 는 이미 적용됨(Flyway checksum). 반드시 **신규 버전** `V00NN` 으로 `UPDATE … redirect_uris = …` (또는 문자열 concat). checksum 위반 시 전 데모/CI Flyway 실패.
2. **console 콜백 경로 상이** — console 은 `/api/auth/callback`(no `/iam`), fan·web-store 는 `/api/auth/callback/iam`. 마이그레이션에서 앱별 정확한 경로를 쓸 것(대리지표 금지).
3. **`NEXTAUTH_URL` 누락 시 콜백 base 불일치** — 포트만 바꾸고 `NEXTAUTH_URL`/env 기본을 안 바꾸면 next-auth 가 여전히 `localhost:3000` 콜백을 생성 → mismatch. 포트·redirect·NEXTAUTH_URL **세트**로.
4. **H2 슬라이스 portability** — `V0011` 주석대로 auth-service SAS 슬라이스 테스트는 H2. MySQL 전용 `JSON_SET`/`JSON_ARRAY` 금지, redirect_uris 를 리터럴 JSON 배열로 임베드.
5. **admin-dashboard 3001 잔재** — 폐기 앱 client 가 이미 3001 을 물고 있음(무해, 별 client). web-store redirect 는 **web-store client** 에만 추가.

---

## Failure Scenarios

- **F1 — 기존 3000 redirect 를 제거/치환.** 3000 을 가정한 CI/e2e·기존 데모가 즉시 깨진다. **additive** 만(3000 유지 + 신규 포트 추가).
- **F2 — `package.json` 포트만 바꾸고 마이그레이션/NEXTAUTH_URL 누락.** 로그인이 `redirect_uri_mismatch` 로 깨짐. 세트 원칙.
- **F3 — 적용된 마이그레이션(V0011 등)을 직접 편집.** Flyway checksum mismatch → auth-service 부팅 실패(전 스택 로그인 불가). 신규 버전으로만.
- **F4 — Playwright/CI 의 `baseURL: localhost:3000` 잔존(web-store/fan).** 포트 이동 후 e2e 가 빈 포트를 때려 전건 실패. AC-0 §2 전수 재열거로 방지.

---

## Definition of Done

- [ ] AC-0 재측정(3 package.json + 3 client redirect 실값 + 소비처 전수 재열거 + web-store OIDC 사용 여부)
- [ ] web-store `--port 3001`, fan `--port 3002` (package.json dev/start)
- [ ] 신규 Flyway 마이그레이션(additive redirect `localhost:3001`/`3002`, post-logout 병행, H2 portable)
- [ ] fan/web-store `NEXTAUTH_URL`·env 기본 정합
- [ ] e2e/CI/README 포트 소비처 전수 갱신
- [ ] 규약 문서화(dev 전용·Traefik 별개 명시 + 표)
- [ ] web-store:3001·fan:3002·console:3000 동시 기동 + 로그인 완주 재현 근거
- [ ] auth-service Flyway validate/slice GREEN (기존 3000 하위호환 유지 확인)

---

## Notes

- **분량**: small–medium. 편집 자체는 기계적(package.json 2 + 마이그레이션 1 + env/doc)이나, **소비처 전수 재열거(AC-0 §2)와 로그인 재현(AC-2)** 이 실질 비용.
- **근본 원인 = 선언 부재**: 세 앱이 각자 3000 을 하드코딩한 건 "dev 포트 규약"이 어디에도 선언돼 있지 않기 때문(1곳에만 있는 규칙=없는 규칙). 문서화(§6)가 재발 방지의 본체.
- **형제**: TASK-MONO-456(데모 콘솔 로그인 env 갭)과 같은 "데모/로컬 배선 정합" 계열. 둘 다 "healthy 컨테이너 ≠ 로그인 가능"의 변주.
- **발견 경위**: fan 풀스택 로컬 로그인 검증 세션. 그 세션에서 fan 을 3000 에 못박은 redirect_uri 때문에 console-web(3000)과 동시 기동 불가 + fan 3002 이동 시 로그인 깨짐을 실측 확인. (메모리 `project_fan_platform_local_bringup`.)
