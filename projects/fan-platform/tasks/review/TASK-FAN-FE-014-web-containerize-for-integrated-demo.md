# Task ID

TASK-FAN-FE-014

# Title

`fan-platform-web` 을 컨테이너화해 통합 데모 스택에서 `fan-platform.${DEMO_DOMAIN}` 으로 서빙한다

# Status

review

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

- [x] Dockerfile + compose 서비스 + Traefik 라벨 커밋
- [x] 실주행 증거 기록 (아래 § 구현 결과)
- [x] `verify-demo-wrapper.sh` 통과
- [x] 메모리 실측 기록
- [x] Ready for review

---

# 구현 결과 (2026-08-05, Opus 5)

## 산출물

| 파일 | 성격 |
|---|---|
| `web/fan-platform-web/Dockerfile` | 신설. 멀티스테이지 + standalone, 빌드 컨텍스트 = pnpm 워크스페이스 루트 |
| `web/fan-platform-web/next.config.ts` | `output:'standalone'` 을 `NEXT_OUTPUT_STANDALONE=1` **옵트인**으로 |
| `docker-compose.yml` | `fan-platform-web` 서비스 + Traefik 라벨 + 헬스체크 + 256M 상한 |
| `infra/traefik/docker-compose.yml` (루트) | `web.fan-platform.<DEMO_DOMAIN>` network alias 1행 |
| `projects/iam-platform/.../V0031__*.sql` | `TASK-BE-573` — 콜백 등록(하드 선행, 같은 PR) |
| `projects/iam-platform/specs/contracts/http/auth-api.md` | Registered Clients 행 동기화 |

## AC 판정

- **AC-0 (재측정)** PASS — web-store 선례를 실제로 읽고 **그대로 못 쓰는 것 3가지**를 분리했다:
  (1) 워크스페이스 형태가 다르다(`web/*` 단독, `packages/` 도 `turbo.json` 도 없음 → `COPY packages/` 레이어 없음, 필터는 `--filter fan-platform-web`),
  (2) web-store 는 `NEXT_PUBLIC_API_URL`·Toss 키를 **빌드 인자로 굽는데** 이 이미지는 아무 호스트명도 굽지 않는다(아래 AC-7),
  (3) `output:'standalone'` 이 여기서는 기본값이 아니다(AC-1).
  그리고 **In Scope 의 판정 항목이 틀렸음을 확인** → `TASK-BE-573` 로 분리(위 § 선행 의존).
- **AC-1 (프로덕션 빌드)** PASS — 컨테이너 PID 1 = `node web/fan-platform-web/server.js`, 자식 = `next-server (v15.5.15)`, `NODE_ENV=production`. `next dev` 아님. 기동 로그 `✓ Ready in 310ms`.
- **AC-2 (Traefik 도달)** PASS — `DEMO_DOMAIN=local` 기동에서 `GET http://web.fan-platform.local/login` = **200**(실제 로그인 화면 마크업 확인), `GET /` = **307 → `/login?from=%2F`**. 호스트명은 `web.fan-platform.<DEMO_DOMAIN>` — 이유는 아래 § 호스트명 결정.
- **AC-3 (로그인 성립)** PASS — **14/14 체크.** 아래 § 라이브 실측.
- **AC-4 (헬스체크)** PASS — `wget http://127.0.0.1:3000/login`(`localhost` 아님). 컨테이너 healthy, Traefik 이 라우팅.
- **AC-5 (래퍼 가드)** PASS — `verify-demo-wrapper.sh` 정적 20가드 전량. **가드 (i) 가 무는 것도 확인**: alias 를 지우고 재실행하면 `FAIL: Traefik alias 가 없는 라우터 호스트명: web.fan-platform.local` 로 정확히 이 호스트를 지목한다.
- **AC-6 (메모리 실측)** — **45.0 MiB / 이미지 309 MB**. 아래 § 메모리.
- **AC-7 (`.local` 하드코딩 0건)** PASS(단서 있음) — 이 서비스가 만드는 모든 호스트명(`NEXTAUTH_URL`, Traefik 라우터)은 `${DEMO_DOMAIN:-local}` 를 통과한다. **예외는 `OIDC_ISSUER_URL: ${OIDC_ISSUER_URL:-http://iam.local}` 하나인데, 이건 같은 파일의 기존 5개 서비스와 글자 그대로 같은 형태**이고 데모에서는 `demo.env` 가 `http://iam.${DEMO_DOMAIN}` 로 덮는다. 여기만 다르게 쓰면 형제 파리티가 깨지므로 유지했다.

## 호스트명 결정 — `web.fan-platform.<DEMO_DOMAIN>`

