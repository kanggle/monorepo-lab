# ADR-MONO-001 — PORT_PREFIX 슬롯 부족과 7개+ 프로젝트 동시 운영 정책

**Status:** PROPOSED
**Date:** 2026-05-02
**Decision driver:** fan-community + scm + erp + mes 프로젝트 추가 예정 → 현행 PORT_PREFIX 정책의 사용 가능 슬롯이 부족함
**Supersedes:** none (PORT_PREFIX 의 첫 monorepo-level ADR)
**Related:** [CLAUDE.md § Port Namespace Convention](../../CLAUDE.md), [TEMPLATE.md § Port Namespace Convention](../../TEMPLATE.md)

---

## 1. Context

현재 `CLAUDE.md` L252–266 와 `TEMPLATE.md` L289–339 의 PORT_PREFIX 규약은 다음과 같이 동작한다:

```yaml
ports: ["${PORT_PREFIX:-N}XXXX:YYYY"]
```

- `XXXX` = 원래 4자리 컨테이너/표준 포트 (5432, 8080, 9092 등)
- `N` = 프로젝트별 prefix 1자리 (ecommerce=1, wms=2, GAP=3 reserved)

### 현재 할당

| Prefix | 프로젝트 | 상태 |
|---|---|---|
| 1 | ecommerce-microservices-platform | 사용 중 |
| 2 | wms-platform | 사용 중 |
| 3 | global-account-platform | reserved (사용 중) |
| 4–5 | (미할당) | 가용 |
| 6 | (사용 가능하지 않음 — 아래 참조) | ❌ |
| 7+ | 사용 불가 (TEMPLATE.md 명시) | ❌ |

### 슬롯 한계의 실제 — TEMPLATE.md 의 진술보다 더 좁다

호스트 포트 max = `65535`. prefix `N` + 4자리 포트 의 최대값 = `N9999`.

`N9999 ≤ 65535` 이어야 유효 → `N ≤ 5` 만 안전. **prefix 6 도 부분적으로만 동작한다**:

| prefix | 가능한 원래 포트 범위 | 흔한 서비스 포트 적용 가능 여부 |
|---|---|---|
| 1–5 | 0000–9999 (전부) | ✅ 모두 가능 |
| 6 | 0000–5535 | ❌ 6379(redis), 8080(gateway), 9092(kafka) 모두 65535 초과 |
| 7+ | (사실상 불가) | ❌ 표준 포트 거의 모두 65535 초과 |

따라서 **실효 가용 슬롯은 5개** (1–5).

### 신규 프로젝트 4개 → 슬롯 2개 부족

향후 추가 예정 프로젝트:

1. fan-community (B2C 팬덤 — GAP IdP 1차 소비자, [ADR-001](../../projects/global-account-platform/docs/adr/ADR-001-oidc-adoption.md))
2. scm-platform (B2B 공급망)
3. erp-platform (B2B 회계·구매·HR 기간계)
4. mes-platform (B2B 제조 실행)

**필요 슬롯: 4** (위 4개 신규) **+ 3 (기존)** = **7**, 가용 = **5** → 부족분 **2 슬롯**.

---

## 2. Constraints

이 결정에 영향을 주는 제약:

- **포트폴리오 단일 작성자**: 운영 복잡도가 높은 솔루션은 채택 비용 > 이득. 단순함이 강한 가산점.
- **로컬 dev 시나리오**: 한 머신에서 동시에 몇 개의 프로젝트를 띄우는가 — 실제로는 **2–3개 동시**가 일반적. 7개 동시는 드물다 (cross-project integration 테스트 시 한정).
- **Docker Desktop / WSL2 환경**: 사용자는 Windows + WSL2 + Docker Desktop. resource 한계로 7개 동시 기동 자체가 비현실적.
- **CI 의존성**: GitHub Actions 의 Testcontainers 잡들은 각 프로젝트 별로 독립 컨테이너 — host port 충돌과 무관.
- **포트폴리오 분산 배포**: 각 프로젝트가 standalone 레포로도 추출 가능해야 함 (sync-portfolio.sh) — extracted 레포는 단일 프로젝트 자체 prefix 만 알면 충분.
- **PRODUCTION-ORIENTED 시그널**: 채용 평가자 시점에서 "포트 충돌을 회피하려고 야금야금 prefix 를 쌓는" 식의 hack 보다 명확한 정책이 보이는 게 좋음.

