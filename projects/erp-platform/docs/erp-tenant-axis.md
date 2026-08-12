# erp 의 테넌트 축 — 한 프로퍼티가 세 가지 뜻으로 읽히던 자리

**작성**: TASK-ERP-BE-043 (2026-08-12) · **결정 근거**: [`ADR-001`](adr/ADR-001-erp-event-plane-tenant-axis.md) — **D** (ACCEPTED 2026-08-12)

이 문서는 ADR 을 요약하지 않는다. ADR 은 *무엇을 결정했나*를 말하고, 이 문서는
**그 결정이 코드의 어디에 어떻게 내려앉았는지**와 **래칫이 무엇을 보고 무엇을 못 보는지**를
말한다. 충돌하면 ADR 이 정경이다.

---

## 1. 사건: 정상 이벤트가 전량 DLT 로 갔고, 화면 둘이 비어 있었다

`erp.approval.*` 이벤트가 두 소비자에서 각각 거부됐다.

| | 증상 | 실측 |
|---|---|---|
| `read-model-service` | 위임 프로젝션이 안 참 | `delegation_fact_proj` **0행**, `/erp/delegation` 빈 화면 |
| `notification-service` | ERP 알림함이 안 참 | `GET /api/erp/notifications` → `200` / `totalElements 0`, `notification` **0행** |

두 소비자의 로그가 원인을 그대로 적고 있었다:

```
Non-erp tenant 'demo-corp' on topic erp.approval.delegated.v1
Out-of-contract tenantId 'demo-corp' on topic erp.approval.submitted.v1 (single-tenant invariant: erp)
```

## 2. 원인: `erpplatform.oauth2.required-tenant-id` 를 **세 축**이 읽고 있었다

값은 하나(`erp`)인데 읽는 쪽마다 뜻이 달랐다.

| 축 | 읽는 곳 | 뜻 | `demo-corp` 결과 |
|---|---|---|---|
| HTTP 인가 | `ServiceLevelOAuth2Config` · `ReadAuthorizationGate` (양 서비스) | **도메인 키** (`entitled_domains ∋ erp`) | 통과 ✅ |
| 이벤트 평면 | `read-model`/`DelegationEnvelopeToCommandMapper` · `notification`/`EnvelopeToCommandMapper` | **테넌트 값** (등호 비교) | 거절 ❌ |
| 영속 읽기 | `notification`/`NotificationInboxController` | **질의 테넌트 값** | 0행 ❌ |

세 번째 축은 **ADR 도 티켓도 명명하지 않았다** — 이 티켓 구현 중에 발견했다. 이것을 안
고치면 이벤트 관문만 고쳤을 때 **알림 테이블은 차는데 알림함은 계속 0** 이 된다: 쓰기는
봉투의 테넌트(`demo-corp`)로, 읽기는 상수(`erp`)로 하기 때문이다. 표면이 둘이면 각각
초록이어도 **합성이 결함**일 수 있다는 것의 교과서적 사례다.

⇒ 값 하나로는 세 축을 동시에 만족시킬 수 없다. `OIDC_REQUIRED_TENANT_ID=demo-corp` 로
바꾸면 이벤트 관문은 열리지만 **HTTP 인증이 도메인 전체에서 깨진다.** 설정으로는 못 고친다.

## 3. 관문이 지키려던 불변식은 **이미 깨져 있었다**

`erp` 는 *"All projected rows belong to the `erp` tenant"* 로 선언돼 있었다. 실측:

| 프로젝션 | 행 | 이벤트 관문 |
|---|---|---|
| `approval_fact_proj` | 2 | ❌ 없음 |
| `department_proj` / `employee_proj` | 4 / 4 | ❌ 없음 |
| `cost_center_proj` / `job_grade_proj` | 3 / 3 | ❌ 없음 |
| `delegation_fact_proj` | **0** | ✅ 있음 |

