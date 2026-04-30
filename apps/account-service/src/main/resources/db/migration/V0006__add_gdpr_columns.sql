-- TASK-BE-054: GDPR/PIPA data rights — add columns for PII masking
ALTER TABLE accounts ADD COLUMN email_hash VARCHAR(64) NULL;
ALTER TABLE profiles ADD COLUMN masked_at DATETIME(6) NULL;