---

## 3. Options

### Option A — 정적 5슬롯 + 페어드 공유 (least invasive)

prefix 1–5 를 정적으로 할당하고, **두 프로젝트가 한 prefix 를 공유**하되 동시에 기동하지 않도록 운영 규약을 둔다.

**제안 매핑:**

| prefix | 1차 프로젝트 | 2차 (공유) | 동시기동 가능? |
|---|---|---|---|
| 1 | ecommerce | — | — |
| 2 | wms | mes | ❌ — `.env` 의 `PORT_PREFIX` 로 1회 결정 |
| 3 | global-account-platform | — | — |
| 4 | fan-community | erp | ❌ — `.env` 의 `PORT_PREFIX` 로 결정 |
| 5 | scm | — | — |

페어 선정 근거:
- **wms ↔ mes**: 둘 다 backoffice 형 enterprise B2B. 한 dev 세션에서 둘을 동시 보는 시나리오 적음.
- **fan-community ↔ erp**: B2C consumer ↔ back-office accounting. 도메인이 정반대라 동시 보는 일 거의 없음.

**구현:**
- 각 프로젝트 `.env.example` 의 `PORT_PREFIX=<n>` 은 **1차 프로젝트의 디폴트** 값.
- 2차 프로젝트의 `.env.example` 도 같은 prefix 를 디폴트로 갖되, 1차와 동시에 기동하면 충돌이 즉시 드러나도록 README 에 경고 명시.
- `docker compose up` 시점의 충돌은 docker 자체가 즉시 실패시키므로 sliently 망가지지 않음.

**Pros:**
- ✅ 현행 규약·코드·CI 거의 그대로. 변경 = `.env.example` 디폴트 + 문서.
- ✅ 7개 프로젝트가 모두 한 머신에서 같은 prefix 정책으로 운영됨.
- ✅ standalone 레포 추출 시에도 변경 없음.

**Cons:**
- ⚠️ 페어드 공유 프로젝트를 동시 기동하려면 한쪽의 `.env` 에서 prefix 를 일시 override 해야 함 (운영 의존).
- ⚠️ 페어 선정이 "도메인 동시성" 가정에 의존. 가정이 틀어지면 재할당 필요.

**Estimated effort:** XS (반나절 — 디폴트값 + 문서 갱신만)

---

### Option B — Service-class 단위 정적 포트 레지스트리 (medium)

PORT_PREFIX 를 프로젝트 단위에서 **서비스 단위**로 이동. 각 서비스에 고유한 호스트 포트를 부여하고 단일 레지스트리(`infra/host-ports.md`)에 기록한다.

**예시 매핑:**

| 서비스 | 호스트 포트 |
|---|---|
| ecommerce-postgres | 15432 |
| wms-postgres | 25432 |
| gap-postgres | 35432 |
| fan-community-postgres | 45432 |
| scm-postgres | 55432 |
| erp-postgres | 16432 (5자리, 6XXXX 회피) |
| mes-postgres | 17432 |

각 프로젝트의 `docker-compose.yml` 에서:

```yaml
postgres:
  ports: ["${POSTGRES_HOST_PORT:-15432}:5432"]
```

**Pros:**
- ✅ N 개 프로젝트로 무한 확장 (단, 각 항목이 65535 한계 안에 들어가야 함).
- ✅ 페어드 공유 같은 운영 가정 불필요 — 모든 프로젝트 동시 기동 가능.

