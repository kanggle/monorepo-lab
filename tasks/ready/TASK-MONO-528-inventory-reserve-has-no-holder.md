# Task ID

TASK-MONO-528

# Title

`INVENTORY_RESERVE` 를 쥔 자격증명이 없다 — 출고 사가의 예약 단계가 막혀 있고, `ADR-MONO-061` 이 그것을 이제 **부여 가능**하게만 만들어 두었다

# Status

ready

# Owner

monorepo

# Task Tags

- iam
- security
- demo

---

# 배경

`TASK-MONO-514` 가 발굴하고 **의도적으로 묶지 않은** 두 번째 표면이다.

wms 출고 사가의 예약 단계는 `INVENTORY_RESERVE` 를 요구하는데, 그 권한을 쥔 주체가 없다.
운영자에게 주는 것은 **계약 위반**이다 — `inventory-service-api.md` 가 명시한다:

> `INVENTORY_RESERVE` is a **machine-to-machine** scope. **Human users do not hold it.**

즉 답은 운영자 엔타이틀먼트 확대가 아니라 **워크로드 자격증명**이고, `TASK-MONO-514` §③ 이
*"별도 결정이다. 이 티켓에서 묶지 않는다"* 로 남겼다.

## 무엇이 바뀌어서 지금 이 티켓이 생겼나

`ADR-MONO-061`(ACCEPTED 2026-08-13, **C**)이 워크로드 토큰에 `roles` 를 실을 수 있게 만들었고,
`TASK-MONO-514` 가 `WorkloadRoleCatalog` 로 그 배선을 깔았다. **능력은 생겼고 부여는 안 했다** —
ADR 의 fail-closed 기본값(열거되지 않은 클라이언트는 아무 role 도 받지 않는다)이 그대로 유지된다.

🔴 **"이제 가능하다" 는 "이제 옳다" 가 아니다.** 이 티켓이 답해야 할 것은 배선이 아니라 결정이다.

---

# Goal

wms 출고 사가의 예약 단계가 **실제로 통과**한다 — 또는 그것이 v1 범위 밖이라는 것이 결정으로
기록되고, 데모/워크스루가 그 전제 위에서 정확하게 서술된다.

---

# Scope

## In Scope

- `INVENTORY_RESERVE` 를 어느 워크로드 클라이언트가, **어느 scope 로** 받는지 결정
- 예약 단계를 실제로 호출하는 주체가 무엇인지 실측(서비스 간 호출인가, 사가 오케스트레이터인가,
  아니면 **아무도 부르지 않는가**)
- `docs/guides/interview-demo-walkthrough.md` § 6 의 *"WMS 출고는 주문까지만 심는다"* 행 갱신

## Out of Scope

- 운영자 엔타이틀먼트 확대 — **계약이 금지한다**(위 인용)
- `ADR-MONO-061` 의 재해석. C 는 확정이고, 이 티켓은 그 안에서 부여를 정한다

---

# Acceptance Criteria

- [ ] **AC-0 (전제 실측 — 착수의 첫 일)** — 🔴 *부여할 대상이 실재하는지부터 재라.* 예약 단계를
      **호출하는 코드**가 저장소에 있는가, 있다면 어떤 자격증명으로 호출하는가를 전수하라.
      `TASK-MONO-514` 가 배운 교훈이 그대로 적용된다 — **이름이 존재한다는 사실은 그것이 무언가를
      연다는 증거가 아니고**, 그 역도 참이다: 권한을 부여해도 **부르는 코드가 없으면** 아무것도
      안 열린다. 워크스루 행이 함께 말하는 *"배송은 도달 불가 TMS 스텁에 의존한다"* 도 같은 축에서
      다시 재라 — 예약을 풀어도 그 다음이 막혀 있으면 이 행은 여전히 🔴 다.
- [ ] **AC-1 (부여 결정)** — 어느 cc 클라이언트가 어느 scope 로 `INVENTORY_RESERVE` 를 받는지
      `WorkloadRoleCatalog` 에 명시한다. 🔴 **기본값을 건드리지 말 것** — 열거되지 않은 클라이언트는
      계속 아무것도 받지 않는다(`ADR-MONO-061` § 무엇이 구속력을 갖나 3).
      대응하는 scope 가 없으면 scope 신설도 이 티켓의 결정이다.
- [ ] **AC-2 (도달 가능성)** — 예약 단계가 **실제 호출로 통과**한다. 토큰에 클레임이 실렸다는 것만으로는
      부족하다 — `TASK-MONO-514` 의 결함이 정확히 그 모양이었다.
- [ ] **AC-3 (음성 대조)** — 그 자격증명이 **없는** 호출자는 여전히 거부된다. 🔵 `TASK-MONO-514` 가
      쓴 형태를 재사용하라: **같은 클라이언트가 좁은 scope 로 받은 토큰**이 거부되는 것이 가장
      날카로운 대조군이다(클라이언트·시크릿·테넌트가 전부 같고 요청 scope 만 다르다).
- [ ] **AC-4 (사람 평면 불변)** — 어떤 운영자 토큰도 `INVENTORY_RESERVE` 를 얻지 않는다.
      `OperatorRoleDerivation` 은 손대지 않으며, 그것을 **단언으로 고정**한다(계약 인용 그대로).
- [ ] **AC-5 (워크스루 정합)** — § 6 의 해당 행이 실측 결과대로 갱신된다.
      `check-walkthrough-ledger-drift.sh` rc=0.

---

# Related Specs

- `projects/wms-platform/specs/contracts/inventory-service-api.md` (§ roles — 인용의 출처)
- [`ADR-MONO-061`](../../docs/adr/ADR-MONO-061-workload-token-authorization-plane.md) (ACCEPTED — C)
- `projects/iam-platform/apps/auth-service/.../WorkloadRoleCatalog.java`
- `tasks/done/TASK-MONO-514-wms-master-writes-need-a-role-nobody-can-get.md` (§③ · § 구현 ⑦)

# Related Contracts

- `platform/contracts/jwt-standard-claims.md` § Gateway Enforcement Rules (머신 인가 축)

# Edge Cases

- **부르는 코드가 없을 수 있다.** 그 경우 이 티켓의 답은 부여가 아니라 *"예약 단계는 v1 에서 도달
  경로가 없다"* 를 기록하는 것이고, 그것도 산출물이다(조용히 부여만 하고 닫으면 아무도 쓰지 않는
  권한이 늘어난다).
- wms 는 데이터에 테넌트가 거의 없다 — `tenant_id` 컬럼을 가진 테이블이 5개 DB 통틀어
  `outbound_db.outbound_order` 하나뿐이다(`TASK-MONO-514` Edge Case 실측). 권한 범위를 테넌트
  격리로 좁힐 수 없다는 뜻이다.

# Failure Scenarios

- **role 만 부여하고 닫는다** — 부르는 코드가 없으면 아무것도 열리지 않고, 권한만 넓어진다.
- **AC-2 없이 토큰 클레임만 확인한다** — `TASK-MONO-514` 가 정확히 그 함정을 문서화했다.
- **워크스루 행을 ✅ 로 바꾸면서 "배송" 절반을 빠뜨린다** — 그 행은 예약과 배송 **둘 다**를
  서술한다. 한쪽만 풀렸으면 🔵 이지 ✅ 가 아니다.

# Definition of Done

- [ ] AC-0 실측 기록 (부르는 코드 전수)
- [ ] 결정 + 배선 (또는 "범위 밖" 결정 기록)
- [ ] AC-2/AC-3 실측 증거
- [ ] 워크스루 § 6 행 갱신 + 가드 rc=0
- [ ] Ready for review
