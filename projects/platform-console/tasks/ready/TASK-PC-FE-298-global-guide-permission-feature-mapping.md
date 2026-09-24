# Task ID

TASK-PC-FE-298

# Title

전역「가이드」메뉴 + 도메인별 가이드 확장 + 권한·기능 매핑 문서(샘플 방문자 열람 가능)

# Status

ready

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
