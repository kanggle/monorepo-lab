# Task ID

TASK-BE-584

# Title

`ADR-MONO-064`·`065` 의 테넌트 축이 **화면까지 닿는지 한 번도 측정되지 않았다** — 데모 볼륨 리셋 + 재시드 합동 검증

# Status

ready

# Owner

wms-platform

# Task Tags

- demo
- verification
- read-model

---

# 배경 — 닫힌 티켓 두 개가 **같은 한 칸**을 열어 둔 채 끝났다

| 출처 | 열린 칸 | 원문 |
|---|---|---|
| `TASK-BE-581` AC-3 (`ADR-MONO-064`) | 콘솔 렌더 계층 | *"⚠️ 범위 — 콘솔 렌더 계층은 태우지 않았다. … 남은 미검증 구간은 **BFF/React 가 비어 있지 않은 배열을 실제로 렌더하는가** 뿐"* (기동 중 커밋 차지 92% 도달로 중단) |
| `TASK-BE-583` AC-2 (`ADR-MONO-065`) | 프로젝션 행의 테넌트 | *"🔴 기존 프로젝션 행은 소급 채우지 않는다 — 복구 경로는 064 와 같은 볼륨 초기화 + 재시드이고 두 결정이 **같은 재시드 한 번**으로 함께 풀린다"* |

`TASK-BE-581` 의 Definition of Done 은 지금도 **"콘솔 목록 브라우저 증거" 가 미체크**다.

⇒ **두 칸은 같은 리셋 한 번으로 닫힌다.** 따로 하면 볼륨을 두 번 태우게 되고, 이 저장소는
이미 두 번 "이중 bake 회피" 판단을 내렸다.

## 🔴 손대지 않은 볼륨에서 지금 띄우면 아무것도 증명하지 못한다

`ADR-MONO-065` D1 의 읽기 술어는 두 리포지토리에 같은 모양으로 있다:

```java
// OrderSummaryRepository:24 / ShipmentSummaryRepository:22
"WHERE (:tenantId IS NULL OR o.tenantId = :tenantId) "
```

같은 파일 L17-L20 이 그 의미를 직접 적고 있다 — *"null means an unrestricted caller and
matches every row, **INCLUDING rows whose own tenantId is null**"*. 그리고 `V3` 는
`tenant_id` 를 nullable 로 넣었고 **기존 행을 소급 stamp 하지 않았다**(D1).

조합하면, 데모 운영자(`demo-corp` → `restrictedTo`)에게는:

```
:tenantId = 'demo-corp'   →   o.tenant_id = 'demo-corp'
기존 행     o.tenant_id = NULL
SQL 3치 논리:  NULL = 'demo-corp'  →  UNKNOWN  →  행 제외
```

⇒ 지금 재면 **0원소**가 나온다. 그리고 0 은 아래를 **전부 같은 값**으로 낸다:

- 격리가 동작한다
- 프로젝션이 비었다
- 컨슈머가 죽었다
- 내가 부른 엔드포인트가 틀렸다

**판정 불가**다. 리셋 없는 초록도, 리셋 없는 빨강도 근거가 되지 못한다.

🔵 그리고 이 유도가 맞다면 **`TASK-BE-583` 이 축 도입 *전* 에 실측한
`/dashboard/orders → totalElements=1` 이 지금은 `0` 으로 바뀌어 있다** — 결함이 아니라
그 행이 축보다 오래됐기 때문이다. 즉 현재 데모의 wms 대시보드는 **비어 있을 가능성이 높다.**

⚠️ 🔴 **이것은 JPQL + 알려진 행 상태에서 유도한 예측이고 라이브로 재지 않았다**(티켓 작성 시점
스택 정지 — `monorepo-traefik` 만 가동). **AC-0 이 사실로 만들거나 뒤집는다.** 유도를 실측으로
적지 않는다.

---

# Goal

