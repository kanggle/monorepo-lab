# Task ID

TASK-MONO-370

# Title

wms 를 정책의 키잉 규칙으로 정렬한다 — 그리고 **MONO-368 이 못 지운 거짓 주장 4곳**을 지운다

# Status

done

# Owner

monorepo

# Task Tags

- security
- gateway
- rate-limit
- drift-guard
- cleanup

---

# Dependency Markers

- **선행 (머지됨)**: [`TASK-MONO-368`](../done/TASK-MONO-368-ratelimit-keying-vacuous-tenant-isolation.md) — 정책 § Rate Limiting 을 규칙으로 승격하고(익명→IP / 인증→principal / tenant 세그먼트는 클레임이 **실제로 변수일 때만**), 가드 **I4** 를 신설했으며, **wms 를 기록된 이탈로 등재**했다. 이 task 는 그 이탈을 **해소**한다.
- **가드**: `scripts/check-gateway-drift.sh` **I4**. 이 task 는 `RATELIMIT_IP_ONLY_ALLOWLIST` 를 **비운다** ⇒ 이후 wms 가 IP-only 로 되돌아가면 CI 가 RED.

---

# Goal

## 1) wms 정렬

wms 게이트웨이의 **5개 라우트가 전부 JWT 인증**인데(`master`/`inbound`/`inventory`/`outbound`/`admin`), rate limit 은 **클라이언트 IP 단독**으로 키잉한다. 게다가 키에 **프로젝트 접두사가 없다**(`{ip}:{routeId}`).

- **한 NAT 뒤의 창고 운영자 전원이 버킷 하나를 공유**한다. 반대로 **IP 를 로테이션하는 남용자는 계정 단위로 전혀 제한되지 않는다.**
- 접두사가 없으면 두 도메인이 Redis 를 공유하는 순간 키가 충돌한다(현재는 프로젝트별 Redis 라 미발현).

**이것은 결함이 아니라 *구 정책의 준수*였다.** MONO-368 이전 `platform/api-gateway-policy.md` L92 는 `(clientIp, routeId)` 를 기본값으로 선언했고, **wms 는 그것을 지킨 유일한 비-multi-tenant 게이트웨이였다.** 368 이 정책을 올렸으므로 이제 wms 는 이탈이다 — 그래서 368 이 **조용히 바꾸지 않고 기록된 이탈로 남겨 사람 결정을 기다렸다.**

**결정**: 정렬한다. 나머지 5개(scm/fan/finance/erp/ecommerce)가 이미 principal 로 키잉하고, **fan 이 같은 상황에서 이미 정답을 증명했다** — fan 도 `tenant_id` 가 상수(`fan-platform`)이지만 **상수 축을 피해 account 로 키잉**했다.

## 2) 🔴 MONO-368 의 AC-5 는 충족되지 않았다

368 의 AC-5 는 이렇게 적혀 있다:

> **`grep -rn "no documented rationale" projects/` → 0건.**

**나는 그 grep 을 돌리지 않고 done 으로 닫았다.** `RateLimitConfig.java` 2개만 고쳤고, **같은 거짓 주장이 4곳에 남아 있다**:

| 파일 | 내용 |
|---|---|
| `projects/finance-platform/specs/services/gateway-service/architecture.md:189` | *"wms keys by client IP with no namespace and carries **no documented rationale**"* |
| `projects/erp-platform/specs/services/gateway-service/architecture.md:191` | 동일 |
| `projects/finance-platform/apps/gateway-service/src/test/java/.../RateLimitKeyTest.java:14-16` | *"wms does not — its keys are a bare `{ip}:{routeId}` … carries no documented rationale"* |
| `projects/erp-platform/apps/gateway-service/src/test/java/.../RateLimitKeyTest.java:14-16` | 동일 |

**이 세션 내내 쫓던 "수정이 일부 표면에만 닿는다" 를 내가 다시 했다.** 그리고 그것을 **AC 로 명시해 놓고도** 검증하지 않았다 — **AC 를 쓰는 것과 확인하는 것은 다른 일이다.**

---

# Scope

## In Scope

1. **`wms/apps/gateway-service/.../config/RateLimitConfig.java`**
   - `accountKeyResolver` (`@Primary`) 신설 — 인증 시 `rate:wms-platform:<routeId>:acct:<sub>`, 보안 컨텍스트 부재 시 IP 키로 **폴백**(scm/fan/finance/erp 와 동일 패턴).
   - `clientIpKeyResolver` 는 **접두사를 갖도록** 수정(`rate:wms-platform:<routeId>:<ip>`) — pre-auth 라우트용으로 존치.
   - **폴백이 있으므로 구조적으로 안전하다**: JWT 가 없는 경로는 **현행 동작(IP)으로 강등**된다.
2. **`wms/apps/gateway-service/src/main/resources/application.yml`** — 5개 라우트의 `key-resolver` 를 `#{@accountKeyResolver}` 로.
3. **wms 스펙** — `specs/services/gateway-service/{architecture,overview}.md` 가 `(clientIp, routeId)` / `100 rpm/IP` 를 **여러 곳에서 선언**한다. **스펙이 task 를 이긴다 — 구현과 함께 갱신**한다.
4. **거짓 주장 4곳 제거**(§ Goal 2) — finance/erp 의 spec 2 + test Javadoc 2.
5. **`platform/api-gateway-policy.md`** — 함대 표에서 wms 의 ⚠️ 이탈 행을 **준수**로.
6. **`scripts/check-gateway-drift.sh`** — `RATELIMIT_IP_ONLY_ALLOWLIST` 를 **비운다**(빈 값). 주석에 *"비어 있는 것이 정상 — 이탈이 생기면 이름과 이유를 적어라"*.

