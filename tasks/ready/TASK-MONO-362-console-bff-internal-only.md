# Task ID

TASK-MONO-362

# Title

`console-bff` 를 실제로 internal-only 로 만든다 — 주석이 아니라 **배선**으로. 공유 Traefik 엣지에서 라우터를 제거하고, MONO-360 가드의 예외를 **정책에 써넣는 대신 삭제**한다

# Status

ready

# Owner

monorepo

# Task Tags

- infra
- security
- architecture

---

# Goal

`platform/api-gateway-policy.md` 는 예외 없이 선언한다:

- L13 — "All external traffic **MUST** pass through the gateway."
- L14 — "**No** backend service may be directly exposed to external traffic."

그런데 `console-bff` 는 **공유 Traefik 엣지에 라우터를 보유한다**:

```yaml
- "traefik.http.routers.console-bff.rule=Host(`console-bff.local`)"   # projects/platform-console/docker-compose.yml:103
```

**8개 프로젝트 중 엣지 라우터를 가진 백엔드 서비스는 이것 하나뿐**이다(다른 곳에서 라우터를 갖는 것은 게이트웨이 · 프런트엔드 앱 · 운영 툴링뿐). 즉 정책 L14 가 금지한 바로 그 모양이다.

## 그런데 코드가 스스로 "internal-only" 라고 두 번 말한다

> *"**Internal-only** (console-web server-side calls only — browser never reaches this hostname directly)."* — `docker-compose.yml:79`
> *"The browser **NEVER** reaches `console-bff` directly."* — `console-web/src/app/api/console/dashboards/domain-health/route.ts:12`

**즉 의도는 internal-only 인데, 그것을 강제하는 것이 아무것도 없다 — 주석뿐이다.** 엣지 라우터가 있는 한 `Host: console-bff.local` 헤더를 붙일 수 있는 누구든 BFF 에 도달한다.

## 그리고 어제, 가드가 이 위반을 **승인**해 버렸다

`TASK-MONO-360` 이 게이트웨이 드리프트 가드를 도입하면서 platform-console 을 I2 검사에서 **범위 밖으로** 두고 그 근거를 이렇게 적었다:

> *"`console-bff` deliberately holds `console-bff.local`. … calling a structure **the policy explicitly permits** a 'violation' is how a guard loses trust."* — `scripts/check-gateway-drift.sh:73-76`

**정책은 그것을 명시적으로 허용한 적이 없다.** 예외 조항은 어느 문서에도 없다(ADR-MONO-013 은 Model B 를 정할 뿐이고, ADR-MONO-017 은 `console-bff.local` 라벨을 *후속 task 목록*에 적어둘 뿐 정책 예외로 **결정하지 않는다**). 이제 **실행 가능한 CI 가드가 정책 문서가 금지한 것을 축복**하고 있고, 정책만 읽는 사람은 여전히 절대 규칙을 믿는다 — MONO-347/354 와 **동일한 거짓-문서 클래스**이며, 이번엔 그 거짓이 *실행되는 아티팩트*에 들어갔다.

## 해결: 정책을 완화하는 게 아니라 **노출을 없앤다**

브라우저가 BFF 를 부르지 않으므로 **엣지 라우트가 애초에 필요 없다.** 그리고 이건 새 설계가 아니라 **이미 검증된 배선으로의 수렴**이다 — **fed-e2e 스택(CI 가 돌리는 권위 있는 풀 데모)이 이미 정확히 그렇게 한다**:

```yaml
CONSOLE_BFF_URL: http://console-bff:8080    # tests/federation-hardening-e2e/docker/docker-compose.federation-e2e.yml:725
                                            # (그 스택의 console-bff 에는 Traefik 라벨이 없다)
```

`console-bff.local` 라우트는 **thin 프로젝트 compose(`pnpm console:up`)에만** 남아 있다. 이 task 는 그 하나를 fed-e2e 와 같은 모양으로 맞춘다.

**얻는 것**:

1. 정책 L14 를 **문서 한 줄 안 고치고** 준수 → **예외 조항 불필요 → ADR 불필요** (= 이 변경은 아키텍처 결정이 아니라 이미 문서화된 의도의 **집행**이다).
2. `console-bff` 가 다른 모든 백엔드 서비스와 **같은 모양**이 된다.
3. **MONO-360 가드의 예외가 사라진다** — platform-console 을 I2 검사 **범위 안으로** 넣을 수 있고, 그래도 통과한다(유일한 라우터 보유자 `console-web` 은 이미 allowlist). **가드가 느슨해지는 게 아니라 엄격해진다.**

**과대평가 금지**: `console-bff` 는 유효한 operator JWT 를 요구하므로 **열린 문이 아니다**. 관측된 침해도 0 건이다. 결손은 **구조적**이다 — 게이트웨이가 없으니 rate limiting · 신원헤더 strip→enrich · 통일 에러 envelope 이 없고, "internal-only" 가 **강제되지 않는다**. 온디맨드 AWS 데모가 스택을 공개 노출하려 하는 만큼 지금 줄여두는 값어치가 있다.

