package com.example.membershipservice.controller;

import com.example.membershipservice.config.BenefitRegistry;
import com.example.membershipservice.model.entity.Subscription;
import com.example.membershipservice.model.enums.MembershipTier;
import com.example.membershipservice.model.enums.PlanType;
import com.example.membershipservice.model.enums.SubscriptionStatus;
import com.example.membershipservice.model.value.TierBenefit;
import com.example.membershipservice.service.SubscriptionService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@RestController
@RequestMapping("/api/subscriptions")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final BenefitRegistry benefitRegistry;

    public SubscriptionController(SubscriptionService subscriptionService, BenefitRegistry benefitRegistry) {
        this.subscriptionService = subscriptionService;
        this.benefitRegistry = benefitRegistry;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SubscriptionResponse subscribe(@RequestBody SubscribeRequest request) {
        return toResponse(subscriptionService.subscribe(request.userId(), request.planType(), request.tier()));
    }

    @GetMapping
    public SubscriptionResponse getSubscription(@RequestParam String userId) {
        return toResponse(subscriptionService.getSubscription(userId));
    }

    @PatchMapping("/{id}/tier")
    public SubscriptionResponse changeTier(@PathVariable String id, @RequestBody ChangeTierRequest request) {
        return toResponse(subscriptionService.changeTier(id, request.tier()));
    }

    @DeleteMapping("/{id}")
    public SubscriptionResponse cancel(@PathVariable String id) {
        return toResponse(subscriptionService.cancel(id));
    }

    private SubscriptionResponse toResponse(Subscription sub) {
        long daysRemaining = Math.max(0, ChronoUnit.DAYS.between(LocalDateTime.now(), sub.getExpiryDate()));
        TierBenefit benefits = benefitRegistry.getBenefits(sub.getTier());
        return new SubscriptionResponse(
                sub.getId(), sub.getUserId(), sub.getPlanType(), sub.getTier(),
                sub.getPrice(), sub.getStartDate(), sub.getExpiryDate(),
                sub.getStatus(), benefits, daysRemaining
        );
    }

    public record SubscribeRequest(String userId, PlanType planType, MembershipTier tier) {}
    public record ChangeTierRequest(MembershipTier tier) {}
    public record SubscriptionResponse(
            String id, String userId, PlanType planType, MembershipTier tier,
            BigDecimal price, LocalDateTime startDate, LocalDateTime expiryDate,
            SubscriptionStatus status, TierBenefit benefits, long daysRemaining
    ) {}
}
