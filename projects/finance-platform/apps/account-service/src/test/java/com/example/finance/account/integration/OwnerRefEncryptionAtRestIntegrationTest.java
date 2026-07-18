package com.example.finance.account.integration;

import com.example.finance.account.application.AccountApplicationService;
import com.example.finance.account.application.command.OpenAccountCommand;
import com.example.finance.account.application.view.AccountView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * fintech F7 — regulated PII at rest. Proves the {@code accounts.owner_ref}
 * column always holds the encrypted {@code "v1:"} envelope and never plaintext.
 *
 * <p>Guards the TASK-FIN-BE-003 Defect 1 fix (AccountRepositoryAdapter
 * flush-then-detach): the prior code wrote plaintext back onto the JPA-managed
 * entity post-save, so a tx-commit dirty-flush re-persisted plaintext over the
 * envelope (and a subsequent decrypt of a hyphen-bearing plaintext threw
 * {@code Illegal base64 character 2d}). The owner ref here intentionally
 * contains hyphens to exercise exactly that regression path.
 */
class OwnerRefEncryptionAtRestIntegrationTest extends AbstractAccountIntegrationTest {

    @Autowired
    AccountApplicationService service;

    @Test
    @DisplayName("F7: owner_ref persisted as the encrypted v1: envelope, never plaintext")
    void ownerRefEncryptedAtRest() {
        String plaintext = "owner-ref-with-hyphens-7e3";

        AccountView opened = service.openAccount(
                new OpenAccountCommand(HOLDER, plaintext, "KRW", "NONE"));

        String rawColumn = jdbcTemplate.queryForObject(
                "SELECT owner_ref FROM accounts WHERE id = ?",
                String.class, opened.accountId());

        // Encrypted envelope wire form = PiiEncryptor ACTIVE_KEY_ID + ":" + base64.
        assertThat(rawColumn).isNotNull();
        assertThat(rawColumn).startsWith("v1:");
        assertThat(rawColumn).isNotEqualTo(plaintext);
        assertThat(rawColumn).doesNotContain(plaintext);

        // Round-trip: a fresh read decrypts back to the original plaintext
        // (no Base64 failure on the hyphen-bearing value).
        AccountView reread = service.getAccount(opened.accountId(), HOLDER);
        assertThat(reread.accountId()).isEqualTo(opened.accountId());
    }
}
