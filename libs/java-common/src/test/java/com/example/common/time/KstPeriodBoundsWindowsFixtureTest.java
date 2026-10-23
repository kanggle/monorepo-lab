package com.example.common.time;

import com.example.common.metrics.PerformanceMetrics;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks {@link KstPeriodBounds}' performance-metrics windows against the shared
 * fixture the console's {@code kst-period} tests also read
 * ({@code platform/contracts/fixtures/performance-metrics-windows.json},
 * TASK-MONO-806 AC-1 · AC-5). If the two implementations disagree, one side goes red.
 * <p>
 * Each case is evaluated through clocks in three different zones, so the answer
 * cannot depend on the zone the clock — or the host — happens to be in (AC-2).
 */
class KstPeriodBoundsWindowsFixtureTest {

    private static final Path FIXTURE =
            Path.of("../../platform/contracts/fixtures/performance-metrics-windows.json");

    private static final List<ZoneId> CLOCK_ZONES = List.of(
            ZoneId.of("UTC"), ZoneId.of("Asia/Seoul"), ZoneId.of("America/Los_Angeles"));

    @Test
    void every_fixture_case_matches_in_every_clock_zone() throws IOException {
        assertThat(FIXTURE).as("fixture path is resolved from libs/java-common").exists();
        JsonNode cases = new ObjectMapper().readTree(Files.readString(FIXTURE)).get("cases");
        // Non-vacuity: an empty or truncated fixture must not pass by checking nothing.
        assertThat(cases.size()).isGreaterThanOrEqualTo(8);

        int checked = 0;
        for (JsonNode c : cases) {
            Instant now = Instant.parse(c.get("now").asText());
            for (ZoneId zone : CLOCK_ZONES) {
                KstPeriodBounds bounds = KstPeriodBounds.from(Clock.fixed(now, zone));
                for (MetricsPeriod period : MetricsPeriod.values()) {
                    String label = c.get("name").asText() + " / " + period.wire() + " / clock " + zone;
                    assertCase(bounds, period, c.get(period.wire()), label);
                    checked++;
                }
            }
        }
        assertThat(checked).isEqualTo(cases.size() * CLOCK_ZONES.size() * MetricsPeriod.values().length);
    }

    private static void assertCase(KstPeriodBounds bounds, MetricsPeriod period, JsonNode expected, String label) {
        PerformanceMetrics m = PerformanceMetrics.of(bounds, period, List.of(), Map.of());

        assertThat(m.current().from()).as(label + " current.from").isEqualTo(expected.at("/current/from").asText());
        assertThat(m.current().to()).as(label + " current.to").isEqualTo(expected.at("/current/to").asText());
        assertThat(m.previous().from()).as(label + " previous.from").isEqualTo(expected.at("/previous/from").asText());
        assertThat(m.previous().to()).as(label + " previous.to").isEqualTo(expected.at("/previous/to").asText());

        List<LocalDate> dates = bounds.currentDates(period);
        assertThat(dates).as(label + " dateCount").hasSize(expected.get("dateCount").asInt());
        assertThat(dates.get(0)).as(label + " firstDate").hasToString(expected.get("firstDate").asText());
        assertThat(dates.get(dates.size() - 1)).as(label + " lastDate").hasToString(expected.get("lastDate").asText());
        assertThat(m.series()).as(label + " one series row per date").hasSize(dates.size());

        // The previous window is never longer than the current one, and never reaches into it.
        KstWindow cur = bounds.currentWindow(period);
        KstWindow prev = bounds.previousWindow(period);
        assertThat(java.time.Duration.between(prev.from(), prev.to()))
                .as(label + " previous length")
                .isLessThanOrEqualTo(java.time.Duration.between(cur.from(), cur.to()));
        assertThat(prev.to()).as(label + " previous ends by current start").isBeforeOrEqualTo(cur.from());
    }
}
