package com.example.admin.infrastructure.access;

import com.example.admin.infrastructure.persistence.access.AdminResourceTagJpaRepository;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * TASK-BE-355 (ADR-MONO-029) — resolves an account's governance tags for the
 * RESOURCE_TAG access condition. Applicable to the account lock/unlock mutations
 * {@code POST /api/admin/accounts/{accountId}/lock|unlock}; the collection-level
 * {@code POST /api/admin/accounts/bulk-lock} (no single id) is not matched
 * (skipped). Tags come from the trusted admin-local {@code admin_resource_tags}
 * table (type {@code ACCOUNT}).
 */
@Component
public class AccountResourceTagResolver extends AbstractLocalResourceTagResolver {

    private static final Pattern ACCOUNT_MUTATION =
            Pattern.compile("^/api/admin/accounts/([^/]+)/(?:lock|unlock)$");

    public AccountResourceTagResolver(AdminResourceTagJpaRepository repository) {
        super(repository, "ACCOUNT", ACCOUNT_MUTATION);
    }
}
