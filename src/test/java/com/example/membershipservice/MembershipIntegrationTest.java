package com.example.membershipservice;

import com.example.membershipservice.controller.PlanController;
import com.example.membershipservice.controller.SubscriptionController;
import com.example.membershipservice.controller.TierController;
import com.example.membershipservice.controller.UserController;
import com.example.membershipservice.model.enums.MembershipTier;
import com.example.membershipservice.model.enums.PlanType;
import com.example.membershipservice.model.enums.SubscriptionStatus;
import com.example.membershipservice.model.value.TierCriteria;
import com.example.membershipservice.service.PlanService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class MembershipIntegrationTest {

    @Autowired private UserController userController;
    @Autowired private PlanController planController;
    @Autowired private SubscriptionController subscriptionController;
    @Autowired private TierController tierController;

    @Test
    void fullMembershipLifecycle() {
        // 1. Create user
        var user = userController.createUser(new UserController.CreateUserRequest("Bob", "bob@example.com"));
        assertNotNull(user.getId());
        assertEquals("Bob", user.getName());

        // 2. List plans — 9 options (3 plans × 3 tiers)
        List<PlanService.PlanOption> plans = planController.getPlans();
        assertEquals(9, plans.size());

        // 3. Subscribe to MONTHLY SILVER
        var subResponse = subscriptionController.subscribe(
                new SubscriptionController.SubscribeRequest(user.getId(), PlanType.MONTHLY, MembershipTier.SILVER));
        assertEquals(MembershipTier.SILVER, subResponse.tier());
        assertEquals(SubscriptionStatus.ACTIVE, subResponse.status());
        assertEquals(0, subResponse.price().compareTo(new BigDecimal("99")));
        assertFalse(subResponse.benefits().freeDelivery());

        // 4. Get subscription by userId
        var fetched = subscriptionController.getSubscription(user.getId());
        assertEquals(subResponse.id(), fetched.id());

        // 5. Upgrade to GOLD — price updates, expiry stays the same
        var upgraded = subscriptionController.changeTier(
                subResponse.id(), new SubscriptionController.ChangeTierRequest(MembershipTier.GOLD));
        assertEquals(MembershipTier.GOLD, upgraded.tier());
        assertEquals(0, upgraded.price().compareTo(new BigDecimal("199")));
        assertTrue(upgraded.benefits().freeDelivery());
        assertEquals(subResponse.expiryDate(), upgraded.expiryDate());

        // 6. Evaluate tier from manual criteria
        var evalResponse = tierController.evaluate(
                new TierCriteria(25, new BigDecimal("12000"), "VIP"));
        assertEquals(MembershipTier.PLATINUM, evalResponse.recommendedTier());

        // 7. Cancel subscription
        var cancelled = subscriptionController.cancel(subResponse.id());
        assertEquals(SubscriptionStatus.CANCELLED, cancelled.status());
    }
}
