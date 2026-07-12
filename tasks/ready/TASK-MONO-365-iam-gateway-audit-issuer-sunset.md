# Task ID

TASK-MONO-365

# Title

iam 게이트웨이 실사 결과 — **레거시 발급자 일몰이 iam 자기 엣지를 죽인다** (`TASK-BE-398` 미인지) + `,iam` 기본값이 **6개 게이트웨이**에 있는데 `TASK-BE-390`은 **1개**만 본다

# Status

ready

# Owner

monorepo

# Task Tags

- security
- gateway
- config

---

# Dependency Markers

- **발굴 출처**: `ADR-MONO-049` § D6 이 *"iam 게이트웨이는 비용으로 제외했지 안전성으로 승인한 적 없다 — 별도 실사 필요"* 라 남긴 항목. 그 실사를 수행한 결과다.
- **충돌 대상 (날짜 게이트, 2026-08-01)**:
  - [`TASK-BE-398`](../../projects/iam-platform/tasks/ready/TASK-BE-398-legacy-custom-jwt-flow-sunset-removal.md) — 레거시 커스텀-JWT 플로우(`/api/auth/login`) 제거. **`iss=iam` 을 발행하는 그 경로다.** 이 task 는 iam 게이트웨이를 **라우트 관점으로만** 언급하고 **발급자는 언급하지 않는다.**
  - [`TASK-BE-390`](../../projects/ecommerce-microservices-platform/tasks/ready/TASK-BE-390-legacy-iam-issuer-removal-after-2026-08-01.md) — `allowed-issuers` 에서 `,iam` 제거. **ecommerce 게이트웨이만** 범위(AC-3 의 grep 이 그 트리로 한정).
- **⚠️ 이 task 자체는 날짜 게이트가 아니다** — 아래 F1 은 **지금 이미 발현 중**이고, 8/1 에 터지는 게 아니라 **8/1 에 총체적으로 죽는다**.

---

# 실사 결과 — iam 게이트웨이는 **결함이 아니었다** (프레이밍 정정)

`ADR-MONO-049` 초안과 내 앞선 보고는 iam 게이트웨이를 *"JWT 검증·JWKS 캐시·레이트리밋을 직접 구현한, 공유 보안 경계 밖의 엣지"* 로 규정했다. **실사 결과 그 프레이밍은 틀렸다.**

| 항목 | 실측 (`8ffe49793`) | 판정 |
|---|---|---|
| JWT 서명 검증 | `TokenValidator` → **`libs/java-security` 의 `Rs256JwtVerifier`** (플랫폼 공용). JJWT `verifyWith(publicKey)` — **alg confusion 방어**(공개키로는 비대칭 alg만), `alg:none` 거부 | ✅ **손으로 짠 암호 코드가 아니다** |
| 신원 헤더 위조 차단 | `stripSpoofedHeaders()` → `X-Account-ID` · `X-Device-Id` · `X-Tenant-Id`. **public route 통과 이전에 실행**(strip → enrich 순서 보존) | ✅ |
| strip 집합이 충분한가 | iam 다운스트림(account/auth/admin/membership)이 **실제로 읽는** 신원 헤더 census → **정확히 그 셋뿐**. `X-User-*` / `X-Roles` / `X-Account-Type` 을 읽는 곳 **0** | ✅ **집합이 좁은 게 아니라 어휘가 좁다**(IdP 이지 업무 도메인이 아님). **위조 가능 헤더 갭 없음** — 356 의 `X-Seller-Scope` 와 같은 클래스로 의심했으나 **아니다** |
| 테넌트 legacy fallback | `enabled` **기본 `false`** (`EdgeGatewayProperties:64`) | ✅ **닫힘-기본** |
| rate limit | `TokenBucketRateLimiter` = **Redis + atomic Lua** | ✅ **분산**(per-instance in-memory 아님) |
| `iss` 검증 | `expected-issuer` 강제 | ⚠️ **아래 F1** |

**추가로 iam 만 갖고 있는 것**: 토큰 강제무효화 체크(Redis `access:invalidate-before:`) · `/internal/tenants/{id}/**` 경로-테넌트 스코프 강제 · `device_id` 전파. **다른 게이트웨이엔 없다.**

