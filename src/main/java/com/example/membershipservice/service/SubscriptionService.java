package com.example.membershipservice.service;

import com.example.membershipservice.config.PlanPriceMatrix;
import com.example.membershipservice.exception.ConflictException;
import com.example.membershipservice.exception.ResourceNotFoundException;
import com.example.membershipservice.model.entity.Subscription;
import com.example.membershipservice.model.enums.MembershipTier;
import com.example.membershipservice.model.enums.PlanType;
import com.example.membershipservice.model.enums.SubscriptionStatus;
import com.example.membershipservice.repository.SubscriptionRepository;
import com.example.membershipservice.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SubscriptionService {

    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PlanPriceMatrix priceMatrix;
    private final ConcurrentHashMap<String, Object> userLocks = new ConcurrentHashMap<>();

    public SubscriptionService(UserRepository userRepository,
                               SubscriptionRepository subscriptionRepository,
                               PlanPriceMatrix priceMatrix) {
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.priceMatrix = priceMatrix;
    }

    public Subscription subscribe(String userId, PlanType planType, MembershipTier tier) {
        userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        Object lock = userLocks.computeIfAbsent(userId, k -> new Object());
        synchronized (lock) {
            subscriptionRepository.findActiveByUserId(userId).ifPresent(s -> {
                throw new ConflictException("User already has an active subscription: " + s.getId());
            });
            BigDecimal price = priceMatrix.getPrice(planType, tier);
            Subscription subscription = new Subscription(userId, planType, tier, price);
            return subscriptionRepository.save(subscription);
        }
    }

    public Subscription changeTier(String subscriptionId, MembershipTier newTier) {
        Subscription current = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found: " + subscriptionId));

        if (current.getStatus() != SubscriptionStatus.ACTIVE) {
            throw new ConflictException("Cannot change tier on a non-active subscription");
        }

        BigDecimal newPrice = priceMatrix.getPrice(current.getPlanType(), newTier);
        return subscriptionRepository.update(subscriptionId, sub -> sub.withTier(newTier, newPrice))
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found: " + subscriptionId));
    }

    public Subscription cancel(String subscriptionId) {
        subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found: " + subscriptionId));

        return subscriptionRepository.update(subscriptionId, Subscription::cancelled)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found: " + subscriptionId));
    }

    public Subscription getSubscription(String userId) {
        return subscriptionRepository.findActiveByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("No active subscription for user: " + userId));
    }
}