---

# Scope

## In Scope

- `projects/platform-console/docker-compose.yml`
  - `console-bff` 의 **Traefik 라우터 라벨 제거**(`traefik.enable` / `routers.*` / `services.*.loadbalancer`).
  - **`networks: traefik-net` 은 유지** — console-web 과 **같은 네트워크에 있어야 도달**한다. **네트워크 참여 ≠ 노출**이다. 노출은 *라우터 라벨*에서 온다. 라벨이 없으면 Traefik 은 이 컨테이너로 가는 경로를 **모른다**.
  - `console-web` 에 `CONSOLE_BFF_URL: http://console-bff:8080` **명시** (fed-e2e 와 동일 값).
- `console-web` 의 BFF URL 기본값 4곳 (`api/console/**/route.ts`) — `http://console-bff.local` → `http://console-bff:8080`. **제거된 엣지 라우트를 가리키는 기본값을 남겨두면 조용히 실패한다.**
- `scripts/check-gateway-drift.sh` — platform-console 을 **I2 범위 안으로** 편입 + 거짓 주석(*"the policy explicitly permits"*) 정정.
- 문서 정합: `TEMPLATE.md`(hostname 표 · hosts 한 줄 · 매트릭스) · `scripts/dev-setup.{sh,ps1}`(hosts 배열) · `projects/platform-console/docs/onboarding/local-dev.md` · `specs/services/console-bff/architecture.md` · `specs/contracts/console-integration-contract.md`.

## Out of Scope

- **`console-bff` 앞에 게이트웨이 신설** — 트래픽이 내부(SSR→BFF)뿐이라 홉을 하나 더 얹는 것은 과하다. 필요해지는 시점은 **BFF 가 외부 표면을 갖게 될 때**이고, 그때는 이 task 가 아니라 새 결정이다.
- **`api-gateway-policy.md` 수정** — 이 task 의 요점이 바로 **정책을 고칠 필요가 없다**는 것이다. 코드가 정책을 따라가면 된다.
- `console-web` 의 Traefik 라우터(`console.local`) — 프런트엔드 앱은 **외부 클라이언트 그 자체**이지 "직접 노출된 백엔드" 가 아니다(정책 L14 의 대상 아님. MONO-360 의 allowlist 근거와 동일).
- fed-e2e / demo 오버레이 — **이미 내부망을 쓴다**(변경 불필요).

---

# Acceptance Criteria

- [ ] **AC-1** `projects/platform-console/docker-compose.yml` 의 `console-bff` 에 **Traefik 라우터 라벨 0건**. `docker compose config` 렌더 성공.
- [ ] **AC-2 (도달성 보존)** `console-web` → `console-bff` 가 **내부망으로 도달**한다(`CONSOLE_BFF_URL=http://console-bff:8080`, 둘 다 `traefik-net` 참여). thin 스택(`pnpm console:up`) 기동 후 콘솔 대시보드가 degrade 되지 않는지 확인.
- [ ] **AC-3 (엣지 노출 부재)** `console-bff.local` 이 **더 이상 라우팅되지 않는다** — Traefik 라우터 부재. `console-bff.local` 은 hosts/문서에서도 제거.
- [ ] **AC-4 (가드가 엄격해진다)** `check-gateway-drift.sh` 의 I2 가 **platform-console 도 검사**하고 **통과**한다(라우터 보유자는 allowlist 된 `console-web` 뿐). **mutation 필수**: `console-bff` 의 라우터 라벨을 되돌리면 가드가 **FAIL** 해야 한다 — 통과만으로는 가드가 무는지 알 수 없다.
- [ ] **AC-5** 가드의 *"the policy explicitly permits"* 주석이 **사실로 정정**된다(정책은 허용한 적이 없다 — 거짓 진술을 실행되는 아티팩트에 남기지 않는다).
- [ ] **AC-6** BFF URL 기본값이 **엣지가 아닌 내부망**을 가리킨다(4개 route.ts). 제거된 호스트명을 가리키는 잔존 기본값 0건.
- [ ] **AC-7** 문서 정합 — `TEMPLATE.md` hostname 표 · dev-setup hosts 배열 · local-dev.md · architecture.md · console-integration-contract.md 에서 `console-bff.local` 이 **엔트리 호스트명으로 선언되지 않는다**.

---

# Related Specs

