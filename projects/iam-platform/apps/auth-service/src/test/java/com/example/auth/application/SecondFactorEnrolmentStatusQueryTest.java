package com.example.auth.application;

import com.example.auth.domain.mfa.AccountTotp;
import com.example.auth.domain.repository.AccountTotpRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * TASK-MONO-771 S5 — {@link SecondFactorEnrolmentStatusQuery}: confirmed enrolments only (a pending
 * {@code /mfa/setup} is NOT a second factor), the per-call cap, blank / duplicate ids. Runs over the repository
 * port's DEFAULT {@code findConfirmedAccountIds} (an in-memory port with only the three base methods) — so the
 * «pending does not count» rule is pinned on the domain predicate {@code AccountTotp.isConfirmed()}, the same one
 * the JPA query's {@code confirmed_at IS NOT NULL} mirrors.
 */
@DisplayName("SecondFactorEnrolmentStatusQuery (unit)")
class SecondFactorEnrolmentStatusQueryTest {

    private final Map<String, AccountTotp> rows = new HashMap<>();
    private final List<String> lookedUp = new ArrayList<>();

    private final AccountTotpRepository repo = new AccountTotpRepository() {
        @Override
        public Optional<AccountTotp> findByAccountId(String accountId) {
            lookedUp.add(accountId);
            return Optional.ofNullable(rows.get(accountId));
        }

        @Override
        public AccountTotp save(AccountTotp totp) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteByAccountId(String accountId) {
            throw new UnsupportedOperationException();
        }
    };

    private final SecondFactorEnrolmentStatusQuery query = new SecondFactorEnrolmentStatusQuery(repo);

    private static AccountTotp row(String accountId, Instant confirmedAt) {
        return new AccountTotp(accountId, "t", new byte[] {1}, "v1", confirmedAt,
                confirmedAt == null ? null : List.of("h"), null, null, Instant.EPOCH, Instant.EPOCH, 0);
    }

    @Test
    @DisplayName("🔴 confirmed counts · pending does NOT · absent does not")
    void confirmedOnly() {
        rows.put("confirmed", row("confirmed", Instant.EPOCH));
        rows.put("pending", row("pending", null));

        assertThat(query.enrolledAmong(List.of("confirmed", "pending", "absent"))).containsExactly("confirmed");
    }

    @Test
    @DisplayName("blank / null / duplicate ids collapse before the read")
    void normalises() {
        rows.put("a", row("a", Instant.EPOCH));
        List<String> ids = new ArrayList<>(List.of("a", " ", "a"));
        ids.add(null);

        assertThat(query.enrolledAmong(ids)).containsExactly("a");
        assertThat(lookedUp).containsExactly("a");
    }

    @Test
    @DisplayName("empty / null input → empty, no read")
    void empty() {
        assertThat(query.enrolledAmong(List.of())).isEmpty();
        assertThat(query.enrolledAmong(null)).isEmpty();
        assertThat(lookedUp).isEmpty();
    }

    @Test
    @DisplayName("more than 500 distinct ids → refused (the caller chunks), no read")
    void overCap() {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i <= SecondFactorEnrolmentStatusQuery.MAX_ACCOUNT_IDS; i++) ids.add("id-" + i);

        assertThatIllegalArgumentException().isThrownBy(() -> query.enrolledAmong(ids));
        assertThat(lookedUp).isEmpty();
    }
}
