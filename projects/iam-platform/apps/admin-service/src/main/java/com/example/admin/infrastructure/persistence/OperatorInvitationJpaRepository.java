package com.example.admin.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

/**
 * TASK-MONO-772 S2 — {@code operator_invitation}. The state changes are conditional UPDATEs: the affected-row
 * count IS the decision (1 = this writer won, 0 = the row was no longer in the state it read). They are native SQL
 * (the house form for this kind of compare-and-set, e.g. auth-service {@code CredentialJpaRepository}) so the
 * statement that runs is exactly the one written here.
 */
public interface OperatorInvitationJpaRepository extends JpaRepository<OperatorInvitationJpaEntity, Long> {

    Optional<OperatorInvitationJpaEntity> findByInvitationId(String invitationId);

    /** TASK-MONO-772 S3 — the row whose CURRENT token hashes to this value ({@code uk_operator_invitation_token_hash}). */
    Optional<OperatorInvitationJpaEntity> findByTokenHash(String tokenHash);

    boolean existsByTenantIdAndEmailAndStatus(String tenantId, String email, String status);

    Page<OperatorInvitationJpaEntity> findByTenantIdAndStatusOrderByCreatedAtDesc(
            String tenantId, String status, Pageable pageable);

    Page<OperatorInvitationJpaEntity> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    /** {@code PENDING → CANCELLED}. 0 rows ⇒ the invitation was no longer PENDING (accepted / cancelled meanwhile). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE operator_invitation"
            + "   SET status = 'CANCELLED', cancelled_at = :at, cancelled_by = :cancelledBy,"
            + "       updated_at = :at, version = version + 1"
            + " WHERE id = :id AND status = 'PENDING'",
            nativeQuery = true)
    int cancelIfPending(@Param("id") long id, @Param("cancelledBy") long cancelledBy, @Param("at") Instant at);

    /**
     * Resend: a new token hash · a new expiry · the re-sender as {@code invited_by}, on the SAME row. Guarded by
     * status AND the version that was read, so a concurrent accept / cancel / resend makes this one lose (0 rows).
     * The previous delivery result is cleared — it described the token that just died.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE operator_invitation"
            + "   SET token_hash = :tokenHash, expires_at = :expiresAt, invited_by = :invitedBy,"
            + "       last_delivery_status = NULL, last_delivery_at = NULL,"
            + "       updated_at = :at, version = version + 1"
            + " WHERE id = :id AND status = 'PENDING' AND version = :expectedVersion",
            nativeQuery = true)
    int rotateIfPending(@Param("id") long id, @Param("expectedVersion") int expectedVersion,
                        @Param("tokenHash") String tokenHash, @Param("expiresAt") Instant expiresAt,
                        @Param("invitedBy") long invitedBy, @Param("at") Instant at);

    /**
     * The delivery result of ONE token. Keyed on the token hash too, so the result of a mail for a token a later
     * resend already replaced cannot overwrite the newer token's result. Not a state change — no version bump.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE operator_invitation"
            + "   SET last_delivery_status = :status, last_delivery_at = :at"
            + " WHERE id = :id AND token_hash = :tokenHash",
            nativeQuery = true)
    int recordDelivery(@Param("id") long id, @Param("tokenHash") String tokenHash,
                       @Param("status") String status, @Param("at") Instant at);

    /**
     * TASK-MONO-772 S3 — the acceptance's claim: {@code PENDING → ACCEPTED}. 🔴 Three guards in one statement
     * (data-model.md § {@code operator_invitation} invariant): still {@code PENDING}, the token that was presented
     * is still the row's token (a resend between the read and this write kills the old link — it must not accept),
     * and not expired at {@code :at}. 0 rows ⇒ one of the three no longer holds; the caller re-reads to say which.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE operator_invitation"
            + "   SET status = 'ACCEPTED', accepted_at = :at, accepted_account_id = :accountId,"
            + "       updated_at = :at, version = version + 1"
            + " WHERE id = :id AND status = 'PENDING' AND token_hash = :tokenHash AND expires_at > :at",
            nativeQuery = true)
    int acceptIfPending(@Param("id") long id, @Param("tokenHash") String tokenHash,
                        @Param("accountId") String accountId, @Param("at") Instant at);

    /** TASK-MONO-772 S3 — the operator the acceptance just created, on the row it just accepted. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE operator_invitation"
            + "   SET accepted_operator_id = :operatorId, updated_at = :at"
            + " WHERE id = :id AND status = 'ACCEPTED'",
            nativeQuery = true)
    int recordAcceptedOperator(@Param("id") long id, @Param("operatorId") long operatorId, @Param("at") Instant at);
}
