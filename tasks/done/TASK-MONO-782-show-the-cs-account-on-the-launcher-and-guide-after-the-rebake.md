# Task ID

TASK-MONO-782

# Status

done

# Title

데모 CS 2선 계정(`cs@demo.com`)을 론처 · 콘솔 가이드에 표시하고 (z11) 가 그 계정도 대조하게 한다 — **재굽기 뒤에만**, 트레이드오프 문구와 함께

# Owner

monorepo

# Task Tags

- demo
- launcher
- guard
- platform-console

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet 5 — `TASK-MONO-730` 과 같은 모양(론처 한 행 + (z11) 한 칸 + 대조군 + 가이드 한 줄). 문구는 아래 AC 에 고정돼 있다.

---

# Dependency Markers

- 선행: `TASK-MONO-781`(시드 · 트레이드오프 핀) — 그 PR 의 스쿼시 커밋이 배포 AMI 에 실려야 한다(AC-0).
- 선례: `TASK-MONO-730`(BE-597 의 `viewer@demo.com` 을 재굽기 뒤 론처 · (z11) · 가이드에 — 같은 절차).
- 관련: `TASK-PC-FE-326`(라이브 확인이 이 계정으로 잰다).

# Goal

`TASK-MONO-781` 이 CS 2선 데모 운영자 `cs@demo.com`(같은 공개 데모 비밀번호, 홈 `ecommerce`, `SUPPORT_LOCK`@`ecommerce`)을 시드했다. 방문자가 그 계정을 알고, **그 계정이 실제로 받는 권한을 정직하게** 알 수 있게 론처와 콘솔 가이드에 표시하고, 표시된 이메일이 시드와 갈라지지 않게 (z11) 가 대조하게 한다.

🔴 **지금 하면 안 된다.** 론처(`infra/demo/aws/site/index.html`)와 콘솔(전역 가이드 · `/login`)은 머지 즉시 Vercel 로 나가지만, 계정은 AMI 안의 클론에만 있고 부팅 시 `git pull` 이 없다(`infra/demo/aws/README.md:90-94`). 기안 시점 배포 AMI 는 `REPO_COMMIT=e2a0c7eb5`(25차) — `TASK-MONO-781` 이전이다 ⇒ 지금 표시하면 **로그인이 실패하는 계정을 안내**한다.

# Scope

## In

- 론처 「로그인 계정」 카드에 CS 행(`id="c-cs-email"` 등, 비밀번호는 기존 행과 같음) + 설명.
- (z11) 확장(`infra/demo/verify-demo-wrapper.sh`): 론처 CS 이메일 ↔ `R__seed_demo_cs_operator_credential.sql` 의 email **컬럼 실값** 대조, 앵커 = 그 행의 account_id 리터럴 `0199de70-0000-7000-8000-00000000ad08`(파일에 1회인지 `grep -c` 로 먼저 확인), 대조군(`x` 접미) 포함 — 뷰어 · 플랫폼 운영자 칸과 같은 4단 구조.
- 콘솔 `GlobalGuideScreen.tsx` 「권한 및 테스트 계정」 탭의 테스트 계정 카드에 한 줄(+ 그 카드를 핀하는 테스트 갱신).
- (선택, 판단해서 기록) `/login` 의 `DemoLoginCredentials` 에 한 줄 — 플랫폼 운영자 줄과 같은 모양.

## Out

- 계정 · 권한 자체의 변경(`TASK-MONO-781` 에서 끝남, 소유자 결정 A).

# Acceptance Criteria

