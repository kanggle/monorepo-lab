# Task ID

TASK-PC-FE-298

# Title

전역「가이드」메뉴 + 도메인별 가이드 확장 + 권한·기능 매핑 문서(샘플 방문자 열람 가능)

# Status

done

# Owner

frontend

# Task Tags

- code
- frontend
- console-web
- test

---

# Dependency Markers

- **선행 (prerequisite)**: `TASK-PC-FE-297` — 같은 파일 `console-nav-config.ts` 를 건드린다. **297을 먼저, 같은 worktree에서 297 → 298 순서로 직렬 진행**한다(공유 파일 시리즈 규율, `CLAUDE.md`). 297이 아직 `review`/`done` 이 아니면 이 티켓에 먼저 착수하지 않는다.

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus 5.5 — 매핑 표는 여러 소스(nav config·rbac.md·컨트롤러 어노테이션)를 정합시키는 교차검증 작업이고, 드리프트 감시 테스트까지 설계해야 한다.

---

# Goal

콘솔에 **전역 1뎁스「가이드」메뉴**를 신설해 시스템 아키텍처·서버 구성·전체 메뉴 소개 등 7개 탭을 담고, 기존 6개 도메인 가이드 화면 각각에 8개 탭(도메인 설명/용어/사용 가이드/메뉴별 설명/메뉴별 절차/권한 안내/업무 흐름/연결 서비스)을 추가한다. 모든 nav 항목을 커버하는 **권한·기능 매핑 표**를 만들고, nav href가 매핑 표에 없으면 실패하는 테스트로 드리프트를 막는다. 이 콘텐츠는 로그인 없이도(샘플 방문자) 보여야 한다.

# Scope

## In Scope

- **전역 가이드 메뉴**(1뎁스, 신설): 탭 7개 — 시스템 아키텍처 / 도메인별 서비스 구성 / 서버 구성(Vercel·AWS·백엔드·인증 서버·데이터베이스) / 전체 메뉴 소개 / 권한 및 테스트 계정 / 대표 업무 흐름 / 서버 기동·종료 방식. 순수 정적 콘텐츠(백엔드 호출 없음).
- **도메인별 가이드 확장**: `(console)/{iam,wms,scm,finance,erp,ecommerce}/guide/page.tsx` 6개 각각에 탭 8개 — 도메인 전체 설명 / 공통 정의 및 용어 / 도메인 사용 가이드 / 메뉴별 설명 / 메뉴별 사용 절차 / 권한 안내 / 대표 업무 흐름 / 연결 서비스. **먼저 이 6개 파일이 지금 이미 담고 있는 내용을 읽고 재사용**한다 — 처음부터 다시 쓰지 않는다.
- **권한·기능 매핑 표**: 열 = 메뉴 뎁스 / 메뉴명 / 라우트 / 권한 코드 / 설명 / 조회·생성·수정·삭제 / 용도 / 연결 서비스 / 비로그인 데모 가능 여부(`src/shared/sample/coverage.ts` 의 커버리지 원장 참조) / 테스트 계정 접근 가능 여부. **모든** nav 항목을 커버한다.
  - 소스 오브 트루스: `console-nav-config.ts`(항목 목록) + `projects/iam-platform/specs/services/admin-service/rbac.md:62-112`(역할×권한 매트릭스, 2026-09-24 UTC 확인 — Seed Matrix 표가 :100-114) + 각 컨트롤러의 `@RequiresPermission` 어노테이션(실제 게이트).
  - 표 내용은 **파생**이어야 한다(추측 금지) — 각 행이 어느 소스에서 왔는지 확인 가능해야 한다.
  - **드리프트 방지 유닛 테스트**: nav href가 매핑 표에 행이 없으면 실패하는 테스트를 추가한다.
  - **알려진 불일치를 기록**: 「권한」/「권한 세트」 화면은 `operator.manage` 로 게이트된다(`rbac-catalog.ts:34-36`, `rbac.md:80` 부근) — 표에서 이 불일치(또는 특수 케이스)가 드러나도록 명시적으로 적는다.
- **샘플 방문자 열람**: 가이드 콘텐츠는 ADR-MONO-074 샘플 모드(`isSampleVisitor()`, `session.ts:297-303` 부근)에서도 보여야 한다 — 순수 정적 콘텐츠이므로 백엔드 호출 없이 이 조건을 만족해야 한다.
- **내용 정확성 규율**: 인프라(Vercel/AWS/Lambda/번들)에 대한 모든 사실 서술은 저장소 파일을 인용해야 한다 — 근거 없는 서술 금지.

