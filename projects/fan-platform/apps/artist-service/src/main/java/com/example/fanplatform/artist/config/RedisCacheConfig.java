package com.example.fanplatform.artist.config;

import org.springframework.context.annotation.Configuration;

/**
 * Bean wiring for the Redis-backed artist directory cache. Spring Boot's
 * default {@code StringRedisTemplate} auto-configuration is sufficient, and
 * {@code ArtistDirectoryCacheAdapter} / {@code ArtistEventPublisherAdapter}
 * take the {@code ObjectMapper} Boot's {@code JacksonAutoConfiguration}
 * provides by dependency injection — there is nothing left to wire here.
 *
 * <p><strong>TASK-FAN-BE-050 (fixed 2026-10-04 UTC).</strong> This class used
 * to declare its own {@code ObjectMapper @Bean} ({@code new ObjectMapper()
 * .findAndRegisterModules()}, marked {@code @ConditionalOnMissingBean}). That
 * bean existed first in every context, so Boot's own {@code ObjectMapper}
 * auto-configuration backed off, and the service ran with a mapper whose
 * {@code WRITE_DATES_AS_TIMESTAMPS} is Jackson's library default
 * ({@code true}) rather than Boot's default ({@code false}) — every
 * {@code java.time.Instant} on {@code AgencyView}/{@code ArtistView}/
 * {@code ArtistGroupView}/{@code FandomView} serialized as a JSON number and
 * every {@code LocalDate} as a 3-element array, violating {@code
 * artist-api.md}'s documented ISO-8601 string shape and breaking the console
 * fan directory's JSON parsing. Removing the bean restores Boot's
 * auto-configured mapper (ISO-8601 strings for {@code java.time} types) for
 * every consumer of the injected {@code ObjectMapper} — the directory cache
 * adapter and the outbox event-envelope writer alike. See
 * {@code ArtistObjectMapperDateFormatContractTest} for the regression pin.
 */
@Configuration
public class RedisCacheConfig {
}
