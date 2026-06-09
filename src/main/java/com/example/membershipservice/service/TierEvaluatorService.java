package com.example.membershipservice.service;

import com.example.membershipservice.model.enums.MembershipTier;
import com.example.membershipservice.model.value.TierCriteria;
import com.example.membershipservice.strategy.CompositeTierEvaluator;
import org.springframework.stereotype.Service;

@Service
public class TierEvaluatorService {

    private final CompositeTierEvaluator evaluator;

    public TierEvaluatorService(CompositeTierEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    public MembershipTier computeTier(TierCriteria criteria) {
        return evaluator.evaluate(criteria);
    }
}
