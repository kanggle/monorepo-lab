package com.example.fanplatform.artist;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * fan-platform artist-service entry point.
 *
 * <p>Master-data REST service for artist profiles, groups, fandom metadata.
 * Hexagonal architecture (ports/adapters) per
 * {@code projects/fan-platform/specs/services/artist-service/architecture.md}.
 *
 * <p>{@code @EnableScheduling} powers the outbox polling scheduler.
 */
@SpringBootApplication
@EnableScheduling
public class ArtistServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ArtistServiceApplication.class, args);
    }
}
