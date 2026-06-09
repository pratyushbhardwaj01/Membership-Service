package com.example.membershipservice.config;

import com.example.membershipservice.model.enums.MembershipTier;
import com.example.membershipservice.model.enums.PlanType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class PlanPriceMatrix {

    private static final Map<PlanType, Map<MembershipTier, BigDecimal>> PRICES = Map.of(
            PlanType.MONTHLY, Map.of(
                    MembershipTier.SILVER,   new BigDecimal("99"),
                    MembershipTier.GOLD,     new BigDecimal("199"),
                    MembershipTier.PLATINUM, new BigDecimal("299")
            ),
            PlanType.QUARTERLY, Map.of(
                    MembershipTier.SILVER,   new BigDecimal("269"),
                    MembershipTier.GOLD,     new BigDecimal("539"),
                    MembershipTier.PLATINUM, new BigDecimal("809")
            ),
            PlanType.YEARLY, Map.of(
                    MembershipTier.SILVER,   new BigDecimal("999"),
                    MembershipTier.GOLD,     new BigDecimal("1999"),
                    MembershipTier.PLATINUM, new BigDecimal("2999")
            )
    );

    public BigDecimal getPrice(PlanType planType, MembershipTier tier) {
        return PRICES.get(planType).get(tier);
    }

    public Map<PlanType, Map<MembershipTier, BigDecimal>> getAllPrices() {
        return PRICES;
    }
}
