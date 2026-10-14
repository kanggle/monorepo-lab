# Task ID

TASK-MONO-796

# Title

스토어 «아티스트 굿즈» 18개의 이미지를 상품에 어울리는 사진으로 바꾼다 (`ADR-MONO-077` R2 뒤집기)

# Status

review

# Owner

monorepo

# Task Tags

- code
- data

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet 5 (시드 UPDATE 두 벌 + 픽스처 + 생성기 재실행 — 결정은 소유자가 했다)

---

# Dependency Markers

- **선행 (prerequisite)**: `TASK-MONO-739`(done) — 굿즈 18개 시드(V21 · h2 V14). 이 티켓은 그 행의 `thumbnail_url` 만 덮는다.

# Goal

굿즈샵(web-store)의 «아티스트 굿즈» 카드가 지금은 `placehold.co` 글자 카드(아티스트 색 + 영문 상품명)다. 소유자 요청(2026-10-14): **어울리는 이미지로 넣어 달라**. `ADR-MONO-077` R2 가 «한 줄로 뒤집는 법» 으로 적어 둔 「이미지는 Unsplash 로」 를 적용한다 — 다른 시드 상품(V9 · V11 · V19)과 같은 Unsplash CDN 주소.

# Scope

- `product-service` `db/migration/V23__artist_goods_photos.sql` + 쌍둥이 `db/migration-h2/V16__artist_goods_photos.sql` — 18행 `UPDATE … SET thumbnail_url`. 🔴 V21 · V14 는 고치지 않는다(Flyway checksum).
- `infra/demo/public-data/fixtures/raw-backend-responses.mjs` — `GOODS_IMG(...)` 18곳 → `U('<id>')`, 헬퍼 삭제.
- `infra/demo/public-data/snapshots/store.json` — 생성기(`build-bundled-snapshots.mjs`) 재실행.
- 테스트: `public-data.test.mjs`(R2 단언 교체 + 굿즈끼리 사진 중복 0) · `CollectionRefMigrationTwinTest`(h2 트리 적용 후 18개 전부 Unsplash · 서로 다름).
- `ADR-MONO-077` R2 행에 뒤집힘 기록 한 줄.

범위 밖: 데모 서버 DB(재굽기 · 신선 볼륨 전까지 옛 이미지 — 공개 열람 번들은 배포만으로 바뀐다), web-store 코드(`images.unsplash.com` 은 `next.config.ts` 에 이미 허용).

# Acceptance Criteria

- [x] **AC-1** — 굿즈 18개마다 상품 종류(응원봉 · 포토카드 세트 · 후드티 · 티셔츠 · 포스터)와 설명 문구에 맞는 사진 한 장. 실존 아티스트의 얼굴 · 로고가 주제인 사진은 쓰지 않는다. 18장 전부 서로 다르다.
- [x] **AC-2** — 고른 사진은 전부 **무료 Unsplash 라이선스**(Unsplash+ 아님 — `/photos/<slug>/download` 가 `images.unsplash.com/photo-…` 로 302 하는 것으로 판정)이고, 실제로 쓰는 URL 모양(`?w=600&q=80&auto=format&fit=crop`) 그대로 `200 image/jpeg`.
- [x] **AC-3** — postgres · h2 · 픽스처 · 번들 저장본 네 벌이 같은 18개 주소를 갖는다.
- [x] **AC-4** — `public-data` 테스트 · `CollectionRefMigrationTwinTest` 가 새 이미지를 단언하고 통과한다.

# Related Specs

- `docs/adr/ADR-MONO-077-fan-goods-sold-by-the-ecommerce-store.md` § 라이더 R2

# Related Contracts

- 없음 — `thumbnailUrl` 필드 모양은 그대로(문자열 URL).

# Edge Cases

- 굿즈 사진 한 장(유노 포토카드의 색소폰)은 팬 게시글 사진으로도 쓰이고 있다 — 같은 아티스트(재즈)라 의도적으로 둔다. 굿즈끼리의 중복만 금지.
- web-store `ProductCard`/`ProductImage` 는 `placehold.co` 일 때만 `unoptimized` — Unsplash 는 Next 이미지 최적화를 탄다(다른 시드 상품과 같은 경로).

# Failure Scenarios

- V21 을 직접 고치면 기존 볼륨이 checksum 불일치로 기동 실패 ⇒ 새 버전 UPDATE 로만 바꾼다.
- 한 벌만 고치면 h2 로컬 · 공개 열람 · 실 DB 가 서로 다른 그림을 보인다 ⇒ AC-3.
- Unsplash 사진이 내려가면 깨진 이미지 — 다른 시드 상품과 같은 위험(기존 수용).

---

# Implementation Record (2026-10-14 UTC)

