package com.example.fanplatform.community.presentation.dto;

/**
 * Shape rules for {@code mediaRefs} on the write DTOs (TASK-MONO-679 ⓐ).
 *
 * <p>A media ref is an absolute {@code https://} URL the browser renders as-is — see
 * {@code specs/contracts/http/community-api.md} § {@code mediaRefs}. Enforcing that shape at the
 * edge is what keeps the column from ever holding a storage key ({@code s3://…}), a plain-http URL
 * (blocked as mixed content on the https site) or a scheme-relative one — values the read side
 * would hand to an {@code <img src>} unchanged. The pattern is the same one the public-data
 * envelope contract uses for {@code imageUrls}, so both paths accept exactly the same URLs.
 *
 * <p>Annotation attributes need compile-time constants, hence a constants holder rather than a
 * validator bean.
 */
final class MediaRefRules {

    /** At most this many refs per post. Generous for a feed card; bounds the JSONB column. */
    static final int MAX_COUNT = 10;

    /** {@code https://} + a host + a path; no whitespace anywhere. */
    static final String HTTPS_URL = "^https://[^\\s/]+/\\S*$";

    private MediaRefRules() {
    }
}
