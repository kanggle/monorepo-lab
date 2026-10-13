# Task ID

TASK-PC-FE-337

# Title

콘솔 SCM 공급사 ID 입력 2곳 — 공급사 목록에서 코드 · 이름으로 고르기 (묶음 ④)

# Status

review — 구현 완료(2026-10-13 UTC). AC-1~7 ✅. 라이브 확인은 AC 밖(데모 창). 제안 발주(코드 저장)를 공급사 선택으로 못 거르는 한계는 생산자 몫 — Out.

# Owner

platform-console

# Task Tags

- scm-ops
- scm-config
- ux
- pickers

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet 5 — 공용 읽기 하나(`shared/api`) + 같은 출처 프록시 + 선택기 두 곳. 백엔드 변경 없음.

---

# Dependency Markers

- 선행 없음. 같은 계열: `TASK-PC-FE-334` · `335` · `336`(묶음 ①②③). 이 묶음은 소유자 «확인»(2026-10-13) 요청으로 콘솔 전수 재검사를 한 결과 새로 찾은 칸이다.

# Goal

SCM 화면 두 곳이 공급사를 사람이 모르는 값으로 직접 치게 한다. 생산자에는 공급사 목록이 이미 있다(`GET /api/procurement/suppliers` — `{ id, code, name, status, … }` 페이지). 콘솔이 그것을 안 쓸 뿐이다.

| 화면 | 지금 | 생산자가 받는 값 |
|---|---|---|
| `features/scm-ops/components/ScmPoTable.tsx` 발주 목록 필터 «공급사 ID» | 직접 입력 | `GET /po?supplierId=` — **저장된 `supplierId` 와 정확히 같은 값**(procurement-api § `supplierCode` 6번) |
| `features/scm-config/components/SupplierMapForm.tsx` SKU 공급사 매핑 «공급사 ID» | 직접 입력(free-text/uuid) | **공급사 업무 코드**(demand-planning-api — wms 거래처 코드 · UUID 를 넣으면 wms 가 재시도 없이 거부, `TASK-MONO-683`) |

# Scope

## In

- 공급사 목록 서버 읽기를 `shared/api/` 에 둔다(두 feature 가 소비 — `architecture.md` § Forbidden Dependencies 승격 규칙). 기존 scm 서버 호출 경로(`features/scm-ops/api/scm-client.ts` 계열)와 같은 토큰 · 테넌트 처리를 쓴다.
- 같은 출처 프록시 `GET /api/scm/procurement/suppliers?page&size&status`.
- 매핑 폼: 공급사 선택 → **`code`** 를 `supplierId` 칸에 넣는다(표시 «코드 · 이름 · 상태»). 자유 입력은 남긴다(목록에 없는 거래처 코드도 계약상 유효) — 선택기 + «직접 입력» 또는 `<datalist>`(구현 판단, 근거 기록).
- 발주 필터: 공급사 선택 → **`id`** 를 `supplierId` 필터로 보낸다. «전체 공급사» = 파라미터 없음. 목록 실패 시 같은 testid 의 ID 입력.
- 🔴 발주 필터의 한계를 **화면 안내로** 적는다: 보충 제안에서 만든 발주는 공급사 **코드**로 저장돼 있어 공급사를 골라도 그 발주는 안 걸린다(생산자 필터가 저장값 정확 일치 — 콘솔만으로 못 고친다). 직접 입력으로 코드를 넣으면 찾을 수 있다는 것까지.
- 단위 시험.

## Out

- 생산자 `GET /po` 가 `supplierId` 를 «id 또는 code» 로 찾게 하는 변경 — 백엔드 + AMI 재굽기. 필요하면 별도 티켓(소유자 결정).
- 공급사 서버 검색(`code` 정확 일치만 있음) — 한 페이지(최대 크기는 생산자 확인) 안에서 고르고 «처음 N개만» 안내.

# Acceptance Criteria

