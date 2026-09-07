# `@demo/public-data`

공개 열람 데이터의 **버전별 저장본** — 계약 · 판독자 · 질의 · 발행자.
`ADR-MONO-070` (2026-09-07) / `TASK-MONO-635`.

```ts
import { createPublicDataReader, bundledEnvelope, queryProducts } from '@demo/public-data';
import bundled from '@demo/public-data/snapshots/store.json';

const { readPublicData } = createPublicDataReader('store', bundledEnvelope('store', bundled));

const result = await readPublicData();
//  result.data      { products, categories }
//  result.envelope  { source, generatedAt, dataVersion, coverage, collectionStatus, … }
//  result.degraded  저장본을 **시도했는데 못 읽어서** 번들로 떨어졌는가
```

---

## 자리 — 왜 `infra/demo/` 인가

`ADR-MONO-068` 이 `@demo/backend-resolver` 에 대해 세운 근거와 **같다**: 이 모듈이 읽는
`DEMO_PUBLIC_DATA_BASE_URL` 은 앱의 설정이 아니라 **데모의 계약**이고, 같은 계약의 다른
클라이언트들(론처 · 부팅 스크립트 · 발행자)이 이미 `infra/demo/` 에 산다.

🔴 루트 `libs/` 가 아닌 이유는 **HARDSTOP-03** 이다 — 그 트리는 프로젝트 비종속이어야 하는데,
이 패키지의 `datasets.ts` 는 아티스트·상품·콘솔이라는 **제품 개념**을 이름으로 든다.

---

## 파일이 이 모양인 이유

| 파일 | 왜 |
|---|---|
| `src/contract.mjs` | **런타임 계약 한 벌.** 소비자가 둘(TS 판독자 · node CLI 발행자)이고 실행 환경이 다르다. 두 벌로 쓰면 한쪽만 고쳐지고, 여기서 그것은 **발행자가 개인정보를 실어 보내는데 판독자는 «계약대로» 라고 읽는** 상태를 뜻한다. |
| `src/contract.d.mts` | 위 파일의 타입 면. 🔴 **로직을 쓰지 마라** — 조건 하나만 들어가도 계약이 두 벌이 된다. |
| `src/datasets.ts` | 공개 필드 **허용 목록**. 타입이 곧 목록이다. |
| `src/transform.mjs` | 백엔드 응답 → 공개 DTO. **보안 성질을 들고 있는 자리**이고, 순수 함수라 네트워크 없이 전수 시험된다. |
| `src/read.ts` | 판독자. **백엔드로 가는 코드가 없다.** |
| `src/query.ts` | 저장본 안의 검색·필터·정렬·페이지. |
| `src/store.mjs` | 저장소 어댑터 — Blob / 로컬 디렉터리. § 왜 둘인가 |
| `src/publish.mjs` | 발행 절차(불변 세대 → 검증 → 포인터 교체 → 정리). |
| `fixtures/` | **실제 시드 DB 의 행** + 음성 대조군. 🔴 지우지 마라 — § 픽스처 |
| `snapshots/` | 번들 시드. **손으로 쓰지 않는다** — § 시드 |

---

## 🔴🔴 왜 저장소 어댑터가 둘인가

이 저장소에는 Vercel 토큰·CLI 가 **없다**(`TASK-MONO-575` 실측). 발행 경로를 Blob 에만 묶으면
**이 세션에서도, CI 에서도, 다음 사람의 노트북에서도 한 번도 돌려볼 수 없는 코드**가 된다 —
그리고 안 돌려본 코드는 처음 돌릴 때 틀린다.

⇒ 같은 발행 절차를 **로컬 디렉터리에도** 적용한다(`--out`). 절차 자체(정상본 보존 · 동시 발행 ·
구세대 정리)를 실제로 실행해 시험할 수 있고, Blob 어댑터가 하는 일은 `put`/`list`/`del` 세
호출로 줄어든다.

🔴 **「로컬로 돌았으니 Blob 도 된다」는 주장이 아니다.** 로컬 어댑터가 통과시키는 것은 **절차**
이지 Blob 이 아니다. 두 어댑터가 같은 발행자 코드를 받는다는 사실이 위험을 줄일 뿐이다.

---

## 🔴 픽스처 — 음성 대조군을 지우지 마라

`fixtures/raw-backend-responses.mjs` 에는 **공개되면 안 되는 값이 일부러 들어 있다**:
아티스트 본명 · `accountId`/`tenantId` · 옵션별 재고 · 회원 전용 본문과 `bodyPreview` ·
삭제된 글 · 숨김 상품 · 비공개 아티스트의 고아 글.

