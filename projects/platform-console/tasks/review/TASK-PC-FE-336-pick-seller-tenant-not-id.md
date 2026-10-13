# Task ID

TASK-PC-FE-336

# Title

콘솔 셀러 · 테넌트 ID 입력 6곳 — 셀러는 목록에서 고르고, 테넌트는 아는 테넌트를 제안 (묶음 ③)

# Status

review — 구현 완료(2026-10-13 UTC). AC-1~6 ✅(AC-1 의 실측 범위는 아래 그대로). 라이브 확인은 AC 밖(데모 창).

# Owner

platform-console

# Task Tags

- ux
- ecommerce-ops
- operator-groups
- pickers

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet 5 — 선택기 둘 + 화면 여섯 교체. 백엔드 변경 없음.

---

# Dependency Markers

- 선행 없음. 같은 계열: `TASK-PC-FE-334`(묶음 ①) · `TASK-PC-FE-335`(묶음 ②) — 목록 실패 시 기존 ID 입력으로 대체하는 같은 규칙.

# Goal

소유자 요청(2026-10-13 «모든 도메인», 묶음 ①→②→③)의 마지막 묶음.

| 화면 | 지금 | 쓸 읽기 |
|---|---|---|
| ecommerce 정산 `SellerBalanceLookup` — 셀러 잔액 조회 | 셀러 ID 입력 | `GET /api/ecommerce/sellers?page=0&size=100` |
| ecommerce `CommissionRateLookup` — 수수료율 조회 | 셀러 ID 입력 | 같음 |
| ecommerce `CommissionRateForm` — 수수료율 설정 | 셀러 ID 입력 | 같음 |
| ecommerce `AccrualsSection` — 정산 라인 필터의 셀러 | 셀러 ID 입력 | 같음(«전체 셀러» = 파라미터 안 보냄) |
| 운영자 그룹 `GroupForm` — 새 그룹의 테넌트 | 테넌트 ID 입력 | `GET /api/registry`(상단 테넌트 전환기와 같은 출처) |
| 운영자 그룹 `GroupGrantDialog` — tenant-assignment 테넌트 | 테넌트 ID 입력 | 같음 |

판단: 셀러는 서버 검색이 없고(`listSellers` = page · size 뿐) 수가 적다 → 한 페이지(최대 100) `<select>`, 더 많으면 «처음 N개만» 안내(결재자 선택과 같은 규칙). 테넌트 ID 는 사람이 읽는 slug 이고 호출자 권한 범위가 전환기 목록보다 넓을 수 있다(SUPER_ADMIN) → **자유 입력은 남기고** 아는 테넌트를 `<datalist>` 제안으로 붙인다.

# Scope

## In

- ecommerce-ops 안의 셀러 선택기 하나(예: `components/SellerSelect.tsx`) — «표시명 · sellerId · 상태», 목록 실패 시 같은 testid 의 ID 입력으로 대체. 4곳 교체. 요청에는 지금과 같은 `sellerId`.
- 테넌트 제안 — `/api/registry` 응답(`RegistryResponseSchema`, `@/shared/api/registry-types`)의 제공 중 제품 테넌트 합집합(상단 전환기의 `selectableTenants` 와 같은 규칙)을 `<datalist>` 로. 실패하면 제안 없이 지금 입력 그대로. `'*'` 는 제안하지 않는다(그룹은 플랫폼 전역 불가).
- 단위 시험.

## Out

- **정산 라인 필터의 주문 ID** — 주문 검색 읽기가 없다(주문 화면에서 복사해 오는 값). 그대로 둔다.
- **팬 소속사의 셀러 연결**(`AgencyDetail`) — 값이 사람이 읽는 slug(`acme-goods`)이고, 팬 화면은 팬 테넌트 토큰이라 ecommerce 셀러 목록을 부를 수 없다.
- **파트너십 초대의 상대 테넌트**(`PartnershipInviteForm`) — 상대는 정의상 **다른 회사**다. 다른 회사 테넌트 목록을 보여 주는 것은 운영자 «테넌트 배정»과 같은 보안 결정이라 소유자 몫.
- 백엔드 · 계약 변경(셀러 검색 조건 등).

# Acceptance Criteria