불변식은 **관문 없는 형제 5개가 16행으로 매일 깨고 있었고**, 관문이 있는 하나만 굶었다.
그리고 `tenant_id` 컬럼을 가진 erp 테이블 전수 `GROUP BY tenant_id` 의 결과는
**`demo-corp` 하나 · 문자열 `erp` 0행**이었다. 문제는 *단일 vs 다중*이 아니라
**그 하나뿐인 테넌트를 뭐라고 부르는가**였다.

## 4. 무엇이 바뀌었나 (ADR-ERP-001 — D 집행)

| # | 변경 | 파일 |
|---|---|---|
| 1 | 이벤트 관문 **2곳**이 `required-tenant-id` 를 **더 이상 읽지 않는다.** 테넌트는 **싣되 비교하지 않는다** — **부재만** invalid → 즉시 DLT | `DelegationEnvelopeToCommandMapper` · `notification`/`EnvelopeToCommandMapper` |
| 2 | `DelegationEventEnvelope.hasTenant(required)` → `resolvedTenantId()` (top-level → `payload` 폴백) | `DelegationEventEnvelope` |
| 3 | 봉투의 테넌트를 **프로젝션에 기록** — `delegation_fact_proj.tenant_id` 가 원본 `delegation_grant.tenant_id` 와 일치한다 | `DelegationFactCommand` · `DelegationFactProjection` · `…JpaEntity` · `…RepositoryImpl` · `ApplyDelegationFactUseCase` |
| 4 | 알림함 질의 테넌트를 **호출자 자신의 검증된 claim** 으로 (상수 아님) | `NotificationInboxController` |
| 5 | 계약 3문서의 `"tenantId": "erp"` **리터럴 → `"<tenantId>"` 플레이스홀더** + 소비자 규칙 명문화 | `erp-approval-events.md` · `erp-masterdata-events.md` · `notification-subscriptions.md` |
| 6 | 두 `architecture.md` § Multi-tenancy / 실패모드 표 갱신 | read-model · notification |

> 🔴 **3번은 선택이 아니라 필수였다.** `delegation_fact_proj.tenant_id` 는 V3 이래
> `DEFAULT 'erp'` 를 갖고 있었고 JPA 엔티티에 **매핑조차 안 돼 있었다.** 관문만 걷어내면
> 이 프로젝션은 `erp` 로, 나머지 erp 전체는 `demo-corp` 로 적히므로 **distinct = 2** —
> 즉 D 가 세운 래칫이 *정상 시스템에서* 즉시 발화한다. 프로젝션이 사실을 싣지 않으면
> D 는 자기 자신과 모순이다.

> 🔵 **범위 밖 (ADR 이 명시적으로 배제)**: `PROJECT.md` traits · 읽기 경로의 테넌트 필터
> 신설 · masterdata 프로젝션에 `tenant_id` 컬럼 추가. 4번은 *신설*이 아니라 이미 있던
> 필터의 **출처**를 상수에서 호출자로 바꾼 것이다.

> 🔵 **계약 문서는 셋이었다.** ADR 의 구속 표는 *"두 계약 문서"* 라고 적었지만 실측하면
> `notification-subscriptions.md` 가 같은 리터럴을 같은 이유로 갖고 있다. 셋 다 고쳤다 —
> 둘만 고치면 남은 하나가 다음 사람의 근거가 된다.

## 5. 래칫 — **둘이고, 서로의 대체물이 아니다**

D 는 *거부*를 *사후 탐지*와 맞바꿨다: **"erp 전체에서 distinct `tenant_id` ≥ 2 이면 RED,
그때가 Option B 를 다시 논의할 시점"**. 거부를 없애고 탐지를 안 놓으면 교환의 한쪽만
실행한 것이다. 이 저장소는 **술어만 있고 도는 레인이 없는 가드**를 두 번 만들었고
(`TASK-MONO-518` · `524`) 둘 다 영원히 초록이었다.

