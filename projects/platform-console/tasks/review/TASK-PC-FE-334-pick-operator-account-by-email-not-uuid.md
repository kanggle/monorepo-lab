# Task ID

TASK-PC-FE-334

# Title

콘솔 운영자 · 계정 · 사용자 ID 입력 5곳 — UUID 를 외워 치는 대신 이메일 · 이름으로 찾아 고르기 (묶음 ①)

# Status

review — 구현 완료(2026-10-13 UTC). AC-1~7 ✅. 라이브 확인은 AC 밖(데모 창). 제외한 운영자 «테넌트 배정»은 소유자 결정 대기.

# Owner

platform-console

# Task Tags

- ux
- operators
- accounts
- pickers

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet 5 — 공용 부품 둘 + 화면 다섯 교체. 백엔드 변경 없음(기존 읽기 API 재사용).

---

# Dependency Markers

- 선행 없음. 같은 계열 선례: `TASK-PC-FE-317`(운영자 그룹 «멤버 추가» — 운영자 목록을 받아 이메일 · 이름으로 거르는 패턴, `GroupMemberDialog` · `use-group-member-candidates.ts`), `TASK-PC-FE-333`(WMS 필터, UUID 모양 입력이면 «이 ID로» 탈출구).
- 후속: 묶음 ② ERP 직원 4곳 · 묶음 ③ 셀러 · 테넌트 6곳(별도 티켓).

# Goal

소유자 요청(2026-10-13 «모든 도메인», 묶음 ① → ② → ③ 순서): WMS 외 도메인에도 사람이 모르는 UUID 를 직접 치게 하는 입력이 18곳 있다. 이 티켓은 그중 **기존 읽기 API 만으로 고칠 수 있는 운영자 · 계정 · 사용자** 5곳이다.

| 화면 | 지금 | 사람이 아는 것 | 쓸 읽기 |
|---|---|---|---|
| 조직 계층 `OrgAdminPanel` — 노드 관리자 배정 | 운영자 ID 입력 | 이메일 · 이름 | `GET /api/operators`(활성 테넌트, 같은 출처 프록시) |
| 파트너십 `ParticipantManager` — 참여자 배정 · 해제 | 운영자 ID 입력 | 이메일 · 이름 | 같음(파트너 쪽 = 활성 테넌트 = 자기 운영자) |
| 감사 로그 `AuditFilterBar` — 계정 필터 | 계정 ID 입력 | 이메일 | `GET /api/accounts?email=`(정확 일치) |
| 팬 디렉터리 `ArtistCreateForm` — 아티스트 계정 | 계정 ID 입력 | 이메일 | 같음(활성 테넌트 = fan 테넌트) |
| ecommerce `CouponIssueDialog` — 발급 대상 | 사용자 ID 목록 입력 | 이메일 | `GET /api/ecommerce/users?email=`(부분 일치) |

판단(PC-FE-333 과 같다): 사람이 보는 칸은 이메일 · 이름, **API 에는 지금처럼 ID** 를 넘긴다.

# Scope

## In

- 공용 운영자 선택 부품(`shared/ui`) — 활성 테넌트 운영자 목록을 받아(`size=100`, 다음 페이지 «더 보기») 이메일 · 이름 부분 일치로 거른다(서버에 검색 조건 없음). 고르면 `operatorId` 를 넘기고 «이름 · 이메일» 로 보여 준다. UUID 모양 입력이면 «이 ID로 사용» 후보(목록 밖 운영자 — 노드 관리자의 reach 는 활성 테넌트보다 넓을 수 있다). 목록 조회 실패(403 · 503 · 네트워크) → 안내 + 기존 ID 직접 입력으로 대체.
- 공용 계정 이메일 찾기 부품(`shared/ui`) — 이메일 입력 + «찾기» → `/api/accounts?email=` → 0건 «현재 테넌트에 이 이메일의 계정이 없습니다» / 1건 이상 → 골라서 계정 ID 칸을 채운다. 실패 → `role="alert"` 안내. **기존 계정 ID 입력 칸은 남긴다**(감사 · 아티스트 둘 다 ID 를 이미 아는 경우가 있다 — 보조 수단).
- 쿠폰 발급 — «이메일로 찾아 추가»: 이메일 부분 입력 → `/api/ecommerce/users?email=&size=20` → 결과 행 «추가» 가 사용자 ID 를 목록에 넣는다(중복 제외). 기존 목록 입력은 남긴다(대량 붙여넣기).
- 단위 시험: 부품 둘 + 다섯 화면.

