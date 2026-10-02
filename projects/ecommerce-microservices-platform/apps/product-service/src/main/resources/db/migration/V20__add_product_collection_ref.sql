-- TASK-MONO-749 (ADR-MONO-079 D3) — 상품의 팬 아티스트 컬렉션.
--
-- collection_ref = 팬 아티스트 id(fan-platform `artists.id`, UUID 문자열). NULL = 컬렉션 없음.
-- 🔴 FK 가 없다 — 다른 프로젝트의 DB 를 가리킨다. 아티스트가 보관돼도 상품은 스토어에 그대로 남는다.
-- 🔵 기존 행은 전부 NULL 로 남는다(백필 없음) — 필드가 생기기 전의 상품은 어느 아티스트의 굿즈도 아니다.
--
-- 🔴 이 파일은 `db/migration-h2/V13__add_product_collection_ref.sql` 과 **쌍둥이**다. 한쪽만 고치면
--    그쪽 경로(postgres 데모 / h2 local)에서만 엔티티의 `collection_ref` 매핑이 깨진다.
--    🔵 인덱스 모양이 다른 것이 **정상**이다: 이 트리는 V13(tenant_id)을 지나왔고 h2 트리는 안 지나왔다.
-- 🔵 굿즈 시드(`TASK-MONO-739`)는 이 컬럼 위에서 **다음 버전**으로 들어간다.

ALTER TABLE products ADD COLUMN collection_ref VARCHAR(64);

-- 모든 읽기는 tenant 로 시작한다(V13 의 idx_products_tenant 와 같은 규율).
CREATE INDEX idx_products_tenant_collection_ref ON products (tenant_id, collection_ref);
