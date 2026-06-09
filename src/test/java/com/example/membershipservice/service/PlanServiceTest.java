package com.example.membershipservice.service;

import com.example.membershipservice.config.BenefitRegistry;
import com.example.membershipservice.config.PlanPriceMatrix;
import com.example.membershipservice.model.enums.MembershipTier;
import com.example.membershipservice.model.enums.PlanType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlanServiceTest {

    private PlanService planService;

    @BeforeEach
    void setUp() {
        planService = new PlanService(new PlanPriceMatrix(), new BenefitRegistry());
    }

    @Test
    void getAvailablePlansReturnsNineOptions() {
        List<PlanService.PlanOption> options = planService.getAvailablePlans();
        assertEquals(9, options.size());
    }

    @Test
    void getAvailablePlansIncludesCorrectPriceForMonthlyGold() {
        List<PlanService.PlanOption> options = planService.getAvailablePlans();
        PlanService.PlanOption monthlyGold = options.stream()
                .filter(o -> o.planType() == PlanType.MONTHLY && o.tier() == MembershipTier.GOLD)
                .findFirst()
                .orElseThrow();
        assertEquals(0, monthlyGold.price().compareTo(new java.math.BigDecimal("199")));
    }

    @Test
    void getAvailablePlansIncludesBenefitsForEachOption() {
        List<PlanService.PlanOption> options = planService.getAvailablePlans();
        options.forEach(o -> {
            assert o.benefits() != null : "Benefits must not be null for " + o.tier();
        });
    }
}
