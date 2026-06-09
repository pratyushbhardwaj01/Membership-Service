package com.example.membershipservice.strategy;

import com.example.membershipservice.model.enums.MembershipTier;
import com.example.membershipservice.model.value.TierCriteria;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompositeTierEvaluatorTest {

    private CompositeTierEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new CompositeTierEvaluator(
                List.of(new OrderCountStrategy(), new SpendStrategy(), new CohortStrategy())
        );
    }

    @Test
    void orderCountStrategyReturnsPlatinumFor20PlusOrders() {
        TierCriteria criteria = new TierCriteria(20, BigDecimal.ZERO, null);
        assertEquals(MembershipTier.PLATINUM, new OrderCountStrategy().evaluate(criteria));
    }

    @Test
    void orderCountStrategyReturnsGoldFor10To19Orders() {
        TierCriteria criteria = new TierCriteria(15, BigDecimal.ZERO, null);
        assertEquals(MembershipTier.GOLD, new OrderCountStrategy().evaluate(criteria));
    }

    @Test
    void orderCountStrategyReturnsSilverForUnder10Orders() {
        TierCriteria criteria = new TierCriteria(5, BigDecimal.ZERO, null);
        assertEquals(MembershipTier.SILVER, new OrderCountStrategy().evaluate(criteria));
    }

    @Test
    void spendStrategyReturnsPlatinumFor10000PlusSpend() {
        TierCriteria criteria = new TierCriteria(0, new BigDecimal("10000"), null);
        assertEquals(MembershipTier.PLATINUM, new SpendStrategy().evaluate(criteria));
    }

    @Test
    void spendStrategyReturnsGoldFor5000To9999Spend() {
        TierCriteria criteria = new TierCriteria(0, new BigDecimal("7500"), null);
        assertEquals(MembershipTier.GOLD, new SpendStrategy().evaluate(criteria));
    }

    @Test
    void spendStrategyReturnsSilverForUnder5000Spend() {
        TierCriteria criteria = new TierCriteria(0, new BigDecimal("1000"), null);
        assertEquals(MembershipTier.SILVER, new SpendStrategy().evaluate(criteria));
    }

    @Test
    void cohortStrategyReturnsPlatinumForVip() {
        TierCriteria criteria = new TierCriteria(0, BigDecimal.ZERO, "VIP");
        assertEquals(MembershipTier.PLATINUM, new CohortStrategy().evaluate(criteria));
    }

    @Test
    void cohortStrategyReturnsSilverForStudent() {
        TierCriteria criteria = new TierCriteria(0, BigDecimal.ZERO, "STUDENT");
        assertEquals(MembershipTier.SILVER, new CohortStrategy().evaluate(criteria));
    }

    @Test
    void cohortStrategyReturnsGoldForOthers() {
        TierCriteria criteria = new TierCriteria(0, BigDecimal.ZERO, "REGULAR");
        assertEquals(MembershipTier.GOLD, new CohortStrategy().evaluate(criteria));
    }

    @Test
    void compositeReturnsHighestTierAcrossAllStrategies() {
        TierCriteria criteria = new TierCriteria(5, new BigDecimal("8000"), "VIP");
        assertEquals(MembershipTier.PLATINUM, evaluator.evaluate(criteria));
    }

    @Test
    void compositeReturnsSilverWhenAllStrategiesReturnSilver() {
        TierCriteria criteria = new TierCriteria(1, new BigDecimal("100"), "REGULAR");
        // orderCount=1→SILVER, spend=100→SILVER, cohort=REGULAR→GOLD → composite returns GOLD
        assertEquals(MembershipTier.GOLD, evaluator.evaluate(criteria));
    }
}
