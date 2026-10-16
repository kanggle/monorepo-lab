-- TASK-MONO-795 (ADR-MONO-083 P8 · owner decisions, 2026-10-16 UTC) - E-commerce fine-grained domain roles.
-- Canonical: admin-api.md § 카탈로그 · § 시스템 프리셋; data-model.md § Migration Strategy (TASK-MONO-795);
-- ecommerce iam-integration.md § 업무 역할 (what each role opens).
--
-- The catalog (DomainRoleCatalog) replaced ('ecommerce', 'ECOMMERCE_OPERATOR') with thirteen E-commerce business roles.
-- ECOMMERCE_OPERATOR is now the ENTRY role: auth-service derives it when >= 1 E-commerce business role rides the token;
-- it is never assigned, it is what the ecommerce gateway admits on, and it opens no business API in any ecommerce
-- service on its own. This file moves the DATA to match, so every existing operator keeps the same API set. Same shape
-- as V0058 (Finance, P7):
--   1. seven E-commerce presets with FIXED set ids (INSERT IGNORE - same ids in every environment). No member-operate
--      preset: the member area is read-only (owner decision Q1);
--   2. every set holding ('ecommerce', 'ECOMMERCE_OPERATOR') - presets ecommerce-full / all-domains-full /
--      legacy-compat AND every tenant set (a tenant set that held it was made as «E-commerce 전체 업무») - gains the
--      thirteen roles FIRST, then the ECOMMERCE_OPERATOR rows are deleted. The order is the safety: no set is ever
--      «E-commerce 0», not even mid-file;
--   3. the ecommerce-full description stops saying «세분 미지원»;
--   4. partnership scopes (tenant_partnership.delegated_scope, tenant_partnership_participant.participant_scope -
--      JSON {"domains": [...], "roles": [...]}) get the same rewrite, guarded by JSON_CONTAINS (rows without the role
--      are not touched). auth carries delegated roles verbatim, so an unrewritten delegation would lose E-commerce at
--      deploy.
--
-- Re-run = 0 rows (INSERT IGNORE on PKs · DELETE finds nothing · both UPDATEs are guarded). Forward-only. MySQL-only.
-- No cross-statement user variable (V0016 cycle-2 trap). No semicolon inside any literal (the ITs split on it).
-- Shape: V0059MigrationShapeTest · behaviour on real MySQL: V0059EcommerceRoleMigrationIntegrationTest.

-- ========================================================================
-- 1. Presets (admin-api.md § 시스템 프리셋)
-- ========================================================================
INSERT IGNORE INTO domain_permission_set
    (set_id, tenant_id, system_key, name, description, created_by, created_at, updated_at, version)
VALUES
    ('00000083-0000-7000-8000-000000000027', NULL, 'ecommerce-read',                 'E-commerce 조회', 'E-commerce 주문 · 상품 · 회원 · 정산 · 프로모션 · 배송 · 알림 조회', NULL, NOW(6), NOW(6), 0),
    ('00000083-0000-7000-8000-000000000028', NULL, 'ecommerce-order-operate',        '주문 운영',       'E-commerce 주문 조회 · 변경',          NULL, NOW(6), NOW(6), 0),
    ('00000083-0000-7000-8000-000000000029', NULL, 'ecommerce-product-operate',      '상품 운영',       'E-commerce 상품 · 판매자 조회 · 변경',  NULL, NOW(6), NOW(6), 0),
    ('00000083-0000-7000-8000-000000000030', NULL, 'ecommerce-settlement-operate',   '정산 운영',       'E-commerce 정산 조회 · 변경',          NULL, NOW(6), NOW(6), 0),
    ('00000083-0000-7000-8000-000000000031', NULL, 'ecommerce-promotion-operate',    '프로모션 운영',   'E-commerce 프로모션 조회 · 변경',      NULL, NOW(6), NOW(6), 0),
    ('00000083-0000-7000-8000-000000000032', NULL, 'ecommerce-shipping-operate',     '배송 운영',       'E-commerce 배송 조회 · 변경',          NULL, NOW(6), NOW(6), 0),
    ('00000083-0000-7000-8000-000000000033', NULL, 'ecommerce-notification-operate', '알림 운영',       'E-commerce 알림 템플릿 조회 · 변경',   NULL, NOW(6), NOW(6), 0);