## Out of Scope

- 메뉴 순서/아이콘/파비콘 — `TASK-PC-FE-297`(선행 완료 전제).
- 권한에 따른 화면 숨김/노출 로직 변경 — 매핑 표는 **문서**이지 게이트 로직을 바꾸지 않는다.
- IAM `rbac.md` 자체의 권한 정의 변경 — 이 티켓은 그 문서를 **읽기만** 한다.

# Acceptance Criteria

- [ ] **AC-1** — 전역「가이드」메뉴가 1뎁스에 있고 7개 탭이 모두 존재하며 로그인 없이 렌더된다(샘플 방문자 상태에서 확인).
- [ ] **AC-2** — 6개 도메인 가이드 화면 각각에 8개 탭이 있고, 기존 콘텐츠가 재사용됐다는 것을 diff로 확인할 수 있다(완전 재작성이 아니라 재구성임을 보인다).
- [ ] **AC-3** — 권한·기능 매핑 표가 모든 nav 항목을 커버한다. 드리프트 방지 테스트가 존재하고, nav에 항목을 추가했는데 매핑 표에 행을 안 넣으면 그 테스트가 실패함을 bite로 확인한다(추가 → RED → 행 추가 → GREEN).
- [ ] **AC-4** — 매핑 표의 각 행이 `console-nav-config.ts` / `rbac.md` / 컨트롤러 어노테이션 중 어디서 파생됐는지 추적 가능하다(각주 또는 소스 컬럼).
- [ ] **AC-5** — 「권한」/「권한 세트」 화면의 `operator.manage` 게이트 불일치가 표에 명시적으로 기록돼 있다.
- [ ] **AC-6** — 인프라 관련 서술 전부가 저장소 파일을 인용한다(임의 서술 0건 — 재그렙으로 확인).
- [ ] **AC-7** — 유닛 테스트 + typecheck + build 통과. 브라우저로 전역 가이드 + 도메인 가이드 최소 1개를 확인한다.

# Related Specs

- `projects/platform-console/apps/console-web/src/shared/ui/console-nav-config.ts`
- `projects/platform-console/apps/console-web/src/app/(console)/{iam,wms,scm,finance,erp,ecommerce}/guide/page.tsx`
- `projects/iam-platform/specs/services/admin-service/rbac.md` § Seed Matrix (:62-118 부근)
- `projects/platform-console/apps/console-web/src/shared/sample/coverage.ts`
- `projects/platform-console/apps/console-web/src/shared/lib/session.ts`(`isSampleVisitor()`)
- ADR-MONO-074 (샘플 방문자 모드)
- 2026-09-24 UTC 포트폴리오 UX 전수조사(소유자 승인)

---

# Related Contracts

- 없음(정적 문서 + 프런트엔드 전용, 백엔드 API 무변경).

---

# Target App

- `apps/console-web`

---

# Edge Cases

- rbac.md 가 이후 변경되면 매핑 표가 낡을 수 있다 — 드리프트 테스트는 **nav 항목 누락**만 잡고 권한 코드 자체의 최신성까지는 자동으로 못 잡는다는 한계를 문서에 기록한다.
- 도메인 가이드 8개 탭 중 일부는 콘텐츠가 빈약할 수 있다(예: 연결 서비스가 명확하지 않은 도메인) — 빈 섹션을 "정보 없음"으로 정직하게 표시하고 지어내지 않는다.

# Failure Scenarios

- 매핑 표 내용을 추측으로 채운다 — rbac.md/컨트롤러를 안 읽고 "아마 이럴 것" 으로 적으면 이 티켓의 존재 이유(정확한 권한 안내)가 무너진다.
- 드리프트 테스트가 없거나 항상 통과하도록 설계되면(예: nav 항목을 안 읽고 하드코딩된 리스트만 검사) 다음 nav 변경에서 매핑 표가 조용히 낡는다.
- 297이 아직 review 이전인데 298을 시작해 `console-nav-config.ts` 에서 병합 충돌이 난다.

---

# Implementation Record (2026-09-24 UTC)

> 구현=Opus 5.5(서브에이전트). worktree `ml-wt-b-297`, 브랜치 `feat/pc-fe-297-nav-guide`. 선행 297 은 같은 worktree 에서 먼저 커밋(`9f5afdd07`)하고 review 로 옮긴 뒤 착수 — 직렬.

## 변경 파일

