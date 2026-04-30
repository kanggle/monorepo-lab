package com.example.account.application.port;

import com.example.account.application.result.AccountDetailResult;
import com.example.account.application.result.AccountSearchResult;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

/**
 * Port for account search/query operations that require pagination or join queries
 * not supported by the domain repository interfaces.
 *
 * <p>Implemented by infrastructure layer ({@code AccountQueryPortAdapter}).</p>
 */
public interface AccountQueryPort {

    AccountSearchResult findAll(Pageable pageable);

    Optional<AccountSearchResult.Item> findByEmail(String email);

    Optional<AccountDetailResult> findDetailById(String accountId);
}
