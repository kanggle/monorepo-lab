-- DEV ONLY: short operator_id "admin" for convenience.
-- Same password as V0014 dev operator: "devpassword123!"

INSERT IGNORE INTO admin_operators (
    operator_id, email, password_hash, display_name, status, created_at, updated_at, version
) VALUES (
    'admin',
    'admin@example.com',
    '$argon2id$v=16$m=65536,t=3,p=1$7u/kw4KcLt7/i1nTEzEfsH7kRIraSsh1w9qOB7BhxUMTJdk3Oqp6zBklBlcMzJ4jS0PpgLYN+MW+1HlJF3m7ew$OJzCJkqvkul/EbS2FejjcDPx7Htj2HkAiCz74xcGBeY',
    'Admin',
    'ACTIVE',
    NOW(6), NOW(6), 0
);

INSERT IGNORE INTO admin_operator_roles (operator_id, role_id, granted_at, granted_by)
SELECT o.id, r.id, NOW(6), NULL
  FROM admin_operators o
  JOIN admin_roles r ON r.name = 'SUPER_ADMIN'
 WHERE o.operator_id = 'admin';
