# Task ID

TASK-MONO-394

# Title

`iam-platform` 의 `community-service` · `membership-service` 는 **CI 는 살아 있고 배포는 죽어 있다** — 어느 compose 도 띄우지 않는데 매 플랫폼 스윕이 이들을 건드리고, **테넌트 게이트 정책이 실제 운영본과 반대다**. 삭제할 것인가, 살릴 것인가 (사람 결정)

# Status

ready

# Owner

monorepo

# Task Tags

- decision
- cleanup
- security
- adr-followup

---

# Dependency Markers

- **발굴**: `TASK-MONO-392`(D5-8) 수행 중. [`ADR-MONO-049` § 1.12](../../docs/adr/ADR-MONO-049-framework-neutral-security-library.md) 에 관찰만 기록하고 **고치지 않았다** — 행동 변경이고, 추출은 행동을 바꾸지 않기 때문이다.
- **선행 (머지됨)**: `TASK-MONO-392`(D5 완결, 사본 49 → 0).
- **연관**: `TASK-MONO-179`(`global-account-platform` → `iam-platform` 개명) — 이 서비스들의 출신을 설명한다.
- **후속**: 없음(이 task 가 결정한다).

---

# Goal

**`iam-platform/apps/community-service` 와 `membership-service` 를 어떻게 할지 결정하고 실행한다.**

두 서비스는 **CI 가 빌드·테스트하지만 어떤 compose 도 배포하지 않는다.** 그리고 `fan-platform` 에 **같은 이름의 서비스가 따로 있고 그쪽이 실제로 배포된다.**
비용은 계속 나가고(모든 플랫폼-전역 스윕이 이들을 건드린다), **보안 정책은 운영본과 반대 방향으로 갈라져 있다.**

---

# 실측 (2026-07-14, `origin/main` `480b8f8ae` — 착수 시 **다시 확인할 것**)

## 실측 1 — 배포되지 않는다

| | |
|---|---|
| `projects/iam-platform/docker-compose.yml` 이 띄우는 서비스 | `auth` · `account` · `security` · `admin` · `gateway` — **5개. community/membership 없음.** |
| `projects/fan-platform/docker-compose.yml` | `image: fan-platform/community-service:local` · `image: fan-platform/membership-service:local` — **fan 자기 이미지** |
| iam 게이트웨이 라우트 | `community`/`membership` **0건** |
| console / e2e / 다른 프로젝트 참조 | **0건** |

**이들을 참조하는 곳은 `settings.gradle` 과 `.github/workflows/ci.yml` 뿐이다.** 즉 **빌드·테스트되지만 아무 데서도 실행되지 않는다.**

## 실측 2 — 그런데 살아 있어 *보였다*

`projects/iam-platform/infra/prometheus/prometheus.yml` 이 여전히 이들을 스크레이프한다:

```yaml
- job_name: 'community-service'
  ... targets: ['host.docker.internal:8086']
- job_name: 'membership-service'
  ... targets: ['host.docker.internal:8087']
```

**자기 compose 가 띄우지도 않는 서비스를 감시하는 설정이다.** 선언↔진실 드리프트이며, **이 파일 때문에 처음 조사에서 "배포됨" 으로 오독할 뻔했다.**

## 실측 3 — fan 쪽은 **재패키징된 포크**다 (공유 모듈이 아니다)

| | 패키지 | main java 파일 |
|---|---|---|
| iam | `com.example.community` / `com.example.membership` | 87 / 68 |
| fan | `com.example.fanplatform.community` / `...membership` | 99 / 61 |

**같은 도메인, 다른 패키지, 별개 소스 트리.** `iam-platform` 은 `global-account-platform`(GAP) 의 개명이고(`TASK-MONO-179`), 그 시절 fan 데모를 GAP 안에서 호스팅했다 — **fan-platform 이 독립 프로젝트가 되면서 자기 사본을 가져간 것**으로 보인다. **(이 가설은 `git log --follow` 로 확인할 것.)**

## 실측 4 — 🔴 **보안 정책이 운영본과 반대다**

두 서비스 모두 `tenant_id = fan-platform` 을 핀한다(`OIDC_REQUIRED_TENANT_ID:fan-platform`) — **즉 fan 의 테넌트를 지킨다.** 그런데 게이트 정책이 갈라져 있다:

| | SUPER_ADMIN 와일드카드 (`tenant_id="*"`) | entitlement |
|---|---|---|
| **`fan-platform/community`** (실제 배포됨) | **✅ 허용** | ✗ |
| **`iam-platform/community`** (배포 안 됨) | **❌ 거부** | ✗ |

**같은 테넌트를 지키는 두 게이트가 SUPER_ADMIN 토큰에 반대로 답한다.** `ADR-MONO-049` § 1.7 의 표는 두 프로젝트를 **각각 적었을 뿐 연결하지 않았다.**

**현재 라이브 결함은 아니다** — iam 쪽이 안 뜨니까. **문제는 이것이 언제든 라이브가 될 수 있다는 것이고, 그때 어느 쪽이 옳은지 아무도 모른다는 것이다.**

