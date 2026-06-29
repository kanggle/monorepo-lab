# ADR-008: 내부 전용 엔드포인트(`/api/internal/**`)의 인증 경계를 네트워크-단독에서 앱-레이어 방어심층으로 승격

- **Status**: PROPOSED (2026-06-29)
- **Date**: 2026-06-29
- **Authors**: backend (code-marker discovery sweep — `TASK-BE-118-fix-002` 미티켓 TODO 승격)
- **Supersedes**: —
- **Superseded by**: —
- **History**: PROPOSED 2026-06-29 — `auth-service` `SecurityConfig` 의 `TASK-BE-118-fix-002` TODO("내부 클러스터 IP 대역 제한(NetworkPolicy) 또는 mTLS/서비스계정 토큰 기반 인증을 별도 ADR로 승격 후 여기서 강제할 것")가 미티켓 상태로 잔존함이 발굴 스윕에서 확인됨. 본 ADR 은 그 결정을 정식화한다. **ACCEPTANCE 는 user-gated** — 사용자가 D2 방향(권장: 앱-레이어 토큰 게이트)을 확정한 뒤 별도로 ACCEPTED 전환 + per-service 구현 task 등록(ADR-007 / ADR-MONO-022 선례와 동형).

---

## Context

ecommerce 의 일부 서비스는 `/api/internal/**` prefix 로 **운영자 전용(내부)** 엔드포인트를 노출한다. 대표 트리거:

- `auth-service` `AdminUserRepublishController` — `POST /api/internal/users/republish-signup-events`. 호출 시 **전 사용자의 signup 이벤트를 일괄 재발행**한다(다운스트림 read-model 재구성용 운영 툴). 영향 범위가 큰 작업이다.
- 동일 `/api/internal/**` prefix 는 `order-service`·`batch-worker` 에도 존재한다(컨트롤러 present). 각 서비스의 현행 필터 자세는 채택 task 에서 감사한다.

**현행 경계 (검증됨, auth-service 기준)**: `SecurityConfig` 에서 `/api/internal/**` 를 `permitAll()` 로 둔다. 인증 경계는 전적으로 **네트워크 레벨**에 의존한다:

1. `gateway-service` 라우팅 테이블에 미등록 → 공개 Ingress(외부 인터넷)에서 도달 불가.
2. 접근 경로 = `kubectl port-forward` 또는 동일 Kubernetes 네트워크 내부 운영자 도구.

### 이 ADR 이 필요한 이유 (HARDSTOP-09)

현행은 **앱 레이어 인증/인가/감사가 0** 이다(필터가 `permitAll`). 따라서:

- 클러스터 내부에 도달할 수 있는 **어떤 워크로드/포트포워드든** 신원 증명 없이 고영향 작업(전 사용자 이벤트 재발행)을 호출할 수 있다.
- 호출 주체를 식별·감사할 앱-레이어 근거가 없다(누가 republish 를 돌렸는지 앱이 모른다).
- 보안 자세가 **단일 네트워크 경계**에 전적으로 의존한다 — NetworkPolicy 미적용 클러스터(CNI 미지원/오설정)에서는 그 경계마저 사라진다.

`integration-heavy`/B2C 자세에서 "내부 전용"은 **방어심층(defense-in-depth)** 으로 다뤄야 하며, 그 결정(어떤 메커니즘을 어디서 강제할지)은 서비스 경계·운영 워크플로·비밀 관리 자세를 확정하므로 코드로 즉흥 결정할 수 없다 → **별도 ADR(HARDSTOP-09)**.

`data_sensitivity=internal`, `scale_tier=startup` 컨텍스트(PROJECT.md)를 결정의 비례성 기준으로 삼는다.

---

## Decision

### D1 — 위협 모델 / 범위

대상 위협 = **클러스터 내부에 도달한 비인가 주체**(측면 이동한 침해 파드, 과도 권한 포트포워드, 오설정된 in-cluster 툴)가 내부 엔드포인트를 호출하는 것. 외부 인터넷 위협은 게이트웨이 라우팅-배제로 이미 차단됨(이 ADR 의 1차 대상 아님). 범위 = ecommerce 전 서비스의 `/api/internal/**`.

### D2 — 결정: 네트워크 경계 + **앱-레이어 토큰 게이트**(방어심층)

`/api/internal/**` 의 `permitAll()` 을 **앱-레이어 인증 게이트**로 대체하되, 네트워크 경계를 폐기하지 않고 **두 레이어를 모두 유지**한다:

- **레이어 1 (인프라)**: 게이트웨이 라우팅-배제(유지) + **NetworkPolicy** 로 내부 엔드포인트 도달을 운영자 도구 네임스페이스/파드로 제한(문서화된 필수 인프라 통제).
- **레이어 2 (앱)**: 서비스가 **서비스계정/공유시크릿 bearer 토큰**을 필터에서 검증한다(아래 D3). 토큰 부재/불일치 = 401.

