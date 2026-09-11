-- =============================================================================
-- TASK-MONO-659 — 재고 스냅샷·ASN 요약에 warehouse_code 를 비정규화한다
-- =============================================================================
-- 왜: 두 read-model 은 옆 참조를 이미 전부 비정규화하고 있었다
--   · admin_inventory_snapshot : location_code · sku_code · lot_no  (창고만 빠짐)
--   · admin_asn_summary        : supplier_name                      (창고만 빠짐)
-- 그래서 콘솔의 그 칸만 raw UUID 를 그렸고, 콘솔에서는 고칠 수 없었다
-- (읽을 이름이 화면에 도착조차 하지 않는다 — `TASK-PC-FE-277` 이 세 길을 다 재 봤다).
--
-- 🔴 기존 UUID 컬럼(`warehouse_id`)은 **지우지 않는다.** UUID 로 조회하는 경로가 있고,
--    운영자가 그 id 로 지원 요청을 받는다. 이것은 **더하는** 변경이지 바꾸는 변경이 아니다.
--
-- -----------------------------------------------------------------------------
-- 🔴🔴 데이터 질문이 코드 질문보다 앞선다
-- -----------------------------------------------------------------------------
-- 새 컬럼은 **기존 행에서 NULL 로 태어난다.** 프로젝터만 고치면 그 행들은 이벤트가 다시
-- 올 때까지 영원히 NULL 이고, 화면은 계속 UUID(또는 «이름 확인 불가»)를 그린다.
--
-- 🔵 그런데 여기서는 **값이 이미 이 데이터베이스 안에 있다** — `admin_warehouse_ref` 가
--    `warehouse_code` 를 들고 있다. ⇒ 백필이 «이벤트 재생» 이 아니라 **조인 한 번**이다.
--    그래서 재투영(reprojection)을 고르지 않았다: 재생은 훨씬 비싸고, 같은 답을 낸다.
--
-- 🔴 참조가 아직 투영되지 않은 행은 NULL 로 남는다 — 그것이 옳다. 「코드가 없다」와
--    「창고가 없다」는 다른 사실이고, 여기서 빈 문자열이나 UUID 문자열로 채우면 그 둘이
--    합쳐져 다시는 못 갈린다.
-- =============================================================================

ALTER TABLE admin_inventory_snapshot ADD COLUMN warehouse_code VARCHAR(40);
ALTER TABLE admin_asn_summary        ADD COLUMN warehouse_code VARCHAR(40);

-- 백필 — 값의 출처는 이미 있다(`admin_warehouse_ref.warehouse_code`).
UPDATE admin_inventory_snapshot s
   SET warehouse_code = w.warehouse_code
  FROM admin_warehouse_ref w
 WHERE w.id = s.warehouse_id
   AND s.warehouse_code IS NULL;

UPDATE admin_asn_summary a
   SET warehouse_code = w.warehouse_code
  FROM admin_warehouse_ref w
 WHERE w.id = a.warehouse_id
   AND a.warehouse_code IS NULL;
