package com.example.fanplatform.artist.adapter.out.cache;

import com.example.fanplatform.artist.application.port.in.SearchArtistDirectoryUseCase.DirectorySearchResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-FAN-BE-050 AC-4 — Redis directory-cache backward compatibility.
 *
 * <p>{@code ArtistDirectoryCacheAdapter} stores {@code DirectorySearchResult} JSON under a
 * TTL of 5 minutes ({@code artist-api.md} § Directory search). An entry written by the
 * pre-fix mapper (the one {@code RedisCacheConfig} used to contribute — {@code Instant} as
 * a JSON number, {@code LocalDate} as a 3-element array) can still be sitting in Redis the
 * moment this fix deploys. This test proves the fixed mapper's {@code get()} path (same
 * {@code objectMapper.readValue(..., DirectorySearchResult.class)} call the adapter makes)
 * still reads such an entry correctly, rather than throwing and counting a spurious
 * {@code artist_directory_cache_unavailable_total} — Jackson's {@code jackson-datatype-jsr310}
 * deserializers for {@code Instant}/{@code LocalDate} accept either the legacy numeric/array
 * shape or the new ISO-8601-string shape regardless of the {@code WRITE_DATES_AS_TIMESTAMPS}
 * serialization setting (that flag only controls what the mapper <em>writes</em>), so no
 * cache-key version bump is needed.
 */
@DisplayName("artist directory 캐시 — 구 포맷(숫자/배열) 항목이 수리된 매퍼로도 읽힌다 (TASK-FAN-BE-050 AC-4)")
class ArtistDirectoryCacheOldFormatCompatibilityTest {

    /** A {@code DirectorySearchResult} JSON exactly as the PRE-FIX mapper would have
     * written it: {@code createdAt}/{@code updatedAt}/{@code publishedAt} as JSON numbers
     * (epoch seconds with fractional nanos) and {@code debutDate} as a {@code [y,m,d]}
     * array — the shapes measured in {@code ArtistObjectMapperDateFormatContractTest}'s
     * AC-0 run before this task's fix landed. */
    private static final String OLD_FORMAT_CACHE_ENTRY = """
            {
              "items": [
                {
                  "id": "ar1",
                  "tenantId": "fan-platform",
                  "accountId": "acc1",
                  "artistType": "SOLO",
                  "status": "PUBLISHED",
                  "stageName": "STAR-A",
                  "realName": null,
                  "debutDate": [2024, 5, 1],
                  "agency": null,
                  "agencyId": null,
                  "bio": null,
                  "profileImageRef": null,
                  "createdAt": 1785370282.333000000,
                  "updatedAt": 1785370282.333000000,
                  "publishedAt": 1785370282.333000000,
                  "archivedAt": null
                }
              ],
              "page": 0,
              "size": 20,
              "totalElements": 1,
              "totalPages": 1
            }
            """;

    @Test
    @DisplayName("구 포맷(숫자 Instant · 배열 LocalDate) 항목을 수리된 매퍼로 역직렬화해도 값이 보존된다")
    void oldFormatEntryStillDeserializesUnderFixedMapper() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
                .run(context -> {
                    ObjectMapper fixedMapper = context.getBean(ObjectMapper.class);

                    // Guard the premise: this IS the fixed (Boot auto-configured) mapper —
                    // it would render a raw Instant as a string, not a number.
                    assertThat(fixedMapper.writeValueAsString(Instant.parse("2026-07-30T00:11:22.333Z")))
                            .isEqualTo("\"2026-07-30T00:11:22.333Z\"");

                    DirectorySearchResult result =
                            fixedMapper.readValue(OLD_FORMAT_CACHE_ENTRY, DirectorySearchResult.class);

                    assertThat(result.items()).hasSize(1);
                    var artist = result.items().get(0);
                    assertThat(artist.id()).isEqualTo("ar1");
                    assertThat(artist.createdAt()).isEqualTo(Instant.parse("2026-07-30T00:11:22.333Z"));
                    assertThat(artist.updatedAt()).isEqualTo(Instant.parse("2026-07-30T00:11:22.333Z"));
                    assertThat(artist.publishedAt()).isEqualTo(Instant.parse("2026-07-30T00:11:22.333Z"));
                    assertThat(artist.archivedAt()).isNull();
                    assertThat(artist.debutDate()).isEqualTo(LocalDate.of(2024, 5, 1));
                });
    }
}
