package com.example.membershipservice.model.value;

public record TierBenefit(
        boolean freeDelivery,
        int discountPercent,
        boolean exclusiveDeals,
        boolean prioritySupport
) {}
