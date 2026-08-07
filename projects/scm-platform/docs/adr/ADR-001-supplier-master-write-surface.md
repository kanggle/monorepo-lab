# ADR-SCM-001 — v1 공급사 마스터의 쓰기 표면

**Status**: Proposed
**Date**: 2026-08-07
**Deciders**: (미정 — 승격은 `platform/architecture-decision-rule.md § The ACCEPTED Gate`)
**Gates**: `TASK-SCM-BE-059` 전체

---

## Context

### 무엇이 관측됐나 (실측, 2026-08-07 — `TASK-MONO-510` 데모 시드)

데모 시드가 발주를 만들려다 막혔다:

```
POST /api/v1/procurement/po  {supplierId: <신규>, …}  → SUPPLIER_NOT_FOUND
```

공급사를 만들 방법을 찾다 다음을 확인했다:

| 확인 | 결과 |
|---|---|
| 저장소 전 컨트롤러의 suppliers **생성 매핑** | **0건** |
| 이 프로젝트의 e2e | `ProcurementDbFixtures.insertActiveSupplier` — **직접 JDBC INSERT**(호출자 6개) |
| 도메인 계층 | `Supplier.create(...)` · `SupplierRepository.save(...)` **이미 존재** |
| 콘솔의 공급사 화면 | 없음 |

⇒ **없는 것은 도메인 모델이 아니라 인바운드 표면 하나뿐이다.**

### 🔴 그런데 스펙 세 곳이 서로 다른 말을 한다

1. **`specs/services/procurement-service/architecture.md`** — 공급사 마스터는 **범위 안**이다:
   - L27 `Bounded Context | Procurement (PO + ASN + **Supplier master v1**)`
   - L51-52 *"**Maintain** a v1 internal `suppliers` master with **AES-GCM-encrypted credentials** (S6). v2 will migrate this responsibility to `supplier-service`."*
2. **`specs/contracts/http/procurement-api.md`** — 공급사 엔드포인트가 **0건**이다. PO·ASN·웹훅만 있다.
3. **`ProcurementDbFixtures` Javadoc** — *"procurement-service v1 has no public 'register supplier' endpoint … This is a **deliberate trade-off recorded in the task spec § Failure Scenarios**"*.

🔴 **세 번째의 인용이 dangling 이다.** `TASK-SCM-INT-001` 의 § Failure Scenarios 를 열면
A(Docker fix 실패) · B(크로스프로젝트 소비가 wms 의존) · C(nightly 자원 비용) **셋뿐**이고
공급사 픽스처 얘기는 **한 줄도 없다**. 즉 "의도적 결정" 이라는 주장은 **출처가 없다** —
추론이 주석 안에서 인용으로 굳었다.

⇒ 남는 그림: **마스터는 범위 안인데(1) 채울 길이 없고(관측), 계약은 비어 있으며(2),
비어 있는 이유는 기록되지 않았다(3).** 이 상태에서 엔드포인트를 추가하는 것도, 추가하지
않는 것도 **어느 스펙의 승인도 받지 못한다.** → HARDSTOP-09.

### 왜 "그냥 만들면 되는" 일이 아닌가

- **자격증명이 얽힌다.** 마스터는 S6 로 **AES-GCM 암호화된 공급사 자격증명**을 보유한다
  (`SupplierCredentialsEncryptor` 실재). 등록 API 는 곧 "그 자격증명을 누가 어떤 경로로
  넣는가" 를 정하는 일이다 — 순수 CRUD 가 아니다.
- **v2 이관 경계를 선점한다.** `PROJECT.md` 의 v2 서비스 맵이 `supplier-service` 에
  *"supplier 마스터, contract / SLA, supplier 별 adapter, catalog sync"* 를 배정한다.
  v1 에 공개 등록 표면을 만들면 그 이관의 모양이 바뀐다.
- **계약이 외부 표면이다.** `procurement-api.md` 는 게이트웨이를 통해 노출되는 계약이고,
  콘솔이 그 read surface 를 이미 소비한다(ADR-MONO-013 Model B).

---

## 결정해야 하는 것

**v1 `suppliers` 마스터에 공개 쓰기 표면을 두는가? 둔다면 어디에, 어떤 권한·자격증명
취급으로 두는가?**

### A. procurement-service 에 운영자용 등록/조회 엔드포인트를 추가한다

`POST /api/procurement/suppliers` + `GET /api/procurement/suppliers`(및 단건).
자격증명은 **이 티켓 범위 밖**으로 두고(별도 엔드포인트/후속), 최소 필드(name·status·계약기간)만.

- **얻는 것**: e2e 픽스처 6개 호출자와 데모 시드의 `dbexec` 가 **동시에** 사라진다.
  마스터가 "범위 안" 이라는 architecture.md 의 선언이 실제로 성립한다.
- **대가**: v1 계약 표면이 늘고, v2 이관 시 그 엔드포인트의 운명(이전/폐기/프록시)을
  다시 정해야 한다. 🔴 그리고 **자격증명 없는 공급사**가 정상 상태가 되므로
  `SupplierAdapterPort` 가 그것을 어떻게 다루는지 정의가 필요하다.

