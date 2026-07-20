# TASK-BE-540 — 선체크의 범위와 유니크 제약의 범위가 서로 다른 곳 2건: 어느 쪽이 옳은지 판정하고 정렬한다

**Status:** ready

**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus 4.8 (판정이 실질. 코드 변경은 작지만 어느 쪽을 정답으로 삼느냐가 테넌트 격리 의미를 바꾼다)

> `TASK-BE-538` **Edge 3** 수행 중 발견. 두 곳 모두 **애플리케이션 선체크가 조회하는 범위**와 **DB 유니크 제약이 강제하는 범위**가 다르다. 둘이 다르면 셋 중 하나가 일어난다: 선체크가 좁아 제약에 걸리거나(500), 선체크가 넓어 제약에 **영영 도달하지 못하거나**(잘못된 행을 수정), 아무 일도 없거나(도달 불가). **어느 것인지는 아직 측정되지 않았다.**

---

## Goal

### 사례 A — `notification-service` push 구독

| | 범위 |
|---|---|
| 제약 `uq_push_subscriptions_tenant_endpoint` (`V6__create_push_subscriptions.sql:25`) | `(tenant_id, endpoint)` — **endpoint 는 테넌트마다 반복 가능** |
| 조회 `PushSubscriptionJpaRepository.findByEndpoint` (`:15`) | `endpoint` 만 — **전역에서 최대 1행** |

이 둘은 **서로 모순된 주장**을 한다. 리포지터리 주석(`PushSubscriptionJpaRepository.java:14`)은 *"Endpoint is a globally-unique push-service URL — at most one row"* 라고 단언하는데, 제약은 정확히 그 반대 — 테넌트마다 같은 endpoint 를 허용 — 를 선언한다.

`PushSubscriptionService.register` (`:29-43`) 는 이 전역 조회로 upsert 를 한다. 조회가 제약보다 **넓으므로**:

- 테넌트 B 가 이미 테넌트 A 소유인 endpoint 를 등록하면, `findByEndpoint` 가 **A 의 행을 찾아** `updateKeys` 로 **A 의 키를 회전**시키고 200 을 돌려준다(`:31-33`). 교차 테넌트 쓰기이며 조용하다.
- 그리고 제약은 **영영 도달되지 않는다** — INSERT 분기(`:35`)에 갈 일이 없으므로.

### 사례 B — `review-service` 리뷰 작성

| | 범위 |
|---|---|
| 제약 `uq_reviews_user_product_active` (`V2__fix_unique_constraint_for_soft_delete.sql:5`) | `(user_id, product_id) WHERE status='ACTIVE'` — **`tenant_id` 없음** |
| 선체크 `ReviewRepositoryImpl.existsByUserIdAndProductId` (`:55-58`) | `...AndTenantId(..., TenantContext.currentTenant())` — **테넌트 스코프** |

여기서는 선체크가 제약보다 **좁다.** 같은 `(user_id, product_id)` 쌍이 두 테넌트에 존재할 수 있다면, 두 번째 테넌트의 작성 요청은 선체크를 **결정적으로 통과**한 뒤 제약을 위반하고, review-service 에 `DataIntegrityViolationException` 핸들러가 없으므로(`GlobalExceptionHandler.java:122` 의 `Exception.class` 로 낙하) **500 `INTERNAL_ERROR`** 가 된다.

**🔴 단, 도달 가능한지는 측정되지 않았다.** `product_id` 가 테넌트별로 발급되는 UUID 라면 두 테넌트가 같은 `(user_id, product_id)` 쌍을 가질 일이 없어 이 경로는 **도달 불가**이고, 그렇다면 결함은 "500 이 난다" 가 아니라 **"제약이 V5 의 테넌트 도입을 반영하지 않은 채 남아 있다"** 는 정합성 문제로 축소된다. **AC-0 이 이걸 먼저 가른다.**

### 공통 축

