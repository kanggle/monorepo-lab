# Task ID

TASK-FAN-BE-045

# Title

아티스트 엔티티에 계정이 없어 `ARTIST_POST` 를 **어떤 실제 호출자도 만들 수 없다** — 피드 조인이 요구하는 저자 id 를 발행 경로가 낼 수 없다

# Status

ready

# Owner

fan-platform

# Task Tags

- backend
- domain
- community

---

# 배경

`TASK-MONO-509`(팬 시드) AC-8 이 발굴했다. 시드가 `ARTIST_POST` 를 직접-DB 로 넣는 이유다.

## 세 사실이 서로 맞지 않는다 (전부 실측/코드 확인)

1. **피드는 저자 id 로 잇는다.** `PostJpaRepository.findFeedForFan`:

   ```sql
   WHERE p.authorAccountId IN (SELECT f.artistAccountId FROM Follow f
                               WHERE f.fanAccountId = :fanAccountId ...)
   ```

2. **팔로우 대상은 아티스트 엔티티 id 다.** `artists/[id]/page.tsx`:

   ```tsx
   <FollowButton artistAccountId={artist.id} initialFollowing={false} />
   ```

   그리고 `artists` 테이블에는 **`account_id` 컬럼이 없다**(실측 — 컬럼 15개 전수).
   `RegisterArtistRequest` 에도 id/계정 필드가 없어 외부에서 묶을 수 없다.

3. **발행은 저자를 호출자로 고정한다.** `PublishPostUseCase`:

   ```java
   Post draft = Post.createDraft(postId, actor.tenantId(), actor.accountId(), ...);
   ```

⇒ `ARTIST_POST` 가 피드에 뜨려면 `author_account_id == artists.id` 여야 하는데,
**그 값을 낼 수 있는 호출자가 존재하지 않는다.** ARTIST 역할 계정이 써도, 운영자가
써도(`TASK-MONO-512` 가 열린다 해도) 저자는 그 사람의 `sub` 이 되고, 그 글은
**그 아티스트를 팔로우한 팬의 피드에 뜨지 않는다.**

## 왜 지금까지 초록이었나

`PublishPostUseCase` 의 ARTIST 역할 게이트는 **단위 테스트로 잘 덮여 있다** — 역할이
없으면 `PermissionDeniedException` 이 난다는 것을. 그러나 그 테스트들은 저자 id 가
**팔로우 대상과 이어지는지**를 묻지 않는다. 발행과 피드가 각각 초록인데 둘을 잇는
불변식은 아무도 단언하지 않는다.

---

# Goal

`ARTIST_POST` 를 **실제 호출자가** 만들 수 있고, 그 글이 그 아티스트를 팔로우한 팬의
피드에 뜬다. 그리고 그 연결이 테스트로 고정된다.

---

# Scope

## In Scope

- 아티스트 엔티티 ↔ 계정의 연결 모델 결정
- `PublishPostUseCase` 의 저자 결정 규칙
- 발행 ↔ 피드 조인을 **함께** 단언하는 테스트

## Out of Scope

- 운영자 역할 발급 가능성 — `TASK-MONO-512`. **둘은 독립이다**: 그 티켓이 열려도
  이 문제는 남고, 이 티켓이 풀려도 그 문제는 남는다
- 팬 게시물 작성 UI — `TASK-FAN-FE-016`

---

# 🔴 고르기 전에 물어야 할 것

세 가지 형태가 가능하고, 셋이 서로 다른 제품을 만든다:

| 안 | 모양 | 대가 |
|---|---|---|
| A. `artists.account_id` 추가 | 아티스트가 로그인하는 실제 계정을 가진다 | 아티스트 온보딩(계정 발급) 경로가 필요하다 |
| B. 발행 시 `authorAccountId` 를 파라미터로 | 운영자가 "이 아티스트 이름으로" 쓴다 | **저자 위조 표면**이 열린다 — 누가 어느 id 로 쓸 수 있는지 규칙이 필요 |
| C. 팔로우/피드를 아티스트 id 가 아닌 **계정 id** 로 재정의 | 조인의 의미를 바꾼다 | 프런트·follows 테이블·기존 행 마이그레이션 |

**A 가 기본값이다** — 이 도메인은 "아티스트가 글을 쓴다" 를 모델링하고 있고, 계정이
없다는 것이 그 모델의 구멍이기 때문이다. B 를 고른다면 위조 방지가 AC 가 되어야 한다.

