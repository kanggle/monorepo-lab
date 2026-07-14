# Task ID

TASK-MONO-400

# Title

`TASK-MONO-394` 가 저장소 5곳에 **거짓 한 문장**을 심었다 (자진 신고) — *"V0009 의 OAuth 클라이언트는 고아 행으로 남는다(무해)"*. **고아가 아니다: `fan-platform` 의 살아있는 community-service 가 그 자격증명으로 인증한다.** 지우면 프로덕션이 끊긴다

# Status

done

# Owner

monorepo

# Task Tags

- correction
- security
- docs
- self-reported

---

# Dependency Markers

- **원인**: `TASK-MONO-394` (iam community/membership 은퇴, impl PR #2527 `2063027ce` / close #2531 `bd868f44e`).
- **선례**: `TASK-MONO-398` — `TASK-MONO-393` 이 심은 거짓 근거를 자진 신고하고 정정. **이 티켓은 그 패턴의 반복이다** (같은 세션이 이틀 새 두 번).
- **후속**: 없음.

---

# 🔴 무엇이 틀렸나

`TASK-MONO-394` 는 iam 의 `community-service`·`membership-service` 를 은퇴시키면서, **적용된 Flyway 마이그레이션은 불변**이므로 `auth-service` 의 `V0009__seed_community_membership_oauth_clients.sql` 를 남겼다. **그 판단은 옳다.** 그러나 남긴 **이유를 이렇게 적었다**:

> *"두 서비스의 OAuth 클라이언트 행은 **고아 상태로 남는다(무해)**."*

**거짓이다.** 실측:

```
projects/fan-platform/apps/community-service/src/main/resources/application.yml:116
    client-id: ${COMMUNITY_SERVICE_CLIENT_ID:community-service-client}
    # L112 주석: "Uses the pre-seeded community-service-client (IAM V0009, scope membership.read)."
```

| | 실제 |
|---|---|
| `community-service-client` (V0009) | **`fan-platform` 의 배포된 community-service 가 사용하는 살아있는 자격증명.** `client_credentials` → `membership.read` → fan 의 membership-service `/internal/membership/*` 호출 (`HttpMembershipChecker` · `IamClientCredentialsTokenProvider`) |
| `membership.read` / `account.read` 스코프 (V0009) | **fan-platform 테넌트로 스코프됨. 살아있다.** |
| `membership-service-client` (V0009) | V0009 자신의 주석: *"Reserved for future outbound calls"* — 현재 소비자 없음. **하지만 계약에 등록돼 있다** (`auth-api.md` L309, tenant `fan-platform`). |

**계약 파일은 처음부터 옳았다** — `specs/contracts/http/auth-api.md` 는 두 클라이언트를 **`fan-platform` 테넌트**로 정확히 적어뒀다. 틀린 것은 `MONO-394` 의 **서술뿐**이다.

## 왜 위험한가

*"고아니까 무해하다"* 를 읽은 다음 사람이 **정리 마이그레이션으로 revoke** 하면 → **fan 의 community-service 가 membership 접근 체크를 못 하게 된다.** `TASK-FAN-BE-019` 가 그 게이트를 **fail-close** 로 못박았으므로, 토큰 발급 실패는 **프리미엄 피드 전면 차단**으로 나타난다. 조용한 500 이 아니라 기능 정지다.

## 은퇴 자체는 옳았다 — 코드는 맞고 설명이 틀렸다

`MONO-394` 는 **V0009 를 지우지 않았고**, CI 가 그것을 증명했다(`Integration (fan-platform)` · `E2E (fan-platform v1 live-trio)` · fed-e2e 전부 GREEN, 31/31). **잘못된 것은 산출물이 아니라 산출물에 붙인 설명이다.** 그러나 설명은 다음 사람이 읽는 것이고, 이 설명은 그를 프로덕션 사고로 안내한다.

## 뿌리

`MONO-394` 의 교훈은 *"선행 문서의 숫자는 가설이다 — 재측정하라"* 였다. **그 티켓을 쓰면서, V0009 의 소비자를 한 번도 grep 하지 않았다.** *"서비스를 지웠으니 그 클라이언트도 고아겠지"* 라는 **추론을 실측으로 착각했다.** — `MONO-398` 이 `MONO-393` 에 대해 진단한 것과 **같은 병**(*유보를 적을 자리에 확신을 적었다*), 같은 세션에서 이틀 새 두 번.

---

# Scope

**포함 — 거짓 문장이 살아있는 5곳:**

| # | 파일 | 상태 |
|---|---|---|
| 1 | `settings.gradle` L88 — *"those rows are now orphaned and harmless"* | 편집 가능 |
| 2 | `projects/iam-platform/PROJECT.md` § 잔존물 | 편집 가능 |
| 3 | `projects/iam-platform/docs/adr/ADR-001-oidc-adoption.md` — MONO-394 additive note | **ADR 본문 불변 — 내가 붙인 additive note 만 정정** |
| 4 | `tasks/INDEX.md` — MONO-394 DONE 노트 | 편집 가능 |
| 5 | `tasks/done/TASK-MONO-394-*.md` | **🔒 HARDSTOP-05 동결 — 편집 금지.** 정정은 (4) INDEX 에 남긴다 (`MONO-398` 이 `MONO-393` 에 대해 쓴 것과 같은 처리) |

**제외:**

- **`V0009` 는 여전히 건드리지 않는다.** 적용된 마이그레이션은 불변이고, **이제는 지우면 안 될 적극적 이유까지 생겼다**(살아있는 자격증명).
- **`fan-platform` 코드 무수정.** 정상 동작 중이다.
- **`membership-service-client` 의 정리 여부는 이 티켓의 질문이 아니다.** 소비자 0 이지만 계약에 등록돼 있고, 클라이언트 폐기는 **별개 판단**이다. 관찰만 기록한다.

---

# Acceptance Criteria

- **AC-1 (실측 재확인)** — 착수 시 `community-service-client` 의 **살아있는 소비자를 다시 grep** 한다. **탐지식을 아는 답에 먼저 돌려 자기검증**할 것(같은 술어가 `wms-internal-services-client` 의 소비자도 찾아내야 한다 — 빈 결과는 부재가 아니다).
- **AC-2 (거짓 문장 잔존 = 0)** — `git grep -in 'orphan\|고아'` 가 **MONO-394 의 잔존물 서술에서 0건**. (다른 문맥의 "orphan" 은 무관 — 경로/문맥으로 가릴 것.)
- **AC-3 (대체 문장은 *왜 지우면 안 되는지*를 말한다)** — *"불변이라 남긴다"* 로 끝내지 말 것. **"`fan-platform` 의 community-service 가 이 클라이언트로 인증한다 — 지우면 fail-close 게이트가 프리미엄 피드를 전면 차단한다"** 를 명시한다. 다음 사람이 **행동을 바꾸기에 충분한** 문장이어야 한다.
- **AC-4 (동결 파일 우회)** — `tasks/done/TASK-MONO-394-*.md` 는 **편집하지 않는다**. 정정은 `tasks/INDEX.md` DONE 노트에 남기고, 거기서 이 티켓을 가리킨다.
- **AC-5 (빌드 무영향)** — 문서·주석만 바뀌므로 `./gradlew check` 무관하지만, `settings.gradle` 주석 수정이 **gradle 구성을 깨지 않는지** 확인한다(`./gradlew projects` 통과).
- **AC-6 (관찰 기록)** — `membership-service-client` 는 **소비자 0 이지만 계약에 등록**돼 있다는 사실을 정정 문장에 함께 적는다. **"둘 다 고아" 를 "둘 다 살아있다" 로 바꾸는 것도 똑같이 거짓이다.**

---

# Related Specs

- `projects/iam-platform/specs/contracts/http/auth-api.md` § Registered Clients — **처음부터 옳았던 곳**(두 클라이언트 = tenant `fan-platform`). 변경 없음.
- `projects/iam-platform/apps/auth-service/src/main/resources/db/migration/V0009__seed_community_membership_oauth_clients.sql` — **불변. 읽기만.**
- `projects/fan-platform/apps/community-service/.../IamClientCredentialsTokenProvider.java` · `HttpMembershipChecker.java` · `application.yml` — **소비자. 무수정.**
- `TASK-FAN-BE-019` (feed premium gate fail-close) — **왜 자격증명 상실이 조용한 실패가 아닌지**를 설명하는 곳.

# Related Contracts

`auth-api.md` § Registered Clients (변경 없음 — 이미 진실을 말하고 있다).

---

# Edge Cases

- **`tasks/done/` 파일은 HARDSTOP-05 로 동결** — 고치려 들면 훅이 막는다. INDEX 로 우회(AC-4).
- **`membership-service-client` 를 "살아있다" 로 뭉뚱그리지 말 것** — 소비자 0 이다. 정확히 갈라 쓴다(AC-6).
- **"orphan" grep 이 무관한 문맥을 잡는다**(예: JPA orphanRemoval). 경로/문맥으로 가릴 것.

# Failure Scenarios

- **정정 문장이 "불변이라 남긴다" 에서 멈춘다** → 다음 사람이 *"그럼 새 마이그레이션으로 revoke 하면 되겠네"* 라고 읽는다. **불변성은 지우면 안 되는 이유가 아니다** — 살아있는 소비자가 이유다. Guard = AC-3.
- **5곳 중 일부만 고친다** → `MONO-375` 가 겪은 그대로: 정정도 일부 표면에만 닿는다. Guard = AC-2 의 전수 grep.
- **`V0009` 를 "정리" 한다** → **fan 프로덕션 중단.** 이 티켓이 막으려는 바로 그것.

---

# Provenance

`TASK-MONO-394` 머지 후 **"내 변경이 저장소를 정합 상태로 뒀는가"** 를 스스로 재측정하다 발견. 스펙·규칙·문서는 전부 깨끗했다(거짓 선언 0건). **틀린 것은 내가 새로 쓴 문장이었다.**

**이 발견의 값어치는 정정 자체가 아니라 어떻게 나왔는가에 있다**: `V0009` 를 남기기로 한 결정은 옳았고 CI 도 초록이었으므로, **아무 신호도 없었다.** 잡힌 유일한 이유는 **"내가 지운 것의 잔존물을 누가 쓰고 있나" 를 한 번 더 물었기 때문**이다. `MONO-394` 를 수행할 때는 묻지 않았다 — *"서비스를 지웠으니 클라이언트도 고아겠지"* 라는 **추론을 실측이라 착각했다.**

⇒ **`MONO-398`(MONO-393 자진 신고)와 같은 병이 같은 세션에서 이틀 새 두 번.** 공통 지문: **결정은 옳고, 그 결정에 붙인 근거 문장이 측정되지 않았다.**

분석=Opus 4.8 / 구현 권장=**Sonnet** (기계적 문서 정정 5곳 — 판단은 이 티켓이 이미 했다. 단 **AC-3 의 문장은 그대로 옮길 것**: "왜 지우면 안 되는지" 가 이 티켓의 전부다.)
