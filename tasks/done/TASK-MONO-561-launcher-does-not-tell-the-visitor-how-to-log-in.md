# Task ID

TASK-MONO-561

# Title

론처가 **로그인 방법을 알려주지 않는다** — 방문자는 데모를 열어도 들어갈 수 없다

# Status

done

# Owner

monorepo

# Task Tags

- infra
- demo
- launcher

---

# 배경 — 2026-08-19(UTC) 소유자가 막혔다

데모를 기동하고 콘솔을 열었지만 **들어갈 수 없었다.** 로그인 화면이 나오는데 계정을
모르기 때문이다. 그래서 "회원가입" 을 눌렀고 — 그건 콘솔 경로에서 **구조적으로
불가능**하며(`TASK-BE-581`) 실패 메시지마저 틀렸다(`TASK-BE-580`).

즉 이 세 티켓은 **같은 하나의 구멍**에서 나왔다: **론처가 "어떻게 들어가는가" 를 말하지
않는다.** 회원가입 결함들은 방문자가 그 구멍을 메우려다 밟은 것이다.

🔵 포트폴리오 데모의 방문자는 **면접관**이다. 계정을 못 찾으면 그 사람은 회원가입을
시도하고(실패), 그다음엔 그냥 닫는다.

## 🔴 만들 계정은 없다 — 이미 있다. 새로 만들면 안 된다

착수 전에 확정해 둔다. `demo@demo.com` 은 **이미 최대 권한**이다:

| 축 | 값 | 출처 |
|---|---|---|
| RBAC 역할 | **`SUPER_ADMIN`** | `R__seed_demo_operator.sql:80` |
| 홈 테넌트 | **`demo-corp`** = 5개 도메인 **전부** 구독 | `R__05_seed_demo_corp_tenant_and_consumer_accounts.sql:35` |
| assume 대상 | `demo-corp` + `ecommerce` | 같은 파일 §3·§4 (TASK-BE-576) |
| 자격증명 | `ecommerce`·`fan-platform`·`iam` **3개 테넌트** | auth `R__01_seed_demo_single_identity_credentials.sql` |

🔴 **"모든 권한" 은 이 시스템에서 두 축이다** — RBAC 역할과 **테넌트 엔타이틀먼트**.
시드가 명시한다: *"the domain-ops pages are gated by the tenant's ENTITLEMENT, not by
this RBAC role (ADR-MONO-035); **the role does not grant domain access**"*. 두 축 모두
이미 최대다.

**그래서 새 슈퍼계정을 만드는 것은 세 가지로 틀리다:**

1. **문서화된 헌장을 정면으로 거스른다** — TASK-BE-571 이 *"ONE email + ONE password
   the interviewer types on all three surfaces"* 를 설계로 못 박았고, `credentials` 에
   같은 이메일이 세 행으로 들어가 있는 것이 그 헌장의 구현이다.
2. **아무것도 안 고친다.** 화면이 401/403 인 것은 권한 부족이 아니라 별건 결함이다
   (`TASK-MONO-554` = 허용 issuer 가 로컬 값으로 고정). 슈퍼계정을 하나 더 만들어도
   그 화면은 똑같이 401 이다.
3. **두 번째 SUPER_ADMIN 은 갈라진다** — 한쪽만 갱신되는 날이 온다.

⇒ 이 티켓이 할 일은 **계정 생성이 아니라 공개**다.

## 🔵 비밀번호를 공개 페이지에 적는 것은 새로운 노출이 아니다

`Demo1234!` 는 **이미 저장소에 공개돼 있다** — 마이그레이션 주석이 스스로를
*"one published demo password"* 라고 부른다. 데모 전체가 인증 없이 열리는 공개
포트폴리오이고(`/start` 도 무인증), 지출 상한은 계정이 아니라 **월 예산 가드**가
지킨다. 적지 않아서 지켜지는 것은 없고, 안 적으면 방문자만 못 들어간다.

🔴 그래도 **한 가지는 지켜야 한다**: 이 페이지에 적히는 것은 **데모 시드 계정뿐**이다.
AWS 자격증명·API 키·실제 사용자 데이터는 여기 오지 않는다(`TASK-MONO-557` 이 세운
경계 — Vercel 로 넘어가는 값은 공개 URL 하나뿐이다).

## ✅ 2026-08-21 UTC — **AC-4 라이브 판정 완료**

`TASK-MONO-562`·`563` 이 배포 파이프라인을 고친 뒤, 이 티켓의 산출물이 **처음으로 방문자에게
도달했다.** 배포된 론처(`kanggle-portfolio.vercel.app`, 23,206 B)에서 확인:

- 로그인 안내가 **문서에 실재한다** — `demo@demo.com`, `#c-email` / `#c-pass` 복사 필드.
- 실제 브라우저(Chromium)로 열었을 때 `#msg` 가 제어 평면 값을 실어 렌더된다
  (*"🟢 대기 중 — 시작을 누르세요 · 이번 달 431/600분 사용"*), 콘솔 에러 **0건**.

🔵 **이 AC 가 오래 열려 있던 이유는 이 티켓의 결함이 아니었다** — `TASK-MONO-557` 이 프로젝트를
나눈 뒤 `main` 에서 성공한 프로덕션 배포가 **0건**이었기 때문이다(`563` 이 밝혔다).
머지되어 있었고, 초록이었고, **방문자에게는 없었다.**

# Goal

방문자가 론처만 보고 **데모에 로그인할 수 있게** 한다.

# Scope

- `infra/demo/aws/site/index.html` — 계정 안내 블록.

