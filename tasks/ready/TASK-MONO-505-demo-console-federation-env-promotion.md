# Task ID

TASK-MONO-505

# Title

통합 데모(`infra/demo`)에서 콘솔이 5도메인 전부 `available:true` 로 뜨도록 federation env 를 승격한다

# Status

ready

# Owner

monorepo

# Task Tags

- infra
- demo
- code

---

# 배경

`infra/demo/demo-up.sh` 는 8개 프로젝트를 공유 Traefik 위에 띄우는 **온디맨드 데모의 유일한 기동 경로**이고,
AWS 데모 호스트(`demo-stack.service` → `demo-boot.sh` → `demo-up.sh`)가 그대로 호출한다.
그런데 `infra/demo/README.md` § "남은 작업" 이 스스로 이렇게 적어 두었다:

> 콘솔이 5/5 도메인을 실제 `available:true` 로 렌더하려면 cross-domain OIDC issuer / per-domain
> base URL / seed 등 런타임 federation env 배선이 필요하며, 이는 `tests/federation-hardening-e2e/`
> 데모 오버레이(MONO-170/174)가 이미 6도메인에 대해 해둔 것과 겹친다. 데모 호스트 정식화 시 그
> 오버레이 env 를 이 래퍼의 프로젝트별 `.env` 로 승격/재사용한다(중복 재구현 금지).

즉 **배선은 이미 한 번 만들어졌고, 다른 하네스에만 있다.** 이 태스크는 그 승격이다.

이 배선이 없으면 컨테이너가 전부 healthy 인 채로 콘솔의 도메인 운영 섹션만 비거나 degraded 로
뜬다 — 이 저장소가 반복해서 당한 실패 모드(MONO-358)이고, `docker compose config` 도 healthcheck 도
증명하지 못한다.

---

# Goal

`bash infra/demo/demo-up.sh full` (및 도메인 부분집합 기동)으로 뜬 스택에서, 브라우저로 콘솔에
로그인한 운영자가 **WMS · SCM · Finance · ERP · E-Commerce 다섯 도메인 운영 섹션을 전부 실데이터로**
연다. `DEMO_DOMAIN=local` 과 `DEMO_DOMAIN=<ip>.sslip.io` 양쪽에서 동일하게 성립한다.

---

# Scope

## In Scope

- `infra/demo/demo.env` — 콘솔이 읽는 per-domain base URL / OIDC issuer / JWKS 키 승격
- `infra/demo/projects.sh` — 5도메인 운영에 필요한 게이트웨이(`scm-gateway`, `erp-gateway`)가
  각 프로젝트의 compose 조합에 실제로 포함되는지 확인하고, 빠졌으면 `COMPOSE[slug]` 보강
- `infra/demo/verify-demo-wrapper.sh` — "콘솔이 읽는 도메인 키가 `demo.env` 에 전부 있는가" 가드 추가
  (기존 가드 (g) "미설정 compose 변수 0건" 의 확장이며, 별개 술어다: (g)는 *compose 가 참조하는*
  변수를 보고, 이 가드는 *콘솔 코드가 읽는* 키를 본다)
- `infra/demo/README.md` — § "남은 작업" 갱신

## Out of Scope

- `tests/federation-hardening-e2e/**` 수정 — CI federation 게이트가 소유한다. **바이트 불변**으로 둔다
- 새 도메인 추가 / 콘솔 신규 화면
- 데모 계정·도메인 데이터 시드 — `TASK-MONO-506` / `TASK-BE-571`(iam) 소유
- AWS 재굽기 — `TASK-MONO-399` AC-6 / `TASK-MONO-477` AC-8

---

# Acceptance Criteria

- [ ] **AC-0 (착수 = 재측정)** — 문서 목록을 신뢰하지 않는다. (a) fed-e2e 데모 오버레이가 `console-web` /
      `console-bff` 에 실제로 주입하는 env 키를 `docker compose config` 렌더로 **전수 열거**하고,
      (b) 콘솔 코드가 실제로 읽는 키를 소스에서 **독립적으로 열거**한 뒤, (c) 두 집합의 차집합을
      태스크에 기록한다. 두 목록이 어긋나면 코드가 이긴다
- [ ] **AC-1** — `demo-up.sh full` + `DEMO_DOMAIN=local` 기동 후 **브라우저**로 콘솔 로그인 →
      `/dashboards/overview` 의 도메인 상태 5개가 전부 `available:true`. 직접 토큰 스모크로 대체 금지
      (직접 토큰은 실 클라이언트 인증 경로를 증명하지 않는다)
- [ ] **AC-2** — 다섯 도메인 운영 섹션(`/wms` `/scm` `/finance` `/erp` `/ecommerce`) 각각의 개요가
      degraded 배너 없이 렌더된다. 데이터가 비어 있는 것은 허용(시드는 MONO-506 소유), **오류/degraded 는 불가**
- [ ] **AC-3** — `scm-gateway` · `erp-gateway` 가 통합 데모 기동 경로에 포함된다. 포함이 불가능하면
      그 사유와 대안(직접 서비스 호출 배선)을 태스크에 기록하고 AC-2 를 그 형태로 충족한다
- [ ] **AC-4 (가드가 무는가)** — 추가한 가드를 **네거티브 테스트**한다: `demo.env` 에서 도메인 base URL
      키 하나를 지우면 `verify-demo-wrapper.sh` 가 exit≠0 이고 **그 키 이름을 지목**한다. 되돌린 뒤 통과 확인