- [x] **AC-1** — 정산 4곳의 셀러 ID 입력이 셀러 선택기로 바뀌고, 조회 · 설정 요청에 **고른 `sellerId`** 가 기존 이름으로 간다. 실측: 화면 시험이 요청 URL · 본문을 단언. 실측 결과: 잔액 화면 — 실제 셀러 목록 렌더 → `acme-corp` 선택 → 요청 URL `/sellers/acme-corp/balance`(`ecommerce-settlements-screen.test.tsx`). 🟡 **목록에서 고르는 경로는 이 한 화면만 실측** — 수수료율 조회 · 설정 · 정산 라인은 기존 시험이 목록 응답을 주지 않아 같은 testid 의 ID 입력(대체) 경로로 지나며, 설정의 PUT 이 `.../commission-rates/acme-corp` 로 간다는 단언이 그 경로에서 통과. 네 화면이 같은 `SellerSelect`(같은 `value` · `onChange`)를 쓰므로 위험은 낮다고 판단.
- [x] **AC-2** — 정산 라인 필터에서 «전체 셀러»(미선택)면 `sellerId` 를 보내지 않는다. 실측: 시험. 실측 결과: 정산 라인 요청에 `orderId=ord-9` 는 있고 `sellerId=` 는 없음.
- [x] **AC-3** — 셀러 목록 실패 시 같은 testid 의 ID 입력으로 대체돼 흐름이 끝까지 된다. 실측: 시험(실패 모의). 실측 결과: 정산 시험 전부가 목록 실패 → 같은 testid 입력 경로로 통과 + 대체 안내 testid 단언.
- [x] **AC-4** — 그룹 생성 · 그룹 권한 부여의 테넌트 칸에 레지스트리 테넌트가 제안으로 붙고(`'*'` 제외), 자유 입력은 그대로 된다. 레지스트리 실패 시 제안 없이 동작. 실측: 시험. 실측 결과: `OperatorGroupsScreen.test.tsx` 신규 3(그룹 생성 — 포커스 시 제안 · `*` 없음 · 자유 입력 제출 · 레지스트리 실패 시 제안 없이 동작 / 권한 부여 — 제안) + `registry-tenant-suggestions.test.ts` 4.
- [x] **AC-5** — 각각 독립 실행 · 명시 rc: `vitest run` · `pnpm lint` · `tsc --noEmit` · `next build`. 실측 결과(구현 에이전트, 각각 독립 · build 는 vitest 뒤): `vitest run` rc=0 **370 파일 · 4207** · `pnpm lint` rc=0 · `tsc --noEmit` rc=0 · `next build` rc=0(경고는 기존 `@opentelemetry`).
- [x] **AC-6** — e2e 스펙이 바뀐 testid · 라벨을 쓰는지 grep 해 기록(쓰면 같이 고친다). 실측 결과: `tests/e2e/**` 에 바뀐 testid · 라벨 6종 0건.

# Related Specs

- `specs/services/console-web/architecture.md` — feature 간 import 금지. 레지스트리 스키마는 `shared/api/registry-types` 에 있다.

# Related Contracts

- ecommerce 셀러 목록(콘솔 `sellers-api.ts` — page · size, 최대 `SELLER_MAX_PAGE_SIZE`=100) · 정산 잔액 · 수수료율 · 정산 라인(`settlement-api.md`) — 소비만.
- 콘솔 레지스트리(`/api/registry`) — 소비만.

# Edge Cases

| 상황 | 기대 |
|---|---|
| 셀러 100개 초과 | 처음 N개 + 안내 |
| 셀러 0개 | 빈 선택기 + 안내 |
| 정지된 셀러 | 표시 · 선택 가능(조회 화면이다 — 생산자가 권위) |
| 레지스트리에 테넌트 없음 | 제안 없음, 자유 입력 |

# Failure Scenarios

| 실패 | 기대 |
|---|---|
| 셀러 목록 503 · 403 | ID 입력으로 대체 |
| 레지스트리 503 · 401 | 제안 없이 기존 입력 |

# Notes

- `features/ecommerce-ops/components/SellerSelect.tsx` — 기존 react-query 훅 `useSellers` 재사용(대상 화면 시험이 이미 `QueryClientProvider` 안).
- 테넌트 제안: `shared/lib/registry-tenant-suggestions.ts`(순수 함수 — `features/tenant` 의 `selectableTenants` 와 같은 규칙 + `*` 제외; feature 간 import 금지라 복사) · `features/operator-groups/hooks/use-tenant-suggestions.ts`(유일한 소비 feature 안 — `architecture.md` 의 `shared/` 배치에 `hooks/` 가 없다 · **포커스 시** 한 번 가져옴 — 마운트 즉시 부르면 «시드 목록은 추가 fetch 없이» 기존 단언이 깨진다).
- 기존 정산 시험의 잠복 결함 수리: `mockResolvedValue(jsonResponse(...))` 는 모든 fetch 에 **같은 Response** 를 돌려줘 두 번째 `.json()` 이 «body already used» 로 던진다 — 호출이 하나뿐일 땐 안 보였고 `SellerSelect` 가 두 번째 호출을 더하자 드러났다. 호출마다 새 Response(`mockImplementation`)로.