**Cons:**
- ⚠️ 모든 프로젝트의 docker-compose.yml 을 수정해야 함 (3개 기존 + 4개 신규).
- ⚠️ 단일 레지스트리 유지 비용. 새 서비스 추가 시 매번 등록.
- ⚠️ 프로젝트 standalone 추출 시 자체 prefix 정책으로 fallback 필요 (이중 정책).

**Estimated effort:** M (2–3일 — 기존 3개 프로젝트 docker-compose 전수 마이그레이션 + 레지스트리 신설 + 검증 스크립트)

---

### Option C — 내부 네트워크 위주 + 선택적 호스트 노출 (biggest)

`docker-compose internal network` 를 기본으로 하고, 호스트 포트는 **개발자 노출이 필요한 엔드포인트** 에만 dynamic port (`:0`) 로 부여. Traefik/Caddy reverse proxy 도입해 hostname 기반 접근.

```yaml
postgres:
  expose: ["5432"]   # internal only, no host port
gateway:
  ports: ["${PORT_PREFIX:-1}8080:8080"]   # only this is host-exposed
```

**Pros:**
- ✅ production 환경과 가까운 패턴 — 채용 평가자에게 가산점.
- ✅ 이론적으로 무한 프로젝트 확장 (호스트 포트는 gateway 만 점유).
- ✅ 보안 측면에서 노출 면적 축소.

**Cons:**
- ⚠️ Largest 변경 — 모든 프로젝트의 docker-compose 수정 + Traefik/Caddy 설정 추가.
- ⚠️ 초기 학습 곡선 (특히 다른 사용자가 README 보고 따라할 때).
- ⚠️ Testcontainers · 외부 도구 (DBeaver, Redis Insight, Kafka UI) 가 호스트 포트를 가정 — 추가 wiring 필요.

**Estimated effort:** L (5–7일 — Traefik 도입 + 모든 프로젝트 마이그레이션 + 외부 도구 연동 가이드 작성)

---

## 4. Recommendation

**Option A (정적 5슬롯 + 페어드 공유)** 를 권고한다.

근거:

1. **포트폴리오 dev 시나리오에 충분**: 한 머신에서 7개 동시 기동은 비현실적 (Docker resource 한계). 페어 가정이 깨질 가능성 < 5%.
2. **변경 비용 vs 이득**: Option B 와 C 의 운영 안정성 향상은 단일 작성자 환경에서는 over-engineered. 채용 평가자에게도 "정적 5슬롯 + 명시적 페어드 정책" 이 더 명확한 시그널.
3. **현행과의 호환성**: ecommerce/wms/GAP 의 docker-compose.yml 그대로. 신규 4개 프로젝트만 정의하면 끝.
4. **에스컬레이션 경로 보존**: 향후 동시성 요구가 커지면 Option B/C 로 마이그레이션. 본 ADR 의 결정이 그 경로를 막지 않음.

권고하지 않는 이유:

- **Option B**: 작은 이득(이론상 무한 확장) 위해 모든 docker-compose 를 다시 짜야 함. 7→8번째 프로젝트가 등장할 가능성도 낮음.
- **Option C**: production-grade 시그널은 강하지만, 외부 도구·dev UX·CI 와의 마찰이 큼. 채용 평가자에게 보일 가산점 < 운영 비용.

---

## 5. Decision Items (사용자 확인 필요)

이 ADR 을 PROPOSED → ACCEPTED 로 전환하기 위해 사용자가 확인해줄 항목:

### D1. 옵션 선택

- [ ] **A** — 정적 5슬롯 + 페어드 공유 (권고)
- [ ] **B** — Service-class 단위 정적 포트 레지스트리
- [ ] **C** — 내부 네트워크 + reverse proxy

### D2. (Option A 선택 시) 페어 매핑

- [ ] **D2-a** 권고 매핑 그대로:
  - prefix 2: wms ↔ mes
  - prefix 4: fan-community ↔ erp