## 실측 5 — 비용은 실제로 나가고 있다

이 두 서비스는 **모든 플랫폼-전역 스윕에 딸려 왔다**:

- `TASK-BE-454` / `TASK-BE-455` — outbox v2 마이그레이션 (배포 안 되는 서비스의 outbox 를 v2 로 옮겼다)
- `TASK-MONO-392`(D5-8) — 보안 클래스 통합 + 정책 핀 신설 + 슬라이스/통합 테스트 위임 배선

**즉 "그냥 두면 공짜" 가 아니다.** 매 스윕마다 사람과 CI 시간을 쓰고, **스윕을 하는 사람은 이게 죽은 코드인 줄 모른다.**

---

# Scope

**포함:**

1. **D1 — 출신 확인** (`git log --follow`): fan 쪽이 iam 쪽에서 갈라져 나온 것이 맞는가? 이후 **독립적으로 진화**했는가(단순 사본이 아니라)?
2. **D2 — 결정** (아래 § 결정 지점).
3. 결정에 따른 실행 + `prometheus.yml` 의 stale 스크레이프 정리(어느 결정이든 **이건 무조건 고친다** — 지금 상태는 거짓말이다).

**제외:**

- **`fan-platform` 쪽 서비스는 건드리지 않는다.** 그쪽이 운영본이다.
- **fan 의 와일드카드 정책 변경 금지** — 그건 별개 판단이고 이 task 의 질문이 아니다.

---

# 🔴 결정 지점 (사람)

## (A) 삭제한다 — **기본 권고**

`settings.gradle` 에서 두 모듈 제거 + 소스 트리 삭제 + `ci.yml` 정리 + `prometheus.yml` 스크레이프 제거.

- **근거**: 배포 0, 라우트 0, 참조 0. **스윕 비용만 나간다.** 그리고 **반대 방향으로 갈라진 보안 정책을 들고 있는 죽은 코드는 그냥 죽은 코드보다 나쁘다** — 언젠가 누가 되살리면 무엇이 옳은지 알 수 없다.
- **위험**: 되살릴 일이 정말 없는가? DB 마이그레이션·outbox 스키마가 다른 서비스와 얽혀 있지 않은지 확인 필요.

## (B) 유지하되 정책을 fan 과 정렬한다

`allowSuperAdminWildcard()` 를 켜서 fan 과 맞춘다.

- **근거**: 언젠가 iam 이 자기 community/membership 을 다시 띄울 계획이 있다면.
- **문제**: **그 계획이 어디에도 없다.** 그리고 이건 **행동 변경**이므로 "왜 iam 만 엄격했는가" 에 답해야 한다 — **답이 "실수" 라면 (A) 가 더 정직하다.**

## (C) 유지하고 정책 발산을 **명문화**한다

"iam 쪽은 의도적으로 더 엄격하다" 를 문서화하고 가드를 붙인다.

- **문제**: **배포되지 않는 서비스의 정책을 명문화하는 것은 아무것도 지키지 않는다.** `TASK-MONO-355`·`387`·`392` 가 반복해서 만난 그 실패 모드다 — *관측할 수 없는 성질은 아무도 지키고 있지 않은 성질이다.*

> **⚠️ 에이전트 단독 결정 금지.** (A) 는 **파괴적**(모듈 삭제)이고, 어느 쪽이든 **아키텍처 판단**이다. **D1 실측을 먼저 제시하고 사람에게 물어라.**

---

# Acceptance Criteria

- **AC-1 (실측 재확인)** — 착수 시 § 실측 1~5 를 **다시 측정**한다. 특히 *"어떤 compose 도 안 띄운다"* 는 **탐지식을 아는 답에 먼저 돌려 자기검증**할 것(같은 술어가 `auth-service` 는 찾아내야 한다 — 빈 결과는 부재가 아니다).
- **AC-2 (D1 출신 확인)** — `git log --follow` 로 fan 쪽이 iam 쪽에서 갈라졌는지, 갈라진 뒤 **독립 진화했는지** 확인한다. **단순 사본이면 (A) 가 쉽고, 독립 진화했으면 (A) 여도 삭제 전에 fan 이 잃는 게 없는지 확인해야 한다.**
- **AC-3 (사람 결정)** — D2 는 **사람이 선택**한다. AC-1·AC-2 결과를 표로 제시하고 물을 것.
- **AC-4 (`prometheus.yml` 은 무조건 고친다)** — 결정과 무관하게, **자기 compose 가 띄우지 않는 서비스를 스크레이프하는 설정은 거짓말이다.** 삭제(A) 든 유지(B/C) 든 이 파일은 진실이 되어야 한다.
- **AC-5 (삭제 선택 시) — 얽힘 확인** — DB 마이그레이션·outbox·Kafka 토픽·계약이 **다른 살아있는 서비스와 공유되지 않는지** 확인. 공유가 있으면 **STOP 하고 보고**.
- **AC-6 (삭제 선택 시) — 빌드가 초록** — `settings.gradle`·`ci.yml`·`docker-compose*`·`prometheus.yml` 에서 참조가 **0** 이고 `./gradlew check` 가 통과한다. **참조 grep 은 `iam-platform` 경로로 한정**할 것(fan 쪽을 잡으면 안 된다).
- **AC-7 (기록)** — `ADR-MONO-049` § 1.12 의 "후속이 먼저 물어야 할 것" 에 **답을 적는다**. 이 task 가 그 질문을 닫는다.

