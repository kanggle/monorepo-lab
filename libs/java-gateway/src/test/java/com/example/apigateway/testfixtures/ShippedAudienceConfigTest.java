package com.example.apigateway.testfixtures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The two deployment-file predicates every gateway's shipped-config suite leans on. Each cell
 * that expects a hit is the bite: the per-gateway suites only ever see their own (clean) files,
 * so without these a predicate that matched nothing would keep all of them green.
 */
@DisplayName("ShippedAudienceConfig — compose · .env 의 audience mode 술어")
class ShippedAudienceConfigTest {

    @TempDir
    Path dir;

    @Nested
    @DisplayName("modeOverrides — 전달 줄 외의 mode 설정은 전부 적발")
    class ModeOverrides {

        @Test
        @DisplayName("대조군: map 형 전달 줄 · list 형 전달 줄 · 주석 → 적발 0")
        void passThroughForms_andComments_areClean() throws IOException {
            write("docker-compose.yml", """
                    services:
                      edge:
                        environment:
                          # OIDC_AUDIENCE_MODE: SHADOW would be the rollback — set it in the env file
                          OIDC_AUDIENCE_MODE: ${OIDC_AUDIENCE_MODE:-ENFORCE}
                      other:
                        environment:
                          - OIDC_AUDIENCE_MODE=${OIDC_AUDIENCE_MODE:-ENFORCE}
                    """);
            write(".env.example", "OIDC_AUDIENCE_MODE=ENFORCE\n");

            assertThat(ShippedAudienceConfig.modeOverrides(dir)).isEmpty();
        }

        @Test
        @DisplayName("compose 에 SHADOW 고정 → 적발")
        void fixedShadowInCompose_isHit() throws IOException {
            write("docker-compose.yml", "services:\n  edge:\n    environment:\n      OIDC_AUDIENCE_MODE: SHADOW\n");

            assertThat(ShippedAudienceConfig.modeOverrides(dir)).singleElement().asString()
                    .contains("docker-compose.yml:4").contains("SHADOW");
        }

        @Test
        @DisplayName("compose 에 ENFORCE 고정 → 적발 (전달 줄이 아니면 레버가 아니다)")
        void fixedEnforceInCompose_isHit() throws IOException {
            write("docker-compose.e2e.yml", "services:\n  edge:\n    environment:\n      - OIDC_AUDIENCE_MODE=ENFORCE\n");

            assertThat(ShippedAudienceConfig.modeOverrides(dir)).hasSize(1);
        }

        @Test
        @DisplayName("다른 기본값의 전달 줄 → 적발")
        void passThroughWithShadowDefault_isHit() throws IOException {
            write("docker-compose.yml",
                    "services:\n  edge:\n    environment:\n      OIDC_AUDIENCE_MODE: ${OIDC_AUDIENCE_MODE:-SHADOW}\n");

            assertThat(ShippedAudienceConfig.modeOverrides(dir)).hasSize(1);
        }

        @Test
        @DisplayName(".env 에 SHADOW 고정 → 적발")
        void shadowInEnvFile_isHit() throws IOException {
            write("docker-compose.yml", "services: {}\n");
            write(".env", "OIDC_AUDIENCE_MODE=SHADOW\n");

            assertThat(ShippedAudienceConfig.modeOverrides(dir)).singleElement().asString()
                    .contains(".env:1");
        }

        @Test
        @DisplayName("compose 파일이 하나도 없으면 → 빈 결과가 아니라 예외 (공허한 통과 금지)")
        void noComposeFile_throws() {
            assertThatThrownBy(() -> ShippedAudienceConfig.modeOverrides(dir))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("read nothing");
        }
    }

    @Nested
    @DisplayName("servicesPassingModeThrough — 전달 줄이 실제로 그 서비스 아래에 있는가")
    class PassThrough {

        @Test
        @DisplayName("map 형 · list 형 · YAML merge key 로 들어온 전달 줄을 모두 읽는다")
        void readsMapListAndMergedEnvironments() throws IOException {
            Path compose = write("docker-compose.yml", """
                    x-common: &common
                      OIDC_AUDIENCE_MODE: ${OIDC_AUDIENCE_MODE:-ENFORCE}
                    services:
                      a-map:
                        environment:
                          OIDC_AUDIENCE_MODE: ${OIDC_AUDIENCE_MODE:-ENFORCE}
                      b-list:
                        environment:
                          - SERVER_PORT=8080
                          - OIDC_AUDIENCE_MODE=${OIDC_AUDIENCE_MODE:-ENFORCE}
                      c-merged:
                        environment:
                          <<: *common
                          SERVER_PORT: "8080"
                      d-none:
                        environment:
                          SERVER_PORT: "8080"
                    """);

            assertThat(ShippedAudienceConfig.servicesPassingModeThrough(compose))
                    .containsExactly("a-map", "b-list", "c-merged");
        }

        @Test
        @DisplayName("주석 처리된 전달 줄 · 다른 값 → 그 서비스는 전달하지 않는 것으로 센다")
        void commentedOrDifferentValue_doesNotCount() throws IOException {
            Path compose = write("docker-compose.yml", """
                    services:
                      commented:
                        environment:
                          # OIDC_AUDIENCE_MODE: ${OIDC_AUDIENCE_MODE:-ENFORCE}
                          SERVER_PORT: "8080"
                      fixed:
                        environment:
                          OIDC_AUDIENCE_MODE: ENFORCE
                    """);

            assertThat(ShippedAudienceConfig.servicesPassingModeThrough(compose)).isEmpty();
        }
    }

    private Path write(String name, String content) throws IOException {
        return Files.writeString(dir.resolve(name), content);
    }
}