### D3 — 토큰 메커니즘 (앱-레이어)

- 운영자/툴이 `X-Internal-Token` 헤더로 **구성된 공유 시크릿**을 제시한다.
- 필터에서 **constant-time 비교**(`MessageDigest.isEqual`)로 검증(타이밍 사이드채널 회피; SCM webhook HMAC 검증과 동형 패턴).
- **prod 프로파일에서 시크릿 미설정/blank = fail-closed**(전체 deny) — 운영 배포가 무방비로 뜨지 않게. 로컬/dev 프로파일은 명시적 완화 허용(문서화).
- 회전 = 시크릿(K8s Secret/env) 갱신. IAM 발급 JWT 가 **아님**(운영 툴은 IAM 신원이 없음 — D-Alt3 참조).

### D4 — NetworkPolicy = 문서화된 필수 인프라 통제

레이어 1 NetworkPolicy 매니페스트를 `infra/` 에 둔다(현재 암묵적 "게이트웨이가 라우팅 안 함" 가정을 **명시적 선언적 통제**로 승격). 레이어 2(앱 토큰)가 CNI-독립적 보강이 되어, NetworkPolicy 비강제 클러스터에서도 앱 게이트가 남는다.

### D5 — 감사

내부 엔드포인트 호출을 **감사 로그**로 남긴다(주체 식별 토큰 ID/이름 + 작업 + 결과). 고영향 운영 작업(전 사용자 republish 등)의 사후 추적 가능성 확보.

### D6 — 적용 범위 / 롤아웃

본 정책은 ecommerce 전 `/api/internal/**`(auth/order/batch-worker)에 적용한다. **per-service 구현 task** 가 ACCEPTANCE 후 등록되며, 각 task 는 해당 서비스의 현행 필터 자세를 감사하고 D2–D5 를 적용한다. 공유 가능한 필터/검증 로직은 서비스-로컬로 시작하되, 3서비스 채택 후 중복이 확인되면 `libs/` 승격을 후속으로 검토(과잉 추상화 회피).

---

## 버린 대안 (Rejected alternatives)

- **D-Alt1 — 현행 유지(네트워크-단독)**. 게이트웨이 라우팅-배제 + NetworkPolicy 만으로 충분하다고 보는 안. **기각**: 단일 경계 의존 → NetworkPolicy 비강제/오설정 시 무방비; 앱-레이어 신원·감사 0; 고영향 운영 작업에 비해 방어심층 부재. (단, NetworkPolicy 자체는 D4 로 **유지·승격**한다 — 폐기가 아니라 보강.)
- **D-Alt2 — mTLS / 서비스 메시**. 인증서 기반 상호 인증으로 강한 신원. **기각(현 시점)**: 메시 도입·인증서 회전·운영 부담이 `scale_tier=startup` + 단일 운영 툴 호출 빈도에 **불비례**. 민감도/스케일 상승 시 재검토(이 ADR 이 막지 않음 — 레이어 2 를 mTLS 로 교체 가능한 여지를 남김).
- **D-Alt3 — IAM 발급 JWT 강제**. 일반 API 처럼 IAM OIDC 토큰 요구. **기각**: 내부 운영 툴/포트포워드 호출자는 **IAM 사용자 신원이 없다**(서비스-투-서비스/운영자 CLI 컨텍스트). 억지로 IAM 을 끼우면 운영 워크플로를 깨고 부적절한 결합을 만든다. 공유시크릿 서비스계정 토큰(D3)이 이 컨텍스트에 비례적.

---

## Consequences

**긍정**

- 방어심층: 네트워크 경계 + 앱 게이트 2레이어 → 한 레이어 실패가 곧 노출이 아님.
- 앱-레이어 신원·감사(D5) → 고영향 운영 작업의 호출 주체 추적 가능.
- CNI-독립: NetworkPolicy 비강제 클러스터에서도 앱 토큰이 남음.
- 비례성: 공유시크릿 게이트는 startup 자세에 맞는 저-인프라 통제(메시 불필요).

**부정 / 비용**

- 시크릿 관리(배포 주입 + 회전)가 추가됨.
- 운영 워크플로 변경: 내부 엔드포인트 호출 시 `X-Internal-Token` 제시 필요(런북 갱신).
- per-service 구현 task N개(auth/order/batch-worker) 필요.

**중립 / 후속**

- prod fail-closed(D3)는 시크릿 미주입 배포를 의도적으로 막는다(배포 체크리스트 반영 필요).
- `platform/security-rules.md`(공유)·서비스 `architecture.md` 의 내부-경로 섹션을 ACCEPTANCE 시 D2–D5 로 정합(권위 스펙 갱신은 구현 task 범위).
