package com.example.membership.domain.plan;

import java.util.Optional;

public interface MembershipPlanRepository {

    Optional<MembershipPlan> findByPlanLevel(PlanLevel planLevel);
}
