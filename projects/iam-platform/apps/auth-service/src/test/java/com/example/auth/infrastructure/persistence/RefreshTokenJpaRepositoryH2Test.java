package com.example.auth.infrastructure.persistence;

import com.example.auth.domain.token.RefreshToken;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-BE-607 AC-2 — H2 auxiliary slice (platform/testing-strategy.md § H2 auxiliary-slice
 * exception). NON-AUTHORITATIVE — {@link RefreshTokenJpaRepositoryTest} (Testcontainers MySQL,
 * {@code ddl-auto=validate} against the real Flyway-migrated schema) remains the source of
 * truth and is unaffected either way by this class.
 *
 * <p><b>Why this slice exists and the Testcontainers test does not catch the regression it
 * guards.</b> {@link RefreshTokenJpaEntity#jti} / {@code rotatedFrom} declared
 * {@code length = 36} while Flyway {@code V0014} widened the actual columns to
 * {@code VARCHAR(255)} to hold SAS's ~128-char refresh token values (TASK-MONO-046-1 Cluster A;
 * {@code specs/services/auth-service/data-model.md} already documented 255 correctly — only the
 * entity annotation drifted). {@code ddl-auto=validate} — what the Testcontainers/Flyway IT
 * uses — does not fail on a declared-length-vs-actual-column-length mismatch, so that test stays
 * green with the wrong annotation. Hibernate DOES use the declared length when it generates the
 * schema itself ({@code ddl-auto=create-drop}, this slice and the H2-backed
 * {@code OAuth2AuthorizationServerSliceTest}), so there the column really is created at 36 and
 * a SAS-length token INSERT fails with a truncation error — this is the failure TASK-BE-604 § ⑤
 * hit and worked around locally (BE-601's discovered defect, ⑩-5).
 */
@DataJpaTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:refresh_token_length_test;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=VALUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@DisplayName("RefreshTokenJpaEntity H2 슬라이스 — jti/rotated_from 길이 (TASK-BE-607 AC-2, 비권위)")
class RefreshTokenJpaRepositoryH2Test {

    @Autowired
    private RefreshTokenJpaRepository repo;

    @Test
    @DisplayName("SAS 길이(128자) jti INSERT 성공 — length=36 이던 시절엔 Hibernate DDL 이 컬럼을 36으로 만들어 여기서 실패했다")
    void insert_sasLengthJti_succeeds() {
        // Shaped like SAS's default StringKeyGenerator / PublicClientRefreshTokenGenerator
        // output (~96-byte URL-safe base64, ~128 chars) — see V0014's own rationale comment.
        String sasLengthJti = "a".repeat(128);
        assertThat(sasLengthJti).hasSize(128);

        RefreshToken token = RefreshToken.create(
                sasLengthJti, UUID.randomUUID().toString(), "fan-platform",
                Instant.now(), Instant.now().plus(Duration.ofDays(30)), null, null, null);

        repo.save(RefreshTokenJpaEntity.fromDomain(token));

        Optional<RefreshTokenJpaEntity> saved = repo.findByJti(sasLengthJti);
        assertThat(saved).isPresent();
        assertThat(saved.get().getJti()).isEqualTo(sasLengthJti);
    }

    @Test
    @DisplayName("SAS 길이(128자) rotated_from INSERT 성공 — 대칭 컬럼도 같은 결함을 가졌다")
    void insert_sasLengthRotatedFrom_succeeds() {
        String parentJti = "b".repeat(128);
        String childJti = "c".repeat(128);

        repo.save(RefreshTokenJpaEntity.fromDomain(RefreshToken.create(
                parentJti, UUID.randomUUID().toString(), "fan-platform",
                Instant.now(), Instant.now().plus(Duration.ofDays(30)), null, null, null)));
        repo.save(RefreshTokenJpaEntity.fromDomain(RefreshToken.create(
                childJti, UUID.randomUUID().toString(), "fan-platform",
                Instant.now(), Instant.now().plus(Duration.ofDays(30)), parentJti, null, null)));

        Optional<RefreshTokenJpaEntity> child = repo.findByJti(childJti);
        assertThat(child).isPresent();
        assertThat(child.get().getRotatedFrom()).isEqualTo(parentJti);
    }
}
