package com.example.membershipservice.strategy;

import com.example.membershipservice.model.enums.MembershipTier;
import com.example.membershipservice.model.value.TierCriteria;
import org.springframework.stereotype.Component;

@Component
public class OrderCountStrategy implements TierEvaluationStrategy {

    @Override
    public MembershipTier evaluate(TierCriteria criteria) {
        if (criteria.orderCount() >= 20) return MembershipTier.PLATINUM;
        if (criteria.orderCount() >= 10) return MembershipTier.GOLD;
        return MembershipTier.SILVER;
    }
}
