package com.example.membershipservice.model.entity;

import com.example.membershipservice.model.enums.MembershipTier;
import com.example.membershipservice.model.enums.PlanType;
import com.example.membershipservice.model.enums.SubscriptionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class Subscription {
    private final String id;
    private final String userId;
    private final PlanType planType;
    private final MembershipTier tier;
    private final BigDecimal price;
    private final LocalDateTime startDate;
    private final LocalDateTime expiryDate;
    private final SubscriptionStatus status;

    public Subscription(String userId, PlanType planType, MembershipTier tier, BigDecimal price) {
        this.id = UUID.randomUUID().toString();
        this.userId = userId;
        this.planType = planType;
        this.tier = tier;
        this.price = price;
        this.startDate = LocalDateTime.now();
        this.expiryDate = this.startDate.plusMonths(planType.getDurationMonths());
        this.status = SubscriptionStatus.ACTIVE;
    }

    private Subscription(String id, String userId, PlanType planType, MembershipTier tier,
                         BigDecimal price, LocalDateTime startDate, LocalDateTime expiryDate,
                         SubscriptionStatus status) {
        this.id = id;
        this.userId = userId;
        this.planType = planType;
        this.tier = tier;
        this.price = price;
        this.startDate = startDate;
        this.expiryDate = expiryDate;
        this.status = status;
    }

    public Subscription withTier(MembershipTier newTier, BigDecimal newPrice) {
        return new Subscription(id, userId, planType, newTier, newPrice, startDate, expiryDate, status);
    }

    public Subscription cancelled() {
        return new Subscription(id, userId, planType, tier, price, startDate, expiryDate, SubscriptionStatus.CANCELLED);
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public PlanType getPlanType() { return planType; }
    public MembershipTier getTier() { return tier; }
    public BigDecimal getPrice() { return price; }
    public LocalDateTime getStartDate() { return startDate; }
    public LocalDateTime getExpiryDate() { return expiryDate; }
    public SubscriptionStatus getStatus() { return status; }
}