---

# ✅ AC-0 재측정 (2026-08-07) — 세 사실 전부 유지, **신규 발견 2건**

## ① 피드 조인 — 그대로 (원문 인용)

```java
// PostJpaRepository.findFeedForFan
WHERE p.tenantId = :tenantId
  AND p.authorAccountId IN (SELECT f.artistAccountId FROM Follow f
                            WHERE f.fanAccountId = :fanAccountId AND f.tenantId = :tenantId)
```

## ② `artists` 컬럼 전수 (AC-0 이 명시적으로 요구) — **계정 컬럼 0개**

마이그레이션 2개(`V1__init.sql`·`V2__artist_outbox_v2.sql`) 전수. `artists` 는 16컬럼:

```
id · tenant_id · artist_type · status · stage_name · real_name · debut_date · agency
bio · profile_image_ref · created_at · updated_at · published_at · archived_at · version
```

`account` 를 포함하는 컬럼/ALTER **0건**. ⇒ 아티스트 엔티티에 계정이 없다는 전제 유지.

## ③ `PublishPostUseCase` 저자 대입 — 그대로

```java
Post.createDraft(..., actor.tenantId(), actor.accountId(), ...)   // 저자 = 호출자
```

---

## 🔴 신규 1 — `Follow.artistAccountId` 는 **검증되지 않는 자유 문자열**이다

```java
public FollowResult execute(String artistAccountId, ActorContext actor) {
    if (artistAccountId.equals(actor.accountId())) throw new SelfFollowForbiddenException();
    ...
    Follow.create(actor.accountId(), artistAccountId, actor.tenantId());   // 그대로 저장
}
```

호출자가 준 값을 **그대로** 저장한다. 아티스트 엔티티 존재 확인도, 형식 검사도 없다.
티켓은 *"팔로우 대상은 아티스트 엔티티 id"* 라고 적었지만 그것을 강제하는 것은
**프런트엔드의 관례**이지 백엔드가 아니다.

⇒ 이 필드는 이름만 `artistAccountId` 이고 실제로는 **아무것과도 이어져 있지 않다.**
AC-1 의 A/B/C 는 "저자 id 를 어디서 얻나" 뿐 아니라 **"팔로우 대상이 무엇인지 누가
보증하나"** 까지 결정해야 한다 — 두 끝이 같은 id 공간에 있다는 보장이 지금은 0이다.

## 🔴🔴 신규 2 — `ARTIST` 역할은 **IdP 가 발급할 수 없다**

`PublishPostUseCase:32` 가 `ARTIST` 를 저작 권한으로 받는다:

```java
if (cmd.postType() == PostType.ARTIST_POST
        && !actor.hasRole(ROLE_ARTIST)
        && !actor.isOperator()) {
    throw new PermissionDeniedException("ARTIST role required to publish ARTIST_POST");
}
```

🔴 **정정 (2026-08-07, 같은 날 늦게).** 이 인용문은 처음에 `!hasRole(ROLE_FAN) &&
!hasRole(ROLE_ARTIST) && !isOperator()` 로 잘못 적혀 PR #3255 로 머지됐다. 실제 조건에
`ROLE_FAN` 은 **없다**. `ADR-003` 이 같은 코드를 정확히 인용하고 있었는데 나는 그것을
열지 않고 내 grep 결과를 옮겨 적었다 — **이 세션이 반복해서 벌받은 바로 그 실수**를,
그 실수를 문서화하는 작업 중에 저질렀다.

🔵 **결론은 바뀌지 않고 오히려 더 강해진다**: `ARTIST_POST` 는 `ARTIST` **또는** 운영자를
요구하는데, `ARTIST` 는 iam 에 0건이고 fan 의 `isOperator()` 가 받는 `FAN_OPERATOR` 도
발급 경로가 0 이다(이 티켓 본문). ⇒ **두 갈래가 모두 닫혀 있어** 실제 호출자는
`ARTIST_POST` 를 만들 수 없다.


그런데 `projects/iam-platform` 전체(`*.java`/`*.yml`/`*.sql`)에서 `ARTIST` **0건**.
🔵 계측기 검증: 같은 글롭에서 `FAN_OPERATOR` 는 3건 잡힌다 ⇒ 진짜 부재.

