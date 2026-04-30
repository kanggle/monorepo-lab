CREATE TABLE content_access_policies (
    id                    VARCHAR(36)   NOT NULL,
    visibility_key        VARCHAR(50)   NOT NULL,
    required_plan_level   VARCHAR(20)   NOT NULL,
    description           VARCHAR(200)  NULL,
    PRIMARY KEY (id),
    UNIQUE INDEX idx_cap_visibility_key (visibility_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO content_access_policies (id, visibility_key, required_plan_level, description)
VALUES
    ('00000000-0000-0000-0000-000000000101', 'MEMBERS_ONLY', 'FAN_CLUB', '팬 클럽 회원 전용 콘텐츠');