`V5__add_tenant_id.sql` 이 두 서비스 모두에서 `tenant_id` 를 도입하면서 **비유니크 보조 인덱스만 만들고 기존 유니크 인덱스는 손대지 않았다**(review `V5:14`, notification `V5:20`). 사례 A 는 제약을 테넌트화했으나 조회를 안 했고, 사례 B 는 조회를 테넌트화했으나 제약을 안 했다 — **같은 마이그레이션 웨이브에서 반대 방향으로 반쪽만 적용된 것.**

---

## Scope

### In Scope

1. **AC-0 도달성 측정**(아래). 두 사례 각각 실제로 발화 가능한지부터 가른다.
2. 각 사례에서 **조회와 제약 중 어느 쪽이 정답인지 판정**하고 나머지를 거기에 맞춘다.
3. 판정에 따른 코드/마이그레이션 변경 + 회귀 테스트.

### Out of Scope

- `uq_notifications_event_id` 의 모양 문제는 `TASK-BE-539` — 같은 서비스지만 원인이 다르다(제약이 *쓰기 모양*과 어긋남).
- `DataIntegrityViolationException` → 409 미배선 일반 문제는 `TASK-BE-542`. **이 task 는 500 을 409 로 바꾸는 게 아니라 애초에 위반이 나지 않게 범위를 맞춘다.**
- 다른 서비스의 선체크/제약 범위 전수 감사는 이 task 밖 — **다만 AC-4 로 같은 지문을 한 번 훑는다.**

---

## Acceptance Criteria

- **AC-0 (gate — 도달성 측정)** — 두 사례 각각에 대해 **발화 가능한지 실측**한다:
  - **A**: 서로 다른 두 테넌트가 같은 `endpoint` 문자열을 가질 수 있는 경로가 실재하는가? (같은 브라우저/기기가 두 테넌트 사용자로 구독하는 경우 — 데모 테넌트 구성에서 가능한지 확인)
  - **B**: 같은 `(user_id, product_id)` 쌍이 두 테넌트에 존재할 수 있는가? **`product_id` 발급 규칙을 확인하라** — 테넌트별 UUID 면 도달 불가다.
  - **도달 불가로 나오면 그 사례는 "500 결함" 이 아니라 "정합성 결함" 으로 재분류하고 티켓 본문을 정정한다.** 결론이 바뀌는 것은 실패가 아니다; 바뀌지 않았다고 적는 것이 실패다.

> ### 🔴 사례 B 의 AC-0 은 이미 답이 나왔다 (2026-07-20, `TASK-BE-542` / PR #2775)
>
> `TASK-BE-542` 가 백스톱 핸들러의 도달성을 증명할 실 DB 경로를 찾다가 **사례 B 가 바로 그 경로임을 발견했다.** 두 테넌트가 같은 `(user_id, product_id)` 로 리뷰를 작성하는 IT 를 작성했고 **로컬 Postgres 와 CI Linux 통합 레인 양쪽에서 통과**했다. **🔴 2026-07-20 후속 정정 — 나는 이것을 "도달 가능 확정" 으로 적었으나 그것은 과장이었다.** **메커니즘만 증명됐고 프로덕션 도달성은 미측정이다** — IT 가 같은 `X-User-Id` 를 두 `X-Tenant-Id` 로 **직접 만들어** 넣었는데, `user-service/V4__add_tenant_id.sql:8` 은 *"an IAM user belongs to one tenant"* 라고 적는다. 그 불변식이 강제되면 이 픽스처는 **불가능한 입력**이고 사례 B 는 도달 불가다. 증명된 것은 **SQLState 23505 가 핸들러까지 전달된다**는 것뿐. 판정 = `TASK-BE-543`. (사례 A 의 도달성은 **여전히 미측정**이다.)
>
> **그리고 BE-542 가 이 결함을 더 조용하게 만들었다.** 이제 `review-service` 에 `@ExceptionHandler(DataIntegrityViolationException.class)` 가 있어 증상이 **500 → 409 `DATA_INTEGRITY_VIOLATION`** 으로 바뀌었다. **올바른 응답은 201 이다**(다른 테넌트의 정당한 리뷰). 기능적으로 더 나빠지진 않았지만 **500 은 알림에 남고 409 는 안 남는다** — 이 티켓 § F1 이 경고한 시나리오가 실제로 일어난 것이다. **백스톱이 이 결함을 가려 준다고 읽지 말 것.**
>
> **역방향 의존**: 이 티켓이 `uq_reviews_user_product_active` 를 `tenant_id` 포함으로 재정의하면 `ReviewDataIntegrityBackstopIntegrationTest` 는 **반드시 함께 바뀌어야 한다**(두 번째 삽입이 정당하게 201 이 되므로). 그러면 함대에 **결정적 백스톱 도달 경로가 0 개**가 되고, 그 사실은 `TASK-MONO-450`(함대 표준 판정)의 입력이 된다. 착수 시 세 곳을 함께 볼 것.
- **AC-1 (A 판정)** — endpoint 가 전역 유일인지 테넌트별인지 판정하고 근거를 적는다. 그리고 **조회와 제약을 같은 범위로 정렬**한다:
  - 전역 유일이 맞다 → 제약을 `UNIQUE (endpoint)` 로 되돌린다(V5 의 테넌트화가 오류였다).
  - 테넌트별이 맞다 → `findByEndpoint` 를 테넌트 스코프로 바꾼다.
  - **둘 다 그대로 두는 결론은 허용되지 않는다** — 지금 상태가 결함이다.