⇒ **[[TASK-MONO-512]] 와 같은 모양이고, 그 티켓이 놓친 두 번째 역할이다.** B 안("저자 id 를
지정할 수 있는 주체를 제한") 을 고르면 그 "주체" 를 역할로 표현하게 될 텐데, 지금
저작용 역할은 **아무도 받을 수 없다** — 두 티켓의 결정이 얽힌다. 🔴 순서를 정하지 않고
따로 구현하면 한쪽이 다른 쪽을 무효화한다.

---

---

# ✅ 게이트 해제 — `ADR-MONO-059` **ACCEPTED — A** (2026-08-07)

AC-0 재측정이 이 티켓과 **`TASK-MONO-512`** 을 같은 코드 한 줄로 수렴시켰고, 두 티켓의
결정을
[`docs/adr/ADR-MONO-059-fan-authoring-identity-plane.md`](../../../../docs/adr/ADR-MONO-059-fan-authoring-identity-plane.md)
하나로 묶었다. 소유자가 **A** 를 정확형으로 승인했다.

## A 채택이 이 티켓에 지정하는 것

ADR § 결과 표 A 행: *"`FAN-BE-045` = **스키마 + 온보딩 + 조인 검증**"*. 셋 다 이 티켓이다.

1. **스키마** — `artists` 에 계정 참조(`account_id`)를 더한다. 현재 16컬럼 중 계정 컬럼 0개.
   🔵 기존 행 백필은 데모라 시드로 무해(ADR § A 가 이미 그렇게 적었다).
2. **온보딩** — 아티스트 계정을 발급하는 경로. 🔴 **역할 부여(`ARTIST`)는 `TASK-MONO-512`**
   가 맡는다 — 이 티켓은 계정을 만들고 연결하는 데까지다. 둘이 만나야 게이트가 열린다.
3. **조인 검증** — `FollowArtistUseCase` 가 `artistAccountId` 를 **무검증 저장**하는 문제.
   A 에서는 검증 대상이 생긴다(`artists.account_id` 참조) ⇒ **이 티켓 범위 안**이다.
   🔴 ADR § 결과 말미가 *"어느 안이든 별도로 남는다"* 고 적은 그 항목이며, A 는 그것을
   **없애는 것이 아니라 검증 가능하게** 만든다.

## 🔴 B 가 배제됐다 — AC-4 는 해당 없음

**저자 id 를 파라미터로 받지 않는다.** 저자는 지금처럼 **인증된 호출자**(`actor.accountId()`)
로 남는다 ⇒ 위조 표면이 생기지 않고, 아래 AC-4(임의 id 로 쓰기 → 403)는 **성립할 대상이
없으므로 종결**한다. 대신 조인의 두 끝이 같은 id 공간이라는 것이 **정의상** 보장돼야 한다
(AC-6).

---

# ⛔ AC-6 PAUSED on `ADR-004` (HARDSTOP-09, 2026-08-11 착수 시)

착수 직전 전제 재측정에서 **`ADR-MONO-059` 의 전제 하나가 무너졌다.** A 를 고른 근거 문장은

> `FollowArtistUseCase` 의 무검증 필드에 검증할 대상이 생긴다(`artists.account_id` **참조**)

인데, 그 "참조" 는 **로컬 참조를 전제**한 표현이다. 실측:

| 테이블 | 서비스 | 데이터베이스 |
|---|---|---|
| `follows` | community-service | `fanplatform_community` |
| `artists` | artist-service | `fanplatform_artist` |

**서로 다른 데이터베이스**이고, `specs/services/community-service/architecture.md`
§ Forbidden dependencies 는 *"community-service does not reach into **artist-service** …
never a DB-level reach-in"* 으로 우회로를 이미 막아 뒀다. `ADR-MONO-059` 본문 어디에도
cross-service 라는 말이 없다 ⇒ **ADR 이 이 사실을 모르는 채로 AC-6 을 이 티켓에 배정했다.**
(같은 형태를 `TASK-SCM-BE-059` 가 *"그 ADR 은 내가 썼다"* 로 이미 겪었다.)

남은 기전은 **동기 호출** 아니면 **이벤트 투영** 뿐이고 **둘 다 새 크로스서비스 간선**이라
`architecture-decision-rule.md` § Mandatory Rule 2항 → ADR + PAUSE 다.
⇒ [`docs/adr/ADR-004-artist-account-existence-seam.md`](../../docs/adr/ADR-004-artist-account-existence-seam.md)
**Proposed** 제출(A 동기 internal 엔드포인트 / B 이벤트 투영 / C 검증 안 함을 명시 결정으로).

🔴 **AC-1 의 "별도 ADR 을 추가로 올리지 않는다" 와 충돌하지 않는다.** 그 문장이 막는 것은
**같은 결정**(어느 신원 평면인가)을 두 곳에 적는 것이다. `ADR-004` 는 그 결정을 다시 열지
않고 — A 는 확정이다 — **A 를 어떤 이음매로 실행하는가**만 정한다. `ADR-MONO-059` 는 그
질문을 제기한 적이 없다.

🔵 **PAUSE 범위는 AC-6 하나뿐이다.** AC-1b(스키마 + 온보딩) · AC-2/AC-3(발행 → 피드 도달 +
음성 대조) · AC-5(시드 회수)는 세 안 어디서도 같으므로 게이트되지 않는다. 다만 이 티켓은
*"컬럼만 추가하고 조인 검증을 빼먹는다"* 를 Failure Scenario 로 적어 두었으므로, **AC-6 이
정해지기 전에 AC-1b 만 랜딩하면 그 시나리오를 스스로 실행하는 것**이다 ⇒ ACCEPT 를 기다린다.

---

# ✅ 게이트 해제 — `ADR-004` **ACCEPTED — A** (2026-08-11)

**동기 internal 엔드포인트.** artist-service 에 `/internal/**` 표면을 두고 community-service 가
동기·**fail-closed** 로 호출해 `Follow.artistAccountId` 를 검증한다
(`HttpMembershipChecker`/FAN-BE-010 + `ADR-MONO-005` Order(1) 체인과 같은 모양).

**B 배제** ⇒ community-service 는 인바운드 이벤트 컨슈머가 **되지 않는다**
(`architecture.md` L26/L33 유지). **C 배제** ⇒ AC-6 은 살아 있다.

## 🔴 ACCEPT 가 답하지 않은 것 — e2e 탈출구 (구현이 명시적으로 답한다)

ADR § 추천 말미가 *"A 를 고를 경우 **반드시 함께 결정되는 것**"* 으로 e2e 탈출구의 모양을
제기하며 두 가지를 나란히 놓았다 — **두지 않는다** / 두되 **거부 쪽 기본값**. 도착한 것은
**plain `A`** 이고 rider 는 언급되지 않았다 ⇒ **싣기로도 안 싣기로도 확정되지 않았다.**

`ADR-MONO-060` 의 `act` 클레임이 정확히 같은 자리였고, 그때의 처리를 그대로 따른다:
**구현이 답하고 그 답을 기록한다**(조용히 빠뜨리는 것은 답이 아니다). ⇒ 아래 **AC-7**.

🔴 **membership 의 `AlwaysAllowMembershipChecker` 를 복사하지 말 것** — 그 모양이면 검증을
넣고도 **꺼진 채 초록**이 된다(ADR § Decision Drivers 3 · `MONO-360`). 기본 권장은 탈출구를
두지 않는 것이고(artist-service 는 live-trio e2e 에 이미 떠 있다), 두어야 한다면 **거부**가
기본값이어야 한다.

## 🔴 계약 선갱신 — 코드보다 먼저

ADR § 결과 A 행이 **계약 선갱신**을 명시한다. `specs/contracts/http/` 에 artist-service 의
새 internal 엔드포인트를 **구현 전에** 올린다(CLAUDE.md § Layer Rules). **ACCEPT 는 그
계약의 *내용*을 승인하지 않았다** — 형태·상태코드·에러코드는 이 티켓이 정하고 리뷰가 본다.

---

# 📍 착수 현황 — 2회차까지 (2026-08-11). 다음 세션은 여기서 이어간다

**브랜치**: `task/fan-be-045-artist-account`
**worktree**: `C:/Users/kangdow/dev/project/ai-project/wt-fan-artist-identity`
**PR**: 아직 없음. **계약 2건이 다 올라갔으므로 다음은 코드다**(V3 → 도메인/어댑터 → AC-6).

## ✅ 끝난 것

1. **AC-6 기전 결정** — `ADR-004` **ACCEPTED — A**(동기 internal 엔드포인트), main 에 랜딩.
2. **계약 1/2 랜딩(커밋 `0998c70b3`)** — `artist-api.md` 에
   `GET /internal/artists/exists?accountId=&tenantId=` → `200 {"exists": bool}`.
   membership 의 `/internal/membership/access` 를 1:1 미러(workload identity /
   게이트웨이 미라우팅 / fail-closed). 아티스트 `status` 는 **의도적 미노출**(사유는 계약 본문).
3. **계약 2/2 랜딩 (2026-08-11, 2회차)** — `POST /api/artists` 에 `accountId`
   (`artist-api.md` § `accountId`) + `data-model.md` 의 컬럼·제약·V3 백필 의무.
   **코드는 아직 한 줄도 없다**(계약 선갱신 순서 준수). 정한 것 6가지:
   - **필수(NOT NULL)** — 선택이면 *디렉터리에는 보이는데 팔로우는 영영 안 되는* 아티스트가
     생긴다(AC-6 이 거절하므로). 결함이 모양만 바꿔 재발한다. 값은 항상 확보 가능 —
     `ADR-MONO-059` A 가 계정 발급을 IAM 에 배정했으므로 등록 시점에 계정이 이미 있다.
   - **`(tenant_id, account_id)` UNIQUE** → 409 `ARTIST_ACCOUNT_CONFLICT`(신규 코드).
     한 사람이 여러 페르소나를 갖는가는 아무도 안 정했다 ⇒ 보수적인 쪽. 나중에 푸는 것은
     제약 해제라 계약 파괴가 아니다.
   - **불변 — PATCH 불가.** 재바인딩하면 `follows`/`posts`(다른 DB)의 옛 값이 남아 **기존
     팔로워 전원이 조용히 떨어져 나가고** 기존 글이 고아가 되는데, artist-service 는 그쪽을
     고칠 수 없다(Forbidden dependencies). 데이터 마이그레이션 문제지 요청 필드가 아니다.
     🔴 판정은 "필드를 안 넣었다" 가 아니라 **PATCH 로 값이 안 바뀐다는 테스트** — 필드 부재의
     강도는 JSON 바인더의 unknown-field 기본값만큼밖에 안 된다(계약에 명시).
   - **IAM 실재 검증 안 함(명시적 결정)** — artist → IAM 은 `ADR-004` 가 승인한 간선이
     아니다(승인된 것은 community → artist 하나). 대가를 그대로 적었다: 오타난 `accountId` 는
     *아무도 저작할 수 없는 아티스트*를 만들고 첫 발행 시도에서야 드러난다. admin-tier 전용이라
     수용.
   - **기존 3행 백필 = `account_id := id`(항등)** — 시드가 이미 조인의 **양쪽 모두**에
     아티스트 **엔티티 id** 를 쓰고 있다(follows 는 API 로, posts 는 직접-DB). 항등이 아니면
     AC-6 이 랜딩하는 날 **시드 자신의 팔로우 호출이 거절된다**. 🔴 백필값은 실재하는 IAM
     subject 가 아니다 — 그 절반은 `MONO-512`.
   - **읽기 응답에 `accountId` 노출** — 비밀이 아니고(피드가 이미 `authorAccountId` 를 노출),
     프런트가 팔로우하려면 필요하다.

## 🔴 2회차가 새로 드러낸 것 — 프런트 한 줄이 곧 틀린 값이 된다

`artists/[id]/page.tsx:30` 이 `<FollowButton artistAccountId={artist.id} />` 다.
이것은 **`account_id == id` 인 동안만** 맞다 — 즉 백필된 데모 3행에서만. 실제 IAM subject 로
등록된 아티스트에서는 **틀린 값을 보내고 AC-6 이 그 팔로우를 거절한다.**
⇒ `artist.accountId` 를 읽도록 고쳐야 하고, `entities/artist/types.ts` 의 `Artist` 에도
필드를 더해야 한다. 🔵 **이 티켓 안에서 처리한다** — AC-1b 의 "온보딩" 이 제품에 도달하는
마지막 한 칸이고, 두 파일·두 줄이라 별도 티켓을 세울 무게가 아니다.
🔴 조용히 빠뜨리면 *데모에서만 초록인* 상태로 닫히게 된다(백필 덕에 증상이 안 보인다).

## 🔴 1회차 실측이 바꾼 것 두 가지

**① AC-5 는 이 티켓이 풀 수 없다 — `TASK-MONO-512` 가 푼다.**
`seed-fan.sh` 의 `dbexec --why` 가 두 티켓을 **함께** 사유로 들어서 이 티켓 몫으로 읽히는데,
실제 차단은 역할이다. 실측:

```java
// artist-service config/SecurityConfig.java
ADMIN_ROLES = { "ADMIN", "OPERATOR", "SUPER_ADMIN", "FAN_OPERATOR" };
.requestMatchers(HttpMethod.POST, "/api/artists/**", "/api/artists").hasAnyRole(ADMIN_ROLES)
```

⇒ 아티스트를 **API 로 만들려면** 그 역할이 필요하고, **그것을 발급할 경로가 없다는 것이
정확히 `MONO-512` 의 결함**이다(시드 실측 `POST /api/v1/artists` → **403 FORBIDDEN**).
AC-5 의 *"풀렸다면"* 은 조건부로 옳게 쓰였고, **그 조건의 주체가 이 티켓이 아니다.**
⇒ 이 티켓은 **왜 아직 안 옮기는지**를 적고 넘긴다. AC-5 갱신 완료(아래).

**② "온보딩" 의 경계는 이미 ADR 이 정해 뒀다 — 새 게이트 없음.**
계정을 누가 만드는가가 또 하나의 크로스서비스 **쓰기** 간선이 될까 봐 멈춰서 확인했는데,
`ADR-MONO-059` § 선택지 A 가 명시한다 — *"계정 발급 + `ARTIST` 역할 부여를 **iam 이 해야
하고**"*. ⇒ **fan-platform 은 계정을 만들지 않는다.** 이 티켓 몫은 **스키마 + 계정 id 를
받아 연결 + 검증**이고, 계정 생성·역할 부여는 IAM 쪽(= `MONO-512`).
실측 보강: fan 앱 어디에도 IAM provisioning 호출부 **0건**(신설하지 않는다).

## ▶️ 다음 세션의 작업 순서

- [x] **계약 2/2 (코드보다 먼저) — 완료 2026-08-11.** `artist-api.md` § `accountId` +
      `data-model.md` 컬럼·제약·V3 백필 의무. 정한 6가지는 위 § 착수 현황 3.
- [ ] **V3 마이그레이션** — `artists.account_id VARCHAR(36)` + `UNIQUE (tenant_id,
      account_id)`. 🔵 백필 정책은 계약이 정했다(**항등 `account_id := id`**) 그대로 구현.
      `SCM-BE-059` V6 교훈대로 *"NULL 을 남길 수 없고 충돌할 수 없음"* 증명을 주석에 —
      **증명 본문은 `data-model.md` § V3 백필 의무에 이미 적혀 있다**(nullable 추가 →
      `SET account_id = id` → `SET NOT NULL`; `id` 는 PK 라 NULL 없음·전역 유일 ⇒ 더 느슨한
      키가 안 부딪히므로 더 조인 키도 못 부딪힌다).
- [ ] **도메인/어댑터** — `Artist` 엔티티에 `accountId`(현재 필드: id·tenantId·artistType·
      status·profile·타임스탬프·version, 계정 컬럼 0개) · `RegisterArtistRequest` ·
      `ArtistView`(응답 노출) · 매퍼. 🔴 `PATCH` 로는 안 바뀐다는 **테스트**까지가 한 벌.
      🔴 기존 호출부 전수 갱신 필요 — `ArtistApiContractTest` ·
      `ArtistControllerSliceTest` · `AdminRoleEnforcementIntegrationTest` ·
      `ArtistAndPostFlowE2ETest` / `E2ETestFixtures` (필수 필드라 전부 컴파일/실행이 깨진다).
- [ ] **프런트 2줄** — `entities/artist/types.ts` 의 `Artist` 에 `accountId` 추가 +
      `artists/[id]/page.tsx:30` 을 `artist.accountId` 로. 사유는 위 § 2회차 발견.
- [ ] **AC-6** — artist-service `/internal/**` Order(1) 보안 체인 **신설**(현재 `internal`
      참조 **0건** — 두 번째 인스턴스가 아니라 신설이다) + `InternalArtistController` +
      community `ArtistAccountChecker` 포트/HTTP 어댑터 + `FollowArtistUseCase` 배선.
      🔴 판정은 배선이 아니라 **잘못된 id 거부 + artist-service 내렸을 때 팔로우가 안 열림**.
- [ ] **AC-7** — 탈출구 **두지 않는다**로 답하고 근거를 코드/티켓에 적는다.
      🔵 근거는 측정됐다: live-trio e2e 잡이 띄우는 것은 `community-service` +
      `artist-service` 이고 **membership-service 는 없다** — 즉 membership 이 탈출구를
      가진 이유(그 서비스가 e2e 에 없음)가 artist 에는 **성립하지 않는다**.
      🔴 `AlwaysAllowMembershipChecker` 형 복사 금지.
- [ ] **AC-2/AC-3** — 발행 → 팔로워 피드 도달(같은 테스트 안에서) + 비팔로워 음성 대조.
- [ ] **AC-5** — 아래 갱신된 판정대로 "안 옮긴다 + 사유" 를 적고 `MONO-512` 로 넘긴다.

# Acceptance Criteria

- [x] **AC-0 (재측정) — 완료 2026-08-07.** 세 사실 전부 유지(`artists` 16컬럼 중 계정 0개 ·
      저자 = 호출자 · 조인 원문 동일) · 🔴 신규 2건: `Follow.artistAccountId` **무검증** ·
      `ARTIST` 역할 **발급 경로 0** (MONO-512 와 얽힘). 상세는 위 §
- [x] **AC-1 (결정) — 완료 2026-08-07.** `ADR-MONO-059` **ACCEPTED — A**. 결정이
      monorepo-level ADR 로 올라갔으므로 `projects/fan-platform/docs/adr/` 에 별도 ADR 을
      **추가로 올리지 않는다**(같은 결정을 두 곳에 적으면 갈라진다)
- [ ] **AC-1b (스키마 + 온보딩)** — `artists.account_id` 추가 + 아티스트 계정 발급 경로.
      🔴 `ARTIST` **역할 부여는 `TASK-MONO-512`** 범위 — 여기서 겹쳐 구현하지 말 것
- [ ] **AC-2 (도달 가능성)** — 실제 호출자가 `ARTIST_POST` 를 만들고, **같은 테스트 안에서**
      그 아티스트를 팔로우한 팬의 피드에 그 글이 뜨는 것을 단언한다.
      🔴 발행 201 만 단언하는 테스트는 이 결함을 재발시킨다 — **조인을 건너뛰기 때문이다**
- [ ] **AC-3 (음성 대조)** — 팔로우하지 않은 팬의 피드에는 뜨지 않아야 한다.
      양성만으로는 "이어졌다" 와 "모두에게 보인다" 를 구별할 수 없다
- [x] **AC-4 (B 를 골랐을 때) — 해당 없음.** B 가 배제됐다(ADR-059 ACCEPT). 저자는 인증된
      호출자로 남으므로 "저자 id 를 지정하는 주체" 자체가 존재하지 않는다
- [ ] **AC-6 (조인 검증 — A 가 새로 요구) — 🟢 게이트 해제 (`ADR-004` ACCEPTED — A).**
      기전은 **동기 internal 엔드포인트 + fail-closed** 로 확정됐다(투영/미검증 배제).
      🔴 판정은 "클라이언트를 배선했다" 가 아니라 **잘못된 `artistAccountId` 가 실제로
      거부되는 것**이고, artist-service 를 내렸을 때 **팔로우가 열리지 않는 것**(fail-closed)
      까지 함께 단언한다 — 열리면 그건 fail-open 이고 검증이 없는 것과 같다.
      `FollowArtistUseCase` 가 받는
      `artistAccountId` 가 **실재하는 `artists.account_id` 인지 검증**하고, 아닌 값은
      거부한다(테스트로 고정). 🔴 지금은 무검증 저장이라 피드 조인이 **우연히만** 성립한다 —
      A 의 요점이 그 우연을 구조로 바꾸는 것이므로, 이것을 빼면 A 를 고른 이유가 사라진다
- [ ] **AC-5 (시드 회수) — 🔴 이 티켓으로는 안 풀린다(2026-08-11 실측). `MONO-512` 가 푼다.**
      `dbexec --why` 가 이 티켓과 `MONO-512` 를 **함께** 사유로 들어 이 티켓 몫으로 읽히지만,
      실제 차단은 **역할**이다: `POST /api/artists` 는 `hasAnyRole(ADMIN, OPERATOR,
      SUPER_ADMIN, FAN_OPERATOR)` 인데 그 역할을 **발급할 경로가 없다는 것이 `MONO-512`**
      다(시드 실측 403 FORBIDDEN). ⇒ 이 티켓에서는 **"안 옮긴다 + 사유"** 를 적고 넘긴다.
      🔵 아래 원문의 *"풀렸다면"* 은 조건부로 옳게 쓰였다 — 틀린 것은 그 조건의 **주체**를
      이 티켓으로 읽은 것이다. 원문 보존:
      풀렸다면 그 블록을 API 로 옮긴다.
      🔴 옮길 때 **저자는 여전히 데모 계정이 아니어야 한다** — 데모 계정이 저자면
      `actor.owns()` 로 가시성 게이팅이 통째로 우회돼 시연이 공허해진다
- [ ] **AC-7 (ACCEPT 가 남긴 rider 에 답한다 — `ADR-004` § 결정)** — e2e 탈출구를
      **두는가 두지 않는가**를 명시적으로 답하고, 그 답과 사유를 코드/티켓에 남긴다.
      🔴 **조용히 빠뜨리는 것은 답이 아니다**(`ADR-MONO-060` 의 `act` 처리와 동형).
      두지 않기로 하면 live-trio e2e 에서 artist-service 가 실제로 떠 있는지 확인해
      그 근거를 적고, 두기로 하면 **기본값이 거부**임을 테스트로 고정한다.
      🔴 `AlwaysAllowMembershipChecker` 형(항상 통과)은 **선택지가 아니다** — 그 모양이면
      검증이 꺼진 채 초록이 된다(ADR § Drivers 3)

---

# Related Specs

- `projects/fan-platform/specs/services/community-service/`
- `apps/community-service/.../infrastructure/jpa/PostJpaRepository.java` (피드 JPQL)
- `apps/community-service/.../application/PublishPostUseCase.java`
- `apps/artist-service/.../adapter/in/web/dto/request/RegisterArtistRequest.java`
- `web/fan-platform-web/src/app/(main)/artists/[id]/page.tsx` (`artistAccountId={artist.id}`)
- `infra/demo/seed/seed-fan.sh`

# Edge Cases

- `follows` 테이블에 이미 아티스트 엔티티 id 로 된 행이 있다(데모 시드 포함) — C 안은
  마이그레이션이 필요하다
- `PostAccessGuard` / `GetFeedUseCase` 의 `actor.owns(author)` 우회는 저자 모델이 바뀌면
  **의미가 달라진다**: 아티스트 계정이 생기면 그 계정은 자기 유료 글을 무료로 본다(정상)

# Failure Scenarios

- **발행만 테스트하고 끝낸다** — 지금과 같은 상태로 되돌아간다. 두 초록이 사이의
  불변식을 증명하지 않는다
- ~~**B 를 고르고 위조 방지를 빠뜨린다**~~ — B 배제로 해당 없음
- 🔴 **`account_id` 컬럼만 추가하고 조인 검증(AC-6)을 빼먹는다** — 스키마는 생겼는데
  `FollowArtistUseCase` 는 여전히 아무 값이나 받는다 ⇒ 조인은 **여전히 우연**이고, A 를
  고른 이유가 통째로 사라진다. 컬럼의 존재는 보증이 아니다
- 🔴 **512 의 역할 부여를 여기서 같이 해 버린다** — 두 티켓이 같은 파일을 나눠 갖게 되고
  merge 시점에 충돌한다. 겹치면 **직렬로** 진행할 것

# Definition of Done

- [x] 결정 — `ADR-MONO-059` ACCEPTED — A (2026-08-07)
- [x] 이음매 결정 — `ADR-004` ACCEPTED — A, 동기 internal 엔드포인트 (2026-08-11)
- [x] 계약 선갱신 (코드보다 먼저) — 1/2 internal 엔드포인트 · 2/2 `POST /api/artists`
      `accountId` + `data-model.md` (2026-08-11)
- [ ] 스키마(`artists.account_id`) + 온보딩 구현
- [ ] AC-6 조인 검증(무검증 저장 제거) + fail-closed 단언
- [ ] AC-7 e2e 탈출구 rider 에 명시적으로 답하고 기록
- [ ] AC-2/AC-3 테스트(발행 → 피드 도달, 음성 대조 포함)
- [ ] `seed-fan.sh` 회수 여부 명시
- [ ] Ready for review
