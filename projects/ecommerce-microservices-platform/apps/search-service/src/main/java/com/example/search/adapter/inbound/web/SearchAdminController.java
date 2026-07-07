package com.example.search.adapter.inbound.web;

import com.example.search.application.port.in.ReindexAllUseCase;
import com.example.web.exception.AccessDeniedException;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 검색 인덱스의 운영/복구용 Admin 엔드포인트.
 *
 * 이 엔드포인트는 개발/운영자 수동 트리거를 전제로 하며, 외부 네트워크에
 * 직접 노출해서는 안 된다(Gateway에서 차단 필요). 현재는 gateway를 통한
 * 포워딩 없이 서비스 내부 포트(8085)로만 접근한다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/search/admin")
@Validated
public class SearchAdminController {

    private final ReindexAllUseCase reindexAllUseCase;

    /**
     * product-service에서 전체 상품을 읽어 Elasticsearch 인덱스를 재구성한다.
     *
     * 이벤트(ProductCreated) 유실이나 시드 데이터로 인해 인덱스와 DB가
     * 동기화되지 않은 상태를 복구할 때 사용한다.
     */
    @PostMapping("/reindex")
    public ResponseEntity<Map<String, Object>> reindex(
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestParam(defaultValue = "50") @Positive(message = "batchSize must be positive") int batchSize
    ) {
        validateAdminRole(userRole);
        int indexed = reindexAllUseCase.reindexAll(batchSize);
        return ResponseEntity.ok(Map.of("indexed", indexed, "batchSize", batchSize));
    }

    private void validateAdminRole(String userRole) {
        if (!hasAdminRole(userRole)) {
            throw new AccessDeniedException();
        }
    }

    private static boolean hasAdminRole(String userRole) {
        if (userRole == null || userRole.isBlank()) {
            return false;
        }
        for (String role : userRole.split(",")) {
            if ("ECOMMERCE_OPERATOR".equalsIgnoreCase(role.trim())) {
                return true;
            }
        }
        return false;
    }
}
