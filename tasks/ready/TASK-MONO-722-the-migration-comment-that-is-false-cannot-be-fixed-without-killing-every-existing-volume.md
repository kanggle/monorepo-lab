# Task ID

TASK-MONO-722

# Title

⏳ 🔴 **거짓인 마이그레이션 주석을 고치면 기존 볼륨이 전부 죽는다** — `V0019` 헤더 정정은 **신선 볼륨 게이트**에 묶인다

# Status

ready (2026-09-23 UTC — ⏳ **SCHEDULED / 조건 게이트**. AC-0 을 통과하기 전에는 **시작하지 마라**)

# Owner

미지정

# Task Tags

- iam-platform
- flyway
- demo
- scheduled

---

> **분석 모델:** Opus 5 / **구현 권장:** Sonnet — 고칠 것은 주석 한 문단이다.
> 🔴 **어려운 것은 편집이 아니라 «지금 해도 되는가» 이고, 그것이 AC-0 전부다.**

---

# ⏳ DO NOT START — 이 티켓은 날짜가 아니라 **조건**에 묶여 있다

조건: **auth-service 의 `auth_db` 가 `V0019` 행을 갖지 않는 볼륨 위에서 돌 때**
(= 신선 볼륨. 데모에서는 `terraform apply` 가 인스턴스를 교체하는 날).

🔴 **달력을 보고 시작하지 마라.** AC-0 이 그 조건을 **측정**하고, 측정이 아니라고 답하면
이 티켓은 그대로 `ready/` 에 남는다. 저장소 규정이 이 모양을 요구한다
(`⏳ SCHEDULED / DO NOT START` + **AC-0 verify-then-act 게이트**, 맨 달력 확인 금지).

---

# 배경 — 이것은 가설이 아니라 **측정**이다

`ADR-MONO-076` § D5 가 이 티켓을 만들었다. `TASK-MONO-721` AC-3 은
`V0019__seed_internal_service_workload_clients.sql` 헤더의 이 문장을 고치라고 한다:

```sql
--   These clients are GAP platform infrastructure, not bound to a product tenant.
--   tenant_id='global-account-platform', tenant_type='INTERNAL'. The receiving
--   resource servers (account/security) validate signature + issuer only and do NOT
--   pin tenant (they serve all tenants), so the tenant claim is informational here.
```

🔴 **이 문장은 거짓이다.** 게이트웨이 `JwtAuthenticationFilter` 가 검증된 토큰의 `tenant_id` 로
`X-Tenant-Id` 를 주입하고 account-service 의 `TenantScopeGuard` 가 경로 `{tenantId}` 와 대조한다
⇒ 수신 측은 **핀한다**(게이트웨이 경유 호출에 한해). `TASK-MONO-717` 이 **그 문장을 인용해**
테넌트를 골랐고, 그것이 16차 창 FAIL 의 절반이다.

## 🔴🔴 그런데 **그 파일의 바이트를 고치면 기존 볼륨이 죽는다**

| 잰 것 | 값 | 어떻게 |
|---|---|---|
| `V0019` 가 이미 적용된 마이그레이션인가 | ✅ | `db/migration/` 에 있고 `V0036` 까지 그 위에 쌓였다 |
| Flyway 체크섬이 **주석**을 포함하는가 | ✅ | 파일 전체가 체크섬 입력이다 |
| auth-service 가 `validateOnMigrate` 를 끄는가 | 🔴 **안 끈다** | `application.yml` · `application-e2e.yml` 둘 다 그 키가 **없다** ⇒ 기본값(참) |
| 데모 볼륨이 stop/start 를 건너 사는가 | ✅ | 15차 창 실측(루트 EBS 보존) |
| CI 가 이것을 잡을 수 있는가 | 🔴 **영원히 못 잡는다** | CI·`docker compose up` 은 **언제나 빈 볼륨** |

