package com.example.fanplatform.artist.adapter.in.web.controller;

import com.example.fanplatform.artist.adapter.in.web.dto.response.ApiEnvelope;
import com.example.fanplatform.artist.adapter.in.web.dto.response.PageMeta;
import com.example.fanplatform.artist.adapter.in.web.security.CurrentActor;
import com.example.fanplatform.artist.application.ActorContext;
import com.example.fanplatform.artist.application.port.in.ArtistView;
import com.example.fanplatform.artist.application.port.in.SearchArtistDirectoryUseCase;
import com.example.fanplatform.artist.application.port.in.SearchArtistDirectoryUseCase.DirectorySearchResult;
import com.example.fanplatform.artist.application.port.in.SearchArtistDirectoryUseCase.SearchArtistDirectoryQuery;
import com.example.fanplatform.artist.domain.artist.ArtistType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public directory search at {@code GET /api/artists?q=...}.
 *
 * <p>Sits on the same {@code /api/artists} prefix as
 * {@link ArtistController#getById(String)} but distinguishes via the absence
 * of a path variable. Spring routes the request based on URL pattern
 * specificity — the path-variable mapping in {@code ArtistController} only
 * matches {@code /api/artists/{id}}, so {@code GET /api/artists} (with or
 * without query string) lands here.
 */
@RestController
@RequestMapping("/api/artists")
@RequiredArgsConstructor
public class ArtistDirectoryController {

    private final SearchArtistDirectoryUseCase searchUseCase;

    @GetMapping
    public ResponseEntity<ApiEnvelope<List<ArtistView>>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @CurrentActor ActorContext actor) {
        ArtistType parsedType = parseType(type);
        DirectorySearchResult result = searchUseCase.search(
                new SearchArtistDirectoryQuery(actor, q, parsedType, page, size));
        return ResponseEntity.ok(ApiEnvelope.of(
                result.items(),
                PageMeta.of(result.page(), result.size(), result.totalElements(), result.totalPages())));
    }

    private static ArtistType parseType(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return ArtistType.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("type must be SOLO or GROUP_MEMBER");
        }
    }
}
