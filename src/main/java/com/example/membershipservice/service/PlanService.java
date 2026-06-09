package com.example.membershipservice.service;

import com.example.membershipservice.config.BenefitRegistry;
import com.example.membershipservice.config.PlanPriceMatrix;
import com.example.membershipservice.model.enums.MembershipTier;
import com.example.membershipservice.model.enums.PlanType;
import com.example.membershipservice.model.value.TierBenefit;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class PlanService {

    private final PlanPriceMatrix priceMatrix;
    private final BenefitRegistry benefitRegistry;

    public PlanService(PlanPriceMatrix priceMatrix, BenefitRegistry benefitRegistry) {
        this.priceMatrix = priceMatrix;
        this.benefitRegistry = benefitRegistry;
    }

    public List<PlanOption> getAvailablePlans() {
        List<PlanOption> options = new ArrayList<>();
        for (PlanType planType : PlanType.values()) {
            for (MembershipTier tier : MembershipTier.values()) {
                BigDecimal price = priceMatrix.getPrice(planType, tier);
                TierBenefit benefits = benefitRegistry.getBenefits(tier);
                options.add(new PlanOption(planType, tier, price, benefits));
            }
        }
        return options;
    }

    public BigDecimal getPrice(PlanType planType, MembershipTier tier) {
        return priceMatrix.getPrice(planType, tier);
    }

    public record PlanOption(PlanType planType, MembershipTier tier, BigDecimal price, TierBenefit benefits) {}
}