게이트웨이가 이미 `Host(fan-platform.<DEMO_DOMAIN>)` 를 통째로 점유한다. 같은 호스트에 웹을 얹으면
경로 우선순위로 갈라야 하는데, **이 앱은 `/api/auth/*` 를 자기 라우트로 쓰고 게이트웨이도 `/api/**` 를
쓴다** — 경계가 서로 얽힌다. `web.ecommerce.<DEMO_DOMAIN>` 과 같은 형태로 분리하는 것이 형제 선례이고,
라우터가 하나뿐이라 404 진단도 단순하다(Failure Scenarios 의 "healthy 인데 404" 가 정확히 라우터 미매치 케이스다).

## `output:'standalone'` 을 옵트인으로 둔 이유 — 실측으로 확정

기존 `next.config.ts` 주석은 "Windows 심볼릭 링크 권한 때문에 standalone 을 뺐다"고 적어 뒀다.
**물려받지 않고 다시 쟀다** (같은 워크트리, 같은 호스트):

```
pnpm --filter fan-platform-web build                        → rc=0  (standalone 디렉터리 없음)
NEXT_OUTPUT_STANDALONE=1 pnpm --filter fan-platform-web build → rc=1
  ⚠ Failed to copy traced files …
    [Error: EPERM: operation not permitted, symlink
     '…/node_modules/.pnpm/react@19.2.5/node_modules/react' -> '…/.next/standalone/…/react']
```

주장은 **오늘도 참이다.** 그래서 무조건 켜면 컨테이너 이득을 로컬 빌드 회귀와 맞바꾸게 된다.
컨테이너 빌드는 Linux 라 제약이 없으므로 Dockerfile 에서만 `NEXT_OUTPUT_STANDALONE=1` 를 켠다.

## 라이브 실측 (로컬, `DEMO_DOMAIN=local`, 15 컨테이너)