데모 볼륨을 리셋+재시드한 뒤, `ADR-MONO-064`(생성 경로가 호출자 테넌트를 박는다)와
`ADR-MONO-065`(admin 읽기 평면의 **2 격리 / 6 전역** 분할)가 **콘솔 화면까지 실제로 닿는다**는
것이 **차등 대조군**으로 확인된다. 닿지 않으면 **어느 구간에서 끊기는지**가 특정된다.

---

# Scope

## In Scope

- wms 데모 볼륨 리셋 + 재시드 — **064·065 를 한 번에**
- 기동 범위 = `iam` + `wms` + `console` (`demo-up.sh wms console` — `resolve_deps` 가
  하드 의존 `iam` 을 자동 포함한다)
- 판정 지표 = **BFF/API 응답의 원소 수**
- `ADR-MONO-065` 의 **양쪽 절반**: 격리 2개 표면 + 창고 전역 6개 표면(R1=a)
- `ADR-MONO-064` 의 남은 칸: 콘솔 `/wms/outbound` 가 **비어 있지 않은 목록을 렌더**

## Out of Scope

- `profile=full` 기동 — 이 호스트에서 불가(§ Edge Cases)
- **제품 코드 변경** — 이 티켓은 검증이다. 결함이 나오면 **별도 fix 티켓**을 만든다
- `dbexec` 로 `tenant_id` 를 손으로 박는 것 — `TASK-BE-581` § Out of Scope 가 이미 금지했다
  (제품이 만들 수 없는 행을 만들고, 그 위의 모든 검증이 무효가 된다)
- 기존 행 소급 backfill — `ADR-MONO-065` D1 이 금지

---

# Acceptance Criteria

- [ ] **AC-0 (리셋 *전* 실측 — 이 순서가 load-bearing)**
      리셋하면 되돌릴 수 없으므로 **먼저** 잰다. 이 값이 차등의 한쪽이다.
      - `admin_order_summary` · `admin_shipment_summary` 의 행 수와 `tenant_id` 분포
      - `outbound_order` 의 행 수와 `tenant_id` 분포
      - `demo-corp` 운영자 토큰으로 `/api/v1/admin/dashboard/orders` · `/dashboard/shipments`
        **원소 수** ⇒ 위 § 예측(`1 → 0`)을 **확정하거나 뒤집는다**
      - 🔴 **두 번째 테넌트를 assume 할 수 있는가** — `TASK-BE-583` ⑤ 가 실측한 wms-entitled
        모집단은 `acme-corp` · `demo-corp` · `ecommerce` · `initech-corp` · `wms` 이고,
        `TASK-BE-581` 2회차는 **`assume wms` 가 실패**함을 실측했다. **AC-3·AC-4 가 전적으로
        이것에 달려 있다.** 하나도 assume 되지 않으면 AC-3·AC-4 는 **라이브로 판정 불가**이며,
        그 사실을 그대로 적는다 — 🔴 **대체 초록(단일 테넌트 측정)으로 메우지 않는다**

- [ ] **AC-1 (리셋 + 재시드)**
      `wms_*` 볼륨 제거 후 `bash infra/demo/demo-up.sh wms console`.
      🔴 **compose 프로젝트명은 짧은 슬러그 `wms` 다** — `docker compose -f …` 를 그냥 부르면
      프로젝트명이 디렉터리명(`wms-platform`)이 되어 **새 빈 볼륨**을 만든다.
      `TASK-BE-581` 3회차가 이 함정을 밟았고, 그대로 갔으면 신선 볼륨 위에서 전부 0을 보고
      결론지을 뻔했다

- [ ] **AC-2 (`ADR-MONO-064` — 생성 경로가 화면까지)**
      재시드 후 `outbound_order.tenant_id` 가 `demo-corp` 로 **채워져 있고**(D1 이 박는다),
      콘솔 BFF `/api/wms/outbound` 가 **원소 ≥ 1**.
      🔴 **판정은 HTML grep 이 아니다** — 콘솔 wms 화면은 클라이언트 렌더라 SSR HTML 을
      grep 하면 0건이 나오고, 그 0건은 부재의 증거가 아니다

