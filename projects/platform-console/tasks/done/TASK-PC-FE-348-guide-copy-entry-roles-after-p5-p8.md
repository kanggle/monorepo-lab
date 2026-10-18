# Task ID

TASK-PC-FE-348

# Title

가이드 문안이 `ADR-MONO-083` P5~P8 뒤 틀렸다 — «도메인 권한 = `*_OPERATOR` 하나 · 테넌트를 고르면 자동 부여» 문장을 세분 업무 역할 · 진입 역할 파생으로 고친다 (SCM · E-commerce · IAM 가이드)

# Status

done

# Owner

frontend

# Task Tags

- docs
- code
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (정적 문안 · 표 데이터 — 🔵 사용자에게 보이는 문장이라 소유자 검토 후 머지)
> 기안 2026-10-16 UTC — `TASK-PC-FE-342` ~ `345` 가 각 티켓에 «가이드 문안이 지금 틀림» 으로 적어 둔 것을 하나로 모은 후속(코디네이터 지시, `TASK-PC-FE-344` PR 에서 기안). 서버 · 신호 무변경.

# Dependency Markers

- 근거(이미 라이브, 28차 창 2026-10-16): `TASK-MONO-792`(SCM) · `793`(ERP) · `794`(Finance) · `795`(E-commerce) — 각 도메인 `iam-integration.md` § 업무 역할(정본).
- 형제(신호 · 버튼): `TASK-PC-FE-342` · `343` · `344` · `345` · `346` · `347`. 이 티켓은 **문안만** 고친다.

# Goal

P5~P8 뒤 SCM · ERP · Finance · E-commerce 의 운영 권한은 «테넌트를 고르면 자동으로 주어지는 `*_OPERATOR` 하나» 가 아니다. 업무 권한 세트로 **배정된** 영역별 조회/변경 역할이고, `*_OPERATOR` 는 그중 하나라도 있으면 auth 가 덧붙이는 **진입 역할**(배정 불가 · 단독으로 업무 API 를 열지 않음)이다. 정적 가이드가 옛 모델을 말하고 있어 방문자 · 운영자가 «세트를 줘도 안 되는» 이유를 가이드에서 찾을 수 없다.

# Scope

## In Scope (AC-0 에서 전수 재측정 — 아래는 2026-10-16 grep 결과)

| 파일 | 줄 | 지금 말하는 것 | 바뀌어야 할 것 |
|---|---|---|---|
| `apps/console-web/src/features/scm-guide/data.ts` | `:27` · `:363-369` | «단일 SCM_OPERATOR · 이 권한 하나뿐 · 보충 · 설정은 테넌트만 맞으면 열린다» | SCM 8 업무 역할(발주 · 수요계획 · 물류 · 재고 가시성 × 조회/변경) · `SCM_OPERATOR` = 파생 진입 역할 · 보충/설정 = 수요계획 역할 · 출고 «발송 재시도» = `SCM_LOGISTICS_WRITE` |
| `apps/console-web/src/features/ecommerce-guide/data.ts` | `:26` · `:553-558` · `:632` | «ECOMMERCE_OPERATOR 하나로만 열린다 · 테넌트를 고르면 자동 부여 · 7 화면이 똑같이» | 13 영역 역할(주문 · 상품(+판매자) · 회원(조회만) · 정산 · 프로모션 · 배송 · 알림) · 진입 역할 파생 · 화면마다 다른 역할 |
| `apps/console-web/src/features/iam-guide/data.ts` | `:139` · `:682-685` | 도메인 역할 목록 = `*_OPERATOR` 7 개 / 도메인별 `roles: '<X>_OPERATOR'` | 세분 4 도메인은 업무 역할(카탈로그) + «진입 역할 `*_OPERATOR` 는 파생» 문장, WMS · MES · fan 은 지금 카탈로그대로 |
| `apps/console-web/src/features/wms-guide/data.ts` | `:396` | «테넌트를 선택하면 WMS 재고 · 출고 권한이 자동으로 주어진다» | P3(`TASK-BE-632`) 뒤 = 배정된 세트 ∩ 구독 — «자동» 이 아니다 (WMS 는 P5~P8 밖이지만 같은 문장 모양) |
| (코드 주석 — 사용자에게 안 보임, 같은 PR 에서 정정 권장) | `ecommerce-ops/api/products-api.ts:150` · `ecommerce-ops/api/settlement-types.ts:12` | «role `ECOMMERCE_OPERATOR`» | 게이트웨이는 진입 역할, 서비스는 영역 역할 |

