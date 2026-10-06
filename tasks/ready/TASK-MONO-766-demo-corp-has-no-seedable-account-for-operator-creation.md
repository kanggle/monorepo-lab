# Task ID

TASK-MONO-766

# Status

ready

# Title

데모에서 신규 운영자 생성을 끝까지 보여줄 수 없다 — tenant `demo-corp` 에 "아직 운영자가 아닌" 계정이 시드돼 있지 않다

# Owner

monorepo

# Task Tags

- demo
- seed
- iam
- bug

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet — 시드 보강, 재굽기 필요.

---

# Dependency Markers

- 출처: `TASK-MONO-764` 23차 창(데모 기능 점검표, 흐름 15 — 운영자 생성 · 권한 세트 보기).

---

# 배경 — 23차 창 라이브 실측 (2026-10-06 UTC)

콘솔 `/operators` 생성 폼의 사전 게이트는 **이메일이 tenant `demo-corp` 에 이미 등록된
계정이어야 한다**고 요구한다(`CreateOperatorAccountAdvisory.tsx` / `account-existence.ts`
경로 — admin-service `CreateOperatorUseCase` 가 실제로 그 선재 조건을 검사하는 것으로
보인다).

라이브 `account_db.accounts` 를 테넌트별로 센 결과:

```
consumer-pool   8
ecommerce       1   (시드 셀러 신원)
demo-corp       0
```

운영자 `demo@demo.com` · `requester@demo.com`(둘 다 tenant `demo-corp`)은
`admin_db.admin_operators` 에만 있다 — 즉 **이미 운영자인 사람만** demo-corp 에 존재하고,
"아직 운영자가 아닌 demo-corp 계정"이 하나도 없다. 방문자가 demo-corp 소속으로 새 운영자를
만들 수 있는 **이메일 입력 자원 자체가 없다.**

그려지는 부여 가능 역할: `TENANT_BILLING_ADMIN` · `SUPPORT_LOCK` · `SUPPORT_READONLY` ·
`SECURITY_ANALYST`.

---

# Goal

방문자가 콘솔 `/operators` 생성 폼으로 **신규** 운영자를 실제로 만들어 볼 수 있도록,
`demo-corp` 테넌트에 "운영자가 아직 아닌" 계정을 최소 1개 시드한다.

---

# Scope

## In Scope

- `demo-corp` 테넌트에 아직 운영자로 승격되지 않은 계정을 1개 이상 시드한다(예: "신규
  입사자" 류 — 이름은 구현 시 정한다).
- 그 계정이 어떤 경로로 만들어지는가부터 조사한다 — `demo-corp` 는 ecommerce 의
  consumer-pool 모델과 다른 B2B/엔터프라이즈 테넌트로 보인다. 기존 시드 스크립트가 계정을
  만드는 방식(직접 DB insert vs account-service API 호출)을 그대로 따른다
  (`infra/demo/seed/` 의 기존 계정 생성 패턴을 먼저 grep 할 것).
- 비밀번호는 `infra/demo/seed/lib.sh` 의 기존 데모 비밀번호 컨벤션을 따른다(변수에만,
  출력 금지).

## Out of Scope

- `CreateOperatorUseCase` 의 사전 게이트 로직 변경 — 그 요구사항(이메일이 이미 등록된
  계정이어야 한다)은 의도된 제약이고 결함이 아니다.
- UI 자동화로 이 창에서 실제로 운영자를 생성해 보는 것 — 그건 다음 창의 라이브 확인이다.
- 기존 `demo@demo.com`/`requester@demo.com`(또는 다른 기존 demo-corp 운영자)의 비밀번호·
  역할 변경 — 절대 건드리지 않는다.
- 역할을 미리 부여하는 것 — 콘솔의 생성 흐름 자체가 생성 시점에 역할을 부여한다.

---

# Acceptance Criteria

- [ ] **AC-0 (재측정)** — 착수 시 `account_db.accounts` 를 테넌트별로 다시 세어
      `demo-corp` 가 여전히 0(또는 전부 이미 운영자)인지 확인한다.
- [ ] **AC-1** — `demo-corp` 계정이 오늘 어떤 메커니즘으로 생기는지 조사해 기록한다
      (자체 가입 경로가 있는가? 없다면 시드가 유일한 생성 수단인가).
- [ ] **AC-2** — 시드에 "운영자 아님" 상태의 demo-corp 계정을 1개 이상 추가한다.
- [ ] **AC-3** — 기존 demo-corp 운영자(최소 `demo@demo.com`/`requester@demo.com`)의
      비밀번호·역할이 시드 전후 동일함을 대조로 확인한다(diff).
- [ ] **AC-4 (라이브 확인 — ⏳ 다음 창)** — 재굽기 뒤, 콘솔 `/operators` 생성 폼에서 그
      계정 이메일을 입력하면 사전 게이트를 통과하고, 역할 하나(예: `SUPPORT_READONLY`)를
      부여해 운영자 생성이 실제로 끝난다. 이 AC 는 이 티켓이 ready→done 되는 시점에 닫히지
      않을 수 있다 — 라이브 창이 선행이면 ⚪ + 이유로 남긴다.

---

# Related Specs

- `projects/iam-platform/specs/features/multi-tenancy.md` § 소비자 계정 풀(비교 대상 —
  demo-corp 는 그 풀과 다른 모델일 가능성이 있다, 착수 시 확인)
- `projects/iam-platform/apps/admin-service/src/main/java/com/example/admin/application/CreateOperatorUseCase.java`
- `projects/platform-console/apps/console-web/src/features/operators/components/CreateOperatorAccountAdvisory.tsx`
- `projects/platform-console/apps/console-web/src/features/operators/api/account-existence.ts`

# Related Contracts

- 없음(시드 전용 — API/이벤트 계약 변경 없음)

---

# Edge Cases

- `demo-corp` 가 consumer-pool 과 다른 B2C/B2B 축이라면, 같은 시드 메커니즘을 재사용하면
  안 될 수 있다 — 착수 시 AC-1 이 그 모델을 먼저 확인한다.
- 시드 계정이 **다른** 창(예: `TASK-MONO-764` 흐름 1~4, iam 계정 플로우)의 전제를
  건드리지 않는지 확인 — 새 행 하나를 더하는 것이지 기존 계정 수·축을 바꾸는 것이 아니다.

# Failure Scenarios

- **시드 계정을 운영자로도 함께 만든다** — 생성 흐름을 시연할 대상이 사라진다.
- **consumer-pool 메커니즘을 그대로 복사해 demo-corp 계정을 만든다** — 테넌트 모델이
  다르면 잘못된 축에 데이터가 들어간다(AC-1 이 이것을 막는다).
- **기존 demo-corp 운영자 행을 "정리"하며 같이 건드린다** — Out of Scope 위반, 다른 창의
  전제를 깬다.