- [ ] **AC-3 (`ADR-MONO-065` 격리 절반 — 2개 표면)**
      **차등 대조군**으로 잰다:

      ```
      demo-corp 토큰    → /dashboard/orders      원소 ≥ 1   (자기 행이 보인다)
      <타 테넌트> 토큰  → /dashboard/orders      원소 0     (같은 행이 안 보인다)
      ```

      `/dashboard/shipments` 도 같은 쌍으로.
      🔴 **한 테넌트만으로 재면 필터가 있든 없든 같은 값이 나온다** — 상수 비교와 구별되지
      않는다. `TASK-BE-583` § Failure Scenarios 1번이 정확히 이 실패다

- [ ] **AC-4 (`ADR-MONO-065` 전역 절반 — 6개 표면, R1=a)**
      **같은 타 테넌트 토큰**으로 나머지 6개가 **막히지 않는다**:
      `/dashboard/adjustments` · `/alerts` · `/asns` · `/inventory` · `/refs` · `/throughput`.
      🔴 이 칸이 없으면 *"격리를 넣었다"* 만 확인하고 **계약의 나머지 절반(창고 전역 공개)은
      한 번도 행사되지 않는다.** 짝을 이루는 축에서 한쪽만 열려 있으면 거의 항상 결함이다

- [ ] **AC-5 (원소 0 의 귀속을 강제한다)**
      어떤 표면이든 0원소가 나오면 **격리 때문인지 프로젝션이 아직 안 왔는지**를 구별한다:
      `admin_order_summary` 를 **DB 에서 직접 읽어 행 존재를 먼저 확정**하고, 그 다음에 API
      원소 수를 판정한다. `TASK-BE-583` § Edge Cases 가 프로젝션이 원본보다 뒤쳐지는 것을
      실측했다(`outbound_order.status=PICKING` vs 프로젝션 `RECEIVED`)

- [ ] **AC-6 (기록)**
      결과를 이 티켓과 [`docs/guides/interview-demo-walkthrough.md`](../../../../docs/guides/interview-demo-walkthrough.md)
      원장에 반영.
      🔵 `TASK-BE-581` · `TASK-BE-583` 은 `done/` 이므로 **본문을 고치지 않는다**
      (review/done 파일 편집 금지) — 열린 칸이 닫혔다는 사실은 **이 티켓**에 적고 원장이 가리킨다

---

# Related Specs

- [`docs/adr/ADR-MONO-064`](../../../../docs/adr/ADR-MONO-064-wms-outbound-tenant-visibility-plane.md) — ACCEPTED — B. D1 이 생성 경로에 테넌트를 박고 **소급 stamp 를 금지**한다
- [`docs/adr/ADR-MONO-065`](../../../../docs/adr/ADR-MONO-065-wms-admin-read-plane-tenant-axis.md) — ACCEPTED — `B1` + `R1=a`. D1(2개 표면 격리) · D3(6개 표면 전역, 계약에 명문화)
- `projects/wms-platform/tasks/done/TASK-BE-581-*.md` § AC-3 — 열린 칸 ①
- `projects/wms-platform/tasks/done/TASK-BE-583-*.md` § AC-2 — 열린 칸 ②
- `infra/demo/projects.sh` — `COMPOSE[wms]` · `DEPS[wms]=iam` · `resolve_deps`
- `infra/demo/seed/README.md` — *"넣을 수 있는 것은 실제 API 로 넣는다"*, `dbexec --why` 게이트

# Related Contracts