| 상품 | 사진(Unsplash slug) | 고른 이유 |
|---|---|---|
| 루미 응원봉 | `CGmDxiTURBU` | 보라빛 공연장 불빛(루미 색) |
| 루미 포토카드 | `km4mhtfG_H4` | 사진 카드 여러 장 배열 |
| 루미 「밤의 끝」 후드티 | `l5ItSKxctcg` | 밤 조명 아래 후드티 뒷모습 |
| 노아 응원봉 | `m00QXptqTFE` | 푸른 응원 불빛으로 찬 객석(노아 색) |
| 노아 포토카드(작업실 비하인드) | `Jymh4EctxLs` | 녹음실 마이크 |
| 노아 프로듀싱 노트 포스터 | `4XTnkRfoKho` | 벽에 건 포스터 액자 |
| 세아 응원봉 | `Kcx_YuUR-dw` | 분홍빛 공연장(세아 색) |
| 세아 포토카드(안무 연습실) | `3ckWUnaCxzc` | 거울 있는 연습실 |
| 세아 티셔츠 | `elbKS4DY21g` | 흰 티셔츠 플랫레이 |
| 하린 응원봉 | `ATb7h82kuBE` | 손에 든 따뜻한 불빛(하린 앰버) |
| 하린 포토카드(리허설) | `FIabpZvQJ68` | 무대 리허설 흑백 |
| 하린 단독 공연 포스터 | `gB_AQ6Yc6-w` | 붉은 무대 실루엣 = 키 비주얼 |
| 리오 응원봉 | `_QcW7e2RdPc` | 초록 불빛을 든 객석(리오 색) |
| 리오 포토카드(커버 무대) | `ubwn8GI9-2o` | 무대 조명과 관객 |
| 리오 티셔츠 | `eAUE_FmclYE` | 머치 티셔츠 랙 |
| 유노 응원봉 | `DrPcfuaeYFQ` | 어둠 속 링 라이트(유노 슬레이트) |
| 유노 포토카드(재즈 녹음) | `dBWvUqBoOU8` | 재즈 색소폰 |
| 유노 재즈 세션 포스터 | `mI1FkOY_r30` | 조명 아래 색소폰 연주자 |

- **AC-1** — 18장 전부 내려받아 눈으로 확인 후 선택. 실존 아티스트 사진 · 로고 주제 없음. 굿즈끼리 중복 0(테스트 두 곳이 단언).
- **AC-2** — slug 마다 `/photos/<slug>/download` → `images.unsplash.com/photo-…` 302 확인(302 가 안 나온 후보 11개 = Unsplash+ · 일러스트는 탈락). 최종 URL 18개 `curl` → 전부 `200 image/jpeg`(11.8–181.8 KB).
- **AC-3** — V23 · h2 V16 은 같은 본문(헤더 주석만 다름) · 픽스처 18곳 `U('<id>')` · `store.json` 은 생성기 재실행으로 18줄만 바뀜(`fan.json` 변경 0).
- **AC-4** — `node --test tests/public-data.test.mjs` rc=0 · `CollectionRefMigrationTwinTest` 3/3 · `SellerMembersMigrationTwinTest` 1/1(h2 트리 끝까지 적용) — 결과 XML 로 실행 확인.
- ⚪ postgres V23 은 로컬에서 안 돌렸다(Testcontainers) — CI 와 다음 재굽기가 잰다. `ArtistGoodsSeedOnExistingVolumeIntegrationTest` 는 `target=21` 이라 V23 과 무관.
- 🔴 데모 서버 DB 는 재굽기(신선 볼륨) 전까지 옛 `placehold.co` 이미지다. 공개 열람(번들 저장본)은 머지 · Vercel 배포로 바뀐다.

---

## CORRECTION

2026-10-14 UTC — 위 기록의 Scope 와 AC-4 는 **소비자 하나를 빠뜨렸다**. 팬 웹 아티스트 페이지의 굿즈 카드(`PublicArtistGoods`)도 번들 `store.json` 의 `thumbnailUrl` 을 `<img>` 로 렌더하고, 그 테스트 `public-pages.test.tsx:344` 가 `placehold.co` 를 고정하고 있었다 — PR CI 의 `Frontend unit tests` 가 잡았다(로컬 탐색이 글롭 실수로 그 디렉터리를 못 봤다). 같은 PR 에서 단언을 `images.unsplash.com/photo-` 로 고쳤다; 팬 웹은 `images.unoptimized` + `<img>` 라 호스트 허용 변경은 필요 없다. 로컬: `pnpm exec vitest run src/features/public-browse` **9 파일 · 108/108 통과**. 저장소 전체 재탐색 결과 남은 `placehold.co` 고정은 web-store 폴백 테스트 둘(썸네일 없는 상품 경로 — 무관)뿐이다.