- [ ] **AC-5 (하드코딩 `.local` 0건)** — 승격한 값 중 `DEMO_DOMAIN` 을 우회해 `.local` 을 박은 것이 없다.
      `DEMO_DOMAIN=<ip>.sslip.io` 로 기동해도 AC-1 이 성립한다(로컬에서는 hosts 엔트리로 대체 검증 가능)
- [ ] **AC-6** — `bash infra/demo/verify-demo-wrapper.sh` 전체가 통과하고, CI `demo-wrapper-smoke` 잡이 green

---

# Related Specs

> **Before reading Related Specs**: `platform/entrypoint.md` Step 0 — 이 태스크는 monorepo-level 이므로
> 대상 프로젝트 `PROJECT.md` 대신 `CLAUDE.md` § Required Workflow 의 monorepo-level 경로를 따른다.

- `infra/demo/README.md` — 래퍼 불변식 (a)~(u)
- `infra/demo/projects.sh` — 프로젝트 맵 단일 출처
- `docs/guides/console-fullstack-local-dev.md` — fed-e2e 오버레이가 무엇을 배선했는지의 human 기록
  (AI 소스 오브 트루스 아님 — 실제 값은 compose 렌더로 확인할 것)
- `platform/architecture-decision-rule.md`

# Related Skills

- `.claude/skills/INDEX.md` — devops / 통합 데모 관련 항목

---

# Related Contracts

- 없음 (런타임 환경 배선만 — API·이벤트 계약 불변)

---

# Implementation Notes

- **중복 재구현 금지.** fed-e2e 오버레이가 이미 푼 문제다. 값을 옮기되, 옮긴 값이 **왜 그 값인지**를
  주석으로 남긴다(컨테이너 DNS vs 브라우저 도달 가능 호스트의 구분이 핵심).
- **Traefik network alias (가드 (i))** — 콘솔은 토큰 교환을 서버사이드로 한다. AWS 는 인스턴스가 자기
  공인 IP 로 보내는 트래픽을 되돌려주지 않으므로(hairpin 부재), 컨테이너 안에서 `iam.<ip>.sslip.io` 로
  나가면 죽는다. alias 가 같은 이름을 Docker 임베디드 DNS 로 해소해야 한다.
- **게이트웨이 JVM DNS 캐시** — admin/auth 컨테이너를 재생성하면 IP 가 바뀌고 게이트웨이가 stale 캐시로
  503 을 낸다. 재생성 시 게이트웨이도 재시작한다.
- **검증 연타 금지** — 콘솔의 operator 교환은 5s 타임아웃이라 스택 부하 시 false `unavailable` 이 뜬다.
  기동 후 idle 안정화 뒤 1회 확인한다.
- 로컬 호스트(WSL 12GB)에서 `full` 41 JVM 동시 기동은 불가능하다. AC-1/AC-2 는 **iam + console +
  대상 도메인** 슬라이스 조합으로 나누어 충족하고, 5도메인 동시 성립은 `TASK-MONO-399`/`477` 의
  EC2 실주행에서 최종 확인한다. 슬라이스로 나눈 사실과 각 슬라이스 실측 메모리를 태스크에 기록한다.

---

# Edge Cases

- 콘솔은 뜨는데 특정 도메인만 blank — 그 도메인 게이트웨이 컨테이너 부재(NXDOMAIN)가 최빈 원인
- `DEMO_DOMAIN` 이 빈 문자열이면 Traefik 이 `Host(\`console.\`)` 라우터를 **거부하지 않고** 만들어
  아무 요청과도 매치하지 않는다 → 에러 0건, 전부 healthy, 404
- non-healthy 컨테이너는 Traefik 이 조용히 스킵한다 → 라우터는 있는데 502/404
- 도메인 부분집합 기동(`demo-up.sh console`)에서는 미기동 도메인이 degraded 로 보이는 것이 **정상**이다.
  AC-2 는 해당 도메인이 함께 떠 있을 때만 적용된다

---

# Failure Scenarios

- **컨테이너 전부 healthy 인데 로그인만 안 된다** — iam Traefik 오버레이 / 데모 도메인 시드 /
  network alias 셋 중 하나가 빠진 경우. 가드 (i)(j)(k)(l) 이 각각을 지킨다
- **승격한 env 가 fed-e2e 와 미묘하게 다른 값** — 컨테이너 DNS 이름을 그대로 옮기면 통합 데모의
  프로젝트-분리 네트워크에서 해소되지 않을 수 있다. AC-0 의 렌더 diff 가 이것을 잡는다
- **가드를 추가했는데 물지 않는다** — 술어가 대리지표(파일 존재 여부 등)로 후퇴한 경우.
  AC-4 네거티브 테스트가 유일한 증거다
- **로컬에서 초록인데 EC2 에서 실패** — 로컬은 hairpin 이 되고 AWS 는 안 된다. AC-5 + 가드 (i)

---

# Test Requirements

- `infra/demo/verify-demo-wrapper.sh` 정적 가드 전체 통과 + 신규 가드 네거티브 테스트
- 브라우저 실주행(콘솔 로그인 → 5섹션) — 슬라이스 단위 허용, 슬라이스 분할 사실 기록
- CI `demo-wrapper-smoke` green

---

# Definition of Done

- [ ] 구현 완료
- [ ] 가드 추가 + 네거티브 테스트로 무는 것 확인
- [ ] 브라우저 실주행 증거 기록(슬라이스별)
- [ ] `infra/demo/README.md` § 남은 작업 갱신
- [ ] Ready for review
