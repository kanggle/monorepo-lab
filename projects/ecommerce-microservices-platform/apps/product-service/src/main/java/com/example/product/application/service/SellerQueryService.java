package com.example.product.application.service;

import com.example.common.summary.PeriodSummary;
import com.example.common.time.KstPeriodBounds;
import com.example.product.application.dto.SellerListResult;
import com.example.product.application.dto.SellerSummary;
import com.example.product.application.port.SellerQueryPort;
import com.example.product.domain.exception.SellerNotFoundException;
import com.example.product.domain.model.Seller;
import com.example.product.domain.repository.SellerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Operator read surface for the marketplace seller axis (ADR-MONO-030 Step 4
 * facet f). Both reads are tenant-scoped automatically — the list delegates to
 * {@link SellerQueryPort} (adapter filters {@code WHERE tenant_id}) and the detail
 * reuses the tenant-scoped {@link SellerRepository#findById}, so a cross-tenant
 * {@code sellerId} is indistinguishable from a missing one and yields 404 (M3/M6).
 */
@Service
@RequiredArgsConstructor
public class SellerQueryService {

    private final SellerQueryPort sellerQueryPort;
    private final SellerRepository sellerRepository;

    @Transactional(readOnly = true)
    public SellerListResult listSellers(int page, int size) {
        return sellerQueryPort.findAll(page, size);
    }

    @Transactional(readOnly = true)
    public SellerSummary getSeller(String sellerId) {
        Seller seller = sellerRepository.findById(sellerId)
                .orElseThrow(() -> new SellerNotFoundException(sellerId));
        return SellerSummary.from(seller);
    }

    /**
     * Returns tenant-scoped KST calendar-period-to-date seller counts for the
     * admin summary dashboard (TASK-BE-468, TASK-MONO-322).
     *
     * <p>Boundaries are computed in Asia/Seoul (KST) so that "today", "this week",
     * and "this month" align with the Korean business calendar rather than UTC.
     * All three period starts are inclusive; {@code now} is the exclusive upper bound.
     */
    @Transactional(readOnly = true)
    public PeriodSummary getPeriodSummary() {
        KstPeriodBounds b = KstPeriodBounds.now();

        long total = sellerRepository.countAllForTenant();
        long today = sellerRepository.countCreatedBetween(b.todayStartInstant(), b.nowInstant());
        long week  = sellerRepository.countCreatedBetween(b.weekStartInstant(), b.nowInstant());
        long month = sellerRepository.countCreatedBetween(b.monthStartInstant(), b.nowInstant());

        return new PeriodSummary(today, week, month, total);
    }
}