- `platform/api-gateway-policy.md` (**정책 원문** — L13/L14)
- `tasks/done/TASK-MONO-360-gateway-declaration-drift-guard.md` (가드 + 그 예외)
- `tasks/done/TASK-MONO-347-*.md` / `TASK-MONO-357-*.md` (finance/erp 는 direction A = 게이트웨이 신설로 해소. console 은 **노출 제거**로 해소 — 표면이 다르므로 처방도 다르다)
- `docs/adr/ADR-MONO-013` (Model B — 게이트웨이 부재의 근거) · `ADR-MONO-017` (console-bff 아키텍처)
- `docs/adr/ADR-MONO-001` (Local Network Convention — hostname routing)
- `tests/federation-hardening-e2e/docker/docker-compose.federation-e2e.yml:725` (**이미 내부망을 쓰는 선례**)

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` (§ edge routing)

---

# Target Service

`platform-console` (`console-bff` · `console-web`) + 공유 `scripts/` · `TEMPLATE.md`

---

# Implementation Notes

- **네트워크 참여 ≠ 노출.** `console-bff` 는 `traefik-net` 에 남아야 한다(console-web 과의 공유 네트워크). Traefik 이 **라우터 라벨이 없는 컨테이너로는 경로를 만들지 않는다** — 그것이 노출을 없애는 메커니즘이다.
- **호스트 사이드 `next dev` 는 문서화된 워크플로가 아니다** — `local-dev.md` 는 `pnpm console:up`(docker) 만 규정한다. 라우트를 지우면 호스트에서 BFF 로 직접 가는 경로가 사라지지만, **문서화된 것을 깨지 않는다**. (그 워크플로가 필요해지면 host-port 발행이 아니라 별도 논의.)
- **fed-e2e 는 손대지 않는다** — 이미 `http://console-bff:8080`.

---

# Edge Cases

- **기본값을 안 고치고 라우트만 지우면 조용히 깨진다** — `process.env.CONSOLE_BFF_URL || 'http://console-bff.local'` 가 4곳에 있고, compose 가 env 를 주지 않으면 그 기본값이 **제거된 라우트**를 가리킨다. compose env 명시 + 기본값 교체를 **둘 다** 한다.
- **공유 `traefik-net` 의 service-name alias** — `console-bff` 라는 서비스명을 가진 프로젝트는 platform-console 하나뿐이라 충돌 없음. (백킹 서비스(redis/postgres)는 traefik-net 에 참여하지 않으므로 7중 `redis` 는 무관.)
- **가드를 넓히다 다른 프로젝트를 오탐**시키지 말 것 — I2 를 전 프로젝트로 넓히면 `web-store`·`kafka-ui`·`grafana` 가 대상에 들어온다. 이미 allowlist 에 있으므로 통과해야 한다. **넓힌 뒤 반드시 전체 실행으로 오탐 0 확인**(첫날 RED 인 가드는 꺼진다 — MONO-360 이 스스로 적어둔 규율).

---

# Failure Scenarios

- **F1 — 콘솔 대시보드 전면 degrade**: BFF 도달 실패. 완화: AC-2 실기동 확인(fed-e2e 가 같은 배선으로 이미 도는 것이 1차 근거).
- **F2 — 가드를 넓혔는데 오탐**: 다른 프로젝트의 라우터 보유 서비스가 allowlist 에 없음. 완화: AC-4 전체 실행.
- **F3 — 문서만 고치고 코드를 안 고침**(또는 반대) → 다시 3자 불일치. 완화: AC-1/AC-6/AC-7 을 함께 검증.

---

# Test Requirements

- `bash scripts/check-gateway-drift.sh` GREEN(오탐 0) + **mutation**: 라우터 라벨 복원 → FAIL.
- `docker compose --project-directory projects/platform-console config -q` 렌더.
- `bash infra/demo/verify-demo-wrapper.sh` (compose 불변식 — 미설정 변수 · 호스트포트 · 이미지 실재).
- console-web: `tsc` + `lint` + `vitest`(BFF URL 기본값 변경이 테스트를 깨지 않는지).
- **AC-2 실기동**: thin 스택 기동 → 콘솔 대시보드가 BFF 에 도달.

---

# Definition of Done

- [ ] AC-1 ~ AC-7 충족, mutation 확인.
- [ ] `tasks/INDEX.md` done entry.

---

# Provenance

발굴 2026-07-12 — MONO-350 종결 후 큐가 빈 상태에서 "정책이 여전히 거짓말을 하는 곳이 남았나" 를 점검하다 발견. 처음에는 **"정책에 예외 조항을 넣을지" 를 사람 결정으로 넘기려 했으나**, 조사해 보니 **그 프레이밍 자체가 틀렸다** — 브라우저가 BFF 를 부르지 않으므로 엣지 라우트가 애초에 불필요하고, **fed-e2e 는 이미 내부망을 쓴다**. 즉 정책을 완화할 이유가 없고, 코드가 이미 선언한 의도(internal-only)를 **집행**하면 된다. 사용자가 이 방향(엣지 노출 제거)을 선택했다.

분석=Opus 4.8 / 구현 권장=Sonnet (compose 라벨 제거 + URL 전환 + 가드 범위 확대; 판단은 종료).
