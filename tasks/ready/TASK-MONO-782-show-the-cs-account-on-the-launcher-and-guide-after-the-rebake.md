# Task ID

TASK-MONO-782

# Status

ready — ⏳ **DO NOT START before AC-0 is true** (재굽기된 AMI 가 `TASK-MONO-781` 스쿼시를 담을 때).

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

- [ ] **AC-0 (verify-then-act)** — `infra/demo/aws/deployed-ami.env` 의 `REPO_COMMIT` 이 `TASK-MONO-781` 스쿼시 커밋의 **자손**인가(`git merge-base --is-ancestor <781 스쿼시> <REPO_COMMIT>`). 아니면 **멈추고 ready/ 에 남는다** — 측정한 날짜(UTC)와 커밋만 한 줄 적는다.
- [ ] **AC-1** — 데모 창(라이브 브라우저): `cs@demo.com` 로그인 → 스위처에 `ecommerce` 하나(자동 선택) → 사이드바에 «계정 운영» → 검색 전용 안내 → `demo@demo.com` 검색 1행 → 잠금 → 해제 → 빈 검색이면 안내로 복귀(= `TASK-PC-FE-326` 마지막 AC). 함께 기록: 잠금 응답 `scope = SITE_MEMBERSHIP`, «감사 · 보안» 화면이 열림(`audit.read`).
- [ ] **AC-2** — 론처 행 + (z11) 칸. 전체 `bash infra/demo/verify-demo-wrapper.sh` rc=0(파일로 리다이렉트 후 `$?`) + bite(론처 CS 이메일 변조 → rc=1 → 원복 → rc=0).
- [ ] **AC-3** — 콘솔 가이드 행. **공개 문구(소유자 지시 2026-10-09 UTC, 뜻을 바꾸지 말 것)**: 누구 칸 «CS 2선(계정 잠금·해제)», 설명 칸에 «데모에서는 테넌트 ecommerce 를 고르면 이커머스·WMS 운영 권한도 함께 받습니다 — 테넌트를 고르면 그 테넌트가 구독한 도메인의 운영 권한이 따라오기 때문입니다. 감사 · 보안 화면도 열립니다.» 정도. 가이드의 쉬운 말 가드(티켓 id · 파일 경로 · 역할 상수 노출 금지 등)를 초록으로 유지. `pnpm exec tsc --noEmit` · `pnpm lint` · `pnpm exec vitest run` 각각 rc 기록.
- [ ] **AC-4** — 문구가 말하는 권한 집합이 여전히 참인지 확인: auth-service `DemoCsOperatorDerivedRolesTest` green(구독이 바뀌었다면 그 테스트가 먼저 RED 이고, 문구를 고친다).

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