- ERP · Finance 가이드(`erp-guide` · `finance-guide`)는 역할 문장이 없다(grep 0) — AC-0 에서 «권한» 절이 필요한지만 판단(만들지 않으면 그 이유를 적는다).

### AC-0 재측정 결과 (2026-10-18 UTC, `features/**` + `src/**` 전수)

패턴: `(SCM|ERP|FINANCE|ECOMMERCE)_OPERATOR` · `자동으로\s*(부여|주어|붙|생)`(🔴 JSX 줄바꿈을 넘도록 multiline) · `하나뿐` · `테넌트만 맞으면` · `도메인 롤|구독 도메인` 문맥. 위 표(2026-10-16)보다 **넓다** — 티켓 패턴 `자동으로 (부여|주어)` 는 줄바꿈 · «자동으로 생깁니다» 를 놓쳤다.

| 파일 : 줄 (착수 시점) | 지금 말하던 것 | 처리 |
|---|---|---|
| `scm-guide/data.ts:26-27 · 363-369` | 단일 SCM_OPERATOR · 자동 · 하나뿐 · 보충/설정 테넌트만 | 고침 (주석 2 + `SCM_ROLE_NOTE`) |
| `ecommerce-guide/data.ts:26 · 551-558 · 632` | ECOMMERCE_OPERATOR 하나 · 자동 부여 · 7 화면 똑같이 | 고침 (주석 2 + `ECOMMERCE_ROLE_NOTE` + 용어집 `assume-tenant`) |
| `iam-guide/data.ts:137 · 139` | 「아무도 부여하지 않습니다 — 구독 도메인에서 자동으로 생깁니다」 · `*_OPERATOR` 7 개 | 고침 (`AUTH_PLANES[1]`) |
| `iam-guide/data.ts:677-687` | 도메인별 `roles: '<X>_OPERATOR'` | 고침 (`DOMAIN_ROLE_MAP` 4 행 = 업무 역할 전부 · WMS/MES/fan 행은 그대로) |
| `iam-guide/data.ts:293 · 345 · 704 · 708` | 「구독 도메인이 곧 직원의 도메인 운영 권한」 · 「자동으로 붙어」 · 「자동으로 생깁니다」 — **티켓 표에 없던 자리** | 고침 (같은 모양 — 두 곳이 서로 다른 말을 하는 Failure 2 방지). 온보딩 레시피에 «업무 권한 세트 배정» 단계 1 개 추가 |
| `iam-guide/components/IamGuideScreen.tsx:386 · 528-535 · 538 · 545` | 「구독 도메인에서 생기는 도메인 롤」 · 「아래처럼 자동으로 생깁니다」 — **표에 없던 자리** | 고침 (제목 · 설명 · 캡션 · 열 머리) |
| `wms-guide/data.ts:396` | 「WMS 재고·출고 권한이 자동으로 주어집니다」 | 고침 (용어집) |
| `wms-guide/components/WmsGuideScreen.tsx:207-208` | 「…선택하면 자동으로 주어집니다」 — **표에 없던 자리**(JSX 줄바꿈이라 grep 이 놓침) | 고침 |
| `ecommerce-ops/api/products-api.ts:150 · settlement-types.ts:12` (코드 주석) | role `ECOMMERCE_OPERATOR` | 고침 (게이트웨이 문 vs 서비스 영역 역할) |
| `app/api/ecommerce/products/[id]/route.ts:22` (코드 주석) | 「closed to `ECOMMERCE_OPERATOR` at the ecommerce gateway」 | **그대로** — 게이트웨이 문 얘기라 지금도 맞다 |
| `scm-guide/data.ts:174` | 「확정(확정에는 운영자 권한 필요)」 | **그대로** — 발주 확정의 운영자 행위자 판정은 792 도 무변경(scm § 업무 역할). 모호하면 소유자가 검토 |
| `shared/guide/permission-map.ts:294 · 318 · 336 · 388` | 진입 역할은 카탈로그에 없다는 영문 주석 | 이미 맞음 |
| `ecommerce-guide/data.ts:266` · 그 밖의 `하나뿐` 4 곳 | 배송 상태머신 · 데모 resolver 등 — 권한 문장 아님 | 해당 없음 |

