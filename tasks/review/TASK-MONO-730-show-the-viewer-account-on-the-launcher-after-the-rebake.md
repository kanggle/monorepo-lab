# Task ID

TASK-MONO-730

# Status

review — AC-2·AC-3 구현 완료(2026-09-26 UTC). **AC-1 은 창(라이브 브라우저) 대기로 열려 있다** — `review/` 에 두는 이유는 `platform/git-workflow-policy.md` § The Fourth Dimension 의 창-대기 선례(`TASK-BE-602`)와 같다.

# Title

론처에 무권한 데모 계정(`viewer@demo.com`)을 표시하고 (z11) 가드가 그 계정도 대조하게 한다 — **재굽기 뒤에만**

# Owner

monorepo

# Task Tags

- demo
- launcher
- guard

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet 5 — 론처 마크업 한 행 + (z11) 대조 한 칸 + 대조군.

---

# Goal

`TASK-BE-597`(#4001)이 권한 부족 화면 시연용 무권한 운영자 `viewer@demo.com` / `Demo1234!` 를 시드했다
(admin-service `R__seed_demo_viewer_operator.sql` · auth-service `R__seed_demo_viewer_operator_credential.sql`).
방문자가 그 계정을 알 수 있게 론처 「로그인 계정」 카드에 표시하고, 표시된 값이 시드와 갈라지지 않게 (z11) 가
대조하게 한다.

🔴 **지금 하면 안 된다.** 론처(`infra/demo/aws/site/index.html`)는 머지 즉시 Vercel 로 나가지만 계정은 AMI 안의
클론에만 있고 부팅 시 `git pull` 이 없다(`infra/demo/aws/README.md:87-97`). 현재 배포 AMI 는
`REPO_COMMIT=f1da21800`(`infra/demo/aws/deployed-ami.env`)으로 #4001 이전이다 ⇒ 지금 표시하면 **로그인이 실패하는
계정을 방문자에게 안내**하게 된다.

# Scope

## In
- 론처 「로그인 계정」 카드에 viewer 행(`id="c-viewer-email"` 등) + 「권한 부족 화면 시연용 — 대부분 메뉴에서 403」 설명.
- (z11) 확장: 론처에 표시된 viewer 이메일 ↔ auth-service credential 시드의 이메일 열 대조, 대조군(값을 틀리게 주입 → rc=1) 포함.
- 기존 `demo@demo.com` ↔ `seed/lib.sh` `user_token()` 대조는 그대로.

## Out
- 계정·권한 자체의 변경(BE-597 에서 끝남). `/partnerships` 403 은 소유자 결정으로 유지(ADR-MONO-045).

# Acceptance Criteria

- [x] **AC-0 (verify-then-act)** — 배포 AMI 의 `REPO_COMMIT` 이 `a3f5d52ec`(#4001 머지)의 **자손**인가(`git merge-base --is-ancestor a3f5d52ec <REPO_COMMIT>`). 아니면 **멈추고 ready/ 에 남는다** — 측정한 날짜와 커밋만 한 줄 적는다.
      🟢 **참 (2026-09-26 UTC)** — 15차 AMI `ami-004f04b67daf40b89` · `REPO_COMMIT=46aa31911` · `is-ancestor` rc=0. 라이브(15차 부팅 02:43Z): `auth_db.credentials` 에
      `viewer@demo.com`(iam, `…ad05`) · `admin_db.admin_operators` ACTIVE · 역할 **0**. ⇒ 착수 게이트 해소 — AC-1(창 · 브라우저)·론처 변경 진행 가능.
- [ ] **AC-1** — ⚪ **창(라이브 브라우저) 대기 — 아직 열지 않았다.** 창에서 viewer 로 콘솔 로그인 → `/api/admin/me` 200 · `roles=[]` · 게이트된 화면(운영자·감사·테넌트) 403 「권한 없음」 렌더(스크린샷). 런북은 아래 § AC-1 런북(다음 창) 참조.
- [x] **AC-2** — ✅ 2026-09-26 UTC. 론처 viewer 행 추가(`infra/demo/aws/site/index.html` `id="c-viewer-email"`/`c-viewer-pass` + 설명 문구), (z11) 확장(`infra/demo/verify-demo-wrapper.sh` — 론처 뷰어 이메일 ↔ `R__seed_demo_viewer_operator_credential.sql` 의 email 컬럼 실값 대조, account_id 앵커 유일성 확인 + 대조군). `bash infra/demo/verify-demo-wrapper.sh` **rc=0**(전체, 아래 § 구현 기록). bite: 뷰어 id 행의 이메일을 일시적으로 변조 → 새 (z11) 칸 **rc=1**(대조군 실패 아님 — 정상 대조 실패로 확인) → 원복 → rc=0.
- [x] **AC-3** — ✅ 2026-09-26 UTC. 콘솔 전역 가이드 「권한 및 테스트 계정」 탭이 viewer 를 언급하지 않았다(실측 확인 — `DEMO_TEST_ACCOUNT` 는 `demo-operator` 하나뿐, `permission-map.ts` 에 뷰어 필드 없음) ⇒ `GlobalGuideScreen.tsx` 의 테스트 계정 카드에 한 줄 추가(역할 없음 · 대부분 화면 403 · `/partnerships` 는 demo-operator 도 403 이라 뷰어만의 특징이 아님 · 비밀번호는 반복하지 않음 — 298 결정 유지). 콘솔↔론처↔permission-map 을 함께 대조하는 드리프트 테스트는 이 저장소에 **없다**(확인함 — `permission-map-drift.test.ts` 는 nav↔행 대조뿐) ⇒ 새 드리프트 테스트를 만들지 않았다(범위 밖). console-web 유닛 테스트는 **미실행**(아래 § 구현 기록 — node_modules 없음).

# Related Specs

- `projects/iam-platform/tasks/review/TASK-BE-597-demo-account-read-coverage-and-restricted-account.md` § 구현 기록 · 후속
- `infra/demo/aws/README.md` § 배포 층

# Related Contracts

- 없음(계약 변경 없음).

# Edge Cases

| 상황 | 기대 |
|---|---|
| 재굽기 뒤 누군가 `demo@demo.com`(SUPER_ADMIN)으로 viewer 에게 역할을 부여 | 공개 데모의 알려진 한계(BE-597 기록). 시드 재적용이 되돌리지 않는다 — 론처 문구는 «시연용» 이지 보장이 아니다 |
| 방문자가 셀프 온보딩으로 `demo-viewer` 슬러그를 선점 | 같은 한계. 막으려면 account-service 시드에 `demo-viewer` SUSPENDED 등록 한 줄(BE-597 제안) — 별도 소유자 결정 |

# Failure Scenarios

1. AC-0 을 건너뛰고 표시 → 방문자가 로그인 실패 계정을 본다.
2. (z11) 를 «이메일 문자열이 론처에 있는가» 로만 짠다 → 시드와 갈라져도 초록(대조가 아니라 존재 확인).

---

# Implementation Record (2026-09-26 UTC)

## 변경 파일

- `infra/demo/aws/site/index.html` — 「로그인 계정」 카드에 뷰어 안내 문단 + `<dl>` 두 행(`id="c-viewer-email"` = `viewer@demo.com`, `id="c-viewer-pass"` = `Demo1234!`). 복사 버튼은 기존 `.copy` 제네릭 핸들러(`document.querySelectorAll(".copy")` → `btn.dataset.copy` 로 대상 id 조회)가 그대로 집는다 — 새 JS 불필요.
- `infra/demo/verify-demo-wrapper.sh` — (z11) 블록 안에 TASK-MONO-730 절 추가. 권위는 `seed/lib.sh` 가 아니라 `R__seed_demo_viewer_operator_credential.sql` 의 INSERT VALUES 의 email 컬럼 **실값**이다(BE-597 구현 기록이 이미 이렇게 정했다 — 뷰어는 `user_token()` 이 안 쓴다). 앵커는 그 행의 account_id 리터럴(`0199de70-0000-7000-8000-00000000ad05`)이고, 그 앵커가 파일에 정확히 1번 나오는지 `grep -c` 로 먼저 확인한다(1이 아니면 추출이 불안정하다고 실패). 추출 유효성(빈 값 거부) · 값 대조 · 대조군(`x` 접미로 강제 불일치)까지 기존 demo@demo.com 짝과 같은 4단 구조를 반복했다.
- `projects/platform-console/apps/console-web/src/features/global-guide/components/GlobalGuideScreen.tsx` — 「권한 및 테스트 계정」 탭의 테스트 계정 카드에 뷰어 한 줄 추가.

## AC-2 — 가드 실행 결과

- `bash infra/demo/verify-demo-wrapper.sh` 전체 실행, stdout/stderr 를 파일로 리다이렉트한 뒤 `$?` 를 별도로 확인(파이프 뒤 `tail`/`grep` 로 rc 를 가리지 않음).
- (z11) 절 출력:
  ```
  [verify] (z11) 론처·콘솔 로그인에 적힌 계정이 시드가 실제로 쓰는 값과 같은가
    ok: 론처·콘솔 계정 ↔ 시드 일치 (email=demo@demo.com · 비밀번호 일치 · 여섯 값 모두 추출 확인 · 콘솔 선언 각 1개 · 대조군 2/2 통과)
    ok: 론처 뷰어 계정 ↔ auth-service 자격증명 시드 일치 (email=viewer@demo.com · account_id 앵커 유일 확인 · 대조군 통과)
  ```
  기존 demo@demo.com 짝은 **손대지 않았고 그대로 통과**한다(회귀 없음).
- 전체 실행 **rc=0**(전체 로그·정확한 종료코드는 아래 「전체 실행」 항목 참조 — 이 세션이 마지막으로 확인).
- **bite**: `id="c-viewer-email"` 의 텍스트를 `viewer@demo.com` → `viewer@demo.comx` 로 임시 변조 → (z11) 재실행 → `fail "(z11) 론처의 뷰어 이메일이 시드 자격증명 컬럼과 다릅니다…"` 로 **rc=1**. 원복 → 재실행 → **rc=0**. (대조군 칸 자체와는 다른 축 — 대조군은 "비교가 죽어 있지 않은가"를 재고, 이 bite 는 "실제 드리프트를 잡는가"를 쟀다.)

## AC-3 — 가이드 확인

- 실측: 수정 전 `permission-map.ts` 의 `DEMO_TEST_ACCOUNT` 는 `demo-operator`(=`demo@demo.com`) 하나만 기술하고, `GlobalGuideScreen.tsx` 「권한 및 테스트 계정」 탭·`PermissionMapTable.tsx`·`KnownMismatches` 어디에도 `viewer@demo.com` 문자열이 없었다(그레핑 확인) ⇒ AC 문구의 "언급하지 않는다"가 참.
- 콘솔↔론처↔permission-map 을 함께 대조하는 기존 드리프트 테스트는 없다 — `permission-map-drift.test.ts` 는 nav↔행 존재만 대조하고 뷰어 관련 어떤 단언도 갖지 않는다. 그래서 새 드리프트 테스트를 만들지 않았다(티켓 지시가 "있으면 갱신" — 없으므로 대상 없음).
- **console-web 유닛 테스트 미실행** — 이 worktree(`C:/Users/kangdow/dev/project/ai-project/mlab-730`)에는 `node_modules` 가 없다(루트에도, `apps/console-web` 에도 없음 — 확인). 지시에 따라 `pnpm install`/`npm install` 을 실행하지 않았다. 따라서 `GlobalGuideScreen.test.tsx`(테스트 계정 카드를 검사하는 스위트)를 이번 세션에서 **green 이라고 주장하지 않는다** — 다음에 이 worktree 에서 의존성이 설치된 뒤(또는 메인 체크아웃에서) `vitest run tests/unit/GlobalGuideScreen.test.tsx tests/unit/permission-map-drift.test.ts` 로 확인이 필요하다. 변경은 순수 텍스트 추가(JSX 문자열 하나)라 기존 단언(`toHaveTextContent(EMAIL)`, RBAC 매트릭스 testid 등)을 깨뜨릴 구조적 여지는 없지만, 이것은 코드 검토이지 실행 확인이 아니다.

## AC-1 런북 (다음 창)

1. 재굽기된 AMI(15차, `ami-004f04b67daf40b89`, `REPO_COMMIT=46aa31911`)로 데모 인스턴스 기동.
2. 브라우저로 `https://console.hubwang.com/` 접속 → 로그인 → `viewer@demo.com` / `Demo1234!`.
3. 로그인 성공 후 콘솔 셸(`/console`, `/dashboards/overview`)이 열리는지 확인 — 역할이 없어도 이 두 화면은 `operator` 게이트(로그인만 요구)라 200이어야 한다.
4. 브라우저 DevTools 네트워크 탭에서 `/api/admin/me` 호출을 찾아 상태 200 · 응답 `roles: []` 확인.
5. `/operators` · `/audit` · `/tenants` 등 게이트된 화면으로 이동 → 403 「권한 없음」 렌더 확인. `/partnerships` 는 demo-operator(SUPER_ADMIN)도 403 이므로 이 계정만의 증거로 쓰지 않는다(BE-597 IT `DemoOperatorSeedIntegrationTest#viewerIsDeniedOnGatedReads` 가 코드 레벨로는 이미 핀).
6. 스크린샷 저장(로그인 화면 · `/api/admin/me` 응답 · 게이트된 화면 403 화면 1장 이상) → 이 섹션에 경로/타임스탬프로 기록.
7. 통과하면 AC-1 체크박스를 `[x]` 로, Status 를 `done` 후보로 갱신하고 `tasks/INDEX.md` 를 정리한다(4차원 검증 포함).