- **AC-2 (A 회귀)** — 판정이 "테넌트별" 이면: 테넌트 B 의 등록이 테넌트 A 의 행을 **수정하지 않는다**는 IT. 판정이 "전역" 이면: 제약 되돌림 후에도 재구독 키 회전이 동작한다는 IT.
- **AC-3 (B 정렬)** — 제약과 선체크의 범위를 맞춘다. 도달 가능이면 회귀 IT 필수(수정 전 RED 확인 포함); 도달 불가여도 **제약을 V5 이후 상태와 정합하게** 만든다.
- **AC-4 (형제 훑기)** — ecommerce 전 서비스에서 `existsBy*`/`findBy*` 선체크와 대응 유니크 제약의 컬럼 집합을 대조해 **같은 불일치가 더 있는지** 훑는다. 발견분은 이 티켓에서 고치지 말고 **목록으로 남긴다**(후속 티켓 판단용). 0건이면 0건이라고 적는다.
- **AC-5** — `migration-h2` 병행 디렉터리가 있는 서비스는 함께 갱신한다.

---

## Related Specs

- `apps/notification-service/src/main/resources/db/migration/V6__create_push_subscriptions.sql` — 사례 A 제약
- `apps/notification-service/src/main/resources/db/migration/V5__add_tenant_id.sql` — 반쪽 적용의 출처
- `apps/review-service/src/main/resources/db/migration/V2__fix_unique_constraint_for_soft_delete.sql` — 사례 B 제약
- `apps/review-service/src/main/resources/db/migration/V5__add_tenant_id.sql` — 반쪽 적용의 출처
- `specs/services/notification-service/architecture.md` · `specs/services/review-service/architecture.md`
- `tasks/ready/TASK-BE-538-adr-002-d3-wording-adjudication.md` § Edge 3 — 출처

## Related Contracts

- `specs/contracts/http/` 의 push 구독 / 리뷰 작성 계약 — **AC-1/AC-3 판정이 테넌트 격리 의미를 바꾸면 계약 문서의 중복·충돌 서술을 함께 갱신**한다. 판정이 사용자 가시 거동(예: 교차 테넌트 재등록 결과)을 바꾸면 계약 변경이 **선행**이다.

---

## Edge Cases