## Out

- **운영자 «테넌트 배정»(`AssignOperatorForm`)** — 배정 대상은 정의상 **현재 테넌트 밖** 운영자인데, 그를 이메일로 찾는 읽기가 없다(`GET /api/admin/operators/lookup` 은 조회 테넌트 **소속**만 · 반환도 `accountId`). 다른 테넌트 운영자 존재를 얼마나 드러낼지는 보안 결정이라 소유자 몫 — 이 티켓에서 다루지 않는다.
- ERP 직원(묶음 ②) · 셀러 · 테넌트(묶음 ③).
- 백엔드 · 계약 변경.

# Acceptance Criteria

- [x] **AC-1** — 조직 노드 관리자 배정 · 파트너십 참여자 배정/해제가 공용 운영자 선택 부품을 쓴다. 이메일 · 이름 일부로 거르고 고르면, 기존 요청에 **그 운영자의 `operatorId`** 가 간다. 실측: 화면 시험이 `onGrant` / `onOpen` 인자의 id 를 단언. 실측 결과: `OrgAdminPanel.test.tsx`(+1 — `onGrant` 에 고른 id, 사유 창에 이름(이메일)) · `ParticipantManager.test.tsx`(신규 3 — 배정 · 해제의 `onOpen.operatorId` · 미선택 시 버튼 비활성).
- [x] **AC-2** — 운영자 목록 조회 실패 시 안내 + ID 직접 입력으로 같은 흐름이 끝까지 된다. 실측: 부품 시험(실패 모의). 실측 결과: `OperatorPicker.test.tsx` — 403 → 안내 + 같은 testid 의 ID 입력 → `onChange` 에 입력 id.
- [x] **AC-3** — UUID 모양 입력이면 «이 ID로 사용» 후보가 나오고, 아니면 안 나온다. 실측: 부품 시험 양성 · 음성. 실측 결과: `OperatorPicker.test.tsx` — UUID 양성 · 음성 2칸.
- [x] **AC-4** — 감사 필터 · 아티스트 등록에서 이메일로 찾아 고르면 계정 ID 칸이 채워지고 기존 제출 경로 그대로 그 id 가 간다. 0건 · 실패는 각각 다른 문구(실패는 `role="alert"`). 실측: 부품 시험 3칸 + 화면 시험 2칸. 실측 결과: `AccountEmailLookup.test.tsx`(신규 5 — 선택 · 여러 행 테넌트 표시 · 0건 · 403 alert · 일반 실패 alert) · `AuditFilterBar.test.tsx` · `ArtistCreateForm.test.tsx`(POST 본문에 고른 accountId).
- [x] **AC-5** — 쿠폰 발급에서 이메일로 찾은 사용자를 추가하면 목록에 그 사용자 ID 가 들어가고(중복이면 한 번), 발급 요청 본문에 실린다. 이메일이 없는 사용자 행은 ID 로 표시. 실측: 화면 시험. 실측 결과: `CouponIssueDialog.test.tsx`(신규 4 — 본문 `userIds=[user-1]` · 중복 한 번 · 이메일 null 행은 id · 검색 실패는 검색 영역에만).
- [x] **AC-6** — 각각 독립 실행 · 명시 rc: `vitest run` · `pnpm lint` · `tsc --noEmit` · `next build`. 실측 결과(구현 에이전트, 각각 독립): `vitest run` rc=0 **375 파일 · 4218** · `pnpm lint` rc=0 · `tsc --noEmit` rc=0 · `next build` rc=0(경고는 기존 `@opentelemetry` 선택 의존). 그 전 두 번의 실행에서 보인 실패는 ⓐ 자기 코드 결함(아래 Notes) ⓑ `next build` 와 동시 실행한 부하 — 단독 재실행 22/22 · 최종 단독 전체 실행 0 실패.
- [x] **AC-7** — nightly e2e 스펙(`e2e/`)이 바뀐 testid · 라벨을 쓰는지 grep 해 기록한다(쓰면 같이 고친다). 실측 결과: `tests/e2e/**` 에 바뀐 testid 5종 0건 — 고칠 것 없음. 기존 testid(`org-admin-operator-input` 등)는 부품 접미사로 유지.

