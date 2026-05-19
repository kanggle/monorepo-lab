package com.example.finance.account.infrastructure.persistence.jpa;

import com.example.finance.account.domain.account.Account;
import com.example.finance.account.domain.account.repository.AccountRepository;
import com.example.finance.account.infrastructure.crypto.PiiEncryptor;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.Optional;

/**
 * Account persistence adapter. The {@code ownerRef} is a regulated identifier
 * (F7) — encrypted on write, decrypted on read. The encryption is applied via
 * a transient round-trip on the same {@link Account} entity so the domain
 * never sees ciphertext and the DB never sees plaintext.
 */
@Component
@RequiredArgsConstructor
public class AccountRepositoryAdapter implements AccountRepository {

    private final AccountJpaRepository jpa;
    private final PiiEncryptor piiEncryptor;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Account save(Account account) {
        String plaintextOwnerRef = account.getOwnerRef();
        writeField(account, "ownerRef", piiEncryptor.encryptToString(plaintextOwnerRef));
        // Flush the ciphertext to the DB, then DETACH before restoring plaintext:
        // a managed entity mutated back to plaintext would be dirty-flushed on
        // tx commit, re-persisting plaintext over the envelope and breaking F7
        // (the column must always hold ciphertext). The caller gets a detached
        // instance carrying plaintext; its mutation is no longer tracked.
        Account saved = jpa.saveAndFlush(account);
        entityManager.detach(saved);
        writeField(saved, "ownerRef", plaintextOwnerRef);
        return saved;
    }

    @Override
    public Optional<Account> findById(String id, String tenantId) {
        return jpa.findByIdAndTenantId(id, tenantId).map(this::decrypt);
    }

    private Account decrypt(Account a) {
        // Detach BEFORE writing the decrypted plaintext so the read does not
        // leave a dirty managed entity that would flush plaintext into the
        // owner_ref column on tx commit (F7: ciphertext at rest only).
        entityManager.detach(a);
        writeField(a, "ownerRef", piiEncryptor.decryptFromString(a.getOwnerRef()));
        return a;
    }

    private static void writeField(Object target, String name, String value) {
        try {
            Field f = Account.class.getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to apply PII codec to " + name, e);
        }
    }
}
