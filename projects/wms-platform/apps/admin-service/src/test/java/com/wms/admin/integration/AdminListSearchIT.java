package com.wms.admin.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.wms.admin.application.repository.AssignmentRepository;
import com.wms.admin.application.repository.RoleRepository;
import com.wms.admin.application.repository.SettingRepository;
import com.wms.admin.application.repository.UserRepository;
import com.wms.admin.application.settings.LowStockThresholdReader;
import com.wms.admin.domain.Setting;
import com.wms.admin.domain.SettingScope;
import com.wms.admin.domain.User;
import com.wms.admin.domain.UserStatus;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

/**
 * TASK-BE-652 — the admin-service list {@code search} queries (settings, users, roles,
 * assignments) against real PostgreSQL, with the filters absent or partially set.
 *
 * <p>The live defect: an unfiltered {@code GET /settings} was a 500 because
 * {@code :keyPrefix IS NULL OR s.key LIKE CONCAT(:keyPrefix, '%')} hands PostgreSQL a
 * null bound as {@code bytea} inside {@code CONCAT} ("operator does not exist: character
 * varying ~~ bytea"). Mock/in-memory repositories and the controller slice tests cannot
 * see it; only the real driver + dialect can. The container is shared and carries the dev
 * seed, so assertions are scoped to the seeded low-stock setting or a per-test token.
 */
@Tag("integration")
@SpringBootTest(classes = com.wms.admin.AdminServiceApplication.class,
        properties = {
                // Same location list as every other class on this container
                // (see ReadModelPersistenceIntegrationTest) — and the seed supplies the setting row.
                "spring.flyway.locations=classpath:db/migration,classpath:db/seed",
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
        })
@ActiveProfiles({"test", "standalone"})
@ContextConfiguration(initializers = AdminServiceIntegrationBase.Initializer.class)
@Transactional
class AdminListSearchIT extends AdminServiceIntegrationBase {

    private static final Instant NOW = Instant.parse("2026-10-23T00:00:00Z");
    private static final PageRequest PAGE = PageRequest.of(0, 200);

    @Autowired SettingRepository settingRepo;
    @Autowired UserRepository userRepo;
    @Autowired RoleRepository roleRepo;
    @Autowired AssignmentRepository assignmentRepo;

    private String tok;

    @BeforeEach
    void token() {
        tok = UUID.randomUUID().toString().substring(0, 8).toLowerCase(Locale.ROOT);
    }

    private static java.util.List<String> keys(org.springframework.data.domain.Page<Setting> page) {
        return page.map(Setting::key).getContent();
    }

    @Test
    void settings_noFilters_returnsSeededSetting() {
        // The console's default call. Was HTTP 500 on PostgreSQL.
        assertThat(keys(settingRepo.search(null, null, null, PAGE)))
                .contains(LowStockThresholdReader.KEY);
    }

    @Test
    void settings_keyPrefixOnly_matchesPrefix() {
        assertThat(keys(settingRepo.search("inventory.low_stock", null, null, PAGE)))
                .contains(LowStockThresholdReader.KEY)
                .allSatisfy(k -> assertThat(k).startsWith("inventory.low_stock"));
        assertThat(keys(settingRepo.search("zz-no-such-prefix-" + tok, null, null, PAGE))).isEmpty();
    }

    @Test
    void settings_scopeOnly() {
        assertThat(keys(settingRepo.search(null, SettingScope.GLOBAL, null, PAGE)))
                .contains(LowStockThresholdReader.KEY);
        assertThat(settingRepo.search(null, SettingScope.WAREHOUSE, null, PAGE).getContent())
                .allSatisfy(s -> assertThat(s.scope()).isEqualTo(SettingScope.WAREHOUSE));
    }

    @Test
    void settings_warehouseIdOnly() {
        assertThat(settingRepo.search(null, null, UUID.randomUUID(), PAGE).getContent()).isEmpty();
    }

    @Test
    void users_noFilters_and_eachFilterAlone() {
        UUID wh = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        userRepo.save(new User(id, "U-" + tok, tok + "@example.test", "Tester " + tok, null,
                UserStatus.ACTIVE, wh, 0L, NOW, "it", NOW, "it"));

        // Unfiltered: every param null, the default call of GET /users.
        assertThat(userRepo.search(null, null, null, PAGE).getTotalElements()).isGreaterThanOrEqualTo(1);
        assertThat(userRepo.search(UserStatus.ACTIVE, null, null, PAGE).getContent())
                .extracting(User::id).contains(id);
        assertThat(userRepo.search(null, wh, null, PAGE).getContent())
                .extracting(User::id).containsExactly(id);
        // q is matched case-insensitively against email / name / userCode.
        assertThat(userRepo.search(null, null, tok.toUpperCase(Locale.ROOT), PAGE).getContent())
                .extracting(User::id).containsExactly(id);
    }

    @Test
    void roles_and_assignments_noFilters() {
        roleRepo.search(null, PAGE);
        assignmentRepo.search(null, null, null, null, PAGE);
    }
}
