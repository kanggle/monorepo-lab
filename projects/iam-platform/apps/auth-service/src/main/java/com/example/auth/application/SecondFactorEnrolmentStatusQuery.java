package com.example.auth.application;

import com.example.auth.domain.repository.AccountTotpRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * TASK-MONO-771 S5 (owner decision OD-4) — «which of these accounts already have a confirmed second factor»,
 * for the tenant entry-policy pre-check: before a tenant admin turns «2단계 필수» on, the console shows how many
 * of that tenant's operators will be asked to enrol on their next entry (admin-to-auth.md § second-factor
 * enrolment status).
 *
 * <p>Answers a set-membership question only — no secret, no recovery code, no timestamp leaves this class. A
 * pending enrolment (an unfinished {@code /mfa/setup}) is NOT counted: that account still has no second factor.
 */
@Service
@RequiredArgsConstructor
public class SecondFactorEnrolmentStatusQuery {

    /** Upper bound per call — the caller chunks. A larger request is a 400, never a silent truncation. */
    public static final int MAX_ACCOUNT_IDS = 500;

    private final AccountTotpRepository accountTotpRepository;

    /**
     * @param accountIds account ids to look at (blank entries are ignored; duplicates collapse)
     * @return the subset with a confirmed enrolment
     * @throws IllegalArgumentException when more than {@link #MAX_ACCOUNT_IDS} distinct ids are asked for
     */
    @Transactional(readOnly = true)
    public Set<String> enrolledAmong(Collection<String> accountIds) {
        Set<String> ids = new LinkedHashSet<>();
        if (accountIds != null) {
            for (String id : accountIds) {
                if (id != null && !id.isBlank()) {
                    ids.add(id);
                }
            }
        }
        if (ids.size() > MAX_ACCOUNT_IDS) {
            throw new IllegalArgumentException("At most " + MAX_ACCOUNT_IDS + " accountIds per call");
        }
        if (ids.isEmpty()) {
            return Set.of();
        }
        return accountTotpRepository.findConfirmedAccountIds(ids);
    }
}
