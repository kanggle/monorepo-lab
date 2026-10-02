-- TASK-MONO-752 (ADR-MONO-079 D5 · rider R4) — 셀러 구성원: 사람의 풀 계정을 셀러에 붙인다.
--
-- seller_members         : 한 셀러에 사람 여럿. role 은 하나(MEMBER, R4). status ACTIVE | REVOKED.
--                          account_id = IAM 풀 계정 id(스토어 토큰 sub). FK 없음 — 다른 DB(IAM)를 가리킨다.
--                          🔴 sellers.account_id(셀러 기계 계정, ADR-042 D2)와 다른 것이다 — 역방향 잠금 투영
--                             (AccountStatusChangedSellerConsumer)은 기계 계정만 본다.
-- seller_member_invitations : 운영자 초대. 토큰 원문은 저장하지 않는다(SHA-256 hex 만). 1회용(status) · 만료(expires_at).
--
-- 🔵 새 테이블만 — 기존 테이블·열·제약을 건드리지 않는다. 그래서 V21 까지 있는 기존 볼륨에 그대로 올라간다
--    (ArtistGoodsSeedOnExistingVolumeIntegrationTest 는 이 버전이 생기면서 업그레이드 대상을 V21 로 못 박았다).
-- 🔴 이 파일은 db/migration-h2/V15__create_seller_members.sql 과 쌍둥이다. h2 트리에는 sellers 테이블이 없어
--    (V14·V15 를 그 트리가 안 지나왔다) 그쪽에는 sellers 로 가는 FK 가 없다 — 그 차이가 정상이다.

CREATE TABLE seller_members (
    tenant_id  VARCHAR(64) NOT NULL,
    seller_id  VARCHAR(64) NOT NULL,
    account_id VARCHAR(64) NOT NULL,
    role       VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    status     VARCHAR(20) NOT NULL,
    joined_at  TIMESTAMP   NOT NULL,
    CONSTRAINT pk_seller_members PRIMARY KEY (tenant_id, seller_id, account_id),
    CONSTRAINT fk_seller_members_seller FOREIGN KEY (tenant_id, seller_id) REFERENCES sellers (tenant_id, seller_id),
    CONSTRAINT ck_seller_members_role CHECK (role IN ('MEMBER')),
    CONSTRAINT ck_seller_members_status CHECK (status IN ('ACTIVE', 'REVOKED'))
);

-- 정지 회수의 «다른 셀러에서 아직 ACTIVE 인가» 질문은 (tenant, account) 로 시작한다.
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
    CONSTRAINT fk_seller_member_invitations_seller FOREIGN KEY (tenant_id, seller_id) REFERENCES sellers (tenant_id, seller_id),
    CONSTRAINT ck_seller_member_invitations_status CHECK (status IN ('PENDING', 'ACCEPTED'))
);

CREATE INDEX idx_seller_member_invitations_seller ON seller_member_invitations (tenant_id, seller_id, created_at);