INSERT IGNORE INTO domain_permission_set_role (set_id, domain_key, role_name)
SELECT s.id, v.domain_key, v.role_name
  FROM domain_permission_set s
  JOIN (
        SELECT 'ecommerce-read' AS system_key, 'ecommerce' AS domain_key, 'ECOMMERCE_ORDER_READ' AS role_name
        UNION ALL SELECT 'ecommerce-read',                 'ecommerce', 'ECOMMERCE_PRODUCT_READ'
        UNION ALL SELECT 'ecommerce-read',                 'ecommerce', 'ECOMMERCE_MEMBER_READ'
        UNION ALL SELECT 'ecommerce-read',                 'ecommerce', 'ECOMMERCE_SETTLEMENT_READ'
        UNION ALL SELECT 'ecommerce-read',                 'ecommerce', 'ECOMMERCE_PROMOTION_READ'
        UNION ALL SELECT 'ecommerce-read',                 'ecommerce', 'ECOMMERCE_SHIPPING_READ'
        UNION ALL SELECT 'ecommerce-read',                 'ecommerce', 'ECOMMERCE_NOTIFICATION_READ'
        UNION ALL SELECT 'ecommerce-order-operate',        'ecommerce', 'ECOMMERCE_ORDER_READ'
        UNION ALL SELECT 'ecommerce-order-operate',        'ecommerce', 'ECOMMERCE_ORDER_WRITE'
        UNION ALL SELECT 'ecommerce-product-operate',      'ecommerce', 'ECOMMERCE_PRODUCT_READ'
        UNION ALL SELECT 'ecommerce-product-operate',      'ecommerce', 'ECOMMERCE_PRODUCT_WRITE'
        UNION ALL SELECT 'ecommerce-settlement-operate',   'ecommerce', 'ECOMMERCE_SETTLEMENT_READ'
        UNION ALL SELECT 'ecommerce-settlement-operate',   'ecommerce', 'ECOMMERCE_SETTLEMENT_WRITE'
        UNION ALL SELECT 'ecommerce-promotion-operate',    'ecommerce', 'ECOMMERCE_PROMOTION_READ'
        UNION ALL SELECT 'ecommerce-promotion-operate',    'ecommerce', 'ECOMMERCE_PROMOTION_WRITE'
        UNION ALL SELECT 'ecommerce-shipping-operate',     'ecommerce', 'ECOMMERCE_SHIPPING_READ'
        UNION ALL SELECT 'ecommerce-shipping-operate',     'ecommerce', 'ECOMMERCE_SHIPPING_WRITE'
        UNION ALL SELECT 'ecommerce-notification-operate', 'ecommerce', 'ECOMMERCE_NOTIFICATION_READ'
        UNION ALL SELECT 'ecommerce-notification-operate', 'ecommerce', 'ECOMMERCE_NOTIFICATION_WRITE'
       ) v ON v.system_key = s.system_key;

-- ========================================================================
-- 2. Every set holding ECOMMERCE_OPERATOR: add the thirteen roles, THEN delete ECOMMERCE_OPERATOR
-- ========================================================================
INSERT IGNORE INTO domain_permission_set_role (set_id, domain_key, role_name)
SELECT h.set_id, 'ecommerce', v.role_name
  FROM (SELECT DISTINCT set_id FROM domain_permission_set_role
         WHERE domain_key = 'ecommerce' AND role_name = 'ECOMMERCE_OPERATOR') h
  CROSS JOIN (
        SELECT 'ECOMMERCE_ORDER_READ' AS role_name
        UNION ALL SELECT 'ECOMMERCE_ORDER_WRITE'
        UNION ALL SELECT 'ECOMMERCE_PRODUCT_READ'
        UNION ALL SELECT 'ECOMMERCE_PRODUCT_WRITE'
        UNION ALL SELECT 'ECOMMERCE_MEMBER_READ'
        UNION ALL SELECT 'ECOMMERCE_SETTLEMENT_READ'
        UNION ALL SELECT 'ECOMMERCE_SETTLEMENT_WRITE'
        UNION ALL SELECT 'ECOMMERCE_PROMOTION_READ'
        UNION ALL SELECT 'ECOMMERCE_PROMOTION_WRITE'
        UNION ALL SELECT 'ECOMMERCE_SHIPPING_READ'
        UNION ALL SELECT 'ECOMMERCE_SHIPPING_WRITE'
        UNION ALL SELECT 'ECOMMERCE_NOTIFICATION_READ'
        UNION ALL SELECT 'ECOMMERCE_NOTIFICATION_WRITE'
       ) v;

