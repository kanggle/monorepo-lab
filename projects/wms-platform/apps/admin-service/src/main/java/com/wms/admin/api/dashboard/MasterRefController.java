package com.wms.admin.api.dashboard;

import com.wms.admin.api.dashboard.dto.MasterRefResponse;
import com.wms.admin.api.dto.PageResponse;
import com.wms.admin.readmodel.master.LocationRefRepository;
import com.wms.admin.readmodel.master.LotRefRepository;
import com.wms.admin.readmodel.master.PartnerRefRepository;
import com.wms.admin.readmodel.master.SkuRefRepository;
import com.wms.admin.readmodel.master.WarehouseRefRepository;
import com.wms.admin.readmodel.master.ZoneRefRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code admin-service-api.md § 1.7} — generic master reference projection
 * lookup keyed by {@code {type}}.
 */
@RestController
@RequestMapping("/api/v1/admin/dashboard/refs")
@PreAuthorize("hasRole('WMS_VIEWER')")
public class MasterRefController {

    private static final String DEFAULT_SORT = "lastEventAt,desc";

    private final WarehouseRefRepository warehouseRepo;
    private final ZoneRefRepository zoneRepo;
    private final LocationRefRepository locationRepo;
    private final SkuRefRepository skuRepo;
    private final LotRefRepository lotRepo;
    private final PartnerRefRepository partnerRepo;

    public MasterRefController(WarehouseRefRepository warehouseRepo,
                               ZoneRefRepository zoneRepo,
                               LocationRefRepository locationRepo,
                               SkuRefRepository skuRepo,
                               LotRefRepository lotRepo,
                               PartnerRefRepository partnerRepo) {
        this.warehouseRepo = warehouseRepo;
        this.zoneRepo = zoneRepo;
        this.locationRepo = locationRepo;
        this.skuRepo = skuRepo;
        this.lotRepo = lotRepo;
        this.partnerRepo = partnerRepo;
    }

    @GetMapping("/{type}")
    public PageResponse<MasterRefResponse> list(
            @PathVariable String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = DEFAULT_SORT) String sort) {
        PageRequest pageable = PageableSupport.pageable(page, size, sort);
        switch (type) {
            case "warehouses":
                return PageResponse.from(warehouseRepo.findAll(pageable), sort, MasterRefResponse::from);
            case "zones":
                return PageResponse.from(zoneRepo.findAll(pageable), sort, MasterRefResponse::from);
            case "locations":
                return PageResponse.from(locationRepo.findAll(pageable), sort, MasterRefResponse::from);
            case "skus":
                return PageResponse.from(skuRepo.findAll(pageable), sort, MasterRefResponse::from);
            case "lots":
                return PageResponse.from(lotRepo.findAll(pageable), sort, MasterRefResponse::from);
            case "partners":
                return PageResponse.from(partnerRepo.findAll(pageable), sort, MasterRefResponse::from);
            default:
                throw new IllegalArgumentException(
                        "type must be one of warehouses|zones|locations|skus|lots|partners");
        }
    }
}
