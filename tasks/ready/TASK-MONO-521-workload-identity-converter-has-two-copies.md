# Task ID

TASK-MONO-521

# Title

`WorkloadIdentityAuthoritiesConverter` 가 두 벌이 됐다 — 승격 후보이되, **승격 자체가 결정**이라 먼저 재야 한다

# Status

ready

# Owner

monorepo

# Task Tags

- shared-library
- security

---

# 배경

`TASK-FAN-BE-045`(PR #3270) 가 artist-service 에 `/internal/**` 워크로드 체인을 신설하면서
membership-service 의 `WorkloadIdentityAuthoritiesConverter` 와 **구조적으로 동일한** 클래스를
두 번째로 만들었다. 그때 승격하지 않은 이유는 `libs/` 가 monorepo-level 공유 경로라 **root
태스크가 필요**했기 때문이고(CLAUDE.md § Task Rules), 그 티켓 범위에서 몰래 하지 않고
클래스 주석에 후보라고 이름만 남겼다. 이 티켓이 그 이름을 받는다.

## 🔴 "두 벌이니 승격" 은 결론이 아니라 가설이다

이 저장소의 규칙은 *"1곳뿐인 규칙은 없는 규칙"* 이지만 **중복이 곧 병은 아니다**. 승격을
정당화하려면 두 사본이 **같은 것을 하기 때문에** 같은 것이지, 우연히 닮은 것이 아님을 보여야
한다. 실제로 둘은 한 축에서 **다르다**:

| | membership-service | artist-service |
|---|---|---|
| `REQUIRED_WORKLOAD_SCOPE` | `membership.read` | `artist.read` |

즉 공유 가능한 것은 **메커니즘**(scope 클레임을 세 가지 모양 — 배열/공백구분 문자열/`scp` —
으로 읽어 하나의 필수 스코프와 대조하고 `ROLE_INTERNAL` 을 부여)이고, **정책**(어느 스코프인가)은
서비스별이다. 승격한다면 스코프를 **생성자 파라미터**로 받는 형태여야 한다.

🔴 그리고 이 클래스는 **보안 판정기**다. 잘못 공유하면 한 서비스의 완화가 전 서비스에
퍼진다 — 승격의 대가가 다른 유틸리티와 다르다.

---

# Goal

`WorkloadIdentityAuthoritiesConverter` 를 `libs/java-security` 로 승격할지 **결정하고**,
승격한다면 정책(필수 스코프)을 주입 가능한 형태로 옮기고 두 서비스를 그 위로 옮긴다.

---

# Scope

## In Scope

- 두 사본의 **행 단위 비교** — 메커니즘이 정말 같은지, 다른 축이 스코프 하나뿐인지
- 모집단 재확인: 다른 프로젝트(wms/scm/finance/erp/ecommerce)에 같은 모양의 워크로드
  판정기가 더 있는지 — 🔴 **2벌이라고 가정하고 시작하지 말 것**
- 결정: 승격 / 유지 / 다른 형태. 유지도 산출물이다
- 승격 시: `libs/java-security` 이동 + 두(또는 그 이상) 서비스 전환 + 테스트 이전

## Out of Scope

- 각 서비스가 **어느 스코프를 요구하는가** — 그것은 서비스 정책이고 이 티켓이 바꾸지 않는다
- 새 `/internal/**` 표면 신설
- `platform/security-rules.md` 의 "subject allow-list OR required scope" 규칙 자체 변경

---

# Acceptance Criteria

- [ ] **AC-0 (모집단 재측정)** — `ROLE_INTERNAL` 을 부여하는 컨버터/필터를 저장소 전체에서
      센다. 🔴 2벌은 **내가 아는 수**이지 측정한 수가 아니다 — 다른 도메인이 같은 문제를
      다른 이름으로 이미 풀었을 수 있다(`SystemClientSubjectValidator` 계열 포함)
- [ ] **AC-1 (같음의 근거)** — 사본들을 **행 단위로** 대조해 다른 축을 전부 열거한다.
      스코프 외에 다른 차이가 있으면 그것이 승격 형태를 바꾼다
- [ ] **AC-2 (결정)** — 승격/유지를 **명시적으로** 적는다. 🔴 유지를 고르면 두 사본이
      갈라졌을 때 무엇이 그것을 잡는지도 함께 적을 것(아무것도 없으면 그건 유지의 대가다)
- [ ] **AC-3 (승격했다면)** — 정책은 주입, 메커니즘만 공유. `libs/` 는 project-agnostic 이어야
      하므로 **스코프 문자열 리터럴이 라이브러리에 들어가면 HARDSTOP-03** 이다
- [ ] **AC-4 (승격했다면 — 판정)** — 두 서비스의 기존 인증 매트릭스 테스트가 그대로 통과해야
      한다. 🔴 특히 fan 의 *엔드유저 스코프(`fan-platform.artist.read`)가 거절되는* 케이스 —
      그 케이스가 살아 있어야 공유 클래스가 판정을 느슨하게 만들지 않았음이 보인다
- [ ] **AC-5 (CI 표면)** — `libs/` 모듈은 프로젝트 CI 잡의 gradle task 목록에 안 잡힐 수 있다
      (CLAUDE.md § Project-scoped shared modules 가 지적하는 그 문제). 승격 시 새 모듈/변경이
      **실제로 테스트되는 잡에 들어가는지** 확인할 것

---

# Related Specs

- `platform/shared-library-policy.md`
- `platform/security-rules.md` (machine 토큰 인가 축)
- `platform/contracts/jwt-standard-claims.md`
- `projects/fan-platform/apps/membership-service/.../infrastructure/security/WorkloadIdentityAuthoritiesConverter.java`
- `projects/fan-platform/apps/artist-service/.../config/WorkloadIdentityAuthoritiesConverter.java`

# Related Contracts

- 없음 — 내부 인가 메커니즘이고 HTTP 계약을 바꾸지 않는다

# Edge Cases

- `scp` 배열 / 공백 구분 `scope` 문자열 / JSON 배열 — 세 모양을 다 받는 것이 현재 동작이다.
  승격 시 **하나라도 빠지면 issuer 모양이 다른 배포에서 조용히 403** 이 된다
- 🔴 `tenant_id` **부재를 판정에 쓰지 않는다** 는 것이 두 사본에 모두 주석으로 박혀 있다
  (`TASK-FAN-BE-029` 의 사고). 승격 시 그 주석도 함께 옮길 것 — 이유를 잃으면 다시 넣는다

# Failure Scenarios

- 🔴 **중복이라는 이유만으로 승격한다** — 정책까지 함께 올라가면 한 서비스의 완화가 전부에
  퍼진다. 올라가는 것은 메커니즘뿐이어야 한다
- 🔴 **모집단을 2로 가정한다** — AC-0 이 그래서 있다
- 🔴 **승격하고 테스트는 한쪽에만 남긴다** — 공유 클래스는 두 서비스의 매트릭스가 **각각**
  통과해야 의미가 있다

# Definition of Done

- [ ] AC-0 모집단 실측
- [ ] 사본 행 단위 대조 결과
- [ ] 승격/유지 결정 기록
- [ ] (승격 시) 이동 + 전환 + 양쪽 매트릭스 통과 + CI 표면 확인
- [ ] Ready for review

---

분석=Opus 5 / 구현 권장=**Opus** — 공유 라이브러리 경계 + 보안 판정기라 결정 비용이 크다.
