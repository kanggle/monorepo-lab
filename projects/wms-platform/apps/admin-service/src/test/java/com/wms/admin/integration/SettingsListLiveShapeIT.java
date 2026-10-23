package com.wms.admin.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.wms.admin.api.dto.PageResponse;
import com.wms.admin.api.settings.SettingsController;
import com.wms.admin.api.settings.dto.SettingResponse;
import com.wms.admin.application.repository.SettingRepository;
import com.wms.admin.application.settings.LowStockThresholdReader;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

/**
 * TASK-BE-652 — the live 500 on {@code GET /settings}, reproduced the way the console
 * actually calls it: the controller with {@code page=0&size=20} and the controller's
 * default {@code sort=key,asc} (a <b>sorted</b> {@code Pageable}).
 *
 * <p>{@link AdminListSearchIT} called the repository with an <i>unsorted</i>
 * {@code PageRequest} and was green on the unfixed query, so it did not reproduce the
 * live failure. The two things this class changes against it are (1) the sorted pageable
 * the controller builds ({@code PageableSupport.pageable}) and (2) a cold Hibernate
 * query-interpretation cache before every test, so the unfiltered call is the
 * <i>first</i> execution of the query in the JVM, as in the live service.
 */
@Tag("integration")
@SpringBootTest(classes = com.wms.admin.AdminServiceApplication.class,
        properties = {
                "spring.flyway.locations=classpath:db/migration,classpath:db/seed",
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
        })
@ActiveProfiles({"test", "standalone"})
@ContextConfiguration(initializers = AdminServiceIntegrationBase.Initializer.class)
@Transactional
@WithMockUser(username = "ops-it", roles = {"WMS_ADMIN", "WMS_VIEWER"})
class SettingsListLiveShapeIT extends AdminServiceIntegrationBase {

    @Autowired SettingsController controller;
    @Autowired SettingRepository settingRepo;
    @Autowired EntityManagerFactory emf;

    @BeforeEach
    void coldQueryCache() {
        emf.unwrap(SessionFactoryImplementor.class).getQueryEngine()
                .getInterpretationCache().close();
    }

    @Test
    void consoleCall_noQueryFilters_sortedPage20_isServed() {
        // GET /settings?page=0&size=20 — exactly the console's listSettings({}) call.
        PageResponse<SettingResponse> res = controller.list(null, null, null, 0, 20, "key,asc");

        assertThat(res.content()).extracting(SettingResponse::key)
                .contains(LowStockThresholdReader.KEY);
    }

    @Test
    void repository_sortedPageable_noFilters() {
        assertThat(settingRepo.search(null, null, null,
                        PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "key"))).getContent())
                .isNotEmpty();
    }
}
