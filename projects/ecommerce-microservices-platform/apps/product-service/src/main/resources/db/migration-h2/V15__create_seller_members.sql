-- TASK-MONO-752 (ADR-MONO-079 D5) — 셀러 구성원 · 초대.
--
-- 🔴 이 파일은 db/migration/V22__create_seller_members.sql 과 쌍둥이다. 한쪽만 고치면 그쪽 경로(postgres 데모 /
--    h2 local)에서만 엔티티 매핑이 깨진다.
--    🔵 sellers 로 가는 FK 가 없는 것이 **정상**이다: 이 트리는 sellers 테이블을 만든 적이 없다
--    (postgres V14·V15 에 해당하는 h2 파일이 없다 — V13 의 collection_ref 헤더와 같은 사정).

CREATE TABLE seller_members (
    tenant_id  VARCHAR(64) NOT NULL,
    seller_id  VARCHAR(64) NOT NULL,
    account_id VARCHAR(64) NOT NULL,
    role       VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    status     VARCHAR(20) NOT NULL,
    joined_at  TIMESTAMP   NOT NULL,
    CONSTRAINT pk_seller_members PRIMARY KEY (tenant_id, seller_id, account_id),
    CONSTRAINT ck_seller_members_role CHECK (role IN ('MEMBER')),
    CONSTRAINT ck_seller_members_status CHECK (status IN ('ACTIVE', 'REVOKED'))
);

CREATE INDEX idx_seller_members_tenant_account ON seller_members (tenant_id, account_id, status);

CREATE TABLE seller_member_invitations (
    id                  VARCHAR(36)  NOT NULL,
    tenant_id           VARCHAR(64)  NOT NULL,
    seller_id           VARCHAR(64)  NOT NULL,
    email               VARCHAR(320) NOT NULL,
    token_hash          VARCHAR(64)  NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    expires_at          TIMESTAMP    NOT NULL,
    invited_by          VARCHAR(64),
    created_at          TIMESTAMP    NOT NULL,
    accepted_at         TIMESTAMP,
    accepted_account_id VARCHAR(64),
    CONSTRAINT pk_seller_member_invitations PRIMARY KEY (id),
    CONSTRAINT uq_seller_member_invitations_token UNIQUE (token_hash),
    CONSTRAINT ck_seller_member_invitations_status CHECK (status IN ('PENDING', 'ACCEPTED'))
);

CREATE INDEX idx_seller_member_invitations_seller ON seller_member_invitations (tenant_id, seller_id, created_at);
