package com.example.fanplatform.artist.config;

import com.example.fanplatform.artist.adapter.out.store.HttpStoreSellerDirectory;
import com.example.fanplatform.artist.application.exception.StoreSellerLookupUnavailableException;
import com.example.fanplatform.artist.application.port.out.StoreSellerDirectory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.filter.AssignableTypeFilter;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TASK-MONO-759 AC-4 — «{@code UnwiredStoreSellerDirectory} 가 제거되거나 비활성일 때도 허용 쪽
 * 빈이 생기지 않는다 (빈이 0개면 기동 실패 or fail-closed 기본값)».
 *
 * <p>The unwired adapter is deleted, so the property 748's
 * {@code AgencyDisplayTest.unwiredDirectoryIsFailClosed} pinned is re-asserted here against what
 * replaced it — behaviourally (each configuration ends fail-closed or fails to start) and
 * structurally (no second {@code StoreSellerDirectory} can sneak in behind a different switch).
 * Docker-free {@link ApplicationContextRunner}, so it runs in {@code check}.
 */
@DisplayName("StoreSellerDirectoryConfig — no permissive StoreSellerDirectory (TASK-MONO-759 AC-4)")
class StoreSellerDirectoryConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(StoreSellerDirectoryConfig.class);

    @Test
    @DisplayName("defaults → exactly one bean, the HTTP adapter")
    void defaultIsTheHttpAdapter() {
        runner.run(ctx -> {
            assertThat(ctx.getBeanNamesForType(StoreSellerDirectory.class)).hasSize(1);
            assertThat(ctx.getBean(StoreSellerDirectory.class)).isInstanceOf(HttpStoreSellerDirectory.class);
        });
    }

    @Test
    @DisplayName("🔴 configured but nothing answers → «cannot verify», never a status")
    void unreachableStoreIsFailClosed() {
        // Port 9 (discard) on loopback: nothing listens, the connection is refused.
        runner.withPropertyValues(
                        "iam.internal-client.token-uri=http://127.0.0.1:9/oauth2/token",
                        "artist.store-seller.base-url=http://127.0.0.1:9",
                        "artist.store-seller.connect-timeout-ms=300",
                        "artist.store-seller.read-timeout-ms=300")
                .run(ctx -> assertThatThrownBy(
                        () -> ctx.getBean(StoreSellerDirectory.class).findStatus("default"))
                        .isInstanceOf(StoreSellerLookupUnavailableException.class));
    }

    @Test
    @DisplayName("🔴 half-configured (a blank URL / client) → startup fails, it does not fall back to anything")
    void blankConfigurationFailsStartup() {
        for (String blank : List.of("artist.store-seller.base-url=", "iam.internal-client.token-uri=",
                "iam.internal-client.client-id=", "artist.store-seller.tenant-id=", "artist.store-seller.scope=")) {
            runner.withPropertyValues(blank).run(ctx ->
                    assertThat(ctx).as(blank).hasFailed());
        }
    }

    @Test
    @DisplayName("🔴 the configuration removed → zero beans → a consumer of the port cannot start")
    void zeroBeansMeansNoStartup() {
        new ApplicationContextRunner()
                .withUserConfiguration(NeedsADirectory.class)
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    @DisplayName("structural — exactly one StoreSellerDirectory @Bean method in the configuration")
    void singleBeanMethod() {
        List<String> beanMethods = Arrays.stream(StoreSellerDirectoryConfig.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(Bean.class))
                .filter(m -> StoreSellerDirectory.class.isAssignableFrom(m.getReturnType()))
                .map(Method::getName)
                .toList();
        assertThat(beanMethods).containsExactly("httpStoreSellerDirectory");
    }

    @Test
    @DisplayName("structural — the HTTP adapter is the only StoreSellerDirectory implementation in main code")
    void onlyOneImplementation() {
        ClassPathScanningCandidateComponentProvider scan = new ClassPathScanningCandidateComponentProvider(false);
        scan.addIncludeFilter(new AssignableTypeFilter(StoreSellerDirectory.class));
        Set<BeanDefinition> found = scan.findCandidateComponents("com.example.fanplatform.artist");
        List<String> names = found.stream().map(BeanDefinition::getBeanClassName)
                // test-scope classes (none today) are not production adapters
                .filter(n -> !n.endsWith("Test") && !n.contains("$"))
                .toList();
        assertThat(names)
                .as("a second StoreSellerDirectory (e.g. an always-ACTIVE stub) is the failure AC-4 forbids")
                .containsExactly(HttpStoreSellerDirectory.class.getName());
    }

    /** Stands in for AgencyService: something that cannot exist without the port. */
    @Configuration
    static class NeedsADirectory {
        @Bean
        Object consumer(StoreSellerDirectory directory) {
            return directory;
        }
    }
}