🔵 **이 저장소는 같은 부류를 이미 한 번 밟고 문서로 남겼다** —
[`projects/iam-platform/docs/flyway-dev-seed-migrations.md`](../../projects/iam-platform/docs/flyway-dev-seed-migrations.md)
§ 1~2: *"a dev-only seed broke production-shaped startup for every developer with an existing
volume, and **nothing in CI could ever have caught it**."*

🔴 그러므로 **로컬 초록도, CI 초록도 이 변경의 증거가 아니다.** 둘 다 빈 볼륨에서 잰다.

---

# Goal

`V0019` 헤더의 거짓 문장을, **기존 볼륨을 죽이지 않는 시점에** 고친다.

---

# Scope

## 포함

- AC-0 의 조건 측정과, 그 결과에 따른 **실행 또는 대기**.
- 조건이 참일 때 `V0019` 헤더 문단의 정정.

## 제외

- `ADR-MONO-076` D5-1(계약 문서 개정)·D5-2(새 `V0037` 헤더의 정정) — **그 둘은 이 티켓을
  기다리지 않는다.** 즉시 가능하고 `TASK-MONO-721` 이 든다. 🔵 그래서 **낡은 문장이 반증되지
  않은 채 방치되는 구간은 없다** — 이 티켓은 «마지막 복사본 하나» 만 든다.
- `flyway repair` 로 우회하기 — 🔴 **대안이 아니다**(아래 § Edge Cases).
- `validateOnMigrate: false` 로 끄기 — 🔴 **대안이 아니다**(같은 곳).

---

# Acceptance Criteria

## AC-0 — ⏳ **착수 게이트: 조건을 «재고» 나서 행동한다** (달력 금지)

- [ ] 🔴 **먼저 측정한다.** 대상 DB 에서:

```sql
SELECT version, checksum, installed_on
  FROM flyway_schema_history
 WHERE version = '19';
```

  - **행이 없다** ⇒ 신선 볼륨이다 ⇒ **AC-1 로 간다.**
  - **행이 있다** ⇒ 🔴 **이 티켓은 여기서 멈춘다.** 파일을 고치지 말고 `ready/` 에 그대로 둔다.
    측정한 날짜와 값을 § 측정 기록에 **한 줄 적고** 끝낸다(다음 사람이 같은 측정을 반복하지 않게).

- [ ] 🔴🔴 **모집단을 하나로 착각하지 마라.** 「신선하다」는 **볼륨마다** 참/거짓이다:
      데모 인스턴스 · 각 개발자의 로컬 `iam-mysql` 볼륨 · 그 밖에 떠 있는 것.
      데모가 신선해져도 **로컬 볼륨을 가진 사람은 여전히 죽는다** ⇒ AC-2 가 그것을 다룬다.

## AC-1 — 정정한다 (조건이 참일 때만)

- [ ] `V0019` 헤더의 그 문단을 실측에 맞게 고친다. 🔵 **지우지 말고 고쳐라** —
      *"왜 그렇게 적혀 있었는가"* 가 사라지면 다음 사람이 같은 추론을 다시 한다.
- [ ] 정정 문장은 **기전을 이름으로** 지목한다: `JwtAuthenticationFilter` 가 주입하고
      `TenantScopeGuard` 가 대조한다, 그리고 **헤더가 없으면 건너뛴다**는 예외까지.

## AC-2 — 🔴 **남은 볼륨을 가진 사람에게 무엇을 시킬지 적는다**

- [ ] 커밋 메시지와 `flyway-dev-seed-migrations.md` 에 **복구 한 줄**을 남긴다
      (`flyway repair` 가 **이 경우에는** 맞는 도구다 — § Edge Cases 가 왜인지 적는다).
- [ ] 🔴 **적지 않으면** 다음 사람의 증상은 *"auth-service 가 기동 안 됨"* 이고, 원인은
      **두 주 전 머지된 주석 한 줄**이다. 그 거리가 이 AC 의 존재 이유다.