---

# Related Specs

- **`docs/adr/ADR-MONO-049-framework-neutral-security-library.md` § 1.12** — 이 발산을 기록하고 이 task 를 지목한 곳
- `docs/adr/ADR-MONO-049-...` § 1.7 — 6개 프로젝트 정책 표(**두 프로젝트를 각각 적었을 뿐 연결하지 않았다**)
- `projects/iam-platform/apps/{community,membership}-service/` — 대상
- `projects/fan-platform/apps/{community,membership}-service/` — **운영본. 건드리지 말 것.**
- `projects/iam-platform/docker-compose.yml` · `infra/prometheus/prometheus.yml`
- `TASK-MONO-179` — GAP → iam-platform 개명(출신 단서)

# Related Contracts

**없다** — 배포되지 않는 서비스에는 살아있는 계약이 없다. **AC-5 가 그것을 확인한다**(공유 스키마·토픽이 있으면 이 문장이 거짓이 된다).

---

# Edge Cases

- **`fan-platform` 에 같은 이름의 서비스가 있다.** 경로를 확인하지 않고 grep/삭제하면 **운영본을 지운다.** 모든 술어를 `iam-platform` 경로로 한정할 것.
- **`prometheus.yml` 이 살아 있는 척한다** — 이것 때문에 "배포됨" 으로 오독할 뻔했다(실측 2).
- **`TASK-BE-454/455`(outbox v2)가 이 서비스들을 이미 손봤다** — 누군가 "유지 중" 이라고 오해할 근거가 된다. **스윕에 딸려 온 것이지 의도적 유지가 아니다**(그것이 실측 5 의 요점).
- **CI 시간**: 이 두 모듈은 `ci.yml` 의 IAM 백엔드 잡에서 빌드·테스트된다. 삭제하면 그 잡이 빨라진다(부수 효과, 목표 아님).

# Failure Scenarios

- **`fan-platform` 쪽을 건드린다** → **운영 중인 서비스를 깨뜨린다.** Guard: 모든 경로를 `iam-platform` 으로 한정 + AC-6.
- **"죽은 것 같다" 로 삭제한다** → 얽힘(공유 스키마/토픽)을 놓친다. Guard: **AC-5 를 먼저**, 발견 시 STOP.
- **(C) 를 고른다** → **배포되지 않는 서비스의 정책을 문서화하는 것은 아무것도 지키지 않는다.** `MONO-355`·`387`·`392` 가 세 번 만난 실패 모드다.
- **에이전트가 단독으로 (A) 를 실행한다** → 모듈 삭제는 파괴적이고 아키텍처 결정이다. Guard: AC-3.
- **탐지식의 빈 출력을 "배포 안 됨" 으로 읽는다** → 이 저장소에서 **여러 번** 대가를 치른 함정. Guard: AC-1 의 자기검증(같은 술어가 `auth-service` 를 찾아내야 한다).

---

# Provenance

`TASK-MONO-392`(D5-8) 가 iam 의 두 servlet 서비스를 공유 보안 클래스로 옮기던 중 발굴. **정책 핀을 쓰려고 "이 서비스의 테넌트가 뭐지?" 를 물었더니 `fan-platform` 이었고, 그제서야 fan-platform 프로젝트에 같은 이름의 서비스가 있다는 게 눈에 들어왔다.**

**이 발견은 D5 가 반복해서 만난 것과 같은 클래스다.** 사본을 지우는 일 자체는 기계적이었지만, **지우려고 들여다볼 때마다 아무도 보고 있지 않던 것이 나왔다** — 정경 클래스 자신의 결함(§1.9), 인라인 람다(§1.10), 무방비 면제(D5-6), 거부 커버리지 0(D5-7), 자기 사본을 검사하던 테스트(§1.12). **이번엔 아무도 배포하지 않는 서비스 두 개다.**

**그리고 정직하게: 이건 보안 결함이 아니다.** iam 쪽은 안 뜨니까 지금 뚫린 곳은 없다. **문제는 비용과 거짓말이다** — 매 스윕이 죽은 코드를 유지보수하고, `prometheus.yml` 은 있지도 않은 서비스를 감시한다고 말하며, **보안 정책 하나가 운영본과 반대 방향으로 조용히 갈라져 있다.**

분석=Opus 4.8 / 구현 권장=**Opus** (결과가 "모듈 삭제" 일 수 있고 그건 파괴적이다. **D1 실측 → 사람 결정 → 실행** 순서를 지킬 것. 실측만 하고 멈추는 것도 정당한 완료다.)
