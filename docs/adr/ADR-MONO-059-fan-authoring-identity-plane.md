# ADR-MONO-059: fan-platform 의 **저작 신원 평면** — 아티스트 글을 쓸 수 있는 주체를 무엇으로 표현하는가

**Status:** PROPOSED
**Date:** 2026-08-07
**History:** PROPOSED 2026-08-07 (this record). **ACCEPT is a human gate — this record authorises no code.** 이 저장소의 ADR 규약상 승인은 소유자의 **정확형** 지시를 요구하며(`ADR-MONO-059 ACCEPTED — <A|B|C|D>`), 일반적인 "진행"/"proceed" 는 이것을 승인하지 않는다. 작성 에이전트는 자기 제안을 스스로 ACCEPT 할 수 없다.
**Decision driver:** `TASK-MONO-512` 와 `TASK-FAN-BE-045` 의 AC-0 재측정(2026-08-07, PR #3255)이 두 티켓을 **같은 코드 한 줄**로 수렴시켰다 — `PublishPostUseCase` 의 `ARTIST_POST` 게이트가 요구하는 두 통과 경로가 **둘 다 발급 경로 0** 이다.
**Related:** `TASK-MONO-512`(팬 운영자 역할 발급 불가) · `TASK-FAN-BE-045`(아티스트 저자 부재) · `ADR-003`(fan-platform, `FAN_POST` **가시성 티어** — 같은 use case 를 인용하지만 **다른 질문**: 이 ADR 은 *누가 저자가 될 수 있나*, ADR-003 은 *어떤 티어로 쓸 수 있나*) · `ADR-MONO-019`/`ADR-MONO-020`(운영자 멀티테넌트 배정) · `TASK-BE-576`(권한 테넌트 ≠ 가시성 테넌트)

---

## 왜 ADR 이 하나인가 — 두 티켓이 **같은 하나의 결여**를 다른 각도에서 보고 있다

`TASK-MONO-512` 와 `TASK-FAN-BE-045` 는 별개로 파일됐지만, 2026-08-07 AC-0 재측정
(PR #3255)에서 **같은 코드 한 줄**로 수렴했다:

```java
// PublishPostUseCase#execute — ARTIST_POST 저작 게이트 (원문)
if (cmd.postType() == PostType.ARTIST_POST
        && !actor.hasRole(ROLE_ARTIST)
        && !actor.isOperator()) {
    throw new PermissionDeniedException("ARTIST role required to publish ARTIST_POST");
}
```

이 게이트를 통과하는 길은 **정확히 둘**이고, **둘 다 닫혀 있다**:

| 통과 경로 | 왜 닫혀 있나 | 근거 |
|---|---|---|
| `hasRole("ARTIST")` | `projects/iam-platform` 전체(`*.java`/`*.yml`/`*.sql`)에 `ARTIST` **0건** | 대조군: 같은 글롭에서 `FAN_OPERATOR` 3건 ⇒ 계측 실패 아님 |
| `isOperator()` | fan 의 `ActorContext.isOperator()` 는 `FAN_OPERATOR` 를 받는데, 그 역할은 **어떤 테넌트도 `fan` 을 구독하지 않아** 파생되지 않는다 | `tenant_domain_subscription` 18행/12테넌트 중 `fan` **0건** |

⇒ **`ARTIST_POST` 는 어떤 실제 호출자도 만들 수 없다.** 시드가 직접-DB 로 넣고 있는 이유가
이것이고(`seed-fan.sh` 의 두 번째 `dbexec --why`), 두 티켓은 그 하나의 사실을 각각
"역할이 발급 안 된다"(512)와 "저자가 없다"(045)로 본 것이다.

🔴 **그래서 따로 결정하면 한쪽이 다른 쪽을 무효화한다.** 512 를 "역할을 발급한다" 로 풀면
045 의 B안(운영자 대리 저작)이 자동으로 열려 버리고, 045 를 A안(아티스트 계정)으로 풀면
512 의 운영자 평면 질문이 **불필요해질 수도** 있다. 순서가 아니라 **한 번의 선택**이 맞다.

---

## 실측 (2026-08-07, 전부 재측정 — 추론 아님)

```
tenant_domain_subscription  ecommerce 2 · erp 3 · finance 5 · scm 3 · wms 5 · fan 0
tenants                     fan-platform 존재, 구독 NULL
operator_tenant_assignment  acme-corp · demo-corp · ecommerce · globex-corp  (fan-platform 없음)
assume fan-platform         실패        assume fan  실패        assume demo-corp  성공(대조군)
artists 컬럼 16개           id·tenant_id·artist_type·status·stage_name·real_name·debut_date
                            ·agency·bio·profile_image_ref·created_at·updated_at·published_at
                            ·archived_at·version   ⇒ 계정 컬럼 0개
PublishPostUseCase          Post.createDraft(..., actor.accountId(), ...)   저자 = 호출자
findFeedForFan              p.authorAccountId IN (SELECT f.artistAccountId FROM Follow f …)
FollowArtistUseCase         호출자가 준 artistAccountId 를 무검증 저장
```

### 🔴 조인의 두 끝이 같은 id 공간이라는 **보장이 0** 이다

피드는 `authorAccountId`(= 발행자의 계정)와 `artistAccountId`(= 팔로우 시 클라이언트가 준 값)를
잇는다. 그런데 후자는 백엔드가 아무 검증도 하지 않는다 — 아티스트 엔티티 존재 확인도, 형식
검사도 없다. *"팔로우 대상은 아티스트 엔티티 id"* 는 **프런트엔드의 관례**일 뿐이다.

⇒ 이 ADR 은 "저자 id 를 어디서 얻나" 만이 아니라 **"두 끝이 같은 것을 가리킨다고 누가
보증하나"** 를 함께 정해야 한다. 그러지 않으면 어느 안을 골라도 조인은 우연히만 성립한다.

---

## Decision Drivers

- **데모 도달성** — 실제 호출자가 `ARTIST_POST` 를 만들고, 그것이 팔로워 피드에 떠야 한다
  (지금은 시드가 직접-DB 로 우회한다)
- **위조 표면** — 저자를 파라미터로 받는 순간 "누가 어느 id 로 쓸 수 있는가" 가 새 규칙이 된다
- **테넌트 평면의 일관성** — `fan-platform` 은 `B2C_CONSUMER` 테넌트다. 운영자가 assume 하는
  대상이 되면 이 저장소에 **없던 조합**이 생긴다
- **가역성** — 스키마 변경(계정 컬럼·조인 의미)은 되돌리기 비싸고, 역할 발급은 상대적으로 싸다

🔵 **`TASK-BE-576` 이 세운 구분이 여기서도 성립한다**: *권한*(구독에서 파생되는 역할)과
*가시성*(행이 사는 테넌트)은 다른 축이다. 다만 팬은 ecommerce 와 **모양이 다르다** — ecommerce 는
토큰을 통과시킨 뒤 행 필터에서 비웠고, 팬 게이트웨이는 `required-tenant-id: fan-platform` 으로
**엣지에서 자른다**. 그래서 `demo-corp` 에 `fan` 구독을 얹는 것만으로는 아무것도 열리지 않는다.

---

## 선택지

### A. 아티스트에게 실제 계정을 준다 (`artists.account_id`)

아티스트 엔티티가 로그인 가능한 계정을 갖고, 그 계정이 `ARTIST` 역할로 글을 쓴다.
피드 조인의 두 끝이 **정의상** 같은 id 공간이 된다.

- ✅ 조인이 우연이 아니라 구조가 된다. 위조 표면 없음(저자 = 인증된 호출자, 현행 유지)
- ✅ `FollowArtistUseCase` 의 무검증 필드에 검증할 대상이 생긴다(`artists.account_id` 참조)
- ❌ **아티스트 온보딩 경로**가 필요하다 — 계정 발급 + `ARTIST` 역할 부여를 iam 이 해야 하고,
  그건 결국 512 의 "역할 발급" 질문을 **A 안 안에서** 다시 만난다
- ❌ 스키마 + 마이그레이션(기존 `artists` 행에 계정을 어떻게 채우나 — 데모는 시드라 무해)

### B. 발행 시 저자 id 를 파라미터로 받고, 운영자가 대리 저작한다

`fan-platform` 을 assume 한 운영자가 "이 아티스트 이름으로" 쓴다.

- ✅ 스키마 변경 0. 콘솔의 기존 운영자 모델과 같은 모양
- ❌ **저자 위조 표면이 열린다** — 누가 어느 id 로 쓸 수 있는지가 새 규칙이고 테스트로 고정해야
  한다(`FAN-BE-045` AC-4 가 이미 그것을 요구)
- ❌ **512 의 질문을 정면으로 요구한다**: `B2C_CONSUMER` 테넌트를 운영자가 assume 하는 것이
  이 저장소에서 허용되는가. 지금은 배정 행도, 구독도, 전례도 없다
- 🔴 그리고 이것만으로는 **조인이 여전히 우연**이다 — 저자 id 를 자유롭게 받는데 그것이
  `Follow.artistAccountId` 와 같은 공간이라는 보장은 새로 만들어야 한다

### C. 팔로우/피드를 아티스트 엔티티 id 가 아닌 **계정 id** 로 재정의

조인의 의미 자체를 바꾼다.

- ✅ 무검증 필드 문제가 정의상 사라진다
- ❌ 프런트(`artists/[id]` 링크) · `follows` 테이블 · 기존 행 마이그레이션
- ❌ **그래도 "아티스트의 계정" 이 필요하다** ⇒ 사실상 A 를 포함하면서 조인까지 건드린다

### D. 팬 도메인은 운영자 평면을 **갖지 않는다**고 명시하고, 세 곳의 수용부를 정리한다

`FAN_OPERATOR`(3곳)와 `ARTIST`(1곳)를 **죽은 수용부로 인정하고 제거**한다. `ARTIST_POST` 는
v1 에서 제품 기능이 아니라고 선언한다.

- ✅ 결정 비용 최소. 코드가 "받을 수 있지만 아무도 못 주는 역할" 을 들고 있지 않게 된다
- ❌ 데모에서 아티스트 글이 사라진다(시드의 직접-DB 삽입도 함께 제거해야 정직하다)
- ❌ `specs` 와 e2e 가 `ARTIST_POST` 를 전제하고 있다면 그것들도 함께 줄여야 한다 — **범위 확인 필요**

---

## 추천 — **A** (근거와 함께, 다만 이것은 제안이지 결정이 아니다)

A 는 **위조 표면을 만들지 않는 유일한 안**이면서, 이 도메인이 이미 모델링하려던 것
("아티스트가 글을 쓴다")을 그대로 성립시킨다. B 의 매력은 "스키마 변경 0" 인데, 실측이
보여주듯 B 를 골라도 **조인 보증을 새로 만들어야 하므로** 그 이점이 절반은 사라진다.

🔴 **A 를 고르더라도 512 의 질문은 남는다** — 아티스트 계정에 `ARTIST` 역할을 **누가 발급하나**.
다만 그 질문은 "B2C 테넌트를 운영자가 assume 하는가"(B 가 요구하는 것)보다 **훨씬 좁다**:
`fan-platform` 안에서 계정에 역할을 붙이는 문제이지, 테넌트 평면의 새 조합이 아니다.

🔵 **D 도 정당한 답이다.** 이 저장소가 포트폴리오 데모라는 점을 감안하면, "아티스트 저작은
v1 범위 밖" 이라고 **명시적으로 적는 것**이 죽은 역할 4곳을 들고 있는 것보다 정직하다.
D 를 고른다면 그것은 축소가 아니라 **범위의 명문화**다.

---

## 결과 (ACCEPTED 시)

| 안 | 후속 |
|---|---|
| A | `FAN-BE-045` = 스키마 + 온보딩 + 조인 검증 · `MONO-512` = **좁혀서** 재작성(역할 발급만) |
| B | `MONO-512` 선행(운영자 평면 개설) → `FAN-BE-045` 는 위조 규칙 + 조인 보증 |
| C | A 의 후속 + 프런트/마이그레이션 |
| D | 두 티켓 모두 **종결**(구현 없음) + 수용부 4곳 제거 + 시드의 직접-DB 삽입 제거 + 스펙 범위 정정 |

어느 쪽이든 **`FollowArtistUseCase` 의 무검증 저장**은 별도로 남는다 — 조인의 한쪽 끝을
누가 보증하는지는 네 안 모두에서 답해야 한다.

---

## The ACCEPTED Gate

이 ADR 은 **PROPOSED** 다. `platform/architecture-decision-rule.md` § The ACCEPTED Gate 에 따라
에이전트가 스스로 ACCEPT 할 수 없고, 다음 **정확형**만이 PAUSE 를 해제한다:

```
ADR-MONO-059 ACCEPTED — <A|B|C|D>
```

그때까지 `TASK-MONO-512` 와 `TASK-FAN-BE-045` 는 **둘 다 착수하지 않는다.**