- [ ] **D2-b** 다른 페어:
  - prefix 4: scm ↔ mes (manufacturing+supply chain — 동시 가능성이 더 높지 않을까? 검토 요)
  - prefix 5: erp 단독
  - prefix ?: fan-community 단독
- [ ] **D2-c** 사용자 정의 매핑

### D3. 신규 prefix 할당 확정 시점

- [ ] **D3-a** 본 ADR ACCEPTED 와 동시에 모두 할당 (4=fan-community, 5=scm, 페어 매핑 적용)
- [ ] **D3-b** 각 신규 프로젝트 부트스트랩 PR 에서 그때 할당

### D4. ADR 채택 여부

- [ ] PROPOSED → ACCEPTED 로 승격하고, 본 결정에 따라 `CLAUDE.md` § Port Namespace Convention 과 `TEMPLATE.md` § Port Namespace Convention 동시 갱신

---

## 6. Consequences

### Option A 선택 시

다음 변경이 발생한다 (모두 monorepo 루트):

1. **`CLAUDE.md` § Port Namespace Convention**:
   - 할당 표를 5개 prefix + 페어드 매핑으로 갱신
   - "prefix 6 부분 미동작·7+ 불가" 사실을 명시 (현행은 "above 6 cannot be used" 만 표기)
2. **`TEMPLATE.md` § Port Namespace Convention (PORT_PREFIX)**:
   - 같은 표 갱신 + "동시 기동 매트릭스" 섹션 신설
   - 페어드 공유 시 운영 가이드 (`.env` override 절차) 추가
3. **각 신규 프로젝트의 `.env.example`** (부트스트랩 시): `PORT_PREFIX=<assigned>` 라인 + 페어드 공유 경고 (해당 시)
4. **신규 프로젝트 부트스트랩 태스크 (TASK-MONO-022 등)** 에 prefix 할당이 포함됨

### Option B/C 선택 시

별도 follow-up 태스크에서 마이그레이션 PR 작성:
- B: TASK-MONO-022 (host-ports registry + 7개 프로젝트 docker-compose 일괄 수정)
- C: TASK-MONO-022 (Traefik 도입 ADR 별도) + 프로젝트별 마이그레이션 task

---

## 7. Risks & Mitigations

| Risk | Mitigation |
|---|---|
| (Option A) 사용자가 페어드 공유를 잊고 두 프로젝트 동시 기동 시 충돌 | docker 가 즉시 실패 — 데이터 corruption 위험 0. README 페어 명시 |
| (Option A) 향후 8번째 프로젝트 추가 시 슬롯 또 부족 | 본 ADR 보강(또는 Option B/C 로 마이그레이션) ADR-MONO-002 추가 |
| (어느 옵션이든) Jaeger UI(16686) 등 5자리 포트는 prefix 적용 불가 | 현행 규약대로 unprefixed 유지. 5자리 포트는 충돌 가능성 낮음 |
| 페어 가정이 미래에 깨짐(예: WMS+MES 동시 운영 요구) | `.env` PORT_PREFIX 일시 override 로 즉시 회피 가능 |

---

## 8. References

- 외부:
  - [TCP/IP host port limit (RFC 793)](https://datatracker.ietf.org/doc/html/rfc793) — port range 0–65535
- monorepo:
  - [CLAUDE.md § Port Namespace Convention](../../CLAUDE.md)
  - [TEMPLATE.md § Port Namespace Convention](../../TEMPLATE.md)
  - [ADR-001 (GAP) — OIDC Authorization Server](../../projects/global-account-platform/docs/adr/ADR-001-oidc-adoption.md) (fan-community 가 OIDC consumer 로 등장하는 근거)
- 메모리:
  - `project_port_prefix_convention.md` (현행 규약 요약)

---

## 9. Decision Log

| Date | Status | Note |
|---|---|---|
| 2026-05-02 | PROPOSED | 초안 작성. D1–D4 사용자 확인 대기. |
