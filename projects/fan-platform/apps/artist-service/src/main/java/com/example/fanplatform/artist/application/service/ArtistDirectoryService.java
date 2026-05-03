package com.example.fanplatform.artist.application.service;

import com.example.fanplatform.artist.application.port.in.ArtistView;
import com.example.fanplatform.artist.application.port.in.SearchArtistDirectoryUseCase;
import com.example.fanplatform.artist.application.port.out.ArtistDirectoryCache;
import com.example.fanplatform.artist.application.port.out.ArtistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Read-heavy directory service. Implements read-through Redis caching with
 * fail-open semantics (cache miss / unavailability → DB query). Per task
 * spec § Implementation Notes.
 *
 * <p>Cache key: {@code <tenantId>:<sha256(qNorm|type|page|size)>}. The
 * adapter prefixes {@code cache:fan-platform:artist:directory:} per the
 * project naming convention (see {@code application.yml} →
 * {@code fanplatform.artist.cache.directory.namespace}).
 */
@Service
@RequiredArgsConstructor
public class ArtistDirectoryService implements SearchArtistDirectoryUseCase {

    private final ArtistRepository artistRepository;
    private final ArtistDirectoryCache cache;

    @Override
    @Transactional(readOnly = true)
    public DirectorySearchResult search(SearchArtistDirectoryQuery q) {
        String tenantId = q.actor().tenantId();
        int page = Math.max(0, q.page());
        int size = Math.max(1, Math.min(100, q.size()));
        String qNorm = q.q() == null ? "" : q.q().trim();

        String key = buildKey(qNorm, q.type() == null ? "" : q.type().name(), page, size);
        Optional<DirectorySearchResult> cached = cache.get(tenantId, key);
        if (cached.isPresent()) {
            return cached.get();
        }
        ArtistRepository.DirectoryPage dbPage = artistRepository.findPublishedDirectoryPage(
                tenantId, qNorm.isEmpty() ? null : qNorm, q.type(), page, size);
        List<ArtistView> items = dbPage.items().stream().map(ArtistView::from).toList();
        DirectorySearchResult result = new DirectorySearchResult(
                items, dbPage.page(), dbPage.size(), dbPage.totalElements(), dbPage.totalPages());
        cache.put(tenantId, key, result);
        return result;
    }

    static String buildKey(String qNorm, String typeName, int page, int size) {
        String raw = qNorm + "|" + typeName + "|" + page + "|" + size;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandatory in every JVM — should never fire.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
