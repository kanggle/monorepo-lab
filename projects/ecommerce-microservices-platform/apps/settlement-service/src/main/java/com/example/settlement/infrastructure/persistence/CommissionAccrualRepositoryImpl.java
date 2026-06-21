package com.example.settlement.infrastructure.persistence;

import com.example.settlement.domain.model.AccrualType;
import com.example.settlement.domain.model.CommissionAccrual;
import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.example.settlement.domain.model.SellerBalance;
import com.example.settlement.domain.repository.CommissionAccrualRepository;
import com.example.settlement.domain.repository.SellerAccrualFold;
import com.example.settlement.domain.seller.SellerScopeContext;
import com.example.settlement.domain.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Accrual-ledger persistence adapter. Write methods are insert-only (F3). Read
 * methods apply the tenant filter (always) + a nested net-zero seller-scope filter
 * (isolate-then-attribute, AC-7/AC-8) sourced from {@link TenantContext} /
 * {@link SellerScopeContext} — the single chokepoint for cross-tenant / cross-seller
 * isolation.
 */
@Repository
@RequiredArgsConstructor
public class CommissionAccrualRepositoryImpl implements CommissionAccrualRepository {

    private final CommissionAccrualJpaRepository jpaRepository;

    @Override
    public void appendAll(List<CommissionAccrual> accruals) {
        List<CommissionAccrualJpaEntity> entities = accruals.stream()
                .map(CommissionAccrualRepositoryImpl::toEntity)
                .toList();
        jpaRepository.saveAll(entities);
    }

    @Override
    public boolean existsAccrualFor(String orderId, String paymentId) {
        return jpaRepository.existsByOrderIdAndPaymentIdAndType(orderId, paymentId, AccrualType.ACCRUAL);
    }

    @Override
    public List<CommissionAccrual> findAccrualsByOrderId(String orderId) {
        // Consume path: addressed by globally-unique orderId, tenant-agnostic (the
        // reversal must find the order's accruals regardless of ambient context).
        return jpaRepository.findByOrderIdAndType(orderId, AccrualType.ACCRUAL).stream()
                .map(CommissionAccrualRepositoryImpl::toDomain)
                .toList();
    }

    @Override
    public List<CommissionAccrual> findReversalsByOrderId(String orderId) {
        // The order's existing REVERSAL rows — each carries reverses_accrual_id, so the
        // proportional clawback computes per-accrual cumulative reversed across partial refunds.
        return jpaRepository.findByOrderIdAndType(orderId, AccrualType.REVERSAL).stream()
                .map(CommissionAccrualRepositoryImpl::toDomain)
                .toList();
    }

    @Override
    public PageResult<CommissionAccrual> findAccruals(String sellerIdFilter, String orderIdFilter,
                                                      PageQuery pageQuery) {
        PageRequest pageable = PageRequest.of(pageQuery.page(), pageQuery.size(),
                Sort.by(Sort.Direction.DESC, "occurredAt"));
        Page<CommissionAccrualJpaEntity> page = jpaRepository.findAccruals(
                TenantContext.currentTenant(),
                SellerScopeContext.isRestricted(), SellerScopeContext.currentSellerScope(),
                sellerIdFilter, orderIdFilter, pageable);
        List<CommissionAccrual> content = page.getContent().stream()
                .map(CommissionAccrualRepositoryImpl::toDomain)
                .toList();
        return new PageResult<>(content, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    @Override
    public SellerBalance sellerBalance(String sellerId) {
        SellerBalanceProjection p = jpaRepository.aggregateSellerBalance(
                TenantContext.currentTenant(), sellerId,
                SellerScopeContext.isRestricted(), SellerScopeContext.currentSellerScope());
        if (p == null) {
            return SellerBalance.empty(sellerId);
        }
        return new SellerBalance(sellerId, p.accruedNetMinor(), p.platformCommissionMinor(),
                p.grossMinor(), p.accrualCount());
    }

    @Override
    public List<SellerAccrualFold> foldByPeriod(String tenantId, Instant from, Instant to) {
        // Read-only fold over the immutable accrual ledger (F3) — half-open [from, to).
        return jpaRepository.foldByPeriod(tenantId, from, to).stream()
                .map(p -> new SellerAccrualFold(
                        p.sellerId(), p.payableNetMinor(), p.commissionMinor(),
                        Math.toIntExact(p.accrualCount())))
                .toList();
    }

    private static CommissionAccrualJpaEntity toEntity(CommissionAccrual a) {
        return CommissionAccrualJpaEntity.of(a.accrualId(), a.tenantId(), a.orderId(), a.paymentId(),
                a.sellerId(), a.type(), a.grossMinor(), a.rateBps(), a.commissionMinor(),
                a.sellerNetMinor(), a.occurredAt(), a.reversesAccrualId());
    }

    private static CommissionAccrual toDomain(CommissionAccrualJpaEntity e) {
        return new CommissionAccrual(e.getAccrualId(), e.getTenantId(), e.getOrderId(), e.getPaymentId(),
                e.getSellerId(), e.getType(), e.getGrossMinor(), e.getRateBps(), e.getCommissionMinor(),
                e.getSellerNetMinor(), e.getOccurredAt(), e.getReversesAccrualId());
    }
}
