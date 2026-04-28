package com.wms.inventory.application.service;

import com.wms.inventory.application.port.in.QueryReservationUseCase;
import com.wms.inventory.application.port.out.ReservationRepository;
import com.wms.inventory.application.query.ReservationListCriteria;
import com.wms.inventory.application.result.PageView;
import com.wms.inventory.application.result.ReservationView;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QueryReservationService implements QueryReservationUseCase {

    private final ReservationRepository repository;

    public QueryReservationService(ReservationRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ReservationView> findById(UUID id) {
        return repository.findViewById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public PageView<ReservationView> list(ReservationListCriteria criteria) {
        return repository.listViews(criteria);
    }
}
