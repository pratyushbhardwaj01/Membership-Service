package com.example.membershipservice.strategy;

import com.example.membershipservice.model.enums.MembershipTier;
import com.example.membershipservice.model.value.TierCriteria;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class SpendStrategy implements TierEvaluationStrategy {

    @Override
    public MembershipTier evaluate(TierCriteria criteria) {
        BigDecimal spend = criteria.totalOrderValue();
        if (spend.compareTo(new BigDecimal("10000")) >= 0) return MembershipTier.PLATINUM;
        if (spend.compareTo(new BigDecimal("5000")) >= 0) return MembershipTier.GOLD;
        return MembershipTier.SILVER;
    }
}
