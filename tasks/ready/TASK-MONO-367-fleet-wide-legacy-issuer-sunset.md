# Task ID

TASK-MONO-367

# Title

⏳ 2026-08-01 — 레거시 발급자 `,iam` 일몰은 **게이트웨이 7개 전부**의 일이다. `TASK-BE-390` 은 **1개**만 본다

# Status

ready

# Owner

monorepo

# Task Tags

- security
- gateway
- config
- cleanup

---

# ⏳ SCHEDULE GUARD — 2026-08-01 이전 착수 금지

`TASK-BE-398`(레거시 커스텀-JWT 플로우 제거)과 **같은 일몰 날짜**를 공유한다. 그 전에 `,iam` 을 지우면 **아직 레거시 토큰을 들고 있는 소비자를 끊는다**(90일 마이그레이션 윈도우 위반).

**단, 이 task 는 "그 날 무엇을 해야 하는지" 의 유일한 완전한 목록이다.** BE-390 도 BE-398 도 전체를 보고 있지 않다.

---

# Dependency Markers

- **선행 (머지됨)**: [`TASK-MONO-365`](TASK-MONO-365-iam-gateway-audit-issuer-sunset.md) — iam 게이트웨이를 단일-발급자에서 **CSV allowlist** 로 옮기고, **레거시가 사라진 세계에서 엣지가 살아있음을 테스트로 단언**했다(`TokenValidatorUnitTest#theEdgeSurvivesTheLegacyIssuerSunset`). **그 작업이 없었으면 이 일몰이 iam 엣지를 죽였다.**
- **같은 날짜, 부분 범위**:
  - [`TASK-BE-390`](../../projects/ecommerce-microservices-platform/tasks/ready/TASK-BE-390-legacy-iam-issuer-removal-after-2026-08-01.md) — **ecommerce 게이트웨이만**. AC-3 의 grep 이 자기 프로젝트 트리로 한정된다.
  - [`TASK-BE-398`](../../projects/iam-platform/tasks/ready/TASK-BE-398-legacy-custom-jwt-flow-sunset-removal.md) — **발행 측**(`iss=iam` 을 만드는 경로 제거). MONO-365 가 그 파일에 선행 의존 노트를 박아뒀다.
- **가드**: `scripts/check-gateway-drift.sh` **I3**(MONO-365) — 게이트웨이가 단일 발급자로 되돌아가거나 SAS 발급자를 잃으면 CI RED. **이 task 를 수행한 뒤에도 I3 는 GREEN 이어야 한다**(SAS 발급자는 남고 레거시만 빠지므로).

---

# Goal

**`,iam` 레거시 발급자 기본값은 게이트웨이 6개 전부에 있다.** `TASK-BE-390` 은 ecommerce 하나만 본다 ⇒ 그대로 수행하면 **일몰이 6분의 1만 일어나고, 5개 게이트웨이가 폐기된 발급자를 계속 받는다.**

## 착수 전 실측 (2026-07-12, `6dda6db7c` — 착수 시 재검증할 것)

| 게이트웨이 | `allowed-issuers` 기본값 | 레거시 `,iam` |
|---|---|---|
| wms | `${OIDC_ALLOWED_ISSUERS:${OIDC_ISSUER_URL:http://localhost:8081},iam}` | ✅ 있음 |
| scm | `${OIDC_ALLOWED_ISSUERS:${OIDC_ISSUER_URL:http://iam.local},iam}` | ✅ 있음 |
| fan | `${OIDC_ALLOWED_ISSUERS:${OIDC_ISSUER_URL:http://iam.local},iam}` | ✅ 있음 |
| ecommerce | `${OIDC_ALLOWED_ISSUERS:${OIDC_ISSUER_URL:http://localhost:8081},iam}` | ✅ 있음 — **BE-390 이 보는 유일한 것** |
| finance | `${OIDC_ALLOWED_ISSUERS:${OIDC_ISSUER_URL:http://iam.local},iam}` | ✅ 있음 |
| erp | `${OIDC_ALLOWED_ISSUERS:${OIDC_ISSUER_URL:http://iam.local},iam}` | ✅ 있음 |
| **iam** | `${JWT_ALLOWED_ISSUERS:${OIDC_ISSUER_URL:http://localhost:8081},iam}` | ✅ 있음 — **MONO-365 가 방금 이 축에 올려놨다** |

**7개 전부다.**

## 그리고 게이트웨이만이 아니다

servlet 서비스들의 `ServiceLevelOAuth2Config` 도 `AllowedIssuersValidator` 로 같은 allowlist 를 강제한다(finance 2 + erp 4). **그쪽 기본값도 확인할 것** — 게이트웨이만 지우고 서비스가 계속 레거시를 받으면 이중방어의 한 겹이 폐기된 발급자를 통과시킨다.

---

# Scope

## In Scope

1. **게이트웨이 7개**의 `allowed-issuers` 기본값에서 후행 `,iam` 제거.
2. **finance/erp servlet 서비스 6개**의 발급자 allowlist 기본값 확인 및 필요 시 제거(§ Goal 마지막 문단).
3. **테스트**: `iss=iam` 이 **거부**되는 것을 단언하고, SAS 발급자가 **통과**하는 것을 단언한다. iam 은 `TokenValidatorUnitTest#theEdgeSurvivesTheLegacyIssuerSunset` 가 **이미 그것을 단언하고 있다** — 설정만 바꾸면 초록이어야 한다(**그게 그 테스트를 미리 쓴 이유다**).
4. **`TASK-BE-390` 을 이 task 로 흡수하거나 폐기**한다. 두 개가 같은 파일을 다른 범위로 들고 있으면 안 된다.