**ERP · Finance 가이드의 «권한 안내» 절 — 만들지 않는다.** 이유: (1) 두 가이드의 «권한 안내» 탭은 `permissions: null` 이고 공용 `DomainGuideTabs` 가 «메뉴별 권한» 표와 «알아 둘 예외» 를 `permission-map.ts` 에서 **파생**해 그린다(`serverRequires` / `writeRequires` 가 이미 ERP · Finance 행에 있다 — 342~347). 산문 절을 손으로 더 쓰면 같은 사실이 두 사본이 된다. (2) 두 가이드에는 옛 모델을 말하는 문장이 0 이라 «틀린 문장» 이 없다 — 이 티켓의 범위는 틀린 문장을 고치는 것이다. (3) ERP · Finance 의 업무 역할은 IAM 가이드 «구독 도메인별 도메인 롤» 표에 이제 전부 적혀 있고, 거기서 진입 역할 설명도 한다. 산문 절이 필요하면(소유자 판단) 별도 티켓.
- 가이드 시험(`*GuideScreen.test.tsx`)의 해당 단언 갱신 · 문안이 `permission-map.ts` 데이터와 어긋나지 않는지 대조.

## Out of Scope

- 신호 · 버튼(`permission-map.ts` `serverRequires` / `writeRequires`) · 서버 판정 — 형제 티켓.
- `R__seed_demo_cs_operator.sql` 머리 주석(체크섬 — `TASK-MONO-795` 가 일부러 남김).

# Acceptance Criteria

- [x] **AC-0** — `features/**` 의 `(SCM|ERP|FINANCE|ECOMMERCE)_OPERATOR` · «자동으로 (부여|주어)» · «하나뿐» · «테넌트만 맞으면» 전수 grep(🔴 한글 패턴은 그룹으로 감싼다) · 위 표 갱신. — 증거: 위 «AC-0 재측정 결과». 티켓 표 4 곳 → 실제 고칠 자리 9 곳(IAM 가이드 5 곳 · WMS 화면 1 곳 추가 — multiline grep 으로 발견).
- [x] **AC-1** — 바뀐 문장은 각 도메인 `iam-integration.md` § 업무 역할의 말과 같다(역할 이름 · 무엇을 여는지). 진입 역할을 «배정하는 권한» 으로 적은 문장 0. — 증거: 문장별 전후 + 근거(`iam-integration.md` 줄)는 PR 본문의 검토 표(소유자 검토용). 역할 이름 · 개수(SCM 8 · ERP 4 · Finance 5 · E-commerce 13)는 시험이 `permission-map.ts` 카탈로그와 대조하고, 진입 역할 이름은 «진입 역할» 곁에서만 나오도록 시험이 강제한다.
- [x] **AC-2** — 가이드 시험 갱신 + 옛 문장이 돌아오면 빨개지는 단언 1 개 이상(예: SCM 가이드 본문에 «하나뿐» 없음). — 증거: `tests/unit/domain-guides-entry-role-copy.test.tsx`(신규 35 케이스) — 옛 문안에서 25 개가 **단언 실패**(red 커밋 `dfcb3fcd2`), 새 문안에서 통과. 전체 vitest 404 파일 · 4858 테스트 통과, `tsc --noEmit` rc=0, `next lint` rc=0(기존 경고 1 건만).
- [x] **AC-3** — 소유자 문안 검토(사용자에게 보이는 문장) — PR 본문에 바뀐 문장 전후를 표로.
  - ✅ 2026-10-18 UTC 소유자 승인(«두 곳 다듬고 머지»): ① SCM «발주 확정은 … 운영자 행위자 확인도 거칩니다» → «발주 확정은 발주 변경 역할(SCM_PROCUREMENT_WRITE)이 있어도 운영자 계정으로만 할 수 있습니다» (내부 용어 `isOperator` 제거) ② E-Commerce «화면에는 "접근 권한이 없습니다" 라는 안내가 나오며» 삭제(화면마다 문구가 달라 글자 그대로는 거짓). 나머지는 검토 표 그대로.