DELETE FROM domain_permission_set_role WHERE domain_key = 'ecommerce' AND role_name = 'ECOMMERCE_OPERATOR';

-- ========================================================================
-- 3. ecommerce-full description (guarded - a re-run matches 0 rows)
-- ========================================================================
UPDATE domain_permission_set
   SET description = 'E-commerce 카탈로그 역할 전부(주문 · 상품 · 회원 · 정산 · 프로모션 · 배송 · 알림)', updated_at = NOW(6)
 WHERE system_key = 'ecommerce-full'
   AND description <> 'E-commerce 카탈로그 역할 전부(주문 · 상품 · 회원 · 정산 · 프로모션 · 배송 · 알림)';

-- ========================================================================
-- 4. Partnership scopes: ECOMMERCE_OPERATOR -> the thirteen roles (JSON_CONTAINS-guarded)
-- ========================================================================
UPDATE tenant_partnership
   SET delegated_scope = JSON_SET(delegated_scope, '$.roles',
           JSON_MERGE_PRESERVE(
               JSON_REMOVE(JSON_EXTRACT(delegated_scope, '$.roles'),
                           JSON_UNQUOTE(JSON_SEARCH(JSON_EXTRACT(delegated_scope, '$.roles'), 'one', 'ECOMMERCE_OPERATOR'))),
               JSON_ARRAY('ECOMMERCE_ORDER_READ', 'ECOMMERCE_ORDER_WRITE', 'ECOMMERCE_PRODUCT_READ',
                          'ECOMMERCE_PRODUCT_WRITE', 'ECOMMERCE_MEMBER_READ', 'ECOMMERCE_SETTLEMENT_READ',
                          'ECOMMERCE_SETTLEMENT_WRITE', 'ECOMMERCE_PROMOTION_READ', 'ECOMMERCE_PROMOTION_WRITE',
                          'ECOMMERCE_SHIPPING_READ', 'ECOMMERCE_SHIPPING_WRITE', 'ECOMMERCE_NOTIFICATION_READ',
                          'ECOMMERCE_NOTIFICATION_WRITE'))),
       updated_at = NOW(6)
 WHERE JSON_CONTAINS(JSON_EXTRACT(delegated_scope, '$.roles'), '"ECOMMERCE_OPERATOR"');

UPDATE tenant_partnership_participant
   SET participant_scope = JSON_SET(participant_scope, '$.roles',
           JSON_MERGE_PRESERVE(
               JSON_REMOVE(JSON_EXTRACT(participant_scope, '$.roles'),
                           JSON_UNQUOTE(JSON_SEARCH(JSON_EXTRACT(participant_scope, '$.roles'), 'one', 'ECOMMERCE_OPERATOR'))),
               JSON_ARRAY('ECOMMERCE_ORDER_READ', 'ECOMMERCE_ORDER_WRITE', 'ECOMMERCE_PRODUCT_READ',
                          'ECOMMERCE_PRODUCT_WRITE', 'ECOMMERCE_MEMBER_READ', 'ECOMMERCE_SETTLEMENT_READ',
                          'ECOMMERCE_SETTLEMENT_WRITE', 'ECOMMERCE_PROMOTION_READ', 'ECOMMERCE_PROMOTION_WRITE',
                          'ECOMMERCE_SHIPPING_READ', 'ECOMMERCE_SHIPPING_WRITE', 'ECOMMERCE_NOTIFICATION_READ',
                          'ECOMMERCE_NOTIFICATION_WRITE')))
 WHERE participant_scope IS NOT NULL
   AND JSON_CONTAINS(JSON_EXTRACT(participant_scope, '$.roles'), '"ECOMMERCE_OPERATOR"');
