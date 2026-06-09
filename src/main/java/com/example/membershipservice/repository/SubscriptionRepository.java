package com.example.membershipservice.repository;

import com.example.membershipservice.model.entity.Subscription;
import com.example.membershipservice.model.enums.SubscriptionStatus;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

@Repository
public class SubscriptionRepository {

    private final ConcurrentHashMap<String, Subscription> store = new ConcurrentHashMap<>();

    public Subscription save(Subscription subscription) {
        store.put(subscription.getId(), subscription);
        return subscription;
    }

    public Optional<Subscription> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    public Optional<Subscription> findActiveByUserId(String userId) {
        return store.values().stream()
                .filter(s -> s.getUserId().equals(userId) && s.getStatus() == SubscriptionStatus.ACTIVE)
                .findFirst();
    }

    public Optional<Subscription> update(String id, UnaryOperator<Subscription> updater) {
        Subscription[] result = {null};
        store.computeIfPresent(id, (key, sub) -> {
            result[0] = updater.apply(sub);
            return result[0];
        });
        return Optional.ofNullable(result[0]);
    }
}
