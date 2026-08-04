# Task ID

TASK-FAN-FE-014

# Title

`fan-platform-web` 을 컨테이너화해 통합 데모 스택에서 `fan-platform.${DEMO_DOMAIN}` 으로 서빙한다

# Status

ready

# Owner

frontend

# Task Tags

- code
- deploy

---

# 배경 — 통합 데모에 팬 고객 화면이 없다

`infra/demo/projects.sh` 의 `COMPOSE[fan]` 은 `projects/fan-platform/docker-compose.yml` 하나이고,
그 compose 의 서비스는 `gateway-service · community-service · artist-service · membership-service ·
notification-service · postgres · redis · kafka` 다 — **웹이 없다.** `web/fan-platform-web/` 에는
Dockerfile 도 없다.

그래서 통합 데모(`demo-up.sh full`)에서 `fan-platform.local` 은 **게이트웨이(API)** 로 뜨고,
팬 고객이 실제로 보는 화면은 어디에서도 서빙되지 않는다. 형제 프로젝트는 이미 되어 있다 —
ecommerce 의 `web-store` 는 compose 서비스이고 Traefik 라우터
`Host(\`web.ecommerce.${DEMO_DOMAIN:-local}\`)` 로 노출된다. **형제 파리티 결손이다.**

---

# Goal

`bash infra/demo/demo-up.sh fan` (또는 `full`) 로 뜬 스택에서 브라우저가
`http://fan-platform.<DEMO_DOMAIN>` (또는 별도 호스트명)으로 팬 웹에 접속해 IAM 로그인 후
피드·아티스트·멤버십·알림 화면을 연다. AMI 재굽기 후 AWS 데모에서도 동일하게 성립한다.

---

# Scope

## In Scope

- `web/fan-platform-web/Dockerfile` 신설 (`apps/web-store/Dockerfile` 을 선례로 삼되 그대로 복사하지 말 것)
- `docker-compose.yml` 에 web 서비스 추가 + Traefik 라벨 + `traefik-net` 합류
- IAM issuer / NextAuth 관련 env 배선 (`DEMO_DOMAIN` 파라미터화)
- `infra/demo/verify-demo-wrapper.sh` 불변식 (b)(c)(d)(e)(i)(j) 를 깨지 않음을 확인
- 필요 시 IAM OIDC 클라이언트의 redirect_uri 에 데모 호스트 추가가 필요한지 **판정**
  (런타임 시드 `infra/demo/seed-demo-domain.sh` 가 이미 데모 도메인 콜백을 등록하므로
  대개 불필요 — 판정 결과를 태스크에 기록)

## Out of Scope

- 팬 웹 기능/화면 변경
- 데모 데이터 시드 — `TASK-MONO-506` 소유
- 계정 시드 — `TASK-BE-571`(iam-platform) 소유
- IAM Flyway 마이그레이션 수정 (필요하다고 판정되면 별도 티켓)

---

# Acceptance Criteria

- [ ] **AC-0 (착수 = 재측정)** — `web-store` 의 Dockerfile · compose 서비스 정의 · Traefik 라벨 ·
      빌드 인자를 실제로 읽고, 팬 웹에 그대로 적용 가능한 것과 아닌 것을 구분해 기록한다
      (팬 웹은 next-auth 세션 경로가 다르다 — `getToken()` 으로 raw 세션 JWT 를 디코드한다)
- [ ] **AC-1 (프로덕션 빌드)** — 컨테이너는 `next build` → `next start` 산출물로 뜬다.
      `next dev` 는 금지한다(개발 서버는 ~2GB 를 쓰고 호스트 자원 고갈 시 SSR 이 OOM 으로 죽으면서
      UI 에는 "게이트웨이 미응답" 으로 잘못 표시된다 — 실측된 오진 경로다)
- [ ] **AC-2 (Traefik 도달)** — `DEMO_DOMAIN=local` 로 기동 시 브라우저가 호스트명으로 팬 웹을 연다.
      호스트명은 게이트웨이(`fan-platform.local`)와 **충돌하지 않게** 정한다(예: `web.fan-platform.local`)
      — 선택한 이름과 그 이유를 기록한다
- [ ] **AC-3 (로그인 성립)** — 팬 웹에서 IAM 로그인 → 콜백 → 세션 성립 → **SSR 페이지가 bearer 를 실어
      호출**해 데이터가 렌더된다. 직접 토큰 스모크로 대체 금지(선례: 직접 토큰은 200 인데 next-auth SSR
      경로는 전 페이지 401 이었다 — `TASK-FAN-FE-008`)
- [ ] **AC-4 (헬스체크 / Traefik 스킵 방지)** — 헬스체크는 IPv4 로 바인딩된 엔드포인트를 보고
      `localhost` 를 쓰지 않는다(가드 (j)). non-healthy 컨테이너는 Traefik 이 **조용히 스킵**한다
- [ ] **AC-5 (래퍼 가드)** — `bash infra/demo/verify-demo-wrapper.sh` 전체 통과
      (`container_name` 유일성 · host port 무충돌 · `build:` 서비스 기여 · `Host()` ↔ alias 정합)
- [ ] **AC-6 (메모리 실측)** — 추가된 컨테이너의 실측 메모리를 기록한다. 데모 호스트(m6i.2xlarge, 32GB)의
      여유가 이미 ~2.8GB 로 타이트하므로 `TASK-MONO-399` AC-2 재측정 입력이 된다
- [ ] **AC-7 (`.local` 하드코딩 0건)** — 모든 호스트명이 `${DEMO_DOMAIN:-local}` 를 통과한다

---

# 선행 의존 (AC-0 판정 결과, 2026-08-05)

**`TASK-BE-573`(iam-platform) — 하드 선행.** AC-3(로그인 성립)은 이것 없이 성립하지 않는다.

