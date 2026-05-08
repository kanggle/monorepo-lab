package com.wms.admin.api.dashboard;

import com.wms.admin.api.dashboard.dto.AsnSummaryResponse;
import com.wms.admin.api.dashboard.dto.InspectionSummaryResponse;
import com.wms.admin.api.dto.PageResponse;
import com.wms.admin.readmodel.inbound.AsnSummaryEntity;
import com.wms.admin.readmodel.inbound.AsnSummaryRepository;
import com.wms.admin.readmodel.inbound.InspectionSummaryRepository;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** {@code admin-service-api.md § 1.4} — ASN summary + per-ASN inspection. */
@RestController
@RequestMapping("/api/v1/admin/dashboard/asns")
@PreAuthorize("hasRole('WMS_VIEWER')")
public class AsnDashboardController {

    private static final String DEFAULT_SORT = "receivedAt,desc";

    private final AsnSummaryRepository asnRepo;
    private final InspectionSummaryRepository inspectionRepo;

    public AsnDashboardController(AsnSummaryRepository asnRepo,
                                  InspectionSummaryRepository inspectionRepo) {
        this.asnRepo = asnRepo;
        this.inspectionRepo = inspectionRepo;
    }

    @GetMapping
    public PageResponse<AsnSummaryResponse> list(
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) UUID supplierPartnerId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String source,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expectedArriveDateFrom,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expectedArriveDateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = DEFAULT_SORT) String sort) {
        DateRangeSupport.validate("expectedArriveDate", expectedArriveDateFrom, expectedArriveDateTo);
        Page<AsnSummaryEntity> result = asnRepo.search(warehouseId, supplierPartnerId, status,
                source, expectedArriveDateFrom, expectedArriveDateTo,
                PageableSupport.pageable(page, size, sort));
        return PageResponse.from(result, sort, AsnSummaryResponse::from);
    }

    @GetMapping("/{asnId}/inspection")
    public ResponseEntity<InspectionSummaryResponse> inspection(@PathVariable UUID asnId) {
        return inspectionRepo.findById(asnId)
                .map(InspectionSummaryResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