⇒ **iam 게이트웨이는 "공유 라이브러리 갭" 이 아니라 "다른, 정당한 설계" 다.** `ADR-MONO-048` 이 iam 을 추출 대상에서 뺀 것은 **비용 판단으로도 옳았고 안전성으로도 문제없다.** ADR-049 § D6 을 그렇게 정정했다.

**그리고 이 실사가 `ADR-MONO-049` 의 D1 을 뒤집었다**: `TokenValidator` 가 `libs/java-security` 를 쓰고 있었고 — **그 모듈은 이미 존재했다.** ADR 은 그걸 "신설하자" 고 제안하고 있었다(§ 1.5 정정 삽입 완료). **실사가 아니었으면 없는 모듈을 만들 뻔했다.**

---

# Goal — 실사가 실제로 찾은 것

## 🔴 F1 — 레거시 발급자 일몰이 **iam 자기 엣지를 죽인다**

IAM 은 **두 개의 발급자**를 발행한다:

```
auth-service/application.yml:114   issuer: iam                        ← 레거시 커스텀 JWT
auth-service/application.yml:177   issuer: ${OIDC_ISSUER_URL:...}     ← SAS / OIDC
```

**게이트웨이 7개의 발급자 설정:**

| 게이트웨이 | 설정 | 받는 발급자 |
|---|---|---|
| wms · scm · fan · ecommerce · finance · erp | `allowed-issuers: ${OIDC_ALLOWED_ISSUERS:${OIDC_ISSUER_URL:…},iam}` | **CSV allowlist — SAS + 레거시 둘 다** |
| **iam** | `expected-issuer: ${JWT_EXPECTED_ISSUER:iam}` | **단일 값. 그것도 레거시.** |

`JWT_EXPECTED_ISSUER` 는 **어떤 compose / 배포 yml 에도 없다** — 전수 grep 0건. **오버라이드된 적이 없다.**

**따라서 지금 이미**: SAS/OIDC 로 발급된 토큰(= 다른 6개 게이트웨이가 **1순위**로 받는 그것)은 **iam 자기 엣지에서 401 로 거부된다.** iam 게이트웨이가 앞단에 선 라우트는 `/api/accounts/**` · `/api/admin/**` · `/api/auth/**` 다.

**그리고 8/1 에**: `TASK-BE-398` 이 레거시 커스텀-JWT 플로우를 제거하면 **`iss=iam` 이 더 이상 발행되지 않는다** ⇒ **iam 게이트웨이가 받을 수 있는 발급자가 0 이 된다 = 엣지 전면 사망.** BE-398 은 iam 게이트웨이를 **라우트** 항목으로만 적어뒀고 **발급자는 모른다.**

**왜 아무도 못 봤나**: console-bff 가 IAM 서비스를 **게이트웨이 우회로 직결**하기 때문이다(`TASK-MONO-347` 이 기록한 내부 호출 패턴). **엣지를 아무도 안 지나므로 깨져 있어도 아무 데서도 안 터진다** — 이 세션이 반복해서 만난 바로 그 모양이다.

## 🟠 F2 — `,iam` 레거시 기본값은 **6개** 게이트웨이에 있는데, `TASK-BE-390` 은 **1개**만 본다

BE-390(ecommerce `tasks/ready/`)의 AC-3 grep 은 `gateway-service/src/main/resources docker-compose* tests/` — **자기 프로젝트 트리로 한정**된다. 그런데 `,iam` 기본값은 **wms · scm · fan · ecommerce · finance · erp 전부**에 있다.

⇒ BE-390 을 그대로 수행하면 **ecommerce 하나만 레거시 발급자를 거부하고, 나머지 5개는 폐기된 발급자를 계속 받는다.** 일몰이 절반만 일어난다.

---

# Scope

## In Scope

