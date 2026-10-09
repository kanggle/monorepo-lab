package com.example.auth.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** TASK-MONO-771 — Spring Data access to {@code account_totp}; the id IS {@code account_id}. */
public interface AccountTotpJpaRepository extends JpaRepository<AccountTotpJpaEntity, String> {
}