위 In Scope 의 판정 항목("런타임 시드 `infra/demo/seed-demo-domain.sh` 가 이미 데모 도메인
콜백을 등록하므로 **대개 불필요**")을 실제로 판정한 결과 **필요하다**로 나왔다. 그 스크립트는
*이미 등록된* URI 의 `.local/` 을 `.${DEMO_DOMAIN}/` 로 **치환**할 뿐이고
(`REPLACE(uri,'.local/',@dom) WHERE uri LIKE '%.local/%'`), **`web.` 서브도메인을 새로
만들어내지는 못한다.** `fan-platform-user-flow-client` 의 현재 등록은 3건
(`localhost:3000` · `fan-platform.local` · `localhost:3002` — V0011 → V0024 가
`/gap`→`/iam` 재작성 → V0028)뿐이고 `web.fan-platform` 은 전 마이그레이션에 0건이다.

형제 선례가 이를 확증한다 — ecommerce 의 web-store 는 시드 마이그레이션 V0012 가
`web.ecommerce.local` 콜백을 **처음부터 등록**했다. 새 웹 호스트에는 등록 마이그레이션이
따라붙는 것이 이 저장소의 패턴이다.

🔴 미등록 시 authorize 는 `401 {"code":"UNAUTHORIZED","message":"Missing or invalid
internal credentials"}` 를 뱉는데 **메시지가 원인을 전혀 가리키지 않는다**(자격증명 문제가
아니다). 컨테이너는 전부 healthy 이고 로그인 폼도 200 인데 콜백만 죽는다.

---

# Related Specs

> **Before reading Related Specs**: `platform/entrypoint.md` Step 0 —
> `projects/fan-platform/PROJECT.md` 의 domain/traits 로 rule 레이어 로드.

- `infra/demo/README.md` — 래퍼 불변식 (a)~(u), 특히 (i)(j)(k)(l)
- `projects/ecommerce-microservices-platform/apps/web-store/Dockerfile` — 형제 선례
- `projects/fan-platform/specs/services/gateway-service/architecture.md`
- `TEMPLATE.md` § Local Network Convention

# Related Skills

- `.claude/skills/frontend/...`
- `.claude/skills/INDEX.md`

---

# Related Contracts

- 없음 (배포 형태만 — API 계약 불변)

---

# Target Service

- `fan-platform-web` (`web/fan-platform-web`)

---

# Architecture

- 기존 앱 구조 불변. 이 태스크는 **배포 아티팩트**만 추가한다

---

# Implementation Notes

- **포트 선례**: 팬 웹 `package.json` 의 dev 스크립트는 `--port 3002` 인데 IAM 에 등록된 콜백은
  `localhost:3000` 과 `localhost:3002` 둘 다 있다(`V0011` + `V0028`). 컨테이너에서는 Traefik 호스트명으로
  접근하므로 로컬 dev 포트 규약과 별개다 — 혼동하지 말 것.
- **`NEXT_PUBLIC_*` 는 빌드 타임에 인라인된다.** 런타임에 달라져야 하는 값(데모 도메인)을
  `NEXT_PUBLIC_` 접두사로 넣으면 AMI 에 구워진 값이 고정된다. 런타임 값은 서버 사이드에서 읽는다
- **Secure 쿠키** — 데모는 HTTP 다. `Secure` 쿠키는 localhost 를 제외하면 HTTP 에서 저장되지 않는다.
  콘솔이 `CONSOLE_COOKIE_SECURE=false` 로 푸는 것과 동등한 처리가 필요한지 확인한다
- Dockerfile 은 멀티스테이지 + standalone 출력으로 이미지 크기를 줄인다(web-store 선례 확인)

---

# Edge Cases

- 게이트웨이와 웹이 같은 호스트명을 요구하면 Traefik 라우터가 경로 우선순위로 갈린다 — 호스트명을 분리한다
- pnpm 워크스페이스 루트가 `projects/fan-platform` 이라 빌드 컨텍스트를 잘못 잡으면 lockfile 을 못 찾는다
- 이미지 빌드는 `DEMO_BUILD=1` 경로에서만 일어난다. AMI 는 prebake 하므로 **재굽기 전까지 AWS 데모에
  도달하지 않는다**(compose·앱소스는 AMI 층)

---

# Failure Scenarios

- **컨테이너는 healthy 인데 브라우저 404** — Traefik 라우터 이름과 실제 호스트명 표기 불일치
  (선례: `tr '.' '-'` 로 파생한 대시 표기 vs 점 표기, sslip.io 가 둘 다 해석해줘 DNS/TCP 는 붙고
  라우터만 미매치 → 404, 에러 로그 0건)
- **로그인은 되는데 전 페이지가 데이터 없음** — SSR fetch 가 bearer 없이 나감(FAN-FE-008 재발). AC-3
- **빈 `DEMO_DOMAIN`** → `Host(\`web.fan-platform.\`)` 라우터가 만들어지고 아무것과도 매치하지 않는다
- **호스트 자원 고갈 시 SSR OOM** → UI 는 백엔드 장애처럼 보인다. AC-1(프로덕션 빌드)이 완화

---

# Test Requirements

- 컨테이너 빌드 + 기동 + 헬스체크 통과
- `verify-demo-wrapper.sh` 정적 가드 전체
- 브라우저 실주행: 로그인 → 최소 3화면(피드/아티스트/멤버십) 데이터 렌더

---

# Definition of Done

- [ ] Dockerfile + compose 서비스 + Traefik 라벨 커밋
- [ ] 브라우저 실주행 증거 기록
- [ ] `verify-demo-wrapper.sh` 통과
- [ ] 메모리 실측 기록
- [ ] Ready for review