| | CI 절반 | 라이브 절반 |
|---|---|---|
| **무엇** | `SingleTenantRatchetIntegrationTest` (read-model + notification) | `scripts/check-erp-single-tenant-ratchet.sh` |
| **레인** | `ci.yml` / `erp-integration-tests` — **모든 erp PR** | 살아 있는 스택에 대고 수동 (`demo-up.sh iam erp console` 후) |
| **어디에 대고** | Testcontainers 실 MySQL + Kafka | erp 의 **네 스키마 전부** (`information_schema` 로 테이블 발견 — 손으로 안 적는다) |
| **보는 것** | **코드가** 사실 옆에 상수를 찍어 두 번째 테넌트를 만드는 경우 | **런타임에** 다른 erp-entitled 테넌트가 assume 으로 들어와 쓴 경우 |
| **못 보는 것** | 런타임 유입 (CI 엔 살아 있는 erp DB 가 없다) | 스택이 안 떠 있으면 아무것도 — 그래서 **SKIP 이 아니라 실패**다 |

두 절반 모두 **0건을 통과로 세지 않는다**: 대상 테이블 0개 / 행 0건은 "위반 없음" 이 아니라
계측 실패이므로 RED 다.

## 6. DLT 에 이미 쌓인 것 (AC-6) — **재처리하지 않고 폐기한다. 근거를 적는다**

관문이 거부한 메시지들은 `.DLT` 에 남아 있다(실측 시점: `delegated` 4 · `submitted` 4 ·
`delegation.revoked` 2 + `approved`). **재처리 기계를 만들지 않는다.**

- **사실이 유실되지 않는다.** 모든 DLT 메시지의 원본 애그리게이트는 권위 저장소인
  `approval-service` 에 그대로 있다. DLT 는 사본이지 원본이 아니다.
- **최신-사실 프로젝션이다.** `delegation_fact_proj` 는 이력이 아니라 최신 상태를 담는다 —
  현재 상태를 다시 흘려보내면 같은 종착점에 도달한다. 이력 재생이 필요한 표면이 아니다.
- **데모 전용 백로그에 재드라이브 경로를 만드는 것은 비용이 사실보다 크다.**

🔴 **대신 반드시 알아야 할 귀결**: 기존 볼륨에서는 이 수정 **이전에 발행된** 이벤트의 위임
행·알림 행이 **소급해서 생기지 않는다.** 새 이벤트만 투영된다. 이력까지 원하면 복구는
재드라이브가 아니라 **볼륨 초기화 + 재시드**다:

```bash
docker compose -f projects/erp-platform/docker-compose.yml down -v   # 사장님 실행 (분류기 차단)
bash infra/demo/demo-up.sh iam erp console
bash infra/demo/seed/seed-erp.sh
```

🔵 그래도 손으로 재드라이브하고 싶다면 기계 없이 가능하다 — `<topic>.DLT` 를 콘솔 컨슈머로
읽어 원 토픽에 그대로 다시 producing 하면 된다(봉투가 이미 유효하므로 이제는 통과한다).
`eventId` 기반 dedupe(`processed_events`)가 있으므로 **중복 투영 위험은 없다.**

## 7. 다음 사람에게

- 이 프로퍼티(`erpplatform.oauth2.required-tenant-id`)는 **도메인 키다.** 영속 계층이나
  이벤트 평면에서 테넌트 *값*으로 읽지 말 것. 그렇게 읽는 코드가 이 문서의 사건 전부다.
- 테스트 픽스처에 `"tenantId": "erp"` 를 쓰지 말 것. **erp 의 어떤 행도 그 값을 가진 적이
  없다.** 그 픽스처는 현실보다 관대해서, 초록이어도 아무것도 증명하지 않는다.
- 래칫이 울리면 그것은 고칠 버그가 아니라 **ADR-ERP-001 Option B 를 다시 열라는 신호**다.
