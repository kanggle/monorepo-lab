# Architecture Decision Records — fan-platform

이 프로젝트의 주요 기술·아키텍처 결정과 그 **이유**를 기록한다. 결정 자체보다 **왜 그 결정을 했는지**, 그리고 **버린 대안은 무엇이었는지**에 초점을 둔다.

| # | 제목 | 상태 |
|---|---|---|
| [ADR-001](ADR-001-real-pg-portone-verification-boundary.md) | 실 PG 연동 — PortOne V2 클라이언트 개시 결제 + 서버측 검증 경계(profile 게이팅, mock 은 CI/test 기본값 유지) | Proposed |
| [ADR-002](ADR-002-billing-key-auto-renewal.md) | 빌링키 기반 자동 갱신(정기결제) — 서버 개시 청구 + `BillingKeyEnrollment` + fail-closed 실패 정책(새 상태 없음, `RenewMembershipUseCase` 재사용). `ADR-MONO-057`(라이브러리 확장)의 컴패니언 | Accepted |
| [ADR-003](ADR-003-fan-post-visibility-authoring-rule.md) | `FAN_POST` 의 가시성 티어 — 팬이 게이팅된 글을 쓸 수 있는가. **ACCEPTED — B**: 세 티어를 계속 허용하고 근거를 계약에 명문화(코드 무변경). **스펙이 침묵이 아니라 반대를 규정**한다(`v1-e2e-scenarios.md` § Scenario 3 + `VisibilityTierE2ETest` 가 `FAN_POST` 를 PREMIUM/MEMBERS_ONLY 로 발행) ⇒ 좁히기는 드리프트 교정이 아니라 **새 제약 도입**. `TASK-FAN-BE-047` 을 게이트 | ACCEPTED |
| [ADR-004](ADR-004-artist-account-existence-seam.md) | `Follow.artistAccountId` 의 실재 검증을 **어느 이음매**에 두는가 — 동기 internal 엔드포인트(A, 권고) / 이벤트 투영(B) / 검증 안 함을 명시 결정으로(C). `ADR-MONO-059` 가 A 를 고르며 조인 검증을 `FAN-BE-045` 에 배정했는데 그 근거 문장(*"`artists.account_id` **참조**"*)이 **로컬 참조를 전제**했다 — 실측하니 `follows`(community DB)와 `artists`(artist DB)는 **다른 데이터베이스**이고 reach-in 은 이미 금지돼 있다. 🔴 B 는 세 안 중 유일하게 **선언된 Service Type 을 바꾼다**(community = *"단일타입 rest-api · 인바운드 컨슈머 표면 없음"*). ✅ **ACCEPTED — A** (2026-08-11, 소유자 정확형) ⇒ `TASK-FAN-BE-045` AC-6 게이트 해제. 🔴 **ACCEPT 가 답하지 않은 rider 1건**: e2e 탈출구의 모양(두지 않음 / 거부 기본값) — plain `A` 가 도착했으므로 **구현이 명시적으로 답하고 기록**한다(`ADR-MONO-060` 의 `act` 처리와 동형). membership 의 `AlwaysAllow` 스텁 복사는 답이 아니라 드라이버 3 위반 | **Accepted — A** |

## ADR 작성 원칙

- **한 장으로 수렴**. Context → Decision → Consequences.
- 고른 결정뿐 아니라 **버린 대안과 그 이유**를 함께 기록.
- 검증되기 전까지는 `Proposed`, 뒤집히면 `Superseded by ADR-XXX`.
- monorepo-level(cross-cutting·플랫폼) 결정은 repo-root `docs/adr/ADR-MONO-*` 에, 본 프로젝트 도메인-내부 결정은 여기에 기록한다.
- ACCEPTED 승격은 `platform/architecture-decision-rule.md § The ACCEPTED Gate` 를 따른다(라이브 검증 + deciders 의 정확형 intent, self-ACCEPT 금지).
