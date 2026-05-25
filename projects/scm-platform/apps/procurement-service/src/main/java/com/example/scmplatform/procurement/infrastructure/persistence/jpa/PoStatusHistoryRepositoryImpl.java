package com.example.scmplatform.procurement.infrastructure.persistence.jpa;

import com.example.scmplatform.procurement.domain.po.status.PoStatusHistory;
import com.example.scmplatform.procurement.domain.po.status.PoStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PoStatusHistoryRepositoryImpl implements PoStatusHistoryRepository {

    private final PoStatusHistoryJpaRepository jpa;

    @Override
    public PoStatusHistory save(PoStatusHistory entry) {
        return jpa.save(entry);
    }
}
