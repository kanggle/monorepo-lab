package com.example.membership.infrastructure.persistence;

import com.example.membership.domain.plan.MembershipPlan;
import com.example.membership.domain.plan.MembershipPlanRepository;
import com.example.membership.domain.plan.PlanLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class MembershipPlanRepositoryAdapter implements MembershipPlanRepository {

    private final MembershipPlanJpaRepository jpa;

    @Override
    public Optional<MembershipPlan> findByPlanLevel(PlanLevel planLevel) {
        return jpa.findByPlanLevel(planLevel);
    }
}