- 신규 `src/shared/guide/permission-map.ts` — **권한·기능 매핑 표**(nav leaf 48개 전부 1행) + `RBAC_SEED_MATRIX`(rbac.md:98-114 사본) + `DEMO_TEST_ACCOUNT`(데모 시드). 메뉴명·뎁스·경로는 `GROUPS` 에서, 비로그인 데모 여부는 `coverage.ts` `screenStatusFor()` 에서, 테스트 계정 접근은 매트릭스×시드에서 **계산**(손으로 적지 않음).
- 신규 `src/shared/guide/PermissionMapTable.tsx`(표 · 메뉴별 설명 · 메뉴별 사용 절차 · 불일치 목록), `src/shared/guide/DomainGuideTabs.tsx`(8탭 골격), `src/shared/ui/guide-tabs.tsx`(WAI-ARIA 탭: 화살표/Home/End, roving tabindex, `#탭id` 딥링크, 비활성 패널도 DOM 에 `hidden` 으로 렌더).
- 신규 `src/features/global-guide/`(data · GlobalGuideScreen · index) + `src/app/(console)/guide/page.tsx`. nav 1뎁스 `nav-global-guide`(최상위 첫 항목 — 297 의 가이드-먼저 원칙), `coverage.ts` `'/guide': 'static'`.
- 도메인 가이드 6개(`{iam,wms,scm,finance,erp,ecommerce}-guide/components/*GuideScreen.tsx`) — 기존 섹션을 8탭으로 **이동**, 페이지 내 목차(GuideToc) → 탭 목록, 소개/읽기경로 문구의 「맨 아래 참조」 류를 탭 이름으로 갱신.
- `features/iam-guide/data.ts` — `group.manage` 를 SUPER_ADMIN · TENANT_ADMIN · ORG_ADMIN 과 권한 키 목록에 추가(rbac.md:90,94,96,114). 매핑 표를 rbac.md 와 대조하는 새 테스트가 **IAM 가이드가 이 키를 빠뜨리고 있던 것**을 찾았다.
- `specs/services/console-web/architecture.md` — 트리에 `features/global-guide/` · `shared/guide/` · `shared/ui/guide-tabs.tsx` · `console-nav-icons.tsx` 등재.
- 테스트: 신규 `permission-map-drift.test.ts`(11) · `GlobalGuideScreen.test.tsx`(9) · `tests/helpers/{domain-guide-tabs,cited-path}.ts`; 6개 `*GuideScreen.test.tsx` 에 8탭 단언 추가(WMS 는 옛 TOC 단언을 대체).

## AC 별 판정

