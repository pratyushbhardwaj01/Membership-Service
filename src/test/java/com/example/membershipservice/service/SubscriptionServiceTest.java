package com.example.membershipservice.service;

import com.example.membershipservice.config.PlanPriceMatrix;
import com.example.membershipservice.exception.ConflictException;
import com.example.membershipservice.exception.ResourceNotFoundException;
import com.example.membershipservice.model.entity.Subscription;
import com.example.membershipservice.model.entity.User;
import com.example.membershipservice.model.enums.MembershipTier;
import com.example.membershipservice.model.enums.PlanType;
import com.example.membershipservice.model.enums.SubscriptionStatus;
import com.example.membershipservice.repository.SubscriptionRepository;
import com.example.membershipservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SubscriptionServiceTest {

    private UserRepository userRepository;
    private SubscriptionRepository subscriptionRepository;
    private SubscriptionService subscriptionService;
    private User savedUser;

    @BeforeEach
    void setUp() {
        userRepository = new UserRepository();
        subscriptionRepository = new SubscriptionRepository();
        subscriptionService = new SubscriptionService(userRepository, subscriptionRepository, new PlanPriceMatrix());
        savedUser = userRepository.save(new User("Alice", "alice@example.com"));
    }

    @Test
    void subscribeShouldCreateActiveSubscription() {
        Subscription sub = subscriptionService.subscribe(savedUser.getId(), PlanType.MONTHLY, MembershipTier.GOLD);

        assertEquals(savedUser.getId(), sub.getUserId());
        assertEquals(PlanType.MONTHLY, sub.getPlanType());
        assertEquals(MembershipTier.GOLD, sub.getTier());
        assertEquals(SubscriptionStatus.ACTIVE, sub.getStatus());
        assertNotNull(sub.getExpiryDate());
        assertEquals(0, sub.getPrice().compareTo(new java.math.BigDecimal("199")));
    }

    @Test
    void subscribeShouldSetExpiryToOneMonthForMonthlyPlan() {
        Subscription sub = subscriptionService.subscribe(savedUser.getId(), PlanType.MONTHLY, MembershipTier.SILVER);
        assertTrue(sub.getExpiryDate().isAfter(sub.getStartDate().plusDays(28)));
        assertTrue(sub.getExpiryDate().isBefore(sub.getStartDate().plusDays(32)));
    }

    @Test
    void subscribeShouldThrowConflictIfAlreadySubscribed() {
        subscriptionService.subscribe(savedUser.getId(), PlanType.MONTHLY, MembershipTier.SILVER);

        assertThrows(ConflictException.class,
                () -> subscriptionService.subscribe(savedUser.getId(), PlanType.MONTHLY, MembershipTier.GOLD));
    }

    @Test
    void subscribeShouldThrowNotFoundForUnknownUser() {
        assertThrows(ResourceNotFoundException.class,
                () -> subscriptionService.subscribe("unknown-id", PlanType.MONTHLY, MembershipTier.SILVER));
    }

    @Test
    void changeTierShouldUpdateTierAndRecalculatePrice() {
        Subscription sub = subscriptionService.subscribe(savedUser.getId(), PlanType.MONTHLY, MembershipTier.SILVER);
        Subscription upgraded = subscriptionService.changeTier(sub.getId(), MembershipTier.PLATINUM);

        assertEquals(MembershipTier.PLATINUM, upgraded.getTier());
        assertEquals(0, upgraded.getPrice().compareTo(new java.math.BigDecimal("299")));
        assertEquals(sub.getExpiryDate(), upgraded.getExpiryDate());
    }

    @Test
    void changeTierShouldThrowNotFoundForUnknownSubscription() {
        assertThrows(ResourceNotFoundException.class,
                () -> subscriptionService.changeTier("unknown-id", MembershipTier.GOLD));
    }

    @Test
    void changeTierShouldThrowConflictForCancelledSubscription() {
        Subscription sub = subscriptionService.subscribe(savedUser.getId(), PlanType.MONTHLY, MembershipTier.SILVER);
        subscriptionService.cancel(sub.getId());

        assertThrows(ConflictException.class,
                () -> subscriptionService.changeTier(sub.getId(), MembershipTier.GOLD));
    }

    @Test
    void cancelShouldSetStatusToCancelled() {
        Subscription sub = subscriptionService.subscribe(savedUser.getId(), PlanType.MONTHLY, MembershipTier.GOLD);
        Subscription cancelled = subscriptionService.cancel(sub.getId());

        assertEquals(SubscriptionStatus.CANCELLED, cancelled.getStatus());
    }

    @Test
    void getSubscriptionShouldReturnActiveSubscription() {
        subscriptionService.subscribe(savedUser.getId(), PlanType.QUARTERLY, MembershipTier.GOLD);
        Subscription sub = subscriptionService.getSubscription(savedUser.getId());

        assertEquals(savedUser.getId(), sub.getUserId());
        assertEquals(SubscriptionStatus.ACTIVE, sub.getStatus());
    }

    @Test
    void getSubscriptionShouldThrowNotFoundIfNoneActive() {
        assertThrows(ResourceNotFoundException.class,
                () -> subscriptionService.getSubscription(savedUser.getId()));
    }
}
