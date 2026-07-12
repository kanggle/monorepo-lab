# Task ID

TASK-MONO-361

# Title

finance/erp 서비스 보안 클래스 14파일이 **게이트웨이를 "(v1-deferred)" / "future" 라고 서술**한다 — `TASK-MONO-357` 이 그 게이트웨이를 만들었는데도. **357 이 남긴 stale**

# Status

done

# Owner

monorepo

# Task Tags

- docs
- security
- cleanup

---

# Dependency Markers

- **원인 (머지됨)**: [`TASK-MONO-357`](../done/TASK-MONO-357-finance-erp-gateways.md) (PR #2431 `7296d1ac6`) — finance/erp 게이트웨이를 **실제로 만들었다**. 그런데 그 게이트웨이가 없다고 말하는 주석 14파일을 **갱신하지 않았다**. **이 task 는 내 앞선 작업의 부작용을 치우는 것이다.**
- **후속 (별건, ADR 필요)**: servlet 보안 클래스 **3종 × 6사본 = 18파일** 중복 제거. **이 task 범위 밖** — § 별건 참조.

---

# Goal

`TASK-MONO-357` 이 finance/erp 게이트웨이를 신설했다. 그런데 두 프로젝트의 **서비스-레벨 보안 클래스 14파일**은 여전히 게이트웨이를 **존재하지 않는 것**으로 서술한다:

- `Mirrors the **(v1-deferred)** erp gateway-service validator chain …` (`ServiceLevelOAuth2Config` × 6)
- `Even if a request bypasses the **(v1-deferred)** gateway, this validator …` (`TenantClaimValidator` × 6)
- `byte-identical to the **future** finance gateway-service allowed-issuers` (`AllowedIssuersValidator` — finance × 2)
- `… the same issuer verdict the **(v1-deferred)** gateway would apply` (`AllowedIssuersValidator` — erp × 2)

**이번 세션이 내내 쫓던 결함 클래스를 내가 새로 하나 만든 것이다** — 문서가 코드와 다른 말을 하고, 그 거짓이 다음 사람의 판단에 비용을 물린다. 그리고 **하필 이 파일들이** *"servlet 사본 중복을 정리해도 되는가"* 를 판단할 때 읽는 바로 그 파일들이다. 지금 그 파일들은 **"게이트웨이는 아직 없다"** 고 말한다.

---

# Scope

## 착수 전 실측 (2026-07-12, `d6dc88605`)

`grep -rniE 'v1-deferred|future .* gateway'` on `projects/{finance,erp}-platform` (게이트웨이 모듈 제외) → **14파일 / 15개 문장**.

| 클래스 | 사본 | stale 문구 |
|---|---|---|
| `ServiceLevelOAuth2Config` | 6 | `Mirrors the (v1-deferred) {erp,finance} gateway-service validator chain` |
| `TenantClaimValidator` | 6 | `Even if a request bypasses the (v1-deferred) gateway` |
| `AllowedIssuersValidator` | 4 | finance ×2 = `the future finance gateway-service`; erp ×2 = `the (v1-deferred) gateway would apply` |

**갱신 대상이 아닌 것**(정확한 서술이므로 손대지 말 것): `TenantClaimEnforcer` 의 *"defense-in-depth — gateway + TenantClaimValidator during decode + this filter"* — 게이트웨이가 생긴 지금 **오히려 참이 됐다**.

## 구현

1. `(v1-deferred)` / `future` 를 걷어낸다. 게이트웨이는 **존재한다**.
2. **서비스-레벨 검증기의 존재 이유를 다시 쓴다.** 이건 단순 치환이 아니다 — 원 주석은 *"게이트웨이가 없으니 서비스가 대신 검증한다"* 는 **대체(substitute) 논리**였는데, 게이트웨이가 생긴 지금 그 논리는 **이중방어(defense-in-depth)** 로 바뀐다. `TASK-MONO-357` 이 명시적으로 *"게이트웨이가 생겨도 서비스-레벨 검증기는 그대로 둔다(이중방어)"* 로 결정했다 — 주석이 그 결정을 반영해야 한다.
3. **로직 변경 0.** 이 task 는 주석만 만진다.

---

# 별건 (이 task 범위 밖 — **ADR 필요**)

**servlet 보안 클래스 3종 × 6사본 = 18파일** (`AllowedIssuersValidator` · `TenantClaimValidator` · `TenantClaimEnforcer` × finance{account,ledger} + erp{approval,masterdata,notification,read-model}).

**착수 전 실측 (`d6dc88605`)**:

- **프레임워크 중립 확정** — 세 클래스 전부 reactor/WebFlux/ServerHttp 참조 **0건**.
- **동작 드리프트 0** — 6사본 전부 동작 동일.
- **그러나 텍스트는 이미 갈라졌다** — 정규화 본문 비교에서 `TenantClaimValidator` 6개 중 **4개**, `TenantClaimEnforcer` 6개 중 **4개**가 서로 다르다. 차이는 전부 **포매팅**(줄바꿈 · `java.util.ArrayList` FQN vs import · 지역변수 추출)과 **정당한 도메인 차이**(`erpplatform...:erp` vs `financeplatform...:finance` 프로퍼티 키)뿐. **보안 동작 차이는 없다.**
- ⇒ **"손으로 유지되고 있다" 는 증거는 이미 있다. 아직 깨지지 않았을 뿐이다.** D7 의 논거(`FailOpenRateLimiter` 수정이 4개 중 3개에만 도달하고 wms 를 조용히 빠뜨렸다)와 **같은 메커니즘**이고, 사본 수는 더 많다(6 > 4).

**왜 가드가 아니라 ADR 인가**: "본문 동일" 텍스트 가드는 (a) 포매터가 이 레포에 **없어서**(spotless/checkstyle 부재) **첫날부터 RED** 이고 — `TASK-MONO-360` 이 못박은 실패 모드(*첫날 RED 인 가드는 꺼지고, 꺼진 가드는 없는 가드보다 나쁘다*) — (b) `TenantClaimValidator` 는 lib 판이 355 에서 **빌더로 파라미터화**돼 servlet 판과 형태가 다르므로 텍스트 비교 자체가 성립하지 않는다. **봉합이 아니라 사본을 없애야 한다.**

**`libs/java-gateway` 로는 공유할 수 없다** — `implementation` 의존이 소비자의 **런타임** 클래스패스에 올라가므로 servlet 서비스가 쓰면 WebFlux+SCG 를 함께 끌어온다(ADR-MONO-048 D1 이 격리하는 그 누출, MONO-044a 거울상). ⇒ **프레임워크 중립 모듈**이 필요하고, 그건 새 shared-library 결정이라 `platform/shared-library-policy.md` § Change Rule → **ADR 게이트**(HARDSTOP-09).

**⇒ `ADR-MONO-049` (PROPOSED) 로 발행하고, ACCEPT 는 사람이 한다. 이 task 에서 하지 말 것.**

---

# Acceptance Criteria

- [ ] **AC-1** — `projects/{finance,erp}-platform` 의 **비**-게이트웨이 코드에서 `grep -riE 'v1-deferred|future .*gateway'` → **0건**.
- [ ] **AC-2** — 각 주석이 서비스-레벨 검증기의 존재 이유를 **이중방어**로 서술한다(대체가 아니라). 357 의 결정("게이트웨이가 생겨도 서비스 검증기는 유지")과 일치.
- [ ] **AC-3 — 로직 변경 0**: `git diff` 가 주석/javadoc 밖의 라인을 건드리지 않는다. **`./gradlew :projects:finance-platform:...:test :projects:erp-platform:...:test` 무수정 GREEN**(테스트 파일도 손대지 않는다).
- [ ] **AC-4 — 과잉 치환 금지**: `TenantClaimEnforcer` 의 *"defense-in-depth — gateway + …"* 서술은 **이제 참**이므로 건드리지 않는다. 게이트웨이를 언급한다는 이유만으로 고치지 말 것.
- [ ] **AC-5** — § 별건(18파일 중복)을 `tasks/INDEX.md` 🔴 항목으로 갱신한다(수치 정정: 12 → **18**, 그리고 "본문 동일" → "**텍스트는 이미 갈라짐, 동작은 아직 동일**").

---

# Related Specs

- [`TASK-MONO-357`](../done/TASK-MONO-357-finance-erp-gateways.md) — 이 stale 을 만든 변경. § Scope "6개 서비스 전부 `ServiceLevelOAuth2Config` 로 게이트웨이 검증 체인을 서비스 안에 복제해 뒀다 … **게이트웨이가 생겨도 이건 그대로 둔다(이중방어)**"
- [`ADR-MONO-048`](../../docs/adr/ADR-MONO-048-shared-reactive-gateway-library.md) § 1.1 (servlet 사본 정정 노트) · § D1 (프레임워크 누출 격리)
- [`platform/shared-library-policy.md`](../../platform/shared-library-policy.md) § Change Rule — 별건이 ADR 게이트에 걸리는 근거
- `docs/adr/ADR-MONO-019` § D5 — entitlement-trust dual-accept (주석이 인용하는 결정)

# Related Contracts

없음 — 주석만. 런타임 표면 불변.

---

# Target Service

`finance-platform` {account, ledger} · `erp-platform` {approval, masterdata, notification, read-model}

---

# Edge Cases

- **단순 치환의 유혹** — `(v1-deferred)` 만 지우면 문장이 *"Mirrors the erp gateway-service validator chain"* 이 되어 **여전히 반쪽만 참**이다. 원 주석의 논리는 *"게이트웨이가 없으니 서비스가 **대신**"* 이었고, 지금은 *"게이트웨이가 있고 서비스가 **또** "* 다. **논리를 다시 써야 한다**(AC-2).
- **`TenantClaimEnforcer` 오탈** — AC-4.
- **테스트 파일 건드리기** — 테스트에도 stale 주석이 있을 수 있으나, 테스트를 손대면 AC-3 의 "무수정 GREEN" 증명이 약해진다. **main 소스만.** 테스트 주석은 별도 확인 후 필요시 후속.

# Failure Scenarios

- **주석만 고치고 별건(18파일)을 "김에" 정리한다** → ADR 게이트(HARDSTOP-09). MONO-349·MONO-357 에서 이 게이트가 실제로 작동한 기록이 있다.
- **stale 을 남긴다** → 다음 사람이 이 파일들을 읽고 *"finance/erp 는 게이트웨이가 없구나"* 라 판단한다. **`TASK-MONO-347` 이 정확히 그렇게 몇 달을 앉아 있었다.**

---

# Provenance

발굴 2026-07-12 — 사용자 질문("게이트웨이 관련 작업은 전부 완료된거야?")에 답하려고 잔여를 전수 조사하다 발견. **내가 357 에서 만든 stale 이다.** D7 이 게이트웨이를 세우면서 "게이트웨이는 없다" 고 말하는 주석을 그대로 뒀다.

분석=Opus 4.8 / 구현 권장=Sonnet (주석 정정 — 단, § Edge Cases 의 "논리 다시 쓰기" 때문에 기계적 치환은 아니다).
</content>
