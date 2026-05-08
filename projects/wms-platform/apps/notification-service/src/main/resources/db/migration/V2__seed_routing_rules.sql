-- Seed routing rules per `domain-model.md` § Seeded Routing Rules (v1).
--
-- 6 rules covering the 6 subscribed topics. Operators may toggle `enabled`
-- or adjust matchers via direct DB edit until v2 admin UI ships.
--
-- matcher_json discriminator union shape (mirrored by the
-- RoutingMatcher sealed type):
--   AlwaysMatch                     {"type":"ALWAYS"}
--   PayloadPredicateMatch           {"type":"PAYLOAD_PREDICATE","jsonPath":"$.payload.delta","op":"ABS_GTE","value":100}
--   SeverityThresholdMatch          {"type":"SEVERITY_THRESHOLD","min":"WARNING"}
--
-- channel_targets_json shape (List<ChannelTarget>):
--   [{"channelType":"SLACK","channelId":"wms-alerts","templateKey":"low_stock"}]

INSERT INTO notification_routing_rule
    (id, event_type, matcher_json, channel_targets_json, severity, enabled, created_at, updated_at)
VALUES
    (
        '00000000-0000-7000-8000-000000000001',
        'inventory.low-stock-detected',
        '{"type":"ALWAYS"}'::jsonb,
        '[{"channelType":"SLACK","channelId":"wms-alerts","templateKey":"low_stock"}]'::jsonb,
        'WARNING',
        true,
        NOW(),
        NOW()
    ),
    (
        '00000000-0000-7000-8000-000000000002',
        'inventory.adjusted',
        '{"type":"PAYLOAD_PREDICATE","jsonPath":"$.payload.delta","op":"ABS_GTE","value":100}'::jsonb,
        '[{"channelType":"SLACK","channelId":"wms-alerts","templateKey":"inventory_adjusted"}]'::jsonb,
        'INFO',
        true,
        NOW(),
        NOW()
    ),
    (
        '00000000-0000-7000-8000-000000000003',
        'inbound.inspection.completed',
        '{"type":"PAYLOAD_PREDICATE","jsonPath":"$.payload.discrepancyCount","op":"GT","value":0}'::jsonb,
        '[{"channelType":"SLACK","channelId":"wms-alerts","templateKey":"inspection_discrepancy"}]'::jsonb,
        'WARNING',
        true,
        NOW(),
        NOW()
    ),
    (
        '00000000-0000-7000-8000-000000000004',
        'inbound.asn.cancelled',
        '{"type":"ALWAYS"}'::jsonb,
        '[{"channelType":"SLACK","channelId":"wms-alerts","templateKey":"asn_cancelled"}]'::jsonb,
        'INFO',
        true,
        NOW(),
        NOW()
    ),
    (
        '00000000-0000-7000-8000-000000000005',
        'outbound.order.cancelled',
        '{"type":"PAYLOAD_PREDICATE","jsonPath":"$.payload.priorStatus","op":"IN","value":["PICKED","PACKED","SHIPPED"]}'::jsonb,
        '[{"channelType":"SLACK","channelId":"wms-alerts","templateKey":"order_cancelled"}]'::jsonb,
        'WARNING',
        true,
        NOW(),
        NOW()
    ),
    (
        '00000000-0000-7000-8000-000000000006',
        'outbound.shipping.confirmed',
        '{"type":"ALWAYS"}'::jsonb,
        '[{"channelType":"SLACK","channelId":"wms-shipping","templateKey":"shipping_confirmed"}]'::jsonb,
        'INFO',
        true,
        NOW(),
        NOW()
    );
