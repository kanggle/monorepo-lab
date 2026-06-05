-- TASK-BE-063
-- auth-service가 credential 저장의 단일 진실 원본이 되도록, credentials 테이블에
-- email 컬럼을 추가한다. 로그인 시 auth-service가 email → credential 조회를 스스로
-- 수행하여 account-service 왕복을 제거한다.
--
-- 이메일은 로그인 식별자이며 restricted 등급의 비밀이 아니다. account-service의
-- accounts.email과 소스 오브 트루스는 여전히 account-service이지만, auth-service는
-- 로그인 처리에 필요한 최소한의 역정규화 사본을 보유한다(S1은 credential_hash의
-- 물리적 분리를 요구하는 것이지 email까지 금지하지는 않음).
--
-- 기존 row는 없다고 가정(테이블이 런타임에 채워진 적 없음 — TASK-BE-063 배경 참조).
-- 만약 기존 row가 있다면 email을 NULL로 두고 수동 백필 필요.

ALTER TABLE credentials
    ADD COLUMN email VARCHAR(320) NULL AFTER account_id;

CREATE UNIQUE INDEX idx_credentials_email ON credentials (email);