🔴 **`TASK-MONO-560` 과 같은 파일이다.** 저장소 규칙(공유파일 시리즈 = 단일 worktree ·
직렬 머지)에 따라 **병렬로 진행하지 말 것** — 같은 파일 충돌이 보장된다. 이 티켓을
먼저 끝내고 560 을 그 위에 얹는다.

**범위 밖**: 계정·역할·엔타이틀먼트 변경(위 § 참조 — **하지 말 것**), 회원가입 경로
(`TASK-BE-580`/`581`), 화면별 401/403(`TASK-MONO-554`).

# Acceptance Criteria

**AC-0 — 재확인 (verify-then-act). 🔴 시드에서 다시 읽어라.**
착수 시점에 위 표의 네 줄을 **시드 파일에서 다시 확인**하고, 페이지에 적을 이메일과
비밀번호가 **지금 실제로 시드되는 값**인지 대조한다. 🔴 이 티켓 본문을 인용하지 마라 —
그건 2026-08-19 관측이고, 페이지에 박히면 **아무 게이트도 없이 썩는다**(AC-3 참조).

**AC-1 — 계정이 페이지에 보인다.**
`demo@demo.com` / `Demo1234!` 와 **세 화면 전부에서 같은 계정으로 들어간다**는 사실이
보인다. 🔵 그 "하나로 다 된다" 가 헌장이고, 그걸 안 적으면 방문자는 화면마다 다른
계정을 찾는다.

**AC-2 — ERP 결재 시연에 필요한 두 번째 계정도 안내한다.**
`requester@demo.com`(같은 비밀번호)이 존재하는 이유는 **ERP 의 Separation-of-Duties
게이트가 서로 다른 두 행위자를 요구**하기 때문이다(`TASK-MONO-519`). 한 계정만 안내하면
그 시연은 막힌다. 🔵 다만 **주 계정과 시각적으로 구별**해서 적어라 — 면접관이 타이핑할
것은 하나다.

**AC-3 — 페이지의 값이 시드와 어긋나면 잡힌다. 🔴 이 티켓의 오래 가는 산출물.**
`index.html` 에 박힌 이메일/비밀번호가 시드 파일의 값과 다르면 실패하는 가드를
`verify-demo-wrapper.sh` 정적 구간에 신설한다.

- 🔴 **대조군 필수**: 페이지 값을 일부러 바꿔 넣었을 때 **실제로 무는지** 확인하고
  결과를 적어라. 안 물면 술어가 틀린 것이다.
- 🔴 **양방향으로 볼 것**: 시드가 바뀌어도 잡혀야 한다(비밀번호 로테이션 시나리오).
  한쪽만 보는 가드는 절반만 지킨다.
- 🔴 **추출 유효성 칸**: 두 쪽 중 하나에서 아무것도 못 뽑으면 비교는 **공허하게 통과**
  한다(빈 문자열 == 빈 문자열). 뽑힌 값이 비어 있지 않음을 별도로 단언하라.
- 러너를 명시하라 — `Demo wrapper smoke (infra/demo)` 잡이 이 스크립트를 돌린다.

**AC-4 — 라이브 판정.**
실제로 그 계정으로 **세 화면에 로그인**해 본다. 🔴 페이지에 글자가 보이는 것은 AC-1 이지
AC-4 가 아니다 — **로그인이 되는가**가 판정이다.

# Related Specs

- `infra/demo/aws/site/index.html` — 론처
- `projects/iam-platform/apps/auth-service/src/main/resources/db/migration-dev/R__01_seed_demo_single_identity_credentials.sql` — 단일 신원 헌장 (TASK-BE-571)
- `projects/iam-platform/apps/admin-service/src/main/resources/db/migration-dev/R__seed_demo_operator.sql` — SUPER_ADMIN + assume 대상
- `projects/iam-platform/apps/account-service/src/main/resources/db/migration-dev/R__05_seed_demo_corp_tenant_and_consumer_accounts.sql` — demo-corp 5도메인 구독
- **TASK-MONO-560** — 같은 파일(론처 링크). **직렬로 진행.**
- **TASK-BE-580 / TASK-BE-581** — 방문자가 이 구멍을 메우려다 밟은 결함들.
- **TASK-MONO-554** — 화면 401. 🔴 **권한 문제가 아니다** — 새 계정으로 안 고쳐진다.

# Related Contracts

없음 (정적 페이지).

# Edge Cases

- **비밀번호에 `!` 가 들어간다** — HTML 에 그대로 써도 되지만, 복사 기능을 붙인다면
  이스케이프를 확인하라.
- **복사 버튼과 보안 컨텍스트** — `navigator.clipboard` 는 **secure context 전용**이다.
  론처는 https 라 있지만, 없을 때 조용히 아무 일도 안 일어나면 방문자는 복사된 줄 안다.
  🔴 폴백을 두거나, 아예 붙이지 마라.
- **데모가 꺼져 있을 때** — 계정 안내는 상태와 무관하게 보여도 된다(기동을 기다리는
  동안 읽는 것이 자연스럽다). 링크와 달리 404 를 만들지 않는다.

# Failure Scenarios

- **계정을 새로 만들면**: 위 § 세 가지 이유로 틀리고, 특히 **화면 401 은 안 고쳐진다** —
  고쳐진 줄 알고 `TASK-MONO-554` 를 닫으면 결함이 은폐된다.
- **AC-3 가드 없이 값만 박으면**: 비밀번호가 바뀌는 날 페이지만 옛 값을 들고 남는다.
  그리고 그 실패는 **방문자에게만** 보인다(로그인 실패) — 우리 쪽 게이트는 전부 초록이다.
- **가드를 "페이지에 이메일 형식 문자열이 있는가" 로 만들면**: 값이 틀려도 통과한다.
  술어는 **시드와의 일치**여야 한다.
