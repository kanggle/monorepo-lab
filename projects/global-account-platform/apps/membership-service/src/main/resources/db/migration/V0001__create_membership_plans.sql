CREATE TABLE membership_plans (
    id             VARCHAR(36)   NOT NULL,
    plan_level     VARCHAR(20)   NOT NULL,
    name           VARCHAR(100)  NOT NULL,
    price_krw      INT           NOT NULL DEFAULT 0,
    duration_days  INT           NOT NULL,
    description    TEXT          NULL,
    is_active      BOOLEAN       NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    UNIQUE INDEX idx_membership_plans_level (plan_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO membership_plans (id, plan_level, name, price_krw, duration_days, description, is_active)
VALUES
    ('00000000-0000-0000-0000-000000000001', 'FREE', '무료', 0, 0, '기본 무료 플랜 (영구)', TRUE),
    ('00000000-0000-0000-0000-000000000002', 'FAN_CLUB', '팬 클럽', 9900, 30, '프리미엄 멤버십 (월간)', TRUE);
