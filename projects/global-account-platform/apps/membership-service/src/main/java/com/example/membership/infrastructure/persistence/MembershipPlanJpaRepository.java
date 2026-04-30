package com.example.membership.infrastructure.persistence;

import com.example.membership.domain.plan.MembershipPlan;
import com.example.membership.domain.plan.PlanLevel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MembershipPlanJpaRepository extends JpaRepository<MembershipPlan, String> {

    Optional<MembershipPlan> findByPlanLevel(PlanLevel planLevel);
}