## Out of Scope

- **발행 측 제거** — `TASK-BE-398` 소유(레거시 커스텀-JWT 플로우 자체). **순서**: BE-398 이 발행을 끊고, 이 task 가 수용을 끊는다. **동시에 하지 않아도 안전하다** — allowlist 는 없는 발급자를 받아도 무해하다. 위험한 건 **반대 순서**(수용을 먼저 끊고 발행이 남으면 살아있는 토큰이 죽는다).
- ADR-MONO-049 구현 — ACCEPT 대기.

---

# Acceptance Criteria

- [ ] **AC-0 (verify-then-act)** — 착수일이 2026-08-01 이후인지, 그리고 **레거시 토큰을 발행하는 경로가 실제로 끊겼는지**(BE-398 머지 확인) 검증한다. 미충족 시 **STOP**.
- [ ] **AC-1** — 게이트웨이 **7개 전부**의 `allowed-issuers` 기본값에 `iam` 이 없다. `grep -rn ',iam' projects/*/apps/gateway-service/src/main/resources/application.yml` → **0건**.
- [ ] **AC-2** — servlet 서비스 6개의 발급자 allowlist 도 정합(§ Scope 2).
- [ ] **AC-3** — `check-gateway-drift.sh` **I3 가 여전히 GREEN**(SAS 발급자는 남아 있으므로). **이 task 가 I3 를 깨뜨리면 잘못한 것이다.**
- [ ] **AC-4** — `iss=iam` 거부를 단언하는 테스트가 각 게이트웨이에 있다. **iam 은 이미 있다**(MONO-365).
- [ ] **AC-5** — `TASK-BE-390` 이 흡수/폐기되어 **같은 파일에 두 개의 범위가 남지 않는다**.
- [ ] **AC-6** — 전체 스위트 0 실패 / 0 skipped.

---

# Related Specs

- `projects/*/apps/gateway-service/src/main/resources/application.yml` (7개)
- `projects/{finance,erp}-platform/apps/*/…/ServiceLevelOAuth2Config.java` (6개 — servlet 측 allowlist)
- `projects/iam-platform/apps/auth-service/src/main/resources/application.yml` L114 (레거시 `iss: iam` — **발행 측**, BE-398 소유)
- `scripts/check-gateway-drift.sh` § I3 (MONO-365)
- `libs/java-gateway/…/security/AllowedIssuersValidator.java` — allowlist 구현(6개 게이트웨이 공용)

# Related Contracts

**있다** — 엣지가 받는 발급자 집합은 클라이언트 가시 계약이다. **이 task 는 좁히는 방향**이므로, 레거시 토큰을 아직 들고 있는 소비자가 있으면 **끊긴다.** AC-0 가 그것을 막는다.

---

# Edge Cases

- **왜 root task 인가** — `,iam` 은 **6개 프로젝트**의 파일에 있다. 프로젝트 task(BE-390)가 다른 프로젝트를 건드리면 `CLAUDE.md` 의 shared/project 경계 위반이다. **이 판단이 `TASK-MONO-365` § AC-4 의 답이다**(선택지 ①BE-390 확장=경계위반 ②root 승격 ③프로젝트별 5개 추가=중복 → **②**).
- **순서를 뒤집지 말 것** — 수용(allowlist)을 발행보다 먼저 끊으면 **살아있는 레거시 토큰이 즉사한다**. § Out of Scope.
- **iam 게이트웨이는 이미 준비돼 있다** — MONO-365 가 allowlist 축으로 옮기고 post-sunset 테스트까지 써뒀다. **여기서 할 일은 `,iam` 한 조각 제거뿐**이고, 그 테스트가 초록이면 끝이다.

# Failure Scenarios

- **BE-390 만 수행하고 끝낸다** → **5개 게이트웨이가 폐기된 발급자를 계속 받는다.** 일몰이 일어났다고 믿으면서. Guard: AC-1.
- **게이트웨이만 지우고 servlet 서비스를 잊는다** → 이중방어의 안쪽 겹이 레거시를 통과시킨다. Guard: AC-2.
- **BE-398 전에 착수** → 살아있는 토큰을 끊는다. Guard: AC-0.

---

# Provenance

발굴 2026-07-12 — `TASK-MONO-365`(iam 게이트웨이 실사) § F2. iam 의 발급자 축을 고치다가 **`,iam` 기본값이 6개 게이트웨이 전부에 있는데 `TASK-BE-390` 은 자기 프로젝트 트리만 grep 한다**는 것을 발견했다.

**단일 프로젝트 task 가 사실은 플릿 전체의 설정을 들고 있는 경우** — 그리고 그 task 를 성실히 수행하면 **일몰이 일어났다고 믿게 되는** 경우다.

분석=Opus 4.8 / 구현 권장=Sonnet (기계적 설정 제거 — 단 **AC-0 의 verify-then-act 와 순서 규율**은 사람 판단).
</content>