- `projects/wms-platform/specs/contracts/http/admin-service-api.md` § 1.3 — 격리 2 / 전역 6 의 명문화(`TASK-BE-583` AC-2 산물)
- `projects/wms-platform/specs/contracts/events/outbound-events.md` § Tenant semantics
- `projects/platform-console/specs/contracts/console-integration-contract.md` — 콘솔이 읽는 8개 표면

# Edge Cases

- ⚠️ 🔴 **호스트 예산 — 인용되던 수치가 이 머신 것이 아니다.** 이 호스트는 **물리 15.7GB /
  커밋 한도 31.4GB** 다(2026-08-14 실측). `TASK-MONO-399` AC-2 의 *"31.5GB 중 여유 ~2.8GB"* 는
  **`free -m` 출력 = AWS 데모 인스턴스**이지 이 윈도우 호스트가 아니다(커밋 한도 31.4GB 와
  숫자가 겹쳐 혼동되기 쉽다). ⇒ 제약은 인용값보다 **더 빡빡하다**
- **판정 지표는 물리 여유가 아니라 커밋 차지다** — 이 호스트의 알려진 캐스케이드가
  커밋 고갈로 터진다(여유 <3GB 위험). `TASK-BE-581` AC-3 가 **92% 에서 중단**한 것이 그 이유다.
  기동 중 커밋을 계속 본다
- wms 7앱 ≈ **5.6GiB** (`TASK-BE-581` 2회차 실측)
- 🔴 **기존 볼륨의 DB 롤 비밀번호가 현재 `.env` 와 어긋나 전 서비스가 Flyway 단계에서
  크래시 루프**한 선례가 있다(`TASK-BE-581` AC-3 부수 발견 — 환경 드리프트). **리셋하면
  이 문제는 소멸한다**(새 볼륨이 현재 `.env` 로 초기화되므로). 리셋 없이 재려 했다면 이것부터
  밟았을 것이다
- 재시드는 `master-service` 읽기 모델이 먼저 차야 `POST /api/v1/outbound/orders` 가
  `422 PARTNER_INVALID_TYPE` 를 내지 않는다(`TASK-BE-581` 2회차 — **슬라이스 한계이지 제품 결함이 아니다**)
- `demo.env` 를 소스하지 않고 compose 를 손으로 부르면 `OIDC_ALLOWED_ISSUERS` 가 컨테이너
  기본값으로 굳어 **전건 401** 이 난다. 🔴 **401 은 도메인 판정이 아니라 "물어보지도 못했다"** 다
  (`TASK-BE-581` 2회차가 이것을 결함으로 적을 뻔했다)

# Failure Scenarios

- **리셋 전 AC-0 을 건너뛴다** → 차등의 한쪽이 영구히 사라진다. 리셋 후 숫자만으로는
  *"축이 화면까지 닿았다"* 를 *"원래 그랬다"* 와 구별할 수 없다
- **한 테넌트로만 잰다** → 필터의 유무와 무관하게 같은 결과가 나온다. 상수 비교다
- **격리 절반(2개)만 재고 "065 검증 완료" 로 기록** → 계약의 **6/8** 이 미검증인 채 닫힌다
- **HTML grep 으로 판정** → 콘솔은 클라이언트 렌더. 0건은 부재의 증거가 아니다
- **원소 0 을 격리로 귀속** → 프로젝션 미도달 · 컨슈머 사망 · 엔드포인트 오타가 **전부 같은 0** 을
  낸다. AC-5 가 막는다
- **`profile=full` 로 기동** → 커밋 고갈 → 이 호스트의 알려진 OOM 캐스케이드
- **검증 티켓에서 발견한 결함을 그 자리에서 고친다** → 검증과 수정이 한 커밋에 섞여, 무엇이
  통과를 만들었는지 구별되지 않는다. § Out of Scope 가 막는다

# Definition of Done

- [ ] AC-0 ~ AC-6 전부
- [ ] 결함 발견 시 **별도 fix 티켓** 등록(이 티켓에서 고치지 않는다)
- [ ] Ready for review
