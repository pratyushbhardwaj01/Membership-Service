package com.example.membershipservice.strategy;

import com.example.membershipservice.model.enums.MembershipTier;
import com.example.membershipservice.model.value.TierCriteria;
import org.springframework.stereotype.Component;

@Component
public class CohortStrategy implements TierEvaluationStrategy {

    @Override
    public MembershipTier evaluate(TierCriteria criteria) {
        if ("VIP".equalsIgnoreCase(criteria.cohort())) return MembershipTier.PLATINUM;
        if ("STUDENT".equalsIgnoreCase(criteria.cohort())) return MembershipTier.SILVER;
        return MembershipTier.GOLD;
    }
}
