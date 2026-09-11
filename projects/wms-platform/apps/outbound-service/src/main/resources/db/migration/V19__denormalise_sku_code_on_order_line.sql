-- =============================================================================
-- TASK-MONO-659 — 출고 주문 라인에 sku_code 를 비정규화한다
-- =============================================================================
-- 왜: 이 서비스는 인입 시점에 **코드로 조회해서** UUID 를 얻는다
--   · FulfillmentRequestedConsumer : requireText(lineNode, "skuCode") → findSkuByCode(...)
--   · WebhookInboxProcessorService : l.skuCode()                     → findSkuByCode(...)
--   · ReceiveOrderService          : findSku(skuId) 로 SkuSnapshot 을 이미 들고 있고,
--                                    발행하는 OrderReceivedEvent 에는 skuCode 를 싣는다
-- ⇒ 🔴 **코드를 손에 쥐고 있다가 버렸다.** 주문 라인만 UUID 로 남아 콘솔이 그것을 그렸고,
--    콘솔에서는 고칠 수 없었다(그 화면에 읽을 이름이 도착하지 않는다).
--
-- 🔴 `sku_id` 는 **지우지 않는다.** UUID 로 조회하는 경로가 있다 — 더하는 변경이다.
--
-- -----------------------------------------------------------------------------
-- 🔴🔴 데이터 질문이 코드 질문보다 앞선다
-- -----------------------------------------------------------------------------
-- 새 컬럼은 기존 행에서 NULL 로 태어난다. 🔵 그런데 값의 출처가 **이 데이터베이스 안에**
-- 있다 — `sku_snapshot.sku_code`(이 서비스의 마스터 read-model, `V1__init_master_readmodel`).
-- ⇒ 백필이 조인 한 번으로 끝난다. 이벤트 재생이 필요 없다.
--
-- 🔴 스냅샷에 없는 SKU 를 참조하는 행은 NULL 로 남는다 — 그것이 옳다. 「코드를 모른다」를
--    빈 문자열이나 UUID 문자열로 채우면 「코드가 없는 행」과 영영 못 갈린다.
-- =============================================================================

ALTER TABLE outbound_order_line ADD COLUMN sku_code VARCHAR(40);

UPDATE outbound_order_line l
   SET sku_code = s.sku_code
  FROM sku_snapshot s
 WHERE s.id = l.sku_id
   AND l.sku_code IS NULL;
