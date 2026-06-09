package com.example.membershipservice.model.value;

import java.math.BigDecimal;

public record TierCriteria(
        int orderCount,
        BigDecimal totalOrderValue,
        String cohort
) {}
