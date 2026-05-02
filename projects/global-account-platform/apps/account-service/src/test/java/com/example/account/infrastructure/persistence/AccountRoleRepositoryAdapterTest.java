package com.example.account.infrastructure.persistence;

import com.example.account.domain.account.AccountRole;
import com.example.account.domain.tenant.TenantId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * TASK-BE-265: Unit tests for {@link AccountRoleRepositoryAdapter#addIfAbsent}
 * concurrent-duplicate handling. The adapter must convert
 * {@link DataIntegrityViolationException} caused by MySQL {@code ER_DUP_ENTRY}
 * (1062) into an idempotent {@code false} (no-op) return, while re-throwing
 * other integrity violations (e.g. FK 1452) so callers still see them.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AccountRoleRepositoryAdapter — TASK-BE-265")
class AccountRoleRepositoryAdapterTest {

    private static final TenantId TENANT_ID = new TenantId("wms");
    private static final String ACCOUNT_ID = "acc-uuid-001";
    private static final String ROLE_NAME = "WAREHOUSE_ADMIN";

    private static final int MYSQL_ER_DUP_ENTRY = 1062;
    private static final int MYSQL_ER_NO_REFERENCED_ROW = 1452;

    @Mock
    private AccountRoleJpaRepository jpaRepository;

    @InjectMocks
    private AccountRoleRepositoryAdapter adapter;

    @Test
    @DisplayName("기존 row 가 없고 save 성공 → true")
    void addIfAbsent_freshInsert_returnsTrue() {
        AccountRole role = AccountRole.create(TENANT_ID, ACCOUNT_ID, ROLE_NAME, "sys-wms");

        given(jpaRepository.findByTenantIdAndAccountIdAndRoleName(
                TENANT_ID.value(), ACCOUNT_ID, ROLE_NAME)).willReturn(Optional.empty());
        given(jpaRepository.save(any(AccountRoleJpaEntity.class)))
                .willAnswer(inv -> inv.getArgument(0));

        boolean inserted = adapter.addIfAbsent(role);

        assertThat(inserted).isTrue();
    }

    @Test
    @DisplayName("findBy 가 이미 존재하는 row 반환 → false (insert 호출 없음)")
    void addIfAbsent_alreadyPresent_returnsFalse() {
        AccountRole role = AccountRole.create(TENANT_ID, ACCOUNT_ID, ROLE_NAME, "sys-wms");
        AccountRoleJpaEntity existing = AccountRoleJpaEntity.fromDomain(role);

        given(jpaRepository.findByTenantIdAndAccountIdAndRoleName(
                TENANT_ID.value(), ACCOUNT_ID, ROLE_NAME)).willReturn(Optional.of(existing));

        boolean inserted = adapter.addIfAbsent(role);

        assertThat(inserted).isFalse();
    }

    @Test
    @DisplayName("동시 insert 경합으로 PK 위반(1062) → false (idempotent no-op)")
    void addIfAbsent_concurrentDuplicate_returnsFalse() {
        AccountRole role = AccountRole.create(TENANT_ID, ACCOUNT_ID, ROLE_NAME, "sys-wms");

        given(jpaRepository.findByTenantIdAndAccountIdAndRoleName(
                TENANT_ID.value(), ACCOUNT_ID, ROLE_NAME)).willReturn(Optional.empty());
        given(jpaRepository.save(any(AccountRoleJpaEntity.class)))
                .willThrow(duplicateKeyException());

        boolean inserted = adapter.addIfAbsent(role);

        assertThat(inserted).isFalse();
    }

    @Test
    @DisplayName("FK 위반(1452 — account 미존재)은 재-throw 되어야 함")
    void addIfAbsent_foreignKeyViolation_isRethrown() {
        AccountRole role = AccountRole.create(TENANT_ID, ACCOUNT_ID, ROLE_NAME, "sys-wms");

        given(jpaRepository.findByTenantIdAndAccountIdAndRoleName(
                TENANT_ID.value(), ACCOUNT_ID, ROLE_NAME)).willReturn(Optional.empty());
        given(jpaRepository.save(any(AccountRoleJpaEntity.class)))
                .willThrow(foreignKeyException());

        assertThatThrownBy(() -> adapter.addIfAbsent(role))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("vendor code 가 누락된 일반 integrity 위반은 재-throw")
    void addIfAbsent_unknownIntegrityViolation_isRethrown() {
        AccountRole role = AccountRole.create(TENANT_ID, ACCOUNT_ID, ROLE_NAME, "sys-wms");

        given(jpaRepository.findByTenantIdAndAccountIdAndRoleName(
                TENANT_ID.value(), ACCOUNT_ID, ROLE_NAME)).willReturn(Optional.empty());
        given(jpaRepository.save(any(AccountRoleJpaEntity.class)))
                .willThrow(new DataIntegrityViolationException("opaque", new RuntimeException("no SQL cause")));

        assertThatThrownBy(() -> adapter.addIfAbsent(role))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * Build a {@link DataIntegrityViolationException} whose cause chain
     * terminates in a {@link SQLIntegrityConstraintViolationException} with
     * MySQL's {@code ER_DUP_ENTRY} (1062). This mirrors the exception that
     * Hibernate raises when two concurrent inserts collide on the composite
     * primary key (Spring then translates it via {@code SQLExceptionTranslator}).
     */
    private static DataIntegrityViolationException duplicateKeyException() {
        SQLException sql = new SQLIntegrityConstraintViolationException(
                "Duplicate entry 'wms-acc-uuid-001-WAREHOUSE_ADMIN' for key 'PRIMARY'",
                "23000",
                MYSQL_ER_DUP_ENTRY);
        return new DataIntegrityViolationException(
                "could not execute statement", sql);
    }

    private static DataIntegrityViolationException foreignKeyException() {
        SQLException sql = new SQLIntegrityConstraintViolationException(
                "Cannot add or update a child row: a foreign key constraint fails",
                "23000",
                MYSQL_ER_NO_REFERENCED_ROW);
        return new DataIntegrityViolationException(
                "could not execute statement", sql);
    }
}