## Out of Scope

- **ecommerce/scm/fan/finance/erp 의 키 형태** — 이미 정책 준수.
- **wms 스펙이 선언하는 `/webhooks/erp/**` 라우트** — `application.yml` 에 **존재하지 않는다**(spec-vs-code 갭). 별건. 이 task 는 **실재하는 5개 라우트만** 다룬다.
- `ADR-MONO-049` — ACCEPT 대기(별도 재측정 필요).

---

# Acceptance Criteria

- [ ] **AC-1** — wms 인증 요청 키가 `rate:wms-platform:<routeId>:acct:<sub>`. **같은 IP 의 서로 다른 두 계정이 서로 다른 키를 받는다**를 단언하는 테스트가 있다.
- [ ] **AC-2** — 보안 컨텍스트 부재 시 **IP 키로 폴백**한다(현행 동작 보존). 테스트로 단언.
- [ ] **AC-3** — 키에 `rate:wms-platform:` 접두사가 있다.
- [ ] **AC-4** — wms 스펙(`architecture.md` + `overview.md`)이 새 키 형태를 선언한다. **`grep -rn 'rpm/IP\|(clientIp, routeId)' projects/wms-platform/specs/` 잔재 0.**
- [ ] **AC-5 (368 이 놓친 것)** — **`grep -rni "no documented rationale" projects/` → 0건.** 이번엔 **실제로 돌린다.**
- [ ] **AC-6** — `RATELIMIT_IP_ONLY_ALLOWLIST` 가 비었고, **가드가 무결 트리에서 GREEN**.
- [ ] **AC-7 (가드가 무는가)** — mutation: wms 라우트를 `clientIpKeyResolver` 로 되돌리면 I4 가 **RED**(`IP-ONLY-BUCKET wms`). **주입이 실제로 적용됐는지 `git diff --stat` 로 먼저 확인**한 뒤 결과를 읽는다.
- [ ] **AC-8** — wms 게이트웨이 전체 스위트 0 실패 / 0 skipped.

---

# Related Specs

- `projects/wms-platform/apps/gateway-service/src/main/{java/com/wms/gateway/config/RateLimitConfig.java,resources/application.yml}`
- `projects/wms-platform/specs/services/gateway-service/{architecture,overview}.md`
- `platform/api-gateway-policy.md` § Rate Limiting > Key shape (MONO-368)
- `scripts/check-gateway-drift.sh` § I4
- 참고 구현: `projects/{scm,fan,finance,erp}-platform/apps/gateway-service/.../RateLimitConfig.java`

# Related Contracts

**없다** — 키는 내부 구현. 다만 **429 를 받는 주체가 바뀐다**: 현재 "그 IP 뒤의 전원" → 수정 후 "폭주한 계정". **그것이 이 task 의 요점이다.**

---

# Edge Cases

- **`sub` 가 없는 인증 토큰** — IP 키로 폴백(널 키 금지). ecommerce(MONO-368)와 같은 규율.
- **`client_credentials` 토큰** — `sub` 가 client_id ⇒ 서비스 계정별 버킷. **올바르다.**
- **키 형태 변경 = 기존 Redis 버킷 유기** — 무해하다(TTL 로 만료). 다만 **배포 직후 짧은 순간 모든 호출자가 새 버킷을 받는다**(리밋이 잠시 후해진다). rate limit 은 soft protection 이므로 수용 가능.
- **`admin` 라우트는 더 낮은 티어**(60 vs 100) — 티어는 라우트 필터 인자라 **키 리졸버 변경과 독립**이다. 건드리지 말 것.

# Failure Scenarios

- **폴백 없이 account 키만 만든다** → JWT 없는 경로에서 **널 키 / NPE**, 또는 전원이 한 합성 버킷으로 뭉친다. Guard: AC-2.
- **접두사만 붙이고 축은 안 바꾼다** → 정책의 절반만 지킨다(충돌은 막지만 NAT 공유는 그대로). Guard: AC-1.
- **또 일부 표면만 고친다** → § Goal 2 가 바로 그 사고다. Guard: **AC-5 를 실제로 실행**.
- **스펙을 안 고친다** → 코드가 스펙을 위반하고, 다음 사람이 스펙을 믿는다. `CLAUDE.md`: 스펙이 이긴다. Guard: AC-4.

---

# Provenance

발굴 2026-07-12 — `TASK-MONO-368` § Out of Scope 가 사람 결정으로 남긴 항목. 사용자가 "언제 하는 게 좋냐" 고 물었고, **컨텍스트가 살아 있는 지금이 가장 싸다**(3개월 뒤엔 "기록된 이탈" 이 *"원래 그런 것"* 으로 썩는다)는 판단으로 착수.

**그리고 그 조사 중에 368 의 AC-5 가 충족되지 않았다는 것을 발견했다** — 내가 AC 로 적어 놓고 검증하지 않았다.

분석=Opus 4.8 / 구현 권장=Sonnet (패턴이 scm/fan/finance/erp 에 이미 있는 기계적 정렬 — 단 **폴백 규율과 스펙 동기화**는 놓치기 쉽다).
