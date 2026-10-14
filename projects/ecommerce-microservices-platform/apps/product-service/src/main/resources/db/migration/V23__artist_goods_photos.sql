-- =============================================================================
-- TASK-MONO-796 — 아티스트 굿즈 18개의 이미지를 상품에 어울리는 Unsplash 사진으로 바꾼다
-- =============================================================================
-- V21 은 `placehold.co` 자리표시(아티스트 색 + 영문 상품명)였다(ADR-MONO-077 R2). 소유자가 R2 를 뒤집었다:
-- 다른 시드 상품(V9·V11·V19)처럼 **Unsplash CDN 주소**를 쓴다. 상품 종류(응원봉·포토카드·후드티·티셔츠·
-- 포스터)와 설명 문구(연습실·작업실·리허설·재즈 세션…)에 맞춰 한 장씩 골랐다 — 실존 아티스트의 얼굴·로고는 없다.
--
-- 🔴🔴 V21 을 고치지 않았다. 적용된 마이그레이션의 내용을 바꾸면 기존 볼륨이 Flyway checksum 으로 기동에서
--    죽는다 ⇒ 새 버전으로 UPDATE 한다(V11 이 V9 를 덮은 것과 같은 모양).
-- 🔴 이 파일은 `db/migration-h2/V16__artist_goods_photos.sql` 과 **쌍둥이**다. 같은 값이
--    `infra/demo/public-data/fixtures/raw-backend-responses.mjs` 에도 있고 `snapshots/store.json` 은 생성기로 만든다.
-- 🔴 데모 서버 DB 는 재굽기(신선 볼륨) 전까지 옛 이미지다 — 공개 열람(번들 저장본)은 머지·배포만으로 바뀐다.
-- =============================================================================

UPDATE products SET thumbnail_url = 'https://images.unsplash.com/photo-1763322564752-12ce8fae2bfe?w=600&q=80&auto=format&fit=crop' WHERE id = 'b0000000-0000-0000-0000-000000000025'; -- 루미 공식 응원봉 (보라빛 공연장 불빛)
UPDATE products SET thumbnail_url = 'https://images.unsplash.com/photo-1736710744799-4ba71f73cf73?w=600&q=80&auto=format&fit=crop' WHERE id = 'b0000000-0000-0000-0000-000000000026'; -- 루미 포토카드 세트 (사진 카드 묶음)
UPDATE products SET thumbnail_url = 'https://images.unsplash.com/photo-1768264229225-5ea270d62f78?w=600&q=80&auto=format&fit=crop' WHERE id = 'b0000000-0000-0000-0000-000000000027'; -- 루미 「밤의 끝」 후드티 (밤 조명 속 후드티)
UPDATE products SET thumbnail_url = 'https://images.unsplash.com/photo-1741810977205-9fc28e5416da?w=600&q=80&auto=format&fit=crop' WHERE id = 'b0000000-0000-0000-0000-000000000028'; -- 노아 공식 응원봉 (푸른 응원 불빛 객석)
UPDATE products SET thumbnail_url = 'https://images.unsplash.com/photo-1767474365536-ef81bfa24c8a?w=600&q=80&auto=format&fit=crop' WHERE id = 'b0000000-0000-0000-0000-000000000029'; -- 노아 포토카드 세트 (작업실 마이크)
UPDATE products SET thumbnail_url = 'https://images.unsplash.com/photo-1676883343810-e3e4b63d8ecd?w=600&q=80&auto=format&fit=crop' WHERE id = 'b0000000-0000-0000-0000-000000000030'; -- 노아 프로듀싱 노트 포스터 (벽에 건 포스터 액자)
UPDATE products SET thumbnail_url = 'https://images.unsplash.com/photo-1520242739010-44e95bde329e?w=600&q=80&auto=format&fit=crop' WHERE id = 'b0000000-0000-0000-0000-000000000031'; -- 세아 공식 응원봉 (분홍빛 공연장)
UPDATE products SET thumbnail_url = 'https://images.unsplash.com/photo-1524594152303-9fd13543fe6e?w=600&q=80&auto=format&fit=crop' WHERE id = 'b0000000-0000-0000-0000-000000000032'; -- 세아 포토카드 세트 (안무 연습실)
UPDATE products SET thumbnail_url = 'https://images.unsplash.com/photo-1620799139507-2a76f79a2f4d?w=600&q=80&auto=format&fit=crop' WHERE id = 'b0000000-0000-0000-0000-000000000033'; -- 세아 시그니처 티셔츠 (흰 티셔츠)
UPDATE products SET thumbnail_url = 'https://images.unsplash.com/photo-1578874763335-4d21bab88a99?w=600&q=80&auto=format&fit=crop' WHERE id = 'b0000000-0000-0000-0000-000000000034'; -- 하린 공식 응원봉 (손에 든 따뜻한 불빛)
UPDATE products SET thumbnail_url = 'https://images.unsplash.com/photo-1780698370183-079584658a65?w=600&q=80&auto=format&fit=crop' WHERE id = 'b0000000-0000-0000-0000-000000000035'; -- 하린 포토카드 세트 (무대 리허설)
UPDATE products SET thumbnail_url = 'https://images.unsplash.com/photo-1780295725666-31c0d6d00f96?w=600&q=80&auto=format&fit=crop' WHERE id = 'b0000000-0000-0000-0000-000000000036'; -- 하린 단독 공연 포스터 (붉은 무대 실루엣)
UPDATE products SET thumbnail_url = 'https://images.unsplash.com/photo-1663026164508-b07adc48ca6c?w=600&q=80&auto=format&fit=crop' WHERE id = 'b0000000-0000-0000-0000-000000000037'; -- 리오 공식 응원봉 (초록 불빛을 든 객석)
UPDATE products SET thumbnail_url = 'https://images.unsplash.com/photo-1758550445758-165aeab5b26e?w=600&q=80&auto=format&fit=crop' WHERE id = 'b0000000-0000-0000-0000-000000000038'; -- 리오 포토카드 세트 (커버 무대 현장)
UPDATE products SET thumbnail_url = 'https://images.unsplash.com/photo-1576871337622-98d48d1cf531?w=600&q=80&auto=format&fit=crop' WHERE id = 'b0000000-0000-0000-0000-000000000039'; -- 리오 시그니처 티셔츠 (머치 티셔츠 랙)
UPDATE products SET thumbnail_url = 'https://images.unsplash.com/photo-1491441555545-d6b8e24ffa33?w=600&q=80&auto=format&fit=crop' WHERE id = 'b0000000-0000-0000-0000-000000000040'; -- 유노 공식 응원봉 (어둠 속 링 라이트)
UPDATE products SET thumbnail_url = 'https://images.unsplash.com/photo-1415201364774-f6f0bb35f28f?w=600&q=80&auto=format&fit=crop' WHERE id = 'b0000000-0000-0000-0000-000000000041'; -- 유노 포토카드 세트 (재즈 세션 색소폰)
UPDATE products SET thumbnail_url = 'https://images.unsplash.com/photo-1641185867887-e8a0c79214b9?w=600&q=80&auto=format&fit=crop' WHERE id = 'b0000000-0000-0000-0000-000000000042'; -- 유노 재즈 세션 포스터 (색소폰 연주자)
