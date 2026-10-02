-- TASK-MONO-749 (ADR-MONO-079 D3) — 상품의 팬 아티스트 컬렉션.
--
-- collection_ref = 팬 아티스트 id(fan-platform `artists.id`, UUID 문자열). NULL = 컬렉션 없음.
-- 🔴 FK 가 없다 — 다른 프로젝트의 DB 를 가리킨다.
--
-- 🔴 이 파일은 `db/migration/V20__add_product_collection_ref.sql` 과 **쌍둥이**다. 한쪽만 고치면
--    그쪽 경로에서만 조용히 갈라진다.
--    🔵 인덱스가 `(tenant_id, collection_ref)` 가 아니라 `(collection_ref)` 인 것이 **정상**이다:
--    이 트리는 V11 까지 tenant_id 컬럼을 products 에 안 만들었다(V12 헤더와 같은 사정).

ALTER TABLE products ADD COLUMN collection_ref VARCHAR(64);

CREATE INDEX idx_products_collection_ref ON products (collection_ref);