1. **F1 — iam 게이트웨이를 나머지 6개와 같은 축으로 옮긴다**: `expected-issuer`(단일) → **`allowed-issuers`(CSV allowlist)**, 기본값에 **SAS 발급자 + 레거시** 둘 다. **`libs/java-security` 의 `AllowedIssuersValidator` 를 쓸 수 있는지 검토**(iam 게이트웨이는 SCG 이지만 Spring Security RS 를 안 쓰므로, `TokenValidator` 에 발급자 allowlist 를 직접 넣는 편이 자연스러울 수 있다 — **ADR-049 가 ACCEPT 되면 그 모듈이 정본이 된다**).
   - **AC 는 "SAS 토큰이 iam 엣지를 통과한다" 를 실제로 단언해야 한다.** 설정만 바꾸고 테스트가 없으면 이 결함이 어떻게 두 달을 살아남았는지 잊는 것이다.
2. **F2 — 일몰 범위를 6개 게이트웨이로 확장**: BE-390 에 나머지 5개를 명시하거나(그 task 는 ecommerce 소유), **root task 로 승격**한다. **판단 필요** — § Edge Cases.
3. **가드**: 게이트웨이별 발급자 설정이 **한 축에서 벗어나면** 잡히는가? (**`TASK-MONO-360` 의 `check-gateway-drift.sh` 에 얹는 것이 자연스럽다** — 이미 게이트웨이 선언/노출을 보고 있다.)

## Out of Scope

- **레거시 커스텀-JWT 플로우 제거 자체** — `TASK-BE-398` 소유, **2026-08-01 게이트**. 이 task 는 **그것이 안전하게 일어날 수 있게** 만드는 선행 작업이다.
- iam 게이트웨이의 `libs/java-gateway` 이관 — § 실사 결과, **불필요하다**(다른 설계이지 갭이 아니다).
- ADR-049 의 구현(D5-1~4) — **ACCEPT 대기**.

---

# Acceptance Criteria

- [ ] **AC-1 — F1 재현 먼저**: 현재 iam 게이트웨이가 **SAS 발급자 토큰을 거부한다**는 것을 **테스트로 먼저 재현**한다(RED). 고친 뒤 GREEN. **재현 없이 고치면 고쳤는지 알 수 없다.**
- [ ] **AC-2 — F1 수정**: iam 게이트웨이가 **SAS 발급자 + 레거시 `iam` 둘 다** 받는다. 나머지 6개와 **같은 형태**(CSV allowlist).
- [ ] **AC-3 — 일몰 안전성 단언**: **레거시 발급자를 뺀 설정**(= BE-398 이후의 세계)에서 **SAS 토큰이 통과하고 iam 엣지가 살아있다**는 것을 테스트가 단언한다. **이것이 이 task 의 존재 이유다** — 8/1 에 BE-398 이 iam 엣지를 죽이지 않는다는 증거.
- [ ] **AC-4 — F2 범위 정합**: `,iam` 기본값을 가진 게이트웨이 **6개 전부**가 일몰 계획에 들어 있다(BE-390 확장 또는 root task 승격 — § Edge Cases 의 판단 기록 필수).
- [ ] **AC-5 — 가드**: 게이트웨이 발급자 설정이 축에서 벗어나면 CI 가 RED. `check-gateway-drift.sh` 확장 권장. **mutation 필수**(iam 을 단일-값으로 되돌리면 RED / 한 게이트웨이의 allowlist 에서 SAS 발급자를 빼면 RED). **`code-changed` 와 AND 금지** — 이 드리프트는 `application.yml` 한 줄로 도착한다.
- [ ] **AC-6 — BE-398 에 선행 의존 명시**: BE-398 의 Dependency Markers 에 *"이 task 선행 없이 착수하면 iam 엣지가 죽는다"* 를 기록한다. **날짜 게이트 task 는 8/1 에 사람이 컨텍스트 없이 집어들 가능성이 높다 — 그때 이 노트가 유일한 방어선이다.**
- [ ] **AC-7** — 전체 스위트 0 실패 / 0 skipped.

---

# Related Specs