# Related Specs

- `specs/services/console-web/architecture.md` — 브라우저 → 같은 출처 프록시(`app/api/**`) → 백엔드, `features/A → features/B` import 금지(공용은 `shared/`).
- `specs/contracts/console-integration-contract.md` — 운영자 · 계정 · ecommerce 사용자 읽기 표면.

# Related Contracts

- iam `admin-api.md` § `GET /api/admin/operators`(소비만 — `tenantId` 기본 = 활성 테넌트, 검색 조건 없음) · § `GET /api/admin/accounts`(`email` = 정확 일치 단건).
- ecommerce `user-api.md` § admin users(`email` = 부분 일치).

# Edge Cases

| 상황 | 기대 |
|---|---|
| 활성 테넌트 운영자가 100명 초과 | «더 보기» 로 다음 페이지 — 거르기는 받은 것 안에서만(안내) |
| 노드 관리자 후보가 활성 테넌트 밖 | UUID 를 붙여 넣어 «이 ID로 사용» |
| 같은 이메일 계정 여러 행(SUPER_ADMIN `*`) | 행마다 테넌트를 같이 보여 주고 고르게 |
| 쿠폰 대상 사용자의 이메일이 null | 검색에 안 걸린다 — 목록 직접 입력이 남아 있다 |
| 고른 운영자를 지움 | 선택 해제 → 제출 버튼 비활성 |

# Failure Scenarios

| 실패 | 기대 |
|---|---|
| `/api/operators` 403(`operator.manage` 없음) · 503 | 안내 + ID 직접 입력(흐름 유지) |
| `/api/accounts` 403 `TENANT_SCOPE_DENIED` | «이 테넌트의 계정을 조회할 권한이 없습니다 — 계정 ID 를 직접 입력하세요» |
| `/api/ecommerce/users` 실패 | 검색 영역에만 오류, 목록 입력 · 발급은 그대로 |

# Notes

- `shared/ui/OperatorPicker.tsx` — react-query 를 쓰지 않는다(`WmsRefPicker` 와 같은 수동 fetch + `AbortController`): 기존 `OrgAdminPanel.test.tsx` 가 `QueryClientProvider` 없이 렌더하기 때문. 표시 라벨과 보고 id 를 분리 — 처음엔 «이 ID로 사용» 선택 시 라벨이 비는 결함이 있었고 부품 시험이 잡았다.
- `shared/ui/AccountEmailLookup.tsx` — 응답 행 스키마를 그 파일 안에서 `tenantId?` 로 넓힌다(공용 스키마 파일은 안 건드림 — 다른 소비자 둘은 그 칸이 필요 없다).
- `partnerships-actions.ts` 참여자 확인 문구가 `a.label`(이름 · 이메일, 모르면 id)을 쓴다 — 모를 때 출력은 이전과 같다.
- 쿠폰 «이메일로 찾아 추가» 는 같은 feature 의 `user-types.ts` 스키마를 쓴다(다른 feature import 없음). 이메일이 null 인 사용자는 검색에 안 걸린다 — 목록 직접 입력이 남아 있다.
