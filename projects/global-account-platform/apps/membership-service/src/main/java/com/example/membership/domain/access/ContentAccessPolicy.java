package com.example.membership.domain.access;

import com.example.membership.domain.plan.PlanLevel;
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
@Table(name = "content_access_policies")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContentAccessPolicy {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "visibility_key", nullable = false, length = 50, unique = true)
    private String visibilityKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "required_plan_level", nullable = false, length = 20)
    private PlanLevel requiredPlanLevel;

    @Column(length = 200)
    private String description;
}
