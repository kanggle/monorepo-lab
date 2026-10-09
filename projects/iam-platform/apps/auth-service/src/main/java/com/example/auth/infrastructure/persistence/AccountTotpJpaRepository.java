package com.example.auth.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/** TASK-MONO-771 — Spring Data access to {@code account_totp}; the id IS {@code account_id}. */
public interface AccountTotpJpaRepository extends JpaRepository<AccountTotpJpaEntity, String> {

    /** S5 — the accounts among {@code accountIds} with a CONFIRMED enrolment (pending rows excluded). */
    @Query("SELECT t.accountId FROM AccountTotpJpaEntity t "
            + "WHERE t.accountId IN :accountIds AND t.confirmedAt IS NOT NULL")
    List<String> findConfirmedAccountIds(@Param("accountIds") Collection<String> accountIds);
}