그것이 이 픽스처의 **존재 이유**다. 변환기가 그 필드들을 지우는지 시험하려면 픽스처가 그것을
**갖고 있어야** 한다. 지우면 그 시험은 «아무것도 안 하면서 초록» 이 된다.

🔵 행의 값은 실제 시드에서 가져왔다(`infra/demo/seed/seed-fan.sh` · product-service 의
`V8`/`V9`/`V11` 마이그레이션) — *"픽스처가 현실을 안 담으면 초록도 공허하다"*.

---

## 🔴 시드는 **생성**한다

`snapshots/*.json` 은 손으로 쓰지 않는다:

```bash
node infra/demo/public-data/bin/build-bundled-snapshots.mjs          # 생성
node infra/demo/public-data/bin/build-bundled-snapshots.mjs --check  # 드리프트 검사(CI)
```

손으로 쓰면 ① 시드가 **변환기를 안 지나서** 이 저장소의 가장 눈에 잘 띄는 공개 JSON 이
유일하게 검증 안 된 것이 되고, ② 「백엔드가 실제로 무엇을 주는가」와 무관해져 실제 발행이
처음 도는 날 화면이 깨진다(그리고 원인은 «시드가 거짓말하고 있었다» 라 진단이 오래 걸린다).

---

## 발행

```bash
# 최초 발행 — 백엔드 없이, 저장소의 시드를 그대로
BLOB_READ_WRITE_TOKEN=… DEMO_PUBLIC_DATA_BASE_URL=https://xxx.public.blob.vercel-storage.com \
  node infra/demo/public-data/bin/publish-public-data.mjs --dataset console-sample --seed

# 백엔드가 떠 있을 때
BLOB_READ_WRITE_TOKEN=… DEMO_PUBLIC_DATA_BASE_URL=… \
  node …/publish-public-data.mjs --dataset store --from http://ecommerce.1-2-3-4.sslip.io \
    --copy-images-from http://minio.1-2-3-4.sslip.io

# 리허설 — 토큰 없이 절차만
node …/publish-public-data.mjs --dataset store --seed --out /tmp/blob --base-url https://example.invalid
```

🔴 **자격증명은 환경변수에서만.** `--token` 플래그를 만들지 않았다 — argv 는 `ps` 와 셸
히스토리에 남는다. 그리고 이 스크립트에는 **HTTP 표면이 없다** ⇒ *"공개 요청으로 임의 데이터
발행이 가능하지 않게 한다"* 는 **엔드포인트를 안 만듦으로써** 만족된다(만들고 지키는 것보다 강하다).

🔴 **꺼진 EC2 를 깨우는 Cron 이 없다.** `--from` 이 응답 안 하면 그냥 실패한다.
발행 시점 넷은 전부 «이미 서버가 떠 있는 순간» 이다: 최초(`--seed`, 서버 불필요) · 시드 직후 ·
운영 중 변경 후 · 수동.

### 이미지

내용 주소(`public-data/img/<sha256>.<ext>`)라 같은 바이트는 같은 경로 ⇒ 재발행이 저장량을
안 늘린다. 🔴 **`--copy-images-from` 으로 명시한 오리진만 복사한다.** 데모 시드 썸네일은
Unsplash CDN 이라 이미 공개이고 복사하면 재배포가 된다 — MinIO 이미지는 EC2 가 꺼지면 안
열리므로 복사한다. 자동 판정을 두면 새 출처가 조용히 복사 대상이 된다.

### 구세대 정리 ≠ 공개 철회

- **정리** = 저장량 관리. 구세대 파일을 지운다(기본 5세대 보관).
- **공개 철회** = 그 데이터가 **새 세대에 없게** 만드는 것. **재발행**이 그 수단이다.

재발행 즉시 방문자 경로에서는 사라진다(화면은 포인터만 따라간다). 🔴 그러나 **구세대 URL 을
아는 사람에게는 정리 전까지 남는다** — 철회가 급하면 `--retain 0` 이거나 그 파일을 직접 지운다.
🔵 이미지는 세대에 안 묶여 있어 여기서 안 지운다(다른 세대가 참조할 수 있다).

---

## 시험

```bash
node --test infra/demo/public-data/tests/public-data.test.mjs
```

🔴 시험하지 **않는** 것과 그 이유는 그 파일 머리에 적혀 있다 — Blob 왕복(토큰 없음) ·
백엔드 HTTP 배관(게이트웨이 없음). 🔵 다만 **보안 성질을 들고 있는 쪽은 변환기**이고 그쪽은
네트워크 없이 전수 시험된다.