## AC-3 — bite

- [ ] 🔴 **빈 볼륨에서 도는 테스트로는 이 변경을 검증할 수 없다.** 검증은
      «기존 볼륨 + 고친 파일 → 기동 실패» 를 **한 번 직접 보는 것**이다(고치기 **전에**).
      🔵 그 실패를 본 적이 없으면 AC-0 의 게이트는 **미신**이고, 미신은 언젠가 무시된다.

---

# Related Specs / Contracts

- [`ADR-MONO-076`](../../docs/adr/ADR-MONO-076-which-workload-credential-may-act-on-which-tenant.md) § D5 — 이 티켓의 출처
- `projects/iam-platform/apps/auth-service/src/main/resources/db/migration/V0019__seed_internal_service_workload_clients.sql`
- [`projects/iam-platform/docs/flyway-dev-seed-migrations.md`](../../projects/iam-platform/docs/flyway-dev-seed-migrations.md) § 1~3
- `TASK-MONO-717` § CORRECTION · `TASK-MONO-721` AC-3

---

# 측정 기록

> 🔴 AC-0 을 돌릴 때마다 **한 줄씩** 여기에 남긴다 — 「아직 아니다」도 측정이다.

| 날짜 | 어느 볼륨 | `version='19'` 행 | 판정 |
|---|---|---|---|
| (아직 없음) | | | |

---

# Edge Cases

- **`flyway repair` 로 지금 우회한다** — 🔴 대안이 아니다. repair 는 **각 DB 에서 사람이 한 번씩**
  돌려야 하고, 안 돌린 곳은 여전히 죽는다. 즉 «고쳤다» 가 **볼륨마다 따로** 참이 되는 상태를
  만든다. 🔵 다만 **AC-2 의 복구 절차로는 맞다** — 이미 깨진 사람에게 주는 처방과, 깨뜨릴지
  말지의 결정은 **다른 질문**이다.
- **`validateOnMigrate: false` 로 끈다** — 🔴 대안이 아니다. 그 게이트는 «파일과 이력이 어긋났다»
  를 잡는 **유일한** 장치이고, 주석 하나를 고치려고 끄면 **진짜 어긋남**도 같이 안 보이게 된다.
- **`R__` 반복 마이그레이션으로 바꾼다** — 🔴 더 나쁘다. `flyway-dev-seed-migrations.md` § 3
  finding 2 가 실측으로 반증했다(*"Detected applied migration not resolved locally"*).
- **주석만 고쳤는데 정말 깨지나** — ✅ 깨진다. 체크섬 입력은 **파일 전체**다. 🔵 AC-3 이 그것을
  한 번 **직접 보게** 하는 이유가 이것이다 — 이 문장은 지금 **문서에서 읽은 것**이지
  이 저장소에서 잰 것이 아니다.

---

# Failure Scenarios

1. 🔴🔴 **AC-0 을 건너뛰고 「어차피 주석인데」 하고 고친다** → 기존 볼륨을 가진 모두가 기동 실패.
   CI 는 초록. 증상과 원인 사이 거리가 몇 주. **이 티켓이 존재하는 이유 전부가 이것이다.**
2. 🔴 **데모가 신선해진 것을 「끝났다」로 읽는다** → 로컬 볼륨을 가진 사람은 그대로 죽는다(AC-0 둘째 칸).
3. 🔴 **AC-3 을 건너뛴다** → 게이트의 근거가 «문서에서 읽었다» 로만 남는다. 미신이 된 게이트는
   다음 사람이 무시한다.
4. 🔴 **이 티켓을 `TASK-MONO-672` 로 옮긴다** → 672 는 «**닫히면서** 집을 잃는 의무» 만 받고,
   게다가 **측정**의 집이다. 이 의무는 조건에 묶인 **변경**이다. (`ADR-MONO-076` § History 가
   기록하듯 첫 판이 정확히 그렇게 틀렸다.)
