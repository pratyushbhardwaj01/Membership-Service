package com.example.membershipservice.strategy;

import com.example.membershipservice.model.enums.MembershipTier;
import com.example.membershipservice.model.value.TierCriteria;

public interface TierEvaluationStrategy {
    MembershipTier evaluate(TierCriteria criteria);
}
