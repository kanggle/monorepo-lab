# Task ID

TASK-PC-FE-333

# Title

콘솔 WMS 입고 · 재고 · 출고 필터 — 창고 · 공급처 · SKU · 위치 · 로트를 UUID 입력 대신 코드 · 이름으로 골라서 조회

# Status

review — 구현 완료(2026-10-12 UTC). AC-1~7 ✅. 선행 `TASK-BE-630` 머지됨(#4313 `8338dda8a`) — 데모 백엔드 반영은 다음 AMI 재굽기, 그 전에도 AC-4 로 데모 규모에서 옳다.

# Owner

platform-console

# Task Tags

- wms-ops
- ux
- filters

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet 5 — 공용 선택 부품 하나 + 필터 세 화면 교체. 🔴 백엔드 검색(TASK-BE-630)이 데모에 닿기 전(AMI 재굽기 전)에도 옳아야 한다.

---

# Dependency Markers

- 선행: `TASK-BE-630`(wms admin-service `GET /dashboard/refs/{type}` 에 `q` · `status` · `warehouseId` · `skuId` · `partnerType` 구현). 🔴 **데모 백엔드는 구운 AMI 에서 돈다** ⇒ BE-630 은 머지만으로는 데모에 안 닿는다(다음 재굽기). 이 티켓의 콘솔은 머지 즉시 Vercel 에 배포된다 ⇒ 그 사이 서버는 `q` 를 무시하고 전체 첫 페이지를 준다 — 아래 AC-4 가 그 창을 덮는다.

# Goal

소유자 요청(2026-10-12): 콘솔 WMS 필터가 사람이 모르는 UUID 를 직접 입력하게 한다. 결과 목록에는 이미 창고 코드 · 공급처 이름 · SKU 코드 · 위치 코드 · 로트 번호가 보이는데 그것으로는 조회할 수 없다.

| 화면 | 지금 (UUID 텍스트 입력) |
|---|---|
| `/wms/inbound` `WmsInboundFilters` | 창고 ID · 공급처 ID |
| `/wms/inventory` `WmsInventoryFilters` | 창고 ID · SKU ID · 위치 ID · 로트 ID |
| `/wms/outbound` `WmsShipmentsFilters` | 창고 ID |

판단(소유자에게 보고한 것): 사람이 고르는 칸은 코드 · 이름으로, **API 에는 지금처럼 ID 를 넘긴다**(코드 · 이름은 바뀔 수 있고 ID 는 안 바뀐다). ID 는 화면 뒤에서만.

# Scope

## In

- `features/wms-ops/api` — `RefQueryParams` 에 `warehouseId` · `skuId` · `partnerType: string[]` 추가, `listRefs` 직렬화(`partnerType` 반복), `/api/wms/master/refs/[type]` 프록시가 그 셋을 전달(`partnerType` 는 `getAll`).
- 공용 선택 부품(예: `WmsRefPicker`) — 입력하면 `/api/wms/master/refs/{type}` 를 호출해 «코드 · 이름» 후보를 보여 주고, 고르면 **id** 를 필터 상태에 넣는다. 고른 값은 코드 · 이름으로 표시, 지우기 가능. 접근성: combobox/listbox ARIA, 키보드 선택.
- 교체: 입고(창고 · 공급처 — `partnerType=SUPPLIER&partnerType=BOTH`) · 재고(창고 · SKU · 위치 — 창고를 골랐으면 `warehouseId` 로 좁힘 · 로트 — SKU 를 골랐으면 `skuId` 로 좁힘) · 출고(창고).
- 기존 단위 시험(`WmsInboundScreen.test.tsx` · `WmsInventoryScreen.test.tsx`)을 새 부품에 맞게 갱신 + 부품 단위 시험.

## Out

- 백엔드 계약 · 구현 — `TASK-BE-630`.
- 입고예정 번호 · 운송장 번호로 찾기(백엔드 조회 조건 추가 필요 — 별도).
- `/wms/master` 화면 자체(그 검색어 칸은 BE-630 이 데모에 닿으면 저절로 동작한다).

# Acceptance Criteria

- [x] **AC-1** — 세 화면의 ID 텍스트 입력 6종이 사라지고, 코드 · 이름으로 고르는 부품(`WmsRefPicker`)이 그 자리에 있다. 조회 요청에는 **고른 항목의 id** 가 기존 파라미터 이름으로 간다. 실측: `WmsInboundScreen.test.tsx` · `WmsInventoryScreen.test.tsx` 가 목록 요청 URL 의 id 를 단언.
- [x] **AC-2** — 공급처 후보는 `partnerType=SUPPLIER&partnerType=BOTH`, 위치는 고른 창고, 로트는 고른 SKU 로 좁혀 요청한다. 실측: 부품 시험 3칸 + 화면 시험. **+ 범위가 바뀌면 이전 범위 후보를 버리고 다시 가져온다**(아래 Notes ①).
- [x] **AC-3** — 지우기 · 다시 타이핑 시 `onChange(undefined)` → 그 필터가 요청에서 빠진다. 실측: 부품 시험 2칸.
- [x] **AC-4** — 서버가 `q` 를 무시해도 후보가 입력과 맞는다(클라이언트 코드 · 이름 부분 일치). **+ 범위(`partnerType` · `warehouseId` · `skuId`)도 클라이언트에서 거른다**(Notes ②). 실측: 부품 시험 3칸(모의 응답이 무관한 행 · 고객사를 섞어 줌).
- [x] **AC-5** — UUID 모양 입력이면 «이 ID로 조회» 후보. 실측: 부품 시험 2칸(양성 · 음성).
- [x] **AC-6** — 조회 실패 시 `role="alert"` 오류 행(빈 목록 아님), 다른 필터는 각자 상태라 그대로. 실측: 부품 시험.
- [x] **AC-7** — 각각 독립 실행 · 명시 rc: `vitest run` rc=0 (**369 파일 · 4195 시험 통과**) · `pnpm lint` rc=0 · `tsc --noEmit` rc=0 · `next build` rc=0(경고는 기존 `@opentelemetry` 선택 의존 · worktree 의 두 lockfile 로 인한 workspace root 추정 — 이 변경 파일과 무관, 로그 grep 0건).

# Related Specs

- `specs/services/console-web/architecture.md` — `features/wms-ops`, 브라우저 → same-origin 프록시 → wms(직접 호출 금지).
- wms `specs/contracts/http/admin-service-api.md` § 1.7(TASK-BE-630 판).

# Related Contracts

- wms admin-service § 1.7 (`GET /dashboard/refs/{type}`) — 소비만. 응답 모양: `{ id, code, name, status, attributes }` 페이지.

# Edge Cases

| 상황 | 기대 |
|---|---|
| 창고를 바꿨는데 위치가 다른 창고 것 | 위치 선택을 해제(범위 밖 조합 방지) |
| SKU 를 해제 | 로트 선택도 해제 |
| 후보 0건 | «일치하는 항목 없음» |
| 입력이 UUID 형식 | «이 ID 로 조회» 후보 제공 |
| 이름 없는 타입(위치 · 로트) | 코드만 표시 |

# Failure Scenarios

1. 서버 필터만 믿는다 → AMI 재굽기 전 데모에서 입력과 무관한 첫 20행이 후보로 뜬다(AC-4).
2. 코드를 그대로 API 에 넘긴다 → 백엔드는 UUID 만 받아 400. 반드시 id 로 변환.
3. 조회 실패를 빈 목록으로 삼킨다 → «그런 창고 없음» 으로 오해(AC-6).

# Implementation Notes

- 구현은 frontend-engineer 에이전트(Sonnet). 완료 보고 뒤 **diff 를 직접 읽어** 보고에 없던 결함 둘을 찾아 고쳤다:
  - ① **범위가 바뀌어도 후보를 다시 안 가져왔다** — 열 때 `rows.length === 0` 일 때만 조회해서, 창고를 바꾼 뒤 위치 칸을 열면 **이전 창고의 위치**가 그대로 떴다(AC-2 위반). `scopeKey` 가 바뀌면 진행 중 요청을 끊고 후보를 비우고, 열려 있으면 다시 가져오게 했다.
  - ② **AC-4 대비가 `q` 에만 걸려 있었다** — 재굽기 전 서버는 `warehouseId` · `skuId` · `partnerType` 도 무시하므로 공급처 칸에 **고객사**가, 위치 칸에 **모든 창고의 위치**가 뜰 수 있었다. 행의 `attributes.*` 로 클라이언트에서도 범위를 거른다(속성이 없는 행은 남긴다 — 서버가 권위).
  - 주석의 «300ms» 를 실제 상수 이름으로(코드는 200ms).
- **bite**: 원본을 scratchpad 에 복사한 뒤 ② 를 끄면(`scoped = rows`) 부품 시험 16 중 **정확히 그 칸 1개**, ① 을 끄면(재설정 effect 조기 return) **그 칸 1개**가 빨강. 원본 복원 바이트 일치 확인.
- 한계(문서화): 재굽기 전에는 서버가 준 **첫 페이지(20행, 최신순)** 안에서만 클라이언트가 거를 수 있다 — 데모 기준정보(창고 1 · 거래처 4 · SKU 3 · 위치 4)에는 충분하고, 서버가 거르기 시작하면 사라진다.
- worktree 의 `node_modules` 는 `pnpm install --frozen-lockfile` 로 만든 **실제 디렉터리**(링크 아님, `LinkType` 빈 값 확인) — 정리 때 저장소 밖을 건드리지 않는다.
