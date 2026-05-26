package com.wms.admin.api.dashboard;

import com.wms.admin.api.dashboard.dto.ProjectionStatusResponse;
import com.wms.admin.application.projection.ProjectionStatusService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code admin-service-api.md § 6.2} — projection lag report.
 *
 * <p>Thin REST controller; all dedupe-aggregation + lag-probe / listener-registry
 * fallback logic lives in {@link ProjectionStatusService}.
 */
@RestController
@RequestMapping("/api/v1/admin/operations")
@PreAuthorize("hasRole('WMS_ADMIN')")
public class OperationsController {

    private final ProjectionStatusService projectionStatusService;

    public OperationsController(ProjectionStatusService projectionStatusService) {
        this.projectionStatusService = projectionStatusService;
    }

    @GetMapping("/projection-status")
    public ProjectionStatusResponse projectionStatus() {
        return projectionStatusService.computeStatus();
    }
}
