package com.example.membershipservice.config;

import com.example.membershipservice.model.enums.MembershipTier;
import com.example.membershipservice.model.value.TierBenefit;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class BenefitRegistry {

    private static final Map<MembershipTier, TierBenefit> BENEFITS = Map.of(
            MembershipTier.SILVER,   new TierBenefit(false, 5,  false, false),
            MembershipTier.GOLD,     new TierBenefit(true,  10, true,  false),
            MembershipTier.PLATINUM, new TierBenefit(true,  15, true,  true)
    );

    public TierBenefit getBenefits(MembershipTier tier) {
        return BENEFITS.get(tier);
    }

    public Map<MembershipTier, TierBenefit> getAllBenefits() {
        return BENEFITS;
    }
}
