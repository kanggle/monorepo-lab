package com.wms.admin.api.dashboard;

import com.wms.admin.api.dashboard.dto.MasterRefResponse;
import com.wms.admin.api.dto.PageResponse;
import com.wms.admin.readmodel.master.LocationRefRepository;
import com.wms.admin.readmodel.master.LotRefRepository;
import com.wms.admin.readmodel.master.PartnerRefRepository;
import com.wms.admin.readmodel.master.RefSearch;
import com.wms.admin.readmodel.master.SkuRefRepository;
import com.wms.admin.readmodel.master.WarehouseRefRepository;
import com.wms.admin.readmodel.master.ZoneRefRepository;
import java.util.List;
import java.util.UUID;
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

    /**
     * TASK-BE-630 — the filters below are § 1.7's table. Before this, the endpoint took
     * only paging and returned {@code findAll}: the console's master screen sent {@code q}
     * and {@code status} and got the same unfiltered page back, with no error to say so.
     * A filter that does not apply to {@code type} (e.g. {@code skuId} on {@code zones})
     * is ignored, not rejected — same as the other § 1 list endpoints.
     */
    @GetMapping("/{type}")
    public PageResponse<MasterRefResponse> list(
            @PathVariable String type,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) UUID skuId,
            @RequestParam(required = false) List<String> partnerType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = DEFAULT_SORT) String sort) {
        PageRequest pageable = PageableSupport.pageable(page, size, sort);
        String like = RefSearch.likePattern(q);
        String st = RefSearch.status(status);
        switch (type) {
            case "warehouses":
                return PageResponse.from(warehouseRepo.search(like, st, pageable), sort,
                        MasterRefResponse::from);
            case "zones":
                return PageResponse.from(zoneRepo.search(like, st, warehouseId, pageable), sort,
                        MasterRefResponse::from);
            case "locations":
                return PageResponse.from(locationRepo.search(like, st, warehouseId, pageable), sort,
                        MasterRefResponse::from);
            case "skus":
                return PageResponse.from(skuRepo.search(like, st, pageable), sort,
                        MasterRefResponse::from);
            case "lots":
                return PageResponse.from(lotRepo.search(like, st, skuId, pageable), sort,
                        MasterRefResponse::from);
            case "partners":
                return PageResponse.from(partnerRepo.search(like, st,
                                RefSearch.anyType(partnerType), RefSearch.types(partnerType), pageable),
                        sort, MasterRefResponse::from);
            default:
                throw new IllegalArgumentException(
                        "type must be one of warehouses|zones|locations|skus|lots|partners");
        }
    }
}