- [x] **AC-0 (verify-then-act)** — 🟢 **참 (2026-10-11 UTC).** `infra/demo/aws/deployed-ami.env` `REPO_COMMIT=69af06d0cd1d523813e93c16a4fc081a3ef16da6`(26차 AMI, `ami-00701c1fa528b5866`). `git merge-base --is-ancestor 53e44f3e5 69af06d0cd1d523813e93c16a4fc081a3ef16da6` → **rc=0**(53e44f3e5 = `TASK-MONO-781` 스쿼시 #4277). 오케스트레이터가 먼저 쟀고, 이 세션이 **독립적으로 재실행**해 같은 결과를 확인했다.
- [x] **AC-1** — 🟢 **CLOSED — 26차 데모 창(2026-10-11 UTC, 소유자 라이브 브라우저).** `cs@demo.com` 로그인 → 테넌트 스위처에 `ecommerce` 하나만 보이고 자동 선택 → 사이드바 «계정 운영» → 검색 전용 안내 → `demo@demo.com` 검색 1행 → 잠금 → 해제 → 검색어를 지우면 안내로 복귀(`TASK-PC-FE-326` 마지막 AC 와 일치). account-service 로그로 확인: `consumer-site lock: account 0199de70-0000-7000-8000-00000000ec01 on site ecommerce by operator demo-cs (ACTIVE -> LOCKED)` 09:47:06Z · `… unlock … (LOCKED -> ACTIVE)` 09:47:33Z ⇒ 잠금 스코프 = **SITE_MEMBERSHIP**(계정 전체가 아니라 그 사이트 멤버십). «감사 · 보안» 화면도 열림(`audit.read`, `SUPPORT_LOCK` 이 가진 권한). 🔵 **라이브에서 발견한 각주** — 목록의 상태 컬럼이 **계정 상태**를 그리므로 사이트 잠금 뒤에도 `ACTIVE` 로 보인다(별도 소유자 결정 대상, 이 티켓 범위 밖) → `TASK-PC-FE-332` 로 기안됨.
- [x] **AC-2** — ✅ 2026-10-11 UTC. 론처 CS 행(`id="c-cs-email"`/`c-cs-pass`, 기존 뷰어·플랫폼 운영자 칸과 같은 구조) + (z11) 4단 확장(앵커=`0199de70-0000-7000-8000-00000000ad08`, 시드 파일에 `grep -c` 1회 확인 후 사용). `bash infra/demo/verify-demo-wrapper.sh` 전체 실행, 파일로 리다이렉트 후 `$?` 확인 — **rc=0**(아래 § 구현 기록). **bite**: 론처 CS 이메일을 `cs@demo.comx` 로 변조 → 재실행 → `FAIL: (z11) 론처의 CS 이메일이 시드 자격증명 컬럼과 다릅니다…` 로 **rc=1** → 원복 → 재실행 → **rc=0**.
- [x] **AC-3** — ✅ 2026-10-11 UTC. 콘솔 `GlobalGuideScreen.tsx` 「권한 및 테스트 계정」 탭에 CS 행 추가(소유자 고정 문구 그대로, 역할 상수 미노출) + `GlobalGuideScreen.test.tsx` 에 그 행을 핀하는 테스트 추가. `pnpm exec tsc --noEmit` **rc=0** · `pnpm lint` **rc=0** · `pnpm exec vitest run --minWorkers=1 --maxWorkers=2`(console-web 전체, 368 files / 4177 tests) **rc=0**(전부 통과, 회귀 없음). `/login` 의 `DemoLoginCredentials` 에도 플랫폼 운영자 줄과 같은 모양으로 CS 줄을 추가하기로 **판단**했다(아래 § 결정 기록) + 그 줄을 핀하는 테스트 추가, 같은 vitest 실행에 포함돼 green.
- [x] **AC-4** — ✅ 2026-10-11 UTC. `./gradlew :projects:iam-platform:apps:auth-service:test --tests '*DemoCsOperatorDerivedRolesTest*'` → **rc=0**, 3/3 PASSED(문구가 말하는 9개 역할 집합이 여전히 참 — 구독 변경 없음).

# Related Specs

- `tasks/in-progress/TASK-MONO-781-demo-cs-support-lock-operator.md`(→ review/done) § AC-0 · § 공개 시점 판단
- `projects/iam-platform/specs/features/multi-tenancy.md` § Platform Console(`confined_tenant_id` 절)
- `infra/demo/aws/README.md` § 배포 층

# Related Contracts

- 없음(계약 변경 없음).

# Edge Cases

| 상황 | 기대 |
|---|---|
| 재굽기 뒤 방문자가 런타임에 `ecommerce` 구독을 바꿈 | 공개 데모의 알려진 한계 — 문구는 시드 상태를 말한다 |
| 방문자가 `demo@demo.com` 의 `ecommerce` 멤버십을 잠근 채 둠 | 다음 방문자는 잠긴 행을 본다 — 해제가 같은 화면에 있다. 자동 복구 없음(시드는 멤버십 상태를 되돌리지 않는다) |
| 가이드 문구에서 «쓰기 포함» 을 뺌 | `DemoCsOperatorDerivedRolesTest` 는 쓰기 역할을 핀한다 — 문구가 더 좁게 말하면 거짓이 된다 |

# Failure Scenarios

1. AC-0 을 건너뛰고 표시 → 방문자가 로그인 실패 계정을 본다.
2. (z11) 를 «이메일 문자열이 론처에 있는가» 로만 짠다 → 시드와 갈라져도 초록.
3. 가이드에 «CS 2선» 만 적고 도메인 권한을 숨긴다 → 소유자 결정 A 의 조건(공개)을 어긴다.

---

# 구현 기록 (2026-10-11 UTC)

## 변경 파일

- `infra/demo/aws/site/index.html` — 「로그인 계정」 카드에 CS 섹션(`id="c-cs-email"`/`c-cs-pass`) 추가. 기존 기본 · 플랫폼 운영자 · 권한 없음 시연 세 칸과 같은 4단 구조(제목 · 용도 · `<dl>` 두 행 · 안내 목록 · 트레이드오프 문단). TASK-MONO-785/786/787/788 이 최근 이 카드와 인접 영역을 정리했으므로 **현재** 구조를 Read 하고 그 모양에 맞췄다(기존 섹션들을 손대지 않음).
- `infra/demo/verify-demo-wrapper.sh` — (z11) 블록에 CS 절 추가. 뷰어·플랫폼 운영자와 같은 모양: 권위 = `R__seed_demo_cs_operator_credential.sql` 의 INSERT VALUES **email 컬럼 실값**(머리 주석 평문이 아님), 앵커 = 그 행의 account_id 리터럴 `0199de70-0000-7000-8000-00000000ad08`(파일에 1회만 나오는지 `grep -c` 로 먼저 확인), 추출 유효성 · 값 대조 · 대조군(`x` 접미) 4단.
- `projects/platform-console/apps/console-web/src/features/global-guide/components/GlobalGuideScreen.tsx` — `testAccounts` 배열에 `cs` 항목 추가. 「누구」 칸 = «CS 2선(계정 잠금·해제)», 「열리는 화면」 칸 = 소유자 고정 문구 그대로(`ecommerce` 선택 시 이커머스·WMS 운영 권한 + 감사·보안 화면). 역할 상수(`SUPPORT_LOCK` 등) 미노출 — 쉬운 말 가드(`GlobalGuideScreen.test.tsx` 의 "no ticket / file / role leaks" 류 테스트)가 커버.
- `projects/platform-console/apps/console-web/tests/unit/GlobalGuideScreen.test.tsx` — CS 행을 핀하는 테스트 추가(`global-guide-test-account-cs`, 이메일·누구·트레이드오프 문구·역할 상수 미노출을 단언).
- `projects/platform-console/apps/console-web/src/widgets/demo-credentials/DemoLoginCredentials.tsx` — **선택 스코프, 판단 = 추가함**(아래 § 결정 기록). `DEMO_CS_OPERATOR_EMAIL` 상수(= `DEMO_LOGIN_*` 과 다른 이름 — (z11) 의 대입문 앵커가 걸리지 않게, 플랫폼 운영자 상수와 같은 이유) + 플랫폼 운영자 문단과 같은 모양의 CS 문단(`data-testid="demo-login-cs-operator"`).
- `projects/platform-console/apps/console-web/tests/unit/demo-login-credentials.test.tsx` — 그 CS 문단을 핀하는 테스트 추가.

## § 결정 기록 — `/login` `DemoLoginCredentials` CS 줄 (선택 스코프)

Scope 가 "선택, 판단해서 기록"으로 남긴 항목. **추가하기로 판단**했다 — 이유:
- `demo@demo.com`(기본 계정, SUPER_ADMIN)으로는 사이드바 «계정 운영» 의 search-only 흐름을 재현할 수 없다(다른 경로가 열린다, `TASK-MONO-781` AC-0 실측). CS 흐름을 보려면 **반드시** `cs@demo.com` 이 필요하므로, 플랫폼 운영자(팬 디렉터리에 반드시 필요)와 **같은 성격의 필수 정보**다.
- 뷰어(`viewer@demo.com`)는 반대로 `DemoLoginCredentials` 에 없다 — 권한 부족 시연은 기본 계정으로도 403 화면 일부를 볼 수 있어 "다른 계정이 아니면 아예 안 보인다"는 성격이 아니기 때문(730 선례의 암묵적 구분). CS 는 뷰어 쪽이 아니라 플랫폼 운영자 쪽 성격이라고 판단했다.
- 이름을 `DEMO_CS_OPERATOR_EMAIL` 로 잡아 (z11) 의 `DEMO_LOGIN_EMAIL`/`DEMO_LOGIN_PASSWORD` 대입문 앵커(각 1회 선언 요구)와 충돌하지 않게 했다 — 플랫폼 운영자 상수가 같은 이유로 이름이 다르다.

## AC-2 — 가드 실행 결과

- `bash infra/demo/verify-demo-wrapper.sh` 전체 실행, stdout/stderr 를 파일로 리다이렉트한 뒤 `$?` 를 별도 문으로 확인(파이프 뒤 `tail`/`grep` 으로 rc 를 가리지 않음) — **rc=0**.
- (z11) 절 출력에 새 줄 확인: `ok: 론처 CS 계정 ↔ auth-service 자격증명 시드 일치 (email=cs@demo.com · account_id 앵커 유일 확인 · 대조군 통과)` — 기존 demo@demo.com · 뷰어 · 플랫폼 운영자 세 짝은 그대로 통과(회귀 없음).
- **bite**: `id="c-cs-email"` 의 텍스트를 `cs@demo.com` → `cs@demo.comx` 로 임시 변조(Edit, `git checkout` 안 씀) → 재실행 → `FAIL: (z11) 론처의 CS 이메일이 시드 자격증명 컬럼과 다릅니다: 페이지="cs@demo.comx" · 시드="cs@demo.com"` 로 **rc=1**. 원복 → 재실행 → **rc=0**.

## AC-3 — tsc·lint·vitest

- console-web 에 이 worktree 전용 `node_modules` 가 없어 `pnpm install --frozen-lockfile` 을 `projects/platform-console/apps/console-web` 안에서 실행(루트/`platform-console` 레벨은 워크스페이스가 아니라 설치가 no-op 였다 — 반드시 `apps/console-web` 안에서 실행해야 함을 확인). 독립 설치이므로 junction 없음.
- `pnpm exec tsc --noEmit` → **rc=0**.
- `pnpm lint` → **rc=0**(`✔ No ESLint warnings or errors`).
- `pnpm exec vitest run --minWorkers=1 --maxWorkers=2`(전체, 다른 스위트와 동시 실행 안 함) → **rc=0** — **368 files / 4177 tests 전부 통과**(934초). `GlobalGuideScreen.test.tsx`(27개, CS 핀 테스트 포함) · `demo-login-credentials.test.tsx`(CS 핀 테스트 포함) · `domain-guides-plain.test.tsx` 전부 green — 쉬운 말 가드 유지 확인.

## AC-4 — auth-service

- `./gradlew :projects:iam-platform:apps:auth-service:test --tests '*DemoCsOperatorDerivedRolesTest*'` → **rc=0**(`BUILD SUCCESSFUL`), `TEST-….DemoCsOperatorDerivedRolesTest.xml` = `tests="3" failures="0" errors="0"`.

## 가드·스테이징

- 스테이징 전 새·수정 파일을 "LOCAL DEMO ONLY / DO NOT COMMIT" 마커로 grep — 0건.
- `git add` 뒤(스테이지 먼저 — `check-*` 가드는 `git ls-files` 를 읽는다) `bash scripts/check-index-queue-drift.sh` → **rc=0**, `bash scripts/check-task-id-collision.sh` → **rc=0**, `bash scripts/check-walkthrough-ledger-drift.sh` → **rc=0**.
- `pnpm-lock.yaml` 은 `pnpm install` 뒤 `git status` 에 modified 로 떴으나 `git diff` 가 **빈 diff**(줄바꿈 정규화 경고만) — 실제 내용 변경이 아니므로 커밋에서 **제외**.
- 콘솔 e2e 스펙(`tests/e2e/*.spec.ts`) 을 grep — 이 가이드 카드·테스트 계정 텍스트를 핀하는 e2e 없음 ⇒ **nightly-e2e 영향 없음**(변경이 유닛 테스트로만 커버됨). `infra/demo/verify-demo-wrapper.sh` 변경은 `nightly-e2e.yml` 의 `--require-coverage` 호출과 `ci.yml` 의 `demo-wrapper-smoke`(경로 게이트, 이 PR 은 `infra/demo/` 를 건드리므로 **실행된다**) 양쪽에 걸린다 — 둘 다 이 PR 의 CI 에서 다시 확인됨.

---

# 완료 기록 (2026-10-11 UTC, 4-dim verified)

- (a) impl PR **#4307** `MERGED` (b) 스쿼시 `c8488c209` = 그 시점 origin/main tip (c) 머지 시점 `statusCheckRollup` 실패 0/66 (d) `# Acceptance Criteria` AC-0~4 본문 ✅(AC-1 = 26차 창 라이브).
- review 단계를 따로 두지 않았다 — 구현 PR 이 머지됐고 AC 가 전부 닫혀 review 에서 기다릴 것이 없다(라이브 AC 는 머지 전에 이미 닫혔다).
- 🔵 남은 관찰: 론처가 방문자에게 «잠금 · 해제 버튼을 눌러 봅니다» 라고 안내하지만 목록 상태 칸은 사이트 잠금을 보여 주지 않는다 → `TASK-PC-FE-332`.
