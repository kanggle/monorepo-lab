# Task ID

TASK-MONO-547

# Title

`/wms/operations` 403 — 워크스루가 적은 사유(`projection-status` 권한 없음)는 **존재하지 않는 권한**이다. 실제로는 대시보드 **읽기 중 유일하게 `WMS_ADMIN` 게이트**이고, 이건 역할모델 결정이다

# Status

done

# Owner

monorepo

# Task Tags

- security
- console
- docs

---

# 배경

`docs/guides/interview-demo-walkthrough.md` § 6 의 이 행은 **추적 티켓이 없다**(`—`).
원래 `TASK-MONO-514` 를 인용했으나 514 는 `MASTER_WRITE` 티켓이고 `projection-status` 를
한 번도 다루지 않아 인용이 지워졌고, 그 뒤로 **어느 큐에도 없다**.

🔴 그리고 § 6 의 드리프트 가드는 이 행을 **구조적으로 볼 수 없다** — 술어가 *"인용된
티켓이 done 인데 행이 그렇게 안 적혀 있나"* 라서, 추적 칸이 `—` 인 행은 영원히 걸리지
않는다. 실측: 가드는 `48행 중 40행이 티켓 인용` 이라고 스스로 말하고 나머지 8행에는
아무 판정도 하지 않는다.

## 🔴 행에 적힌 사유가 틀렸다 (이것이 이 티켓의 첫 산물)

행은 *"운영자 토큰에 `projection-status` 권한이 없다"* 고 적는다. **그런 권한은 없다.**
실측:

- `OperationsController` 는 **클래스 레벨**로 `@PreAuthorize("hasRole('WMS_ADMIN'))"` 이다
  (`projects/wms-platform/apps/admin-service/.../api/dashboard/OperationsController.java:18`).
  경로 전체(`/api/v1/admin/operations/**`)가 그 게이트 아래 있고 `projection-status` 라는
  이름의 권한·스코프는 코드 어디에도 없다.
- 운영자 토큰이 받는 wms 역할은 `WMS_OPERATOR, OUTBOUND_{READ,WRITE},
  INBOUND_{READ,WRITE}, INVENTORY_{READ,WRITE}, MASTER_READ` 다
  (`OperatorRoleDerivation.WMS_OPERATOR_ROLES`). **ADMIN 티어는 의도적으로 제외**돼
  있고(`TASK-BE-433`, `TASK-MONO-514` 가 재확인), 그 javadoc 은 *"reversing it is a
  role-model decision, not a parity fix"* 라고 못 박는다.

즉 **누락된 권한을 부여하는 문제가 아니다.** 사유를 그대로 두면 다음 사람이 있지도 않은
권한을 찾는다.

## 진짜 물음 — 형제 게이트 전수를 세면 한 칸만 어긋나 있다

`admin-service` 의 `@PreAuthorize` 전수(실측):

| 게이트 | 컨트롤러 |
|---|---|
| `WMS_VIEWER` | Throughput · Shipment · Order · MasterRef · Inventory · Asn · AdjustmentAudit · Alert(조회) |
| `WMS_OPERATOR` | Alert(쓰기 1건) |
| `WMS_ADMIN` / `WMS_SUPERADMIN` | User · Role · Assignment — **신원 관리** |
| **`WMS_ADMIN`** | **Operations — GET 하나뿐인 투영 지연 리포트** |

역할 계층은 `WMS_SUPERADMIN > WMS_ADMIN > WMS_OPERATOR > WMS_VIEWER`
(`admin-service/config/SecurityConfig.java`). 그래서 운영자는 `WMS_OPERATOR` 로
`WMS_VIEWER` 를 상속해 대시보드 읽기가 **전부 열린다** — § 6 의 *"`/wms/inventory` 는
실제로 찬다"* 행이 그 증거다. **읽기 전용 화면 중 `WMS_ADMIN` 을 요구하는 것은
`Operations` 하나뿐이다.**

그 한 칸이 의도인지(운영 내부 지표는 관리자 전용) 낙오인지가 이 티켓이 정할 것이다.
신원 관리(User/Role/Assignment)가 ADMIN 인 것은 성격이 다르므로 **선례로 쓰면 안 된다**.

---

# Goal

이 행이 **소유 티켓을 갖고**, 적힌 사유가 **코드와 일치**하며, `/wms/operations` 의
게이트 티어가 **결정으로 확정**된다(바꾸든 유지하든 근거가 남는다).

---

# Scope