- **AC-1** ✅ — 1뎁스 「가이드」(`/guide`), 7탭(테스트가 이름·순서 단언). 쿠키 0개의 브라우저(프로덕션 빌드)로 `/guide` 200, 샘플 배너 표시, 외부 요청 0건·페이지 오류 0건(1280/400 둘 다). 단위 테스트가 `fetch` 호출 0회 단언.
- **AC-2** ✅ — 6개 가이드 각각 8탭(공용 골격이라 이름·순서가 갈라질 수 없다). 재구성 증거: `git diff -w --stat` 에서 6개 화면 합계 +1004/−946 — 줄이 거의 같은 수만큼 **옮겨졌고**, 공백 포함 diff(+2340/−2282)의 대부분은 탭 패널 안으로 들어가며 생긴 들여쓰기다. 각 가이드 테스트가 기존 섹션 id 가 **기대한 탭 패널 안에** 있음을 단언. 「메뉴별 설명」·「메뉴별 사용 절차」는 매핑 행에서 생성(손으로 쓴 단계 0 — 화면에 없는 버튼을 지어내지 않기 위해). WMS·IAM 은 연결 서비스를 따로 기록한 섹션이 없어 매핑 행의 서비스를 모아 보여준다(파생); 권한 서술이 없는 Finance·ERP 는 「권한 안내」에 매핑 표만 나온다. 저장소가 말하지 않는 탭은 「정보 없음」 컴포넌트로 정직하게 표시하는 경로를 두었다(현재 6개 가이드에서 실제로 비는 탭은 없다).
- **AC-3** ✅ — 드리프트 테스트는 `GROUPS` 를 직접 펴서 매핑 표와 **양방향**(누락·고아·중복) 대조, 하한 48로 공허 통과 차단. **bite**: `/bite-probe` nav 항목을 임시로 추가 → `permission-map-drift` **RED**(2 failed: «nav hrefs without a permission-map row: /bite-probe») → 행 추가 → **GREEN**(11/11) → 두 파일 원복(md5 일치 확인). 첫 bite 시도에서 행을 `public` 게이트로 넣자 다른 가드(«public 행은 샘플 원장에 static 이어야 한다»)가 빨개졌다 — 그것도 옳게 문 것이라 probe 행을 `operator` 게이트로 바꿔 다시 쟀다.
- **AC-4** ✅ — 모든 행에 `sources`(파일:줄), 표에 「근거」 열. 테스트가 **인용 경로의 실재**를 검사한다 — 이 검사가 첫 실행에서 틀린 인용 1건(`TenantAdminController` 는 `presentation/tenant/` 아래)을 실제로 잡았다. IAM 권한 키는 `RBAC_SEED_MATRIX` 에 존재해야 하고, 그 매트릭스는 IAM 가이드 `SEED_ROLES` 와 일치해야 한다(두 독자가 rbac.md 를 다르게 읽을 수 없게).
- **AC-5** ✅ — 「권한」·「권한 세트」 행에 불일치 명시: 읽기 전용 화면이 관리 키 `operator.manage` 로 게이트(rbac.md:80, TASK-BE-486) → SUPPORT_READONLY 는 카탈로그를 못 본다. 함께 기록한 특수 케이스: 「계정 운영」 목록 GET 은 애노테이션이 아니라 인라인 `account.read` 검사 + 내보내기=audit.read · GDPR 삭제=account.lock, 「파트너십」은 SUPER_ADMIN 도 `partnership.manage` 가 없어(rbac.md:112) 데모 계정이 403.
- **AC-6** ✅ — 인프라 사실(아키텍처 5 · 서버 5층 12 · 기동 4 · 종료 4 · 흐름 5 · 서비스 구성 7)이 전부 `sources` 를 갖고, 테스트가 인용 0건 항목과 **존재하지 않는 인용 경로**를 모두 빨갛게 만든다(재그렙의 기계화). 서비스 목록은 각 프로젝트 `apps/` 디렉터리와 1:1 대조. 🔵 한계: 탭 머리의 한두 줄 요약 문장(예: 「화면(Vercel)과 데이터(AWS)는 다른 곳에서 돈다」)은 바로 아래 카드의 인용된 사실을 되풀이한 것이라 자체 인용이 없다. 인용된 **줄의 내용**이 서술과 맞는지는 기계가 못 잰다 — 작성 시 각 줄을 열어 대조했다.
- **AC-7** ✅ — `tsc` rc=0 · `next lint` rc=0 · 전체 vitest **319 파일 / 3593 케이스 전부 통과**(이번 실행은 타임아웃 0) · `next build` rc=0(`/guide` 라우트 생성). 브라우저(쿠키 없음, 샘플 방문자): 전역 가이드 4개 탭 + WMS 가이드 3개 탭 + IAM 가이드 해시 딥링크(`#iam-guide-tab-permissions` → 선택됨), 1280·400px.

## 결정 · 편차

- **샘플 방문자에게 비밀번호는 보여주지 않는다** — 테스트 계정 탭은 이메일만(데모 배포의 로그인 화면·론처가 이미 표시). 이메일은 `DemoLoginCredentials.tsx` 유일본(z11 가드 대상)을 page 가 props 로 넘긴다 — 가이드가 사본을 하나 더 들지 않게.
- 「테스트 계정 접근 가능」 = 데모 계정 `demo-operator` 기준. 무권한 테스트 계정은 아직 없다(`TASK-BE-597` 이 신설 예정) — 생기면 `DEMO_TEST_ACCOUNT` 옆에 계정을 추가하면 열이 계산된다.
- 도메인 행의 「권한 코드」는 admin RBAC 키가 아니라 assume-tenant 파생 도메인 롤(`OperatorRoleDerivation.java`)이다 — 두 평면이 다르다는 것을 표 자체가 보여준다(`wms 구독 → WMS_OPERATOR 외 7`).
- 🔵 남긴 낡은 서술(범위 밖, 기록만): `iam-guide/data.ts` `SCREEN_ACCESS` 주석이 `/operator-groups` 를 아직 「스텁」이라 부른다(PC-FE-250 이 실기능화), 그 매트릭스에 운영자 그룹 행이 없다. 전역 매핑 표에는 `group.manage` 행이 정확히 있다.
- 권한 코드의 **최신성**은 여전히 사람 몫이다 — 표 · 전역 가이드 「권한 및 테스트 계정」 탭 · `permission-map.ts` 헤더 세 곳에 그 한계를 적었다.
