package com.example.fanplatform.artist.application.service;

import com.example.fanplatform.artist.application.ActorContext;
import com.example.fanplatform.artist.application.port.in.SearchArtistDirectoryUseCase.DirectorySearchResult;
import com.example.fanplatform.artist.application.port.in.SearchArtistDirectoryUseCase.SearchArtistDirectoryQuery;
import com.example.fanplatform.artist.application.port.out.ArtistDirectoryCache;
import com.example.fanplatform.artist.application.port.out.ArtistRepository;
import com.example.fanplatform.artist.domain.artist.ArtistType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class ArtistDirectoryServiceTest {

    @Mock ArtistRepository repo;
    @Mock ArtistDirectoryCache cache;
    @InjectMocks ArtistDirectoryService service;

    private static final ActorContext ADMIN =
            new ActorContext("admin-1", "fan-platform", Set.of("ADMIN"));

    @Test
    @DisplayName("cache hit: returns cached value, no DB call")
    void cacheHit() {
        DirectorySearchResult cached = new DirectorySearchResult(List.of(), 0, 20, 0, 0);
        when(cache.get(eq("fan-platform"), anyString())).thenReturn(Optional.of(cached));

        DirectorySearchResult out = service.search(
                new SearchArtistDirectoryQuery(ADMIN, "abc", null, 0, 20));

        assertThat(out).isSameAs(cached);
        verify(repo, never()).findPublishedDirectoryPage(anyString(), anyString(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("cache miss: DB query + cache put")
    void cacheMiss() {
        when(cache.get(eq("fan-platform"), anyString())).thenReturn(Optional.empty());
        when(repo.findPublishedDirectoryPage(eq("fan-platform"), eq("abc"), eq(ArtistType.SOLO), eq(0), eq(20)))
                .thenReturn(new ArtistRepository.DirectoryPage(List.of(), 0, 20, 0L, 0));

        service.search(new SearchArtistDirectoryQuery(ADMIN, "abc", ArtistType.SOLO, 0, 20));

        verify(cache, times(1)).put(eq("fan-platform"), anyString(), any(DirectorySearchResult.class));
    }

    @Test
    @DisplayName("buildKey: deterministic for identical inputs")
    void buildKey_deterministic() {
        String a = ArtistDirectoryService.buildKey("abc", "SOLO", 0, 20);
        String b = ArtistDirectoryService.buildKey("abc", "SOLO", 0, 20);
        assertThat(a).isEqualTo(b);
    }

    @Test
    @DisplayName("buildKey: different inputs -> different keys")
    void buildKey_differs() {
        String a = ArtistDirectoryService.buildKey("abc", "SOLO", 0, 20);
        String b = ArtistDirectoryService.buildKey("abc", "SOLO", 1, 20);
        assertThat(a).isNotEqualTo(b);
    }
}
