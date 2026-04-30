package com.example.membership.domain.plan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "membership_plans")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MembershipPlan {

    @Id
    @Column(length = 36)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_level", nullable = false, length = 20)
    private PlanLevel planLevel;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "price_krw", nullable = false)
    private int priceKrw;

    @Column(name = "duration_days", nullable = false)
    private int durationDays;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_active", nullable = false)
    private boolean active;
}