1. **🔴 도달 불가를 결함 없음으로 읽는 것** — AC-0 이 "도달 불가" 로 나와도 **조회와 제약이 서로 모순된 주장을 하는 상태 자체는 결함이다.** 다음 사람이 어느 쪽을 믿을지 알 수 없다. 도달 불가는 우선순위를 낮출 뿐 티켓을 닫지 않는다.
2. **사례 A 에서 제약이 도달 불가라는 점** — 조회가 제약보다 넓으면 INSERT 분기에 갈 일이 없어 **제약은 한 번도 발화하지 않는다.** "제약이 있으니 안전하다" 는 추론이 바로 여기서 깨진다 — 이것이 `TASK-BE-538` Edge 3 이 겨냥한 명제의 세 번째 얼굴이다.
3. **`tenantId` 가 `updatable = false`** (`PushSubscriptionJpaEntity.java:21`) — 사례 A 를 테넌트 스코프로 고칠 때, 기존 행의 테넌트를 옮기는 마이그레이션이 필요한지 확인. 이미 교차 테넌트로 오염된 행이 있으면 데이터 정리가 선행이다(AC-0 에서 실측).
4. **soft-delete 부분 인덱스** — 사례 B 의 제약은 `WHERE status='ACTIVE'` 다. 테넌트를 추가할 때 이 조건을 유지하지 않으면 재리뷰(소프트 삭제 후 재작성)가 막힌다 — `V2` 가 애초에 고쳤던 결함을 되살리는 것.

---

## Failure Scenarios

- **F1 — 500 만 없애고 범위 불일치는 남긴다.** `DataIntegrityViolationException` 핸들러를 붙여 409 로 바꾸면 사례 B 의 500 은 사라지지만, **사용자는 "이미 리뷰했다" 는 잘못된 이유를 듣는다**(다른 테넌트의 행 때문에). 증상만 덮는 것. 그 핸들러는 `TASK-BE-542` 의 백스톱이지 이 결함의 수정이 아니다.
- **F2 — AC-0 없이 착수해 도달 불가 경로에 IT 를 쓴다.** 절대 RED 가 되지 않는 테스트를 만들고 초록을 증거로 착각한다.
- **F3 — 사례 A 에서 "제약이 있으니 교차 테넌트 쓰기는 막힌다" 고 결론.** 막지 못한다 — 조회가 넓어서 제약에 도달조차 안 한다. **제약의 존재는 그 제약이 실행된다는 뜻이 아니다.**

---

## Test Requirements

- IT (Testcontainers, **CI Linux 가 권위**) — 판정된 방향에 맞춘 회귀. 도달 가능으로 판정된 사례는 **수정 전 RED 확인 필수.**
- AC-4 훑기 결과는 테스트가 아니라 **목록 산출물**로 남긴다(파일:라인 + 선체크 컬럼 집합 vs 제약 컬럼 집합).

---

## Definition of Done

- [ ] AC-0 두 사례 도달성 실측 (도달 불가면 본문 재분류)
- [ ] 사례 A 판정 + 조회/제약 정렬 + 회귀 IT
- [ ] 사례 B 판정 + 범위 정렬 (도달 가능 시 수정 전 RED 확인)
- [ ] AC-4 형제 훑기 목록 (0건이면 0건으로 기록)
- [ ] `migration-h2` 병행본 확인
- [ ] 계약 문서 영향 확인 및 필요 시 선행 갱신

---

## Notes

- **분량**: medium. 코드 변경은 작으나 **판정과 도달성 측정이 실질.**
- **dependency**: `선행` = 없음. `형제` = `TASK-BE-539`(같은 서비스, 제약이 *쓰기 모양*과 어긋난 사례) · `TASK-BE-541`(죽은 catch) · `TASK-BE-542`(DIVE→409 미배선). 넷 다 `TASK-BE-538` Edge 3 의 산물.
- **이 task 가 방어하는 실패 모드**: **선체크와 제약은 같은 질문에 대한 두 개의 답이다.** 둘의 범위가 다르면 하나는 반드시 틀렸는데, 어느 쪽이 틀렸는지는 코드가 말해 주지 않는다 — 한쪽은 500 으로, 다른 쪽은 **아무 소리 없이 남의 데이터를 고치는 것으로** 나타난다. [[project_guard_reachability_not_just_bite]] [[feedback_guard_predicate_wrong_verify_the_artifact]] [[project_enforcement_straggler_sibling_parity]]
