package com.example.shipping.domain.repository;

import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.example.shipping.domain.model.Shipping;
import com.example.shipping.domain.model.ShippingStatus;

import java.util.Optional;

public interface ShippingRepository {

    Shipping save(Shipping shipping);

    Optional<Shipping> findById(String shippingId);

    Optional<Shipping> findByOrderId(String orderId);

    boolean existsByOrderId(String orderId);

    PageResult<Shipping> findAll(PageQuery pageQuery);

    PageResult<Shipping> findByStatus(ShippingStatus status, PageQuery pageQuery);
}
