package com.example.fanplatform.artist.config;

import com.example.fanplatform.artist.application.port.in.AgencyView;
import com.example.fanplatform.artist.application.port.in.ArtistGroupView;
import com.example.fanplatform.artist.application.port.in.ArtistView;
import com.example.fanplatform.artist.application.port.in.FandomView;
import com.example.fanplatform.artist.domain.agency.AgencyStatus;
import com.example.fanplatform.artist.domain.artist.ArtistStatus;
import com.example.fanplatform.artist.domain.artist.ArtistType;
import com.example.fanplatform.artist.domain.group.ArtistGroupStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * TASK-FAN-BE-050 AC-0/AC-2 — pins the date-field format of the four directory
 * read models under the mapper the running service actually resolves: whatever
 * bean {@code RedisCacheConfig} + Boot's {@code JacksonAutoConfiguration} produce
 * in combination, resolved via {@link ApplicationContextRunner} exactly as the
 * running controllers and {@code ArtistDirectoryCacheAdapter} resolve their
 * injected {@code ObjectMapper} — not a hand-built mapper (TASK-FAN-BE-038's
 * javadoc on {@code GlobalExceptionHandlerEnvelopeContractTest} names this exact
 * trap).
 *
 * <p><strong>AC-0 measurement (2026-10-04 UTC, before the fix, pinned here so the
 * record survives the fix landing):</strong> while {@code RedisCacheConfig} still
 * declared its own bare {@code new ObjectMapper().findAndRegisterModules()} bean
 * (Boot's {@code JacksonAutoConfiguration} backs off, {@code
 * @ConditionalOnMissingBean}), {@code AgencyView.createdAt} (an {@code Instant})
 * serialized as a JSON number (e.g. {@code 1785370282.333000000}) and {@code
 * ArtistView.debutDate} (a {@code LocalDate}) serialized as a 3-element JSON
 * array (e.g. {@code [2024,5,1]}), confirming the contract violation this task
 * fixes. The assertions below pin the POST-fix shape (ISO-8601 string / {@code
 * yyyy-MM-dd} string); the bite check (temporarily restoring that bean) is
 * recorded in the task file, not re-run here, because re-inverting these
 * assertions on every CI run would defeat the point of fixing the mapper.
 */
@DisplayName("artist 실효 ObjectMapper 하 날짜 포맷 계약 — Instant=ISO 문자열 · LocalDate=yyyy-MM-dd (TASK-FAN-BE-050)")
class ArtistObjectMapperDateFormatContractTest {

    private static final Instant SOME_INSTANT = Instant.parse("2026-07-30T00:11:22.333Z");
    private static final LocalDate SOME_DATE = LocalDate.of(2024, 5, 1);

    @Test
    @DisplayName("AgencyView.createdAt/updatedAt 는 ISO-8601 문자열")
    void agencyViewDatesAreIsoStrings() {
        withEffectiveMapper((mapper, soft) -> {
            AgencyView view = new AgencyView("a1", "fan-platform", "Aurora", AgencyStatus.ACTIVE,
                    null, SOME_INSTANT, SOME_INSTANT);
            JsonNode node = toJson(mapper, view);
            assertIsoInstant(soft, node, "createdAt");
            assertIsoInstant(soft, node, "updatedAt");
        });
    }

    @Test
    @DisplayName("ArtistView.debutDate 는 yyyy-MM-dd 문자열, createdAt 등은 ISO-8601 문자열")
    void artistViewDatesAreStrings() {
        withEffectiveMapper((mapper, soft) -> {
            ArtistView view = new ArtistView("ar1", "fan-platform", "acc1", ArtistType.SOLO,
                    ArtistStatus.PUBLISHED, "STAR-A", null, SOME_DATE, null, null, null, null,
                    SOME_INSTANT, SOME_INSTANT, SOME_INSTANT, null);
            JsonNode node = toJson(mapper, view);
            assertIsoInstant(soft, node, "createdAt");
            assertIsoInstant(soft, node, "updatedAt");
            assertIsoInstant(soft, node, "publishedAt");
            assertLocalDate(soft, node, "debutDate");
        });
    }

    @Test
    @DisplayName("ArtistGroupView.debutDate 는 yyyy-MM-dd 문자열, 멤버의 joinedAt 은 ISO-8601 문자열")
    void artistGroupViewDatesAreStrings() {
        withEffectiveMapper((mapper, soft) -> {
            ArtistGroupView.MemberView member = new ArtistGroupView.MemberView(
                    "ar1", com.example.fanplatform.artist.domain.group.GroupRole.LEADER, SOME_INSTANT, null);
            ArtistGroupView view = new ArtistGroupView("g1", "fan-platform", "Group X", SOME_DATE,
                    null, null, null, ArtistGroupStatus.ACTIVE, SOME_INSTANT, SOME_INSTANT, List.of(member));
            JsonNode node = toJson(mapper, view);
            assertIsoInstant(soft, node, "createdAt");
            assertIsoInstant(soft, node, "updatedAt");
            assertLocalDate(soft, node, "debutDate");
            assertIsoInstant(soft, node.get("members").get(0), "joinedAt");
        });
    }

    @Test
    @DisplayName("FandomView.foundedAt 는 yyyy-MM-dd 문자열, createdAt 등은 ISO-8601 문자열")
    void fandomViewDatesAreStrings() {
        withEffectiveMapper((mapper, soft) -> {
            FandomView view = new FandomView("ar1", "fan-platform", "Hearts", "#FFAA00",
                    SOME_DATE, "Forever", SOME_INSTANT, SOME_INSTANT);
            JsonNode node = toJson(mapper, view);
            assertIsoInstant(soft, node, "createdAt");
            assertIsoInstant(soft, node, "updatedAt");
            assertLocalDate(soft, node, "foundedAt");
        });
    }

    /**
     * Resolves the real context mapper and runs {@code assertion} with a
     * {@link SoftAssertions} that asserts all fields before failing, so a run
     * against the pre-fix buggy mapper reports every offending field (e.g. both
     * a numeric {@code Instant} and an array {@code LocalDate}) in one failure
     * message instead of stopping at the first — that combined report is what
     * AC-0's measurement in this class's javadoc was read from.
     */
    private static void withEffectiveMapper(java.util.function.BiConsumer<ObjectMapper, SoftAssertions> assertion) {
        new ApplicationContextRunner()
                .withUserConfiguration(RedisCacheConfig.class)
                .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
                .run(context -> {
                    ObjectMapper mapper = context.getBean(ObjectMapper.class);
                    SoftAssertions soft = new SoftAssertions();
                    assertion.accept(mapper, soft);
                    soft.assertAll();
                });
    }

    private static JsonNode toJson(ObjectMapper mapper, Object value) {
        try {
            return mapper.readTree(mapper.writeValueAsString(value));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void assertIsoInstant(SoftAssertions soft, JsonNode node, String field) {
        JsonNode f = node.get(field);
        soft.assertThat(f.isTextual())
                .as("%s should be a JSON string (ISO-8601), got: %s", field, f)
                .isTrue();
        if (f.isTextual()) {
            soft.assertThat(f.asText()).endsWith("Z");
        }
    }

    private static void assertLocalDate(SoftAssertions soft, JsonNode node, String field) {
        JsonNode f = node.get(field);
        soft.assertThat(f.isTextual())
                .as("%s should be a JSON string (yyyy-MM-dd), got: %s", field, f)
                .isTrue();
        if (f.isTextual()) {
            soft.assertThat(f.asText()).matches("\\d{4}-\\d{2}-\\d{2}");
        }
    }
}