- [x] **AC-4** — 배포 상태(Vercel)를 따로 적는다.
  - ✅ 2026-10-18 UTC — Vercel `Production – kanggle-console` 배포 7154659816(`920d7975f`) state=success. 운영 `https://console.hubwang.com` 을 비로그인으로 열어 4 가이드 HTML 에서 확인했다. 새 문장: SCM «운영자 계정으로만 할 수 있습니다» · «배정하는 역할이 아니라 진입 역할입니다» / E-Commerce «화면마다 다른 업무 역할» · «업무 역할은 모두 13개입니다» / WMS «WMS를 구독한 회사(테넌트)를 선택하면 붙습니다» / IAM «업무 권한 세트를 배정합니다» · `FINANCE_LEDGER_CONTROL` — 각 2 건(HTML 본문 + RSC 페이로드). 옛 문장: «이 권한 하나뿐입니다» · «운영자 행위자 확인» · «하나로만 열립니다» · «라는 안내가 나오며» · «자동으로 주어집니다» · «구독 도메인에서 자동으로 생깁니다» — 각 0 건. «0 건» 은 같은 파일에서 새 문장이 잡혔으므로 빈 페이지에서 나온 0 이 아니다.

# Related Specs

- `docs/adr/ADR-MONO-083-domain-permission-sets-per-tenant.md` P5 ~ P8
- `projects/{scm,erp,finance,ecommerce-microservices}-platform/specs/integration/iam-integration.md` § 업무 역할

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.3.4 (진입 역할은 선언하지 않는다)

# Edge Cases

- 데모 운영자는 legacy-compat 세트로 모든 역할을 받는다 — «데모에서는 다 열린다» 와 «역할은 배정된다» 를 함께 적어야 거짓이 아니다.
- 샘플 방문자(비로그인)에게도 보이는 정적 문안이다 — 내부 파일 경로 · 서비스 클래스 이름을 본문에 넣지 않는다.

# Failure Scenarios

1. 문안을 위임받은 쪽이 역할 이름 · 항목 수를 바꾼다(«쉽게 다시 쓰기» 가 사실을 흘린다) — AC-1 대조.
2. 한 가이드만 고치고 IAM 가이드의 도메인 역할 표는 그대로 — 두 곳이 서로 다른 말을 한다.

# 닫기 (2026-10-18 UTC) — in-progress → done

- (a) `gh pr view 4409`: `state=MERGED`, `mergeCommit` `920d7975f`.
- (b) `920d7975f` 는 `origin/main`(`b74e05ab7`)의 조상이다.
- (c) 머지 직전 `gh pr checks 4409`: pass 13 · fail 0 · pending 0. console-web 전체 vitest 레인은 pass 였다.
- (d) AC-0~AC-4 를 열어 확인했다. 전부 ✅ — AC-3 = 소유자 승인(두 곳 다듬음), AC-4 = 위 운영 화면 실측.
- `review/` 단계는 거치지 않고 바로 `done/`: 구현 PR 은 머지 시점에 검토됐다(문안은 소유자가 검토). 마지막 AC 는 배포 상태 기록뿐이었다.
