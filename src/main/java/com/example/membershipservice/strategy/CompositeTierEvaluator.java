package com.example.membershipservice.strategy;

import com.example.membershipservice.model.enums.MembershipTier;
import com.example.membershipservice.model.value.TierCriteria;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class CompositeTierEvaluator {

    private final List<TierEvaluationStrategy> strategies;

    public CompositeTierEvaluator(List<TierEvaluationStrategy> strategies) {
        this.strategies = strategies;
    }

    public MembershipTier evaluate(TierCriteria criteria) {
        return strategies.stream()
                .map(s -> s.evaluate(criteria))
                .max(Comparator.comparingInt(MembershipTier::getRank))
                .orElse(MembershipTier.SILVER);
    }
}
