# Task ID

TASK-MONO-758

# Title

`ADR-MONO-081` 단계 6 — 재굽기 창에서 **Vercel 콘솔의 세 화면이 실제 데이터로** 뜨는지 라이브 확인

# Status

ready

# Owner

monorepo

# Task Tags

- demo
- verification
- platform-console

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet — 측정 티켓. 🔴 데모 기동·재굽기·`terraform apply` 는 소유자 승인 대상.
>
> ⏳ **DO NOT START — AC-0 이 참이 되기 전에는 착수하지 않는다.**

---

# Dependency Markers

- **선행**: `TASK-MONO-757` 머지. 🔴 그리고 **757 이후 커밋으로 구운 AMI** — 데모 백엔드는 구워진 클론에서 돈다(재굽기 전까지 데모에 없다). 콘솔 자체는 Vercel 이라 302·303 머지 시점에 이미 배포된다.
- **알림**: `TASK-MONO-648`(포트폴리오 캡처, in-progress) — 저하 화면 셋이 실제 화면이 되면 다시 찍을 수 있다(AC-4).

# Goal

`console.hubwang.com` 에서 운영 개요(`/dashboards/overview`) · 도메인 상태(`/dashboards/health`, `/`) · 알림 벨이 `bffUnavailable` / 502 없이 실제 도메인 데이터로 뜨는 것을 결과 상태로 확인한다.

# Scope

## In Scope

- 데모 기동 1회(다른 재굽기 창과 합칠 수 있으면 합친다) · 화면 확인 · 도메인 하나를 내린 대조군

## Out of Scope

- 코드 변경(발견은 새 티켓)

# Acceptance Criteria

- [ ] **AC-0 (게이트)** — 데모 AMI 의 구운 커밋이 757 머지 커밋의 자손이다(`tfvars` 의 AMI → 그 AMI 의 커밋을 **다시 읽는다** — 목록을 물려받지 않는다). 아니면 착수하지 않는다.
- [ ] **AC-1** — 운영자 로그인 뒤 세 화면이 실제 값으로 뜬다 — 화면 글자 + 같은 시각의 console-web 라우트 응답(200, 카드 `status` 값)을 함께 적는다. 🔴 클라이언트 렌더 화면은 SSR HTML 로 판정하지 않는다.
- [ ] **AC-2** — 🔴 대조군: 도메인 하나(예: scm)를 내린 상태에서 운영 개요는 200 이고 그 카드만 열화.
- [ ] **AC-3** — 데모 호스트에 console-bff 컨테이너가 없다(`docker ps`). 🔵 2026-10-02 UTC (757 이 덧붙임): 757 이 `console` 을 데모 **도메인** 목록에서 뺐다(FULL·CORE·COMPOSE·DOWN_ORDER — 그 compose 엔 데모가 띄울 서비스가 하나도 남지 않아 `up -d` 가 `no service selected` 로 실패한다는 것을 실측했다). 그래서 `console` 프로젝트 컨테이너 자체가 **0개**여야 한다. 루트 디스크를 재사용한 경우 옛 `console` 프로젝트 컨테이너가 남아 있으면 이제 아무것도 그것을 내리거나 재지 않는다 — 있으면 적고 수동으로 지운다.
- [ ] **AC-4** — `TASK-MONO-648` 에 «저하 화면 셋이 실제 화면이 됐다 — 다시 찍을 수 있다» 를 알린다(그 티켓이 in-progress 면 소유 세션에 남길 문장).
- [ ] **AC-5** — 🔵 2026-10-02 UTC 고쳐 씀(757): `infra/demo/console-vercel.override.yml` 은 757 이 **파일째** 지웠다(억제할 서비스가 남지 않았다). 대신 확인할 것: 론처의 콘솔 카드가 iam 기동만으로 «준비됨» 이 되는가(`BUNDLES[console]=iam` — `projects.sh` 와 Lambda `handler.py` 둘 다. Lambda 쪽은 **`terraform apply` 가 선행**이다 — 소유자 몫).

# Related Specs

- `docs/adr/ADR-MONO-081-console-composition-in-the-console-server.md` § Verification
- `docs/adr/ADR-MONO-067-demo-surfaces-served-from-vercel.md`

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.9

# Edge Cases

- Vercel 배포가 302·303 이후 커밋인지 먼저 확인한다(머지 + CI 초록 ≠ 배포됨).
- 데모 주소는 부팅마다 바뀐다 — console-web 서버의 백엔드 해석기가 새 주소를 쓰는지(`@demo/backend-resolver`).

# Failure Scenarios

1. 옛 AMI 로 띄워 «안 고쳐졌다» 와 «아직 안 실렸다» 를 구별하지 못한다(AC-0).
2. 화면 HTML 만 보고 판정해, 클라이언트에서 열화로 바뀌는 것을 놓친다.