1. 워크스루 § 6 해당 행의 **사유 정정** — 존재하지 않는 권한 이름을 실제 게이트로.
2. 게이트 티어 결정. 선택지와 각각의 결과:
   - **(a) 유지** — 데모에서 이 화면은 계속 403. 그렇다면 § 6 행은 *"결함"이 아니라
     "설계상 운영자에게 닫힌 화면"* 으로 성격이 바뀌고, **콘솔이 403 을 오류가 아니라
     권한 없음으로 표시**해야 한다.
   - **(b) 읽기를 `WMS_VIEWER` 로 내린다** — 형제 8개 읽기와 정렬. 투영 지연 리포트가
     운영자에게 민감한지 판단 필요.
   - **(c) 운영자 엔타이틀먼트에 `WMS_ADMIN` 추가** — 🔴 **거의 확실히 틀린 선택**.
     `TASK-BE-433` 이 ADMIN 티어를 통째로 제외한 결정을 뒤집어 취소·강제 사가 실패까지
     열린다. 기각하더라도 **기각 사유를 적을 것**.
3. 결정이 (b)/(c) 면 그것이 **역할모델 변경**이므로 ADR 필요 여부를 판정한다
   (`platform/architecture-decision-rule.md`).

## Out of Scope

- `MASTER_WRITE` 제외 결정 재개 — `TASK-MONO-514` 가 소유하며 **이 티켓과 무관**하다.
- `fan` 아암 등 `OperatorRoleDerivation` 의 다른 분기.

---

# Acceptance Criteria

- [ ] **AC-0 (사실 재확인)** — 착수 시 게이트 전수를 **다시 센다**. 🔴 위 표를 물려받지
      말 것(이 티켓 자체가 *"물려받은 사유가 틀렸다"* 에서 나왔다). `@PreAuthorize`
      전수 + `WMS_OPERATOR_ROLES` + 역할 계층 3개를 각각 파일에서 읽어 대조한다.
- [ ] **AC-1 (라이브 판정)** — 데모 스택에서 운영자 토큰으로 `/api/v1/admin/operations/
      projection-status` 를 직접 호출해 **403 을 재현**하고, 같은 토큰으로 형제 읽기
      (예: inventory 대시보드)가 **200** 인지 함께 확인한다. 🔴 **콘솔 화면이 아니라
      백엔드 응답으로 판정할 것** — 콘솔은 BFF 를 거치므로 화면의 오류 표시가 백엔드
      상태와 다를 수 있다(이 저장소 선례).
- [ ] **AC-2 (결정)** — (a)/(b)/(c) 중 하나를 근거와 함께 확정하고, **기각한 선택지의
      기각 사유도 적는다**. (c) 를 기각한다면 `TASK-BE-433` 의 어떤 결정을 뒤집게 되는지
      명시할 것.
- [ ] **AC-3 (사유 정정)** — § 6 행의 사유를 실제 게이트로 고친다. 🔵 결정이 (a) 면 행의
      **성격 표기도** 바꾼다(🔴 결함 → 설계상 닫힘).
- [ ] **AC-4 (bite)** — 결정이 (b) 라면 게이트 변경이 실제로 무는지 확인한다: 변경 후
      운영자 토큰 200, **`WMS_VIEWER` 미만 토큰은 여전히 403**. 🔴 200 만 보면 "열렸다"와
      "전부 열렸다"가 구별되지 않는다.
- [ ] **AC-5 (행이 다시 고아가 되지 않는다)** — 추적 인용은 **발행 PR 에서 이미 붙였다**
      (`—` → 이 티켓). 이 티켓이 done 으로 가면 § 6 행이 그 사실을 반영해야 하고, 이제
      인용이 있으므로 **안 고치면 드리프트 가드가 CI 를 빨갛게 만든다**. 🔴 단, 결정이
      (a)(유지)면 done 표기가 *"고쳤다"* 로 읽히지 않게 성격을 함께 적을 것.

---

# Related Specs

- `projects/wms-platform/specs/contracts/http/admin-service-api.md` § 6.2 — projection lag report
- `projects/wms-platform/specs/services/gateway-service/architecture.md`
- `platform/architecture-decision-rule.md`

# Related Contracts

- `projects/wms-platform/specs/contracts/http/admin-service-api.md` — 게이트 티어를 바꾸면 **여기부터** 고친다.

---

# Edge Cases

- **콘솔 BFF 가 403 을 어떻게 표시하는지** 가 결정 (a) 의 품질을 좌우한다 — "권한 없음"이
  아니라 "오류"로 보이면 면접관에게는 깨진 화면이다.
- **`WMS_VIEWER` 를 직접 들고 있는 자격이 있는가** — 운영자는 계층 상속으로 얻는다.
  직접 보유 자격이 없다면 (b) 의 bite 에서 "미만 토큰"을 만들 방법을 먼저 정해야 한다.

# Failure Scenarios

- **사유를 안 고치고 게이트만 만진다** → 문서가 계속 없는 권한을 가리킨다.
- **(c) 를 "가장 쉬운 수정"으로 고른다** → `TASK-BE-433` 의 ADMIN 티어 제외를 조용히
  뒤집어 취소·강제 사가 실패가 운영자에게 열린다.
- **콘솔 화면으로 판정한다** → BFF 를 거친 표시와 백엔드 응답이 다를 수 있다.
