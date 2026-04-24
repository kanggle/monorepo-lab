-- OAuth 사용자는 비밀번호가 없으므로 password_hash를 nullable로 변경
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;

-- OAuth 제공자 식별 컬럼 추가 (예: 'google')
ALTER TABLE users ADD COLUMN oauth_provider VARCHAR(50);