### B. 내부 전용 표면으로 둔다 (`/internal/**` 또는 관리 전용 프로파일)

등록을 **시스템 자격**(client_credentials)으로만 열고 게이트웨이 공개 라우트에서 제외.

- **얻는 것**: 외부 계약 표면이 안 늘고, e2e 는 실제 HTTP 를 쓸 수 있다.
- **대가**: 🔴 이 저장소가 **같은 자리에서 세 번 물린 함정**이 여기 있다 — 공유 issuer 에서
  `.authenticated()` 는 "시스템 자격" 을 뜻하지 않는다. 내부 표면을 열면 claim
  discriminator(scope 또는 sub allow-list)를 **반드시** 함께 정해야 한다.
  데모 시드는 운영자 토큰을 쓰므로 시스템 자격으로만 열면 **시드는 여전히 `dbexec` 다**.

### C. 쓰기 표면을 두지 않는다 — 부재를 **명시적 결정으로 승격**한다

마스터는 마이그레이션/운영 시드로만 채우고, architecture.md 에 *"v1 은 관리 경로를 두지
않는다"* 를 명문화한다. 픽스처 주석의 dangling 인용을 이 ADR 참조로 교체한다.

- **얻는 것**: 코드 변경 0. v2 `supplier-service` 의 설계 자유도가 온전히 남는다.
  **현재 상태가 곧 결정이 되므로 드리프트가 사라진다.**
- **대가**: 데모 시드의 `dbexec` 와 e2e 픽스처가 **영구히** 남는다. 🔴 그리고
  `TASK-SCM-BE-060`(발주 상신이 `supplier-mock` 에 의존)과 겹쳐, **공급사를 만들 수도
  없고 발주를 상신할 수도 없는** 상태가 v1 의 공식 입장이 된다 — 그 조합이 받아들일
  만한지 함께 판단해야 한다.

---

## Decision

**미정 (Proposed).** 위 A/B/C 중 하나를 선택해야 한다.

🔵 **판단의 축은 "무엇이 쉬운가" 가 아니다.** 물어야 할 것은 **"v1 공급사 마스터가
운영 대상인가, 배포 산출물인가"** 다. 운영 대상이면 A(운영자가 만든다)이고, 배포
산출물이면 C(마이그레이션이 만든다)이며, B 는 "시스템만 만든다" 는 세 번째 답이다.

🔴 **C 를 고르더라도 그것은 결정이지 현상 유지가 아니다** — 픽스처의 dangling 인용을
고치고 architecture.md 에 부재를 명문화해야 이 조사가 다시 반복되지 않는다.

---

## Consequences

### 공통 (어느 안을 고르든)

- `ProcurementDbFixtures` Javadoc 의 **없는 절을 가리키는 인용**을 이 ADR 참조로 교체한다.
  그 한 줄이 "결정은 이미 났다" 고 읽히게 만들어 조사를 두 번 낭비시켰다.
- `TASK-SCM-BE-059` 의 AC 는 선택된 안에 맞춰 다시 쓴다(현재 AC 는 A 를 전제한다 —
  그 전제가 이 ADR 의 결론을 선취하고 있었다).

### A 를 고르면

- `procurement-api.md` 갱신 → 구현 → e2e 픽스처 6개 호출자 전환 → `seed-scm.sh` 의
  `dbexec` 제거. 🔴 픽스처를 **지우기만 하지 말 것** — 호출자를 전수 grep 해서 옮긴다.
- 자격증명 미보유 공급사에 대한 `SupplierAdapterPort` 의 동작을 정의해야 한다.
- 권한: 데모 운영자 토큰이 그 역할을 **실제로 갖는지 실측**해야 한다. 🔴 wms 마스터
  쓰기가 정확히 여기서 막혔다(`MASTER_WRITE` 를 아무도 못 받는다 — `TASK-MONO-514`).

### B 를 고르면

- claim discriminator 를 명시해야 한다(scope vs sub allow-list). 이 저장소의 선례가 둘 다 있다.
- 데모 시드는 운영자 토큰이므로 **`dbexec` 가 남는다** — 그 사실을 가이드 한계 표에 적는다.

### C 를 고르면

- architecture.md 에 부재를 명문화하고, `TASK-SCM-BE-059` 를 **closed-as-decided** 로 닫는다.
- `TASK-SCM-BE-060` 과의 조합(공급사도 못 만들고 상신도 못 함)을 데모 한계로 확정 기록한다.

---

## References

- `specs/services/procurement-service/architecture.md` § Bounded Context · § Responsibilities
- `specs/contracts/http/procurement-api.md` (공급사 엔드포인트 부재)
- `PROJECT.md` § Service Map v2 (`supplier-service`)
- `tests/e2e/.../ProcurementDbFixtures.java` (dangling 인용의 출처)
- `TASK-SCM-BE-059` · `TASK-SCM-BE-060` · `TASK-MONO-510`(발굴 경위)