구성: traefik + iam(mysql/redis/kafka/kafka-init/auth-service, `iam.local` → auth-service 직행) +
fan(postgres/redis/kafka/community/artist/membership/notification/gateway/**web**).
`demo@demo.com` / `Demo1234!` (`TASK-BE-571` 시드).

> ⚠ `web.fan-platform.local` 은 Windows hosts 파일에 없고 이 셸은 관리자가 아니라 추가할 수 없다.
> 그래서 브라우저 대신 **실 HTTP 클라이언트가 127.0.0.1:80 에 붙고 `Host:` 헤더를 직접 세운다** —
> Traefik 이 라우팅에 쓰는 것이 바로 그 헤더이므로 라우팅·SAS redirect_uri 검증·쿠키 경로는
> 브라우저와 동일하게 통과한다. **브라우저 렌더링 자체는 이 방법으로 증명되지 않는다**(솔직히 기록).

```
PASS  anonymous / is gated and redirects to the login route      307 -> /login?from=%2F
PASS  POST /api/auth/signin/iam redirects to IAM authorize       302 -> iam.local/oauth2/authorize
PASS  the redirect_uri next-auth asks for is the new demo host
      redirect_uri=http://web.fan-platform.local/api/auth/callback/iam
PASS  IAM accepts demo@demo.com on the fan-platform client       302 (no error)
PASS  the OIDC round trip lands back on the fan web
      iam.local/oauth2/authorize -> 302
        -> web.fan-platform.local/api/auth/callback/iam 302
        -> web.fan-platform.local/ 200
PASS  session cookie set, NOT __Secure- prefixed on http         authjs.session-token
PASS  session carries the tenant                                 tenantId=fan-platform roles=["FAN"]
PASS  SSR 피드 (/)            200  11,180 bytes
PASS  SSR 아티스트 (/artists)  200  21,370 bytes
PASS  SSR 멤버십 (/membership) 200  13,274 bytes
PASS  SSR 알림 (/notifications)200  11,768 bytes
PASS  SSR 내 정보 (/me)        200  11,775 bytes
PASS  SSR /:        gateway fetch SUCCEEDED (error branch not taken)
PASS  SSR /artists: gateway fetch SUCCEEDED (errorBranch=false, emptyBranch=false)
→ ALL 14 CHECKS PASS
```

**"200 이면 됐다" 로 끝내지 않은 부분** — `FAN-FE-008` 의 회귀(로그인은 되는데 SSR fetch 가 bearer 없이
나가 전 페이지 401 → 에러 상태)는 **200 만으로는 구분되지 않는다.** 그래서 마지막 두 줄을 따로 뒀다:
`/artists` 는 fetch 실패 시 `ErrorState("디렉토리를 불러올 수 없습니다")`, 성공+0건이면
`EmptyState("아티스트를 찾을 수 없습니다")` 로 **다른 문자열**을 낸다. 실측은 **둘 다 부재** —
즉 fetch 가 200 을 받고 `content.length > 0` 분기로 갔다는 뜻이다. DB 확인: `artists` 6행.
**아티스트 6명이 SSR 로 실제 렌더됐다.** (마커 문자열은 컴포넌트에서 복사했고 추측하지 않았다.)

## 메모리 (AC-6)

`docker stats --no-stream`, 로그인 + 5화면 SSR 직후:

```
fan-platform-web        45.04 MiB / 11.68 GiB   0.38%
(참고) fan-platform-gateway  305.5 MiB   iam-auth-service  912.4 MiB
이미지 크기: 309 MB
```

⚠ **단일 표본이고 시드 데이터가 없는 상태의 값**이다. 상한을 web-store 와 같은 256M 으로 잡았다
(실측 대비 5배 여유). `TASK-MONO-399` AC-2 재측정 입력으로는 "팬 웹 추가분 ≈ 50 MiB 급" 으로 쓸 것.

## redirect_uri 판정 (In Scope 의 판정 항목)

**필요하다** — `TASK-BE-573` 로 분리 기재 후 같은 PR 에서 구현(`V0031`). 근거와 실패 모드는 위
§ 선행 의존. **네거티브 대조까지 실측**:

```
authorize(redirect_uri=http://web.fan-platform.local/...)  → 302   ← 등록됨
authorize(redirect_uri=http://nope.fan-platform.local/...) → 401   ← 미등록, 원인 안 알려주는 그 401
```

## 🔴 구현 중 발견한 것 (미수정, 티켓 후보)

1. **Flyway 는 SQL 주석 안의 `달러-중괄호` 도 플레이스홀더로 치환한다.** V0031 의 헤더 주석에
   `web.fan-platform.<달러><중괄호>DEMO_DOMAIN...` 을 적었더니 마이그레이션 **파싱이 실패하고
   auth-service 가 부팅 자체를 못 했다**(`No value provided for placeholder`). 더 고약한 건
   **고치면서 그 패턴을 설명하려고 다시 써서 두 번째로 죽었다는 것**이다. 라이브 기동이 아니었으면
   Testcontainers IT 단계까지 갔을 결함이다. → V0031 헤더에 경고를 박아 뒀다. 저장소 전체에
   같은 지뢰가 있는지는 확인하지 않았다(별도 스윕 후보).
2. **`demo.env` 의 제네릭 `CORS_ALLOWED_ORIGINS` 가 팬 게이트웨이의 기본값을 덮는다.**
   `CORS_ALLOWED_ORIGINS=http://ecommerce.${DEMO_DOMAIN},...` 이 셸 env 로 주입되므로 팬
   게이트웨이의 `${CORS_ALLOWED_ORIGINS:-http://fan-platform.local,http://localhost:3000}` 이
   **ecommerce 오리진으로 바뀐다.** 이 앱은 브라우저에서 게이트웨이를 직접 호출하지 않아 지금은
   무해하지만(모든 fetch 가 server-only), 팬 웹에 클라이언트 fetch 가 하나라도 생기면 CORS 로 깨진다.
   프로젝트 간 env 이름 충돌 — 데모 전역 파일의 구조적 문제라 이 태스크에서 고치지 않았다.
3. **세션의 `user.email` 이 `undefined`** 다(`tenantId`/`roles` 는 정상). 헤더에 표시 이름이 안 뜬다는
   뜻이라 면접 데모 체감에 영향이 있을 수 있다. id_token 의 `email` 클레임 또는 `session` 콜백 쪽
   문제로 보이나 이 태스크 범위(배포 아티팩트) 밖이라 손대지 않았다.

## 범위 밖으로 남긴 것

- **팬 데모 데이터 시드** — `TASK-MONO-506` 소유. 그래서 위 피드는 비어 있고 아티스트만 6행(서비스 자체 시드)이다.
- **브라우저 실렌더 확인** — hosts 파일에 `web.fan-platform.local` 추가가 필요하고 이 셸은 관리자가 아니다.
  `scripts/dev-setup.ps1` 에 이 호스트명을 넣는 것은 별도 판단(현재 `web.ecommerce.local` 도 hosts 에 없다 —
  형제도 같은 상태이므로 이 태스크에서 일방적으로 바꾸지 않았다).
- **AWS 도달** — AMI 재굽기 전까지 도달하지 않는다(`MONO-399` AC-6 / `MONO-477` AC-7·8 에 병합됨).