- [x] **AC-1** — 매핑 폼에서 공급사를 고르면 저장 요청 본문의 `supplierId` 가 그 공급사의 **`code`** 다(id 아님). 실측: 시험이 PUT 본문을 단언. 실측 결과: `SeedConfigScreen.test.tsx` — datalist 선택지 value 가 공급사 code(id 아님)이고 확인 후 PUT 본문 `supplierId` 가 그 code.
- [x] **AC-2** — 발주 필터에서 공급사를 고르면 목록 요청에 `supplierId=<그 공급사 id>`, «전체» 면 파라미터 없음. 실측: 시험. 실측 결과: `ScmProcurementScreen.test.tsx` — `sup-2` 선택 → `supplierId=sup-2`, «전체» → 파라미터 없음.
- [x] **AC-3** — 공급사 목록 실패 시 두 화면 모두 기존 직접 입력으로 같은 흐름이 끝까지 된다. 실측: 시험. 실측 결과: 두 화면 각 1칸 — 목록 503 → 같은 testid 입력 · 입력값이 요청에 실림(매핑 폼은 `list` 속성 없이 PUT).
- [x] **AC-4** — 발주 필터에 «제안에서 만든 발주는 코드로 저장돼 공급사 선택으로 안 걸린다» 안내가 보인다. 실측: 시험이 문구를 단언. 실측 결과: `scm-po-filter-supplier-hint` 문구 단언.
- [x] **AC-5** — 프록시 시험: 쿼리 전달 · 오류 매핑(기존 scm 프록시와 같은 `mapScmError`). 실측 결과: `scm-proxy.test.ts` — page · size · status 전달, size 100 상한, 401/403/503/429 매핑.
- [x] **AC-6** — 각각 독립 실행 · 명시 rc: `vitest run` · `pnpm lint` · `tsc --noEmit` · `next build`(vitest 뒤). 실측 결과(구현 에이전트, 각각 독립 · build 는 vitest 뒤): `vitest run` rc=0 **377 파일 · 4261** · `pnpm lint` rc=0 · `tsc --noEmit` rc=0 · `next build` rc=0. 오케스트레이터 수정 뒤 재실행: SCM 관련 5 파일 · 102 rc=0 · tsc rc=0 · lint rc=0.
- [x] **AC-7** — e2e 스펙이 바뀐 testid · 라벨을 쓰는지 grep 해 기록. 실측 결과: `tests/e2e/*.spec.ts` 에 해당 testid · 라벨 0건.

# Related Specs

- `specs/services/console-web/architecture.md` — `shared/` 승격 규칙, server read 와 pure 타입 모듈 분리(next/headers).
- `specs/contracts/console-integration-contract.md` — scm 읽기 표면.

# Related Contracts

- scm `procurement-api.md` § `GET /api/procurement/suppliers`(소비만) · § `GET /api/procurement/po` 의 `supplierId` 필터 · § `supplierCode`(TASK-MONO-677).
- scm `demand-planning-api.md` § sku-supplier-map — `supplierId` = 공급사 업무 코드.

# Edge Cases

| 상황 | 기대 |
|---|---|
| 공급사가 한 페이지보다 많음 | 처음 N개 + 안내 |
| 정지된 공급사 | 표시 · 선택 가능(생산자가 권위) |
| 매핑의 기존 값이 목록에 없는 코드 | 그 값 그대로 보이고 저장 가능 |

# Failure Scenarios

| 실패 | 기대 |
|---|---|
| 공급사 목록 503 · 403 · 401 | 직접 입력으로 대체 |

# Notes

- 서버 읽기 `shared/api/scm-suppliers-read.ts`(server) + `scm-suppliers-types.ts`(순수 zod) — 두 feature 가 소비해 `shared/` 로 둔다. `logPrefix` 는 기존 `'scm'` 재사용: 새 값은 `sample-coverage-ledger.test.ts`(ADR-MONO-074)가 새 표면으로 세어 원장 행 · 샘플 고정값을 요구한다 — 같은 procurement-service 라 기존 표면에 `suppliersFixture()` 분기만 더했다.
- 페이지 상한 100 = 생산자 `PageQuery.MAX_SIZE`(`libs/java-common`), 기존 발주 목록 상한과 같다.
- 발주 필터 `SupplierFilterField` — 선택(값=id) + «직접 입력» 전환(같은 testid 입력). 🔴 오케스트레이터 리뷰 수정: 전환 시 값을 비운다 — 직접 입력한 코드가 선택지에 없어 선택 상자는 «전체 공급사» 로 보이는데 필터는 남는 **보이지 않는 필터**가 됐다. 시험 «switching back … clears a typed code» 추가 · bite: 수정을 빼면 `supplierId` 가 요청에 남아 실패(rc=1), 넣으면 통과(rc=0).
- 매핑 폼 — `SeedTextField.options` 를 `string | {value,label}` 로 넓혀 datalist 가 «code · 이름 · 상태» 를 보여 주되 **code 만** 넣는다(기존 통화 선택지는 바이트 동일). 제안은 포커스 시 한 번(`use-supplier-suggestions.ts`).