- `projects/iam-platform/apps/gateway-service/src/main/resources/application.yml` L78 (`expected-issuer`)
- `projects/iam-platform/apps/auth-service/src/main/resources/application.yml` L114 (레거시 `iss: iam`) · L177 (SAS `iss`)
- `projects/{wms,scm,fan,ecommerce,finance,erp}-platform/apps/gateway-service/src/main/resources/application.yml` (`allowed-issuers`)
- [`ADR-MONO-049`](../../docs/adr/ADR-MONO-049-framework-neutral-security-library.md) § 1.5 (이 실사가 발견한 `libs/java-security` 실재) · § D6 (iam 판정 정정본)
- `libs/java-security/src/main/java/com/example/security/jwt/Rs256JwtVerifier.java` — iam 게이트웨이가 실제로 쓰는 검증기
- `scripts/check-gateway-drift.sh` (MONO-360) — AC-5 의 확장 대상

# Related Contracts

**있다** — iam 엣지가 받는 발급자 집합은 **클라이언트 가시 계약**이다. 넓히는 방향(SAS 추가)은 **net-zero**(기존 레거시 토큰 계속 유효). 좁히는 방향(레거시 제거)은 BE-398 소유.

---

# Edge Cases

- **F2 를 어디에 담을 것인가 — 판단 필요.** BE-390 은 **ecommerce 프로젝트 task** 인데 문제는 **6개 프로젝트 공통**이다. ① BE-390 을 확장(프로젝트 task 가 다른 프로젝트를 건드림 = 경계 위반) ② **root task 로 승격**하고 BE-390 은 그쪽을 가리키게 함(권장) ③ 프로젝트마다 task 5개 추가(중복). **선택과 근거를 기록할 것.**
- **iam 게이트웨이에 `AllowedIssuersValidator` 를 억지로 끼우지 말 것** — iam 은 Spring Security Resource Server 를 쓰지 않는다(`TokenValidator` + `Rs256JwtVerifier` 경로). **allowlist 개념만 가져오면 되지 클래스를 가져올 필요는 없다.** 정합을 명분으로 다른 설계를 부수는 것은 이 task 의 목적이 아니다.
- **"지금 안 터지니 괜찮다" 로 미루지 말 것** — 안 터지는 이유는 **console-bff 가 엣지를 우회**하기 때문이다(347). 엣지를 쓰기 시작하는 순간 발현하고, 8/1 에는 우회 여부와 무관하게 **엣지가 죽는다**.

# Failure Scenarios

- **BE-398 이 이 task 없이 8/1 에 착수된다** → 레거시 발급자가 사라지고 **iam 게이트웨이가 모든 토큰을 거부**한다. **날짜 게이트 task 는 두 달 뒤 컨텍스트 없이 집어들린다** — Guard: AC-6(BE-398 에 선행 의존 명시).
- **설정만 고치고 테스트를 안 쓴다** → 이 결함이 **두 달간 아무 데서도 안 터진 이유**(엣지 우회)가 그대로라, 다음 회귀도 똑같이 안 보인다. Guard: AC-1/AC-3.
- **F2 를 잊는다** → 일몰이 ecommerce 에서만 일어나고 5개 게이트웨이가 **폐기된 발급자를 계속 받는다**. Guard: AC-4.
- **iam 을 "정합" 명분으로 `libs/java-gateway` 로 이관** → 실사 결과 **갭이 아니다**. 다른 설계를 부수는 리팩토링이 된다. Guard: § Out of Scope.

---

# Provenance

발굴 2026-07-12 — `ADR-MONO-049` § D6 이 남긴 *"iam 게이트웨이는 안전성으로 승인된 적 없다"* 항목의 실사. **실사는 내가 의심한 것(하드코딩된 암호 검증·위조 가능 헤더)을 전부 무죄로 판정했고, 대신 내가 보지 않던 곳에서 진짜 결함을 찾았다** — 발급자 축.

**부수 효과가 더 컸다**: iam 의 `TokenValidator` 를 읽다가 그것이 **`libs/java-security` 를 쓰고 있다**는 것을 발견했고, 그 모듈은 **ADR-049 가 "신설하자" 고 제안하던 바로 그것**이었다(§ 1.5 정정). **실사하지 않았으면 이미 있는 모듈을 다시 만들 뻔했다.**

분석=Opus 4.8 / 구현 권장=**Opus** (발급자 축은 라이브 인증 계약이고, F2 의 범위 판단은 프로젝트 경계 결정이다).
</content>
